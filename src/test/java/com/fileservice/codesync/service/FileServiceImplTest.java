package com.fileservice.codesync.service;

import com.fileservice.codesync.entity.CodeFile;
import com.fileservice.codesync.repository.FileRepository;
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
class FileServiceImplTest {

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private FileServiceImpl fileService;

    private CodeFile sampleFile;

    @BeforeEach
    void setUp() {
        sampleFile = CodeFile.builder()
                .fileId(1L)
                .projectId(10L)
                .name("Main.java")
                .path("src/Main.java")
                .language("Java")
                .content("public class Main {}")
                .size(20L)
                .createdById(5L)
                .lastEditedBy(5L)
                .isFolder(false)
                .build();
    }

    // ── Test 1: createFile saves correctly and sets size ─────────────────────

    @Test
    @DisplayName("createFile — saves file and calculates size from content length")
    void createFile_savesFileWithCorrectSize() {
        when(fileRepository.findByProjectIdAndPath(10L, "src/Main.java"))
                .thenReturn(Optional.empty());
        when(fileRepository.save(any(CodeFile.class))).thenAnswer(inv -> inv.getArgument(0));

        CodeFile result = fileService.createFile(10L, "Main.java", "src/Main.java",
                "Java", "public class Main {}", 5L);

        ArgumentCaptor<CodeFile> captor = ArgumentCaptor.forClass(CodeFile.class);
        verify(fileRepository).save(captor.capture());

        CodeFile saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(10L);
        assertThat(saved.getName()).isEqualTo("Main.java");
        assertThat(saved.getPath()).isEqualTo("src/Main.java");
        assertThat(saved.getSize()).isEqualTo(20L); // "public class Main {}".length()
        assertThat(saved.getCreatedById()).isEqualTo(5L);
        assertThat(saved.isFolder()).isFalse();
    }

    // ── Test 2: createFile throws when path already exists ───────────────────

    @Test
    @DisplayName("createFile — throws IllegalArgumentException if path already exists")
    void createFile_throwsWhenPathAlreadyExists() {
        when(fileRepository.findByProjectIdAndPath(10L, "src/Main.java"))
                .thenReturn(Optional.of(sampleFile));

        assertThatThrownBy(() ->
                fileService.createFile(10L, "Main.java", "src/Main.java", "Java", "content", 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File already exists at path");

        verify(fileRepository, never()).save(any());
    }

    // ── Test 3: updateFileContent updates content, size and editor ───────────

    @Test
    @DisplayName("updateFileContent — updates content, recalculates size, records editorId")
    void updateFileContent_updatesAllFields() {
        when(fileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(fileRepository.save(any(CodeFile.class))).thenAnswer(inv -> inv.getArgument(0));

        String newContent = "public class Main { public static void main(String[] args){} }";
        CodeFile result = fileService.updateFileContent(1L, newContent, 99L);

        assertThat(result.getContent()).isEqualTo(newContent);
        assertThat(result.getSize()).isEqualTo((long) newContent.length());
        assertThat(result.getLastEditedBy()).isEqualTo(99L);
    }

    // ── Test 4: deleteFile sets isDeleted = true (soft delete) ───────────────

    @Test
    @DisplayName("deleteFile — soft-deletes the file by setting isDeleted to true")
    void deleteFile_setsIsDeletedTrue() {
        assertThat(sampleFile.isDeleted()).isFalse();
        when(fileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(fileRepository.save(any(CodeFile.class))).thenAnswer(inv -> inv.getArgument(0));

        fileService.deleteFile(1L);

        ArgumentCaptor<CodeFile> captor = ArgumentCaptor.forClass(CodeFile.class);
        verify(fileRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
    }

    // ── Test 5: renameFile updates name and path last segment ─────────────────

    @Test
    @DisplayName("renameFile — updates name and replaces last segment of path")
    void renameFile_updatesNameAndPath() {
        when(fileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(fileRepository.save(any(CodeFile.class))).thenAnswer(inv -> inv.getArgument(0));

        CodeFile result = fileService.renameFile(1L, "App.java");

        assertThat(result.getName()).isEqualTo("App.java");
        assertThat(result.getPath()).isEqualTo("src/App.java"); // keeps parent dir
    }

    // ── Test 6: restoreFile clears isDeleted flag ─────────────────────────────

    @Test
    @DisplayName("restoreFile — sets isDeleted to false on a previously deleted file")
    void restoreFile_clearsIsDeletedFlag() {
        sampleFile.setDeleted(true);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(sampleFile));
        when(fileRepository.save(any(CodeFile.class))).thenAnswer(inv -> inv.getArgument(0));

        CodeFile result = fileService.restoreFile(1L);

        assertThat(result.isDeleted()).isFalse();
    }

    // ── Test 7: getFilesByProject delegates to repository ─────────────────────

    @Test
    @DisplayName("getFilesByProject — returns only non-deleted files from repository")
    void getFilesByProject_returnsRepositoryResult() {
        List<CodeFile> expected = List.of(sampleFile);
        when(fileRepository.findByProjectIdAndIsDeletedFalse(10L)).thenReturn(expected);

        List<CodeFile> result = fileService.getFilesByProject(10L);

        assertThat(result).isSameAs(expected);
        verify(fileRepository).findByProjectIdAndIsDeletedFalse(10L);
    }
}
