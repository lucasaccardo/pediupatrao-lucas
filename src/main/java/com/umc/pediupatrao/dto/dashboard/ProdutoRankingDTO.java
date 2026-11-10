package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * RF04 - Linha do ranking de produtos mais vendidos.
 *
 * @param participacaoPercentual fatia do produto no total do critério escolhido
 *                               (quantidade ou faturamento) dentro do período
 */
public record ProdutoRankingDTO(int posicao, String produtoId, String nome, long quantidade,
                                BigDecimal faturamento, BigDecimal participacaoPercentual) {
}
