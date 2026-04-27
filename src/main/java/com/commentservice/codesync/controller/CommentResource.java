package com.commentservice.codesync.controller;

import com.commentservice.codesync.entity.Comment;
import com.commentservice.codesync.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * FIX: Removed @RequiredArgsConstructor → explicit constructor.
 */
@RestController
@RequestMapping("/api/v1/comments")
@Tag(name = "Comments", description = "File and project comment management — add, reply, resolve, delete")
public class CommentResource {

    private final CommentService commentService;

    // FIX: explicit constructor — @RequiredArgsConstructor was not generating
    public CommentResource(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "Add a comment to a file",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Comment created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PostMapping
    public ResponseEntity<Comment> add(
            @Parameter(description = "Authenticated user ID from gateway header")
            @RequestHeader("X-User-Id") String userId,
            @RequestBody AddCommentReq req) {
        Comment c = commentService.addComment(
                req.projectId(), req.fileId(), Long.parseLong(userId),
                req.content(), req.lineNumber(), req.columnNumber(),
                req.parentCommentId(), req.snapshotId());
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    @Operation(summary = "Get all comments for a file",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Comments returned ordered by line number")
    @GetMapping("/file/{fileId}")
    public ResponseEntity<List<Comment>> getByFile(@PathVariable Long fileId) {
        return ResponseEntity.ok(commentService.getByFile(fileId));
    }

    @Operation(summary = "Get all comments for a project",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Comment>> getByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(commentService.getByProject(projectId));
    }

    @Operation(summary = "Get a comment by ID",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comment found"),
        @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Comment> getById(@PathVariable Long id) {
        return commentService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get all replies to a comment",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/{id}/replies")
    public ResponseEntity<List<Comment>> getReplies(@PathVariable Long id) {
        return ResponseEntity.ok(commentService.getReplies(id));
    }

    @Operation(summary = "Get comments at a specific line",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/file/{fileId}/line/{line}")
    public ResponseEntity<List<Comment>> getByLine(@PathVariable Long fileId,
                                                    @PathVariable int line) {
        return ResponseEntity.ok(commentService.getByLine(fileId, line));
    }

    @Operation(summary = "Count comments in a file",
               security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/file/{fileId}/count")
    public ResponseEntity<Map<String, Integer>> getCount(@PathVariable Long fileId) {
        return ResponseEntity.ok(Map.of("count", commentService.getCommentCount(fileId)));
    }

    @Operation(summary = "Update a comment's content",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}")
    public ResponseEntity<Comment> update(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                commentService.updateComment(id, Long.parseLong(userId), body.get("content")));
    }

    @Operation(summary = "Delete a comment",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userId) {
        commentService.deleteComment(id, Long.parseLong(userId));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark a comment as resolved",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}/resolve")
    public ResponseEntity<Comment> resolve(@PathVariable Long id) {
        return ResponseEntity.ok(commentService.resolveComment(id));
    }

    @Operation(summary = "Mark a comment as unresolved",
               security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/{id}/unresolve")
    public ResponseEntity<Comment> unresolve(@PathVariable Long id) {
        return ResponseEntity.ok(commentService.unresolveComment(id));
    }

    record AddCommentReq(
            Long    projectId,
            Long    fileId,
            String  content,
            int     lineNumber,
            Integer columnNumber,
            Long    parentCommentId,
            Long    snapshotId) {}
}
