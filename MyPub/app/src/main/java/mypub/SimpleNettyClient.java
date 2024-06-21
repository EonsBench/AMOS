package mypub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class SimpleNettyClient {
    private static final Logger logger = LoggerFactory.getLogger(SimpleNettyClient.class);
    private String host;
    private int port;
    private volatile boolean messageReceived;
    private final long RECONNECT_TIMEOUT = TimeUnit.MINUTES.toMillis(1);
    private int count=0;
    private static final int MAX_RECONNECT_COUNT=10;
    public SimpleNettyClient(String host, int port) {
        this.host = host;
        this.port = port;
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
        ScheduledExecutorService reconnectScheduler = Executors.newScheduledThreadPool(1);
        try {
            Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new MainQueueHandler(SimpleNettyClient.this));
                        logger.debug("Channel initialized with MainQueueHandler");
                    }
                });

            logger.info("Attempting to connect to server at {}:{}", host, port);
            ChannelFuture future = bootstrap.connect(host, port).sync();
            if (future.isSuccess()) {
                logger.info("Server connect success on port: {}", port);
                scheduleReconnect(reconnectScheduler, eventLoopGroup);
            } else {
                logger.warn("Server connect attempt failed on port: {}", port);
            }
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("An error occurred while connecting to the server", e);
        } finally {
            eventLoopGroup.shutdownGracefully();
            reconnectScheduler.shutdown();
            logger.info("EventLoopGroup shutdown gracefully");
        }
    }

    private void scheduleReconnect(ScheduledExecutorService reconnectScheduler, EventLoopGroup eventLoopGroup) {
        reconnectScheduler.scheduleAtFixedRate(() -> {
            if (!messageReceived) {
                logger.warn("No message received for the last {} minutes, reconnecting...", TimeUnit.MILLISECONDS.toMinutes(RECONNECT_TIMEOUT));
                reconnect(eventLoopGroup);
            } else {
                messageReceived = false;
            }
        }, RECONNECT_TIMEOUT, RECONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    private void reconnect(EventLoopGroup eventLoopGroup) {
        try {
            if(count<MAX_RECONNECT_COUNT){
                count++;
                eventLoopGroup.shutdownGracefully().sync();
                connect();
                logger.info("Reconnect attempt {} of {}", count, MAX_RECONNECT_COUNT);
            }else{
                logger.error("Max reconnect reached. Giving up Reconnect");
            }
            
        } catch (InterruptedException e) {
            logger.error("Error occurred during reconnection", e);
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
