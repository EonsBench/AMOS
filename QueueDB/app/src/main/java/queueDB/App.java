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

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;

public class App {

    private final static Logger logger = LoggerFactory.getLogger(App.class);

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

    private static final String token = "eNmt0dffr6Rr7dCjaK9PQePcIyorV86z78BSqtccLXxd764BOIW0klj26wpij1rGhdquHmqCxNpy51FxaqIvvg==";
    private static final String bucket = "Monitoring";
    private static final String org = "org-name";

    private static InfluxDBClient influxClient;
    static{
        influxClient = InfluxDBClientFactory.create("http://localhost:8086", token.toCharArray(), org, bucket);
    }
    

    private static long lastDataReceivedTime = System.currentTimeMillis();
    private static ConnectionFactory factory;
    private static com.rabbitmq.client.Connection connection;
    private static Channel channel;

    private static void insertDB(String[] data){
        try{
            WriteApiBlocking writeApi = influxClient.getWriteApiBlocking();
            Point point = Point.measurement("MultySensor")
            .addField("windSpeed", roundToTwoDecimalPlaces(Double.parseDouble(data[0])))
            .addField("windDirect", roundToTwoDecimalPlaces(Double.parseDouble(data[1])))
            .addField("temperature", Double.parseDouble(data[2]))
            .addField("humidity", Double.parseDouble(data[3]))
            .time(System.currentTimeMillis(),WritePrecision.MS);
            writeApi.writePoint(point);
            logger.debug("Data inserted into DB: {}",(Object) data);
        }catch (Exception e){
            logger.error("Failed to insert Data",e);
        }
    }

    private static double roundToTwoDecimalPlaces(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static void calculateList() {
        double min = windspeedList.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = windspeedList.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double avgtemp = windspeedList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        jo.put("min", roundToTwoDecimalPlaces(min));
        jo.put("max", roundToTwoDecimalPlaces(max));
        jo.put("avg", roundToTwoDecimalPlaces(avgtemp));
        logger.debug("Calculated wind speed list - min: {}, max: {}, avg: {}", min, max, avgtemp);
    }

    private static void calculateWDList() {
        double wdmin = windDirectionList.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double wdmax = windDirectionList.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double temp = windDirectionList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double wdavg = roundToTwoDecimalPlaces(temp);
        jo.put("wdmin", roundToTwoDecimalPlaces(wdmin));
        jo.put("wdmax", roundToTwoDecimalPlaces(wdmax));
        jo.put("wdavg", wdavg);
        logger.debug("Calculated wind direction list - wdmin: {}, wdmax: {}, wdavg: {}", wdmin, wdmax, wdavg);
    }

    private static void checkDataReception() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastData = currentTime - lastDataReceivedTime;
    
        if (timeSinceLastData > 6000) { // 1 minute timeout
            logger.warn("No data received for over 1 minute, reconnecting...");
    
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close(); // Close the channel if it's open
                }
                if (connection != null && connection.isOpen()) {
                    connection.close(); // Close the connection if it's open
                }
    
                // Reconnect
                setupRabbitMQConnection();
            } catch (Exception e) {
                logger.error("Failed to reconnect to RabbitMQ", e);
            }
        }
    }
    

    private static void setupRabbitMQConnection() throws Exception {
        factory = new ConnectionFactory();
        factory.setHost("192.168.0.196");
        factory.setPort(5672);
        factory.setUsername("rabbit00");
        factory.setPassword("password");
        connection = factory.newConnection();
        channel = connection.createChannel();

        String exchangeName = "amq.direct";
        String key = "routingkey";
        String key2 = "tempkey";

        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, true);
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        channel.queueBind(QUEUE_NAME, exchangeName, key);
        channel.queueBind(QUEUE_NAME, exchangeName, key2);
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
                    windvalue1 = windvalue[0].replace("$0", "");
                    windvalue2 = windvalue[1];
                    synchronized (windspeedList) {
                        if (windspeedList.size() >= 60) {
                            windspeedList.remove(0);
                        }
                        windspeedList.add(roundToTwoDecimalPlaces(Double.parseDouble(windvalue1)));
                    }
                    logger.debug("Parsed wind value data: {}, {}", windvalue[0], windvalue[1]);
                    synchronized (windDirectionList) {
                        if (windDirectionList.size() >= 60) {
                            windDirectionList.remove(0);
                        }
                        windDirectionList.add(roundToTwoDecimalPlaces(Double.parseDouble(windvalue2)));
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
    }

    public static void main(String[] args) {
        try {
            setupRabbitMQConnection();

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
                    dataList.add(windvalue1);
                    dataList.add(windvalue2);
                    dataList.add(thvalue2);
                    dataList.add(thvalue1);
                    String[] dataArray = dataList.toArray(new String[0]);
                    insertDB(dataArray);
                    MqttClient client = new MqttClient("tcp://localhost:1883", "guest");
                    client.connect();
                    jo.put("windspeed", windvalue1);
                    jo.put("winddirect", windvalue2);
                    jo.put("hum", thvalue1);
                    jo.put("temp", thvalue2);

                    String messageContent = jo.toString();
                    MqttMessage msg = new MqttMessage(messageContent.getBytes());
                    msg.setQos(1);
                    client.publish("test", msg);
                    logger.info("Message sent to MQTT: {}", messageContent);
                    client.disconnect();
                } catch (Exception e) {
                    logger.error("Failed to process and send data", e);
                }
            }, 0, 2, TimeUnit.SECONDS);

            ScheduledExecutorService monitorScheduler = Executors.newScheduledThreadPool(1);
            monitorScheduler.scheduleAtFixedRate(App::checkDataReception, 0, 1, TimeUnit.MINUTES);

        } catch (Exception e) {
            logger.error("Failed to set up RabbitMQ or MQTT connection", e);
        }
    }
}
