package com.shopchain.controller;

import com.shopchain.dto.*;
import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import com.shopchain.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

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
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final OrderService orderService;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final CustomerProgramService customerProgramService;

    public WebController(ProductService productService, NewsService newsService, AboutService aboutService,
                         CartService cartService, AuthService authService, HomepageValueRepository homepageValueRepository,
                         HomepagePromoBannerRepository promoBannerRepository, HomepageBlogRepository homepageBlogRepository,
                         CategoryRepository categoryRepository, ReviewService reviewService,
                         UserRepository userRepository, StoreRepository storeRepository, OrderService orderService,
                         SalesInvoiceRepository salesInvoiceRepository, CustomerProgramService customerProgramService) {
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
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.orderService = orderService;
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.customerProgramService = customerProgramService;
    }

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        model.addAttribute("customerLoyaltyPoints", customerLoyaltyPoints(authentication));
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
            @RequestParam(value = "sort", defaultValue = "newest") String sort,
            @RequestParam(value = "sale", defaultValue = "false") boolean saleOnly,
            @RequestParam(value = "discounted", defaultValue = "false") boolean discountedOnly,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "storeId", required = false) Long storeId,
            Model model, Authentication authentication) {

        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String normalizedSort = List.of("newest", "price_asc", "price_desc", "name", "best_seller").contains(sort) ? sort : "newest";
        Double safeMinPrice = minPrice != null && minPrice >= 0 ? minPrice : null;
        Double safeMaxPrice = maxPrice != null && maxPrice >= 0 ? maxPrice : null;
        if (safeMinPrice != null && safeMaxPrice != null && safeMinPrice > safeMaxPrice) {
            Double swap = safeMinPrice;
            safeMinPrice = safeMaxPrice;
            safeMaxPrice = swap;
        }
        List<Store> availableStores = storeRepository.findByActiveTrueAndPickupEnabledTrue();
        Long safeStoreId = storeId != null && availableStores.stream().anyMatch(store -> store.getId().equals(storeId))
                ? storeId : null;
        Sort order = switch (normalizedSort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            case "best_seller" -> Sort.unsorted();
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
        int safePage = Math.max(0, page);
        var pageable = PageRequest.of(safePage, 12, order);
        boolean filterDiscounted = saleOnly || discountedOnly;
        var productPage = "best_seller".equals(normalizedSort)
                ? productService.searchBestSellingProducts(normalizedKeyword, categoryId, filterDiscounted,
                        safeMinPrice, safeMaxPrice, safeStoreId, pageable)
                : productService.searchFilteredProducts(normalizedKeyword, categoryId, filterDiscounted,
                        safeMinPrice, safeMaxPrice, safeStoreId, pageable);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("customerLoyaltyPoints", customerLoyaltyPoints(authentication));
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalResults", productPage.getTotalElements());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("stores", availableStores);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("saleOnly", saleOnly);
        model.addAttribute("discountedOnly", discountedOnly);
        model.addAttribute("minPrice", safeMinPrice);
        model.addAttribute("maxPrice", safeMaxPrice);
        model.addAttribute("selectedStoreId", safeStoreId);
        model.addAttribute("pageNumbers", IntStream.range(0, productPage.getTotalPages()).boxed().toList());

        return "products";
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "keyword", required = false) String keyword) {
        return "redirect:/products" + (keyword == null || keyword.isBlank() ? "" : "?keyword=" +
                java.net.URLEncoder.encode(keyword.trim(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable Long id, Model model, Authentication authentication) {
        Product product = productService.getProductDetail(id);
        model.addAttribute("product", product);
        model.addAttribute("customerLoyaltyPoints", customerLoyaltyPoints(authentication));
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
    public String cart(Model model, Authentication authentication) {
        Long userId = currentUserId(authentication);
        int points = customerLoyaltyPoints(authentication);
        Cart cart = cartService.getCart(userId);
        model.addAttribute("cart", cart);
        model.addAttribute("cartItems", cart.getItems());
        model.addAttribute("customerLoyaltyPoints", points);
        model.addAttribute("stores", storeRepository.findByActiveTrueAndPickupEnabledTrue());

        double subtotal = cart.getItems().stream()
                .mapToDouble(item -> item.getProduct().effectivePrice(points) * item.getQuantity())
                .sum();

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", subtotal >= 500000 ? 0 : 30000);
        model.addAttribute("total", subtotal + (subtotal >= 500000 ? 0 : 30000));

        return "cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(@RequestParam Long productId, Authentication authentication) {
        cartService.removeFromCart(currentUserId(authentication), productId);
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(@RequestParam Long productId, @RequestParam Integer quantity, Authentication authentication) {
        if (quantity == null || quantity < 1) {
            cartService.removeFromCart(currentUserId(authentication), productId);
        } else {
            cartService.updateQuantity(currentUserId(authentication), productId, quantity);
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clearCart(Authentication authentication) {
        cartService.clearCart(currentUserId(authentication));
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(Model model, Authentication authentication) {
        Long userId = currentUserId(authentication);
        int points = customerLoyaltyPoints(authentication);
        Cart cart = cartService.getCart(userId);
        if (cart.getItems().isEmpty()) return "redirect:/cart";
        model.addAttribute("cart", cart);
        model.addAttribute("cartItems", cart.getItems());
        model.addAttribute("stores", storeRepository.findByActiveTrueAndPickupEnabledTrue());
        model.addAttribute("customer", userRepository.findById(userId).orElseThrow());
        model.addAttribute("customerLoyaltyPoints", points);

        double subtotal = cart.getItems().stream()
                .mapToDouble(item -> item.getProduct().effectivePrice(points) * item.getQuantity())
                .sum();

        model.addAttribute("subtotal", subtotal);
        model.addAttribute("shipping", subtotal >= 500000 ? 0 : 30000);
        model.addAttribute("total", subtotal + (subtotal >= 500000 ? 0 : 30000));

        return "checkout";
    }

    @PostMapping("/checkout/place-order")
    public String placeOrder(@RequestParam String name, @RequestParam String phone, @RequestParam String address,
                             @RequestParam String deliveryCity, @RequestParam Long storeId,
                             @RequestParam String fulfillmentType, @RequestParam String paymentMethod,
                             @RequestParam(required = false) Double deliveryLatitude,
                             @RequestParam(required = false) Double deliveryLongitude,
                             Authentication authentication, Model model) {
        try {
            Order order = orderService.placeOnlineOrder(currentUserId(authentication), storeId, name, phone,
                    address, deliveryCity, fulfillmentType, paymentMethod, deliveryLatitude, deliveryLongitude);
            return "redirect:/order-success?orderId=" + order.getId();
        } catch (RuntimeException ex) {
            Cart cart = cartService.getCart(currentUserId(authentication));
            model.addAttribute("cart", cart);
            model.addAttribute("cartItems", cart.getItems());
            model.addAttribute("stores", storeRepository.findByActiveTrueAndPickupEnabledTrue());
            model.addAttribute("customer", userRepository.findById(currentUserId(authentication)).orElseThrow());
            int points = customerLoyaltyPoints(authentication);
            model.addAttribute("customerLoyaltyPoints", points);
            double subtotal = cart.getItems().stream().mapToDouble(item -> item.getProduct().effectivePrice(points) * item.getQuantity()).sum();
            model.addAttribute("subtotal", subtotal);
            model.addAttribute("shipping", subtotal >= 500000 ? 0 : 30000);
            model.addAttribute("total", subtotal + (subtotal >= 500000 ? 0 : 30000));
            model.addAttribute("checkoutError", ex.getMessage());
            return "checkout";
        }
    }

    @GetMapping("/order-success")
    public String orderSuccess(@RequestParam(required = false) Long orderId, Model model) {
        model.addAttribute("orderId", orderId);
        return "order-success";
    }

    @GetMapping("/my/invoices")
    public String myInvoices(Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        model.addAttribute("invoices", salesInvoiceRepository.findByOrderUserIdOrderByIssuedAtDesc(user.getId()));
        return "my-invoices";
    }

    @GetMapping("/my/invoices/{invoiceId}")
    public String myInvoice(@PathVariable Long invoiceId, Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId).orElseThrow();
        if (!invoice.getOrder().getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Biên nhận không thuộc tài khoản này.");
        model.addAttribute("invoice", invoice);
        return "my-invoice";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login-process")
    public String loginProcess(@RequestParam String username, @RequestParam String password,
                               Model model, HttpServletRequest request,
                               jakarta.servlet.http.HttpServletResponse response) {
        try {
            AuthResponse authResponse = authService.login(username, password);
            ResponseCookie cookie = ResponseCookie.from("access_token", authResponse.getAccessToken())
                    .httpOnly(true)
                    .secure(request.isSecure())
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(Duration.ofHours(24))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
            return switch (authResponse.getRole()) {
                case "ROLE_ADMIN" -> "redirect:/admin/dashboard";
                case "ROLE_WAREHOUSE" -> "redirect:/warehouse/inventory";
                case "ROLE_STORE_STAFF" -> "redirect:/staff/orders";
                default -> "redirect:/";
            };
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
    public String profile(Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        List<Order> orders = orderService.getUserOrders(user.getId(), PageRequest.of(0, 50)).getContent();
        double totalSpent = orders.stream()
                .filter(order -> "PAID".equalsIgnoreCase(order.getPaymentStatus()))
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.getStatus()))
                .mapToDouble(Order::getTotalAmount).sum();
        int points = user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints();
        model.addAttribute("customer", user);
        model.addAttribute("customerOrders", orders);
        model.addAttribute("promotionPrograms", customerProgramService.listFor(user.getId()));
        model.addAttribute("totalSpent", totalSpent);
        model.addAttribute("loyaltyTier", points >= 1500 ? "Kim cương" : points >= 500 ? "Vàng" : "Thành viên");
        return "profile";
    }

    @GetMapping("/entertainment")
    public String entertainment() {
        return "entertainment";
    }

    @PostMapping("/logout-process")
    public String logout(jakarta.servlet.http.HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .httpOnly(true).path("/").sameSite("Strict").maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return "redirect:/";
    }

    private Long currentUserId(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản đang đăng nhập."))
                .getId();
    }

    private int customerLoyaltyPoints(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return 0;
        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints())
                .orElse(0);
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
