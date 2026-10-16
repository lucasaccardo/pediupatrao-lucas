package com.umc.pediupatrao.exception;

/** Lançada quando o registro procurado não existe (HTTP 404 nas APIs). */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
