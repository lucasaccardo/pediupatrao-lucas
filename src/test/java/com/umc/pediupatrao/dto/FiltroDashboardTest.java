package com.umc.pediupatrao.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.ModalidadeEntrega;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RequisicaoInvalidaException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** RF05 - Validação e valores padrão dos filtros do dashboard. */
class FiltroDashboardTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    @Test
    void semDatasUsaUltimos30Dias() {
        FiltroDashboard f = FiltroDashboard.de(null, null, null, null, HOJE);
        assertEquals(LocalDate.of(2026, 10, 12), f.inicio());
        assertEquals(HOJE, f.fim());
        assertEquals(30, f.dias());
        assertNull(f.status());
        assertNull(f.modalidade());
    }

    @Test
    void aceitaStatusEModalidadeEmMinusculas() {
        FiltroDashboard f = FiltroDashboard.de("2026-11-01", "2026-11-10", "finalizado", "retirada", HOJE);
        assertEquals(StatusPedido.FINALIZADO, f.status());
        assertEquals(ModalidadeEntrega.RETIRADA, f.modalidade());
    }

    @Test
    void dataEmFormatoErradoGera400NoCampoCerto() {
        RequisicaoInvalidaException e = assertThrows(RequisicaoInvalidaException.class,
                () -> FiltroDashboard.de("10/11/2026", null, null, null, HOJE));
        assertEquals("inicio", e.getCampo());
    }

    @Test
    void inicioMaiorQueFimEhRecusado() {
        assertThrows(RequisicaoInvalidaException.class,
                () -> FiltroDashboard.de("2026-11-10", "2026-11-01", null, null, HOJE));
    }

    @Test
    void periodoMaiorQue366DiasEhRecusado() {
        assertThrows(RequisicaoInvalidaException.class,
                () -> FiltroDashboard.de("2025-01-01", "2026-11-10", null, null, HOJE));
    }

    @Test
    void statusInexistenteEhRecusado() {
        RequisicaoInvalidaException e = assertThrows(RequisicaoInvalidaException.class,
                () -> FiltroDashboard.de(null, null, "ENTREGUE", null, HOJE));
        assertEquals("status", e.getCampo());
    }

    @Test
    void periodoAnteriorTemOMesmoTamanho() {
        FiltroDashboard f = FiltroDashboard.de("2026-11-01", "2026-11-10", null, null, HOJE);
        FiltroDashboard anterior = f.periodoAnterior();
        assertEquals(LocalDate.of(2026, 10, 22), anterior.inicio());
        assertEquals(LocalDate.of(2026, 10, 31), anterior.fim());
        assertEquals(f.dias(), anterior.dias());
    }
}
