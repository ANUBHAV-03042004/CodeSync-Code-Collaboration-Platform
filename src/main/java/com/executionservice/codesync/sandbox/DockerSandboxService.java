package com.executionservice.codesync.sandbox;

import com.executionservice.codesync.entity.ExecutionJob;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Executes code in isolated Docker containers.
 *
 * Active when: sandbox.provider=docker
 *
 * Requirements:
 *  - Docker daemon running on the host
 *  - The JVM process must have access to the Docker socket
 *  - On OpenShift this requires a privileged SCC or a sidecar DinD container
 *
 * For cloud deployments without Docker socket access, use PistonSandboxService instead
 * (sandbox.provider=piston — the default).
 *
 * FIX 1: Implements SandboxService interface so it is mockable via the interface in tests.
 * FIX 2: Removed unreachable catch(TimeoutException) — awaitStatusCode() does NOT throw
 *         the checked java.util.concurrent.TimeoutException; it returns null on timeout.
 *         TLE is now detected by checking (exitCode == null) after awaitStatusCode().
 */
@Service
@ConditionalOnProperty(name = "sandbox.provider", havingValue = "docker")
public class DockerSandboxService implements SandboxService {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxService.class);

    @Value("${sandbox.max-execution-seconds:10}")
    private int maxExecutionSeconds;

    @Value("${sandbox.max-memory-mb:256}")
    private int maxMemoryMb;

    @Value("${docker.host:unix:///var/run/docker.sock}")
    private String dockerHost;

    private DockerClient dockerClient;

    private static final Map<String, String> LANGUAGE_IMAGES = Map.ofEntries(
        Map.entry("python",     "python:3.12-alpine"),
        Map.entry("java",       "eclipse-temurin:17-jre-alpine"),
        Map.entry("javascript", "node:20-alpine"),
        Map.entry("typescript", "node:20-alpine"),
        Map.entry("c",          "gcc:13-alpine"),
        Map.entry("cpp",        "gcc:13-alpine"),
        Map.entry("go",         "golang:1.22-alpine"),
        Map.entry("rust",       "rust:1.77-alpine"),
        Map.entry("ruby",       "ruby:3.3-alpine"),
        Map.entry("php",        "php:8.3-cli-alpine"),
        Map.entry("kotlin",     "zenika/kotlin:2.0"),
        Map.entry("r",          "r-base:4.4.0")
    );

    private static final Map<String, String[]> RUN_COMMANDS = Map.ofEntries(
        Map.entry("python",     new String[]{"python3", "/code/Main.py"}),
        Map.entry("java",       new String[]{"sh", "-c", "cd /code && javac Main.java && java Main"}),
        Map.entry("javascript", new String[]{"node", "/code/Main.js"}),
        Map.entry("typescript", new String[]{"sh", "-c", "cd /code && npx ts-node Main.ts"}),
        Map.entry("c",          new String[]{"sh", "-c", "cd /code && gcc Main.c -o main && ./main"}),
        Map.entry("cpp",        new String[]{"sh", "-c", "cd /code && g++ Main.cpp -o main && ./main"}),
        Map.entry("go",         new String[]{"sh", "-c", "cd /code && go run Main.go"}),
        Map.entry("rust",       new String[]{"sh", "-c", "cd /code && rustc Main.rs -o main && ./main"}),
        Map.entry("ruby",       new String[]{"ruby", "/code/Main.rb"}),
        Map.entry("php",        new String[]{"php", "/code/Main.php"}),
        Map.entry("kotlin",     new String[]{"sh", "-c", "cd /code && kotlinc Main.kt -include-runtime -d main.jar && java -jar main.jar"}),
        Map.entry("r",          new String[]{"Rscript", "/code/Main.r"})
    );

    @PostConstruct
    public void init() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost).build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerHost))
                .maxConnections(20)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(maxExecutionSeconds + 5))
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
        log.info("DockerSandboxService initialized. Host: {}", dockerHost);
    }

    @PreDestroy
    public void cleanup() {
        try { dockerClient.close(); } catch (Exception ignored) {}
    }

    @Override
    public SandboxResult execute(String language, String sourceCode, String stdin) {
        String lang = language.toLowerCase();
        if (!LANGUAGE_IMAGES.containsKey(lang)) {
            return SandboxResult.error("Unsupported language: " + language);
        }

        Path   tempDir     = null;
        String containerId = null;
        long   start       = System.currentTimeMillis();

        try {
            // 1. Write source to temp dir
            tempDir = Files.createTempDirectory("codesync-sandbox-");
            Path sourcePath = tempDir.resolve(getFileName(lang));
            Files.writeString(sourcePath, sourceCode, StandardCharsets.UTF_8);
            if (stdin != null && !stdin.isBlank()) {
                Files.writeString(tempDir.resolve("stdin.txt"), stdin, StandardCharsets.UTF_8);
            }

            // 2. HostConfig
            Map<String, String> tmpFs = new HashMap<>();
            tmpFs.put("/tmp", "rw,noexec,nosuid,size=64m");

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withBinds(new Bind(
                            tempDir.toAbsolutePath().toString(),
                            new Volume("/code"),
                            com.github.dockerjava.api.model.AccessMode.ro))
                    .withMemory((long) maxMemoryMb * 1024 * 1024)
                    .withMemorySwap((long) maxMemoryMb * 1024 * 1024)
                    .withCpuPeriod(100_000L)
                    .withCpuQuota(50_000L)
                    .withNetworkMode("none")
                    .withTmpFs(tmpFs);

            String[] cmd = buildCommand(lang, stdin);
            CreateContainerResponse container = dockerClient
                    .createContainerCmd(LANGUAGE_IMAGES.get(lang))
                    .withCmd(cmd)
                    .withHostConfig(hostConfig)
                    .withWorkingDir("/code")
                    .withNetworkDisabled(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();

            // 3. Wait with timeout
            // FIX: awaitStatusCode() returns null on timeout — it does NOT throw
            // java.util.concurrent.TimeoutException (which was an unreachable catch block).
            // TLE is now detected by null exitCode check below.
            Integer exitCode = dockerClient
                    .waitContainerCmd(containerId)
                    .start()
                    .awaitStatusCode(maxExecutionSeconds, TimeUnit.SECONDS);

            long execTimeMs = System.currentTimeMillis() - start;

            // Detect TLE — awaitStatusCode returns null when the wait times out
            if (exitCode == null) {
                log.warn("Sandbox TLE after {}s [lang={}]", maxExecutionSeconds, lang);
                return SandboxResult.builder()
                        .stdout("").stderr("Execution timed out after " + maxExecutionSeconds + " seconds")
                        .exitCode(-1).executionTimeMs((long) maxExecutionSeconds * 1000L)
                        .memoryUsedKb(0L).status(ExecutionJob.Status.TIMED_OUT)
                        .build();
            }

            // 4. Collect logs
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true).withStdErr(true).withFollowStream(false)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Frame>() {
                        @Override public void onNext(Frame frame) {
                            String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
                            if (frame.getStreamType() == StreamType.STDERR) stderr.append(payload);
                            else stdout.append(payload);
                        }
                    }).awaitCompletion();

            return SandboxResult.builder()
                    .stdout(stdout.toString()).stderr(stderr.toString())
                    .exitCode(exitCode).executionTimeMs(execTimeMs).memoryUsedKb(0L)
                    .status(exitCode == 0
                            ? ExecutionJob.Status.COMPLETED
                            : ExecutionJob.Status.FAILED)
                    .build();

        } catch (Exception e) {
            log.error("Sandbox execution error", e);
            return SandboxResult.error(e.getMessage());
        } finally {
            if (containerId != null) {
                try { dockerClient.removeContainerCmd(containerId).withForce(true).exec(); }
                catch (Exception e) { log.warn("Failed to remove container {}: {}", containerId, e.getMessage()); }
            }
            if (tempDir != null) deleteDirectory(tempDir);
        }
    }

    @Override
    public List<String> getSupportedLanguages() {
        return new ArrayList<>(LANGUAGE_IMAGES.keySet());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getFileName(String lang) {
        return switch (lang) {
            case "java"       -> "Main.java";
            case "python"     -> "Main.py";
            case "javascript" -> "Main.js";
            case "typescript" -> "Main.ts";
            case "c"          -> "Main.c";
            case "cpp"        -> "Main.cpp";
            case "go"         -> "Main.go";
            case "rust"       -> "Main.rs";
            case "ruby"       -> "Main.rb";
            case "php"        -> "Main.php";
            case "kotlin"     -> "Main.kt";
            case "r"          -> "Main.r";
            default           -> "Main.txt";
        };
    }

    private String[] buildCommand(String lang, String stdin) {
        String[] base = RUN_COMMANDS.get(lang);
        if (stdin != null && !stdin.isBlank()) {
            return new String[]{"sh", "-c", String.join(" ", base) + " < /code/stdin.txt"};
        }
        return base;
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder())
                 .map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            log.warn("Failed to delete temp dir {}: {}", dir, e.getMessage());
        }
    }
}
