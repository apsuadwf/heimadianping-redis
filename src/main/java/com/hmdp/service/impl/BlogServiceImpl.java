package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

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
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        // 根据id查询blog
        Blog blog = this.lambdaQuery().eq(Blog::getId, id).one();
        if (blog == null){
            return Result.fail("笔记不存在");
        }
        // 查询blog有关的用户
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 当前用户是否已经点赞该blog
     * @param blog blog信息
     */
    private void isBlogLiked(Blog blog) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        // 判断当前用户是否点赞
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    /**
     * 查询blog相关用户
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
        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
        // 未点赞,可以点赞
        // 包装类与可能存在NPE
        if (BooleanUtil.isFalse(isMember)){
            // 数据库点赞数+1
            boolean isSuccess = lambdaUpdate().setSql("liked = liked + 1").eq(Blog::getId, id).update();
            // 保存用户到redis的set集合
            if (isSuccess){
                redisTemplate.opsForSet().add(key,userId.toString());
            }
        }else{
            // 点过赞,取消点赞
            // 数据库点赞数-1
            boolean isSuccess = lambdaUpdate().setSql("liked = liked - 1").eq(Blog::getId, id).update();
            // 把用户从redis中set集合移除
            if (isSuccess) {
                redisTemplate.opsForSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }
}
