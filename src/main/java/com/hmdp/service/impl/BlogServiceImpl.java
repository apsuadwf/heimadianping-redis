package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Autowired
    private IFollowService followService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        // 根据id查询blog
        Blog blog = this.lambdaQuery().eq(Blog::getId, id).one();
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        // 查询blog有关的用户
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 当前用户是否已经点赞该blog
     *
     * @param blog blog信息
     */
    private void isBlogLiked(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录,无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        // 判断当前用户是否点赞
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    /**
     * 查询blog相关用户
     *
     * @param blog blog信息
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        // 判断当前用户是否点赞
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        // 未点赞,可以点赞
        // 包装类与可能存在NPE
        if (score == null) {
            // 数据库点赞数+1
            boolean isSuccess = lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, id).update();
            // 保存用户到redis的set集合
            if (isSuccess) {
                // key value score
                redisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 点过赞,取消点赞
            // 数据库点赞数-1
            boolean isSuccess = lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, id).update();
            // 把用户从redis中set集合移除
            if (isSuccess) {
                redisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 查询top5的点赞用户 ZRange
        Set<String> userIds = redisTemplate.opsForZSet().range(key, 0, 4);
        if (userIds == null || userIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 解析出其中用户id
        List<Long> ids = userIds.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 根据用户id查询用户
        List<UserDTO> userDTOList = userService.lambdaQuery()
                .in(User::getId, ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 返回
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        this.save(blog);
        // 查询作者的所有粉丝
        List<Follow> followList = followService.lambdaQuery().eq(Follow::getFollowUserId, user.getId()).list();
        // 推送笔记id给所有粉丝
        followList.forEach((follow -> {
            // 获取粉丝ID
            Long userId = follow.getUserId();
            // 推送
            String key = FEED_KEY + userId;
            redisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }));
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 查询收件箱 ZREVRANGEBYSCORE key Max Min WITHSCORES LIMIT Offset Count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 解析数据:BlogId minTime(时间戳) offset
        List<Long> blogIdList = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 获取blogId
            blogIdList.add(Long.valueOf(tuple.getValue()));
            // 获取分数(事件戳)
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 根据id查询blog
        String idStr = StrUtil.join(",", blogIdList);
        List<Blog> blogList = lambdaQuery().in(Blog::getId, blogIdList).last("ORDER BY FIELD(id," + idStr + ")")
                .list().stream().peek(blog -> {
                    // 查询blog有关的用户
                    queryBlogUser(blog);
                    // 查询blog是否被点赞
                    isBlogLiked(blog);
                }).collect(Collectors.toList());
        System.out.println(blogList.size());
        // 封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
