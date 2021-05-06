package se.martin.endlessloop.producer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.context.ApplicationContext;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.martin.endlessloop.KafkaConfig;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class EventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EventProducer.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private static final String PRODUCER_COUNT_CONFIRMED_METRIC = "kafka.producer.confirmed";
    private static final String PRODUCER_COUNT_UNCONFIRMED_METRIC = "kafka.producer.unconfirmed";
    private static final String PRODUCER_SEND_METRIC = "kafka.producer.send.timer";

    private static final String HEADER_TRACE_ID = "trace-id";
    private static final String HEADER_EVENT_ISO_DATE_TIME = "event-timestamp";
    private static final String HEADER_IS_A_PROBLEM = "this-is-a-problem";

    private final KafkaConfig config;

    private final Producer<UUID, byte[]> producer;

    private final byte[] message;

    private final Counter confirmedCounter;
    private final Counter unconfirmedCounter;
    private final Timer sendTimer;

    private final AtomicBoolean haveHadAProblem = new AtomicBoolean();

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
                    var record = new ProducerRecord<>(config.topic, key, message);
                    var headers = record.headers();
                    // Add a trace id
                    headers.add(HEADER_TRACE_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                    // Add a iso date time representing the business event time up to two minutes back in time
                    headers.add(HEADER_EVENT_ISO_DATE_TIME,
                            FORMATTER.format(
                                    ZonedDateTime.now()
                                            .minus(Duration.ofSeconds(
                                                    ThreadLocalRandom.current().nextLong(120l)
                                                    )
                                            )
                            ).getBytes(StandardCharsets.UTF_8)
                    );
                    // 1% chance of this occuring
                    if (ThreadLocalRandom.current().nextInt(100) == 50) {
                        // Only set this header once
                        if (!haveHadAProblem.compareAndExchange(false, true)) {
                            headers.add(HEADER_IS_A_PROBLEM, "true".getBytes(StandardCharsets.UTF_8));
                        }
                    }


                    var future = producer.send(record);
                    try {
                        RecordMetadata recordMetadata = future.get(15l, TimeUnit.SECONDS);
                        confirmedCounter.increment();
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        LOG.error("Unable to confirm publication", e);
                        unconfirmedCounter.increment();
                    }
                }
        );
    }

}
