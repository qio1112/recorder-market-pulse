package com.yipeng.recorder.common.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XAddParams;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Redis-based producer for publishing messages and storing data.
 * Provides pub/sub, streams, and key-value storage capabilities.
 */
public class RedisProducer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RedisProducer.class);
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final String defaultChannel;

    /**
     * Creates a Redis producer with default channel
     * @param jedisPool Redis connection pool
     * @param defaultChannel default channel for pub/sub operations
     */
    public RedisProducer(JedisPool jedisPool, String defaultChannel) {
        this.jedisPool = jedisPool;
        this.defaultChannel = defaultChannel;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a Redis producer without default channel
     * @param jedisPool Redis connection pool
     */
    public RedisProducer(JedisPool jedisPool) {
        this(jedisPool, null);
    }

    /**
     * Publishes a message to a Redis channel
     * @param channel Redis channel name
     * @param message message to publish
     * @return number of subscribers that received the message
     */
    public long publish(String channel, String message) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.publish(channel, message);
            logger.debug("Published message to channel {}: {}", channel, message);
            return result;
        } catch (Exception e) {
            logger.error("Failed to publish message to channel {}", channel, e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }

    /**
     * Publishes a message to the default channel
     * @param message message to publish
     * @return number of subscribers that received the message
     */
    public long publish(String message) {
        if (defaultChannel == null) {
            throw new IllegalStateException("No default channel configured");
        }
        return publish(defaultChannel, message);
    }

    /**
     * Publishes an object as JSON to a Redis channel
     * @param channel Redis channel name
     * @param object object to serialize and publish
     * @return number of subscribers that received the message
     */
    public long publishObject(String channel, Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);
            return publish(channel, json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize object for publishing", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    /**
     * Publishes an object as JSON to the default channel
     * @param object object to serialize and publish
     * @return number of subscribers that received the message
     */
    public long publishObject(Object object) {
        if (defaultChannel == null) {
            throw new IllegalStateException("No default channel configured");
        }
        return publishObject(defaultChannel, object);
    }

    /**
     * Stores a key-value pair in Redis
     * @param key Redis key
     * @param value value to store
     * @return "OK" if successful
     */
    public String set(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(key, value);
            logger.debug("Stored key-value pair: {} = {}", key, value);
            return result;
        } catch (Exception e) {
            logger.error("Failed to store key-value pair: {}", key, e);
            throw new RuntimeException("Failed to store key-value pair", e);
        }
    }

    /**
     * Stores an object as JSON in Redis
     * @param key Redis key
     * @param object object to serialize and store
     * @return "OK" if successful
     */
    public String setObject(String key, Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);
            return set(key, json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize object for storage", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    /**
     * Stores a key-value pair in Redis with expiration
     * @param key Redis key
     * @param value value to store
     * @param expireSeconds expiration time in seconds
     * @return "OK" if successful
     */
    public String setex(String key, String value, int expireSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.setex(key, expireSeconds, value);
            logger.debug("Stored key-value pair with expiration: {} = {} (expires in {}s)", key, value, expireSeconds);
            return result;
        } catch (Exception e) {
            logger.error("Failed to store key-value pair with expiration: {}", key, e);
            throw new RuntimeException("Failed to store key-value pair with expiration", e);
        }
    }

    /**
     * Stores an object as JSON in Redis with expiration
     * @param key Redis key
     * @param object object to serialize and store
     * @param expireSeconds expiration time in seconds
     * @return "OK" if successful
     */
    public String setexObject(String key, Object object, int expireSeconds) {
        try {
            String json = objectMapper.writeValueAsString(object);
            return setex(key, json, expireSeconds);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize object for storage with expiration", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    /**
     * Publishes a message asynchronously
     * @param channel Redis channel name
     * @param message message to publish
     * @return CompletableFuture with the number of subscribers
     */
    public CompletableFuture<Long> publishAsync(String channel, String message) {
        return CompletableFuture.supplyAsync(() -> publish(channel, message));
    }

    /**
     * Publishes an object asynchronously
     * @param channel Redis channel name
     * @param object object to serialize and publish
     * @return CompletableFuture with the number of subscribers
     */
    public CompletableFuture<Long> publishObjectAsync(String channel, Object object) {
        return CompletableFuture.supplyAsync(() -> publishObject(channel, object));
    }

    // ==================== REDIS STREAMS METHODS ====================

    /**
     * Adds a message to a Redis stream
     * @param streamKey Redis stream key
     * @param field field name
     * @param value field value
     * @return StreamEntryID of the added message
     */
    public StreamEntryID addToStream(String streamKey, String field, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> fields = new HashMap<>();
            fields.put(field, value);
            StreamEntryID id = jedis.xadd(streamKey, StreamEntryID.NEW_ENTRY, fields);
            logger.debug("Added message to stream {}: {} = {}", streamKey, field, value);
            return id;
        } catch (Exception e) {
            logger.error("Failed to add message to stream: {}", streamKey, e);
            throw new RuntimeException("Failed to add message to stream", e);
        }
    }

    /**
     * Adds multiple fields to a Redis stream
     * @param streamKey Redis stream key
     * @param fields map of field names to values
     * @return StreamEntryID of the added message
     */
    public StreamEntryID addToStream(String streamKey, Map<String, String> fields) {
        try (Jedis jedis = jedisPool.getResource()) {
            StreamEntryID id = jedis.xadd(streamKey, StreamEntryID.NEW_ENTRY, fields);
            logger.debug("Added message to stream {} with {} fields", streamKey, fields.size());
            return id;
        } catch (Exception e) {
            logger.error("Failed to add message to stream: {}", streamKey, e);
            throw new RuntimeException("Failed to add message to stream", e);
        }
    }

    /**
     * Adds an object as JSON to a Redis stream
     * @param streamKey Redis stream key
     * @param object object to serialize and add
     * @return StreamEntryID of the added message
     */
    public StreamEntryID addObjectToStream(String streamKey, Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);
            Map<String, String> fields = new HashMap<>();
            fields.put("data", json);
            fields.put("timestamp", String.valueOf(System.currentTimeMillis()));
            return addToStream(streamKey, fields);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize object for stream", e);
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    /**
     * Adds a message to a Redis stream with max length limit
     * @param streamKey Redis stream key
     * @param fields map of field names to values
     * @param maxLen maximum length of the stream
     * @return StreamEntryID of the added message
     */
    public StreamEntryID addToStreamWithMaxLen(String streamKey, Map<String, String> fields, long maxLen) {
        try (Jedis jedis = jedisPool.getResource()) {
            XAddParams params = XAddParams.xAddParams().maxLen(maxLen);
            StreamEntryID id = jedis.xadd(streamKey, params, fields);
            logger.debug("Added message to stream {} with max length {}", streamKey, maxLen);
            return id;
        } catch (Exception e) {
            logger.error("Failed to add message to stream with max length: {}", streamKey, e);
            throw new RuntimeException("Failed to add message to stream", e);
        }
    }



    /**
     * Adds a message to a Redis stream asynchronously
     * @param streamKey Redis stream key
     * @param fields map of field names to values
     * @return CompletableFuture with the StreamEntryID
     */
    public CompletableFuture<StreamEntryID> addToStreamAsync(String streamKey, Map<String, String> fields) {
        return CompletableFuture.supplyAsync(() -> addToStream(streamKey, fields));
    }

    /**
     * Adds an object to a Redis stream asynchronously
     * @param streamKey Redis stream key
     * @param object object to serialize and add
     * @return CompletableFuture with the StreamEntryID
     */
    public CompletableFuture<StreamEntryID> addObjectToStreamAsync(String streamKey, Object object) {
        return CompletableFuture.supplyAsync(() -> addObjectToStream(streamKey, object));
    }

    @Override
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("Redis producer connection pool closed");
        }
    }
}
