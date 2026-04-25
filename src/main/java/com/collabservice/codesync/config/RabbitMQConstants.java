package com.collabservice.codesync.config;

/**
 * Central source of truth for every RabbitMQ exchange, queue, and routing key
 * used across all CodeSync microservices.
 *
 * Matches RabbitMQConstants in the platform-wide rabbitmq-config blueprint.
 */
public final class RabbitMQConstants {

    private RabbitMQConstants() {}

    // ── Exchanges ─────────────────────────────────────────────────────────────

    public static final String EXECUTION_EXCHANGE        = "codesync.execution.exchange";
    public static final String NOTIFICATION_EXCHANGE     = "codesync.notification.exchange";
    public static final String COLLAB_EXCHANGE           = "codesync.collab.exchange";
    public static final String DEAD_LETTER_EXCHANGE      = "codesync.dlx";

    // ── Queues ────────────────────────────────────────────────────────────────

    public static final String EXECUTION_QUEUE             = "codesync.execution.queue";
    public static final String EXECUTION_RESULT_QUEUE      = "codesync.execution.result.queue";
    public static final String EXECUTION_DEAD_LETTER_QUEUE = "codesync.execution.dlq";

    public static final String NOTIFICATION_EMAIL_QUEUE       = "codesync.notification.email.queue";
    public static final String NOTIFICATION_INAPP_QUEUE       = "codesync.notification.inapp.queue";
    public static final String NOTIFICATION_DEAD_LETTER_QUEUE = "codesync.notification.dlq";

    public static final String COLLAB_EVENT_QUEUE          = "codesync.collab.event.queue";

    // ── Routing keys ──────────────────────────────────────────────────────────

    public static final String EXECUTION_ROUTING_KEY     = "execution.job.submit";
    public static final String EXECUTION_RESULT_KEY      = "execution.job.result";

    public static final String NOTIFICATION_EMAIL_KEY    = "notification.email";
    public static final String NOTIFICATION_INAPP_KEY    = "notification.inapp";
    public static final String NOTIFICATION_ALL_KEY      = "notification.*";

    public static final String COLLAB_CURSOR_KEY         = "collab.cursor.update";
    public static final String COLLAB_EDIT_KEY           = "collab.edit.delta";
    public static final String COLLAB_SESSION_KEY        = "collab.session.*";
}
