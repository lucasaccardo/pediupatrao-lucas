package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RF05 - Proteção de dados na auditoria. */
class AuditoriaServiceTest {

    @Test
    void senhaNuncaEhGravada() {
        assertEquals("[PROTEGIDO]", AuditoriaService.proteger("senha", "minhaSenha123"));
        assertEquals("[PROTEGIDO]", AuditoriaService.proteger("password", "abc"));
    }

    @Test
    void telefoneEhMascarado() {
        assertEquals("(11) *****-4321", AuditoriaService.proteger("telefone", "(11) 98765-4321"));
    }

    @Test
    void enderecoNaoEhMascarado() {
        assertEquals("Av. Brasil", AuditoriaService.proteger("logradouro", "Av. Brasil"));
    }

    @Test
    void compararRegistraSomenteCamposDiferentes() {
        List<AlteracaoCampo> lista = new ArrayList<>();
        AuditoriaService.comparar(lista, "logradouro", "Rua das Flores", "Av. Brasil");
        AuditoriaService.comparar(lista, "bairro", "Centro", "Centro");
        AuditoriaService.comparar(lista, "complemento", null, "");
        assertEquals(1, lista.size());
        assertEquals("logradouro", lista.get(0).getCampo());
        assertEquals("Rua das Flores", lista.get(0).getValorAnterior());
        assertEquals("Av. Brasil", lista.get(0).getValorNovo());
        assertTrue(lista.stream().noneMatch(a -> a.getCampo().equals("bairro")));
    }
}
