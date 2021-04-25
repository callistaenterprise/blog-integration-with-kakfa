package se.martin.endlessloop;

import io.micronaut.context.annotation.Property;

import javax.inject.Singleton;

@Singleton
public class KafkaConfig {

    @Property(name = "kafka.topic.name")
    public String topic;

    @Property(name = "kafka.topic.partitions")
    public int partitions;

    @Property(name = "kafka.topic.consumer_group")
    public String consumerGroup;

}
