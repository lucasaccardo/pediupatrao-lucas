package com.umc.pediupatrao.entity;

/**
 * RF05 - Um campo alterado: nome, valor anterior e valor novo.
 * Classe imutável (sem setters): depois de criada não pode ser modificada.
 */
public class AlteracaoCampo {

    private final String campo;
    private final String valorAnterior;
    private final String valorNovo;

    public AlteracaoCampo(String campo, String valorAnterior, String valorNovo) {
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
    }

    public String getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNovo() { return valorNovo; }
}
