package com.shopchain.controller;

import com.shopchain.repository.CategoryRepository;
import com.shopchain.service.NewsService;
import com.shopchain.service.ProductService;
import org.springframework.ui.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {WebController.class, StoreLocatorController.class})
public class StorefrontModelAdvice {
    private final CategoryRepository categoryRepository;
    private final ProductService productService;
    private final NewsService newsService;
    private final String googleMapsBrowserKey;

    public StorefrontModelAdvice(CategoryRepository categoryRepository, ProductService productService,
                                 NewsService newsService,
                                 @Value("${maps.google.browser-key:}") String googleMapsBrowserKey) {
        this.categoryRepository = categoryRepository;
        this.productService = productService;
        this.newsService = newsService;
        this.googleMapsBrowserKey = googleMapsBrowserKey;
    }

    @ModelAttribute
    public void addNavigationData(Model model) {
        model.addAttribute("navCategories", categoryRepository.findAll());
        model.addAttribute("navFlashSaleProducts", productService.getFlashSaleProducts().stream().limit(5).toList());
        model.addAttribute("navNewsCategories", newsService.getAllCategories());
        model.addAttribute("googleMapsBrowserKey", googleMapsBrowserKey);
    }
}
