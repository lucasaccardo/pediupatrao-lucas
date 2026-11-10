package com.umc.pediupatrao.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.IndicadoresDTO;
import com.umc.pediupatrao.dto.dashboard.RespostaDashboard;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RF06 - Testes da API do dashboard pela camada HTTP (segurança, formato e erros).
 */
@WebMvcTest(controllers = {DashboardApiController.class, DashboardController.class})
@Import(SecurityConfig.class)
class DashboardApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/dashboard/indicadores")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/indicadores")).andExpect(status().isForbidden());
        mvc.perform(get("/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteRecebeIndicadoresNoEnvelopePadrao() throws Exception {
        FiltroDashboard f = FiltroDashboard.de("2026-11-01", "2026-11-10", null, null, LocalDate.of(2026, 11, 10));
        IndicadoresDTO dto = new IndicadoresDTO(4, 3, 1, 1, 2, new BigDecimal("163.30"), new BigDecimal("54.43"),
                new BigDecimal("5.70"), 5, new BigDecimal("25.0"), new BigDecimal("35.0"), null);
        when(dashboardService.indicadores(any())).thenReturn(RespostaDashboard.de(f, dto));

        mvc.perform(get("/api/dashboard/indicadores").param("inicio", "2026-11-01").param("fim", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.inicio").value("2026-11-01"))
                .andExpect(jsonPath("$.filtros.dias").value(10))
                .andExpect(jsonPath("$.dados.pedidosValidos").value(3))
                .andExpect(jsonPath("$.dados.faturamento").value(163.30))
                .andExpect(jsonPath("$.geradoEm").exists());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dataInvalidaRetorna400ComOCampo() throws Exception {
        mvc.perform(get("/api/dashboard/vendas").param("inicio", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("inicio"))
                .andExpect(jsonPath("$.mensagem").value(containsString("AAAA-MM-DD")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void limiteNaoNumericoRetorna400() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("limite", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("limite"));
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void dashboardEhSomenteLeitura() throws Exception {
        mvc.perform(post("/api/dashboard/indicadores").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void paginaDoDashboardAbreParaOGerente() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard Administrativo")));
    }
}
