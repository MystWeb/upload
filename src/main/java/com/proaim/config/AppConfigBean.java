package com.proaim.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:env/${spring.profiles.active}.properties")
@Data
public class AppConfigBean {
    @Value("${mysql.config.jdbc.driver.class.name}")
    private String driverClassName;
    @Value("${mysql.config.jdbc.url}")
    private String jdbcUrl;
    @Value("${mysql.config.jdbc.username}")
    private String jdbcUsername;
    @Value("${mysql.config.jdbc.password}")
    private String jdbcPassword;
    @Value("${mysql.config.jdbc.filter.class.names}")
    private String filterClassNames;
    @Value("${mysql.config.jdbc.initial.size}")
    private Integer initialSize;
    @Value("${mysql.config.jdbc.min.idle}")
    private Integer minIdle;
    @Value("${mysql.config.jdbc.max.active}")
    private Integer maxActive;
    @Value("${mysql.config.jdbc.max.wait}")
    private Long maxWait;
    @Value("${mysql.config.jdbc.time.between.eviction.runs.millis}")
    private Long timeBetweenEvictionRunsMillis;

}
