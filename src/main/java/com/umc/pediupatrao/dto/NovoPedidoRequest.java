package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.ModalidadeEntrega;
import java.util.List;

/**
 * RF02 - Dados aceitos para registrar um pedido.
 *
 * Repare que NÃO existem campos de preço, desconto, total, status, responsável
 * ou data. Mesmo que alguém envie "valorTotal" no JSON, ele é ignorado:
 * tudo isso é definido pelo servidor (PedidoService).
 */
public record NovoPedidoRequest(String clienteId,
                                ModalidadeEntrega modalidade,
                                String observacao,
                                List<ItemRequest> itens) {

    public record ItemRequest(String produtoId, Integer quantidade) {
    }
}
