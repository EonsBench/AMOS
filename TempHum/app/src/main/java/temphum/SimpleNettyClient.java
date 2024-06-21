package temphum;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleNettyClient {
    private static final Logger logger = LoggerFactory.getLogger(SimpleNettyClient.class);

    private String host;
    private int port;
    private volatile boolean messageReceived;
    private int noMessageCount;
    private static final int MAX_NO_MESSAGE_COUNT = 10;
    public SimpleNettyClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.noMessageCount=0;
        logger.debug("SimpleNettyClient initialized with host: {} and port: {}", host, port);
    }
    public boolean isMessageReceived() {
        return messageReceived;
    }

    public void setMessageReceived(boolean messageReceived) {
        this.messageReceived = messageReceived;
    }
    public void connect() throws InterruptedException {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        ScheduledExecutorService watchdog = Executors.newScheduledThreadPool(1);
        try {
            Bootstrap bootstrap = new Bootstrap().group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new MainQueueHandler(SimpleNettyClient.this));
                        logger.debug("Channel initialized with SimpleQueueHandler");
                    }
                });

            logger.info("Attempting to connect to server at {}:{}", host, port);
            ChannelFuture future = bootstrap.connect(host, port).sync();
            if (future.isSuccess()) {
                logger.info("Server connect success on port: {}", port);
                scheduleWatchdog(watchdog, future);
            } else {
                logger.warn("Server connect attempt failed on port: {}", port);
            }
            scheduleMessageCheck(eventLoopGroup);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("An error occurred while connecting to the server", e);
        } finally {
            eventLoopGroup.shutdownGracefully();
            watchdog.shutdown();
            logger.info("EventLoopGroup shutdown gracefully");
        }
    }
    private void scheduleMessageCheck(EventLoopGroup group) {
        group.scheduleAtFixedRate(() -> {
            if (!messageReceived) {
                noMessageCount++;
                logger.warn("No message received for the last period. Count: {}", noMessageCount);
                if (noMessageCount >= MAX_NO_MESSAGE_COUNT) {
                    logger.warn("No messages received for 10 periods. Reconnecting...");
                    reconnect();
                    noMessageCount=0;
                }
            } else {
                noMessageCount = 0;
            }
            messageReceived = false;
        }, 1, 1, TimeUnit.MINUTES);
    }
    private void scheduleWatchdog(ScheduledExecutorService watchdog, ChannelFuture future) {
        watchdog.scheduleAtFixedRate(() -> {
            if (!messageReceived) {
                logger.warn("No message received for the last period.");
                // Add additional logic if needed to handle unresponsiveness
            }
            messageReceived = false;

            // Additionally, check if the future is still done or not
            if (future.isDone() && !future.isSuccess()) {
                logger.error("ChannelFuture is done but unsuccessful.");
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void reconnect() {
        try {
            connect();
        } catch (InterruptedException e) {
            logger.error("Error while reconnecting", e);
        }
    }
    public static void main(String[] args) throws Exception {
        int port = 1234;
        String host = "ip";
        SimpleNettyClient client = new SimpleNettyClient(host, port);
        logger.info("Starting SimpleNettyClient");
        client.connect();
        logger.info("SimpleNettyClient finished");
    }
}
