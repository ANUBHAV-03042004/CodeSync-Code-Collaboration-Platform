package com.fileservice.codesync.controller;

import com.fileservice.codesync.entity.CodeFile;
import com.fileservice.codesync.security.JwtUserExtractor;
import com.fileservice.codesync.service.FileServiceImpl;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files", description = "Code file and folder management within projects")
public class FileResource {

    private final FileServiceImpl fileService;
    private final JwtUserExtractor jwtUserExtractor;

    public FileResource(FileServiceImpl fileService, JwtUserExtractor jwtUserExtractor) {
        this.fileService       = fileService;
        this.jwtUserExtractor  = jwtUserExtractor;
    }

    private Long resolveUser(String xUserId, String authorization) {
        return jwtUserExtractor.resolveUserId(xUserId, authorization);
    }

    // ── Create file ───────────────────────────────────────────────────────────

    @Operation(summary = "Create a file",
               description = "Creates a new code file inside a project. Path must be unique per project.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "File created",
            content = @Content(schema = @Schema(implementation = CodeFile.class))),
        @ApiResponse(responseCode = "400", description = "File already exists at path", content = @Content),
        @ApiResponse(responseCode = "401", description = "Missing authentication", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> create(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id",    required = false) String xUserId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody CreateFileReq req) {
        Long userId = resolveUser(xUserId, auth);
        if (userId == null) return unauthorized();
        CodeFile f = fileService.createFile(req.projectId(), req.name(), req.path(),
                req.language(), req.content(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(f);
    }

    // ── Create folder ─────────────────────────────────────────────────────────

    @Operation(summary = "Create a folder",
               description = "Creates a new folder entry inside a project.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Folder created",
            content = @Content(schema = @Schema(implementation = CodeFile.class))),
        @ApiResponse(responseCode = "401", description = "Missing authentication", content = @Content)
    })
    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id",    required = false) String xUserId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody CreateFolderReq req) {
        Long userId = resolveUser(xUserId, auth);
        if (userId == null) return unauthorized();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileService.createFolder(req.projectId(), req.name(), req.path(), userId));
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get file by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found",
            content = @Content(schema = @Schema(implementation = CodeFile.class))),
        @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<CodeFile> getById(
            @Parameter(description = "File ID", example = "1") @PathVariable Long id) {
        return fileService.getFileById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Get files by project ──────────────────────────────────────────────────

    @Operation(summary = "Get all non-deleted files in a project")
    @ApiResponse(responseCode = "200", description = "File list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = CodeFile.class))))
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<CodeFile>> getByProject(
            @Parameter(description = "Project ID", example = "1") @PathVariable Long projectId) {
        return ResponseEntity.ok(fileService.getFilesByProject(projectId));
    }

    // ── File tree ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get file tree for a project",
               description = "Returns all non-deleted files and folders — client builds the tree from paths.")
    @ApiResponse(responseCode = "200", description = "File tree",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = CodeFile.class))))
    @GetMapping("/project/{projectId}/tree")
    public ResponseEntity<List<CodeFile>> getTree(
            @Parameter(description = "Project ID", example = "1") @PathVariable Long projectId) {
        return ResponseEntity.ok(fileService.getFileTree(projectId));
    }

    // ── Get content ───────────────────────────────────────────────────────────

    @Operation(summary = "Get file content",
               description = "Returns the raw text content of a file.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Content returned"),
        @ApiResponse(responseCode = "404", description = "File not found or deleted", content = @Content)
    })
    @GetMapping("/{id}/content")
    public ResponseEntity<Map<String, String>> getContent(
            @Parameter(description = "File ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("content", fileService.getFileContent(id)));
    }

    // ── Update content ────────────────────────────────────────────────────────

    @Operation(summary = "Update file content",
               description = "Replaces the entire content of a file and records the editor.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Content updated",
            content = @Content(schema = @Schema(implementation = CodeFile.class))),
        @ApiResponse(responseCode = "401", description = "Missing authentication", content = @Content),
        @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @PutMapping("/{id}/content")
    public ResponseEntity<?> updateContent(
            @Parameter(description = "File ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id",    required = false) String xUserId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, String> body) {
        Long userId = resolveUser(xUserId, auth);
        if (userId == null) return unauthorized();
        return ResponseEntity.ok(fileService.updateFileContent(id, body.get("content"), userId));
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    @Operation(summary = "Rename a file or folder",
               description = "Updates the name and adjusts the path of the last segment.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Renamed",
            content = @Content(schema = @Schema(implementation = CodeFile.class))),
        @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @PutMapping("/{id}/rename")
    public ResponseEntity<CodeFile> rename(
            @Parameter(description = "File ID") @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(fileService.renameFile(id, body.get("name")));
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    @Operation(summary = "Move a file to a new path")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Moved",
            content = @Content(schema = @Schema(implementation = CodeFile.class))),
        @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @PutMapping("/{id}/move")
    public ResponseEntity<CodeFile> move(
            @Parameter(description = "File ID") @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(fileService.moveFile(id, body.get("path")));
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @Operation(summary = "Soft-delete a file",
               description = "Marks the file as deleted. Use /restore to undo.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "File ID") @PathVariable Long id) {
        fileService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    @Operation(summary = "Restore a soft-deleted file")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Restored",
            content = @Content(schema = @Schema(implementation = CodeFile.class))),
        @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @PostMapping("/{id}/restore")
    public ResponseEntity<CodeFile> restore(
            @Parameter(description = "File ID") @PathVariable Long id) {
        return ResponseEntity.ok(fileService.restoreFile(id));
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Operation(summary = "Search files in a project",
               description = "Case-insensitive match on file name or content.")
    @ApiResponse(responseCode = "200", description = "Matching files",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = CodeFile.class))))
    @GetMapping("/project/{projectId}/search")
    public ResponseEntity<List<CodeFile>> search(
            @Parameter(description = "Project ID") @PathVariable Long projectId,
            @Parameter(description = "Search term", example = "main") @RequestParam String q) {
        return ResponseEntity.ok(fileService.searchInProject(projectId, q));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Missing authentication: provide Authorization: Bearer <token>");
    }

    // ── Request records ───────────────────────────────────────────────────────

    @Schema(description = "Request body for creating a file")
    record CreateFileReq(
            @Schema(description = "Project ID",  example = "1")              Long   projectId,
            @Schema(description = "File name",   example = "Main.java")       String name,
            @Schema(description = "Full path",   example = "src/Main.java")   String path,
            @Schema(description = "Language",    example = "Java")            String language,
            @Schema(description = "File content",example = "public class Main {}") String content
    ) {}

    @Schema(description = "Request body for creating a folder")
    record CreateFolderReq(
            @Schema(description = "Project ID", example = "1")            Long   projectId,
            @Schema(description = "Folder name",example = "src")          String name,
            @Schema(description = "Full path",  example = "src")          String path
    ) {}
}
