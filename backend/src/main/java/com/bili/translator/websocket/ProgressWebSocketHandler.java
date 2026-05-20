package com.bili.translator.websocket;

import com.bili.translator.model.TaskStatus;
import com.bili.translator.service.PipelineProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ProgressWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ProgressWebSocketHandler.class);

    private final ConcurrentHashMap<String, List<WebSocketSession>> activeConnections = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PipelineProcessor pipelineProcessor;

    public ProgressWebSocketHandler(PipelineProcessor pipelineProcessor) {
        this.pipelineProcessor = pipelineProcessor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String taskId = extractTaskId(session);
        if (taskId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        activeConnections.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(session);

        TaskStatus status = pipelineProcessor.getTaskStatus(taskId);
        if (status != null) {
            String json = objectMapper.writeValueAsString(Map.of(
                    "task_id", status.getTaskId(),
                    "stage", status.getStage(),
                    "progress", status.getProgress(),
                    "message", status.getMessage()
            ));
            session.sendMessage(new TextMessage(json));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String taskId = extractTaskId(session);
        if (taskId != null) {
            List<WebSocketSession> sessions = activeConnections.get(taskId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    activeConnections.remove(taskId);
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    }

    public void sendProgress(String taskId, String stage, double progress, String message) {
        List<WebSocketSession> sessions = activeConnections.get(taskId);
        if (sessions == null || sessions.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "task_id", taskId,
                    "stage", stage,
                    "progress", progress,
                    "message", message
            ));
            TextMessage textMessage = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.warn("Failed to send WebSocket message: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize progress message: {}", e.getMessage());
        }
    }

    private String extractTaskId(WebSocketSession session) {
        String uri = session.getUri() != null ? session.getUri().getPath() : "";
        if (uri.startsWith("/ws/")) {
            return uri.substring(4);
        }
        return null;
    }
}
