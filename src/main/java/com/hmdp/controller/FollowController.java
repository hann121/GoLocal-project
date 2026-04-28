package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
@Slf4j
public class FollowController {

    @Resource
    private IFollowService followService;

    /*
    * 关注用户
    * */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id")Long id,@PathVariable("isFollow")Boolean isFollow){
        log.info("关注用户{},{}",id,isFollow);
        return followService.follow(id,isFollow);
    }

    /*
    *查看是否关注该用户
    * */
    @GetMapping("/or/not/{id}")
    public Result followOrNot(@PathVariable("id") Long id){
        log.info("调用查询是否关注该用户接口:{}",id);
        return followService.followOrNot(id);
    }

    /*
    *查询共同关注
    * */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id")Long id){
        log.info("调用查询共同关注接口:{}",id);

        return followService.followCommons(id);
    }

}
