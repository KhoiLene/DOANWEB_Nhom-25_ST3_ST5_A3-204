package com.shopchain.service;

import com.shopchain.entity.NewsCategory;
import com.shopchain.entity.Post;
import java.util.List;

public interface NewsService {
    List<Post> getAllPosts();
    Post getPostBySlug(String slug);
    List<Post> getPostsByCategory(Long categoryId);
    List<NewsCategory> getAllCategories();
}
