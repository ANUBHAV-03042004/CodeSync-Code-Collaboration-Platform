package com.fileservice.codesync.repository;

import com.fileservice.codesync.entity.CodeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<CodeFile, Long> {

    List<CodeFile> findByProjectIdAndIsDeletedFalse(Long projectId);
    Optional<CodeFile> findByProjectIdAndPath(Long projectId, String path);
    List<CodeFile> findByProjectIdAndIsFolder(Long projectId, boolean isFolder);
    List<CodeFile> findByLastEditedBy(Long userId);
    int countByProjectId(Long projectId);

    @Query("SELECT f FROM CodeFile f WHERE f.projectId = :pid AND f.isDeleted = true")
    List<CodeFile> findDeleted(@Param("pid") Long projectId);

    @Query("SELECT f FROM CodeFile f WHERE f.projectId = :pid " +
           "AND f.isDeleted = false AND f.isFolder = false " +
           "AND (LOWER(f.name) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(f.content) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<CodeFile> searchInProject(@Param("pid") Long projectId, @Param("q") String query);
}