package com.cloudbox.master.config;

import com.cloudbox.master.realtime.ClusterStatusWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClusterStatusWebSocketHandler clusterStatusWebSocketHandler;

    public WebSocketConfig(ClusterStatusWebSocketHandler clusterStatusWebSocketHandler) {
        this.clusterStatusWebSocketHandler = clusterStatusWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(clusterStatusWebSocketHandler, "/ws/cluster-status")
                .setAllowedOriginPatterns("*");
    }
}
