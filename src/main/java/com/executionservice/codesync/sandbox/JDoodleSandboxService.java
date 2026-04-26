package com.executionservice.codesync.sandbox;

import com.executionservice.codesync.entity.ExecutionJob;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Executes code via the JDoodle Compiler API.
 *
 * JDoodle: https://www.jdoodle.com/compiler-api
 * Free tier: 200 calls/day per account.
 *
 * Request format:
 * POST https://api.jdoodle.com/v1/execute
 * {
 *   "clientId":     "YOUR_CLIENT_ID",
 *   "clientSecret": "YOUR_CLIENT_SECRET",
 *   "script":       "<source code>",
 *   "language":     "python3",
 *   "versionIndex": "4",
 *   "stdin":        "<optional stdin>"
 * }
 *
 * Response:
 * {
 *   "output":             "Hello\n",
 *   "statusCode":         200,
 *   "memory":             "7424",
 *   "cpuTime":            "0.08",
 *   "isExecutionSuccess": true
 * }
 *
 * Active when: sandbox.provider=jdoodle  (set in application.properties)
 */
@Service
@ConditionalOnProperty(name = "sandbox.provider", havingValue = "jdoodle")
public class JDoodleSandboxService implements SandboxService {

    private static final Logger log = LoggerFactory.getLogger(JDoodleSandboxService.class);

    private static final String JDOODLE_URL = "https://api.jdoodle.com/v1/execute";

    @Value("${JDOODLE_CLIENT_ID:}")
    private String clientId;

    @Value("${JDOODLE_CLIENT_SECRET:}")
    private String clientSecret;

    @Value("${sandbox.max-execution-seconds:10}")
    private int maxExecutionSeconds;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Maps CodeSync language identifiers → JDoodle language + versionIndex.
     *
     * Full language list: https://api.jdoodle.com/v1/languages
     * versionIndex "0" is always the oldest stable; higher indexes are newer.
     * Using the highest known stable index for each language.
     */
    private static final Map<String, String[]> LANGUAGE_MAP = Map.ofEntries(
        // [jdoodle_language, versionIndex]
        Map.entry("python",     new String[]{"python3",     "4"}),
        Map.entry("java",       new String[]{"java",        "5"}),
        Map.entry("javascript", new String[]{"nodejs",      "4"}),
        Map.entry("typescript", new String[]{"typescript",  "1"}),
        Map.entry("c",          new String[]{"c",           "5"}),
        Map.entry("cpp",        new String[]{"cpp17",       "1"}),
        Map.entry("go",         new String[]{"go",          "4"}),
        Map.entry("rust",       new String[]{"rust",        "4"}),
        Map.entry("ruby",       new String[]{"ruby",        "4"}),
        Map.entry("php",        new String[]{"php",         "4"}),
        Map.entry("kotlin",     new String[]{"kotlin",      "3"}),
        Map.entry("r",          new String[]{"r",           "4"}),
        Map.entry("swift",      new String[]{"swift",       "4"}),
        Map.entry("csharp",     new String[]{"csharp",      "4"}),
        Map.entry("scala",      new String[]{"scala",       "4"}),
        Map.entry("perl",       new String[]{"perl",        "4"}),
        Map.entry("bash",       new String[]{"bash",        "4"}),
        Map.entry("sql",        new String[]{"sql",         "4"})
    );

