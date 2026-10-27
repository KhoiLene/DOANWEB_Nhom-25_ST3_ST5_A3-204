package com.shopchain.controller.admin;

import com.shopchain.entity.Product;
import com.shopchain.repository.CategoryRepository;
import com.shopchain.repository.ProductRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public AdminProductController(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public String list(Model model) {
        List<Product> products = productRepository.findAll();
        model.addAttribute("products", products);
        model.addAttribute("title", "Quản lý sản phẩm");
        model.addAttribute("activePage", "products");
        model.addAttribute("content", "admin/products/list");
        return "admin/layout";
    }

    @GetMapping("/new")
    public String newProduct(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("title", "Thêm sản phẩm mới");
        model.addAttribute("activePage", "products");
        model.addAttribute("content", "admin/products/form");
        return "admin/layout";
    }

    @PostMapping("/save")
    public String saveProduct(@ModelAttribute Product product,
                              @RequestParam Long categoryId,
                              @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                              Model model) {
        if (product.getCampaignStartAt() != null && product.getCampaignEndAt() != null
                && !product.getCampaignEndAt().isAfter(product.getCampaignStartAt())) {
            model.addAttribute("categories", categoryRepository.findAll());
            model.addAttribute("title", "Chỉnh sửa sản phẩm");
            model.addAttribute("activePage", "products");
            model.addAttribute("content", "admin/products/form");
            model.addAttribute("productFormError", "Thời gian kết thúc ưu đãi phải sau thời gian bắt đầu.");
            return "admin/layout";
        }
        if (product.getDiscountPercent() != null && (product.getDiscountPercent() <= 0 || product.getDiscountPercent() >= 100)) {
            model.addAttribute("categories", categoryRepository.findAll());
            model.addAttribute("title", product.getId() == null ? "Thêm sản phẩm mới" : "Chỉnh sửa sản phẩm");
            model.addAttribute("activePage", "products");
            model.addAttribute("content", "admin/products/form");
            model.addAttribute("productFormError", "Mức giảm phải lớn hơn 0% và nhỏ hơn 100%.");
            return "admin/layout";
        }
        if (product.getDiscountPercent() == null) {
            product.setCampaignName(null);
            product.setCampaignAudience("ALL");
            product.setIsFlashSale(false);
            product.setCampaignStartAt(null);
            product.setCampaignEndAt(null);
        } else {
            product.setIsFlashSale(true);
            if (product.getCampaignName() == null || product.getCampaignName().isBlank())
                product.setCampaignName("Ưu đãi Techtra");
            if (!"VIP".equalsIgnoreCase(product.getCampaignAudience())) product.setCampaignAudience("ALL");
        }
        product.setCategory(categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại: " + categoryId)));
        // In a real scenario, you'd upload imageFile to Cloudinary here
        // and set product.setImageUrl(uploadedUrl);

        if (imageFile != null && !imageFile.isEmpty()) {
            // Mocking image upload for now, in production replace with Cloudinary service
            product.setImageUrl("https://via.placeholder.com/300");
        }

        productRepository.save(product);
        return "redirect:/admin/products";
    }

    @GetMapping("/edit/{id}")
    public String editProduct(@PathVariable Long id, Model model) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product Id:" + id));
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("title", "Chỉnh sửa sản phẩm");
        model.addAttribute("activePage", "products");
        model.addAttribute("content", "admin/products/form");
        return "admin/layout";
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productRepository.deleteById(id);
        return "redirect:/admin/products";
    }
}
