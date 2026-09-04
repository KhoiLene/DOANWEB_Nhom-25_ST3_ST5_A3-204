package com.shopchain.service.impl;

import com.shopchain.entity.NewsCategory;
import com.shopchain.entity.Post;
import com.shopchain.repository.NewsCategoryRepository;
import com.shopchain.repository.PostRepository;
import com.shopchain.service.NewsService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsServiceImpl implements NewsService {

    private final PostRepository postRepository;
    private final NewsCategoryRepository newsCategoryRepository;

    public NewsServiceImpl(PostRepository postRepository, NewsCategoryRepository newsCategoryRepository) {
        this.postRepository = postRepository;
        this.newsCategoryRepository = newsCategoryRepository;
    }

    @Override
    public List<Post> getAllPosts() {
        return postRepository.findByStatusOrderByPublishedAtDesc("published");
    }

    @Override
    public Post getPostBySlug(String slug) {
        return postRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Bài viết không tìm thấy"));
    }

    @Override
    public List<Post> getPostsByCategory(Long categoryId) {
        return postRepository.findByCategory_IdAndStatus(categoryId, "published");
    }

    @Override
    public List<NewsCategory> getAllCategories() {
        return newsCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }
}
