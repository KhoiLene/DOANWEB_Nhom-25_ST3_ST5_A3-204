package com.shopchain.controller;

import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StoreLocatorController {
    @Value("${maps.google.browser-key:}")
    private String googleMapsBrowserKey;

    @GetMapping("/stores")
    public String stores(Model model) {
        model.addAttribute("googleMapsBrowserKey", googleMapsBrowserKey);
        return "stores";
    }
}
