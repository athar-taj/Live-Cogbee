package com.demo.cogbee.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    @GetMapping("/create-room")
    public Map<String, String> createRoom(
            @RequestParam String caller,
            @RequestParam String receiver) {

        String roomName = "room_" + System.currentTimeMillis() + "_" + caller + "_" + receiver;

        Map<String, String> response = new HashMap<>();
        response.put("roomName", roomName);
        response.put("serverUrl", "https://meet.jit.si");

        return response;
    }
}
