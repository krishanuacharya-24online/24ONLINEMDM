package com.e24online.mdm.config;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingExecutionConfigTest {

    @Test
    void jdbcScheduler_executesWorkAndCanBeDisposed() throws InterruptedException {
        BlockingExecutionConfig config = new BlockingExecutionConfig();
        Scheduler scheduler = config.jdbcScheduler();

        assertNotNull(scheduler);

        CountDownLatch latch = new CountDownLatch(1);
        scheduler.schedule(latch::countDown);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        scheduler.dispose();
        assertTrue(scheduler.isDisposed());
    }
}

