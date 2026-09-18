package com.group.restaurant.controllers;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * A3 — WebSocket message handler (STOMP).
 * Client có thể gửi ping để kiểm tra kết nối.
 */
@Controller
public class WebSocketController {

    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public String ping(String message) {
        return "pong: " + message;
    }
}
