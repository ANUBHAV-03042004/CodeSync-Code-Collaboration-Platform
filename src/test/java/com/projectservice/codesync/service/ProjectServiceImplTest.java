package com.projectservice.codesync.service;

import com.projectservice.codesync.entity.Project;
import com.projectservice.codesync.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    private Project sampleProject;

    @BeforeEach
    void setUp() {
        sampleProject = Project.builder()
                .projectId(1L)
                .ownerId(10L)
                .name("CodeSync IDE")
                .description("A collaborative code editor")
                .language("Java")
                .visibility(Project.Visibility.PUBLIC)
                .build();
        sampleProject.getMemberIds().add(10L);
    }

    // ── Test 1: createProject saves correctly and adds owner as member ────────

    @Test
    @DisplayName("createProject — saves project and auto-adds owner as member")
    void createProject_savesProjectAndAddOwnerAsMember() {
        // Arrange
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Project result = projectService.createProject(
                10L, "CodeSync IDE", "A collaborative code editor",
                "Java", Project.Visibility.PUBLIC);

        // Assert
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository, times(1)).save(captor.capture());

        Project saved = captor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo(10L);
        assertThat(saved.getName()).isEqualTo("CodeSync IDE");
        assertThat(saved.getLanguage()).isEqualTo("Java");
        assertThat(saved.getVisibility()).isEqualTo(Project.Visibility.PUBLIC);
        assertThat(saved.getMemberIds()).contains(10L); // owner must be a member
    }

    // ── Test 2: getProjectById returns empty Optional when not found ──────────

    @Test
    @DisplayName("getProjectById — returns empty Optional when project does not exist")
    void getProjectById_returnsEmpty_whenNotFound() {
        // Arrange
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        // Act
        Optional<Project> result = projectService.getProjectById(99L);

        // Assert
        assertThat(result).isEmpty();
        verify(projectRepository).findById(99L);
    }

    // ── Test 3: updateProject applies only non-null fields ────────────────────

    @Test
    @DisplayName("updateProject — updates only the fields that are non-null")
    void updateProject_updatesOnlyNonNullFields() {
        // Arrange: project has name "CodeSync IDE", language "Java"
        when(projectRepository.findById(1L)).thenReturn(Optional.of(sampleProject));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: only update language, leave name and description null
        Project result = projectService.updateProject(1L, null, null, "Kotlin", null);

        // Assert
        assertThat(result.getName()).isEqualTo("CodeSync IDE"); // unchanged
        assertThat(result.getLanguage()).isEqualTo("Kotlin");   // updated
        assertThat(result.getDescription()).isEqualTo("A collaborative code editor"); // unchanged
    }

    // ── Test 4: forkProject increments forkCount and creates a private copy ───

    @Test
    @DisplayName("forkProject — creates private fork and increments source forkCount")
    void forkProject_createsForkAndIncrementsSourceForkCount() {
        // Arrange
        int originalForkCount = sampleProject.getForkCount(); // 0
        when(projectRepository.findById(1L)).thenReturn(Optional.of(sampleProject));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Project fork = projectService.forkProject(1L, 20L);

        // Assert — fork is private and owned by new user
        assertThat(fork.getOwnerId()).isEqualTo(20L);
        assertThat(fork.getVisibility()).isEqualTo(Project.Visibility.PRIVATE);
        assertThat(fork.getTemplateId()).isEqualTo(1L);
        assertThat(fork.getDescription()).startsWith("Forked from:");
        assertThat(fork.getMemberIds()).contains(20L);

        // Assert — source forkCount incremented
        assertThat(sampleProject.getForkCount()).isEqualTo(originalForkCount + 1);

        // save() must be called twice: once for source, once for fork
        verify(projectRepository, times(2)).save(any(Project.class));
    }

    // ── Test 5: archiveProject sets isArchived = true ─────────────────────────

    @Test
    @DisplayName("archiveProject — sets isArchived flag to true")
    void archiveProject_setsArchivedFlag() {
        // Arrange
        assertThat(sampleProject.isArchived()).isFalse();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(sampleProject));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        projectService.archiveProject(1L);

        // Assert
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().isArchived()).isTrue();
    }

    // ── Test 6: findOrThrow throws when project missing (via deleteProject) ───

    @Test
    @DisplayName("deleteProject — delegates to repository deleteById")
    void deleteProject_callsRepositoryDeleteById() {
        // Arrange
        doNothing().when(projectRepository).deleteById(1L);

        // Act
        projectService.deleteProject(1L);

        // Assert
        verify(projectRepository, times(1)).deleteById(1L);
    }

    // ── Test 7: addMember adds userId to memberIds set ────────────────────────

    @Test
    @DisplayName("addMember — adds the given userId to project memberIds")
    void addMember_addsUserToMemberIds() {
        // Arrange: project starts with only ownerId=10 as member
        when(projectRepository.findById(1L)).thenReturn(Optional.of(sampleProject));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Project result = projectService.addMember(1L, 55L);

        // Assert
        assertThat(result.getMemberIds()).contains(10L, 55L);
        assertThat(result.getMemberIds()).hasSize(2);
    }

    // ── Test 8: getProjectsByOwner delegates to repository ───────────────────

    @Test
    @DisplayName("getProjectsByOwner — returns repository result unchanged")
    void getProjectsByOwner_returnsRepositoryResult() {
        // Arrange
        List<Project> expected = List.of(sampleProject);
        when(projectRepository.findByOwnerId(10L)).thenReturn(expected);

        // Act
        List<Project> result = projectService.getProjectsByOwner(10L);

        // Assert
        assertThat(result).isSameAs(expected);
        verify(projectRepository).findByOwnerId(10L);
    }
}
