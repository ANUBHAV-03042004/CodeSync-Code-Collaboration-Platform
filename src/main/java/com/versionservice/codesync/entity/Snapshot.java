package com.versionservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a point-in-time capture of a file's content — analogous to a Git commit.
 * Snapshots form a linked DAG via parentSnapshotId.
 * No Lombok — all getters, setters, and builder implemented manually.
 */
@Entity
@Table(name = "snapshots",
       indexes = {
           @Index(name = "idx_snap_file",    columnList = "fileId"),
           @Index(name = "idx_snap_project", columnList = "projectId"),
           @Index(name = "idx_snap_branch",  columnList = "branch")
       })
public class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long fileId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private String message;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false, length = 64)
    private String hash;

    private Long parentSnapshotId;

    @Column(nullable = false)
    private String branch = "main";

    private String tag;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Snapshot() {}

    private Snapshot(Builder b) {
        this.snapshotId       = b.snapshotId;
        this.projectId        = b.projectId;
        this.fileId           = b.fileId;
        this.authorId         = b.authorId;
        this.message          = b.message;
        this.content          = b.content;
        this.hash             = b.hash;
        this.parentSnapshotId = b.parentSnapshotId;
        this.branch           = b.branch != null ? b.branch : "main";
        this.tag              = b.tag;
        this.createdAt        = b.createdAt;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long          snapshotId;
        private Long          projectId;
        private Long          fileId;
        private Long          authorId;
        private String        message;
        private String        content;
        private String        hash;
        private Long          parentSnapshotId;
        private String        branch = "main";
        private String        tag;
        private LocalDateTime createdAt;

        private Builder() {}

        public Builder snapshotId(Long v)       { this.snapshotId = v;       return this; }
        public Builder projectId(Long v)        { this.projectId = v;        return this; }
        public Builder fileId(Long v)           { this.fileId = v;           return this; }
        public Builder authorId(Long v)         { this.authorId = v;         return this; }
        public Builder message(String v)        { this.message = v;          return this; }
        public Builder content(String v)        { this.content = v;          return this; }
        public Builder hash(String v)           { this.hash = v;             return this; }
        public Builder parentSnapshotId(Long v) { this.parentSnapshotId = v; return this; }
        public Builder branch(String v)         { this.branch = v;           return this; }
        public Builder tag(String v)            { this.tag = v;              return this; }
        public Builder createdAt(LocalDateTime v){ this.createdAt = v;       return this; }

        public Snapshot build()                 { return new Snapshot(this); }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long          getSnapshotId()       { return snapshotId; }
    public Long          getProjectId()        { return projectId; }
    public Long          getFileId()           { return fileId; }
    public Long          getAuthorId()         { return authorId; }
    public String        getMessage()          { return message; }
    public String        getContent()          { return content; }
    public String        getHash()             { return hash; }
    public Long          getParentSnapshotId() { return parentSnapshotId; }
    public String        getBranch()           { return branch; }
    public String        getTag()              { return tag; }
    public LocalDateTime getCreatedAt()        { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setSnapshotId(Long v)       { this.snapshotId = v; }
    public void setProjectId(Long v)        { this.projectId = v; }
    public void setFileId(Long v)           { this.fileId = v; }
    public void setAuthorId(Long v)         { this.authorId = v; }
    public void setMessage(String v)        { this.message = v; }
    public void setContent(String v)        { this.content = v; }
    public void setHash(String v)           { this.hash = v; }
    public void setParentSnapshotId(Long v) { this.parentSnapshotId = v; }
    public void setBranch(String v)         { this.branch = v; }
    public void setTag(String v)            { this.tag = v; }
    public void setCreatedAt(LocalDateTime v){ this.createdAt = v; }

    @Override
    public String toString() {
        return "Snapshot{snapshotId=" + snapshotId +
               ", fileId=" + fileId +
               ", branch='" + branch + '\'' +
               ", message='" + message + '\'' + '}';
    }
}