package com.shopchain.controller.admin;

import com.shopchain.entity.Product;
import com.shopchain.repository.CategoryRepository;
import com.shopchain.repository.ProductRepository;
import com.shopchain.service.ProductService;
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
    private final ProductService productService;

    public AdminProductController(ProductRepository productRepository, CategoryRepository categoryRepository,
                                  ProductService productService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productService = productService;
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

        // Bind only fields represented by the admin form; preserve SKU and other server-owned fields.
        if (product.getId() != null) {
            Product existing = productRepository.findById(product.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm cần chỉnh sửa."));
            existing.setName(product.getName());
            existing.setDescription(product.getDescription());
            existing.setPrice(product.getPrice());
            existing.setIsActive(product.getIsActive());
            existing.setIsFeatured(product.getIsFeatured());
            existing.setIsFlashSale(product.getIsFlashSale());
            existing.setDiscountPercent(product.getDiscountPercent());
            existing.setCampaignName(product.getCampaignName());
            existing.setCampaignAudience(product.getCampaignAudience());
            existing.setCampaignStartAt(product.getCampaignStartAt());
            existing.setCampaignEndAt(product.getCampaignEndAt());
            existing.setBrandName(product.getBrandName());
            existing.setSuitableFor(product.getSuitableFor());
            existing.setUsageInstructions(product.getUsageInstructions());
            existing.setIngredients(product.getIngredients());
            existing.setCautions(product.getCautions());
            existing.setCategory(product.getCategory());
            product = existing;
        }
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                product.setImageUrl(productService.uploadImage(imageFile));
            } catch (IllegalArgumentException | java.io.IOException exception) {
                model.addAttribute("product", product);
                model.addAttribute("categories", categoryRepository.findAll());
                model.addAttribute("title", product.getId() == null ? "Thêm sản phẩm mới" : "Chỉnh sửa sản phẩm");
                model.addAttribute("activePage", "products");
                model.addAttribute("content", "admin/products/form");
                model.addAttribute("productFormError", exception.getMessage());
                return "admin/layout";
            } catch (RuntimeException exception) {
                model.addAttribute("product", product);
                model.addAttribute("categories", categoryRepository.findAll());
                model.addAttribute("title", product.getId() == null ? "Thêm sản phẩm mới" : "Chỉnh sửa sản phẩm");
                model.addAttribute("activePage", "products");
                model.addAttribute("content", "admin/products/form");
                model.addAttribute("productFormError", "Không thể tải ảnh lên lúc này. Vui lòng thử lại.");
                return "admin/layout";
            }
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

    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        if (productRepository.existsById(id)) productRepository.deleteById(id);
        return "redirect:/admin/products";
    }
}
