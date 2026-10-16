package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** RF03 - Testes da máquina de estados do pedido. */
class StatusPedidoTest {

    @Test
    void fluxoNormalDeveSerPermitido() {
        assertTrue(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.EM_PREPARACAO));
        assertTrue(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.PRONTO));
        assertTrue(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.SAIU_PARA_ENTREGA));
        assertTrue(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RETIRADO));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeAvancarPara(StatusPedido.FINALIZADO));
        assertTrue(StatusPedido.RETIRADO.podeAvancarPara(StatusPedido.FINALIZADO));
    }

    @Test
    void naoPodeFinalizarPedidoEmPreparacao() {
        assertFalse(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.FINALIZADO));
    }

    @Test
    void naoPodePularEtapas() {
        assertFalse(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.PRONTO));
        assertFalse(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.SAIU_PARA_ENTREGA));
    }

    @Test
    void cancelado_e_finalizado_nao_aceitam_transicoes() {
        for (StatusPedido destino : StatusPedido.values()) {
            assertFalse(StatusPedido.CANCELADO.podeAvancarPara(destino));
            assertFalse(StatusPedido.FINALIZADO.podeAvancarPara(destino));
        }
    }

    @Test
    void descontoECancelamentoSomenteAntesDaSaida() {
        assertTrue(StatusPedido.RECEBIDO.permiteDescontoOuCancelamento());
        assertTrue(StatusPedido.PRONTO.permiteDescontoOuCancelamento());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA.permiteDescontoOuCancelamento());
        assertFalse(StatusPedido.RETIRADO.permiteDescontoOuCancelamento());
        assertFalse(StatusPedido.CANCELADO.permiteDescontoOuCancelamento());
    }

    @Test
    void converteStatusAntigosDaVersaoInicial() {
        assertEquals(StatusPedido.EM_PREPARACAO, StatusPedido.deTexto("EM_PREPARO"));
        assertEquals(StatusPedido.FINALIZADO, StatusPedido.deTexto("ENTREGUE"));
    }
}
