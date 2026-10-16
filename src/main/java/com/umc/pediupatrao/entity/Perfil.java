package com.umc.pediupatrao.entity;

import java.util.Locale;

/**
 * RF01 - Perfis de acesso do sistema.
 *
 * Usar um enum (em vez de String livre) garante que apenas estes três valores
 * sejam aceitos. Qualquer outro valor enviado por formulário ou API é rejeitado
 * no backend, evitando atribuição indevida de privilégios.
 */
public enum Perfil {

    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Nome da authority usada pelo Spring Security (ex.: ROLE_GERENTE). */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * Converte texto em Perfil de forma estrita.
     *
     * @throws IllegalArgumentException se o valor não for um perfil válido
     */
    public static Perfil deTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Perfil não informado.");
        }
        try {
            return Perfil.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Perfil inválido: " + valor);
        }
    }
}
