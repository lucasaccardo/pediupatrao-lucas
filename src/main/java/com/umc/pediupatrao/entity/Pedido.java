package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * RF02 / RF03 / RF04 - Pedido evoluído.
 *
 * Os valores (subtotal, desconto e total) NÃO têm setters públicos usados pelos
 * controllers: eles só são calculados pelo método {@link #recalcularValores()},
 * chamado pela camada de serviço. Assim uma requisição HTTP não consegue
 * definir o total do pedido.
 */
@Document(collection = "pedidos")
public class Pedido {

    /** Limite de desconto definido pela regra de negócio (RF04). */
    public static final BigDecimal DESCONTO_MAXIMO_PERCENTUAL = new BigDecimal("20");

    @Id
    private String id;

    // ---------- Cliente ----------
    private String clienteId;
    private String clienteNome;

    // ---------- Itens e valores (RF02) ----------
    private List<ItemPedido> itens = new ArrayList<>();
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal percentualDesconto = BigDecimal.ZERO;
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    private BigDecimal valorTotal = BigDecimal.ZERO;
    private String descontoAplicadoPor;
    private LocalDateTime dataHoraDesconto;
    private String motivoDesconto;

    private ModalidadeEntrega modalidade = ModalidadeEntrega.ENTREGA;
    private String observacao;

    // ---------- Status e controle de entrada/saída (RF03) ----------
    private StatusPedido status = StatusPedido.RECEBIDO;

    private LocalDateTime dataHoraEntrada;
    private String responsavelEntrada;

    private LocalDateTime dataHoraSaida;
    private String responsavelSaida;

    private LocalDateTime dataHoraFinalizacao;
    private String responsavelFinalizacao;

    // ---------- Cancelamento (RF04) ----------
    private LocalDateTime dataHoraCancelamento;
    private String responsavelCancelamento;
    private String justificativaCancelamento;

    private List<HistoricoStatus> historico = new ArrayList<>();

    // ---------- Campos da versão inicial (mantidos por compatibilidade) ----------
    private String tipoPizza;
    private String sabor;

    /**
     * Recalcula subtotal, desconto e total a partir dos itens e do percentual
     * de desconto autorizado. Toda a matemática fica no servidor.
     */
    public void recalcularValores() {
        BigDecimal soma = BigDecimal.ZERO;
        for (ItemPedido item : itens) {
            soma = soma.add(item.getSubtotal());
        }
        this.subtotal = soma.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal percentual = percentualDesconto == null ? BigDecimal.ZERO : percentualDesconto;
        this.valorDesconto = subtotal.multiply(percentual)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        this.valorTotal = subtotal.subtract(valorDesconto);
    }

    /** Número curto para exibir nas telas (últimos 6 caracteres do id). */
    public String getCodigo() {
        if (id == null) {
            return "-";
        }
        return id.length() > 6 ? id.substring(id.length() - 6).toUpperCase() : id.toUpperCase();
    }

    public int getQuantidadeItens() {
        return itens == null ? 0 : itens.stream().mapToInt(ItemPedido::getQuantidade).sum();
    }

    // ===== Getters =====
    public String getId() { return id; }
    public String getClienteId() { return clienteId; }
    public String getClienteNome() { return clienteNome; }
    public List<ItemPedido> getItens() { return itens; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getPercentualDesconto() { return percentualDesconto; }
    public BigDecimal getValorDesconto() { return valorDesconto; }
    public BigDecimal getValorTotal() { return valorTotal; }
    public String getDescontoAplicadoPor() { return descontoAplicadoPor; }
    public LocalDateTime getDataHoraDesconto() { return dataHoraDesconto; }
    public String getMotivoDesconto() { return motivoDesconto; }
    public ModalidadeEntrega getModalidade() { return modalidade; }
    public String getObservacao() { return observacao; }
    public StatusPedido getStatus() { return status; }
    public LocalDateTime getDataHoraEntrada() { return dataHoraEntrada; }
    public String getResponsavelEntrada() { return responsavelEntrada; }
    public LocalDateTime getDataHoraSaida() { return dataHoraSaida; }
    public String getResponsavelSaida() { return responsavelSaida; }
    public LocalDateTime getDataHoraFinalizacao() { return dataHoraFinalizacao; }
    public String getResponsavelFinalizacao() { return responsavelFinalizacao; }
    public LocalDateTime getDataHoraCancelamento() { return dataHoraCancelamento; }
    public String getResponsavelCancelamento() { return responsavelCancelamento; }
    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public List<HistoricoStatus> getHistorico() { return historico; }
    public String getTipoPizza() { return tipoPizza; }
    public String getSabor() { return sabor; }

    // ===== Setters usados SOMENTE pela camada de serviço =====
    public void setId(String id) { this.id = id; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }
    public void setPercentualDesconto(BigDecimal percentualDesconto) { this.percentualDesconto = percentualDesconto; }
    public void setDescontoAplicadoPor(String descontoAplicadoPor) { this.descontoAplicadoPor = descontoAplicadoPor; }
    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) { this.dataHoraDesconto = dataHoraDesconto; }
    public void setMotivoDesconto(String motivoDesconto) { this.motivoDesconto = motivoDesconto; }
    public void setModalidade(ModalidadeEntrega modalidade) { this.modalidade = modalidade; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public void setStatus(StatusPedido status) { this.status = status; }
    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) { this.dataHoraEntrada = dataHoraEntrada; }
    public void setResponsavelEntrada(String responsavelEntrada) { this.responsavelEntrada = responsavelEntrada; }
    public void setDataHoraSaida(LocalDateTime dataHoraSaida) { this.dataHoraSaida = dataHoraSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }
    public void setDataHoraFinalizacao(LocalDateTime dataHoraFinalizacao) { this.dataHoraFinalizacao = dataHoraFinalizacao; }
    public void setResponsavelFinalizacao(String responsavelFinalizacao) { this.responsavelFinalizacao = responsavelFinalizacao; }
    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) { this.dataHoraCancelamento = dataHoraCancelamento; }
    public void setResponsavelCancelamento(String responsavelCancelamento) { this.responsavelCancelamento = responsavelCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }
    public void setHistorico(List<HistoricoStatus> historico) { this.historico = historico; }
    public void setTipoPizza(String tipoPizza) { this.tipoPizza = tipoPizza; }
    public void setSabor(String sabor) { this.sabor = sabor; }
}
