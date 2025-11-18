package com.demo.cogbee.config.kurento;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KurentoRoom {

    private final String roomId;
    private final KurentoClient kurentoClient;
    private final MediaPipeline pipeline;
    private final Map<String, WebRtcEndpoint> participants = new ConcurrentHashMap<>();

    public KurentoRoom(String roomId, KurentoClient kurentoClient, MediaPipeline pipeline) {
        this.roomId = roomId;
        this.kurentoClient = kurentoClient;
        this.pipeline = pipeline;
    }

    public String getRoomId() {
        return roomId;
    }

    public MediaPipeline getPipeline() {
        return pipeline;
    }

    public Map<String, WebRtcEndpoint> getParticipants() {
        return participants;
    }

    public WebRtcEndpoint getEndpoint(String sessionId) {
        return participants.get(sessionId);
    }

    public void addParticipant(String sessionId, WebRtcEndpoint endpoint) {
        participants.put(sessionId, endpoint);
    }

    public void removeParticipant(String sessionId) {
        WebRtcEndpoint ep = participants.remove(sessionId);
        if (ep != null) {
            try {
                ep.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        // release endpoints
        participants.values().forEach(ep -> {
            try {
                ep.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        participants.clear();

        // release pipeline
        try {
            pipeline.release();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // destroy KurentoClient
        try {
            kurentoClient.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("KurentoRoom " + roomId + " closed (pipeline + client destroyed)");
    }
}
