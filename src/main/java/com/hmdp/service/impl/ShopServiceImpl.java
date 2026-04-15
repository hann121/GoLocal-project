package com.hmdp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.LockUtil;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private TaskExecutor taskExecutor;
    @Autowired
    private RBloomFilter<Long> bloomFilter;
    @Autowired
    private LockUtil lockUtil;

    @Autowired
    @Qualifier("refreshShopExecutor")
    private Executor refreshShopExecutor;

    /*
     * 根据id查询商铺信息
     * */
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //先通过布隆过滤层
        if (!bloomFilter.contains(id)) {
            return Result.fail("商铺不存在!");
        }
        //从redis查询是否有商铺缓存信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //若存在，解析逻辑是否过期
        if (shopJson != null && shopJson != "") {
            RedisData redisData = JSONUtil.toBean(shopJson,RedisData.class);
            Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
            LocalDateTime expireTime = redisData.getExpireTime();
            //如果逻辑缓存未过期
            if(expireTime.isAfter(LocalDateTime.now())){
                log.info("逻辑缓存未过期，返回旧数据{}",shop);
                return Result.ok(shop);
            }
            //逻辑缓存过期，则进行缓存重建
            //获取互斥锁
            String lockKey = LOCK_SHOP_KEY + id;
            boolean isLock = lockUtil.tryLock(lockKey);
            //判断是否获得成功
            if(isLock){
                try{
                    //成功则用线程池实现重建缓存
                    log.info("执行缓存重建");
                    refreshShopExecutor.execute(()->{
                        this.saveShop2Redis(id,20L);
                    });
                }catch(RuntimeException e){
                    e.printStackTrace();
                }finally {
                    lockUtil.unLock(lockKey);
                }
            }
            return Result.ok(shop);
        }
        //不存在，则查询数据库
        Shop shop = getById(id);
        //不存在数据库，报错(布隆过滤时误判)
        if (shop == null) {
            return Result.fail("商铺不存在!");
        }
        //写入逻辑过期缓存
        saveShop2Redis(id,20L);
        return Result.ok(shop);
    }

    //存入逻辑过期缓存（永不过期，设置逻辑过期时间
    private void saveShop2Redis(Long id,Long expireSeconds){
        //根据id查询商铺信息
        Shop shop = getById(id);
        //封装实现redisdata
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);
        //存入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /*
    * 根据id修改商铺信息
    * */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("商铺不能为空!");
        }
        //先删除一遍缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        //更新数据库，先修改慢操作
        updateById(shop);
        //实现延时双删，
        taskExecutor.execute(()->{
            try{
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        });

        return Result.ok();
    }
}
