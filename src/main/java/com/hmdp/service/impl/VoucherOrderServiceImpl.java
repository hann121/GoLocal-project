package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.LockUtil;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.StringValue;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.Lock;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private IVoucherOrderService voucherOrderService;

    private long createOrdercreateOrder;

    @Autowired
    private LockUtil lockUtil;

    @Autowired
    @Qualifier("orderProcessingExecutor")
    private Executor orderProcessingExecutor;

    @Autowired
    @Qualifier("seckillVoucherBloomFilter")
    private RBloomFilter<Long> seckillVoucherBloomFilter;

    @Autowired
    private IVoucherOrderService proxy;

    public static final String LOCK_PRE = "order";

    //lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    private volatile boolean isRunning = true;

    //启动消费者线程
    @PostConstruct
    public void startVoucherOrderConsumser(){
        log.info("=======启动消费者线程池=======");

        String queueName = "stream.orders";
        orderProcessingExecutor.execute(()->{
            try {
                log.info("消费者延时3s启动");
                // 先睡 2 秒，等 Redis 初始化完成
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                return;
            }
            while(isRunning){
                try{

                    //先处理pending队列（没确认的旧消息）
                    handelPendingList();

                    //获取消息队列里面的信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAM stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if(list==null || list.isEmpty()){
                        continue;
                    }
                    MapRecord<String,Object,Object> record = list.get(0);
                    Map<Object,Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //下单
                    proxy.createOrder(voucherOrder);
                    //下单后ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                }catch (Exception e){
                    log.error("处理订单异常",e);
                }
            }
        });
    }


    //处理未确认的订单
    private void handelPendingList() {
        String queueName = "stream.orders";
        while(isRunning){
            try{
                //获取消息队列里面的信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAM stream.orders >
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                if(list==null || list.isEmpty()){
                    break;
                }
                MapRecord<String,Object,Object> record = list.get(0);
                Map<Object,Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                //下单
                proxy.createOrder(voucherOrder);
                //下单后ACK确认
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            }catch (Exception e){
                log.error("处理pending订单异常",e);
            }
        }
    }

    @PreDestroy
    public void stop(){
        log.info("关闭消费者线程");
        isRunning = false;
    }

    /*
     * 秒杀券下单
     * */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //布隆判断是否存在该秒杀券
        if(!seckillVoucherBloomFilter.contains(voucherId)){
            return Result.fail("不存在对应的秒杀券!");
        }
        //获取用户和订单id
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.createId("order");
        //执行lua脚本,向stream消息队列写入新订单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        if(r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //获取代理对象
        //new一个消费者线程调用线程池在后台专门进行数据库处理

        return Result.ok(orderId);
    }


    /*
    */
/*
    * 秒杀券下单
    * *//*

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询秒杀券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //若秒杀时间未开启
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始!");
        }
        //若秒杀时间已结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束!");
        }
        //在时间段内，判断库存
        //库存不足，返回
        if(voucher.getStock()<1){
            log.info("库存不足!");
            return Result.fail("库存不足!");
        }

        //根据优惠券id和用户id查询订单，实现一人一单
        Long userId = UserHolder.getUser().getId();
        String lockKey = LOCK_PRE + userId;

        boolean isLock = lockUtil.tryLock(lockKey,1200);
        //存在，返回已存在
        if (!isLock){
            //获取锁失败
            return Result.fail("请勿重复购买!");
        }
        //不存在，则扣减库存，创建订单
        try{
            //生成事务代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createOrder(voucherId);
        }finally {
            lockUtil.unLock(lockKey);
        }
    }
*/

    /*
     * 生成订单
     * */
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(VoucherOrder voucherOrder){
        //由于是新的线程执行下单任务，所以不能调用UserHolder
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        //调用redisson的分布式锁
        RLock redisLock = redissonClient.getLock("lock:order:"+userId);
        //尝试获取锁
        boolean isLock = redisLock.tryLock();
        //获取失败，返回不允许重复下单
        if(!isLock){
            log.error("不允许重复下单!");
            return;
        }
        try {
            //获取成功
            //查询订单是否买过
            Integer count = query().eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            // 买过，返回已买过
            if (count > 0) {
                log.error("不允许重复下单");
                return;
            }
            //没买过，则扣减库存，
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    //stock>0
                    .gt("stock", 0)
                    .update();
            //扣减失败，库存不足
            if (!success) {
                log.error("库存不足!");
                return;
            }
            //创建订单
            log.info("后台处理订单");
            save(voucherOrder);
        }finally {
            redisLock.unlock();
        }
    }


    /*
    * 生成订单
    * *//*
    @Transactional
    public Result createOrder(Long voucherId){
        long userId = UserHolder.getUser().getId();
        //再次判断用户是否下过单
        int count = this.query()
                .eq("user_id",userId)
                .eq("voucher_id",voucherId)
                .count();

        if(count>0){
            log.info(userId+"用户已买过，不可重复购买");
            return Result.fail("请勿重复购买!");
        }
        //库存充足，扣减数量，创建订单，返回订单id
        //使用乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id",voucherId)
                .gt("stock",0)
                .update();

        if(!success){
            //扣减失败
            log.info("库存不足!");
            return Result.fail("库存不足!");
        }

        VoucherOrder order = new VoucherOrder();
        //创建订单id
        long orderId = redisIdWorker.createId("order");
        order.setVoucherId(voucherId);
        order.setUserId(userId);
        order.setId(orderId);

        //保存到数据库
        save(order);

        return Result.ok(orderId);
    }*/
}
