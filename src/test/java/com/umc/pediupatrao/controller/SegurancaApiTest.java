package com.umc.pediupatrao.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RF01 - Prova automatizada de que a autorização está no BACKEND:
 * as chamadas vão direto na API (sem tela) e o servidor devolve 401/403.
 */
@WebMvcTest(controllers = PedidoController.class)
@Import(SecurityConfig.class)
class SegurancaApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoPodeCancelar() throws Exception {
        mvc.perform(post("/api/pedidos/abc/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"justificativa\":\"teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoPodeAplicarDesconto() throws Exception {
        mvc.perform(post("/api/pedidos/abc/desconto").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteNaoAdministraUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoVeAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ninguemApagaAuditoria() throws Exception {
        mvc.perform(delete("/api/auditoria").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void postSemTokenCsrfEhBloqueado() throws Exception {
        mvc.perform(post("/api/pedidos/abc/cancelar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"justificativa\":\"teste\"}"))
                .andExpect(status().isForbidden());
    }
}
