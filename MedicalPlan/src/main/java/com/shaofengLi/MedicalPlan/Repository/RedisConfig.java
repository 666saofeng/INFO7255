package com.shaofengLi.MedicalPlan.Repository;

//import org.springframework.data.redis.connection.connection.RedisStandaloneConfiguration;
//import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.*;
import redis.clients.jedis.providers.PooledConnectionProvider;


@Repository
public class RedisConfig {
    @Bean
    public Jedis JedisConnection() {
        return new Jedis("localhost", 6379);
    }

}
