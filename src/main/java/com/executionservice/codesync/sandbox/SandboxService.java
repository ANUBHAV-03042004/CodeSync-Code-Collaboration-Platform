package com.executionservice.codesync.sandbox;

import java.util.List;

/**
 * Abstraction over code execution backends.
 *
 * Current implementations:
 *  - PistonSandboxService  — calls the Piston API over HTTP (recommended for cloud)
 *  - DockerSandboxService  — spawns local Docker containers (requires Docker daemon)
 *
 * The active implementation is selected by setting sandbox.provider in application.properties:
 *   sandbox.provider=piston   (default — zero Docker dependency)
 *   sandbox.provider=docker   (requires Docker socket access on the host)
 */
public interface SandboxService {

    /**
     * Execute source code and return the result.
     *
     * @param language   lowercase language identifier, e.g. "python", "java"
     * @param sourceCode the source code to execute
     * @param stdin      optional stdin to pipe into the program (may be null)
     * @return SandboxResult with stdout, stderr, exitCode, timing, and status
     */
    SandboxResult execute(String language, String sourceCode, String stdin);

    /**
     * List all language identifiers supported by this backend.
     */
    List<String> getSupportedLanguages();
}
