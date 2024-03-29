package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOWS_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // Redis key
        String key = FOLLOWS_KEY + user.getId();
        // 判断是关注or取关
        if (isFollow){
            // 检查是否已经存在关注记录
            int existingCount = count(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, user.getId())
                    .eq(Follow::getFollowUserId, followUserId));

            if (existingCount > 0) {
                return Result.fail("已经关注过该用户");
            }
            // 关注新增数据
            Follow follow = new Follow();
            // 设置当前用户ID
            follow.setUserId(user.getId());
            // 设置关注用户ID
            follow.setFollowUserId(followUserId);
            Boolean saveResult = save(follow);
            if (BooleanUtil.isFalse(saveResult)){
                return Result.fail("关注失败");
            }
            // 关注成功,添加到redis中
            redisTemplate.opsForSet().add(key,followUserId.toString());
        }else{
            // 取关删除数据 delete from tb_follow where user_id = ? and follow_user_id = ?
            Boolean removeResult = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, user.getId())
                    .eq(Follow::getFollowUserId, followUserId));
            if (BooleanUtil.isFalse(removeResult)){
                return Result.fail("取消关注失败");
            }
            // 取消关注,redis中删除
            redisTemplate.opsForSet().remove(key,followUserId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 查询是否关注 select * from tb_follow where user_id = ? and follow_user_id = ?
        int count = count(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, user.getId())
                .eq(Follow::getFollowUserId, followUserId));
        // 判断
        return Result.ok(count > 0);
    }

    @Override
    public Result getCommonFollowers(Long otherUserId) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 创建RedisKey
        String myKey = FOLLOWS_KEY + userId;
        String otherUserKey = FOLLOWS_KEY + otherUserId;
        // 从Redis中查询两个用户关注交集
        Set<String>  intersect= redisTemplate.opsForSet().intersect(myKey, otherUserKey);
        if (intersect == null || intersect.isEmpty()){
            // 交集为空,返回空集合
            return Result.ok(Collections.emptyList());
        }
        // 不为空,通过stream流转换成UserDTOList返回
        List<Long> intersectUserIdList = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOList = listByIds(intersectUserIdList).stream()
                .map(user -> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
