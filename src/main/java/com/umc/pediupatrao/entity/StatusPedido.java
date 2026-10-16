package com.umc.pediupatrao.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * RF03 - Máquina de estados do pedido.
 *
 * Fluxo obrigatório:
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> (SAIU_PARA_ENTREGA | RETIRADO) -> FINALIZADO
 *
 * CANCELADO é um estado excepcional: só é alcançável a partir de RECEBIDO,
 * EM_PREPARACAO ou PRONTO (antes da saída) e somente pelo GERENTE.
 * Depois de CANCELADO ou FINALIZADO nenhuma transição é aceita.
 */
public enum StatusPedido {

    RECEBIDO("Recebido", "bg-secondary"),
    EM_PREPARACAO("Em preparação", "bg-warning text-dark"),
    PRONTO("Pronto", "bg-info text-dark"),
    SAIU_PARA_ENTREGA("Saiu para entrega", "bg-primary"),
    RETIRADO("Retirado", "bg-primary"),
    FINALIZADO("Finalizado", "bg-success"),
    CANCELADO("Cancelado", "bg-danger");

    private final String descricao;
    private final String classeCss;

    StatusPedido(String descricao, String classeCss) {
        this.descricao = descricao;
        this.classeCss = classeCss;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getClasseCss() {
        return classeCss;
    }

    /** Próximos estados do fluxo normal (sem considerar o cancelamento). */
    public Set<StatusPedido> proximosEstados() {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> Collections.emptySet();
        };
    }

    /** Verifica se a transição pertence ao fluxo normal. */
    public boolean podeAvancarPara(StatusPedido destino) {
        return destino != null && proximosEstados().contains(destino);
    }

    /** Estados que representam a saída do pedido (exigem data/hora e responsável). */
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Desconto e cancelamento só são permitidos antes da saída. */
    public boolean permiteDescontoOuCancelamento() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    /** Estados finais não aceitam nenhuma alteração. */
    public boolean isFinal() {
        return this == FINALIZADO || this == CANCELADO;
    }

    /**
     * Converte textos (inclusive nomes antigos usados na versão inicial do
     * projeto, como EM_PREPARO e ENTREGUE) para o enum atual.
     */
    public static StatusPedido deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return RECEBIDO;
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (normalizado) {
            case "PENDENTE", "NOVO" -> RECEBIDO;
            case "EM_PREPARO", "PREPARANDO" -> EM_PREPARACAO;
            case "SAIU", "EM_ENTREGA", "SAIU_ENTREGA" -> SAIU_PARA_ENTREGA;
            case "ENTREGUE", "CONCLUIDO" -> FINALIZADO;
            default -> {
                try {
                    yield StatusPedido.valueOf(normalizado);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Status inválido: " + valor);
                }
            }
        };
    }
}
