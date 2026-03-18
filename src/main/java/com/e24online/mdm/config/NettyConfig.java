package com.e24online.mdm.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class NettyConfig {

    @Bean
    public NettyReactiveWebServerFactory nettyReactiveWebServerFactory() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        factory.addServerCustomizers(httpServer ->
                httpServer
                        .idleTimeout(Duration.ofSeconds(30))
                        .option(ChannelOption.SO_BACKLOG, 4096)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .doOnConnection(conn -> conn
                                .addHandlerLast(new ReadTimeoutHandler(35, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(35, TimeUnit.SECONDS))
                        )
        );
        return factory;
    }
}

