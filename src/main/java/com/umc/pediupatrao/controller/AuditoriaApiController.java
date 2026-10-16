package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF05 - API de consulta da auditoria (somente GET, ADMIN e GERENTE).
 * Qualquer POST/PUT/DELETE em /api/auditoria é negado no SecurityConfig.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaApiController {

    private final AuditoriaService auditoriaService;

    public AuditoriaApiController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<RegistroAuditoria> listar(@RequestParam(required = false) String entidade,
                                          @RequestParam(required = false) String operacao,
                                          @RequestParam(required = false) String usuario) {
        return auditoriaService.consultar(entidade, operacao, usuario);
    }
}
