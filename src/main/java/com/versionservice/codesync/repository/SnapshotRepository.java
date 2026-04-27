package com.versionservice.codesync.repository;

import com.versionservice.codesync.entity.Snapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

    List<Snapshot>     findByFileIdOrderByCreatedAtDesc(Long fileId);
    List<Snapshot>     findByProjectId(Long projectId);
    List<Snapshot>     findByAuthorId(Long authorId);
    List<Snapshot>     findByBranch(String branch);
    Optional<Snapshot> findByHash(String hash);
    Optional<Snapshot> findByTag(String tag);

    @Query("SELECT s FROM Snapshot s WHERE s.fileId = :fid ORDER BY s.createdAt DESC LIMIT 1")
    Optional<Snapshot> findLatestByFileId(@Param("fid") Long fileId);

    @Query("SELECT DISTINCT s.branch FROM Snapshot s WHERE s.projectId = :pid")
    List<String> findBranchesByProject(@Param("pid") Long projectId);
}