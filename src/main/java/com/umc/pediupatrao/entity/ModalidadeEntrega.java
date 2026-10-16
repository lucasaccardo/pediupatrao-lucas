package com.umc.pediupatrao.entity;

/**
 * Define como o pedido sai da pizzaria. A modalidade decide qual é o estado
 * de saída permitido: ENTREGA -> SAIU_PARA_ENTREGA; RETIRADA -> RETIRADO.
 */
public enum ModalidadeEntrega {
    ENTREGA("Entrega"),
    RETIRADA("Retirada no balcão");

    private final String descricao;

    ModalidadeEntrega(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
