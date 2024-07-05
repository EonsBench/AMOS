package com.example.tws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MessageController {
    //private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    /*@MessageMapping("/send")
    @SendTo("/topic/messages")
    public String sendMessage(String message) {
        return message;
    }*/
    /*@Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Scheduled(fixedRate=10000)
    public void sendScheduledMessage(){
        logger.debug("Sending scheduled message...");
        messagingTemplate.convertAndSend("/topic/messages", "Server message");
        logger.debug("Scheduled message sent.");
    }*/
    @GetMapping("/")
    public String index(Model model) {
        // 기본 메시지
        model.addAttribute("message", "Waiting for MQTT message...");

        return "index";
    }

    // MQTT 메시지를 받는 엔드포인트
    @MessageMapping("/mqtt")
    @SendTo("/topic/mqtt")
    public String getMqttMessage(String message) {
        // 받은 MQTT 메시지를 모델에 추가하여 UI에 전달
        return message;
    }    
}
