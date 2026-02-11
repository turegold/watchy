package com.watchparty.watchparty.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable()) // Postman 테스트용
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/rooms/**").permitAll() // rooms 전체 허용
                        .requestMatchers("ws", "/ws/**").permitAll() // WebSocket 허용
                        .anyRequest().permitAll() // 일단 전체 허용
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}
