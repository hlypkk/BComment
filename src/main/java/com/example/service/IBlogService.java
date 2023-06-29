package com.example.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dto.Result;
import com.example.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IBlogService extends IService<Blog> {

    Result saveBlog(Blog blog);

    Result likeBlog(Long id);

    Result queryMyBlog(Integer current);

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result queryBlogLikes(Long id);

    Result queryBlogByUserId(Integer count, Long id);

    Result queryBlogOfFollow(Long max, Integer offset);
}
