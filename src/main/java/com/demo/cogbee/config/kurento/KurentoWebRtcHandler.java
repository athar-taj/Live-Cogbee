package com.demo.cogbee.config.kurento;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.client.MediaPipeline;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KurentoWebRtcHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    // sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // roomId -> KurentoRoom
    private final Map<String, KurentoRoom> rooms = new ConcurrentHashMap<>();
    // sessionId -> roomId
    private final Map<String, String> userRooms = new ConcurrentHashMap<>();
    // sessionId -> queued ICE candidates before endpoint is ready
    private final Map<String, List<IceCandidate>> candidateQueue = new ConcurrentHashMap<>();

    // ---- create a NEW KurentoClient each time (no Spring bean) ----
    private KurentoClient createClient() {
        // change URL if your KMS is not on localhost
        return KurentoClient.create("ws://localhost:8888/kurento");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String id = session.getId();
        sessions.put(id, session);

        session.sendMessage(new TextMessage("{\"type\":\"id\",\"id\":\"" + id + "\"}"));
        System.out.println("Kurento WS client connected: " + id);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        System.out.println(">>> RAW MESSAGE FROM CLIENT: " + message.getPayload());

        Map<String, Object> data = mapper.readValue(message.getPayload(), Map.class);

        String type = (String) data.get("type");
        String sessionId = session.getId();

        if (type == null) return;

        switch (type) {
            case "join" -> handleJoin(session, data);
            case "offer" -> handleOffer(session, data);
            case "candidate" -> handleCandidate(session, data);
            case "leave" -> handleLeave(sessionId);
            default -> System.out.println("Unknown Kurento message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        handleLeave(sessionId);
        sessions.remove(sessionId);
        System.out.println("Kurento WS client disconnected: " + sessionId);
    }

    // ------------------ JOIN ROOM ------------------
    private void handleJoin(WebSocketSession session, Map<String, Object> data) throws Exception {
        String sessionId = session.getId();
        String roomId = (String) data.get("roomId");
        if (roomId == null || roomId.isBlank()) {
            System.out.println("join without roomId from " + sessionId);
            return;
        }

        // Create room IF ABSENT with its own KurentoClient + MediaPipeline
        KurentoRoom room = rooms.computeIfAbsent(roomId, id -> {
            KurentoClient kc = createClient();
            MediaPipeline pipeline = kc.createMediaPipeline();
            System.out.println("Created new Kurento room " + id + " with dedicated KurentoClient & pipeline");
            return new KurentoRoom(id, kc, pipeline);
        });

        userRooms.put(sessionId, roomId);

        // tell client joined ok
        session.sendMessage(new TextMessage(
                mapper.writeValueAsString(Map.of(
                        "type", "joined",
                        "roomId", roomId
                ))
        ));

        System.out.println("User " + sessionId + " joined Kurento room " + roomId);
    }

    // ------------------ HANDLE OFFER ------------------
    private void handleOffer(WebSocketSession session, Map<String, Object> data) throws Exception {
        String sessionId = session.getId();
        String roomId = userRooms.get(sessionId);
        if (roomId == null) {
            System.out.println("offer from user not in room: " + sessionId);
            return;
        }

        String sdpOffer = (String) data.get("offer");
        if (sdpOffer == null) {
            System.out.println("Missing sdp offer from " + sessionId);
            return;
        }

        KurentoRoom room = rooms.get(roomId);
        if (room == null) {
            System.out.println("No room for offer: " + roomId);
            return;
        }

        WebRtcEndpoint endpoint = room.getEndpoint(sessionId);
        boolean isNew = false;

        // ---------------- CREATE ENDPOINT ----------------
        if (endpoint == null) {
            endpoint = new WebRtcEndpoint.Builder(room.getPipeline()).build();
            room.addParticipant(sessionId, endpoint);
            isNew = true;

            System.out.println("Created new WebRtcEndpoint for: " + sessionId);

            // ---------------- LISTEN TO ICE FROM KURENTO ----------------
            WebRtcEndpoint finalEndpoint = endpoint;
            endpoint.addIceCandidateFoundListener(event -> {
                try {
                    Map<String, Object> candidateMsg = Map.of(
                            "type", "candidate",
                            "candidate", Map.of(
                                    "candidate", event.getCandidate().getCandidate(),
                                    "sdpMid", event.getCandidate().getSdpMid(),
                                    "sdpMLineIndex", event.getCandidate().getSdpMLineIndex()
                            )
                    );

                    // Send back to THIS session
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(candidateMsg)));
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        finalEndpoint.release();
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        // ---------------- WIRE WITH OTHER USERS IN ROOM ----------------
        if (isNew) {
            for (Map.Entry<String, WebRtcEndpoint> entry : room.getParticipants().entrySet()) {
                String otherId = entry.getKey();
                WebRtcEndpoint otherEp = entry.getValue();

                if (otherId.equals(sessionId)) continue;

                System.out.println("Connecting " + sessionId + " <--> " + otherId);

                endpoint.connect(otherEp);
                otherEp.connect(endpoint);
            }
        }

        // ---------------- PROCESS OFFER ----------------
        String sdpAnswer = endpoint.processOffer(sdpOffer);

        // -- SEND ANSWER BACK TO CLIENT --
        session.sendMessage(new TextMessage(mapper.writeValueAsString(
                Map.of("type", "answer", "answer", sdpAnswer)
        )));

        // ---------------- FLUSH QUEUED ICE ----------------
        List<IceCandidate> queued = candidateQueue.get(sessionId);
        if (queued != null) {
            System.out.println("Flushing " + queued.size() + " queued ICE candidates for " + sessionId);
            queued.forEach(endpoint::addIceCandidate);
            candidateQueue.remove(sessionId);
        }

        // Start ICE gathering
        endpoint.gatherCandidates();
    }

    // ------------------ HANDLE ICE CANDIDATE ------------------
    @SuppressWarnings("unchecked")
    private void handleCandidate(WebSocketSession session, Map<String, Object> data) {
        String sessionId = session.getId();
        String roomId = userRooms.get(sessionId);
        if (roomId == null) return;

        KurentoRoom room = rooms.get(roomId);
        if (room == null) return;

        WebRtcEndpoint endpoint = room.getEndpoint(sessionId);

        Map<String, Object> cand = (Map<String, Object>) data.get("candidate");
        if (cand == null) return;

        IceCandidate candidate = new IceCandidate(
                (String) cand.get("candidate"),
                (String) cand.get("sdpMid"),
                (Integer) cand.get("sdpMLineIndex")
        );

        // ---------------- IF ENDPOINT NOT READY → QUEUE ----------------
        if (endpoint == null) {
            System.out.println("Queueing ICE candidate for " + sessionId);

            candidateQueue.putIfAbsent(sessionId, new ArrayList<>());
            candidateQueue.get(sessionId).add(candidate);
            return;
        }

        // ---------------- ENDPOINT READY → ADD DIRECTLY ----------------
        endpoint.addIceCandidate(candidate);
    }

    // ------------------ LEAVE ------------------
    private void handleLeave(String sessionId) {
        String roomId = userRooms.remove(sessionId);
        if (roomId == null) return;

        KurentoRoom room = rooms.get(roomId);
        if (room == null) return;

        room.removeParticipant(sessionId);

        if (room.getParticipants().isEmpty()) {
            room.close();
            rooms.remove(roomId);
            System.out.println("Room " + roomId + " destroyed (empty)");
        } else {
            System.out.println("User " + sessionId + " left room " + roomId);
        }
    }
}
