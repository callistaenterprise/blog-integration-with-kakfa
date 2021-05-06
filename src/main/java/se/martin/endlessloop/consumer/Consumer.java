package se.martin.endlessloop.consumer;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.kafka.annotation.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.messaging.Acknowledgement;
import io.micronaut.messaging.MessageHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@KafkaListener(
        groupId = "${kafka.topic.consumer_group}",
        threads = 12,
        offsetReset = OffsetReset.EARLIEST,
        offsetStrategy = OffsetStrategy.DISABLED
)
public class Consumer {

    private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);

    private static final String CONSUMER_COUNT_METRIC = "kafka.consumer.count";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private static final String HEADER_TRACE_ID = "trace-id";
    private static final String HEADER_EVENT_ISO_DATE_TIME = "event-timestamp";
    private static final String HEADER_IS_A_PROBLEM = "this-is-a-problem";

    private final Counter consumerCounter;

    private final RetryConfig retryConfig = RetryConfig.custom()
            .retryExceptions(IllegalStateException.class)
            .maxAttempts(5)
            .waitDuration(Duration.ofSeconds(1l))
            .build();

    private final RetryRegistry registry = RetryRegistry.of(retryConfig);

    public Consumer(ApplicationContext applicationContext) {
        var meterRegistry = applicationContext.getBean(MeterRegistry.class);
        consumerCounter = meterRegistry.counter(CONSUMER_COUNT_METRIC);
    }

    @Topic("endless")
    public void consume(@KafkaKey UUID key, byte[] value, MessageHeaders headers, int partition, long offset, Acknowledgement ack) {

        Retry retry = registry.retry("consume");
        Function<MessageHeaders, Void> checkHeaders
                = Retry.decorateFunction(retry, (MessageHeaders mh) -> {
            if (mh.contains(HEADER_IS_A_PROBLEM)) {
                LOG.warn(String.format("Problem in message with key %s partition %s, offset %s", key, partition, offset));
                throw new IllegalStateException("There is a problem");
            }
            return null;
        });

        checkHeaders.apply(headers);

        consumerCounter.increment();

        // Add a trace id to the logs
        MDC.put("trace-id", Optional.ofNullable(headers.get(HEADER_TRACE_ID)).orElse(UUID.randomUUID().toString()));

        var now = ZonedDateTime.now();
        var eventTs =
                Optional.ofNullable(headers.get(HEADER_EVENT_ISO_DATE_TIME))
                        .map(FORMATTER::parse)
                        .map(ZonedDateTime::from)
                        .orElse(now);
        if (eventTs.isAfter(now.minusSeconds(60l))) {
            LOG.info(String.format("Consuming message with key %s partition %s, offset %s", key, partition, offset));
        } else {
            LOG.info(String.format("Ignoring message with key %s partition %s, offset %s", key, partition, offset));
        }
        ack.ack();
    }
}
