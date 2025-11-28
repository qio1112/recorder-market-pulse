package com.yipeng.recorder.common.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simple Redis utility class for basic operations.
 * Provides key-value storage and retrieval capabilities.
 */
public class RedisUtils {
    private static final Logger logger = LoggerFactory.getLogger(RedisUtils.class);
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    /**
     * Creates a Redis utility instance
     * @param jedisPool Redis connection pool
     */
    public RedisUtils(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.objectMapper = new ObjectMapper();
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
     * Retrieves a value from Redis by key
     * @param key Redis key
     * @return the value, or null if key doesn't exist
     */
    public String get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(key);
            logger.debug("Retrieved value for key {}: {}", key, value);
            return value;
        } catch (Exception e) {
            logger.error("Failed to retrieve value for key: {}", key, e);
            throw new RuntimeException("Failed to retrieve value", e);
        }
    }

    /**
     * Retrieves and deserializes an object from Redis by key
     * @param key Redis key
     * @param clazz class type to deserialize to
     * @param <T> type parameter
     * @return the deserialized object, or null if key doesn't exist
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize object for key: {}", key, e);
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }

    /**
     * Retrieves multiple values from Redis by keys
     * @param keys Redis keys
     * @return list of values (null for non-existent keys)
     */
    public List<String> mget(String... keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> values = jedis.mget(keys);
            logger.debug("Retrieved multiple values for keys: {}", (Object) keys);
            return values;
        } catch (Exception e) {
            logger.error("Failed to retrieve multiple values", e);
            throw new RuntimeException("Failed to retrieve multiple values", e);
        }
    }

    /**
     * Checks if a key exists in Redis
     * @param key Redis key
     * @return true if key exists, false otherwise
     */
    public boolean exists(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            boolean exists = jedis.exists(key);
            logger.debug("Checked existence of key {}: {}", key, exists);
            return exists;
        } catch (Exception e) {
            logger.error("Failed to check existence of key: {}", key, e);
            throw new RuntimeException("Failed to check key existence", e);
        }
    }

    /**
     * Gets the time-to-live (TTL) of a key in seconds
     * @param key Redis key
     * @return TTL in seconds, -1 if key has no expiration, -2 if key doesn't exist
     */
    public long ttl(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            long ttl = jedis.ttl(key);
            logger.debug("TTL for key {}: {} seconds", key, ttl);
            return ttl;
        } catch (Exception e) {
            logger.error("Failed to get TTL for key: {}", key, e);
            throw new RuntimeException("Failed to get TTL", e);
        }
    }

    /**
     * Deletes a key from Redis
     * @param key Redis key
     * @return number of keys deleted (0 or 1)
     */
    public long del(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.del(key);
            logger.debug("Deleted key {}: {} keys deleted", key, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to delete key: {}", key, e);
            throw new RuntimeException("Failed to delete key", e);
        }
    }

    /**
     * Sets expiration time for a key
     * @param key Redis key
     * @param seconds expiration time in seconds
     * @return 1 if timeout was set, 0 if key doesn't exist
     */
    public long expire(String key, int seconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            long result = jedis.expire(key, seconds);
            logger.debug("Set expiration for key {}: {} seconds, result: {}", key, seconds, result);
            return result;
        } catch (Exception e) {
            logger.error("Failed to set expiration for key: {}", key, e);
            throw new RuntimeException("Failed to set expiration", e);
        }
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
}
