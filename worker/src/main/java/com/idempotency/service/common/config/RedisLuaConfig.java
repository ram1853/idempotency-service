package com.idempotency.service.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisLuaConfig {

    //Lua scripts ensure multiple operations are executed in an atomic way (it blocks other operations when a lua script is running)

    @Bean
    public RedisScript<String> reserveScript(){
        return RedisScript.of(new ClassPathResource("redis-scripts/reserve.lua"), String.class);
    }

    @Bean
    public RedisScript<String> completeScript(){
        return RedisScript.of(new ClassPathResource("redis-scripts/complete.lua"), String.class);
    }
}
