package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.IndicadoresDTO;
import com.umc.pediupatrao.dto.dashboard.PontoSerieDTO;
import com.umc.pediupatrao.dto.dashboard.ProdutoRankingDTO;
import com.umc.pediupatrao.dto.dashboard.StatusPedidosDTO;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.ModalidadeEntrega;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RequisicaoInvalidaException;
import com.umc.pediupatrao.repository.PedidoConsultaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RF01 a RF04 e RF07 - Cálculos do dashboard com uma massa pequena e conhecida
 * (sem banco: o repositório é simulado com Mockito).
 *
 * Massa do período 01/11 a 10/11/2026:
 *  p1 FINALIZADO 02/11 19h - 2x Calabresa (50,00)            = 100,00  (saída em 40 min)
 *  p2 FINALIZADO 03/11 20h - Margherita (45) + Refri (12) -10% = 51,30  (saída em 30 min)
 *  p3 CANCELADO  03/11 21h - 1x Calabresa                     = 50,00  (não conta como venda)
 *  p4 EM_PREPARACAO 10/11 19h - 1x Refri                      = 12,00
 * Período anterior (22/10 a 31/10): 1 pedido de 81,65.
 */
class DashboardServiceTest {

    private PedidoConsultaRepository consulta;
    private DashboardService service;
    private FiltroDashboard filtro;

    @BeforeEach
    void setUp() {
        consulta = mock(PedidoConsultaRepository.class);
        service = new DashboardService(consulta);
        filtro = FiltroDashboard.de("2026-11-01", "2026-11-10", null, null, LocalDate.of(2026, 11, 10));

        List<Pedido> atual = new ArrayList<>();
        Pedido p1 = pedido(StatusPedido.FINALIZADO, "2026-11-02T19:00", ModalidadeEntrega.ENTREGA, null,
                item("calabresa", "Pizza Calabresa", 2, "50.00"));
        p1.setDataHoraSaida(p1.getDataHoraEntrada().plusMinutes(40));
        Pedido p2 = pedido(StatusPedido.FINALIZADO, "2026-11-03T20:00", ModalidadeEntrega.RETIRADA, "10",
                item("margherita", "Pizza Margherita", 1, "45.00"), item("refri", "Refrigerante 2L", 1, "12.00"));
        p2.setDataHoraSaida(p2.getDataHoraEntrada().plusMinutes(30));
        atual.add(p1);
        atual.add(p2);
        atual.add(pedido(StatusPedido.CANCELADO, "2026-11-03T21:00", ModalidadeEntrega.ENTREGA, null,
                item("calabresa", "Pizza Calabresa", 1, "50.00")));
        atual.add(pedido(StatusPedido.EM_PREPARACAO, "2026-11-10T19:30", ModalidadeEntrega.ENTREGA, null,
                item("refri", "Refrigerante 2L", 1, "12.00")));

        List<Pedido> anterior = List.of(pedido(StatusPedido.FINALIZADO, "2026-10-25T20:00", ModalidadeEntrega.ENTREGA, null,
                item("portuguesa", "Pizza Portuguesa", 1, "81.65")));

        when(consulta.buscar(filtro)).thenReturn(atual);
        when(consulta.buscar(filtro.periodoAnterior())).thenReturn(anterior);
    }

    // ---------- RF01 ----------

    @Test
    void indicadoresSomamSomentePedidosNaoCancelados() {
        IndicadoresDTO d = service.indicadores(filtro).dados();
        assertEquals(4, d.totalPedidos());
        assertEquals(3, d.pedidosValidos());
        assertEquals(1, d.pedidosCancelados());
        assertEquals(new BigDecimal("163.30"), d.faturamento());   // 100 + 51,30 + 12
        assertEquals(new BigDecimal("54.43"), d.ticketMedio());    // 163,30 / 3
        assertEquals(new BigDecimal("5.70"), d.totalDescontos());  // 10% de 57,00
        assertEquals(5, d.itensVendidos());
        assertEquals(new BigDecimal("25.0"), d.taxaCancelamentoPercentual());
        assertEquals(1, d.pedidosEmAndamento());
        assertEquals(2, d.pedidosFinalizados());
        assertEquals(new BigDecimal("35.0"), d.tempoMedioAteSaidaMinutos()); // (40 + 30) / 2
    }

    @Test
    void indicadoresComparamComPeriodoAnterior() {
        IndicadoresDTO.ComparativoDTO c = service.indicadores(filtro).dados().comparativoPeriodoAnterior();
        assertEquals(LocalDate.of(2026, 10, 22), c.inicio());
        assertEquals(new BigDecimal("81.65"), c.faturamento());
        assertEquals(new BigDecimal("100.0"), c.variacaoFaturamentoPercentual()); // dobrou
        assertEquals(new BigDecimal("200.0"), c.variacaoPedidosPercentual());     // de 1 para 3
        assertEquals(new BigDecimal("-33.3"), c.variacaoTicketMedioPercentual());
    }

    @Test
    void semPeriodoAnteriorAVariacaoFicaNula() {
        when(consulta.buscar(filtro.periodoAnterior())).thenReturn(List.of());
        assertNull(service.indicadores(filtro).dados().comparativoPeriodoAnterior().variacaoFaturamentoPercentual());
    }

    @Test
    void pedidoAntigoSemValoresNaoQuebraOsCalculos() {
        Pedido legado = new Pedido();
        legado.setStatus(null);
        legado.setItens(null);
        legado.setDataHoraEntrada(LocalDateTime.parse("2026-11-05T12:00"));
        when(consulta.buscar(filtro)).thenReturn(List.of(legado));
        IndicadoresDTO d = service.indicadores(filtro).dados();
        assertEquals(1, d.pedidosValidos());
        assertEquals(0, d.itensVendidos());
    }

