package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class LockUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //加锁
    public boolean tryLock(String key){
        log.info(Thread.currentThread().getName()+"抢到互斥锁");
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",7, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    public void unLock(String key){
        log.info(Thread.currentThread().getName()+"释放互斥锁");
        stringRedisTemplate.delete(key);
    }

}
