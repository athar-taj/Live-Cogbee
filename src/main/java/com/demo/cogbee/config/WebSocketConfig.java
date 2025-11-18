package com.demo.cogbee.config;

import com.demo.cogbee.config.kurento.KurentoWebRtcHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final KurentoWebRtcHandler kurentoHandler;

    public WebSocketConfig(KurentoWebRtcHandler kurentoHandler) {
        this.kurentoHandler = kurentoHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(kurentoHandler, "/kurento")
                .setAllowedOrigins(
                        "http://localhost:3000",
                        "http://localhost:3001",
                        "https://cogbee-react.vercel.app",
                        "https://delmar-drearier-arvilla.ngrok-free.dev"
                );
    }
}
