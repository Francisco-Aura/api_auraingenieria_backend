package api.aura.service.modulo_planificador;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * Temporizador diario de los avisos de vencimiento (sustituye al temporizador del servidor del Planificador actual). Apagado por
 * defecto: se activa con {@code planificador.avisos.programado=true}. Con {@code planificador.avisos.solo-marcar=true} no envía
 * nada y da por avisado lo que ya vence (para activarlo sin ruido). Hora: {@code planificador.avisos.cron} (por defecto 7:00).
 * Con varias instancias del servidor, activarlo en una sola.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "planificador.avisos.programado", havingValue = "true")
@RequiredArgsConstructor
public class VencimientosProgramador {

    private static final Logger log = LoggerFactory.getLogger("planificador");

    private final VencimientosService vencimientos;
    @Value("${planificador.avisos.solo-marcar:false}")
    private boolean soloMarcar;

    @Scheduled(cron = "${planificador.avisos.cron:0 0 7 * * *}")
    public void diario() {
        try {
            Map<String, Object> resumen = vencimientos.ejecutar(LocalDate.now(), soloMarcar);
            log.info("Avisos de vencimiento {}: {}", soloMarcar ? "(marcado sin enviar)" : "", resumen);
        } catch (RuntimeException e) {
            log.error("No se pudieron ejecutar los avisos de vencimiento", e);
        }
    }
}
