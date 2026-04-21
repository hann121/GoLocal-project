package com.hmdp.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {

    /*
    * 实现延时双删
    * */
    @Bean
    public Executor taskExecutor(){
        return Executors.newSingleThreadExecutor();
    }

    /*
    * 执行店铺的缓存重建
    * */
    @Bean("refreshShopExecutor")
    public Executor RefreshShopExecutor(){
        return Executors.newSingleThreadExecutor();
    }

    /*
    * 实现下单后后台处理订单
    * */
    @Bean("orderProcessingExecutor")
    public Executor OrderProcessingExecutor(){
        return new ThreadPoolExecutor(
                3,                                  // 核心线程数：根据服务器CPU和IO情况调整
                6,                                  // 最大线程数：防止突刺流量
                60L, TimeUnit.SECONDS,               // 空闲线程存活时间
                new LinkedBlockingQueue<>(5000),     // 有界队列：设置上限，防止OOM
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满了让调用者自己执行，不丢任务
        );
    }
}
