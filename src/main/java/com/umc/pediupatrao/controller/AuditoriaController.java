package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.TipoOperacaoAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * RF05 - Tela de consulta da auditoria (somente leitura, ADMIN e GERENTE).
 * Não existem rotas de edição ou exclusão.
 */
@Controller
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String consultar(@RequestParam(required = false) String entidade,
                            @RequestParam(required = false) String operacao,
                            @RequestParam(required = false) String usuario,
                            Model model) {
        model.addAttribute("registros", auditoriaService.consultar(entidade, operacao, usuario));
        model.addAttribute("operacoes", TipoOperacaoAuditoria.values());
        model.addAttribute("filtroEntidade", entidade);
        model.addAttribute("filtroOperacao", operacao);
        model.addAttribute("filtroUsuario", usuario);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
