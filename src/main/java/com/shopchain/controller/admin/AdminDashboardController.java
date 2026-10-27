package com.shopchain.controller.admin;

import com.shopchain.repository.ProductRepository;
import com.shopchain.repository.OrderRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.service.InventoryOperationsService;
import com.shopchain.service.OperationsControlTowerService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OperationsControlTowerService controlTowerService;
    private final InventoryOperationsService inventoryOperationsService;

    public AdminDashboardController(ProductRepository productRepository, OrderRepository orderRepository,
                                    UserRepository userRepository, OperationsControlTowerService controlTowerService,
                                    InventoryOperationsService inventoryOperationsService) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.controlTowerService = controlTowerService;
        this.inventoryOperationsService = inventoryOperationsService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("title", "Trung tâm vận hành");
        model.addAttribute("activePage", "dashboard");

        model.addAttribute("totalProducts", productRepository.count());
        model.addAttribute("totalOrders", orderRepository.count());
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("operations", controlTowerService.overview());

        model.addAttribute("content", "admin/dashboard");
        return "admin/layout";
    }

    @PostMapping("/operations/transfers")
    public String createTransfer(@RequestParam Long productId, @RequestParam Long sourceWarehouseId,
                                 @RequestParam Long destinationStoreId, @RequestParam Integer quantity,
                                 Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            inventoryOperationsService.transferToStore(productId, sourceWarehouseId, destinationStoreId,
                    quantity, "Điều chuyển được duyệt từ Bảng điều hành toàn chuỗi", authentication.getName());
            redirectAttributes.addFlashAttribute("operationsMessage", "Đã tạo phiếu điều chuyển. Chi nhánh sẽ xác nhận khi nhận hàng.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("operationsError", exception.getMessage());
        }
        return "redirect:/admin/dashboard#stock-alerts";
    }
}
