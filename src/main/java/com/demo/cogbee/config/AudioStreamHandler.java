package com.demo.cogbee.config;

import com.demo.cogbee.service.SpeechToTextService;
import com.demo.cogbee.service.live.AsrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;

@Component
public class AudioStreamHandler extends BinaryWebSocketHandler {

	private final SpeechToTextService speechService;
	private final SimpMessagingTemplate messagingTemplate;

	@Autowired
	public AudioStreamHandler(SpeechToTextService speechService, SimpMessagingTemplate messagingTemplate) {
		this.speechService = speechService;
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		System.out.println("🔗 Audio stream connected: " + session.getId());
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
		byte[] bytes = message.getPayload().array();
		String sessionId = (String) session.getAttributes().getOrDefault("sessionId", "default");

		try {
			// 🧠 Convert chunk → text
			String textChunk = speechService.transcribeChunk(bytes);

			// 📨 Send partial text to frontend
			messagingTemplate.convertAndSend("/topic/live/" + sessionId, textChunk);

			System.out.println("🗣️ Chunk processed: " + textChunk);

		} catch (Exception e) {
			messagingTemplate.convertAndSend("/topic/live/" + sessionId, "Error: " + e.getMessage());
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		System.err.println("❌ WebSocket error: " + exception.getMessage());
	}
}


