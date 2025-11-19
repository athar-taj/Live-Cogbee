package com.demo.cogbee.controller;

import com.demo.cogbee.model.CallMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class CallController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/call")
    public void sendCall(CallMessage callMessage) {
        messagingTemplate.convertAndSend("/topic/call/" + callMessage.getReceiverId(), callMessage);
    }
}

