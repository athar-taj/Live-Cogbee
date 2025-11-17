package com.demo.cogbee.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        registry.addHandler(new WebRtcSignalingHandler(), "/signal")
                .setAllowedOrigins(
                        "http://127.0.0.1:5500",
                        "http://localhost:5500",
                        "https://cogbee.vercel.app",
                        "https://delmar-drearier-arvilla.ngrok-free.dev"
                );
    }
}

