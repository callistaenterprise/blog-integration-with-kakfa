package se.martin.endlessloop.producer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class EventScheduler {

    private static final String EVENT_COUNT_METRIC = "kafka.scheduler.event.count";

    private static final Logger LOG = LoggerFactory.getLogger(EventScheduler.class);

    private final AtomicInteger numberOfMessages = new AtomicInteger(1);

    private final EventProducer eventProducer;

    private final ExecutorService executorService = Executors.newFixedThreadPool(50);

    private final Counter eventCounter;

    // Produce for 20 minutes
    private final Instant shutDown = Instant.now().plus(20l, ChronoUnit.MINUTES);

    public EventScheduler(ApplicationContext applicationContext) {
        this.eventProducer = applicationContext.getBean(EventProducer.class);

        var meterRegistry = applicationContext.getBean(MeterRegistry.class);
        eventCounter = meterRegistry.counter(EVENT_COUNT_METRIC);
    }

    @Scheduled(initialDelay = "15s", fixedDelay = "1s")
    public void publish() {
        int num = numberOfMessages.get();
        eventCounter.increment(num);
        for (int i = 0; i < num; i++) {
            executorService.execute(
                    () -> eventProducer.publish()
            );
        }
    }

    @Scheduled(initialDelay = "180s", fixedDelay = "20s")
    public void adjustSpeed() {
        if (Instant.now().isAfter(shutDown)) {
            if (numberOfMessages.get() > 0) {
                LOG.info("Shutting down publisher");
            }
            numberOfMessages.set(0);
        } else {
            numberOfMessages.incrementAndGet();
        }
    }
}
