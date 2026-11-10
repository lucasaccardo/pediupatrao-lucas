package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

/**
 * RF05 - Valores aceitos pelos filtros, para a tela montar os campos a partir
 * da API (e não de listas fixas no JavaScript).
 */
public record OpcoesFiltroDTO(List<Opcao> status, List<Opcao> modalidades, List<Opcao> agrupamentos,
                              List<Opcao> criteriosRanking, int periodoPadraoDias, int periodoMaximoDias) {

    public record Opcao(String valor, String descricao) {
    }
}
