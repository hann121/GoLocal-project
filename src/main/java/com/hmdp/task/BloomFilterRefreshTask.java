package com.hmdp.task;

import cn.hutool.bloomfilter.BloomFilter;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class BloomFilterRefreshTask {

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    @Qualifier("shopBloomFilter")
    private RBloomFilter<Long> shopBloomFilter;

    @Autowired
    @Qualifier("seckillVoucherBloomFilter")
    private RBloomFilter<Long> seckillVoucherBloomFilter;


    @Scheduled(cron = "0 0/6 * * * *")
    public void refreshShopBloomFilterData(){
        log.info("===== 每6分钟刷新一次布隆过滤器，实现对商铺刷新 =====");
        //删除布隆过滤器，清空旧数据
        shopBloomFilter.delete();
        //重新配置过滤器
        shopBloomFilter.tryInit(100000,0.01);

        //查询数据库所有id
        List<Long> idList = shopMapper.selectAllId();

        for(Long id:idList){
            shopBloomFilter.add(id);
        }

        log.info("===== 布隆过滤器刷新完成，共加载 {} 条商铺数据 =====", idList.size());
    }

    @Scheduled(cron = "0 0/6 * * * *")
    public void refreshSeckillVoucherBloomFilterData(){
        log.info("===== 每6分钟刷新一次布隆过滤器，实现对优惠券刷新 =====");
        //删除布隆过滤器，清空旧数据
        seckillVoucherBloomFilter.delete();
        //重新配置过滤器
        seckillVoucherBloomFilter.tryInit(100000,0.01);

        //查询数据库所有id
        List<Long> idList = seckillVoucherMapper.selectAllId();

        for(Long id:idList){
            seckillVoucherBloomFilter.add(id);
        }

        log.info("===== 布隆过滤器刷新完成，共加载 {} 条优惠券数据 =====", idList.size());
    }
}