    // ---------- RF02 ----------

    @Test
    void vendasPorDiaTrazemTodosOsDiasInclusiveSemVenda() {
        List<PontoSerieDTO> serie = service.vendasNoTempo(filtro, Agrupamento.DIA).dados();
        assertEquals(10, serie.size());
        PontoSerieDTO dia3 = serie.get(2);
        assertEquals("2026-11-03", dia3.chave());
        assertEquals("03/11", dia3.rotulo());
        assertEquals(1, dia3.pedidos());
        assertEquals(1, dia3.cancelados());
        assertEquals(new BigDecimal("51.30"), dia3.faturamento());
        assertEquals(0, serie.get(4).pedidos()); // 05/11 sem vendas
    }

    @Test
    void vendasPorSemanaComecamNaSegundaFeira() {
        List<PontoSerieDTO> serie = service.vendasNoTempo(filtro, Agrupamento.SEMANA).dados();
        assertEquals(3, serie.size()); // semanas de 26/10, 02/11 e 09/11
        assertEquals("2026-11-02", serie.get(1).chave());
        assertEquals(2, serie.get(1).pedidos());
        assertEquals(new BigDecimal("151.30"), serie.get(1).faturamento());
    }

    @Test
    void vendasPorMesUsamRotuloEmPortugues() {
        FiltroDashboard doisMeses = FiltroDashboard.de("2026-10-15", "2026-11-10", null, null, LocalDate.of(2026, 11, 10));
        when(consulta.buscar(doisMeses)).thenReturn(List.of());
        List<PontoSerieDTO> serie = service.vendasNoTempo(doisMeses, Agrupamento.MES).dados();
        assertEquals(2, serie.size());
        assertEquals("out/2026", serie.get(0).rotulo());
        assertEquals("nov/2026", serie.get(1).rotulo());
    }

    @Test
    void vendasPorHoraMostramAs24Horas() {
        List<PontoSerieDTO> horas = service.vendasPorHora(filtro).dados();
        assertEquals(24, horas.size());
        assertEquals(2, horas.get(19).pedidos());   // p1 e p4
        assertEquals(0, horas.get(21).pedidos());   // p3 foi cancelado
        assertEquals(1, horas.get(21).cancelados());
    }

    // ---------- RF03 ----------

    @Test
    void situacaoListaTodosOsStatusComPercentual() {
        StatusPedidosDTO s = service.situacaoDosPedidos(filtro).dados();
        assertEquals(StatusPedido.values().length, s.porStatus().size());
        assertEquals(4, s.total());
        assertEquals(1, s.emAndamento());
        StatusPedidosDTO.ItemStatus finalizado = s.porStatus().stream()
                .filter(i -> i.status().equals("FINALIZADO")).findFirst().orElseThrow();
        assertEquals(2, finalizado.quantidade());
        assertEquals(new BigDecimal("50.0"), finalizado.percentual());
    }

    // ---------- RF04 ----------

    @Test
    void rankingPorQuantidadeIgnoraCancelados() {
        List<ProdutoRankingDTO> r = service.produtosMaisVendidos(filtro, 5, CriterioRanking.QUANTIDADE).dados();
        assertEquals(3, r.size());
        assertEquals("Pizza Calabresa", r.get(0).nome()); // 2 un. (a cancelada não conta)
        assertEquals(2, r.get(0).quantidade());
        assertEquals(new BigDecimal("40.0"), r.get(0).participacaoPercentual()); // 2 de 5
        assertEquals("Refrigerante 2L", r.get(1).nome()); // empate em 2, desempate pelo nome
        assertEquals(1, r.get(0).posicao());
    }

    @Test
    void rankingPorFaturamentoOrdenaPeloValor() {
        List<ProdutoRankingDTO> r = service.produtosMaisVendidos(filtro, 2, CriterioRanking.FATURAMENTO).dados();
        assertEquals(2, r.size());
        assertEquals("Pizza Calabresa", r.get(0).nome());
        assertEquals(new BigDecimal("100.00"), r.get(0).faturamento());
        assertEquals("Pizza Margherita", r.get(1).nome());
        assertEquals(new BigDecimal("59.2"), r.get(0).participacaoPercentual()); // 100 de 169
    }

    @Test
    void limiteForaDoIntervaloEhRecusado() {
        assertThrows(RequisicaoInvalidaException.class,
                () -> service.produtosMaisVendidos(filtro, 0, CriterioRanking.QUANTIDADE));
        assertThrows(RequisicaoInvalidaException.class, () -> service.pedidosRecentes(filtro, 51));
    }

    // ---------- auxiliares ----------

    private static ItemPedido item(String id, String nome, int qtd, String preco) {
        return new ItemPedido(id, nome, qtd, new BigDecimal(preco));
    }

    private static Pedido pedido(StatusPedido status, String entrada, ModalidadeEntrega modalidade,
                                 String descontoPercentual, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setItens(new ArrayList<>(List.of(itens)));
        p.setModalidade(modalidade);
        p.setPercentualDesconto(descontoPercentual == null ? BigDecimal.ZERO : new BigDecimal(descontoPercentual));
        p.recalcularValores();
        p.setStatus(status);
        p.setDataHoraEntrada(LocalDateTime.parse(entrada));
        return p;
    }
}
