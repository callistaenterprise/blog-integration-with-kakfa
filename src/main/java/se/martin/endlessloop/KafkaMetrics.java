package se.martin.endlessloop;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micronaut.context.ApplicationContext;
import io.micronaut.scheduling.annotation.Scheduled;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class KafkaMetrics {

    public static final String LEADER_METRIC = "kafka.admin.partition.leader";
    public static final String REPLICA_METRIC = "kafka.admin.partition.replicas";
    public static final String OFFSET_METRIC = "kafka.admin.consumer_group.offset";
    public static final String LATEST_METRIC = "kafka.admin.latest.offset";

    private final AdminClient admin;

    private final KafkaConfig config;

    private final Map<Integer, AtomicInteger> partitionLeaders = new HashMap<>();
    private final Map<Integer, AtomicInteger> partitionReplicas = new HashMap<>();
    private final Map<Integer, AtomicLong> consumerGroupOffsets = new HashMap<>();
    private final Map<Integer, AtomicLong> latestPartitionOffsets = new HashMap<>();

    private final Map<TopicPartition, OffsetSpec> latestOffsets = new HashMap<>();

    public KafkaMetrics(ApplicationContext applicationContext) {

        admin = applicationContext.getBean(AdminClient.class);
        config = applicationContext.getBean(KafkaConfig.class);

        var meterRegistry = applicationContext.getBean(MeterRegistry.class);

        // Initialise gauges for leader, replication factor, latest offset and consumer group offset
        for (int i = 0; i < config.partitions; i++) {
            List<Tag> tags = Arrays.asList(new ImmutableTag("partition", "" + i));
            partitionLeaders.put(i, meterRegistry.gauge(LEADER_METRIC, tags, new AtomicInteger(-1)));
            partitionReplicas.put(i, meterRegistry.gauge(REPLICA_METRIC, tags, new AtomicInteger(-1)));
            consumerGroupOffsets.put(i, meterRegistry.gauge(OFFSET_METRIC, tags, new AtomicLong(-1l)));
            latestPartitionOffsets.put(i, meterRegistry.gauge(LATEST_METRIC, tags, new AtomicLong(-1l)));
            latestOffsets.put(new TopicPartition(config.topic, i), OffsetSpec.latest());
        }
    }

    @Scheduled(initialDelay = "20s", fixedDelay = "1s")
    public void topicMetrics() {
        var result = admin.describeTopics(Arrays.asList(config.topic));
        try {
            var descriptions = result.all().get(30l, TimeUnit.SECONDS);
            var partitions = Optional.ofNullable(descriptions.get(config.topic))
                    .map(TopicDescription::partitions)
                    .orElse(Collections.emptyList());
            for (var p : partitions) {
                var id = p.partition();
                var leader = Optional.ofNullable(p.leader()).map(Node::id).orElse(-1);
                var replicas = Optional.ofNullable(p.replicas()).map(List::size).orElse(-1);
                partitionLeaders.get(id).set(leader);
                partitionReplicas.get(id).set(replicas);
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            // do nothing
        }
    }

    @Scheduled(initialDelay = "20s", fixedDelay = "1s")
    public void consumerGroupOffsetMetrics() {
        var result = admin.listConsumerGroupOffsets(config.consumerGroup);
        try {
            var offsets = result.partitionsToOffsetAndMetadata().get(30l, TimeUnit.SECONDS);
            offsets.entrySet().forEach(e -> consumerGroupOffsets.get(e.getKey().partition()).set(e.getValue().offset()));
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            // do nothing
        }
    }

    @Scheduled(initialDelay = "20s", fixedDelay = "1s")
    public void topicOffsets() {
        var result = admin.listOffsets(latestOffsets);

        try {
            var latest = result.all().get(30l, TimeUnit.SECONDS);
            latest.entrySet().forEach(e -> latestPartitionOffsets.get(e.getKey().partition()).set(e.getValue().offset()));
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            // do nothing
        }
    }

}
