package com.umc.pediupatrao.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Página de apoio para a DEMONSTRAÇÃO: dispara chamadas diretas às APIs
 * (sem passar pelos botões das telas) e mostra o código HTTP devolvido.
 * Serve para provar que as restrições estão no backend (ex.: 403 Forbidden).
 */
@Controller
public class SegurancaController {

    @GetMapping("/seguranca/teste")
    public String teste(Model model) {
        model.addAttribute("content", "seguranca/teste :: content");
        return "layout";
    }
}
