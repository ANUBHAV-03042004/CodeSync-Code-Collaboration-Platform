package com.versionservice.codesync.service;

import com.versionservice.codesync.entity.Snapshot;
import com.versionservice.codesync.repository.SnapshotRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VersionService Tests")
class VersionServiceTest {

    @Mock
    private SnapshotRepository snapshotRepository;

    @InjectMocks
    private VersionService versionService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Snapshot sampleSnapshot(Long id, Long fileId, String content, String branch) {
        Snapshot s = new Snapshot();
        s.setSnapshotId(id);
        s.setProjectId(1L);
        s.setFileId(fileId);
        s.setAuthorId(42L);
        s.setMessage("test commit");
        s.setContent(content);
        s.setHash(versionService.sha256(content));
        s.setBranch(branch);
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }

    // ── Test 1: createSnapshot saves and returns snapshot with correct fields ─

    @Test
    @DisplayName("createSnapshot() saves snapshot with correct hash and parent linkage")
    void createSnapshot_savesWithHashAndParent() {
        Snapshot parent = sampleSnapshot(10L, 5L, "old content", "main");
        when(snapshotRepository.findLatestByFileId(5L)).thenReturn(Optional.of(parent));
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> {
            Snapshot s = inv.getArgument(0);
            s.setSnapshotId(11L);
            return s;
        });

        Snapshot result = versionService.createSnapshot(1L, 5L, 42L,
                "new commit", "new content", "main");

        assertThat(result.getSnapshotId()).isEqualTo(11L);
        assertThat(result.getHash()).isEqualTo(versionService.sha256("new content"));
        assertThat(result.getParentSnapshotId()).isEqualTo(10L);
        assertThat(result.getBranch()).isEqualTo("main");
        verify(snapshotRepository).save(any(Snapshot.class));
    }

    // ── Test 2: createSnapshot defaults branch to "main" when null ────────────

    @Test
    @DisplayName("createSnapshot() defaults branch to 'main' when branch is null")
    void createSnapshot_defaultsBranchToMain() {
        when(snapshotRepository.findLatestByFileId(anyLong())).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> inv.getArgument(0));

        Snapshot result = versionService.createSnapshot(1L, 5L, 42L,
                "initial", "content", null);

        assertThat(result.getBranch()).isEqualTo("main");
    }

    // ── Test 3: diffSnapshots returns correct delta count ─────────────────────

    @Test
    @DisplayName("diffSnapshots() returns non-empty delta list when content differs")
    void diffSnapshots_returnsDeltasForDifferentContent() {
        Snapshot a = sampleSnapshot(1L, 5L, "line1\nline2\nline3", "main");
        Snapshot b = sampleSnapshot(2L, 5L, "line1\nlineX\nline3\nline4", "main");

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(a));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(b));

        List<Map<String, Object>> deltas = versionService.diffSnapshots(1L, 2L);

        assertThat(deltas).isNotEmpty();
        assertThat(deltas.get(0)).containsKey("type");
        assertThat(deltas.get(0)).containsKey("originalLines");
        assertThat(deltas.get(0)).containsKey("revisedLines");
    }

    // ── Test 4: diffSnapshots returns empty list for identical content ─────────

    @Test
    @DisplayName("diffSnapshots() returns empty list when content is identical")
    void diffSnapshots_returnsEmptyForIdenticalContent() {
        Snapshot a = sampleSnapshot(1L, 5L, "same content", "main");
        Snapshot b = sampleSnapshot(2L, 5L, "same content", "main");

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(a));
        when(snapshotRepository.findById(2L)).thenReturn(Optional.of(b));

        List<Map<String, Object>> deltas = versionService.diffSnapshots(1L, 2L);

        assertThat(deltas).isEmpty();
    }

    // ── Test 5: restoreSnapshot throws on hash mismatch (integrity check) ─────

    @Test
    @DisplayName("restoreSnapshot() throws IllegalStateException when hash is tampered")
    void restoreSnapshot_throwsOnHashMismatch() {
        Snapshot corrupted = sampleSnapshot(1L, 5L, "original content", "main");
        corrupted.setHash("tampered-hash-value"); // deliberately wrong

        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(corrupted));

        assertThatThrownBy(() -> versionService.restoreSnapshot(1L, 99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hash mismatch");
    }

    // ── Test 6: tagSnapshot sets tag and saves ────────────────────────────────

    @Test
    @DisplayName("tagSnapshot() sets tag on snapshot and persists it")
    void tagSnapshot_setsTagAndSaves() {
        Snapshot snap = sampleSnapshot(1L, 5L, "content", "main");
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(snap));
        when(snapshotRepository.save(any(Snapshot.class))).thenAnswer(inv -> inv.getArgument(0));

        Snapshot result = versionService.tagSnapshot(1L, "v1.0.0");

        assertThat(result.getTag()).isEqualTo("v1.0.0");
        verify(snapshotRepository).save(snap);
    }
}
