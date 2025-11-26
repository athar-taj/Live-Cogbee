package com.demo.cogbee.config;


import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AudioStreamHandler extends BinaryWebSocketHandler {


    private final ConcurrentHashMap<String, List<byte[]>> audioBuffers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String query = session.getUri().getQuery();
        String sessionId = query.split("=")[1];

        session.getAttributes().put("sessionId", sessionId);
        audioBuffers.put(sessionId, new ArrayList<>());

        System.out.println("🔗 Audio stream connected: " + sessionId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        int size = message.getPayloadLength();
        if (size == 1) return;

        byte[] chunk = message.getPayload().array();

        String sessionId = (String) session.getAttributes().get("sessionId");
        audioBuffers.get(sessionId).add(chunk);

        System.out.println("🎤 Chunk received (" + sessionId + "): " + size + " bytes");
    }
}
