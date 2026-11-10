package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.entity.ModalidadeEntrega;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RequisicaoInvalidaException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * RF05 - Filtros de consulta do dashboard.
 *
 * Todos os endpoints do dashboard recebem os mesmos 4 parâmetros opcionais:
 * inicio, fim (AAAA-MM-DD), status e modalidade. A validação fica aqui, num
 * único lugar, para que todos os endpoints respondam do mesmo jeito.
 *
 * Regras:
 * - sem datas: últimos 30 dias (hoje incluído);
 * - só "fim": 30 dias terminando em "fim"; só "inicio": de "inicio" até hoje;
 * - inicio não pode ser maior que fim; período máximo de 366 dias.
 */
public record FiltroDashboard(LocalDate inicio, LocalDate fim, StatusPedido status, ModalidadeEntrega modalidade) {

    public static final int DIAS_PADRAO = 30;
    public static final int MAXIMO_DIAS = 366;

    public static FiltroDashboard de(String inicio, String fim, String status, String modalidade) {
        return de(inicio, fim, status, modalidade, LocalDate.now());
    }

    /** Versão com "hoje" explícito, usada nos testes para o resultado não depender do relógio. */
    public static FiltroDashboard de(String inicio, String fim, String status, String modalidade, LocalDate hoje) {
        LocalDate dataFim = lerData(fim, "fim");
        LocalDate dataInicio = lerData(inicio, "inicio");

        if (dataFim == null) {
            dataFim = (dataInicio != null && dataInicio.isAfter(hoje)) ? dataInicio : hoje;
        }
        if (dataInicio == null) {
            dataInicio = dataFim.minusDays(DIAS_PADRAO - 1L);
        }
        if (dataInicio.isAfter(dataFim)) {
            throw new RequisicaoInvalidaException("inicio", "A data inicial não pode ser maior que a data final.");
        }
        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        if (dias > MAXIMO_DIAS) {
            throw new RequisicaoInvalidaException("fim", "O período máximo de consulta é de " + MAXIMO_DIAS + " dias.");
        }
        return new FiltroDashboard(dataInicio, dataFim, lerStatus(status), lerModalidade(modalidade));
    }

    /** Início do período (00:00 do dia inicial). */
    public LocalDateTime inicioDataHora() {
        return inicio.atStartOfDay();
    }

    /** Fim EXCLUSIVO do período (00:00 do dia seguinte ao dia final). */
    public LocalDateTime fimExclusivo() {
        return fim.plusDays(1).atStartOfDay();
    }

    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    /** Período de mesmo tamanho imediatamente anterior (usado para calcular a variação %). */
    public FiltroDashboard periodoAnterior() {
        LocalDate fimAnterior = inicio.minusDays(1);
        return new FiltroDashboard(fimAnterior.minusDays(dias() - 1), fimAnterior, status, modalidade);
    }

    private static LocalDate lerData(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new RequisicaoInvalidaException(campo, "Data inválida em '" + campo + "': " + valor
                    + ". Use o formato AAAA-MM-DD (ex.: 2026-11-10).");
        }
    }

    private static StatusPedido lerStatus(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return StatusPedido.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String validos = Arrays.stream(StatusPedido.values()).map(Enum::name).collect(Collectors.joining(", "));
            throw new RequisicaoInvalidaException("status", "Status inválido: " + valor + ". Valores aceitos: " + validos + ".");
        }
    }

    private static ModalidadeEntrega lerModalidade(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return ModalidadeEntrega.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("modalidade", "Modalidade inválida: " + valor + ". Valores aceitos: ENTREGA, RETIRADA.");
        }
    }
}
