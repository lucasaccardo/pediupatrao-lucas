package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.exception.RequisicaoInvalidaException;
import java.util.Locale;

/** RF02 - Como a série de vendas é agrupada no tempo. */
public enum Agrupamento {
    DIA("Por dia"),
    SEMANA("Por semana"),
    MES("Por mês");

    private final String descricao;

    Agrupamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public static Agrupamento deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return DIA;
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("agrupamento", "Agrupamento inválido: " + valor + ". Valores aceitos: DIA, SEMANA, MES.");
        }
    }
}
