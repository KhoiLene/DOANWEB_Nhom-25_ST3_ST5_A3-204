package com.shopchain.controller.admin;

import com.shopchain.entity.Order;
import com.shopchain.repository.OrderRepository;
import com.shopchain.service.OrderService;
import com.shopchain.service.InvoiceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String paymentStatus,
                       @RequestParam(required = false) String fulfillmentType,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                       @RequestParam(defaultValue = "newest") String sort,
                       Model model) {
        boolean oldestFirst = "oldest".equalsIgnoreCase(sort);
        Sort.Direction direction = oldestFirst ? Sort.Direction.ASC : Sort.Direction.DESC;
        List<Order> allOrders = orderRepository.findAll(Sort.by(direction, "orderDate").and(Sort.by(direction, "id")));
        String statusFilter = normalizedFilter(status);
        String paymentFilter = normalizedFilter(paymentStatus);
        String fulfillmentFilter = normalizedFilter(fulfillmentType);
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        boolean validDateRange = fromDate == null || toDate == null || !fromDate.isAfter(toDate);
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime until = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        List<Order> orders = allOrders.stream()
                .filter(order -> validDateRange)
                .filter(order -> statusFilter == null || statusFilter.equalsIgnoreCase(order.getStatus()))
                .filter(order -> paymentFilter == null || paymentFilter.equalsIgnoreCase(order.getPaymentStatus()))
                .filter(order -> fulfillmentFilter == null || fulfillmentFilter.equalsIgnoreCase(order.getFulfillmentType()))
                .filter(order -> from == null || (order.getOrderDate() != null && !order.getOrderDate().isBefore(from)))
                .filter(order -> until == null || (order.getOrderDate() != null && order.getOrderDate().isBefore(until)))
                .filter(order -> matchesKeyword(order, search))
                .toList();
        model.addAttribute("orders", orders);
        model.addAttribute("statusFilter", status == null ? "" : status);
        model.addAttribute("paymentStatusFilter", paymentStatus == null ? "" : paymentStatus);
        model.addAttribute("fulfillmentTypeFilter", fulfillmentType == null ? "" : fulfillmentType);
        model.addAttribute("keywordFilter", keyword == null ? "" : keyword);
        model.addAttribute("fromDateFilter", fromDate);
        model.addAttribute("toDateFilter", toDate);
        model.addAttribute("sortOrder", oldestFirst ? "oldest" : "newest");
        model.addAttribute("invalidDateRange", !validDateRange);
        model.addAttribute("statusOptions", List.of(
                new FilterOption("PENDING", "Chờ xác nhận"), new FilterOption("CONFIRMED", "Đã xác nhận"),
                new FilterOption("PROCESSING", "Đang chuẩn bị"), new FilterOption("SHIPPING", "Đang giao"),
                new FilterOption("READY_FOR_PICKUP", "Sẵn sàng nhận"), new FilterOption("DELIVERED", "Đã giao"),
                new FilterOption("COMPLETED", "Hoàn tất tại quầy"), new FilterOption("CANCELLED", "Đã hủy"),
                new FilterOption("RETURNED", "Đã hoàn trả")));
        model.addAttribute("title", "Quản lý đơn hàng");
        model.addAttribute("activePage", "orders");
        model.addAttribute("content", "admin/orders/list");
        return "admin/layout";
    }

    private String normalizedFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean matchesKeyword(Order order, String keyword) {
        if (keyword.isBlank()) return true;
        return String.valueOf(order.getId()).contains(keyword)
                || contains(order.getCustomerName(), keyword)
                || contains(order.getCustomerPhone(), keyword)
                || (order.getUser() != null && (contains(order.getUser().getUsername(), keyword)
                    || contains(order.getUser().getEmail(), keyword)));
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    public record FilterOption(String value, String label) { }

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
    public String updateStatus(@RequestParam Long orderId, @RequestParam String status,
                               RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.updateOrderStatus(orderId, status);
            if ("DELIVERED".equalsIgnoreCase(order.getStatus()) && "PAID".equalsIgnoreCase(order.getPaymentStatus()))
                invoiceService.issueForPaidOrder(orderId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái đơn hàng.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/orders/detail/" + orderId;
    }

    @PostMapping("/{orderId}/confirm-transfer")
    public String confirmBankTransfer(@PathVariable Long orderId, RedirectAttributes redirectAttributes) {
        try {
            Order paidOrder = orderService.confirmBankTransferPayment(orderId);
            if ("PICKUP".equalsIgnoreCase(paidOrder.getFulfillmentType()))
                invoiceService.issueForPaidOrder(orderId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận khoản chuyển VietQR.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/orders/detail/" + orderId;
    }
}
