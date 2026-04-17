package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.notification.RunListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService ex = Executors.newFixedThreadPool(500);

    @Test
    public void testId(){

    }

}
