package com.commentservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * FIX: All Lombok removed. Manual builder replaces @Builder.
 * Errors fixed:
 *  - "The method builder() is undefined for the type Comment" — @Builder not processing
 *  - @Builder.Default on 'resolved' not working without Lombok APT
 */
@Entity
@Table(name = "comments", indexes = {
    @Index(name = "idx_comment_file",    columnList = "fileId"),
    @Index(name = "idx_comment_project", columnList = "projectId"),
    @Index(name = "idx_comment_parent",  columnList = "parentCommentId")
})
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long fileId;

    @Column(nullable = false)
    private Long authorId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private int lineNumber;

    private Integer columnNumber;
    private Long    parentCommentId;
    private boolean resolved = false;
    private Long    snapshotId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Comment() {}

    private Comment(Builder b) {
        this.commentId       = b.commentId;
        this.projectId       = b.projectId;
        this.fileId          = b.fileId;
        this.authorId        = b.authorId;
        this.content         = b.content;
        this.lineNumber      = b.lineNumber;
        this.columnNumber    = b.columnNumber;
        this.parentCommentId = b.parentCommentId;
        this.resolved        = b.resolved;
        this.snapshotId      = b.snapshotId;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long    commentId;
        private Long    projectId;
        private Long    fileId;
        private Long    authorId;
        private String  content;
        private int     lineNumber;
        private Integer columnNumber;
        private Long    parentCommentId;
        private boolean resolved = false;
        private Long    snapshotId;

        public Builder commentId(Long v)       { this.commentId       = v; return this; }
        public Builder projectId(Long v)       { this.projectId       = v; return this; }
        public Builder fileId(Long v)          { this.fileId          = v; return this; }
        public Builder authorId(Long v)        { this.authorId        = v; return this; }
        public Builder content(String v)       { this.content         = v; return this; }
        public Builder lineNumber(int v)       { this.lineNumber      = v; return this; }
        public Builder columnNumber(Integer v) { this.columnNumber    = v; return this; }
        public Builder parentCommentId(Long v) { this.parentCommentId = v; return this; }
        public Builder resolved(boolean v)     { this.resolved        = v; return this; }
        public Builder snapshotId(Long v)      { this.snapshotId      = v; return this; }

        public Comment build() { return new Comment(this); }
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long          getCommentId()                { return commentId; }
    public void          setCommentId(Long v)          { this.commentId = v; }

    public Long          getProjectId()                { return projectId; }
    public void          setProjectId(Long v)          { this.projectId = v; }

    public Long          getFileId()                   { return fileId; }
    public void          setFileId(Long v)             { this.fileId = v; }

    public Long          getAuthorId()                 { return authorId; }
    public void          setAuthorId(Long v)           { this.authorId = v; }

    public String        getContent()                  { return content; }
    public void          setContent(String v)          { this.content = v; }

    public int           getLineNumber()               { return lineNumber; }
    public void          setLineNumber(int v)          { this.lineNumber = v; }

    public Integer       getColumnNumber()             { return columnNumber; }
    public void          setColumnNumber(Integer v)    { this.columnNumber = v; }

    public Long          getParentCommentId()          { return parentCommentId; }
    public void          setParentCommentId(Long v)    { this.parentCommentId = v; }

    public boolean       isResolved()                  { return resolved; }
    public void          setResolved(boolean v)        { this.resolved = v; }

    public Long          getSnapshotId()               { return snapshotId; }
    public void          setSnapshotId(Long v)         { this.snapshotId = v; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void          setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public LocalDateTime getUpdatedAt()                { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
