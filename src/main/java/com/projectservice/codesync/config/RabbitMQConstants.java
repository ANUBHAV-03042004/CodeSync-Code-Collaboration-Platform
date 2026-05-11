package com.projectservice.codesync.config;

public final class RabbitMQConstants {
    private RabbitMQConstants() {}

    public static final String COLLAB_EXCHANGE     = "codesync.collab.exchange";
    public static final String COLLAB_EVENT_QUEUE  = "codesync.collab.event.queue";
    public static final String COLLAB_SESSION_KEY  = "collab.session.*";
}
