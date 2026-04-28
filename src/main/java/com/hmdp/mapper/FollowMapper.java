package com.hmdp.mapper;

import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {

    /*
    * 删除关注关系，取关
    * */
    @Delete("delete from tb_follow where follow_user_id = #{id} and user_id = #{userId}")
    boolean remove(Long id, Long userId);
}
