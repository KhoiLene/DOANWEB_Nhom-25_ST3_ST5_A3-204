package com.shopchain.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight liveness endpoint for hosting platform health probes. */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public String health() {
        return "ok";
    }
}
