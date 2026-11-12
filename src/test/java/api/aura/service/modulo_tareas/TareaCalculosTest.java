package api.aura.service.modulo_tareas;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Cambiar el vencimiento un minuto es un cambio (antes valía un margen de casi dos). */
class TareaCalculosTest {

    @Test
    void las_fechas_se_comparan_al_minuto() {
        LocalDateTime seis = LocalDateTime.of(2026, 10, 16, 18, 0);

        assertThat(TareaCalculos.sonFechasIguales(seis, seis.plusSeconds(59).plusNanos(5_000_000))).isTrue();
        assertThat(TareaCalculos.sonFechasIguales(seis, seis.plusMinutes(1))).isFalse();
        assertThat(TareaCalculos.sonFechasIguales(seis, seis.minusMinutes(1))).isFalse();
        assertThat(TareaCalculos.sonFechasIguales(null, null)).isTrue();
        assertThat(TareaCalculos.sonFechasIguales(seis, null)).isFalse();
    }
}
