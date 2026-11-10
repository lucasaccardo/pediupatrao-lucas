package com.umc.pediupatrao.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela do Dashboard Administrativo (ADMIN e GERENTE).
 * A página só monta a estrutura; os dados vêm da API /api/dashboard via JavaScript (RF06).
 */
@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard/painel :: content");
        return "layout";
    }
}
