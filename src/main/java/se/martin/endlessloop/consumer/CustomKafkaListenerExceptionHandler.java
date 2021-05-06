package se.martin.endlessloop.consumer;

import io.micronaut.configuration.kafka.exceptions.DefaultKafkaListenerExceptionHandler;
import io.micronaut.configuration.kafka.exceptions.KafkaListenerException;
import io.micronaut.configuration.kafka.exceptions.KafkaListenerExceptionHandler;
import io.micronaut.context.annotation.Replaces;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

//@Replaces(DefaultKafkaListenerExceptionHandler.class)
@Singleton
public class CustomKafkaListenerExceptionHandler implements KafkaListenerExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CustomKafkaListenerExceptionHandler.class);

    @Override
    public void handle(KafkaListenerException exception) {

        var partition = exception.getConsumerRecord().map(ConsumerRecord::partition).orElse(-1);
        var key = exception.getConsumerRecord().map(ConsumerRecord::key).orElse(null);
        var offset = exception.getConsumerRecord().map(ConsumerRecord::offset).orElse(-1l);
        LOG.error(
                String.format("Consuming message with key %s partition %s, offset %s", key, partition, offset),
                exception.getCause()
        );
        throw exception;
    }
}
