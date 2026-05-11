package com.projectservice.codesync.service;

import com.projectservice.codesync.config.RabbitMQConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CollabEventListener {

    private static final Logger log = LoggerFactory.getLogger(CollabEventListener.class);
    private final ProjectServiceImpl projectService;

    public CollabEventListener(ProjectServiceImpl projectService) {
        this.projectService = projectService;
    }

    @jakarta.transaction.Transactional
    @RabbitListener(queues = RabbitMQConstants.COLLAB_EVENT_QUEUE)
    public void handleCollabEvent(Map<String, Object> event) {
        String type = (String) event.get("type");
        log.debug("Received collab event of type: {}", type);

        if ("PARTICIPANT_JOINED".equals(type) || "SESSION_CREATED".equals(type)) {
            Long projectId = null;
            Long userId = null;

            if (event.get("projectId") != null) {
                projectId = Long.parseLong(event.get("projectId").toString());
            }
            
            if ("SESSION_CREATED".equals(type)) {
                userId = Long.parseLong(event.get("ownerId").toString());
            } else {
                userId = Long.parseLong(event.get("userId").toString());
            }

            if (projectId != null && userId != null && projectId > 0) {
                try {
                    log.info("Auto-onboarding user {} to project {}", userId, projectId);
                    projectService.addMember(projectId, userId);
                } catch (Exception e) {
                    log.error("Failed to auto-onboard user {} to project {}: {}", userId, projectId, e.getMessage());
                }
            }
        }
    }
}
