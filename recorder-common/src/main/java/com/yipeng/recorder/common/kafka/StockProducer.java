package com.yipeng.recorder.common.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Future;

public class StockProducer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(StockProducer.class);
    private final KafkaProducer<String, String> producer;
    private final String topic;

    public StockProducer(String bootstrapServers, String topic) {
        Properties props = KafkaConfig.producerProps(bootstrapServers);
        this.producer = new KafkaProducer<>(props);
        this.topic = topic;
    }

    public Future<RecordMetadata> send(String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        return producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Failed to send message to Kafka", exception);
            } else {
                logger.info("Sent message to topic {} partition {} offset {}", metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    @Override
    public void close() {
        producer.close();
    }
} 