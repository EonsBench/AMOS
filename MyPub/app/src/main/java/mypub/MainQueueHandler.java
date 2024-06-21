package mypub;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class MainQueueHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MainQueueHandler.class);
    private final SimpleNettyClient client;

    String host = "localhost";
    int port = 5672;
    String username = "rabbit00";
    String password = "password";

    String exchangeName = "amq.direct";
    String routingKey = "routingkey";

    public MainQueueHandler(SimpleNettyClient client) {
        this.client = client;
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
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            byte[] body = data.getBytes();
            logger.debug("Publishing message to exchange {} with routing key {}", exchangeName, routingKey);
            channel.basicPublish(exchangeName, routingKey, null, body);
            logger.info("Message published successfully");
        } catch (IOException | TimeoutException e) {
            logger.error("An error occurred while inserting data", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught in channel handler", cause);
        ctx.close();
    }
}
