package com.shopchain.controller;

import com.shopchain.service.StoreReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/customer/reservations")
public class CustomerReservationController {
    private final StoreReservationService reservationService;

    public CustomerReservationController(StoreReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public java.util.List<StoreReservationService.ReservationView> list(Authentication authentication) {
        return reservationService.customerReservations(authentication.getName());
    }

    @GetMapping("/pickup-orders")
    public java.util.List<StoreReservationService.PickupOrderView> pickupOrders(Authentication authentication) {
        return reservationService.customerPickupOrders(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<?> reserve(@RequestBody StoreReservationService.CreateReservationRequest request,
                                     Authentication authentication) {
        try {
            return ResponseEntity.ok(reservationService.reserve(authentication.getName(), request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, Authentication authentication) {
        try {
            return ResponseEntity.ok(reservationService.cancelForCustomer(id, authentication.getName()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
