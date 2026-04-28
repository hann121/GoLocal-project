package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FollowMapper followMapper;
    @Resource
    private IUserService userService;

    private static final String FOLLOW_KEY_PRE = "follow:";
    /*
    * 关注用户
    * */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result follow(Long id, Boolean isFollow) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();

        //判断存不存在
        if (userId == null) {
            return Result.fail("请先登录，再关注");
        }

        //该用户的关注列表
        String key = FOLLOW_KEY_PRE + userId;

        //执行先db后缓存处理
        if (isFollow) {
            //关注，添加对应关系进follow关联表
            try {
                Follow follow = new Follow();
                follow.setFollowUserId(id);
                follow.setUserId(userId);
                boolean isSuccess = save(follow);
                if (isSuccess) {
                    //添加进缓存
                    stringRedisTemplate.opsForSet().add(key, id.toString());
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            //取关，删除关系
            try {
                //数据库取关
                boolean isSuccess = followMapper.remove(id,userId);
                if(isSuccess){
                    //操作redis
                    stringRedisTemplate.opsForSet().remove(key, id.toString());
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return  Result.ok();
    }

    /*
    * 查询是否关注访问的用户
    * */
    @Override
    public Result followOrNot(Long id) {
        Long userId = UserHolder.getUser().getId();
        if(userId == null){
            return Result.fail("未登录，不能关注");
        }
        String key = FOLLOW_KEY_PRE + userId;
        //查询redis是否关注
        Boolean isFollow = stringRedisTemplate.opsForSet().isMember(key,id.toString());

        return Result.ok(BooleanUtil.isTrue(isFollow));
    }

    /*
    * 查询共同关注
    * */
    @Override
    public Result followCommons(Long id) {
        //查询当前用户
        Long userId = UserHolder.getUser().getId();
        //判空
        if(userId == null){
            return Result.fail("请先登录");
        }
        //set求交集
        String key = FOLLOW_KEY_PRE + userId;
        String key2 = FOLLOW_KEY_PRE + id;
        Set<String> set = stringRedisTemplate.opsForSet().intersect(key, key2);
        //判空
        if(set==null||set.isEmpty()){
            //没有共同关注好友
            log.info("没有共同关注的好友");
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询对应的用户
        List<UserDTO> userDTOs = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //返回userDto
        return Result.ok(userDTOs);
    }


}
