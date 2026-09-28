package com.chris64233.cc.museumloan.service;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 通用配置：注入 Clock 便于测试控制“今天”。 */
@Configuration
public class ServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
