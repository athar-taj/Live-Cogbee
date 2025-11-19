package com.demo.cogbee.model;

import lombok.Data;

@Data
public class CallMessage {
    private String callerId;
    private String receiverId;
    private String roomName;
}
