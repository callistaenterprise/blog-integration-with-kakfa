package se.martin.endlessloop.producer;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.context.ApplicationContext;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import se.martin.endlessloop.KafkaConfig;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class EventProducer {

    private static final String PRODUCER_COUNT_CONFIRMED_METRIC = "kafka.producer.confirmed";
    private static final String PRODUCER_COUNT_UNCONFIRMED_METRIC = "kafka.producer.unconfirmed";
    private static final String PRODUCER_SEND_METRIC = "kafka.producer.send.timer";

    private final KafkaConfig config;

    private final Producer<UUID, byte[]> producer;

    private final byte[] message;

    private final Counter confirmedCounter;
    private final Counter unconfirmedCounter;
    private final Timer sendTimer;

    private final RetryConfig retryConfig = RetryConfig.custom()
            .retryExceptions(TimeoutException.class)
            .ignoreExceptions(ExecutionException.class, InterruptedException.class)
            .maxAttempts(5)
            .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1l), 2.0)
            ).build();

    public EventProducer(
            @KafkaClient(id = "endlessloop_producer") Producer<UUID, byte[]> producer,
            ApplicationContext applicationContext
    ) {
        this.producer = producer;
        this.config = applicationContext.getBean(KafkaConfig.class);
        char[] chars = new char[102_400]; // Message size about 100kb
        Arrays.fill(chars, 'a');
        message = new String(chars).getBytes(StandardCharsets.UTF_8);

        var meterRegistry = applicationContext.getBean(MeterRegistry.class);
        confirmedCounter = meterRegistry.counter(PRODUCER_COUNT_CONFIRMED_METRIC);
        unconfirmedCounter = meterRegistry.counter(PRODUCER_COUNT_UNCONFIRMED_METRIC);
        sendTimer = meterRegistry.timer(PRODUCER_SEND_METRIC);
    }

    public void publish() {
        sendTimer.record(
                () -> {
                    UUID key = UUID.randomUUID();
                    try {
                        Retry.decorateCheckedSupplier(
                                Retry.ofDefaults("publish"),
                                () -> send(new ProducerRecord<>(config.topic, key, message))
                        ).apply();
                        confirmedCounter.increment();
                    } catch (Throwable throwable) {
                        unconfirmedCounter.increment();
                    }
                }
        );
    }

    private RecordMetadata send(ProducerRecord<UUID, byte[]> record) throws InterruptedException, ExecutionException, TimeoutException {
        var future = producer.send(record);
        var recordMetadata = future.get(1l, TimeUnit.SECONDS);
        return recordMetadata;
    }

}
