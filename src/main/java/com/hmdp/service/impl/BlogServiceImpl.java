package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;

    /*
    * 查看指定id文章
    * */
    @Override
    public Result findBlogById(Long id) {
        //查询博客
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在!");
        }
        //查询博客对应的发布用户
        queryBlogUser(blog);
        //判断是否点过赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /*
    * 热点
    * */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    //判断是否点过赞
    private void isBlogLiked(Blog blog) {
        //判断用户是否存在
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //用户没登录，不需要查询点赞
            return ;
        }
        //1.获取用户信息
        Long userId = UserHolder.getUser().getId();
        //2.判断用户是否点赞过
        String key = BLOG_LIKED_KEY + blog.getId();
        //SCORE key value，返回时间戳
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    /*
    * 点赞博客
    * */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result likeBlog(Long id) {
        //1.获取用户信息
        Long userId = UserHolder.getUser().getId();
        //2.判断用户是否点赞过
        String key = BLOG_LIKED_KEY + id;
        //SCORE key value，返回时间戳
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //3.未点赞，点赞+1，加入缓存列表
        if (score == null) {
            //添加到缓存列表 ZADD key value score
            stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());

            try {
                //更新数据库
                boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
                if (!isSuccess) {
                    //执行失败，事务自动回滚数据库，需手动回滚缓存
                    stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                    return Result.fail("点赞失败");
                }
            }catch (Exception e){
                // 数据库异常，撤销Redis
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                throw e; // 抛出异常让事务回滚
            }

        } else {
            //4.已点赞，取消点赞
            //更新缓存列表，执行-1操作
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());

            try {
                //更新数据库
                boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
                if(!isSuccess){
                    //执行失败，事务自动回滚数据库，需手动回滚缓存
                    stringRedisTemplate.opsForZSet().add(key,userId.toString(),score);
                    return  Result.fail("取消点赞失败");
                }
            } catch (Exception e) {
                // 数据库异常，恢复Redis
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), score);
                throw e;
            }
        }

        return  Result.ok();
    }

    /*
    * 查询博客点赞列表
    * */
    @Override
    public Result findLikesById(Long id) {
        //查询该id点赞列表的top5
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //不存在，则说明没点赞，返回
        if(top5 == null || top5.isEmpty()){
            return Result.ok();
        }
        //存在
        //取出其中的用户id
        List<Long> ids = new ArrayList<>();
        for(String top : top5){
            ids.add(Long.valueOf(top));
        }
//       List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据id查询用户信息
        List<User> users = userMapper.listByids(ids);
        //平民写法
        List<UserDTO> userDTOS = new ArrayList<>();
        for(User user:users){
            userDTOS.add(BeanUtil.copyProperties(user,UserDTO.class));
        }
//        stream流输入
//        List<UserDTO> userDTOS = users
//                .stream()
//                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
//                .collect(Collectors.toList());
        //返回用户信息
        log.info("该{}博客点赞列表{}",id,userDTOS);
        return Result.ok(userDTOS);
    }

    /*
    * 封装博客的对应作者信息
    * */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
