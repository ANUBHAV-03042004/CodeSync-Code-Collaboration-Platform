package com.projectservice.codesync.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long projectId;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility = Visibility.PUBLIC;

    private Long templateId;

    private boolean isArchived = false;

    private int starCount = 0;

    private int forkCount = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "project_members",
            joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "user_id")
    private Set<Long> memberIds = new HashSet<>();

    public enum Visibility { PUBLIC, PRIVATE }

    // ── No-arg constructor (required by JPA) ──────────────────────────────────

    public Project() {}

    // ── All-arg constructor ───────────────────────────────────────────────────

    public Project(Long projectId, Long ownerId, String name, String description,
                   String language, Visibility visibility, Long templateId,
                   boolean isArchived, int starCount, int forkCount,
                   LocalDateTime createdAt, LocalDateTime updatedAt,
                   Set<Long> memberIds) {
        this.projectId   = projectId;
        this.ownerId     = ownerId;
        this.name        = name;
        this.description = description;
        this.language    = language;
        this.visibility  = visibility != null ? visibility : Visibility.PUBLIC;
        this.templateId  = templateId;
        this.isArchived  = isArchived;
        this.starCount   = starCount;
        this.forkCount   = forkCount;
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
        this.memberIds   = memberIds != null ? memberIds : new HashSet<>();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getProjectId()          { return projectId; }
    public Long getOwnerId()            { return ownerId; }
    public String getName()             { return name; }
    public String getDescription()      { return description; }
    public String getLanguage()         { return language; }
    public Visibility getVisibility()   { return visibility; }
    public Long getTemplateId()         { return templateId; }
    public boolean isArchived()         { return isArchived; }
    public int getStarCount()           { return starCount; }
    public int getForkCount()           { return forkCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Set<Long> getMemberIds()     { return memberIds; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setProjectId(Long projectId)          { this.projectId   = projectId; }
    public void setOwnerId(Long ownerId)              { this.ownerId     = ownerId; }
    public void setName(String name)                  { this.name        = name; }
    public void setDescription(String description)    { this.description = description; }
    public void setLanguage(String language)          { this.language    = language; }
    public void setVisibility(Visibility visibility)  { this.visibility  = visibility; }
    public void setTemplateId(Long templateId)        { this.templateId  = templateId; }
    public void setArchived(boolean archived)         { this.isArchived  = archived; }
    public void setStarCount(int starCount)           { this.starCount   = starCount; }
    public void setForkCount(int forkCount)           { this.forkCount   = forkCount; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt   = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt   = updatedAt; }
    public void setMemberIds(Set<Long> memberIds)     { this.memberIds   = memberIds; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {

        private Long projectId;
        private Long ownerId;
        private String name;
        private String description;
        private String language;
        private Visibility visibility = Visibility.PUBLIC;
        private Long templateId;
        private boolean isArchived    = false;
        private int starCount         = 0;
        private int forkCount         = 0;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Set<Long> memberIds   = new HashSet<>();

        private Builder() {}

        public Builder projectId(Long v)        { this.projectId   = v; return this; }
        public Builder ownerId(Long v)          { this.ownerId     = v; return this; }
        public Builder name(String v)           { this.name        = v; return this; }
        public Builder description(String v)    { this.description = v; return this; }
        public Builder language(String v)       { this.language    = v; return this; }
        public Builder visibility(Visibility v) { this.visibility  = v; return this; }
        public Builder templateId(Long v)       { this.templateId  = v; return this; }
        public Builder isArchived(boolean v)    { this.isArchived  = v; return this; }
        public Builder starCount(int v)         { this.starCount   = v; return this; }
        public Builder forkCount(int v)         { this.forkCount   = v; return this; }
        public Builder memberIds(Set<Long> v)   { this.memberIds   = v; return this; }

        public Project build() {
            return new Project(projectId, ownerId, name, description, language,
                    visibility, templateId, isArchived, starCount, forkCount,
                    createdAt, updatedAt, memberIds);
        }
    }

    @Override
    public String toString() {
        return "Project{projectId=" + projectId +
                ", name='" + name + '\'' +
                ", ownerId=" + ownerId +
                ", language='" + language + '\'' +
                ", visibility=" + visibility + '}';
    }
}
