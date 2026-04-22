package com.projectservice.codesync.repository;

import com.projectservice.codesync.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerId(Long ownerId);

    List<Project> findByVisibility(Project.Visibility visibility);

    List<Project> findByLanguage(String language);

    List<Project> findByIsArchived(boolean archived);

    int countByOwnerId(Long ownerId);

    @Query("SELECT p FROM Project p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<Project> searchByName(@Param("q") String query);

    @Query("SELECT p FROM Project p JOIN p.memberIds m WHERE m = :userId")
    List<Project> findByMemberUserId(@Param("userId") Long userId);

    @Query("SELECT p FROM Project p WHERE p.visibility = 'PUBLIC' " +
           "AND p.isArchived = false ORDER BY p.starCount DESC")
    List<Project> findTopPublicProjects();
}
