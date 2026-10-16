package com.umc.pediupatrao.entity;

/**
 * RF05 - Tipos de operação registrados na auditoria.
 */
public enum TipoOperacaoAuditoria {
    INCLUSAO_PEDIDO("Inclusão de pedido"),
    ALTERACAO_STATUS_PEDIDO("Alteração de status do pedido"),
    DESCONTO_PEDIDO("Desconto no pedido"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),
    INCLUSAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),
    INCLUSAO_USUARIO("Inclusão de usuário"),
    ALTERACAO_USUARIO("Alteração de usuário"),
    EXCLUSAO_USUARIO("Exclusão de usuário");

    private final String descricao;

    TipoOperacaoAuditoria(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
