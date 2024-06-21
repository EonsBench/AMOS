package temphum;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainQueueHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SimpleQueueHandler.class);
    private final SimpleNettyClient client;
    private final ConnectionFactory factory;
    private Connection connection;
    private Channel channel;
    String host = "localhost";
    int port = 5672;
    String username = "rabbit00";
    String password = "password";

    String exchangeName = "amq.direct";
    String routingKey = "tempkey";
    public MainQueueHandler(SimpleNettyClient client) {
        this.client = client;
        factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);

                // 연결 및 채널 초기화
        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            logger.debug("Connecting to RabbitMQ at {}:{}", host, port);
        } catch (IOException | TimeoutException e) {
            logger.error("RabbitMQ Connect Failed", e);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        String receivedMessage = ((ByteBuf) msg).toString(io.netty.util.CharsetUtil.UTF_8);
        client.setMessageReceived(true);
        logger.info("Received Message: {}", receivedMessage);
        insertData(receivedMessage);
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.warn("Channel inactive, connection lost.");
        super.channelInactive(ctx);
    }
    private void insertData(String data) {
        try {
            byte[] body = data.getBytes();
            logger.debug("Publishing message to exchange {} with routing key {}", exchangeName, routingKey);
            channel.basicPublish(exchangeName, routingKey, null, body);
            logger.info("Message published Success");
        } catch (Exception e) {
            logger.error("An error occurred while inserting data", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught in channel handler", cause);
        ctx.close();
    }
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException | TimeoutException e) {
            logger.error("RabbitMQ Resource error", e);
        }
    }
}
