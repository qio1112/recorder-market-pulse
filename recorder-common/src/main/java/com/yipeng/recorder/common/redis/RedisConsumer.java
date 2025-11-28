package com.yipeng.recorder.common.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.StreamEntryID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Redis consumer for subscribing to channels and reading from streams.
 * Provides both pub/sub and streams functionality.
 */
public class RedisConsumer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RedisConsumer.class);
    private final JedisPool jedisPool;
    private final ExecutorService executorService;
    private volatile boolean running = true;

    /**
     * Creates a Redis consumer
     * @param jedisPool Redis connection pool
     */
    public RedisConsumer(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.executorService = Executors.newCachedThreadPool();
    }

    // ==================== PUB/SUB METHODS ====================

    /**
     * Subscribes to a Redis channel and processes messages (real-time, no persistence)
     * @param channel Redis channel name
     * @param messageHandler handler for received messages
     */
    public void subscribeToChannel(String channel, Consumer<String> messageHandler) {
        subscribeToChannels(new String[]{channel}, messageHandler);
    }

    /**
     * Subscribes to multiple Redis channels and processes messages (real-time, no persistence)
     * @param channels Redis channel names
     * @param messageHandler handler for received messages
     */
    public void subscribeToChannels(String[] channels, Consumer<String> messageHandler) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                JedisPubSub pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            logger.debug("Received message from channel {}: {}", channel, message);
                            messageHandler.accept(message);
                        } catch (Exception e) {
                            logger.error("Error processing message from channel {}", channel, e);
                        }
                    }

                    @Override
                    public void onSubscribe(String channel, int subscribedChannels) {
                        logger.info("Subscribed to channel: {}", channel);
                    }

                    @Override
                    public void onUnsubscribe(String channel, int subscribedChannels) {
                        logger.info("Unsubscribed from channel: {}", channel);
                    }
                };

                jedis.subscribe(pubSub, channels);
            } catch (Exception e) {
                logger.error("Error in Redis subscription", e);
            }
        }, executorService);
    }

    // ==================== STREAMS METHODS ====================

    /**
     * Acknowledges a message in a consumer group
     * @param streamKey Redis stream key
     * @param groupName consumer group name
     * @param entryId entry ID to acknowledge
     * @return number of acknowledged messages
     */
    public long acknowledgeMessage(String streamKey, String groupName, String entryId) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.xack(streamKey, groupName, new StreamEntryID(entryId));
            logger.debug("Acknowledged message {} in stream {} group {}", entryId, streamKey, groupName);
            return result;
        } catch (Exception e) {
            logger.error("Failed to acknowledge message in stream: {}", streamKey, e);
            throw new RuntimeException("Failed to acknowledge message", e);
        }
    }

    /**
     * Checks if a stream exists
     * @param streamKey Redis stream key
     * @return true if stream exists, false otherwise
     */
    public boolean streamExists(String streamKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            boolean exists = jedis.exists(streamKey);
            logger.debug("Stream {} exists: {}", streamKey, exists);
            return exists;
        } catch (Exception e) {
            logger.error("Failed to check if stream exists: {}", streamKey, e);
            throw new RuntimeException("Failed to check stream existence", e);
        }
    }

    /**
     * Gets the length of a stream
     * @param streamKey Redis stream key
     * @return number of messages in the stream
     */
    public long getStreamLength(String streamKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            long length = jedis.xlen(streamKey);
            logger.debug("Stream {} length: {}", streamKey, length);
            return length;
        } catch (Exception e) {
            logger.error("Failed to get stream length: {}", streamKey, e);
            throw new RuntimeException("Failed to get stream length", e);
        }
    }

    /**
     * Creates a consumer group for a Redis stream
     * @param streamKey Redis stream key
     * @param groupName consumer group name
     * @param startId starting ID for the group (use "0" for beginning)
     * @return true if group was created successfully
     */
    public boolean createConsumerGroup(String streamKey, String groupName, String startId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.xgroupCreate(streamKey, groupName, new StreamEntryID(startId), true);
            logger.info("Created consumer group {} for stream {}", groupName, streamKey);
            return true;
        } catch (Exception e) {
            logger.error("Failed to create consumer group {} for stream {}", groupName, streamKey, e);
            return false;
        }
    }

    /**
     * Stops the consumer and closes resources
     */
    public void stop() {
        running = false;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    @Override
    public void close() {
        stop();
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("Redis consumer connection pool closed");
        }
    }
}
