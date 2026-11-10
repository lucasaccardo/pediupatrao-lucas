package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.CriterioRanking;
import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.IndicadoresDTO;
import com.umc.pediupatrao.dto.dashboard.OpcoesFiltroDTO;
import com.umc.pediupatrao.dto.dashboard.PedidoResumoDTO;
import com.umc.pediupatrao.dto.dashboard.PontoSerieDTO;
import com.umc.pediupatrao.dto.dashboard.ProdutoRankingDTO;
import com.umc.pediupatrao.dto.dashboard.RespostaDashboard;
import com.umc.pediupatrao.dto.dashboard.StatusPedidosDTO;
import com.umc.pediupatrao.exception.RequisicaoInvalidaException;
import com.umc.pediupatrao.service.DashboardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF06 - API do Dashboard Administrativo (somente leitura, ADMIN e GERENTE).
 *
 * Todos os endpoints aceitam os filtros opcionais (RF05):
 *   inicio=AAAA-MM-DD, fim=AAAA-MM-DD, status=RECEBIDO|...|CANCELADO, modalidade=ENTREGA|RETIRADA
 *
 * Documentação completa com exemplos: docs/API-DASHBOARD.md
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final DashboardService dashboardService;

    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** RF01 - Indicadores gerais (faturamento, pedidos, ticket médio, cancelamentos...). */
    @GetMapping("/indicadores")
    public RespostaDashboard<IndicadoresDTO> indicadores(@RequestParam(required = false) String inicio,
                                                         @RequestParam(required = false) String fim,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String modalidade) {
        return dashboardService.indicadores(FiltroDashboard.de(inicio, fim, status, modalidade));
    }

    /** RF02 - Série de vendas agrupada por DIA, SEMANA ou MES. */
    @GetMapping("/vendas")
    public RespostaDashboard<List<PontoSerieDTO>> vendas(@RequestParam(required = false) String inicio,
                                                         @RequestParam(required = false) String fim,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String modalidade,
                                                         @RequestParam(required = false) String agrupamento) {
        return dashboardService.vendasNoTempo(FiltroDashboard.de(inicio, fim, status, modalidade),
                Agrupamento.deTexto(agrupamento));
    }

    /** RF02 - Vendas por hora do dia (horários de pico). */
    @GetMapping("/vendas/por-hora")
    public RespostaDashboard<List<PontoSerieDTO>> vendasPorHora(@RequestParam(required = false) String inicio,
                                                                @RequestParam(required = false) String fim,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String modalidade) {
        return dashboardService.vendasPorHora(FiltroDashboard.de(inicio, fim, status, modalidade));
    }

    /** RF03 - Quantidade de pedidos em cada status. */
    @GetMapping("/pedidos/status")
    public RespostaDashboard<StatusPedidosDTO> situacao(@RequestParam(required = false) String inicio,
                                                        @RequestParam(required = false) String fim,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String modalidade) {
        return dashboardService.situacaoDosPedidos(FiltroDashboard.de(inicio, fim, status, modalidade));
    }

    /** RF03 - Últimos pedidos registrados (limite 1 a 50, padrão 10). */
    @GetMapping("/pedidos/recentes")
    public RespostaDashboard<List<PedidoResumoDTO>> recentes(@RequestParam(required = false) String inicio,
                                                             @RequestParam(required = false) String fim,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String modalidade,
                                                             @RequestParam(required = false) String limite) {
        return dashboardService.pedidosRecentes(FiltroDashboard.de(inicio, fim, status, modalidade),
                lerLimite(limite, 10));
    }

    /** RF04 - Ranking dos produtos mais vendidos (critério QUANTIDADE ou FATURAMENTO, limite 1 a 50, padrão 5). */
    @GetMapping("/produtos/mais-vendidos")
    public RespostaDashboard<List<ProdutoRankingDTO>> maisVendidos(@RequestParam(required = false) String inicio,
                                                                   @RequestParam(required = false) String fim,
                                                                   @RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) String modalidade,
                                                                   @RequestParam(required = false) String limite,
                                                                   @RequestParam(required = false) String criterio) {
        return dashboardService.produtosMaisVendidos(FiltroDashboard.de(inicio, fim, status, modalidade),
                lerLimite(limite, 5), CriterioRanking.deTexto(criterio));
    }

    /** RF05 - Valores aceitos pelos filtros (para montar a tela). */
    @GetMapping("/filtros")
    public OpcoesFiltroDTO filtros() {
        return dashboardService.opcoesDeFiltro();
    }

    private static int lerLimite(String valor, int padrao) {
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new RequisicaoInvalidaException("limite", "O limite deve ser um número inteiro entre 1 e 50.");
        }
    }
}
