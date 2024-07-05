package temphum;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

public class SimpleNettyClient {
    private static final Logger logger = LoggerFactory.getLogger(SimpleNettyClient.class);
    private String host;
    private int port;
    
    private final int retryInterval = 5;
    private final int idleTimeout = 60;
    private int count=0;
    private static final int MAX_RECONNECT_COUNT=10;
    public SimpleNettyClient(String host, int port) {
        this.host = host;
        this.port = port;
        logger.debug("SimpleNettyClient initialized with host: {} and port: {}", host, port);
    }

    public void connect() {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(new IdleStateHandler(idleTimeout, 0, 0, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new ReconnectHandler(eventLoopGroup));
                        ch.pipeline().addLast(new MainQueueHandler(SimpleNettyClient.this));
                        logger.debug("Channel initialized with MainQueueHandler");
                    }
                });

            logger.info("Attempting to connect to server at {}:{}", host, port);
            ChannelFuture future = bootstrap.connect(host, port).sync();
            if (future.isSuccess()) {
                logger.info("Server connect success on port: {}", port);
            } else {
                logger.warn("Server connect attempt failed on port: {}", port);
                scheduleReconnect(eventLoopGroup);
            }
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("An error occurred while connecting to the server", e);
        } finally {
            eventLoopGroup.shutdownGracefully();
            logger.info("EventLoopGroup shutdown gracefully");
        }
    }

    public void scheduleReconnect(EventLoopGroup group) {
        group.schedule(() -> {
            if (count<MAX_RECONNECT_COUNT) {
                count++;
                logger.warn("Reconnect attempt {} of {}", count, MAX_RECONNECT_COUNT);
                connect();
            } else {
                logger.error("Max reconnect attempts reached. Giving up.");
            }
        }, retryInterval, TimeUnit.SECONDS);
    }

    private class ReconnectHandler extends ChannelInboundHandlerAdapter {
        private final  EventLoopGroup group;
        public ReconnectHandler(EventLoopGroup group){
            this.group = group;
        }
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt){
            if(evt instanceof IdleStateEvent){
                IdleStateEvent event = (IdleStateEvent) evt;
                if(event.state()==IdleState.READER_IDLE){
                    logger.warn("No Data Received for {} seconds. Reconnecting...",idleTimeout);
                    ctx.close();
                    scheduleReconnect(group);
                }
            }else {
                try {
                    super.userEventTriggered(ctx, evt);
                } catch (Exception e) {
                    logger.error("An error occurred while processing userEventTriggered", e);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 4002;
        String host = "192.168.0.221";
        SimpleNettyClient client = new SimpleNettyClient(host, port);
        logger.info("Starting SimpleNettyClient");
        client.connect();
        logger.info("SimpleNettyClient finished");
    }

}
