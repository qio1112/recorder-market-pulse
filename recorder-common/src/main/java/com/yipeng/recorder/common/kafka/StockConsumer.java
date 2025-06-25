package com.yipeng.recorder.common.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.BiConsumer;

public class StockConsumer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(StockConsumer.class);
    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private volatile boolean running = true;

    public StockConsumer(String bootstrapServers, String topic, String groupId) {
        Properties props = KafkaConfig.consumerProps(bootstrapServers, groupId);
        this.consumer = new KafkaConsumer<>(props);
        this.topic = topic;
        consumer.subscribe(Arrays.asList(topic));
    }

    /**
     * Start consuming messages. The handler is called for each record (key, value).
     * This method blocks until stop() is called or the thread is interrupted.
     */
    public void consume(BiConsumer<String, String> handler) {
        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    handler.accept(record.key(), record.value());
                }
            }
        } catch (Exception e) {
            logger.error("Error in Kafka consumer loop", e);
        } finally {
            consumer.close();
        }
    }

    public void stop() {
        running = false;
    }

    @Override
    public void close() {
        stop();
        consumer.close();
    }
} 