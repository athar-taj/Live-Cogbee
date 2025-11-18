package com.demo.cogbee.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebRtcSignalingHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    // Connection id -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * roomId -> RoomState
     */
    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();

    /**
     * userId -> roomId
     * Helps us quickly find which room to broadcast to.
     */
    private final Map<String, String> userRooms = new ConcurrentHashMap<>();

    /**
     * Room metadata: users + who is host/interviewer + meeting flags
     */
    private static class RoomState {
        final Set<String> users = ConcurrentHashMap.newKeySet();
        String hostId;               // first user becomes host by default
        boolean interviewStarted = false;
    }

    // ----------------------------------------------------------------
    //  CONNECTION LIFECYCLE
    // ----------------------------------------------------------------
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String id = UUID.randomUUID().toString();
        sessions.put(id, session);
        session.getAttributes().put("userId", id);

        // Send the client its own id
        session.sendMessage(new TextMessage(
                "{\"type\":\"id\",\"id\":\"" + id + "\"}"
        ));

        System.out.println("Client connected: " + id);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> data = mapper.readValue(message.getPayload(), Map.class);

        String type = (String) data.get("type");
        String userId = (String) session.getAttributes().get("userId");

        if (type == null || userId == null) {
            return;
        }

        switch (type) {
            case "join" -> handleJoin(userId, data, session);
            case "offer", "answer", "candidate" -> forwardToTarget(data, userId);
            case "meeting_event" -> handleMeetingEvent(userId, data);
            case "subtitle" -> handleSubtitle(userId, data); // for STT / captions
            default -> System.out.println("Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");

        if (userId != null) {
            sessions.remove(userId);

            // Find the room this user was in
            String roomId = userRooms.remove(userId);
            if (roomId != null) {
                RoomState room = rooms.get(roomId);
                if (room != null) {
                    room.users.remove(userId);

                    // If host leaves, pick a new host if possible
                    if (userId.equals(room.hostId) && !room.users.isEmpty()) {
                        String newHost = room.users.iterator().next();
                        room.hostId = newHost;

                        // Notify room that host changed
                        broadcastToRoom(roomId, Map.of(
                                "type", "host_changed",
                                "hostId", newHost
                        ));
                    }

                    // If room is now empty, clean it up
                    if (room.users.isEmpty()) {
                        rooms.remove(roomId);
                    }

                    // Inform peers in *that room only*
                    broadcastLeaveToRoom(roomId, userId);
                }
            }
        }

        System.out.println("Client disconnected: " + userId);
    }

    // ----------------------------------------------------------------
    //  ROOM JOIN / LEAVE
    // ----------------------------------------------------------------
    private void handleJoin(String userId, Map<String, Object> data, WebSocketSession session) throws Exception {
        String roomId = (String) data.get("roomId");
        if (roomId == null || roomId.isBlank()) {
            System.out.println("join without roomId from user: " + userId);
            return;
        }

        rooms.putIfAbsent(roomId, new RoomState());
        RoomState room = rooms.get(roomId);

        // First user becomes host by default
        if (room.hostId == null) {
            room.hostId = userId;
        }

        room.users.add(userId);
        userRooms.put(userId, roomId);

        // 1️⃣ Send list of existing peers to the new user
        List<String> peers = room.users.stream()
                .filter(uid -> !uid.equals(userId))
                .toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "peers");
        payload.put("peers", peers);
        payload.put("hostId", room.hostId); // so frontend can know who is host
        payload.put("role", userId.equals(room.hostId) ? "host" : "participant");

        session.sendMessage(new TextMessage(
                mapper.writeValueAsString(payload)
        ));

        // 2️⃣ Notify all existing peers that a new one joined
        Map<String, Object> newPeerMsg = Map.of(
                "type", "new_peer",
                "peerId", userId
        );

        broadcastToRoom(roomId, newPeerMsg, Set.of(userId)); // exclude the new user

        System.out.println("User " + userId + " joined room " + roomId);
    }

    private void broadcastLeaveToRoom(String roomId, String userId) {
        Map<String, Object> msg = Map.of(
                "type", "leave",
                "from", userId
        );
        broadcastToRoom(roomId, msg);
    }

    // ----------------------------------------------------------------
    //  WEBRTC FORWARDING
    // ----------------------------------------------------------------
    private void forwardToTarget(Map<String, Object> data, String senderId) throws Exception {
        data.put("from", senderId);

        String targetId = (String) data.get("to");
        WebSocketSession target = sessions.get(targetId);

        if (target != null && target.isOpen()) {
            target.sendMessage(new TextMessage(mapper.writeValueAsString(data)));
        }
    }



    // ----------------------------------------------------------------
    //  MEETING EVENTS (START/STOP, QUESTION, MUTE, ETC.)
    // ----------------------------------------------------------------
    private void handleMeetingEvent(String userId, Map<String, Object> data) throws Exception {
        String roomId = userRooms.get(userId);
        if (roomId == null) return;

        RoomState room = rooms.get(roomId);
        if (room == null) return;

        // Only host can trigger interview-level events
        if (!userId.equals(room.hostId)) {
            System.out.println("Non-host tried to send meeting_event: " + userId);
            return;
        }

        String event = (String) data.get("event");
        if (event == null) return;

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "meeting_event");
        msg.put("event", event);

        // Optional fields (question text, etc.)
        if (data.containsKey("question")) {
            msg.put("question", data.get("question"));
        }
        if (data.containsKey("targetUserId")) {
            msg.put("targetUserId", data.get("targetUserId"));
        }

        // Maintain interviewStarted flag for this room
        if ("start".equals(event)) {
            room.interviewStarted = true;
        } else if ("end".equals(event)) {
            room.interviewStarted = false;
        }

        broadcastToRoom(roomId, msg);
    }

    // ----------------------------------------------------------------
    //  SUBTITLE / STT EVENTS
    // ----------------------------------------------------------------
    private void handleSubtitle(String userId, Map<String, Object> data) throws Exception {
        String roomId = userRooms.get(userId);
        if (roomId == null) return;

        String text = (String) data.get("text");
        if (text == null) return;

        Map<String, Object> msg = Map.of(
                "type", "subtitle",
                "from", userId,
                "text", text
        );

        broadcastToRoom(roomId, msg);
    }

    // ----------------------------------------------------------------
    //  BROADCAST HELPERS
    // ----------------------------------------------------------------
    private void broadcastToRoom(String roomId, Map<String, Object> payload) {
        broadcastToRoom(roomId, payload, Collections.emptySet());
    }

    private void broadcastToRoom(String roomId,
                                 Map<String, Object> payload,
                                 Set<String> excludeUserIds) {
        RoomState room = rooms.get(roomId);
        if (room == null) return;

        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        for (String uid : room.users) {
            if (excludeUserIds.contains(uid)) continue;

            WebSocketSession s = sessions.get(uid);
            if (s != null && s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(json));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
