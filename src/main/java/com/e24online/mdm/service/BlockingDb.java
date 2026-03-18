package com.e24online.mdm.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Bridge component for executing blocking JDBC operations within a reactive WebFlux application.
 * <p>
 * This component offloads blocking database work to a dedicated scheduler backed by virtual threads
 * (Java 25+), ensuring that Reactor Netty event-loop threads are never blocked.
 * <p>
 * Some operations (like Flyway migrations and certain complex queries) require blocking JDBC.
 * This service provides a safe way to execute such operations without impacting the reactive event loop.
 */
@Component
public class BlockingDb {

    private static final int DEFAULT_MAX_IN_FLIGHT = 256;

    private final Scheduler jdbcScheduler;
    private final Semaphore inFlightLimiter;

    @Autowired
    public BlockingDb(@Qualifier("jdbcScheduler") Scheduler jdbcScheduler,
                      @Value("${mdm.blocking-db.max-in-flight:256}") int maxInFlight) {
        this.jdbcScheduler = jdbcScheduler;
        this.inFlightLimiter = new Semaphore(Math.max(1, maxInFlight), true);
    }

    public BlockingDb(@Qualifier("jdbcScheduler") Scheduler jdbcScheduler) {
        this.jdbcScheduler = jdbcScheduler;
        this.inFlightLimiter = new Semaphore(DEFAULT_MAX_IN_FLIGHT, true);
    }

    /**
     * Execute a blocking operation that returns a single value.
     * @param supplier the blocking operation
     * @return Mono emitting the result on the JDBC scheduler
     */
    public <T> Mono<T> mono(Supplier<T> supplier) {
        return submitBlocking(supplier::get);
    }

    /**
     * Execute a blocking operation that returns multiple values.
     * @param supplier the blocking operation returning an iterable
     * @return Flux emitting results on the JDBC scheduler
     */
    public <T> Flux<T> flux(Supplier<? extends Iterable<T>> supplier) {
        return submitBlocking(supplier::get)
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Execute a blocking operation that returns no result.
     * @param runnable the blocking operation
     * @return Mono that completes when the operation finishes
     */
    public Mono<Void> run(Runnable runnable) {
        return submitBlocking(() -> {
            runnable.run();
            return null;
        }).then();
    }

    private <T> Mono<T> submitBlocking(Callable<T> work) {
        return Mono.fromCallable(() -> {
                    if (!inFlightLimiter.tryAcquire()) {
                        throw new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Server is busy. Retry shortly."
                        );
                    }
                    try {
                        return work.call();
                    } finally {
                        inFlightLimiter.release();
                    }
                })
                .subscribeOn(jdbcScheduler);
    }
}
