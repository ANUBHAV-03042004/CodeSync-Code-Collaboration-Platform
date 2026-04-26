package com.executionservice.codesync.repository;

import com.executionservice.codesync.entity.ExecutionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, String> {

    List<ExecutionJob> findByUserId(Long userId);
    List<ExecutionJob> findByProjectId(Long projectId);
    List<ExecutionJob> findByStatus(ExecutionJob.Status status);
    List<ExecutionJob> findByLanguage(String language);
    List<ExecutionJob> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    int countByUserId(Long userId);

    @Query("SELECT e.language AS language, COUNT(e) AS count " +
           "FROM ExecutionJob e GROUP BY e.language ORDER BY count DESC")
    List<Map<String, Object>> countByLanguage();

    @Query("SELECT e FROM ExecutionJob e WHERE e.userId = :uid ORDER BY e.createdAt DESC")
    List<ExecutionJob> findRecentByUser(@Param("uid") Long userId);
}