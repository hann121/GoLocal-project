package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import io.micrometer.core.instrument.Meter;
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

    private static final String ID_PRE = UUID.randomUUID().toString(true)+"-";

    //加锁
    public boolean tryLock(String key){
        log.info(Thread.currentThread().getName()+"抢到互斥锁");
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",7, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    public void unLock(String key){
        log.info(Thread.currentThread().getName()+"释放互斥锁");
        String threadId = ID_PRE + Thread.currentThread().getId();

        //判断是否为本线程的锁
        if(threadId.equals(stringRedisTemplate.opsForValue().get(key))){
            //释放锁
            stringRedisTemplate.delete(key);
        }
    }

    /*//加锁,用于秒杀券单人单券
    public boolean tryLock(String key,long timeoutSec){
        log.info(Thread.currentThread().getName()+"抢到互斥锁");
        String threadId = ID_PRE + Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,threadId,timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }*/

    //加锁,用于秒杀券单人单券(可重入锁
    public boolean tryLock(String key,long timeoutSec){
        log.info(Thread.currentThread().getName()+"抢到互斥锁");
        String threadId = ID_PRE + Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,threadId,timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

}
