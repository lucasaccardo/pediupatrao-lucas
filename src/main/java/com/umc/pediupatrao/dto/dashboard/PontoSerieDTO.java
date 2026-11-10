package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/**
 * RF02 - Um ponto da série de vendas (um dia, uma semana, um mês ou uma hora do dia).
 *
 * @param chave       identificador ordenável (2026-11-10, 2026-W46 = segunda-feira 2026-11-09, 2026-11, 19)
 * @param rotulo      texto pronto para o eixo do gráfico (10/11, Sem. 09/11, nov/2026, 19h)
 * @param pedidos     pedidos válidos (não cancelados)
 * @param cancelados  pedidos cancelados no mesmo intervalo
 * @param faturamento soma do valor total dos pedidos válidos
 */
public record PontoSerieDTO(String chave, String rotulo, long pedidos, long cancelados, BigDecimal faturamento) {
}
