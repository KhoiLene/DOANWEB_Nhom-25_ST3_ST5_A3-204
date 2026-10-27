package com.shopchain.controller;

import com.shopchain.repository.*;
import com.shopchain.service.InventoryOperationsService;
import com.shopchain.service.PointOfSaleService;
import com.shopchain.service.InvoiceService;
import com.shopchain.service.OrderService;
import com.shopchain.service.StoreReservationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@Controller
@RequestMapping("/staff")
public class StoreStaffController {
    private final UserRepository userRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockTransferRepository transferRepository;
    private final InventoryOperationsService inventoryOperationsService;
    private final PointOfSaleService pointOfSaleService;
    private final OrderRepository orderRepository;
    private final InvoiceService invoiceService;
    private final OrderService orderService;
    private final StoreReservationService reservationService;

    public StoreStaffController(UserRepository userRepository, ProductInventoryRepository inventoryRepository,
                                InventoryMovementRepository movementRepository, StockTransferRepository transferRepository,
                                InventoryOperationsService inventoryOperationsService,
                                PointOfSaleService pointOfSaleService, OrderRepository orderRepository,
                                InvoiceService invoiceService, OrderService orderService,
                                StoreReservationService reservationService) {
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.transferRepository = transferRepository;
        this.inventoryOperationsService = inventoryOperationsService;
        this.pointOfSaleService = pointOfSaleService;
        this.orderRepository = orderRepository;
        this.invoiceService = invoiceService;
        this.orderService = orderService;
        this.reservationService = reservationService;
    }

    @GetMapping({"/orders", "/inventory"})
    public String inventory(Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) {
            throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        }
        Long storeId = user.getStore().getId();
        model.addAttribute("store", user.getStore());
        model.addAttribute("orders", orderRepository.findByStoreIdAndFulfillmentWarehouseIsNullOrderByOrderDateDesc(storeId));
        model.addAttribute("inventory", inventoryRepository.findByStoreId(storeId));
        model.addAttribute("movements", movementRepository.findTop100ByStoreIdOrderByCreatedAtDesc(storeId));
        model.addAttribute("transfers", transferRepository.findByDestinationStoreIdAndStatusOrderByCreatedAtAsc(storeId, "IN_TRANSIT"));
        return "staff/inventory";
    }

    @PostMapping("/orders/{orderId}/status")
    public String updateOrder(@PathVariable Long orderId, @RequestParam String status, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        var order = orderRepository.findById(orderId).orElseThrow();
        if (user.getStore() == null || !order.getStore().getId().equals(user.getStore().getId()) || order.getFulfillmentWarehouse() != null)
            throw new IllegalArgumentException("Đơn hàng không thuộc chi nhánh được phân công.");
        if (!("PROCESSING".equals(status) && "PENDING".equals(order.getStatus())
                || "SHIPPING".equals(status) && "PROCESSING".equals(order.getStatus())
                || "DELIVERED".equals(status) && "SHIPPING".equals(order.getStatus())))
            throw new IllegalArgumentException("Trạng thái đơn hàng không hợp lệ.");
        orderService.updateOrderStatus(orderId, status);
        order = orderRepository.findById(orderId).orElseThrow();
        if ("DELIVERED".equals(status) && "PAID".equals(order.getPaymentStatus())) invoiceService.issueForPaidOrder(order.getId());
        return "redirect:/staff/orders";
    }

    @PostMapping("/transfers/{transferId}/receive")
    public String receiveTransfer(@PathVariable Long transferId, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) {
            throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        }
        inventoryOperationsService.receiveTransfer(transferId, user.getStore().getId(), authentication.getName());
        return "redirect:/staff/inventory";
    }

    @GetMapping("/pos")
    public String pos(Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        model.addAttribute("store", user.getStore());
        model.addAttribute("inventory", inventoryRepository.findByStoreId(user.getStore().getId()));
        return "staff/pos";
    }

    @GetMapping("/reservations")
    public String reservations(Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        model.addAttribute("store", user.getStore());
        model.addAttribute("reservations", reservationService.staffReservations(user.getStore().getId()));
        return "staff/reservations";
    }

    @PostMapping("/reservations/{reservationId}/pickup")
    public String pickupReservation(@PathVariable Long reservationId, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        reservationService.pickup(reservationId, user.getStore().getId());
        return "redirect:/staff/reservations";
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public String cancelReservation(@PathVariable Long reservationId, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        reservationService.cancelForStaff(reservationId, user.getStore().getId());
        return "redirect:/staff/reservations";
    }

    @GetMapping("/invoices/{invoiceId}")
    public String invoice(@PathVariable Long invoiceId, Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        var invoice = pointOfSaleService.getInvoice(invoiceId);
        if (user.getStore() == null || !invoice.getOrder().getStore().getId().equals(user.getStore().getId()))
            throw new IllegalArgumentException("Biên nhận không thuộc chi nhánh được phân công.");
        model.addAttribute("invoice", invoice);
        return "staff/invoice";
    }

    @PostMapping("/sales")
    @ResponseBody
    public java.util.Map<String, Object> sell(@RequestBody SaleRequest request, Authentication authentication) {
        var invoice = pointOfSaleService.sell(authentication.getName(), request.customerName(), request.customerPhone(),
                request.paymentMethod(), request.lines().stream()
                        .map(line -> new PointOfSaleService.SaleLine(line.productId(), line.quantity())).toList());
        return java.util.Map.of("invoiceUrl", "/staff/invoices/" + invoice.getId(), "invoiceNumber", invoice.getInvoiceNumber());
    }

    public record SaleRequest(String customerName, String customerPhone, String paymentMethod, java.util.List<SaleRequestLine> lines) {}
    public record SaleRequestLine(Long productId, Integer quantity) {}
}
