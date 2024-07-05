package com.example.tws;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class MqttSubscriber {
    private final String brokerUrl = "tcp://localhost:1883";
    private final String topic = "test";
    private MqttClient client;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public MqttSubscriber() {
        //MqttClient client;
        try {
            client = new MqttClient(brokerUrl, MqttClient.generateClientId());
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                    System.out.println("Connection lost!");
                }

                @Override
                public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                    String message = new String(mqttMessage.getPayload());
                    JSONObject jo = new JSONObject(message);

                    System.out.println("Message received: " + message);
                    messagingTemplate.convertAndSend("/topic/mqtt", message); // 데이터를 컨트롤러로 전달
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                }
            });

            client.connect();
            client.subscribe(topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    @PreDestroy
    public void cleanUp(){
        try{
            if(client!=null&&client.isConnected()){
                client.disconnect();
                System.out.println("Disconnected Mqtt");
            }
        }catch(MqttException e){
            e.printStackTrace();
        }
    }
}
