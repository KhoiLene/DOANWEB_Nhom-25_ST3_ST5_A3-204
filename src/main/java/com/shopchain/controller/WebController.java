package com.shopchain.controller;

import com.shopchain.dto.*;
import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@Controller
public class WebController {

    private final ProductService productService;
    private final NewsService newsService;
    private final AboutService aboutService;
    private final CartService cartService;
    private final AuthService authService;
    private final HomepageValueRepository homepageValueRepository;
    private final HomepagePromoBannerRepository promoBannerRepository;
    private final HomepageBlogRepository homepageBlogRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewService reviewService;

    public WebController(ProductService productService, NewsService newsService, AboutService aboutService,
                         CartService cartService, AuthService authService, HomepageValueRepository homepageValueRepository,
                         HomepagePromoBannerRepository promoBannerRepository, HomepageBlogRepository homepageBlogRepository,
                         CategoryRepository categoryRepository, ReviewService reviewService) {
        this.productService = productService;
        this.newsService = newsService;
        this.aboutService = aboutService;
        this.cartService = cartService;
        this.authService = authService;
        this.homepageValueRepository = homepageValueRepository;
        this.promoBannerRepository = promoBannerRepository;
        this.homepageBlogRepository = homepageBlogRepository;
        this.categoryRepository = categoryRepository;
        this.reviewService = reviewService;
    }

    // Mock userId for demo purposes until Auth is wired
    private final Long MOCK_USER_ID = 1L;

    @GetMapping("/")
    public String index(Model model) {
        // 1. Brand Core Values
        model.addAttribute("brandValues", homepageValueRepository.findByEnabledTrueOrderBySortOrderAsc());

        // 2. Promo Banners
        model.addAttribute("promoBanners", promoBannerRepository.findByEnabledTrueOrderBySortOrderAsc());

        // 3. Blog Posts
        model.addAttribute("blogPosts", homepageBlogRepository.findByEnabledTrueOrderBySortOrderAsc());

        // 4. Flash Sale Products
        model.addAttribute("flashSaleProducts", productService.getFlashSaleProducts());

        // 5. Featured Products
        model.addAttribute("featuredProducts", productService.getFeaturedProducts());

        // 6. Categories
        model.addAttribute("categories", categoryRepository.findAll());

        return "index";
    }

    @GetMapping("/products")
    public String products(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {

        var productPage = productService.searchProducts(keyword, categoryId, PageRequest.of(page, 12));
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("categories", categoryRepository.findAll());

        return "products";
    }

    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        Product product = productService.getProductDetail(id);
        model.addAttribute("product", product);
        model.addAttribute("reviews", reviewService.getReviewsByProduct(id));
        model.addAttribute("avgRating", reviewService.getAverageRating(id));
        model.addAttribute("reviewCount", reviewService.countReviews(id));
        model.addAttribute("relatedProducts", productService.getRelatedProducts(id));
        model.addAttribute("categories", categoryRepository.findAll());
        return "product-detail";
    }

    @GetMapping("/news")
    public String news(
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            Model model) {

        if (categoryId != null) {
            model.addAttribute("posts", newsService.getPostsByCategory(categoryId));
        } else {
            model.addAttribute("posts", newsService.getAllPosts());
        }
        model.addAttribute("categories", newsService.getAllCategories());

        return "news";
    }

    @GetMapping("/news/{slug}")
    public String newsDetail(@PathVariable String slug, Model model) {
        model.addAttribute("post", newsService.getPostBySlug(slug));
        return "news-detail";
    }

