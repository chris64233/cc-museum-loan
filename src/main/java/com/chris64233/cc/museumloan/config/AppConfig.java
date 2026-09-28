package com.chris64233.cc.museumloan.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * 业务时钟：使用系统默认时区，保证“借展是否已开始”按运营所在地日期判定。
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
