package com.yipeng.recorder.common.redis;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import java.time.Duration;

/**
 * Configuration class for Redis connections.
 * Provides connection pool configuration and utility methods.
 */
public class RedisConfig {
    
    /**
     * Creates a JedisPool with default configuration
     * @param host Redis server host
     * @param port Redis server port
     * @return configured JedisPool
     */
    public static JedisPool createPool(String host, int port) {
        return createPool(host, port, null, null);
    }
    
    /**
     * Creates a JedisPool with authentication
     * @param host Redis server host
     * @param port Redis server port
     * @param password Redis password (can be null)
     * @return configured JedisPool
     */
    public static JedisPool createPool(String host, int port, String password) {
        return createPool(host, port, password, null);
    }
    
    /**
     * Creates a JedisPool with full configuration
     * @param host Redis server host
     * @param port Redis server port
     * @param password Redis password (can be null)
     * @param database Redis database number (can be null, defaults to 0)
     * @return configured JedisPool
     */
    public static JedisPool createPool(String host, int port, String password, Integer database) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        
        // Connection pool settings
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofSeconds(5));
        
        if (password != null && !password.isEmpty()) {
            if (database != null) {
                return new JedisPool(poolConfig, host, port, Protocol.DEFAULT_TIMEOUT, password, database);
            } else {
                return new JedisPool(poolConfig, host, port, Protocol.DEFAULT_TIMEOUT, password);
            }
        } else {
            if (database != null) {
                return new JedisPool(poolConfig, host, port, Protocol.DEFAULT_TIMEOUT, null, database);
            } else {
                return new JedisPool(poolConfig, host, port);
            }
        }
    }
    
    /**
     * Creates a JedisPool with connection string (for Redis Cloud, etc.)
     * @param connectionString Redis connection string
     * @return configured JedisPool
     */
    public static JedisPool createPoolFromConnectionString(String connectionString) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        
        // Connection pool settings
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofSeconds(5));
        
        return new JedisPool(poolConfig, connectionString);
    }
}
