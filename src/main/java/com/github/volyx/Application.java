package com.github.volyx;

import com.github.sarxos.webcam.Webcam;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;


public class Application {

    public static String WEBSOCKET_PATH = "/websocket";
    public static int FPS = 30;

    public static void main(String[] args) throws InterruptedException {

        ChannelGroup allChannels = new DefaultChannelGroup("all", GlobalEventExecutor.INSTANCE);

        startCapture(allChannels);
        startServer(allChannels);
    }

    private static void startServer(final ChannelGroup group) throws InterruptedException

    {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.TRACE))
                    .childHandler(new ChannelInitializer() {

                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(65536))
//                                    .addLast(new WebSocketServerCompressionHandler())
                                    .addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true))
                                    .addLast(new WebSocketFrameHandler(group));
                        }
                    }).bind(8080).sync()
                    .channel()
                    .closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static void startCapture(ChannelGroup group) throws InterruptedException

    {
        Webcam webcam = Webcam.getDefault();
        webcam.setViewSize(new Dimension(640, 480));
        webcam.open();

        long i = 0L;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        ImageIO.write(webcam.getImage(), "jpg", outputStream);
                        group.writeAndFlush(new BinaryWebSocketFrame(Unpooled.copiedBuffer(outputStream.toByteArray())));
                        Thread.sleep(1000 / FPS);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();


    }
}

class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private ChannelGroup group;

    public WebSocketFrameHandler(ChannelGroup group) {
        this.group = group;
    }

    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        System.out.println(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            group.add(ctx.channel());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}