package com.umc.pediupatrao.dto.dashboard;

import com.umc.pediupatrao.exception.RequisicaoInvalidaException;
import java.util.Locale;

/** RF04 - Critério de ordenação do ranking de produtos. */
public enum CriterioRanking {
    QUANTIDADE("Quantidade vendida"),
    FATURAMENTO("Faturamento");

    private final String descricao;

    CriterioRanking(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public static CriterioRanking deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return QUANTIDADE;
        }
        try {
            return valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("criterio", "Critério inválido: " + valor + ". Valores aceitos: QUANTIDADE, FATURAMENTO.");
        }
    }
}
