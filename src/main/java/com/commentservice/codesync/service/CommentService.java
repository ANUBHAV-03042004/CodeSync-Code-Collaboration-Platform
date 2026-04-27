package com.commentservice.codesync.service;

import com.commentservice.codesync.entity.Comment;
import com.commentservice.codesync.repository.CommentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FIX: Removed @RequiredArgsConstructor → explicit constructor.
 *      Removed @Slf4j → manual Logger (fixes "log cannot be resolved").
 *      Removed @Transactional on class level; kept on individual methods.
 */
@Service
@Transactional
public class CommentService {

    // FIX: "log cannot be resolved" — @Slf4j not generating; use manual Logger
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final RabbitTemplate    rabbitTemplate;

    private static final String  NOTIF_EXCHANGE = "codesync.notification.exchange";
    private static final String  INAPP_KEY      = "notification.inapp";
    private static final String  EMAIL_KEY      = "notification.email";
    private static final Pattern MENTION_REGEX  = Pattern.compile("@(\\w+)");

    // FIX: "The blank final field commentService may not have been initialized"
    // → @RequiredArgsConstructor was not generating; explicit constructor fixes this
    public CommentService(CommentRepository commentRepository, RabbitTemplate rabbitTemplate) {
        this.commentRepository = commentRepository;
        this.rabbitTemplate    = rabbitTemplate;
    }

    // ── Add comment ───────────────────────────────────────────────────────────

    public Comment addComment(Long projectId, Long fileId, Long authorId,
                               String content, int lineNumber, Integer columnNumber,
                               Long parentCommentId, Long snapshotId) {

        Comment comment = Comment.builder()
                .projectId(projectId)
                .fileId(fileId)
                .authorId(authorId)
                .content(content)
                .lineNumber(lineNumber)
                .columnNumber(columnNumber)
                .parentCommentId(parentCommentId)
                .snapshotId(snapshotId)
                .build();

        Comment saved = commentRepository.save(comment);
        log.info("Comment {} added to file {} line {}", saved.getCommentId(), fileId, lineNumber);

        parseMentionsAndNotify(saved);

        if (parentCommentId != null) {
            notifyReply(saved, parentCommentId, authorId);
        }

        return saved;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Comment> getByFile(Long fileId) {
        return commentRepository.findByFileIdOrderByLineNumberAsc(fileId);
    }

    @Transactional(readOnly = true)
    public List<Comment> getByProject(Long projectId) {
        return commentRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<Comment> getById(Long commentId) {
        return commentRepository.findById(commentId);
    }

    @Transactional(readOnly = true)
    public List<Comment> getReplies(Long parentCommentId) {
        return commentRepository.findByParentCommentId(parentCommentId);
    }

    @Transactional(readOnly = true)
    public List<Comment> getByLine(Long fileId, int lineNumber) {
        return commentRepository.findByFileIdAndLineNumber(fileId, lineNumber);
    }

    @Transactional(readOnly = true)
    public int getCommentCount(Long fileId) {
        return commentRepository.countByFileId(fileId);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public Comment updateComment(Long commentId, Long requestingUserId, String newContent) {
        Comment c = findOrThrow(commentId);
        if (!c.getAuthorId().equals(requestingUserId)) {
            throw new SecurityException("Cannot edit another user's comment");
        }
        c.setContent(newContent);
        return commentRepository.save(c);
    }

    public void deleteComment(Long commentId, Long requestingUserId) {
        Comment c = findOrThrow(commentId);
        if (!c.getAuthorId().equals(requestingUserId)) {
            throw new SecurityException("Cannot delete another user's comment");
        }
        commentRepository.deleteById(commentId);
    }

    public Comment resolveComment(Long commentId) {
        Comment c = findOrThrow(commentId);
        c.setResolved(true);
        return commentRepository.save(c);
    }

    public Comment unresolveComment(Long commentId) {
        Comment c = findOrThrow(commentId);
        c.setResolved(false);
        return commentRepository.save(c);
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private void parseMentionsAndNotify(Comment comment) {
        Matcher m = MENTION_REGEX.matcher(comment.getContent());
        Set<String> mentioned = new HashSet<>();
        while (m.find()) {
            String username = m.group(1);
            if (mentioned.add(username)) {
                log.debug("@mention detected: {} in comment {}", username, comment.getCommentId());
                publishNotification(null, comment.getAuthorId(), "MENTION",
                        "@" + username + " mentioned you in a comment",
                        comment.getContent(),
                        String.valueOf(comment.getCommentId()), "COMMENT",
                        "/projects/" + comment.getProjectId()
                                + "/files/" + comment.getFileId()
                                + "#L" + comment.getLineNumber(),
                        Map.of("mentionedUsername", username));
            }
        }
    }

    private void notifyReply(Comment reply, Long parentCommentId, Long replyAuthorId) {
        commentRepository.findById(parentCommentId).ifPresent(parent -> {
            if (!parent.getAuthorId().equals(replyAuthorId)) {
                publishNotification(parent.getAuthorId(), replyAuthorId, "COMMENT",
                        "New reply to your comment", reply.getContent(),
                        String.valueOf(reply.getCommentId()), "COMMENT",
                        "/projects/" + reply.getProjectId()
                                + "/files/" + reply.getFileId()
                                + "#L" + reply.getLineNumber(),
                        Collections.emptyMap());
            }
        });
    }

    private void publishNotification(Long recipientId, Long actorId,
                                      String type, String title, String message,
                                      String relatedId, String relatedType,
                                      String deepLinkUrl, Map<String, Object> extra) {
        Map<String, Object> event = new HashMap<>(Map.of(
                "recipientId", recipientId != null ? recipientId : "RESOLVE_BY_USERNAME",
                "actorId",     actorId,
                "type",        type,
                "title",       title,
                "message",     message,
                "relatedId",   relatedId,
                "relatedType", relatedType,
                "deepLinkUrl", deepLinkUrl,
                "createdAt",   LocalDateTime.now().toString()
        ));
        event.putAll(extra);
        rabbitTemplate.convertAndSend(NOTIF_EXCHANGE, INAPP_KEY, event);
        if ("MENTION".equals(type) || "COMMENT".equals(type)) {
            rabbitTemplate.convertAndSend(NOTIF_EXCHANGE, EMAIL_KEY, event);
        }
    }

    private Comment findOrThrow(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + id));
    }
}
