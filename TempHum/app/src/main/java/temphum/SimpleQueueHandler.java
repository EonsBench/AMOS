package temphum;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleQueueHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SimpleQueueHandler.class);
    private final SimpleNettyClient client;
    String host = "localhost";
    int port = 5672;
    String username = "rabbit00";
    String password = "password";

    String exchangeName = "amq.direct";
    String routingKey = "tempkey";
    public SimpleQueueHandler(SimpleNettyClient client) {
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
        Connection connection = null;
        Channel channel = null;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);

            logger.debug("Connecting to RabbitMQ at {}:{}", host, port);
            connection = factory.newConnection();
            channel = connection.createChannel();

            byte[] body = data.getBytes();
            logger.debug("Publishing message to exchange {} with routing key {}", exchangeName, routingKey);
            channel.basicPublish(exchangeName, routingKey, null, body);

            channel.close();
            connection.close();
            logger.info("Message published and connection closed");
        } catch (Exception e) {
            logger.error("An error occurred while inserting data", e);
        } finally {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                    logger.warn("Channel was open and has been closed in finally block");
                } catch (Exception e) {
                    logger.error("Failed to close channel in finally block", e);
                }
            }
            if (connection != null && connection.isOpen()) {
                try {
                    connection.close();
                    logger.warn("Connection was open and has been closed in finally block");
                } catch (Exception e) {
                    logger.error("Failed to close connection in finally block", e);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught in channel handler", cause);
        ctx.close();
    }
}
