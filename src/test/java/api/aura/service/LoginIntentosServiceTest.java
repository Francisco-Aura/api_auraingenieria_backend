package api.aura.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginIntentosServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-06T10:00:00Z");

    /** Reloj que se puede adelantar dentro de un mismo test. */
    private static final class MutableClock extends Clock {
        private Instant ahora;
        MutableClock(Instant inicio) { this.ahora = inicio; }
        void avanzar(long segundos) { ahora = ahora.plusSeconds(segundos); }
        @Override public Instant instant() { return ahora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zona) { return this; }
    }

    private static void fallar(LoginIntentosService s, String usuario, String ip, int veces) {
        for (int i = 0; i < veces; i++) s.registrarFallo(usuario, ip);
    }

    @Test
    void las_variantes_que_mariadb_da_por_iguales_cuentan_como_el_mismo_usuario() {
        String base = LoginIntentosService.normalizar("bruno");
        for (String variante : new String[]{" Bruno ", "BRUNO", "Brúno", "\uFF42runo", "bru\u00ADno", "bru\u200Bno", "b r u n o"})
            assertThat(LoginIntentosService.normalizar(variante)).as(variante).isEqualTo(base);
        assertThat(LoginIntentosService.normalizar("Straße")).isEqualTo(LoginIntentosService.normalizar("strasse"));
        // Acotado: un usuario de megas no se guarda entero
        assertThat(LoginIntentosService.normalizar("x".repeat(5_000_000))).hasSize(LoginIntentosService.MAX_LONGITUD_USUARIO);
    }

    @Test
    void bloquea_usuario_e_ip_tras_demasiados_fallos_y_caduca() {
        LoginIntentosService s = new LoginIntentosService(Clock.fixed(T0, ZoneOffset.UTC));
        fallar(s, "ana", "6.6.6.6", LoginIntentosService.MAX_FALLOS - 1);
        assertThat(s.bloqueado("ana", "6.6.6.6")).isFalse();
        assertThat(s.registrarFallo("ANA", "6.6.6.6")).as("avisa al bloquear").isTrue();

        assertThat(s.bloqueado("Ána", "6.6.6.6")).isTrue();
        assertThat(s.bloqueado("ana", "10.0.0.5")).isFalse();

        LoginIntentosService despues = new LoginIntentosService(Clock.fixed(T0.plusSeconds(301), ZoneOffset.UTC));
        assertThat(despues.bloqueado("ana", "6.6.6.6")).isFalse();
    }

    @Test
    void un_ataque_repartido_bloquea_la_cuenta_salvo_en_los_equipos_del_usuario() {
        LoginIntentosService s = new LoginIntentosService(Clock.fixed(T0, ZoneOffset.UTC));
        s.registrarExito("ana", "192.168.1.20");
        for (int i = 0; i < LoginIntentosService.MAX_FALLOS_USUARIO; i++) s.registrarFallo("ana", "10.9.0." + i);

        assertThat(s.bloqueado("ana", "10.9.0.200")).isTrue();
        assertThat(s.bloqueado("ana", "192.168.1.20")).as("su propio equipo").isFalse();
    }

    @Test
    void el_mapa_no_crece_sin_limite_y_no_suelta_los_bloqueos_activos() {
        LoginIntentosService s = new LoginIntentosService(Clock.fixed(T0, ZoneOffset.UTC));
        fallar(s, "victima", "1.1.1.1", LoginIntentosService.MAX_FALLOS);
        for (int i = 0; i < LoginIntentosService.MAX_ENTRADAS; i++) s.registrarFallo("u" + i, "ip");

        assertThat(s.entradas()).isLessThanOrEqualTo(LoginIntentosService.MAX_ENTRADAS);
        assertThat(s.bloqueado("victima", "1.1.1.1")).isTrue();
    }

    @Test
    void como_mucho_cuatro_logins_a_la_vez() {
        LoginIntentosService s = new LoginIntentosService();
        for (int i = 0; i < LoginIntentosService.MAX_SIMULTANEOS; i++) assertThat(s.ocuparTurno()).isTrue();
        assertThat(s.ocuparTurno()).isFalse();
        s.liberarTurno();
        assertThat(s.ocuparTurno()).isTrue();
    }

    @Test
    void como_mucho_veinte_fallos_por_minuto_desde_una_ip() {
        Instant t0 = Instant.parse("2026-10-07T10:00:00Z");
        MutableClock reloj = new MutableClock(t0);
        LoginIntentosService s = new LoginIntentosService(reloj);
        for (int i = 0; i < LoginIntentosService.MAX_FALLOS_POR_MINUTO_IP; i++) {
            assertThat(s.permitirDesdeIp("10.0.0.9")).isTrue();
            s.registrarFallo("usuario" + i, "10.0.0.9");
        }
        assertThat(s.permitirDesdeIp("10.0.0.9")).isFalse();
        assertThat(s.permitirDesdeIp("10.0.0.10")).as("otro equipo").isTrue();

        reloj.avanzar(60);
        assertThat(s.permitirDesdeIp("10.0.0.9")).as("pasado el minuto").isTrue();
    }

    @Test
    void los_aciertos_desde_una_misma_ip_nunca_bloquean() {
        LoginIntentosService s = new LoginIntentosService(Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC));
        for (int i = 0; i < 200; i++) {
            assertThat(s.permitirDesdeIp("10.0.0.9")).isTrue();
            s.registrarExito("usuario" + i, "10.0.0.9");
        }
    }
}
