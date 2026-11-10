package com.umc.pediupatrao.service;

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
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.ModalidadeEntrega;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RequisicaoInvalidaException;
import com.umc.pediupatrao.repository.PedidoConsultaRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * RF01 a RF07 - Regras de negócio do Dashboard Administrativo.
 *
 * Fluxo de cada método: o repositório busca no MongoDB só os pedidos do filtro
 * (RF05) e este serviço calcula os números com BigDecimal (precisão de
 * centavos). Todos os métodos exigem ADMIN ou GERENTE (@PreAuthorize), além da
 * regra de URL no SecurityConfig.
 *
 * Tratamento de dados (RF07): pedidos antigos sem valores, sem status ou sem
 * itens não quebram os cálculos (valores nulos contam como zero; status nulo
 * conta como RECEBIDO).
 */
@Service
public class DashboardService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int LIMITE_MAXIMO = 50;
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun",
            "jul", "ago", "set", "out", "nov", "dez"};

    /** Status que ainda estão "na cozinha ou no caminho" (nem finalizados, nem cancelados). */
    private static final Set<StatusPedido> EM_ANDAMENTO = EnumSet.of(StatusPedido.RECEBIDO,
            StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO);

    private final PedidoConsultaRepository consulta;

    public DashboardService(PedidoConsultaRepository consulta) {
        this.consulta = consulta;
    }

    // ============================ RF01 - INDICADORES ============================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RespostaDashboard<IndicadoresDTO> indicadores(FiltroDashboard filtro) {
        List<Pedido> atual = consulta.buscar(filtro);
        FiltroDashboard anterior = filtro.periodoAnterior();
        List<Pedido> pedidosAnteriores = consulta.buscar(anterior);

        List<Pedido> validos = validos(atual);
        long cancelados = atual.size() - validos.size();
        BigDecimal faturamento = somarValores(validos);
        BigDecimal ticket = dividir(faturamento, validos.size());

        List<Pedido> validosAnteriores = validos(pedidosAnteriores);
        BigDecimal faturamentoAnterior = somarValores(validosAnteriores);
        BigDecimal ticketAnterior = dividir(faturamentoAnterior, validosAnteriores.size());

        IndicadoresDTO.ComparativoDTO comparativo = new IndicadoresDTO.ComparativoDTO(
                anterior.inicio(), anterior.fim(),
                faturamentoAnterior, validosAnteriores.size(), ticketAnterior,
                variacao(faturamento, faturamentoAnterior),
                variacao(BigDecimal.valueOf(validos.size()), BigDecimal.valueOf(validosAnteriores.size())),
                variacao(ticket, ticketAnterior));

        IndicadoresDTO dto = new IndicadoresDTO(
                atual.size(),
                validos.size(),
                cancelados,
                atual.stream().filter(p -> EM_ANDAMENTO.contains(status(p))).count(),
                atual.stream().filter(p -> status(p) == StatusPedido.FINALIZADO).count(),
                faturamento,
                ticket,
                validos.stream().map(p -> zeroSeNulo(p.getValorDesconto())).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP),
                validos.stream().mapToLong(DashboardService::quantidadeItens).sum(),
                percentual(cancelados, atual.size()),
                tempoMedioAteSaida(atual),
                comparativo);
        return RespostaDashboard.de(filtro, dto);
    }

    // ============================ RF02 - ANÁLISE DE VENDAS ============================

    /** Série de vendas ao longo do período, agrupada por dia, semana ou mês (intervalos sem venda aparecem com zero). */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RespostaDashboard<List<PontoSerieDTO>> vendasNoTempo(FiltroDashboard filtro, Agrupamento agrupamento) {
        Map<String, Acumulador> grupos = criarIntervalos(filtro, agrupamento);
        for (Pedido p : consulta.buscar(filtro)) {
            if (p.getDataHoraEntrada() == null) {
                continue; // RF07: pedido sem data de entrada não entra na série
            }
            Acumulador acc = grupos.get(chaveDoIntervalo(p.getDataHoraEntrada().toLocalDate(), agrupamento));
            if (acc != null) {
                acc.somar(p);
            }
        }
        return RespostaDashboard.de(filtro, paraPontos(grupos));
    }

    /** Distribuição dos pedidos pelas 24 horas do dia (mostra os horários de pico). */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RespostaDashboard<List<PontoSerieDTO>> vendasPorHora(FiltroDashboard filtro) {
        Map<String, Acumulador> horas = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            horas.put(String.valueOf(h), new Acumulador(String.valueOf(h), h + "h"));
        }
        for (Pedido p : consulta.buscar(filtro)) {
            if (p.getDataHoraEntrada() != null) {
                horas.get(String.valueOf(p.getDataHoraEntrada().getHour())).somar(p);
            }
        }
        return RespostaDashboard.de(filtro, paraPontos(horas));
    }

    // ============================ RF03 - SITUAÇÃO DOS PEDIDOS ============================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RespostaDashboard<StatusPedidosDTO> situacaoDosPedidos(FiltroDashboard filtro) {
        List<Pedido> pedidos = consulta.buscar(filtro);
        Map<StatusPedido, Long> contagem = new LinkedHashMap<>();
        for (StatusPedido s : StatusPedido.values()) {
            contagem.put(s, 0L);
        }
        for (Pedido p : pedidos) {
            contagem.merge(status(p), 1L, Long::sum);
        }
        List<StatusPedidosDTO.ItemStatus> itens = new ArrayList<>();
        contagem.forEach((s, qtd) -> itens.add(
                new StatusPedidosDTO.ItemStatus(s.name(), s.getDescricao(), qtd, percentual(qtd, pedidos.size()))));
        long emAndamento = pedidos.stream().filter(p -> EM_ANDAMENTO.contains(status(p))).count();
        return RespostaDashboard.de(filtro, new StatusPedidosDTO(pedidos.size(), emAndamento, itens));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RespostaDashboard<List<PedidoResumoDTO>> pedidosRecentes(FiltroDashboard filtro, int limite) {
        validarLimite(limite);
        List<PedidoResumoDTO> lista = consulta.buscarRecentes(filtro, limite).stream()
                .map(p -> new PedidoResumoDTO(p.getId(), p.getCodigo(),
                        p.getClienteNome() == null ? "-" : p.getClienteNome(),
                        status(p).name(), status(p).getDescricao(),
                        p.getModalidade() == null ? ModalidadeEntrega.ENTREGA.name() : p.getModalidade().name(),
                        p.getQuantidadeItens(), zeroSeNulo(p.getValorTotal()),
                        p.getDataHoraEntrada(), p.getResponsavelEntrada()))
                .toList();
        return RespostaDashboard.de(filtro, lista);
    }

    // ============================ RF04 - PRODUTOS MAIS VENDIDOS ============================

    /** Ranking dos produtos dos pedidos NÃO cancelados (pedido cancelado não é venda). */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RespostaDashboard<List<ProdutoRankingDTO>> produtosMaisVendidos(FiltroDashboard filtro, int limite,
                                                                           CriterioRanking criterio) {
        validarLimite(limite);
        Map<String, AcumuladorProduto> porProduto = new LinkedHashMap<>();
        for (Pedido p : validos(consulta.buscar(filtro))) {
            if (p.getItens() == null) {
                continue;
            }
            for (ItemPedido item : p.getItens()) {
                String chave = item.getProdutoId() != null ? item.getProdutoId() : item.getNomeProduto();
                if (chave == null) {
                    continue;
                }
                porProduto.computeIfAbsent(chave, k -> new AcumuladorProduto(item.getProdutoId(), item.getNomeProduto()))
                        .somar(item);
            }
        }

        long totalQuantidade = porProduto.values().stream().mapToLong(a -> a.quantidade).sum();
        BigDecimal totalFaturamento = porProduto.values().stream().map(a -> a.faturamento)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Comparator<AcumuladorProduto> ordem = criterio == CriterioRanking.FATURAMENTO
                ? Comparator.comparing((AcumuladorProduto a) -> a.faturamento).reversed()
                : Comparator.comparingLong((AcumuladorProduto a) -> a.quantidade).reversed();
        ordem = ordem.thenComparing(a -> a.nome == null ? "" : a.nome);

        List<AcumuladorProduto> ordenados = porProduto.values().stream().sorted(ordem).limit(limite).toList();
        List<ProdutoRankingDTO> ranking = new ArrayList<>();
        for (int i = 0; i < ordenados.size(); i++) {
            AcumuladorProduto a = ordenados.get(i);
            BigDecimal participacao = criterio == CriterioRanking.FATURAMENTO
                    ? percentual(a.faturamento, totalFaturamento)
                    : percentual(a.quantidade, totalQuantidade);
            ranking.add(new ProdutoRankingDTO(i + 1, a.produtoId, a.nome, a.quantidade,
                    a.faturamento.setScale(2, RoundingMode.HALF_UP), participacao));
        }
        return RespostaDashboard.de(filtro, ranking);
    }

    // ============================ RF05 - OPÇÕES DE FILTRO ============================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public OpcoesFiltroDTO opcoesDeFiltro() {
        return new OpcoesFiltroDTO(
                Arrays.stream(StatusPedido.values()).map(s -> new OpcoesFiltroDTO.Opcao(s.name(), s.getDescricao())).toList(),
                Arrays.stream(ModalidadeEntrega.values()).map(m -> new OpcoesFiltroDTO.Opcao(m.name(), m.getDescricao())).toList(),
                Arrays.stream(Agrupamento.values()).map(a -> new OpcoesFiltroDTO.Opcao(a.name(), a.getDescricao())).toList(),
                Arrays.stream(CriterioRanking.values()).map(c -> new OpcoesFiltroDTO.Opcao(c.name(), c.getDescricao())).toList(),
                FiltroDashboard.DIAS_PADRAO, FiltroDashboard.MAXIMO_DIAS);
    }

    // ============================ AUXILIARES ============================

    private static StatusPedido status(Pedido p) {
        return p.getStatus() == null ? StatusPedido.RECEBIDO : p.getStatus();
    }

    private static List<Pedido> validos(List<Pedido> pedidos) {
        return pedidos.stream().filter(p -> status(p) != StatusPedido.CANCELADO).toList();
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static BigDecimal somarValores(List<Pedido> pedidos) {
        return pedidos.stream().map(p -> zeroSeNulo(p.getValorTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static long quantidadeItens(Pedido p) {
        return p.getItens() == null ? 0 : p.getItens().stream().mapToLong(ItemPedido::getQuantidade).sum();
    }

    private static BigDecimal dividir(BigDecimal valor, long divisor) {
        if (divisor == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return valor.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    /** parte / todo x 100, com 1 casa decimal (0 quando o todo é zero). */
    private static BigDecimal percentual(long parte, long todo) {
        return percentual(BigDecimal.valueOf(parte), BigDecimal.valueOf(todo));
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (todo.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return parte.multiply(CEM).divide(todo, 1, RoundingMode.HALF_UP);
    }

    /** (atual - anterior) / anterior x 100. Null quando não há base de comparação. */
    private static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal tempoMedioAteSaida(List<Pedido> pedidos) {
        List<Long> minutos = pedidos.stream()
                .filter(p -> p.getDataHoraEntrada() != null && p.getDataHoraSaida() != null)
                .map(p -> Duration.between(p.getDataHoraEntrada(), p.getDataHoraSaida()).toMinutes())
                .filter(m -> m >= 0)
                .toList();
        if (minutos.isEmpty()) {
            return null;
        }
        long soma = minutos.stream().mapToLong(Long::longValue).sum();
        return BigDecimal.valueOf(soma).divide(BigDecimal.valueOf(minutos.size()), 1, RoundingMode.HALF_UP);
    }

    private static void validarLimite(int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new RequisicaoInvalidaException("limite", "O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
    }

    /** Cria todos os intervalos do período, já zerados, na ordem cronológica. */
    private static Map<String, Acumulador> criarIntervalos(FiltroDashboard f, Agrupamento agrupamento) {
        Map<String, Acumulador> mapa = new LinkedHashMap<>();
        switch (agrupamento) {
            case DIA -> {
                for (LocalDate d = f.inicio(); !d.isAfter(f.fim()); d = d.plusDays(1)) {
                    mapa.put(d.toString(), new Acumulador(d.toString(), d.format(DIA_MES)));
                }
            }
            case SEMANA -> {
                LocalDate segunda = f.inicio().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                for (LocalDate d = segunda; !d.isAfter(f.fim()); d = d.plusWeeks(1)) {
                    mapa.put(d.toString(), new Acumulador(d.toString(), "Sem. " + d.format(DIA_MES)));
                }
            }
            case MES -> {
                for (YearMonth m = YearMonth.from(f.inicio()); !m.isAfter(YearMonth.from(f.fim())); m = m.plusMonths(1)) {
                    mapa.put(m.toString(), new Acumulador(m.toString(), MESES[m.getMonthValue() - 1] + "/" + m.getYear()));
                }
            }
        }
        return mapa;
    }

    private static String chaveDoIntervalo(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data.toString();
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case MES -> YearMonth.from(data).toString();
        };
    }

    private static List<PontoSerieDTO> paraPontos(Map<String, Acumulador> grupos) {
        return grupos.values().stream()
                .map(a -> new PontoSerieDTO(a.chave, a.rotulo, a.pedidos, a.cancelados,
                        a.faturamento.setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    /** Soma pedidos e faturamento de um intervalo. */
    private static final class Acumulador {
        private final String chave;
        private final String rotulo;
        private long pedidos;
        private long cancelados;
        private BigDecimal faturamento = BigDecimal.ZERO;

        private Acumulador(String chave, String rotulo) {
            this.chave = chave;
            this.rotulo = rotulo;
        }

        private void somar(Pedido p) {
            if (status(p) == StatusPedido.CANCELADO) {
                cancelados++;
            } else {
                pedidos++;
                faturamento = faturamento.add(zeroSeNulo(p.getValorTotal()));
            }
        }
    }

    /** Soma quantidade e faturamento de um produto. */
    private static final class AcumuladorProduto {
        private final String produtoId;
        private final String nome;
        private long quantidade;
        private BigDecimal faturamento = BigDecimal.ZERO;

        private AcumuladorProduto(String produtoId, String nome) {
            this.produtoId = produtoId;
            this.nome = nome;
        }

        private void somar(ItemPedido item) {
            quantidade += item.getQuantidade();
            BigDecimal subtotal = item.getSubtotal() != null ? item.getSubtotal()
                    : zeroSeNulo(item.getPrecoUnitario()).multiply(BigDecimal.valueOf(item.getQuantidade()));
            faturamento = faturamento.add(subtotal);
        }
    }
}
