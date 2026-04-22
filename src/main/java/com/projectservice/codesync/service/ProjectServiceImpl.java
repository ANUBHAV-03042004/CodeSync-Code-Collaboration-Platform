package com.projectservice.codesync.service;

import com.projectservice.codesync.entity.Project;
import com.projectservice.codesync.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProjectServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private final ProjectRepository projectRepository;

    // Constructor injection — replaces @RequiredArgsConstructor
    public ProjectServiceImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project createProject(Long ownerId, String name, String description,
                                 String language, Project.Visibility visibility) {
        Project project = Project.builder()
                .ownerId(ownerId)
                .name(name)
                .description(description)
                .language(language)
                .visibility(visibility)
                .build();
        project.getMemberIds().add(ownerId); // owner is always a member
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProjectById(Long projectId) {
        return projectRepository.findById(projectId);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsByOwner(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public List<Project> getPublicProjects() {
        return projectRepository.findTopPublicProjects();
    }

    @Transactional(readOnly = true)
    public List<Project> searchProjects(String query) {
        return projectRepository.searchByName(query);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsByMember(Long userId) {
        return projectRepository.findByMemberUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsByLanguage(String language) {
        return projectRepository.findByLanguage(language);
    }

    public Project updateProject(Long projectId, String name, String description,
                                 String language, Project.Visibility visibility) {
        Project project = findOrThrow(projectId);
        if (name        != null) project.setName(name);
        if (description != null) project.setDescription(description);
        if (language    != null) project.setLanguage(language);
        if (visibility  != null) project.setVisibility(visibility);
        return projectRepository.save(project);
    }

    public void archiveProject(Long projectId) {
        Project project = findOrThrow(projectId);
        project.setArchived(true);
        projectRepository.save(project);
    }

    public void deleteProject(Long projectId) {
        projectRepository.deleteById(projectId);
    }

    public Project forkProject(Long sourceProjectId, Long newOwnerId) {
        Project source = findOrThrow(sourceProjectId);

        Project fork = Project.builder()
                .ownerId(newOwnerId)
                .name(source.getName())
                .description("Forked from: " + source.getName())
                .language(source.getLanguage())
                .visibility(Project.Visibility.PRIVATE)
                .templateId(sourceProjectId)
                .build();
        fork.getMemberIds().add(newOwnerId);

        source.setForkCount(source.getForkCount() + 1);
        projectRepository.save(source);

        Project saved = projectRepository.save(fork);
        log.info("Project {} forked to new project {} by user {}",
                sourceProjectId, saved.getProjectId(), newOwnerId);
        return saved;
    }

    public void starProject(Long projectId, Long userId) {
        Project project = findOrThrow(projectId);
        project.setStarCount(project.getStarCount() + 1);
        projectRepository.save(project);
    }

    public Project addMember(Long projectId, Long userId) {
        Project project = findOrThrow(projectId);
        project.getMemberIds().add(userId);
        return projectRepository.save(project);
    }

    public Project removeMember(Long projectId, Long userId) {
        Project project = findOrThrow(projectId);
        project.getMemberIds().remove(userId);
        return projectRepository.save(project);
    }

    private Project findOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }
}
