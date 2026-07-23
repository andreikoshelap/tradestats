package com.gatto.tradestats.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // отдельный конфиг-класс, чтобы @EnableScheduling не тянулся в главный
    // класс приложения — на будущее, если сервис обрастёт другими конфигами
}