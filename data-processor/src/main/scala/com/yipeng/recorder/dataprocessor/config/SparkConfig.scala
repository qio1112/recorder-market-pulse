package com.yipeng.recorder.dataprocessor.config

import org.apache.spark.SparkConf
import org.apache.spark.api.java.JavaSparkContext
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.{Bean, Configuration}

@Configuration
class SparkConfig {
    
    @Value("${spark.app-name}")
    private var appName: String = _
    
    @Value("${spark.master}")
    private var master: String = _
    
    @Value("${spark.streaming.batch-duration}")
    private var batchDuration: Long = _
    
    @Value("${spark.streaming.checkpoint-dir}")
    private var checkpointDir: String = _
    
    @Bean
    def sparkConf(): SparkConf = {
        new SparkConf()
            .setAppName(appName)
            .setMaster(master)
            .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
            .set("spark.sql.streaming.checkpointLocation", checkpointDir)
            .set("spark.streaming.backpressure.enabled", "true")
            .set("spark.streaming.kafka.maxRatePerPartition", "1000")
    }
    
    @Bean
    def javaSparkContext(): JavaSparkContext = {
        new JavaSparkContext(sparkConf())
    }
    
    @Bean
    def streamingContext(): StreamingContext = {
        val ssc = new StreamingContext(sparkConf(), Seconds(batchDuration / 1000))
        ssc.checkpoint(checkpointDir)
        ssc
    }
} 