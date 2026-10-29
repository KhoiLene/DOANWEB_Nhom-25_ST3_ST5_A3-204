package com.shopchain.controller.admin;

import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import com.shopchain.service.OrderService;
import com.shopchain.service.InvoiceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final InvoiceService invoiceService;

    public AdminOrderController(OrderRepository orderRepository, OrderService orderService, InvoiceService invoiceService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public String list(Model model) {
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        model.addAttribute("title", "Quản lý đơn hàng");
        model.addAttribute("activePage", "orders");
        model.addAttribute("content", "admin/orders/list");
        return "admin/layout";
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid order Id:" + id));
        model.addAttribute("order", order);
        model.addAttribute("allowedStatusTargets", orderService.allowedStatusTargets(order));
        model.addAttribute("title", "Chi tiết đơn hàng #" + id);
        model.addAttribute("activePage", "orders");
        model.addAttribute("content", "admin/orders/detail");
        return "admin/layout";
    }

    @PostMapping("/update-status")
    public String updateStatus(@RequestParam Long orderId, @RequestParam String status) {
        Order order = orderService.updateOrderStatus(orderId, status);
        if ("DELIVERED".equalsIgnoreCase(order.getStatus()) && "PAID".equalsIgnoreCase(order.getPaymentStatus()))
            invoiceService.issueForPaidOrder(orderId);
        return "redirect:/admin/orders";
    }
}
