package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Envelope padrão de TODAS as respostas do dashboard.
 *
 * Além dos dados, devolve os filtros que foram realmente aplicados (já com os
 * valores padrão preenchidos) e o momento em que os dados foram gerados.
 * Assim a tela mostra "Período: 12/10 a 10/11" e "Atualizado às 16:45:03"
 * sem precisar calcular nada (RF07).
 */
public record RespostaDashboard<T>(FiltrosAplicados filtros, LocalDateTime geradoEm, T dados) {

    public record FiltrosAplicados(LocalDate inicio, LocalDate fim, long dias, String status, String modalidade) {
    }

    public static <T> RespostaDashboard<T> de(FiltroDashboard f, T dados) {
        FiltrosAplicados filtros = new FiltrosAplicados(f.inicio(), f.fim(), f.dias(),
                f.status() == null ? null : f.status().name(),
                f.modalidade() == null ? null : f.modalidade().name());
        return new RespostaDashboard<>(filtros, LocalDateTime.now(), dados);
    }
}
