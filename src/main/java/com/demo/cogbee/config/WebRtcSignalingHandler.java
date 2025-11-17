package com.demo.cogbee.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;

public class WebRtcSignalingHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> rooms = new HashMap<>();
    private final Map<String, String> sessionRoom = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Connected: " + session.getId());


        var msg = Map.of(
                "type", "id",
                "id", session.getId()
        );
        session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var json = mapper.readTree(message.getPayload());
        String type = json.get("type").asText();

        // --------------------------
        // JOIN ROOM
        // --------------------------
        if (type.equals("join")) {
            String roomId = json.get("roomId").asText();

            rooms.putIfAbsent(roomId, new HashSet<>());
            rooms.get(roomId).add(session);
            sessionRoom.put(session.getId(), roomId);

            // send existing peers to the new user
            List<String> existingPeers = rooms.get(roomId).stream()
                    .filter(s -> !s.getId().equals(session.getId()))
                    .map(WebSocketSession::getId)
                    .toList();

            var reply = Map.of(
                    "type", "peers",
                    "peers", existingPeers
            );

            session.sendMessage(new TextMessage(mapper.writeValueAsString(reply)));
            return;
        }

        // --------------------------
        // RELAY (OFFER / ANSWER / ICE)
        // --------------------------
        String roomId = sessionRoom.get(session.getId());
        if (roomId == null) return;

        for (WebSocketSession s : rooms.get(roomId)) {
            if (!s.getId().equals(session.getId())) {

                // Add "from" to the message
                var relay = mapper.readValue(message.getPayload(), Map.class);
                relay.put("from", session.getId());

                s.sendMessage(new TextMessage(mapper.writeValueAsString(relay)));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = sessionRoom.get(session.getId());
        if (roomId != null) {
            rooms.get(roomId).remove(session);
            sessionRoom.remove(session.getId());

            // broadcast leave event
            Map<String, Object> msg = Map.of(
                    "type", "leave",
                    "from", session.getId()
            );

            for (WebSocketSession s : rooms.get(roomId)) {
                s.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
            }
        }
    }
}
