package com.umc.pediupatrao.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tratamento centralizado de erros das APIs REST (/api/**).
 * Devolve JSON padronizado em vez de stack trace.
 *
 * Observação: AccessDeniedException NÃO é tratada aqui de propósito; ela segue
 * para o Spring Security, que responde 403 (ver SecurityConfig).
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Map<String, Object>> regraNegocio(RegraNegocioException ex) {
        return corpo(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> naoEncontrado(RecursoNaoEncontradoException ex) {
        return corpo(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Parâmetro inválido (data, status, limite...): 400 com o nome do campo. */
    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> requisicaoInvalida(RequisicaoInvalidaException ex) {
        ResponseEntity<Map<String, Object>> resposta = corpo(HttpStatus.BAD_REQUEST, ex.getMessage());
        resposta.getBody().put("campo", ex.getCampo());
        return resposta;
    }

    /** Banco de dados fora do ar: 503 com mensagem clara, sem stack trace (RF07). */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> bancoIndisponivel(DataAccessException ex) {
        return corpo(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível consultar o banco de dados no momento. Tente novamente em instantes.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> argumentoInvalido(IllegalArgumentException ex) {
        return corpo(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    public static ResponseEntity<Map<String, Object>> corpo(HttpStatus status, String mensagem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("erro", status.getReasonPhrase());
        body.put("mensagem", mensagem);
        return ResponseEntity.status(status).body(body);
    }
}
