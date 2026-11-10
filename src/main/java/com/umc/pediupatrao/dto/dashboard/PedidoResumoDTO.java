package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** RF03 - Resumo de um pedido para a lista de "pedidos recentes" do dashboard. */
public record PedidoResumoDTO(String id, String codigo, String cliente, String status, String statusDescricao,
                              String modalidade, int itens, BigDecimal valorTotal,
                              LocalDateTime dataHoraEntrada, String responsavelEntrada) {
}
