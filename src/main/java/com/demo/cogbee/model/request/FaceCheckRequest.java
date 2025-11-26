package com.demo.cogbee.model.request;

public class FaceCheckRequest {
    private String sessionId;
    private String frame;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getFrame() { return frame; }
    public void setFrame(String frame) { this.frame = frame; }
}
