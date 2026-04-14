package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private TaskExecutor taskExecutor;
    @Autowired
    private RBloomFilter<Long> bloomFilter;
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
        //若存在，直接取
        if (shopJson != null) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //不存在，则查询数据库
        Shop shop = getById(id);
        //不存在数据库，报错
        if (shop == null) {
            //缓存空值，防止穿透
            stringRedisTemplate.opsForValue().set(key,"",2,TimeUnit.MINUTES);
            return Result.fail("商铺不存在!");
        }
        //存在数据库，取出信息放入缓存，并返回商铺信息
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        return Result.ok(shop);
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
