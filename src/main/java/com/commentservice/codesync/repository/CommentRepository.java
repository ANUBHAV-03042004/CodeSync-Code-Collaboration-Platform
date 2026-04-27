package com.commentservice.codesync.repository;

import com.commentservice.codesync.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByFileIdOrderByLineNumberAsc(Long fileId);
    List<Comment> findByProjectId(Long projectId);
    List<Comment> findByAuthorId(Long authorId);
    List<Comment> findByParentCommentId(Long parentCommentId);
    List<Comment> findByFileIdAndLineNumber(Long fileId, int lineNumber);
    List<Comment> findByResolved(boolean resolved);
    int           countByFileId(Long fileId);
}
