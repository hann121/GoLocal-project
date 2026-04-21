package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* 配置布隆过滤器
* */
@Configuration
@Slf4j
public class BloomFilterConfig {

    @Bean("shopBloomFilter")
    public RBloomFilter<Long> ShopBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter("bloom:filter:shop");
        filter.tryInit(100000L, 0.01);
        return filter;
    }

    @Bean("seckillVoucherBloomFilter")
    public RBloomFilter<Long> SeckillVoucherbloomFilter(RedissonClient redissonClient) {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter("bloom:filter:seckillVoucher");
        filter.tryInit(100000L, 0.01);
        return filter;
    }
}
