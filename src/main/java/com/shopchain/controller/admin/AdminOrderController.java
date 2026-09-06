package com.shopchain.controller.admin;

import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderRepository orderRepository;

    public AdminOrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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
        model.addAttribute("title", "Chi tiết đơn hàng #" + id);
        model.addAttribute("activePage", "orders");
        model.addAttribute("content", "admin/orders/detail");
        return "admin/layout";
    }

    @PostMapping("/update-status")
    public String updateStatus(@RequestParam Long orderId, @RequestParam String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // In a real system, you would have an OrderStatus enum
        // order.setStatus(status);

        orderRepository.save(order);
        return "redirect:/admin/orders";
    }
}
