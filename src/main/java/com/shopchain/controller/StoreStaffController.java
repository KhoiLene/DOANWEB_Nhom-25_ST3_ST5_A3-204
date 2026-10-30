package com.shopchain.controller;

import com.shopchain.repository.*;
import com.shopchain.service.InventoryOperationsService;
import com.shopchain.service.PointOfSaleService;
import com.shopchain.service.InvoiceService;
import com.shopchain.service.OrderService;
import com.shopchain.service.StoreReservationService;
import com.shopchain.service.SystemSettingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff")
/**
 * Controller quản lý màn hình và thao tác của nhân viên chi nhánh:
 * Quản lý tồn kho, xác nhận chuyển kho, bán hàng tại quầy (POS), giữ hàng (Reservations) và in hóa đơn.
 */
public class StoreStaffController {
    private final UserRepository userRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockTransferRepository transferRepository;
    private final InventoryOperationsService inventoryOperationsService;
    private final PointOfSaleService pointOfSaleService;
    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final OrderService orderService;
    private final StoreReservationService reservationService;
    private final SystemSettingService systemSettingService;

    public StoreStaffController(UserRepository userRepository, ProductInventoryRepository inventoryRepository,
                                InventoryMovementRepository movementRepository, StockTransferRepository transferRepository,
                                InventoryOperationsService inventoryOperationsService,
                                PointOfSaleService pointOfSaleService, OrderRepository orderRepository,
                                SalesInvoiceRepository invoiceRepository, InvoiceService invoiceService, OrderService orderService,
                                StoreReservationService reservationService, SystemSettingService systemSettingService) {
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.transferRepository = transferRepository;
        this.inventoryOperationsService = inventoryOperationsService;
        this.pointOfSaleService = pointOfSaleService;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.systemSettingService = systemSettingService;
    }