    @Override
    public SandboxResult execute(String language, String sourceCode, String stdin) {
        String lang = language.toLowerCase();

        if (!LANGUAGE_MAP.containsKey(lang)) {
            return SandboxResult.error("Unsupported language: " + language +
                    ". Supported: " + String.join(", ", LANGUAGE_MAP.keySet()));
        }

        String[] langConfig = LANGUAGE_MAP.get(lang);
        long start = System.currentTimeMillis();

        try {
            // Build request body
            var requestBody = new java.util.LinkedHashMap<String, Object>();
            requestBody.put("clientId",     clientId);
            requestBody.put("clientSecret", clientSecret);
            requestBody.put("script",       sourceCode);
            requestBody.put("language",     langConfig[0]);
            requestBody.put("versionIndex", langConfig[1]);
            if (stdin != null && !stdin.isBlank()) {
                requestBody.put("stdin", stdin);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JDoodleResponse> response = restTemplate.exchange(
                    JDOODLE_URL,
                    HttpMethod.POST,
                    entity,
                    JDoodleResponse.class
            );

            long execTimeMs = System.currentTimeMillis() - start;

            if (response.getBody() == null) {
                return SandboxResult.error("Empty response from JDoodle API");
            }

            JDoodleResponse jr = response.getBody();

            // JDoodle statusCode 200 = success, 429 = daily limit exceeded
            if (jr.getStatusCode() != null && jr.getStatusCode() == 429) {
                log.warn("JDoodle daily limit (200 calls/day) exceeded");
                return SandboxResult.error(
                        "Daily execution limit reached (200 calls/day on free tier). " +
                        "Resets at midnight UTC.");
            }

            String output = jr.getOutput() != null ? jr.getOutput() : "";

            // JDoodle returns all output (stdout+stderr) in the "output" field.
            // TLE is indicated by the output containing "Time Limit Exceeded".
            boolean timedOut = output.contains("Time Limit Exceeded");

            // Parse cpuTime
            long cpuMs = 0L;
            if (jr.getCpuTime() != null) {
                try { cpuMs = (long)(Double.parseDouble(jr.getCpuTime()) * 1000); }
                catch (NumberFormatException ignored) {}
            }

            // Parse memory (KB)
            long memKb = 0L;
            if (jr.getMemory() != null) {
                try { memKb = Long.parseLong(jr.getMemory()); }
                catch (NumberFormatException ignored) {}
            }

            ExecutionJob.Status status;
            int exitCode;
            String stdout;
            String stderr = "";

            if (timedOut) {
                status   = ExecutionJob.Status.TIMED_OUT;
                exitCode = -1;
                stdout   = "";
                stderr   = "Execution timed out after " + maxExecutionSeconds + " seconds";
            } else if (Boolean.TRUE.equals(jr.getIsExecutionSuccess())) {
                status   = ExecutionJob.Status.COMPLETED;
                exitCode = 0;
                stdout   = output;
            } else {
                // Compilation error or runtime error
                status   = ExecutionJob.Status.FAILED;
                exitCode = 1;
                stdout   = "";
                stderr   = output;
            }

            log.info("JDoodle executed [lang={} v={} success={} cpuMs={}]",
                    langConfig[0], langConfig[1], jr.getIsExecutionSuccess(), cpuMs);

            return SandboxResult.builder()
                    .stdout(stdout)
                    .stderr(stderr)
                    .exitCode(exitCode)
                    .executionTimeMs(cpuMs > 0 ? cpuMs : execTimeMs)
                    .memoryUsedKb(memKb)
                    .status(status)
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("JDoodle HTTP error: {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            return SandboxResult.error("JDoodle API error " + e.getStatusCode() +
                    ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("JDoodle call failed: {}", e.getMessage(), e);
            return SandboxResult.error("JDoodle API error: " + e.getMessage());
        }
    }

    @Override
    public List<String> getSupportedLanguages() {
        return List.copyOf(LANGUAGE_MAP.keySet());
    }

    // ── JDoodle response DTO ──────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JDoodleResponse {
        private String  output;
        private Integer statusCode;
        private String  memory;
        private String  cpuTime;
        private Boolean isExecutionSuccess;
        private String  error;

        public String  getOutput()             { return output; }
        public Integer getStatusCode()         { return statusCode; }
        public String  getMemory()             { return memory; }
        public String  getCpuTime()            { return cpuTime; }
        public Boolean getIsExecutionSuccess() { return isExecutionSuccess; }
        public String  getError()              { return error; }
    }
}