    @GetMapping("/cart")
    public String cart(Model model) {
        Cart cart = cartService.getCart(MOCK_USER_ID);
        model.addAttribute("cart", cart);
        model.addAttribute("cartItems", cart.getItems());

        double subtotal = cart.getItems().stream()
                .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", subtotal >= 500000 ? 0 : 30000);
        model.addAttribute("total", subtotal + (subtotal >= 500000 ? 0 : 30000));

        return "cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long productId) {
        cartService.removeFromCart(MOCK_USER_ID, productId);
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clearCart() {
        cartService.clearCart(MOCK_USER_ID);
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(Model model) {
        Cart cart = cartService.getCart(MOCK_USER_ID);
        model.addAttribute("cart", cart);
        model.addAttribute("cartItems", cart.getItems());

        double subtotal = cart.getItems().stream()
                .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", subtotal >= 500000 ? 0 : 30000);
        model.addAttribute("total", subtotal + (subtotal >= 500000 ? 0 : 30000));

        return "checkout";
    }

    @PostMapping("/checkout/place-order")
    public String placeOrder(@RequestParam String name, @RequestParam String phone, @RequestParam String address) {
        cartService.clearCart(MOCK_USER_ID);
        return "redirect:/order-success";
    }

    @GetMapping("/order-success")
    public String orderSuccess() {
        return "order-success";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login-process")
    public String loginProcess(@RequestParam String username, @RequestParam String password, Model model) {
        try {
            authService.login(username, password);
            return "redirect:/profile";
        } catch (Exception e) {
            model.addAttribute("error", "Invalid username or password");
            return "login";
        }
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register-process")
    public String registerProcess(@RequestParam String username, @RequestParam String password,
                                  @RequestParam String email, @RequestParam String fullName,
                                  @RequestParam String phone, Model model) {
        try {
            RegisterRequest request = new RegisterRequest(username, password, email, fullName, phone);
            String message = authService.register(request);
            model.addAttribute("message", message);
            return "register-success";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password-process")
    public String forgotPasswordProcess(@RequestParam String email, Model model) {
        try {
            String message = authService.forgotPassword(email);
            model.addAttribute("message", message);
            return "forgot-password-success";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "forgot-password";
        }
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("aboutContent", aboutService.getAboutContent().orElse("Chào mừng bạn đến với Techtra."));
        return "about";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/entertainment")
    public String entertainment() {
        return "entertainment";
    }
}
/*
        if (categoryId != null) {
            model.addAttribute("posts", newsService.getPostsByCategory(categoryId));
        } else {
            model.addAttribute("posts", newsService.getAllPosts());
        }
        model.addAttribute("categories", newsService.getAllCategories());

        return "news";
    }

    @GetMapping("/news/{slug}")
    public String newsDetail(@PathVariable String slug, Model model) {
        model.addAttribute("post", newsService.getPostBySlug(slug));
        return "news-detail";
    }

    @GetMapping("/cart")
    public String cart(Model model) {
        Cart cart = cartService.getCart(MOCK_USER_ID);
        model.addAttribute("cart", cart);
        model.addAttribute("cartItems", cart.getItems());

        double subtotal = cart.getItems().stream()
                .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", subtotal >= 500000 ? 0 : 30000);
        model.addAttribute("total", subtotal + (subtotal >= 500000 ? 0 : 30000));

        return "cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long productId) {
        cartService.removeFromCart(MOCK_USER_ID, productId);
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clearCart() {
        cartService.clearCart(MOCK_USER_ID);
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(Model model) {
        Cart cart = cartService.getCart(MOCK_USER_ID);
        model.addAttribute("cart", cart);
        model.addAttribute("cartItems", cart.getItems());

        double subtotal = cart.getItems().stream()
                .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", subtotal >= 500000 ? 0 : 30000);
        model.addAttribute("total", subtotal + (subtotal >= 500000 ? 0 : 30000));

        return "checkout";
    }

    @PostMapping("/checkout/place-order")
    public String placeOrder(@RequestParam String name, @RequestParam String phone, @RequestParam String address) {
        cartService.clearCart(MOCK_USER_ID);
        return "redirect:/order-success";
    }

    @GetMapping("/order-success")
    public String orderSuccess() {
        return "order-success";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login-process")
    public String loginProcess(@RequestParam String username, @RequestParam String password, Model model) {
        try {
            authService.login(username, password);
            return "redirect:/profile";
        } catch (Exception e) {
            model.addAttribute("error", "Invalid username or password");
            return "login";
        }
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register-process")
    public String registerProcess(@RequestParam String username, @RequestParam String password,
                                  @RequestParam String email, @RequestParam String fullName,
                                  @RequestParam String phone, Model model) {
        try {
            RegisterRequest request = new RegisterRequest(username, password, email, fullName, phone);
            String message = authService.register(request);
            model.addAttribute("message", message);
            return "register-success";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password-process")
    public String forgotPasswordProcess(@RequestParam String email, Model model) {
        try {
            String message = authService.forgotPassword(email);
            model.addAttribute("message", message);
            return "forgot-password-success";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "forgot-password";
        }
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("aboutContent", aboutService.getAboutContent().orElse("Chào mừng bạn đến với Techtra."));
        return "about";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/entertainment")
    public String entertainment() {
        return "entertainment";
    }
}
*/
