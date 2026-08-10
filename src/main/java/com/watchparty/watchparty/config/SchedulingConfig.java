package com.watchparty.watchparty.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// PresenceSweeper의 @Scheduled를 활성화
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
