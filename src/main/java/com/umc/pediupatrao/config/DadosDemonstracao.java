package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.ModalidadeEntrega;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gera uma massa de DADOS DE DEMONSTRAÇÃO para o dashboard (60 dias de pedidos).
 *
 * Só roda quando a propriedade app.demo.pedidos=true (variável DEMO_PEDIDOS=true)
 * E a coleção de pedidos está vazia - nunca mistura com pedidos reais.
 *
 * Os pedidos seguem as mesmas regras do sistema: itens com o preço do cadastro,
 * total calculado por Pedido.recalcularValores(), transições válidas de status,
 * responsáveis e horários de entrada/saída. A semente fixa (2026) faz a massa
 * ser sempre a mesma, o que facilita conferir os números.
 */
@Component
public class DadosDemonstracao {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracao.class);
    private static final int DIAS = 60;
    private static final String OBSERVACAO = "Pedido de demonstração (gerado automaticamente)";
    private static final String[] JUSTIFICATIVAS = {
            "Cliente desistiu por telefone", "Endereço fora da área de entrega",
            "Pagamento não confirmado", "Pedido duplicado"};

    private final PedidoRepository pedidos;
    private final ProdutoRepository produtos;
    private final ClienteRepository clientes;

    public DadosDemonstracao(PedidoRepository pedidos, ProdutoRepository produtos, ClienteRepository clientes) {
        this.pedidos = pedidos;
        this.produtos = produtos;
        this.clientes = clientes;
    }

    public void gerarSeVazio() {
        if (pedidos.count() > 0) {
            log.info("Dados de demonstração não gerados: já existem pedidos no banco.");
            return;
        }
        Random rnd = new Random(2026);
        List<Produto> cardapio = garantirProdutos();
        List<Cliente> listaClientes = garantirClientes();

        LocalDateTime agora = LocalDateTime.now();
        LocalDate hoje = agora.toLocalDate();
        List<Pedido> gerados = new ArrayList<>();

        for (int i = DIAS - 1; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            // tendência: os dias mais recentes vendem um pouco mais (crescimento do negócio)
            double crescimento = 1.0 + (DIAS - i) / (double) DIAS * 0.5;
            int quantidade = (int) Math.round(base(dia.getDayOfWeek()) * crescimento) + rnd.nextInt(3);
            for (int n = 0; n < quantidade; n++) {
                LocalDateTime entrada = dia.atTime(hora(rnd), rnd.nextInt(60));
                if (entrada.isAfter(agora)) {
                    continue; // não cria pedido no futuro
                }
                gerados.add(montarPedido(rnd, entrada, agora, cardapio, listaClientes));
            }
        }
        // alguns pedidos "agora", para o painel mostrar pedidos em andamento
        for (int m : new int[]{3, 12, 22, 35, 50}) {
            gerados.add(montarPedido(rnd, agora.minusMinutes(m), agora, cardapio, listaClientes));
        }

        pedidos.saveAll(gerados);
        log.info("Dados de demonstração: {} pedidos gerados nos últimos {} dias.", gerados.size(), DIAS);
    }

    private Pedido montarPedido(Random rnd, LocalDateTime entrada, LocalDateTime agora,
                                List<Produto> cardapio, List<Cliente> listaClientes) {
        Cliente cliente = listaClientes.get(rnd.nextInt(listaClientes.size()));
        Pedido p = new Pedido();
        p.setClienteId(cliente.getId());
        p.setClienteNome(cliente.getNome());
        p.setModalidade(rnd.nextDouble() < 0.65 ? ModalidadeEntrega.ENTREGA : ModalidadeEntrega.RETIRADA);
        p.setItens(itens(rnd, cardapio));
        p.setObservacao(OBSERVACAO);
        p.setPercentualDesconto(BigDecimal.ZERO);

        String atendente = rnd.nextDouble() < 0.8 ? "atendente" : "gerente";
        p.setStatus(StatusPedido.RECEBIDO);
        p.setDataHoraEntrada(entrada);
        p.setResponsavelEntrada(atendente);
        p.getHistorico().add(new HistoricoStatus(null, StatusPedido.RECEBIDO, entrada, atendente));

        // desconto do gerente em parte dos pedidos (até 15%, abaixo do limite de 20%)
        if (rnd.nextDouble() < 0.12) {
            p.setPercentualDesconto(new BigDecimal(new int[]{5, 10, 15}[rnd.nextInt(3)]).setScale(2, RoundingMode.HALF_UP));
            p.setDescontoAplicadoPor("gerente");
            p.setDataHoraDesconto(entrada.plusMinutes(2));
            p.setMotivoDesconto("Cliente frequente");
        }
        p.recalcularValores();

        long minutosDesdeEntrada = java.time.Duration.between(entrada, agora).toMinutes();
        boolean cancelar = rnd.nextDouble() < 0.07;

        if (cancelar && minutosDesdeEntrada > 10) {
            StatusPedido antes = rnd.nextBoolean() ? StatusPedido.RECEBIDO : StatusPedido.EM_PREPARACAO;
            LocalDateTime t = entrada.plusMinutes(4);
            if (antes == StatusPedido.EM_PREPARACAO) {
                avancar(p, StatusPedido.EM_PREPARACAO, t, "atendente");
                t = t.plusMinutes(3);
            }
            p.setStatus(StatusPedido.CANCELADO);
            p.setDataHoraCancelamento(t);
            p.setResponsavelCancelamento("gerente");
            p.setJustificativaCancelamento(JUSTIFICATIVAS[rnd.nextInt(JUSTIFICATIVAS.length)]);
            p.getHistorico().add(new HistoricoStatus(antes, StatusPedido.CANCELADO, t, "gerente"));
            return p;
        }

        // linha do tempo normal: preparo (5 min), pronto (+15 a 30), saída (+5 a 10), finalização (+15 a 35)
        LocalDateTime preparo = entrada.plusMinutes(5);
        LocalDateTime pronto = preparo.plusMinutes(15 + rnd.nextInt(16));
        LocalDateTime saida = pronto.plusMinutes(5 + rnd.nextInt(6));
        LocalDateTime fim = saida.plusMinutes(15 + rnd.nextInt(21));
        StatusPedido saidaStatus = p.getModalidade() == ModalidadeEntrega.ENTREGA
                ? StatusPedido.SAIU_PARA_ENTREGA : StatusPedido.RETIRADO;

        if (!preparo.isAfter(agora)) {
            avancar(p, StatusPedido.EM_PREPARACAO, preparo, "atendente");
        }
        if (!pronto.isAfter(agora)) {
            avancar(p, StatusPedido.PRONTO, pronto, "atendente");
        }
        if (!saida.isAfter(agora)) {
            String resp = rnd.nextBoolean() ? "atendente" : "gerente";
            avancar(p, saidaStatus, saida, resp);
            p.setDataHoraSaida(saida);
            p.setResponsavelSaida(resp);
        }
        if (!fim.isAfter(agora)) {
            avancar(p, StatusPedido.FINALIZADO, fim, "atendente");
            p.setDataHoraFinalizacao(fim);
            p.setResponsavelFinalizacao("atendente");
        }
        return p;
    }

    private static void avancar(Pedido p, StatusPedido novo, LocalDateTime quando, String usuario) {
        p.getHistorico().add(new HistoricoStatus(p.getStatus(), novo, quando, usuario));
        p.setStatus(novo);
    }

    /** 1 a 3 produtos diferentes, com pesos (pizzas mais pedidas aparecem mais). */
    private static List<ItemPedido> itens(Random rnd, List<Produto> cardapio) {
        Map<String, ItemPedido> escolhidos = new LinkedHashMap<>();
        int quantosProdutos = 1 + rnd.nextInt(3);
        for (int i = 0; i < quantosProdutos; i++) {
            Produto prod = sortearPorPeso(rnd, cardapio);
            if (escolhidos.containsKey(prod.getId())) {
                continue;
            }
            int qtd = "Pizza".equalsIgnoreCase(prod.getTipo()) ? 1 + (rnd.nextDouble() < 0.3 ? 1 : 0) : 1 + rnd.nextInt(2);
            BigDecimal preco = BigDecimal.valueOf(prod.getPreco()).setScale(2, RoundingMode.HALF_UP);
            escolhidos.put(prod.getId(), new ItemPedido(prod.getId(), prod.getNome(), qtd, preco));
        }
        return new ArrayList<>(escolhidos.values());
    }

    private static Produto sortearPorPeso(Random rnd, List<Produto> cardapio) {
        int total = 0;
        int[] pesos = new int[cardapio.size()];
        for (int i = 0; i < cardapio.size(); i++) {
            pesos[i] = peso(cardapio.get(i).getNome());
            total += pesos[i];
        }
        int sorteio = rnd.nextInt(total);
        for (int i = 0; i < pesos.length; i++) {
            sorteio -= pesos[i];
            if (sorteio < 0) {
                return cardapio.get(i);
            }
        }
        return cardapio.get(0);
    }

    private static int peso(String nome) {
        if (nome == null) {
            return 1;
        }
        return switch (nome) {
            case "Pizza Calabresa" -> 10;
            case "Refrigerante 2L" -> 9;
            case "Pizza Margherita" -> 7;
            case "Pizza Frango com Catupiry" -> 6;
            case "Pizza Portuguesa" -> 5;
            case "Pizza Quatro Queijos" -> 4;
            case "Suco Natural 1L" -> 3;
            case "Brownie" -> 2;
            default -> 2;
        };
    }

    /** Pedidos por dia da semana (sexta e sábado são os dias mais fortes). */
    private static int base(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY, TUESDAY -> 2;
            case WEDNESDAY, THURSDAY -> 3;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
            case SUNDAY -> 4;
        };
    }

    /** 20% no almoço (11h-13h) e 80% no jantar (18h-22h, pico às 19h-20h). */
    private static int hora(Random rnd) {
        if (rnd.nextDouble() < 0.2) {
            return 11 + rnd.nextInt(3);
        }
        int[] jantar = {18, 19, 19, 19, 20, 20, 20, 21, 21, 22};
        return jantar[rnd.nextInt(jantar.length)];
    }

    private List<Produto> garantirProdutos() {
        criarProdutoSeFaltar("Pizza Quatro Queijos", "Pizza", 58.00);
        criarProdutoSeFaltar("Pizza Frango com Catupiry", "Pizza", 56.90);
        criarProdutoSeFaltar("Suco Natural 1L", "Bebida", 14.00);
        criarProdutoSeFaltar("Brownie", "Sobremesa", 12.00);
        return produtos.findAll().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0)
                .toList();
    }

    private void criarProdutoSeFaltar(String nome, String tipo, double preco) {
        boolean existe = produtos.findAll().stream().anyMatch(p -> nome.equalsIgnoreCase(p.getNome()));
        if (!existe) {
            Produto p = new Produto();
            p.setNome(nome);
            p.setTipo(tipo);
            p.setPreco(preco);
            p.setAtivo(true);
            produtos.save(p);
        }
    }

    private List<Cliente> garantirClientes() {
        if (clientes.count() >= 5) {
            return clientes.findAll();
        }
        String[][] dados = {
                {"Ana Souza", "(11) 98100-1001", "Rua das Acácias", "120"},
                {"Bruno Lima", "(11) 98100-1002", "Av. Brasil", "455"},
                {"Carla Mendes", "(11) 98100-1003", "Rua Ipê Amarelo", "33"},
                {"Diego Rocha", "(11) 98100-1004", "Rua dos Pinheiros", "908"},
                {"Elisa Martins", "(11) 98100-1005", "Rua Jacarandá", "15"},
                {"Felipe Costa", "(11) 98100-1006", "Av. Paulista", "1500"},
                {"Gabriela Alves", "(11) 98100-1007", "Rua das Palmeiras", "72"},
                {"Henrique Dias", "(11) 98100-1008", "Rua Manacá", "210"}};
        for (String[] d : dados) {
            Cliente c = new Cliente();
            c.setNome(d[0]);
            c.setTelefone(d[1]);
            c.setLogradouro(d[2]);
            c.setNumero(d[3]);
            c.setBairro("Centro");
            c.setCidade("São Paulo");
            c.setEstado("SP");
            clientes.save(c);
        }
        return clientes.findAll();
    }
}