    /**
     * Màn hình quản lý đơn hàng chi nhánh và tồn kho.
     * Hiển thị danh sách đơn chi nhánh, tồn kho sản phẩm, lịch sử xuất nhập và phiếu chuyển kho đang tới.
     */
    @GetMapping({"/orders", "/inventory"})
    public String inventory(Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) {
            throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        }
        Long storeId = user.getStore().getId();
        model.addAttribute("store", user.getStore());
        var allOrders = orderRepository.findByStoreIdAndFulfillmentWarehouseIsNullOrderByOrderDateDesc(storeId);
        var allowedTransitions = allOrders.stream().collect(java.util.stream.Collectors.toMap(
                com.shopchain.entity.Order::getId, orderService::allowedStatusTargets));
        var orders = allOrders.stream().filter(order -> {
            var targets = allowedTransitions.get(order.getId());
            boolean awaitingTransferCheck = "BANK_TRANSFER".equalsIgnoreCase(order.getPaymentMethod())
                    && "UNPAID".equalsIgnoreCase(order.getPaymentStatus())
                    && "PENDING".equalsIgnoreCase(order.getStatus());
            return awaitingTransferCheck || targets.contains("PROCESSING") || targets.contains("SHIPPING")
                    || targets.contains("READY_FOR_PICKUP") || targets.contains("DELIVERED");
        }).toList();
        var orderHistory = allOrders.stream().filter(order -> allowedTransitions.get(order.getId()).isEmpty()).toList();
        model.addAttribute("orders", orders);
        model.addAttribute("orderHistory", orderHistory);
        model.addAttribute("orderStatusLabels", java.util.Map.of(
                "PENDING", "Chờ xác nhận", "CONFIRMED", "Đã xác nhận thanh toán", "PROCESSING", "Đang chuẩn bị",
                "SHIPPING", "Đang giao", "READY_FOR_PICKUP", "Sẵn sàng nhận tại chi nhánh",
                "DELIVERED", "Đã giao / đã nhận", "COMPLETED", "Đã thanh toán tại quầy",
                "CANCELLED", "Đã hủy", "RETURNED", "Đã hoàn trả"));
        model.addAttribute("pendingTransferOrders", orders.stream()
                .filter(order -> "BANK_TRANSFER".equalsIgnoreCase(order.getPaymentMethod())
                        && "UNPAID".equalsIgnoreCase(order.getPaymentStatus())
                        && "PENDING".equalsIgnoreCase(order.getStatus())).toList());
        model.addAttribute("allowedTransitions", allowedTransitions);
        model.addAttribute("inventory", inventoryRepository.findByStoreId(storeId));
        model.addAttribute("movements", movementRepository.findTop100ByStoreIdOrderByCreatedAtDesc(storeId));
        model.addAttribute("transfers", transferRepository.findByDestinationStoreIdAndStatusOrderByCreatedAtAsc(storeId, "IN_TRANSIT"));
        return "staff/inventory";
    }

    /**
     * Cập nhật trạng thái đơn hàng chi nhánh (VD: Đang xử lý, Đang giao, Đã giao).
     * Tự động phát hành hóa đơn khi đơn hoàn tất và đã thanh toán.
     */
    @PostMapping("/orders/{orderId}/status")
    public String updateOrder(@PathVariable Long orderId, @RequestParam String status, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        var order = orderRepository.findById(orderId).orElseThrow();
        if (user.getStore() == null || !order.getStore().getId().equals(user.getStore().getId()) || order.getFulfillmentWarehouse() != null)
            throw new IllegalArgumentException("Đơn hàng không thuộc chi nhánh được phân công.");
        orderService.updateOrderStatus(orderId, status);
        order = orderRepository.findById(orderId).orElseThrow();
        if ("DELIVERED".equals(status) && "PAID".equals(order.getPaymentStatus())) invoiceService.issueForPaidOrder(order.getId());
        return "redirect:/staff/orders";
    }

    @PostMapping("/orders/{orderId}/confirm-transfer")
    public String confirmOnlineTransfer(@PathVariable Long orderId, Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        var order = orderRepository.findById(orderId).orElseThrow();
        if (user.getStore() == null || order.getStore() == null
                || !order.getStore().getId().equals(user.getStore().getId())
                || order.getFulfillmentWarehouse() != null)
            throw new IllegalArgumentException("Đơn hàng không thuộc chi nhánh được phân công.");
        try {
            var paidOrder = orderService.confirmBankTransferPayment(orderId);
            if ("PICKUP".equalsIgnoreCase(paidOrder.getFulfillmentType()))
                invoiceService.issueForPaidOrder(orderId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận chuyển khoản. Đơn hàng chuyển sang chờ xử lý.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/staff/orders";
    }

    /**
     * Xác nhận nhập hàng từ phiếu chuyển kho (StockTransfer) vào kho chi nhánh.
     */
    @PostMapping("/transfers/{transferId}/receive")
    public String receiveTransfer(@PathVariable Long transferId, Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) {
            throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        }
        try {
            inventoryOperationsService.receiveTransfer(transferId, user.getStore().getId(), authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận nhận hàng. Tồn chi nhánh đã được cập nhật.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/staff/inventory";
    }

    /**
     * Màn hình bán hàng tại quầy (POS).
     * Cung cấp danh mục tồn kho chi nhánh và cấu hình tài khoản ngân hàng (VietQR).
     */
    @GetMapping("/pos")
    public String pos(Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        model.addAttribute("store", user.getStore());
        model.addAttribute("inventory", inventoryRepository.findByStoreId(user.getStore().getId()));
        // Lấy thông tin tài khoản ngân hàng từ cài đặt hệ thống để phục vụ tạo mã VietQR
        String qrBankCode = java.util.Objects.toString(systemSettingService.getSetting("payment.qrBankCode", ""), "").trim();
        String qrAccountNumber = java.util.Objects.toString(systemSettingService.getSetting("payment.qrAccountNumber", ""), "").trim();
        String qrAccountName = java.util.Objects.toString(systemSettingService.getSetting("payment.qrAccountName", ""), "").trim();
        boolean bankQrConfigured = qrBankCode.matches("[A-Za-z0-9]{3,12}")
                && qrAccountNumber.matches("[A-Za-z0-9]{4,19}")
                && qrAccountName.length() <= 100;
        model.addAttribute("bankQrConfigured", bankQrConfigured);
        model.addAttribute("qrBankCode", qrBankCode);
        model.addAttribute("qrAccountNumber", qrAccountNumber);
        model.addAttribute("qrAccountName", qrAccountName);
        return "staff/pos";
    }

    /**
     * Màn hình quản lý phiếu giữ hàng tại chi nhánh.
     * Cung cấp danh sách các phiếu giữ HELD còn hiệu lực và thông tin mã QR chuyển khoản.
     */
    @GetMapping("/reservations")
    public String reservations(Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        model.addAttribute("store", user.getStore());
        model.addAttribute("reservations", reservationService.staffReservations(user.getStore().getId()));
        // Lấy thông tin tài khoản nhận tiền để tạo mã QR VietQR tự động khi khách chọn thanh toán Chuyển khoản
        String qrBankCode = java.util.Objects.toString(systemSettingService.getSetting("payment.qrBankCode", ""), "").trim();
        String qrAccountNumber = java.util.Objects.toString(systemSettingService.getSetting("payment.qrAccountNumber", ""), "").trim();
        String qrAccountName = java.util.Objects.toString(systemSettingService.getSetting("payment.qrAccountName", ""), "").trim();
        boolean bankQrConfigured = qrBankCode.matches("[A-Za-z0-9]{3,12}")
                && qrAccountNumber.matches("[A-Za-z0-9]{4,19}")
                && qrAccountName.length() <= 100;
        model.addAttribute("bankQrConfigured", bankQrConfigured);
        model.addAttribute("qrBankCode", qrBankCode);
        model.addAttribute("qrAccountNumber", qrAccountNumber);
        model.addAttribute("qrAccountName", qrAccountName);
        return "staff/reservations";
    }

    /**
     * Xác nhận giao hàng cho khách từ phiếu giữ hàng.
     * Tiêu thụ lượng giữ, trừ tồn kho, tạo giao dịch bán tại quầy và chuyển tiếp đến biên nhận hóa đơn.
     */
    @PostMapping("/reservations/{reservationId}/pickup")
    public String pickupReservation(@PathVariable Long reservationId,
                                    @RequestParam(defaultValue = "CASH") String paymentMethod,
                                    @RequestParam(required = false) String paymentReference,
                                    @RequestParam(defaultValue = "false") boolean bankTransferConfirmed,
                                    Authentication authentication, RedirectAttributes redirectAttributes) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        try {
            var result = reservationService.pickup(reservationId, user.getStore().getId(), authentication.getName(),
                    paymentMethod, paymentReference, bankTransferConfirmed);
            if (result.invoiceId() != null) return "redirect:/staff/invoices/" + result.invoiceId();
            redirectAttributes.addFlashAttribute("errorMessage", "Phiếu đã hết hạn; đã giải phóng lượng hàng giữ và chưa tạo giao dịch.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/staff/reservations";
    }

    /**
     * Hủy phiếu giữ hàng theo yêu cầu nhân viên và hoàn trả tồn kho chi nhánh.
     */
    @PostMapping("/reservations/{reservationId}/cancel")
    public String cancelReservation(@PathVariable Long reservationId, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        if (user.getStore() == null) throw new IllegalStateException("Tài khoản nhân viên cửa hàng chưa được gán chi nhánh.");
        reservationService.cancelForStaff(reservationId, user.getStore().getId());
        return "redirect:/staff/reservations";
    }

    /**
     * Xem chi tiết biên nhận hóa đơn bán lẻ đã xuất tại chi nhánh.
     */
    @GetMapping("/invoices/{invoiceId}")
    public String invoice(@PathVariable Long invoiceId, Model model, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        var invoice = pointOfSaleService.getInvoice(invoiceId);
        if (user.getStore() == null || !invoice.getOrder().getStore().getId().equals(user.getStore().getId()))
            throw new IllegalArgumentException("Biên nhận không thuộc chi nhánh được phân công.");
        model.addAttribute("invoice", invoice);
        return "staff/invoice";
    }

    @GetMapping("/orders/{orderId}/receipt")
    public String orderReceipt(@PathVariable Long orderId, Authentication authentication) {
        var user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        var order = orderRepository.findById(orderId).orElseThrow(
                () -> new IllegalArgumentException("Không tìm thấy đơn hàng."));
        if (user.getStore() == null || order.getStore() == null
                || !order.getStore().getId().equals(user.getStore().getId())
                || order.getFulfillmentWarehouse() != null)
            throw new IllegalArgumentException("Đơn hàng không thuộc chi nhánh được phân công.");
        var invoice = invoiceRepository.findByOrderId(orderId).orElseThrow(
                () -> new IllegalArgumentException("Đơn này chưa có biên nhận."));
        return "redirect:/staff/invoices/" + invoice.getId();
    }

    /**
     * API thực hiện bán hàng tại quầy (POS).
     * Hỗ trợ bán cho khách lẻ (không đổi điểm) hoặc thành viên (được đổi điểm tích lũy với tỷ lệ 1 điểm = 100 đ).
     */
    @PostMapping("/sales")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> sell(@RequestBody(required = false) SaleRequest request,
                                                           Authentication authentication) {
        if (request == null || request.lines() == null || request.lines().isEmpty() || request.lines().size() > 100
                || request.lines().stream().anyMatch(line -> line == null || line.productId() == null || line.quantity() == null)) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "Giỏ hàng tại quầy chưa hợp lệ."));
        }
        try {
            var invoice = pointOfSaleService.sell(authentication.getName(), request.customerUserId(),
                    request.customerName(), request.customerPhone(), request.paymentMethod(), request.paymentReference(),
                    request.bankTransferConfirmed(), request.lines().stream()
                            .map(line -> new PointOfSaleService.SaleLine(line.productId(), line.quantity())).toList(),
                    request.redeemPoints());
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                    "invoiceUrl", "/staff/invoices/" + invoice.getId(), "invoiceNumber", invoice.getInvoiceNumber()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", exception.getMessage()));
        } catch (RuntimeException exception) {
            return org.springframework.http.ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", "Chưa thể hoàn tất giao dịch. Vui lòng thử lại."));
        }
    }

    public record SaleRequest(Long customerUserId, String customerName, String customerPhone, String paymentMethod,
                              String paymentReference, boolean bankTransferConfirmed,
                              java.util.List<SaleRequestLine> lines, Integer redeemPoints) {}
    public record SaleRequestLine(Long productId, Integer quantity) {}
}
