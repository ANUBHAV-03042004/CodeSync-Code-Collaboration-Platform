package com.executionservice.codesync.sandbox;

import com.executionservice.codesync.entity.ExecutionJob;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Executes code via the Piston API — a free, open-source code execution engine.
 *
 * Piston GitHub: https://github.com/engineer-man/piston
 * Public instance: https://emkc.org/api/v2/piston  (rate-limited, good for dev)
 *
 * For production, self-host Piston on any $5/month VM:
 *   docker run -d --restart always -p 2000:2000 ghcr.io/engineer-man/piston
 * Then set: sandbox.piston-url=http://your-vm-ip:2000/api/v2
 *
 * Piston request format:
 * POST /execute
 * {
 *   "language": "python",
 *   "version": "*",
 *   "files": [{"content": "<source code>"}],
 *   "stdin": "<optional stdin>"
 * }
 *
 * Active when: sandbox.provider=piston  (default)
 */
@Service
@ConditionalOnProperty(name = "sandbox.provider", havingValue = "piston", matchIfMissing = true)
public class PistonSandboxService implements SandboxService {

    private static final Logger log = LoggerFactory.getLogger(PistonSandboxService.class);

    @Value("${sandbox.piston-url:https://emkc.org/api/v2/piston}")
    private String pistonUrl;

    @Value("${sandbox.max-execution-seconds:10}")
    private int maxExecutionSeconds;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Maps CodeSync language identifiers to Piston's language/version pairs.
     * Piston uses "python" not "python3", "javascript" not "node", etc.
     */
    private static final Map<String, String[]> LANGUAGE_MAP = Map.ofEntries(
        Map.entry("python",     new String[]{"python",     "3.10.0"}),
        Map.entry("java",       new String[]{"java",       "15.0.2"}),
        Map.entry("javascript", new String[]{"javascript", "18.15.0"}),
        Map.entry("typescript", new String[]{"typescript", "5.0.3"}),
        Map.entry("c",          new String[]{"c",          "10.2.0"}),
        Map.entry("cpp",        new String[]{"c++",        "10.2.0"}),
        Map.entry("go",         new String[]{"go",         "1.16.2"}),
        Map.entry("rust",       new String[]{"rust",       "1.50.0"}),
        Map.entry("ruby",       new String[]{"ruby",       "3.0.1"}),
        Map.entry("php",        new String[]{"php",        "8.2.3"}),
        Map.entry("kotlin",     new String[]{"kotlin",     "1.8.20"}),
        Map.entry("r",          new String[]{"r",          "4.1.1"}),
        Map.entry("swift",      new String[]{"swift",      "5.3.3"}),
        Map.entry("csharp",     new String[]{"csharp",     "6.12.0"})
    );

    @Override
    public SandboxResult execute(String language, String sourceCode, String stdin) {
        String lang = language.toLowerCase();

        if (!LANGUAGE_MAP.containsKey(lang)) {
            return SandboxResult.error("Unsupported language: " + language);
        }

        String[] langVersion = LANGUAGE_MAP.get(lang);
        long start = System.currentTimeMillis();

        try {
            // Build Piston request body
            Map<String, Object> requestBody = Map.of(
                "language", langVersion[0],
                "version",  langVersion[1],
                "files",    List.of(Map.of(
                    "name",    getFileName(lang),
                    "content", sourceCode
                )),
                "stdin",    stdin != null ? stdin : "",
                "run_timeout", maxExecutionSeconds * 1000  // Piston accepts ms
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<PistonResponse> response = restTemplate.exchange(
                pistonUrl + "/execute",
                HttpMethod.POST,
                entity,
                PistonResponse.class
            );

            long execTimeMs = System.currentTimeMillis() - start;

            if (response.getBody() == null) {
                return SandboxResult.error("Empty response from Piston API");
            }

            PistonResponse piston = response.getBody();
            PistonResponse.RunResult run = piston.getRun();

            if (run == null) {
                return SandboxResult.error("No run result in Piston response");
            }

            String stdout = run.getStdout() != null ? run.getStdout() : "";
            String stderr = run.getStderr() != null ? run.getStderr() : "";
            int    exitCode = run.getCode() != null ? run.getCode() : -1;

            // Piston signals TLE via signal="SIGKILL" and code=null or via stderr
            boolean timedOut = "SIGKILL".equals(run.getSignal()) ||
                               stderr.contains("Time limit exceeded");

            ExecutionJob.Status status;
            if (timedOut) {
                status = ExecutionJob.Status.TIMED_OUT;
                stderr = "Execution timed out after " + maxExecutionSeconds + " seconds";
            } else if (exitCode == 0) {
                status = ExecutionJob.Status.COMPLETED;
            } else {
                status = ExecutionJob.Status.FAILED;
            }

            log.info("Piston executed [lang={} v={} exitCode={} timeMs={}]",
                    langVersion[0], langVersion[1], exitCode, execTimeMs);

            return SandboxResult.builder()
                    .stdout(stdout)
                    .stderr(stderr)
                    .exitCode(exitCode)
                    .executionTimeMs(execTimeMs)
                    .memoryUsedKb(0L)   // Piston doesn't report memory usage
                    .status(status)
                    .build();

        } catch (Exception e) {
            log.error("Piston API call failed: {}", e.getMessage(), e);
            return SandboxResult.error("Piston API error: " + e.getMessage());
        }
    }

    @Override
    public List<String> getSupportedLanguages() {
        return List.copyOf(LANGUAGE_MAP.keySet());
    }

    // ── Piston response DTOs ──────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PistonResponse {
        private String    language;
        private String    version;
        private RunResult run;
        private RunResult compile;  // present for compiled languages

        public String    getLanguage() { return language; }
        public String    getVersion()  { return version; }
        public RunResult getRun()      { return run; }
        public RunResult getCompile()  { return compile; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RunResult {
            private String  stdout;
            private String  stderr;
            private String  output;   // combined stdout+stderr in some Piston versions
            private Integer code;
            private String  signal;   // e.g. "SIGKILL" on TLE

            public String  getStdout() { return stdout; }
            public String  getStderr() { return stderr; }
            public String  getOutput() { return output; }
            public Integer getCode()   { return code; }
            public String  getSignal() { return signal; }
        }
    }

    // ── File name helper ──────────────────────────────────────────────────────

    private String getFileName(String lang) {
        return switch (lang) {
            case "java"       -> "Main.java";
            case "python"     -> "main.py";
            case "javascript" -> "main.js";
            case "typescript" -> "main.ts";
            case "c"          -> "main.c";
            case "cpp"        -> "main.cpp";
            case "go"         -> "main.go";
            case "rust"       -> "main.rs";
            case "ruby"       -> "main.rb";
            case "php"        -> "main.php";
            case "kotlin"     -> "main.kt";
            case "r"          -> "main.r";
            case "swift"      -> "main.swift";
            case "csharp"     -> "main.cs";
            default           -> "main.txt";
        };
    }
}
