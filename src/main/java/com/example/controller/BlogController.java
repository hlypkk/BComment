package com.example.controller;

import com.example.dto.Result;
import com.example.entity.Blog;
import com.example.service.IBlogService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 保存博客并推送
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 查询当前用户博客
     * @param current
     * @return
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryMyBlog(current);
    }

    /**
     * 按点赞数排序
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 通过id查询博客
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id){
        return blogService.queryBlogById(id);
    }

    /**
     * 查询点赞数
     * @param id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id){
        return blogService.queryBlogLikes(id);
    }

    /**
     * 通过用户id查询
     * @param count
     * @param id
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(@RequestParam(value = "current" , defaultValue = "1") Integer count,
                                    @RequestParam("id") Long id){
        return blogService.queryBlogByUserId(count , id);
    }

    /**
     * 滑动显示博文功能
     * @param max
     * @param offset
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max,@RequestParam(value = "offset" , defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max,offset);
    }
}
