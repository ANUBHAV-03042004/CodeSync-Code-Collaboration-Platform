package com.versionservice.codesync.service;

import com.versionservice.codesync.entity.Snapshot;
import com.versionservice.codesync.repository.SnapshotRepository;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@Transactional
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    private final SnapshotRepository snapshotRepository;

    // FIX: constructor injection — no Lombok @RequiredArgsConstructor
    public VersionService(SnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    // ── Create snapshot ───────────────────────────────────────────────────────

    public Snapshot createSnapshot(Long projectId, Long fileId, Long authorId,
                                    String message, String content, String branch) {
        String hash = sha256(content);

        Optional<Snapshot> parent = snapshotRepository.findLatestByFileId(fileId);

        // FIX: Snapshot.builder() now works — manual builder implemented in entity
        Snapshot snap = Snapshot.builder()
                .projectId(projectId)
                .fileId(fileId)
                .authorId(authorId)
                .message(message)
                .content(content)
                .hash(hash)
                .parentSnapshotId(parent.map(Snapshot::getSnapshotId).orElse(null))
                .branch(branch != null ? branch : "main")
                .build();

        Snapshot saved = snapshotRepository.save(snap);
        log.info("Snapshot {} created for file {} on branch {}", saved.getSnapshotId(), fileId, branch);
        return saved;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<Snapshot> getById(Long snapshotId) {
        return snapshotRepository.findById(snapshotId);
    }

    @Transactional(readOnly = true)
    public List<Snapshot> getFileHistory(Long fileId) {
        return snapshotRepository.findByFileIdOrderByCreatedAtDesc(fileId);
    }

    @Transactional(readOnly = true)
    public List<Snapshot> getByProject(Long projectId) {
        return snapshotRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<Snapshot> getByBranch(Long projectId, String branch) {
        return snapshotRepository.findByBranch(branch)
                .stream().filter(s -> s.getProjectId().equals(projectId)).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> getLatest(Long fileId) {
        return snapshotRepository.findLatestByFileId(fileId);
    }

    @Transactional(readOnly = true)
    public List<String> getBranches(Long projectId) {
        return snapshotRepository.findBranchesByProject(projectId);
    }

    // ── Diff: Myers algorithm ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> diffSnapshots(Long snapshotIdA, Long snapshotIdB) {
        Snapshot a = snapshotRepository.findById(snapshotIdA)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotIdA));
        Snapshot b = snapshotRepository.findById(snapshotIdB)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotIdB));

        List<String> linesA = Arrays.asList(a.getContent().split("\n", -1));
        List<String> linesB = Arrays.asList(b.getContent().split("\n", -1));

        Patch<String> patch = DiffUtils.diff(linesA, linesB);

        List<Map<String, Object>> result = new ArrayList<>();
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type",          delta.getType().name());
            entry.put("originalPos",   delta.getSource().getPosition());
            entry.put("originalLines", delta.getSource().getLines());
            entry.put("revisedPos",    delta.getTarget().getPosition());
            entry.put("revisedLines",  delta.getTarget().getLines());
            result.add(entry);
        }

        log.debug("Diff {} → {}: {} deltas", snapshotIdA, snapshotIdB, result.size());
        return result;
    }

    // ── Restore snapshot ──────────────────────────────────────────────────────

    public Snapshot restoreSnapshot(Long snapshotId, Long restoredByUserId) {
        Snapshot old = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));

        String computedHash = sha256(old.getContent());
        if (!computedHash.equals(old.getHash())) {
            throw new IllegalStateException("Snapshot integrity check failed — hash mismatch");
        }

        return createSnapshot(
                old.getProjectId(), old.getFileId(), restoredByUserId,
                "Restored from snapshot #" + snapshotId + ": " + old.getMessage(),
                old.getContent(), old.getBranch()
        );
    }

    // ── Branch management ─────────────────────────────────────────────────────

    public Snapshot createBranch(Long fileId, Long authorId, String newBranch) {
        Snapshot latest = snapshotRepository.findLatestByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("No snapshots found for file: " + fileId));

        Snapshot branch = Snapshot.builder()
                .projectId(latest.getProjectId())
                .fileId(latest.getFileId())
                .authorId(authorId)
                .message("Branch '" + newBranch + "' created from '" + latest.getBranch() + "'")
                .content(latest.getContent())
                .hash(latest.getHash())
                .parentSnapshotId(latest.getSnapshotId())
                .branch(newBranch)
                .build();

        log.info("Branch '{}' created for file {}", newBranch, fileId);
        return snapshotRepository.save(branch);
    }

    // ── Tag management ────────────────────────────────────────────────────────

    public Snapshot tagSnapshot(Long snapshotId, String tag) {
        Snapshot snap = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));
        snap.setTag(tag);
        log.info("Snapshot {} tagged as '{}'", snapshotId, tag);
        return snapshotRepository.save(snap);
    }

    // ── SHA-256 helper ────────────────────────────────────────────────────────

    public String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}