package com.projectservice.codesync.controller;

import com.projectservice.codesync.entity.Project;
import com.projectservice.codesync.security.JwtUserExtractor;
import com.projectservice.codesync.service.ProjectServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects",
     description = "Project creation, discovery, forking, starring and member management")
public class ProjectResource {

    private final ProjectServiceImpl projectService;
    private final JwtUserExtractor jwtUserExtractor;

    public ProjectResource(ProjectServiceImpl projectService, JwtUserExtractor jwtUserExtractor) {
        this.projectService    = projectService;
        this.jwtUserExtractor  = jwtUserExtractor;
    }

    /**
     * Resolves the caller's userId from:
     * 1. X-User-Id header  — injected by the API Gateway after JWT validation
     * 2. Authorization: Bearer <token> — used when calling the service directly
     *    (e.g. Swagger UI, integration tests bypassing the gateway)
     */
    private Long currentUserId(String xUserId, String authorization) {
        return jwtUserExtractor.resolveUserId(xUserId, authorization);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Create a project",
        description = "Creates a new project owned by the authenticated user. " +
                      "The owner is automatically added as a member."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Project created",
            content = @Content(schema = @Schema(implementation = Project.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request body or missing auth",
            content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> create(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id",     required = false) String xUserId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization",  required = false) String authorization,
            @RequestBody CreateProjectRequest req) {

        Long userId = currentUserId(xUserId, authorization);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing authentication: provide Authorization: Bearer <token>");
        }
        Project p = projectService.createProject(
                userId, req.name(), req.description(),
                req.language(), Project.Visibility.valueOf(req.visibility()));
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get project by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project found",
            content = @Content(schema = @Schema(implementation = Project.class))),
        @ApiResponse(responseCode = "404", description = "Project not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<Project> getById(
            @Parameter(description = "Project ID", example = "1") @PathVariable Long id) {
        return projectService.getProjectById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Get by owner ──────────────────────────────────────────────────────────

    @Operation(summary = "Get all projects owned by a user")
    @ApiResponse(responseCode = "200", description = "List of projects",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = Project.class))))
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<Project>> getByOwner(
            @Parameter(description = "Owner user ID", example = "1") @PathVariable Long ownerId) {
        return ResponseEntity.ok(projectService.getProjectsByOwner(ownerId));
    }

    // ── Public feed ───────────────────────────────────────────────────────────

    @Operation(
        summary = "Get public projects",
        description = "Returns all non-archived PUBLIC projects ordered by star count descending."
    )
    @ApiResponse(responseCode = "200", description = "Public project feed",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = Project.class))))
    @GetMapping("/public")
    public ResponseEntity<List<Project>> getPublic() {
        return ResponseEntity.ok(projectService.getPublicProjects());
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Operation(summary = "Search projects by name",
               description = "Case-insensitive partial match on project name.")
    @ApiResponse(responseCode = "200", description = "Matching projects",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = Project.class))))
    @GetMapping("/search")
    public ResponseEntity<List<Project>> search(
            @Parameter(description = "Search term", example = "codesync") @RequestParam String q) {
        return ResponseEntity.ok(projectService.searchProjects(q));
    }

    // ── By member ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get projects a user is a member of")
    @ApiResponse(responseCode = "200", description = "Projects the user belongs to",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = Project.class))))
    @GetMapping("/member/{userId}")
    public ResponseEntity<List<Project>> getByMember(
            @Parameter(description = "User ID", example = "1") @PathVariable Long userId) {
        return ResponseEntity.ok(projectService.getProjectsByMember(userId));
    }

    // ── By language ───────────────────────────────────────────────────────────

    @Operation(summary = "Get projects by programming language")
    @ApiResponse(responseCode = "200", description = "Projects using the language",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = Project.class))))
    @GetMapping("/language/{lang}")
    public ResponseEntity<List<Project>> getByLanguage(
            @Parameter(description = "Language name", example = "Java") @PathVariable String lang) {
        return ResponseEntity.ok(projectService.getProjectsByLanguage(lang));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Operation(summary = "Update a project",
               description = "Partial update — only non-null fields are applied.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project updated",
            content = @Content(schema = @Schema(implementation = Project.class))),
        @ApiResponse(responseCode = "404", description = "Project not found", content = @Content)
    })
    @PutMapping("/{id}")
    public ResponseEntity<Project> update(
            @Parameter(description = "Project ID", example = "1") @PathVariable Long id,
            @RequestBody UpdateProjectRequest req) {
        Project.Visibility vis = req.visibility() != null
                ? Project.Visibility.valueOf(req.visibility()) : null;
        return ResponseEntity.ok(
                projectService.updateProject(id, req.name(), req.description(),
                        req.language(), vis));
    }

    // ── Archive ───────────────────────────────────────────────────────────────

    @Operation(summary = "Archive a project",
               description = "Marks project as archived. It disappears from the public feed.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Archived"),
        @ApiResponse(responseCode = "404", description = "Project not found", content = @Content)
    })
    @PutMapping("/{id}/archive")
    public ResponseEntity<Void> archive(
            @Parameter(description = "Project ID") @PathVariable Long id) {
        projectService.archiveProject(id);
        return ResponseEntity.noContent().build();
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Operation(summary = "Delete a project",
               description = "Permanently deletes the project and its member list.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Project not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Project ID") @PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    // ── Fork ──────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Fork a project",
        description = "Creates a PRIVATE copy for the authenticated user and " +
                      "increments the source project's fork count."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Forked project created",
            content = @Content(schema = @Schema(implementation = Project.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "404", description = "Source project not found", content = @Content)
    })
    @PostMapping("/{id}/fork")
    public ResponseEntity<?> fork(
            @Parameter(description = "Source project ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id",    required = false) String xUserId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization) {

        Long userId = currentUserId(xUserId, authorization);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing authentication: provide Authorization: Bearer <token>");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.forkProject(id, userId));
    }

    // ── Star ──────────────────────────────────────────────────────────────────

    @Operation(summary = "Star a project",
               description = "Increments the star count by 1.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Starred"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content),
        @ApiResponse(responseCode = "404", description = "Project not found", content = @Content)
    })
    @PostMapping("/{id}/star")
    public ResponseEntity<?> star(
            @Parameter(description = "Project ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id",    required = false) String xUserId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorization) {

        Long userId = currentUserId(xUserId, authorization);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing authentication: provide Authorization: Bearer <token>");
        }
        projectService.starProject(id, userId);
        return ResponseEntity.ok().build();
    }

    // ── Members ───────────────────────────────────────────────────────────────

    @Operation(summary = "Add a collaborator to a project")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Member added",
            content = @Content(schema = @Schema(implementation = Project.class))),
        @ApiResponse(responseCode = "404", description = "Project not found", content = @Content)
    })
    @PostMapping("/{id}/members/{memberId}")
    public ResponseEntity<Project> addMember(
            @Parameter(description = "Project ID") @PathVariable Long id,
            @Parameter(description = "User ID to add") @PathVariable Long memberId) {
        return ResponseEntity.ok(projectService.addMember(id, memberId));
    }

    @Operation(summary = "Remove a collaborator from a project")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Member removed",
            content = @Content(schema = @Schema(implementation = Project.class))),
        @ApiResponse(responseCode = "404", description = "Project not found", content = @Content)
    })
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Project> removeMember(
            @Parameter(description = "Project ID") @PathVariable Long id,
            @Parameter(description = "User ID to remove") @PathVariable Long memberId) {
        return ResponseEntity.ok(projectService.removeMember(id, memberId));
    }

    // ── Request records ───────────────────────────────────────────────────────

    @Schema(description = "Request body for creating a project")
    record CreateProjectRequest(
            @Schema(description = "Project name",   example = "my-api")  String name,
            @Schema(description = "Description",    example = "REST API") String description,
            @Schema(description = "Language",       example = "Java")     String language,
            @Schema(description = "PUBLIC|PRIVATE", example = "PUBLIC")   String visibility
    ) {}

    @Schema(description = "Request body for updating a project — all fields optional")
    record UpdateProjectRequest(
            @Schema(description = "New name")        String name,
            @Schema(description = "New description") String description,
            @Schema(description = "New language")    String language,
            @Schema(description = "New visibility")  String visibility
    ) {}
}
