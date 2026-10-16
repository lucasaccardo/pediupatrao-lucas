package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ModalidadeEntrega;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacaoAuditoria;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * RF02, RF03 e RF04 - Testes unitários das regras do PedidoService
 * (sem banco de dados: os repositórios são simulados com Mockito).
 */
class PedidoServiceTest {

    private PedidoRepository pedidoRepository;
    private ProdutoRepository produtoRepository;
    private ClienteRepository clienteRepository;
    private AuditoriaService auditoriaService;
    private PedidoService service;

    @BeforeEach
    void setUp() {
        pedidoRepository = mock(PedidoRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        clienteRepository = mock(ClienteRepository.class);
        auditoriaService = mock(AuditoriaService.class);
        service = new PedidoService(pedidoRepository, produtoRepository, clienteRepository, auditoriaService);

        // simula o usuário logado
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("gerente", "x", "ROLE_GERENTE"));

        // o "save" devolve o próprio objeto, com um id
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("abc123");
            }
            return p;
        });
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void prepararClienteEProduto() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(c));

        Produto p = new Produto();
        p.setId("p1");
        p.setNome("Pizza Calabresa");
        p.setPreco(50.0);
        p.setAtivo(true);
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(p));
    }

    private Pedido pedidoComStatus(StatusPedido status) {
        prepararClienteEProduto();
        Pedido p = service.criarPedido(new NovoPedidoRequest("c1", ModalidadeEntrega.ENTREGA, null,
                List.of(new NovoPedidoRequest.ItemRequest("p1", 2))));
        p.setStatus(status);
        when(pedidoRepository.findById("abc123")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void totalEhCalculadoNoServidorComPrecoDoBanco() {
        Pedido p = pedidoComStatus(StatusPedido.RECEBIDO);
        assertEquals(new BigDecimal("100.00"), p.getValorTotal());
        assertEquals("gerente", p.getResponsavelEntrada());
        assertNotNull(p.getDataHoraEntrada());
        verify(auditoriaService).registrar(eq(TipoOperacaoAuditoria.INCLUSAO_PEDIDO), eq("PEDIDO"),
                eq("abc123"), anyList(), any());
    }

    @Test
    void descontoDe10PorCentoRecalculaTotal() {
        pedidoComStatus(StatusPedido.EM_PREPARACAO);
        Pedido p = service.aplicarDesconto("abc123", new BigDecimal("10"), "cliente frequente");
        assertEquals(new BigDecimal("10.00"), p.getValorDesconto());
        assertEquals(new BigDecimal("90.00"), p.getValorTotal());
    }

    @Test
    void descontoAcimaDe20PorCentoEhBloqueado() {
        pedidoComStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto("abc123", new BigDecimal("25"), null));
    }

    @Test
    void descontoDepoisDaSaidaEhBloqueado() {
        pedidoComStatus(StatusPedido.SAIU_PARA_ENTREGA);
        assertThrows(RegraNegocioException.class,
                () -> service.aplicarDesconto("abc123", new BigDecimal("5"), null));
    }

    @Test
    void cancelamentoExigeJustificativa() {
        pedidoComStatus(StatusPedido.RECEBIDO);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("abc123", ""));
    }

    @Test
    void cancelamentoDepoisDaSaidaEhBloqueado() {
        pedidoComStatus(StatusPedido.SAIU_PARA_ENTREGA);
        assertThrows(RegraNegocioException.class, () -> service.cancelar("abc123", "Cliente desistiu"));
    }

    @Test
    void pedidoCanceladoNaoMudaMaisDeStatus() {
        pedidoComStatus(StatusPedido.RECEBIDO);
        service.cancelar("abc123", "Cliente desistiu do pedido");
        assertThrows(RegraNegocioException.class,
                () -> service.alterarStatus("abc123", StatusPedido.EM_PREPARACAO));
    }

    @Test
    void naoPodeFinalizarPedidoEmPreparacao() {
        pedidoComStatus(StatusPedido.EM_PREPARACAO);
        assertThrows(RegraNegocioException.class,
                () -> service.alterarStatus("abc123", StatusPedido.FINALIZADO));
        verify(auditoriaService, never()).registrar(eq(TipoOperacaoAuditoria.ALTERACAO_STATUS_PEDIDO),
                any(), any(), anyList(), any());
    }

    @Test
    void saidaRegistraDataHoraEResponsavel() {
        pedidoComStatus(StatusPedido.PRONTO);
        Pedido p = service.alterarStatus("abc123", StatusPedido.SAIU_PARA_ENTREGA);
        assertNotNull(p.getDataHoraSaida());
        assertEquals("gerente", p.getResponsavelSaida());
    }

    @Test
    void pedidoDeEntregaNaoPodeIrParaRetirado() {
        pedidoComStatus(StatusPedido.PRONTO);
        assertThrows(RegraNegocioException.class,
                () -> service.alterarStatus("abc123", StatusPedido.RETIRADO));
    }
}
