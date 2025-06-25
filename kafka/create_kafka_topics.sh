#!/bin/bash

KAFKA_CONTAINER=kafka
BROKER=localhost:9092

# Wait for Kafka to be ready
sleep 5

docker exec $KAFKA_CONTAINER \
  kafka-topics.sh --create --if-not-exists --bootstrap-server $BROKER --replication-factor 1 --partitions 3 --topic stock-prices

echo "Kafka topic 'stock-prices' created." 