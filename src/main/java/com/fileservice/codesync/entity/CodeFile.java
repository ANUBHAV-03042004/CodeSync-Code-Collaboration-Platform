package com.fileservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_files",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "path"}))
public class CodeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    @Column(nullable = false, name = "project_id")
    private Long projectId;

    @Column(nullable = false)
    private String name;

    /** Full path, e.g. "src/main/App.java" */
    @Column(nullable = false, name = "path")
    private String path;

    private String language;

    /** Null for folders, full text for files */
    @Column(columnDefinition = "TEXT")
    private String content;

    private Long size;

    @Column(nullable = false)
    private Long createdById;

    private Long lastEditedBy;

    private boolean isFolder = false;

    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ── No-arg constructor (required by JPA) ─────────────────────────────────

    public CodeFile() {}

    // ── All-arg constructor ───────────────────────────────────────────────────

    public CodeFile(Long fileId, Long projectId, String name, String path,
                    String language, String content, Long size,
                    Long createdById, Long lastEditedBy,
                    boolean isFolder, boolean isDeleted,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.fileId       = fileId;
        this.projectId    = projectId;
        this.name         = name;
        this.path         = path;
        this.language     = language;
        this.content      = content;
        this.size         = size;
        this.createdById  = createdById;
        this.lastEditedBy = lastEditedBy;
        this.isFolder     = isFolder;
        this.isDeleted    = isDeleted;
        this.createdAt    = createdAt;
        this.updatedAt    = updatedAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getFileId()            { return fileId; }
    public Long getProjectId()         { return projectId; }
    public String getName()            { return name; }
    public String getPath()            { return path; }
    public String getLanguage()        { return language; }
    public String getContent()         { return content; }
    public Long getSize()              { return size; }
    public Long getCreatedById()       { return createdById; }
    public Long getLastEditedBy()      { return lastEditedBy; }
    public boolean isFolder()          { return isFolder; }
    public boolean isDeleted()         { return isDeleted; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setFileId(Long fileId)                  { this.fileId       = fileId; }
    public void setProjectId(Long projectId)            { this.projectId    = projectId; }
    public void setName(String name)                    { this.name         = name; }
    public void setPath(String path)                    { this.path         = path; }
    public void setLanguage(String language)            { this.language     = language; }
    public void setContent(String content)              { this.content      = content; }
    public void setSize(Long size)                      { this.size         = size; }
    public void setCreatedById(Long createdById)        { this.createdById  = createdById; }
    public void setLastEditedBy(Long lastEditedBy)      { this.lastEditedBy = lastEditedBy; }
    public void setFolder(boolean folder)               { this.isFolder     = folder; }
    public void setDeleted(boolean deleted)             { this.isDeleted    = deleted; }
    public void setCreatedAt(LocalDateTime createdAt)   { this.createdAt    = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt)   { this.updatedAt    = updatedAt; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long fileId;
        private Long projectId;
        private String name;
        private String path;
        private String language;
        private String content;
        private Long size;
        private Long createdById;
        private Long lastEditedBy;
        private boolean isFolder  = false;
        private boolean isDeleted = false;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private Builder() {}

        public Builder fileId(Long v)            { this.fileId       = v; return this; }
        public Builder projectId(Long v)         { this.projectId    = v; return this; }
        public Builder name(String v)            { this.name         = v; return this; }
        public Builder path(String v)            { this.path         = v; return this; }
        public Builder language(String v)        { this.language     = v; return this; }
        public Builder content(String v)         { this.content      = v; return this; }
        public Builder size(Long v)              { this.size         = v; return this; }
        public Builder createdById(Long v)       { this.createdById  = v; return this; }
        public Builder lastEditedBy(Long v)      { this.lastEditedBy = v; return this; }
        public Builder isFolder(boolean v)       { this.isFolder     = v; return this; }
        public Builder isDeleted(boolean v)      { this.isDeleted    = v; return this; }

        public CodeFile build() {
            return new CodeFile(fileId, projectId, name, path, language, content,
                    size, createdById, lastEditedBy, isFolder, isDeleted,
                    createdAt, updatedAt);
        }
    }

    @Override
    public String toString() {
        return "CodeFile{fileId=" + fileId + ", projectId=" + projectId +
                ", name='" + name + "', path='" + path + "', isFolder=" + isFolder + "}";
    }
}
