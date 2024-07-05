package com.example.tws;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TimeController{
    @GetMapping("/api/time")
    public Map<String, String> getCurrentTime(){
        Map<String, String> response = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        String formattedDateTime = now.format(DateTimeFormatter.ISO_DATE_TIME);
        response.put("currentTime",formattedDateTime);
        return response;
    }
}