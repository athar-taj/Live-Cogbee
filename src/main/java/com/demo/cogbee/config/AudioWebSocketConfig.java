package com.demo.cogbee.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AudioWebSocketConfig implements WebSocketConfigurer {

	private final AudioStreamHandler audioStreamHandler;

	public AudioWebSocketConfig(AudioStreamHandler audioStreamHandler) {
		this.audioStreamHandler = audioStreamHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioStreamHandler, "/audio-stream")
                .setAllowedOrigins("http://localhost:5500", "http://127.0.0.1:5500");
	}
}
