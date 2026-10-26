package com.bili.translator.websocket;

import com.bili.translator.model.TaskStatus;
import com.bili.translator.pipeline.PipelineProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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

    public ProgressWebSocketHandler(@Lazy PipelineProcessor pipelineProcessor) {
        this.pipelineProcessor = pipelineProcessor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String taskId = extractTaskId(session);
        if (taskId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        TaskStatus status = pipelineProcessor.getTaskStatus(taskId);
        if (status == null) {
            // 任务不存在（或已删除）时不建立长连接，避免 session 条目长期堆积
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // 与 afterConnectionClosed 共用同一个 key 锁（compute），
        // 避免「session 加进了一个刚被移除的列表」这种孤儿条目泄漏
        activeConnections.compute(taskId, (key, sessions) -> {
            List<WebSocketSession> list = sessions != null ? sessions : new CopyOnWriteArrayList<>();
            list.add(session);
            return list;
        });

        String json = objectMapper.writeValueAsString(Map.of(
                "task_id", status.getTaskId(),
                "stage", status.getStage(),
                "progress", status.getProgress(),
                "message", status.getMessage()
        ));
        session.sendMessage(new TextMessage(json));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String taskId = extractTaskId(session);
        if (taskId == null) {
            return;
        }

        // 复合操作必须原子：get → remove → isEmpty → remove(taskId) 四步之间
        // 并发 compute 会新建列表并把 session 加进去，随后被整表移除，导致 session 永久泄漏
        activeConnections.compute(taskId, (key, sessions) -> {
            if (sessions == null) {
                return null;
            }
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
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
