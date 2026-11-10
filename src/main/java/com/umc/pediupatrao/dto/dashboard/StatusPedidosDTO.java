package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * RF03 - Situação dos pedidos: quantidade em cada status (todos os status
 * aparecem, inclusive com zero, para a tela ter sempre as mesmas linhas).
 */
public record StatusPedidosDTO(long total, long emAndamento, List<ItemStatus> porStatus) {

    public record ItemStatus(String status, String descricao, long quantidade, BigDecimal percentual) {
    }
}
