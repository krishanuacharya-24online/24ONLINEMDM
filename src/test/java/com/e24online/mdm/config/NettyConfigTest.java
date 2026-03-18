package com.e24online.mdm.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyConfigTest {

    @Test
    void nettyReactiveWebServerFactory_registersCustomizer() {
        NettyConfig config = new NettyConfig();

        NettyReactiveWebServerFactory factory = config.nettyReactiveWebServerFactory();

        assertNotNull(factory);
        assertEquals(1, factory.getServerCustomizers().size());
    }

    @Test
    void nettyCustomizer_canCustomizeHttpServerInstance() throws Exception {
        NettyReactiveWebServerFactory factory = new NettyConfig().nettyReactiveWebServerFactory();
        Object customizer = factory.getServerCustomizers().iterator().next();
        assertTrue(customizer instanceof Function<?, ?>);

        @SuppressWarnings("unchecked")
        Function<HttpServer, HttpServer> nettyCustomizer = (Function<HttpServer, HttpServer>) customizer;
        HttpServer customized = nettyCustomizer.apply(HttpServer.create());
        assertNotNull(customized);
    }

    @Test
    void nettyCustomizer_executesConnectionHandlersWhenRequestArrives() {
        NettyReactiveWebServerFactory factory = new NettyConfig().nettyReactiveWebServerFactory();
        Object customizer = factory.getServerCustomizers().iterator().next();
        assertTrue(customizer instanceof Function<?, ?>);

        @SuppressWarnings("unchecked")
        Function<HttpServer, HttpServer> nettyCustomizer = (Function<HttpServer, HttpServer>) customizer;
        HttpServer customized = nettyCustomizer.apply(HttpServer.create())
                .port(0)
                .handle((req, res) -> res.sendString(Mono.just("ok")));

        DisposableServer server = customized.bindNow();
        try {
            String response = HttpClient.create()
                    .port(server.port())
                    .get()
                    .uri("/")
                    .responseSingle((res, body) -> body.asString())
                    .block();
            assertEquals("ok", response);
        } finally {
            server.disposeNow();
        }
    }
}
