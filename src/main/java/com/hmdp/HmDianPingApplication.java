package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.hmdp.mapper")
@SpringBootApplication
@Slf4j
@EnableScheduling //任务定时器
@EnableAspectJAutoProxy(exposeProxy = true) // 开启暴露代理
public class HmDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
        log.info("-----黑马点评项目启动-----");
    }

}
