package com.shopchain.controller.admin;

import com.shopchain.repository.SalesInvoiceRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/invoices")
public class AdminInvoiceController {
    private final SalesInvoiceRepository invoiceRepository;

    public AdminInvoiceController(SalesInvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("invoices", invoiceRepository.findAll());
        model.addAttribute("title", "Biên nhận bán hàng");
        model.addAttribute("activePage", "invoices");
        model.addAttribute("content", "admin/invoices/list");
        return "admin/layout";
    }
}
