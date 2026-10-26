package com.shopchain.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StoreLocatorController {
    @GetMapping("/stores")
    public String stores() { return "stores"; }
}
