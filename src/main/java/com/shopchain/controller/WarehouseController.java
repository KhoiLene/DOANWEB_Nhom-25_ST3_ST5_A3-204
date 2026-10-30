package com.shopchain.controller;

import com.shopchain.repository.*;
import com.shopchain.service.InventoryOperationsService;
import com.shopchain.service.InvoiceService;
import com.shopchain.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/warehouse")
public class WarehouseController {
    private final UserRepository userRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockTransferRepository transferRepository;
    private final InventoryOperationsService inventoryOperationsService;
    private final OrderRepository orderRepository;
    private final InvoiceService invoiceService;
    private final OrderService orderService;

    public WarehouseController(UserRepository userRepository, ProductInventoryRepository inventoryRepository,
                               ProductRepository productRepository, StoreRepository storeRepository,
                               InventoryMovementRepository movementRepository, StockTransferRepository transferRepository,
                               InventoryOperationsService inventoryOperationsService, OrderRepository orderRepository,
                               InvoiceService invoiceService, OrderService orderService) {
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
        this.movementRepository = movementRepository;
        this.transferRepository = transferRepository;
        this.inventoryOperationsService = inventoryOperationsService;
        this.orderRepository = orderRepository;
        this.invoiceService = invoiceService;
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public String orders(Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getWarehouse() == null) throw new IllegalStateException("Tài khoản nhân viên kho chưa được gán kho.");
        model.addAttribute("warehouse", user.getWarehouse());
        var orders = orderRepository.findByFulfillmentWarehouseIdAndStatusInOrderByOrderDateAsc(
                user.getWarehouse().getId(), java.util.List.of("PENDING", "CONFIRMED", "PROCESSING", "SHIPPING"));
        model.addAttribute("orders", orders);
        model.addAttribute("orderStatusLabels", java.util.Map.of(
                "PENDING", "Chờ xác nhận", "CONFIRMED", "Đã xác nhận thanh toán", "PROCESSING", "Đang chuẩn bị",
                "SHIPPING", "Đang giao", "READY_FOR_PICKUP", "Sẵn sàng nhận tại chi nhánh",
                "DELIVERED", "Đã giao / đã nhận", "CANCELLED", "Đã hủy", "RETURNED", "Đã hoàn trả"));
        model.addAttribute("pendingTransferOrders", orders.stream()
                .filter(order -> "BANK_TRANSFER".equalsIgnoreCase(order.getPaymentMethod())
                        && "UNPAID".equalsIgnoreCase(order.getPaymentStatus())
                        && "PENDING".equalsIgnoreCase(order.getStatus())).toList());
        model.addAttribute("allowedTransitions", orders.stream().collect(java.util.stream.Collectors.toMap(
                com.shopchain.entity.Order::getId, orderService::allowedStatusTargets)));
        return "warehouse/orders";
    }

    @PostMapping("/orders/{orderId}/status")
    public String updateOrder(@PathVariable Long orderId, @RequestParam String status, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        var order = orderRepository.findById(orderId).orElseThrow();
        if (user.getWarehouse() == null || order.getFulfillmentWarehouse() == null
                || !order.getFulfillmentWarehouse().getId().equals(user.getWarehouse().getId()))
            throw new IllegalArgumentException("Đơn hàng không thuộc kho được phân công.");
        orderService.updateOrderStatus(orderId, status);
        order = orderRepository.findById(orderId).orElseThrow();
        if ("DELIVERED".equals(status) && "PAID".equals(order.getPaymentStatus())) invoiceService.issueForPaidOrder(order.getId());
        return "redirect:/warehouse/orders";
    }

    @PostMapping("/orders/{orderId}/confirm-transfer")
    public String confirmOnlineTransfer(@PathVariable Long orderId, Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        var order = orderRepository.findById(orderId).orElseThrow();
        if (user.getWarehouse() == null || order.getFulfillmentWarehouse() == null
                || !order.getFulfillmentWarehouse().getId().equals(user.getWarehouse().getId()))
            throw new IllegalArgumentException("Đơn hàng không thuộc kho được phân công.");
        try {
            orderService.confirmBankTransferPayment(orderId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận chuyển khoản. Đơn hàng chuyển sang chờ xử lý.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/warehouse/orders";
    }

    @GetMapping("/inventory")
    public String inventory(Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getWarehouse() == null) {
            throw new IllegalStateException("Tài khoản nhân viên kho chưa được gán kho.");
        }
        Long warehouseId = user.getWarehouse().getId();
        model.addAttribute("warehouse", user.getWarehouse());
        model.addAttribute("inventory", inventoryRepository.findByWarehouseId(warehouseId));
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("stores", storeRepository.findAll());
        model.addAttribute("movements", movementRepository.findTop100ByWarehouseIdOrderByCreatedAtDesc(warehouseId));
        model.addAttribute("transfers", transferRepository.findBySourceWarehouseIdOrderByCreatedAtDesc(warehouseId));
        return "warehouse/inventory";
    }

    @PostMapping("/receive")
    public String receive(@RequestParam Long productId, @RequestParam Integer quantity,
                          @RequestParam(required = false) String batchCode,
                          @RequestParam(required = false) LocalDate expirationDate,
                          @RequestParam(required = false) String note, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getWarehouse() == null) {
            throw new IllegalStateException("Tài khoản nhân viên kho chưa được gán kho.");
        }
        try {
            inventoryOperationsService.receiveIntoWarehouse(productId, user.getWarehouse().getId(),
                    quantity, batchCode, expirationDate, note, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật số lượng nhập kho.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/warehouse/inventory";
    }

    @PostMapping("/transfer")
    public String transfer(@RequestParam Long productId, @RequestParam Long storeId,
                           @RequestParam Integer quantity, @RequestParam(required = false) String note,
                           Authentication authentication, RedirectAttributes redirectAttributes) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getWarehouse() == null) {
            throw new IllegalStateException("Tài khoản nhân viên kho chưa được gán kho.");
        }
        try {
            inventoryOperationsService.transferToStore(productId, user.getWarehouse().getId(), storeId,
                    quantity, note, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo phiếu điều chuyển. Tồn kho đã trừ khỏi kho; chi nhánh cần xác nhận nhận hàng để cộng vào tồn bán được.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/warehouse/inventory";
    }
}
