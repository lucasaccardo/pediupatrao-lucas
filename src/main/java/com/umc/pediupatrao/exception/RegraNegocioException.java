package com.umc.pediupatrao.exception;

/**
 * Lançada quando uma regra de negócio é violada (ex.: transição de status
 * inválida, desconto acima de 20%, cancelamento sem justificativa).
 * Resulta em HTTP 422 nas APIs e em mensagem de erro nas telas.
 */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
