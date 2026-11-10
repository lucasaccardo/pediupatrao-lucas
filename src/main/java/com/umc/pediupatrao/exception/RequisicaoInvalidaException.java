package com.umc.pediupatrao.exception;

/**
 * Lançada quando um parâmetro enviado na requisição é inválido
 * (ex.: data em formato errado, data inicial maior que a final, status inexistente).
 * Resulta em HTTP 400 com o nome do campo e a mensagem.
 */
public class RequisicaoInvalidaException extends RuntimeException {

    private final String campo;

    public RequisicaoInvalidaException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
