package com.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dto.Result;
import com.example.dto.ScrollResult;
import com.example.dto.UserDTO;
import com.example.entity.Blog;
import com.example.entity.Follow;
import com.example.entity.User;
import com.example.mapper.BlogMapper;
import com.example.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.service.IFollowService;
import com.example.service.IUserService;
import com.example.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import static com.example.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.example.utils.RedisConstants.FEED_KEY;
import static com.example.utils.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;
    /**
     * 保存博客并推送
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        //1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //2. 保存探店博文
        boolean save = save(blog);
        //3. 判断是否成功
        if (!save){
            return Result.fail("新增博文失败");
        }
        //4. 查询作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //5. 推送博文给粉丝
        follows.forEach((item) -> {
            //获取粉丝id
            Long userId = item.getUserId();
            String key = FEED_KEY + userId;
            //推送
            stringRedisTemplate.opsForZSet().add(key , blog.getId().toString() , System.currentTimeMillis());
        });
        // 返回博文id
        return Result.ok(blog.getId());
    }
    /**
     * 点赞
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 3.如果未点赞，可以点赞
        if (score == null){
            // 3.1.数据库点赞数 + 1
            boolean isSuccess1 = update().setSql("liked = liked + 1 ").eq("id",id).update();
            // 3.2.保存用户到Redis的set集合  zadd key value score
            if (isSuccess1) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }else {
            //4.如果已点赞，取消点赞
            // 4.1.数据库点赞数 -1
            boolean isSuccess2 = update().setSql("liked = liked - 1 ").eq("id",id).update();
            // 4.2.把用户从Redis的set集合移除
            if(isSuccess2){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
            return Result.ok();
    }
    /**
     * 查询当前用户博客
     * @param current
     * @return
     */
    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 按点赞数排序
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 查询博文有关的用户
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        //查询当前用户id
        Long userId = blog.getUserId();
        //得到用户对象
        User user = userService.getById(userId);
        //设置昵称
        blog.setName(user.getNickName());
        //设置头像
        blog.setIcon(user.getIcon());
    }

    /**
     * 通过id查询博客
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //查询博文id
        Blog blog = getById(id);
        //判断是否存在
        if (blog == null){
            //不存在则返回错误
            return Result.fail("笔记不存在");
        }
        //查询博文作者
        queryBlogUser(blog);
        //查询是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 查询点赞数
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //1.查询点赞排行榜前排用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2.解析出其中用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.根据用户id查询用户
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> collect = userService.query().
                in("id",ids).last("ORDER BY FIELD(id," + idStr +")").list().
                stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).
                collect(Collectors.toList());
        //4。返回
        return Result.ok(collect);
    }

    /**
     * 得到分页博文数据
     * @param count
     * @param id
     * @return
     */
    @Override
    public Result queryBlogByUserId(Integer count, Long id) {
        //查询分页数据
        Page<Blog> page = query().eq("user_id", id).page(new Page<>(count, MAX_PAGE_SIZE));
        //得到博文数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 滚轮浏览功能
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1. 查询当前用户id
        Long userId = UserHolder.getUser().getId();
        //2. 拼接key
        String key = FEED_KEY + userId;
        //3. 去redis中查询有序集合（Sorted Set）
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.
                opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        // 4.解析数据：blogId、minTime（时间戳）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 2
        int os = 1; // 2
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) { // 5 4 4 2 2
            // 4.1.获取id
            ids.add(Long.valueOf(Objects.requireNonNull(tuple.getValue())));
            // 4.2.获取分数(时间戳）
            long time = Objects.requireNonNull(tuple.getScore()).longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }
        //5. 根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id",ids).last("ORDER BY FIELD(id," + idStr +")").list();
        blogs.forEach((item) -> {
            //5.1 查询blog有关的用户
            queryBlogUser(item);
            //5.2 查询博文是否被点赞
            isBlogLiked(item);
        });
        //6. 封装并返回
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setOffset(os);
        result.setMinTime(minTime);
        return Result.ok(result);
    }

    /**
     * 查询博文是否被点赞
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
