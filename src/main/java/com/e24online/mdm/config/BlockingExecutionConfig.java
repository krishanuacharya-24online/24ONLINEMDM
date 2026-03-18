package com.e24online.mdm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class BlockingExecutionConfig {

    /**
     * Runs blocking JDBC work on Java 25 virtual threads so Reactor Netty event-loop threads never block.
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler jdbcScheduler() {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        return Schedulers.fromExecutorService(executor);
    }
}

