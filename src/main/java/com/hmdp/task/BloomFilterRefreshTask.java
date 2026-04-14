package com.hmdp.task;

import cn.hutool.bloomfilter.BloomFilter;
import com.hmdp.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class BloomFilterRefreshTask {

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private RBloomFilter<Long> bloomFilter;

    @Scheduled(cron = "0 0/3 * * * *")
    public void refreshBloomFilterData(){
        log.info("===== 每3分钟刷新一次布隆过滤器 =====");
        //删除布隆过滤器，清空旧数据
        bloomFilter.delete();
        //重新配置过滤器
        bloomFilter.tryInit(100000,0.01);

        //查询数据库所有id
        List<Long> idList = shopMapper.selectAllId();

        for(Long id:idList){
            bloomFilter.add(id);
        }

        log.info("===== 布隆过滤器刷新完成，共加载 {} 条数据 =====", idList.size());
    }
}
