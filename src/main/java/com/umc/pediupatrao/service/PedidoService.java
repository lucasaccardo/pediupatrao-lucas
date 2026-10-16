package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.ModalidadeEntrega;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacaoAuditoria;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioAutenticado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * RF02, RF03 e RF04 - Regras de negócio dos pedidos.
 *
 * Todas as regras ficam AQUI (camada de serviço), e não no navegador.
 * Além da proteção por URL no SecurityConfig, cada método possui @PreAuthorize
 * (defesa em profundidade): mesmo que alguém chame o serviço por outro caminho,
 * o Spring Security verifica o perfil antes de executar.
 */
@Service
public class PedidoService {

    private static final int QUANTIDADE_MAXIMA_POR_ITEM = 50;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository,
                         ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    // ===================== CONSULTAS =====================

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAllByOrderByDataHoraEntradaDesc();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado: " + id));
    }

    // ===================== RF02 - REGISTRO DO PEDIDO =====================

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(NovoPedidoRequest dados) {
        if (dados == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        Cliente cliente = clienteRepository.findById(valorObrigatorio(dados.clienteId(), "Selecione o cliente."))
                .orElseThrow(() -> new RegraNegocioException("Cliente informado não existe."));

        List<ItemPedido> itens = montarItens(dados.itens());

        String usuario = UsuarioAutenticado.username();
        LocalDateTime agora = LocalDateTime.now();

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setItens(itens);
        pedido.setModalidade(dados.modalidade() == null ? ModalidadeEntrega.ENTREGA : dados.modalidade());
        pedido.setObservacao(limitar(dados.observacao(), 300));
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.recalcularValores(); // total calculado no servidor

        // RF03 - entrada: status RECEBIDO + data/hora + responsável
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(agora);
        pedido.setResponsavelEntrada(usuario);
        pedido.getHistorico().add(new HistoricoStatus(null, StatusPedido.RECEBIDO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> campos = new ArrayList<>();
        AuditoriaService.comparar(campos, "cliente", null, salvo.getClienteNome());
        AuditoriaService.comparar(campos, "itens", null, descreverItens(salvo.getItens()));
        AuditoriaService.comparar(campos, "modalidade", null, salvo.getModalidade());
        AuditoriaService.comparar(campos, "valorTotal", null, salvo.getValorTotal());
        AuditoriaService.comparar(campos, "status", null, salvo.getStatus());
        auditoriaService.registrar(TipoOperacaoAuditoria.INCLUSAO_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), campos, null);
        return salvo;
    }

    /**
     * Monta os itens buscando o PREÇO NO BANCO. O preço nunca vem da requisição.
     * Itens repetidos do mesmo produto são somados.
     */
    private List<ItemPedido> montarItens(List<NovoPedidoRequest.ItemRequest> itensRequest) {
        if (itensRequest == null || itensRequest.isEmpty()) {
            throw new RegraNegocioException("O pedido precisa ter pelo menos um item.");
        }
        Map<String, Integer> quantidades = new LinkedHashMap<>();
        for (NovoPedidoRequest.ItemRequest item : itensRequest) {
            if (item == null || item.produtoId() == null || item.produtoId().isBlank()) {
                throw new RegraNegocioException("Item sem produto informado.");
            }
            int qtd = item.quantidade() == null ? 0 : item.quantidade();
            if (qtd <= 0) {
                continue; // quantidade zero = produto não escolhido no formulário
            }
            quantidades.merge(item.produtoId(), qtd, Integer::sum);
        }
        if (quantidades.isEmpty()) {
            throw new RegraNegocioException("Informe a quantidade de pelo menos um produto.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (Map.Entry<String, Integer> e : quantidades.entrySet()) {
            if (e.getValue() > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("Quantidade máxima por item é " + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }
            Produto produto = produtoRepository.findById(e.getKey())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + e.getKey()));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("Produto inativo: " + produto.getNome());
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("Produto sem preço válido: " + produto.getNome());
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), e.getValue(), preco));
        }
        return itens;
    }

    // ===================== RF03 - TRANSIÇÕES DE STATUS =====================

    /** Estados para os quais o pedido pode avançar (usado também pela tela). */
    public Set<StatusPedido> proximosStatusPermitidos(Pedido pedido) {
        StatusPedido atual = statusAtual(pedido);
        return atual.proximosEstados().stream()
                .filter(destino -> compativelComModalidade(pedido, destino))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido alterarStatus(String id, StatusPedido novoStatus) {
        if (novoStatus == null) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar use a operação de cancelamento (exclusiva do GERENTE, com justificativa).");
        }
        Pedido pedido = buscarPorId(id);
        StatusPedido atual = statusAtual(pedido);

        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ter o status alterado.");
        }
        if (!atual.podeAvancarPara(novoStatus) || !compativelComModalidade(pedido, novoStatus)) {
            throw new RegraNegocioException("Transição inválida: " + atual.getDescricao()
                    + " → " + novoStatus.getDescricao() + ".");
        }

        String usuario = UsuarioAutenticado.username();
        LocalDateTime agora = LocalDateTime.now();
        List<AlteracaoCampo> campos = new ArrayList<>();
        AuditoriaService.comparar(campos, "status", atual, novoStatus);

        pedido.setStatus(novoStatus);
        if (novoStatus.isSaida()) {
            // RF03 - saída: data/hora e responsável obrigatórios
            pedido.setDataHoraSaida(agora);
            pedido.setResponsavelSaida(usuario);
            AuditoriaService.comparar(campos, "dataHoraSaida", null, agora);
            AuditoriaService.comparar(campos, "responsavelSaida", null, usuario);
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setDataHoraFinalizacao(agora);
            pedido.setResponsavelFinalizacao(usuario);
        }
        pedido.getHistorico().add(new HistoricoStatus(atual, novoStatus, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacaoAuditoria.ALTERACAO_STATUS_PEDIDO,
                AuditoriaService.ENTIDADE_PEDIDO, salvo.getId(), campos, null);
        return salvo;
    }

    // ===================== RF04 - DESCONTO (somente GERENTE) =====================

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String motivo) {
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        BigDecimal p = percentual.setScale(2, RoundingMode.HALF_UP);
        if (p.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O desconto deve ser maior que 0%.");
        }
        if (p.compareTo(Pedido.DESCONTO_MAXIMO_PERCENTUAL) > 0) {
            throw new RegraNegocioException("O desconto máximo permitido é de 20% do valor do pedido.");
        }

        Pedido pedido = buscarPorId(id);
        StatusPedido atual = statusAtual(pedido);
        if (!atual.permiteDescontoOuCancelamento()) {
            throw new RegraNegocioException("Desconto só pode ser aplicado antes da saída para entrega/retirada. Status atual: "
                    + atual.getDescricao() + ".");
        }

        BigDecimal percentualAnterior = pedido.getPercentualDesconto();
        BigDecimal descontoAnterior = pedido.getValorDesconto();
        BigDecimal totalAnterior = pedido.getValorTotal();

        // O percentual SUBSTITUI o anterior (não acumula), garantindo o teto de 20%.
        pedido.setPercentualDesconto(p);
        pedido.setDescontoAplicadoPor(UsuarioAutenticado.username());
        pedido.setDataHoraDesconto(LocalDateTime.now());
        pedido.setMotivoDesconto(limitar(motivo, 200));
        pedido.recalcularValores();

        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> campos = new ArrayList<>();
        AuditoriaService.comparar(campos, "percentualDesconto", percentualAnterior, salvo.getPercentualDesconto());
        AuditoriaService.comparar(campos, "valorDesconto", descontoAnterior, salvo.getValorDesconto());
        AuditoriaService.comparar(campos, "valorTotal", totalAnterior, salvo.getValorTotal());
        auditoriaService.registrar(TipoOperacaoAuditoria.DESCONTO_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), campos, salvo.getMotivoDesconto());
        return salvo;
    }

    // ===================== RF04 - CANCELAMENTO (somente GERENTE) =====================

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        if (justificativa == null || justificativa.trim().length() < 5) {
            throw new RegraNegocioException("A justificativa do cancelamento é obrigatória (mínimo 5 caracteres).");
        }
        Pedido pedido = buscarPorId(id);
        StatusPedido atual = statusAtual(pedido);
        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("O pedido já está cancelado.");
        }
        if (!atual.permiteDescontoOuCancelamento()) {
            throw new RegraNegocioException("Não é possível cancelar: o pedido já saiu para entrega/retirada. Status atual: "
                    + atual.getDescricao() + ".");
        }

        String usuario = UsuarioAutenticado.username();
        LocalDateTime agora = LocalDateTime.now();

        // Os itens e valores são preservados (histórico); apenas o status muda.
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setDataHoraCancelamento(agora);
        pedido.setResponsavelCancelamento(usuario);
        pedido.setJustificativaCancelamento(limitar(justificativa.trim(), 300));
        pedido.getHistorico().add(new HistoricoStatus(atual, StatusPedido.CANCELADO, agora, usuario));

        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> campos = new ArrayList<>();
        AuditoriaService.comparar(campos, "status", atual, StatusPedido.CANCELADO);
        AuditoriaService.comparar(campos, "dataHoraCancelamento", null, agora);
        AuditoriaService.comparar(campos, "responsavelCancelamento", null, usuario);
        auditoriaService.registrar(TipoOperacaoAuditoria.CANCELAMENTO_PEDIDO, AuditoriaService.ENTIDADE_PEDIDO,
                salvo.getId(), campos, salvo.getJustificativaCancelamento());
        return salvo;
    }

    // ===================== AUXILIARES =====================

    private static StatusPedido statusAtual(Pedido pedido) {
        return pedido.getStatus() == null ? StatusPedido.RECEBIDO : pedido.getStatus();
    }

    /** ENTREGA só pode ir para SAIU_PARA_ENTREGA; RETIRADA só para RETIRADO. */
    private static boolean compativelComModalidade(Pedido pedido, StatusPedido destino) {
        ModalidadeEntrega modalidade = pedido.getModalidade() == null ? ModalidadeEntrega.ENTREGA : pedido.getModalidade();
        if (destino == StatusPedido.SAIU_PARA_ENTREGA) {
            return modalidade == ModalidadeEntrega.ENTREGA;
        }
        if (destino == StatusPedido.RETIRADO) {
            return modalidade == ModalidadeEntrega.RETIRADA;
        }
        return true;
    }

    private static String descreverItens(List<ItemPedido> itens) {
        return itens.stream()
                .map(i -> i.getQuantidade() + "x " + i.getNomeProduto() + " (R$ " + i.getPrecoUnitario() + ")")
                .collect(Collectors.joining("; "));
    }

    private static String valorObrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new RegraNegocioException(mensagem);
        }
        return valor;
    }

    private static String limitar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
