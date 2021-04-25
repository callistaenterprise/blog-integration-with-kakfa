package se.martin.endlessloop.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.OffsetReset;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@KafkaListener(
        groupId = "endlessloop_group",
        clientId = "${kafka.topic.consumer_group}",
        offsetReset = OffsetReset.EARLIEST
)
public class Consumer {

    private static final String CONSUMER_COUNT_METRIC = "kafka.consumer.count";

    private final Counter consumerCounter;

    public Consumer(ApplicationContext applicationContext) {
        var meterRegistry = applicationContext.getBean(MeterRegistry.class);
        consumerCounter = meterRegistry.counter(CONSUMER_COUNT_METRIC);
    }

    @Topic("endless")
    public void consume(@KafkaKey UUID key, byte[] value) {
        consumerCounter.increment();
    }
}
