package com.cloudbox.master.realtime;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClusterStatusWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ClusterStatusWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public ClusterStatusWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("Cliente conectado ao WebSocket de status do cluster: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("Cliente desconectado do WebSocket de status do cluster: {}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        closeQuietly(session);
        log.warn("Erro no cliente WebSocket de status do cluster: {}", session.getId(), exception);
    }

    public void broadcast(ClusterStatusMessage statusMessage) {
        final TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(statusMessage));
        } catch (JacksonException exception) {
            log.error("Não foi possível serializar a mudança de status do cluster", exception);
            return;
        }

        sessions.forEach(session -> send(session, message));
    }

    private void send(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }

        try {
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException exception) {
            sessions.remove(session);
            closeQuietly(session);
            log.warn("Não foi possível enviar status ao cliente WebSocket: {}", session.getId(), exception);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException exception) {
            log.debug("Erro ao encerrar sessão WebSocket {}", session.getId(), exception);
        }
    }
}
