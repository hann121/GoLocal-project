package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /*
    * 实现商铺类型缓存
    * */
    @Override
    public Result queryByType() {
        //判断redis缓存中是否有对应数据
        String key = "ShopType";
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        //若存在，直接返回
        if(shopTypeJson != null){
            List<ShopType> list = JSONUtil.toList(shopTypeJson,ShopType.class);
            return Result.ok(list);
        }
        //不存在，查询数据库
        List<ShopType> list = query().orderByAsc("sort").list();
        //不存在，返回报错
        if(list == null){
            return Result.fail("商铺类型不存在!");
        }
        //存在，取出并写入缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(list),30, TimeUnit.MINUTES);
        return Result.ok(list);
    }
}
