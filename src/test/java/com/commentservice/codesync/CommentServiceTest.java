package com.commentservice.codesync;

import com.commentservice.codesync.entity.Comment;
import com.commentservice.codesync.repository.CommentRepository;
import com.commentservice.codesync.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock RabbitTemplate    rabbitTemplate;

    @InjectMocks CommentService commentService;

    private Comment sampleComment;

    @BeforeEach
    void setUp() {
        sampleComment = Comment.builder()
                .commentId(1L)
                .projectId(10L)
                .fileId(20L)
                .authorId(5L)
                .content("Fix the null check on line 42")
                .lineNumber(42)
                .resolved(false)
                .build();
    }

    // ── Test 1: addComment saves and returns comment ──────────────────────────

    @Test
    void addComment_savesAndReturnsComment() {
        when(commentRepository.save(any(Comment.class))).thenReturn(sampleComment);

        Comment result = commentService.addComment(
                10L, 20L, 5L, "Fix the null check on line 42",
                42, null, null, null);

        assertThat(result.getCommentId()).isEqualTo(1L);
        assertThat(result.getContent()).isEqualTo("Fix the null check on line 42");
        assertThat(result.getAuthorId()).isEqualTo(5L);
        verify(commentRepository).save(any(Comment.class));
    }

    // ── Test 2: addComment with @mention fires RabbitMQ notification ──────────

    @Test
    void addComment_withMention_publishesNotification() {
        Comment mentionComment = Comment.builder()
                .commentId(2L).projectId(10L).fileId(20L).authorId(5L)
                .content("Hey @alice please review this").lineNumber(10).build();

        when(commentRepository.save(any(Comment.class))).thenReturn(mentionComment);

        commentService.addComment(10L, 20L, 5L,
                "Hey @alice please review this", 10, null, null, null);

        // Should publish in-app + email notifications for the mention
        verify(rabbitTemplate, atLeast(2))
                .convertAndSend(anyString(), anyString(), any(Object.class));
    }

    // ── Test 3: getByFile returns comments ordered by line ────────────────────

    @Test
    void getByFile_returnsCommentList() {
        when(commentRepository.findByFileIdOrderByLineNumberAsc(20L))
                .thenReturn(List.of(sampleComment));

        List<Comment> result = commentService.getByFile(20L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLineNumber()).isEqualTo(42);
    }

    // ── Test 4: updateComment throws when wrong user tries to edit ────────────

    @Test
    void updateComment_throwsSecurityException_whenNotAuthor() {
        when(commentRepository.findById(1L)).thenReturn(Optional.of(sampleComment));

        assertThatThrownBy(() ->
                commentService.updateComment(1L, 99L, "Hacked content"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Cannot edit another user");
    }

    // ── Test 5: resolveComment sets resolved=true ─────────────────────────────

    @Test
    void resolveComment_setsResolvedTrue() {
        when(commentRepository.findById(1L)).thenReturn(Optional.of(sampleComment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment result = commentService.resolveComment(1L);

        assertThat(result.isResolved()).isTrue();
        verify(commentRepository).save(sampleComment);
    }

    // ── Test 6: deleteComment throws when comment not found ───────────────────

    @Test
    void deleteComment_throwsIllegalArgument_whenCommentNotFound() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                commentService.deleteComment(999L, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Comment not found: 999");
    }
}
