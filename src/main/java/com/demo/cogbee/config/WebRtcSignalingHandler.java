package com.demo.cogbee.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;

public class WebRtcSignalingHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> rooms = new HashMap<>();
    private final Map<String, String> sessionRoom = new HashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("Connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var json = new ObjectMapper().readTree(message.getPayload());
        String type = json.get("type").asText();

        if ("join".equals(type)) {
            String roomId = json.get("roomId").asText();
            rooms.putIfAbsent(roomId, new HashSet<>());
            rooms.get(roomId).add(session);
            sessionRoom.put(session.getId(), roomId);

            // send list of existing participants to the new user
            var existing = new ArrayList<String>();
            for (WebSocketSession s : rooms.get(roomId)) {
                if (!s.getId().equals(session.getId())) {
                    existing.add(s.getId());
                }
            }

            session.sendMessage(new TextMessage("{\"type\":\"peers\",\"peers\":" + existing + "}"));
            return;
        }

        // relay SDP/ICE to all peers in same room
        String roomId = sessionRoom.get(session.getId());
        for (WebSocketSession s : rooms.get(roomId)) {
            if (!s.getId().equals(session.getId())) {
                s.sendMessage(message);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String room = sessionRoom.get(session.getId());
        if (room != null) {
            rooms.get(room).remove(session);
        }
    }
}
