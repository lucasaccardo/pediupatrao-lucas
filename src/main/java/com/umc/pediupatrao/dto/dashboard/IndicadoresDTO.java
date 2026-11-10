package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * RF01 - Indicadores gerais de vendas do período filtrado.
 *
 * Regras de cálculo (documentadas em docs/DOCUMENTACAO-DASHBOARD.md):
 * - faturamento: soma do valor total dos pedidos NÃO cancelados;
 * - ticketMedio: faturamento / pedidosValidos;
 * - taxaCancelamentoPercentual: pedidosCancelados / totalPedidos x 100;
 * - tempoMedioAteSaidaMinutos: média entre entrada e saída (só pedidos que já saíram).
 */
public record IndicadoresDTO(
        long totalPedidos,
        long pedidosValidos,
        long pedidosCancelados,
        long pedidosEmAndamento,
        long pedidosFinalizados,
        BigDecimal faturamento,
        BigDecimal ticketMedio,
        BigDecimal totalDescontos,
        long itensVendidos,
        BigDecimal taxaCancelamentoPercentual,
        BigDecimal tempoMedioAteSaidaMinutos,
        ComparativoDTO comparativoPeriodoAnterior) {

    /**
     * Comparação com o período anterior de mesmo tamanho (tendência).
     * Os percentuais são null quando o período anterior não tem base (zero).
     */
    public record ComparativoDTO(
            java.time.LocalDate inicio,
            java.time.LocalDate fim,
            BigDecimal faturamento,
            long pedidosValidos,
            BigDecimal ticketMedio,
            BigDecimal variacaoFaturamentoPercentual,
            BigDecimal variacaoPedidosPercentual,
            BigDecimal variacaoTicketMedioPercentual) {
    }
}
