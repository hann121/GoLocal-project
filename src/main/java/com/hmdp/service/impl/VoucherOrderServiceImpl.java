package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.LockUtil;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
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
    @Qualifier("seckillVoucherBloomFilter")
    private RBloomFilter<Long> seckillVoucherBloomFilter;

    public static final String LOCK_PRE = "order";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /*
     * 秒杀券下单
     * */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //布隆判断是否存在该秒杀券
        if(!seckillVoucherBloomFilter.contains(voucherId)){
            return Result.fail("不存在对应的秒杀券!");
        }
        Long userId = UserHolder.getUser().getId();
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        if(r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //TODO 将订单放入队列
        return Result.ok();
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
    }
}
