package queueDB;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Guset {

    private final static Logger logger = LoggerFactory.getLogger(Guset.class);

    private final static String QUEUE_NAME = "queueName";
    private static String windvalue1 = "";
    private static String windvalue2 = "";
    private static String thvalue1 = "";
    private static String thvalue2 = "";

    private static final String DB_URL = "jdbc:mariadb://localhost:3306/Sensor";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    private static final List<Double> windspeedList = new CopyOnWriteArrayList<>();
    private static final List<Double> windDirectionList = new CopyOnWriteArrayList<>();
    private static final JSONObject jo = new JSONObject();

    private static long lastDataReceivedTime = System.currentTimeMillis();

    private static void insertData(String[] data) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "INSERT INTO MultySensor(windSpeed, windDirect, temperature, humidity) VALUES(?,?,?,?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < data.length; i++) {
                    pstmt.setString(i + 1, data[i]);
                }
                pstmt.execute();
                logger.debug("Data inserted into database: {}", (Object) data);
            }
        } catch (Exception e) {
            logger.error("Failed to insert data into database", e);
        }
    }

    private static void calculateList() {
        double min = windspeedList.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = windspeedList.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double avgtemp = windspeedList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avg = Math.round(avgtemp * 1000) / 1000.0;
        jo.put("min", min);
        jo.put("max", max);
        jo.put("avg", avg);
        logger.debug("Calculated wind speed list - min: {}, max: {}, avg: {}", min, max, avg);
    }

    private static void calculateWDList() {
        double wdmin = windDirectionList.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double wdmax = windDirectionList.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double temp = windDirectionList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double wdavg = Math.round(temp);
        jo.put("wdmin", wdmin);
        jo.put("wdmax", wdmax);
        jo.put("wdavg", wdavg);
        logger.debug("Calculated wind direction list - wdmin: {}, wdmax: {}, wdavg: {}", wdmin, wdmax, wdavg);
    }

    private static void checkDataReception() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastData = currentTime - lastDataReceivedTime;

        if (timeSinceLastData > 60000) { // 1 minute timeout
            logger.warn("No data received for over 1 minute.");
        }
    }

    public static void main(String[] args) {
        int rabbit_port = 5672;
        String username = "rabbit00";
        String password = "password";

        String exchangeName = "amq.direct";
        String Key = "routingkey";
        String Key2 = "tempkey";

        String broker = "tcp://localhost:1883";
        String topic = "test";
        String clientId = "guest";

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("192.168.0.196");
            factory.setPort(rabbit_port);
            factory.setUsername(username);
            factory.setPassword(password);
            com.rabbitmq.client.Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, true);
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            channel.queueBind(QUEUE_NAME, exchangeName, Key);
            channel.queueBind(QUEUE_NAME, exchangeName, Key2);
            logger.info("Waiting for messages...");

            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String routingKey = envelope.getRoutingKey();
                    String message = new String(body, "UTF-8");
                    logger.info("Received message with routing key '{}': '{}'", routingKey, message);

                    lastDataReceivedTime = System.currentTimeMillis();

                    if (routingKey.equals("routingkey")) {
                        String[] windvalue = message.split(",");
                        windvalue1 = windvalue[0].replace("$", "");
                        windvalue2 = windvalue[1];
                        synchronized (windspeedList) {
                            if (windspeedList.size() >= 60) {
                                windspeedList.remove(0);
                            }
                            windspeedList.add(Double.parseDouble(windvalue1));
                        }
                        logger.debug("Parsed wind value data: {}, {}", windvalue[0], windvalue[1]);
                        synchronized(windDirectionList){
                            if(windDirectionList.size()>=60){
                                windDirectionList.remove(0);
                            }
                            windDirectionList.add(Double.parseDouble(windvalue2));
                        }
                    } else if (routingKey.equals("tempkey")) {
                        String[] thvalue = message.split("\\s");
                        thvalue1 = thvalue[1];
                        thvalue2 = thvalue[4];
                        logger.debug("Parsed temperature and humidity data: {}, {}", thvalue[1], thvalue[4]);
                    }
                }
            };

            channel.basicConsume(QUEUE_NAME, true, consumer);

            ScheduledExecutorService calcScheduler = Executors.newScheduledThreadPool(1);
            calcScheduler.scheduleAtFixedRate(() -> {
                try {
                    calculateList();
                    calculateWDList();
                } catch (Exception e) {
                    logger.error("Failed to calculate lists", e);
                }
            }, 1, 1, TimeUnit.MINUTES);

            ScheduledExecutorService dataScheduler = Executors.newScheduledThreadPool(1);
            dataScheduler.scheduleAtFixedRate(() -> {
                try {
                    ArrayList<String> dataList = new ArrayList<>();
                    dataList.add(windvalue2);
                    dataList.add(windvalue1);
                    dataList.add(thvalue2);
                    dataList.add(thvalue1);
                    String[] dataArray = dataList.toArray(new String[0]);
                    insertData(dataArray);
                    MqttClient client = new MqttClient(broker, clientId);
                    client.connect();
                    jo.put("windspeed", windvalue1);
                    jo.put("winddirect", windvalue2);
                    jo.put("hum", thvalue1);
                    jo.put("temp", thvalue2);

                    String messageContent = jo.toString();
                    MqttMessage msg = new MqttMessage(messageContent.getBytes());
                    msg.setQos(1);
                    client.publish(topic, msg);
                    logger.info("Message sent to MQTT: {}", messageContent);
                    client.disconnect();
                } catch (Exception e) {
                    logger.error("Failed to process and send data", e);
                }
            }, 0, 2, TimeUnit.SECONDS);

            ScheduledExecutorService monitorScheduler = Executors.newScheduledThreadPool(1);
            monitorScheduler.scheduleAtFixedRate(Guset::checkDataReception, 0, 1, TimeUnit.MINUTES);

        } catch (Exception e) {
            logger.error("Failed to set up RabbitMQ or MQTT connection", e);
        }
    }
}
