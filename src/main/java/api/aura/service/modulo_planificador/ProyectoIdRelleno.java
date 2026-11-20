package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Estudio;
import api.aura.entity.modulo_planificador.pl_Meta;
import api.aura.entity.modulo_planificador.pl_Presupuesto;
import api.aura.repository.modulo_planificador.pl_EstudioRepository;
import api.aura.repository.modulo_planificador.pl_MetaRepository;
import api.aura.repository.modulo_planificador.pl_PresupuestoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Una sola vez por base de datos: da a los presupuestos y estudios que ya había su {@code proyecto_id}, el de la propuesta de su cliente
 * y proyecto (la crea si no existe). Usa la misma comparación de nombres que el resto de la aplicación («Cliente · Proyecto» sin
 * mayúsculas, tildes ni «v2» / «(copia)» final), así que lo que ya se veía agrupado sigue agrupado. No cambia ningún otro dato ni la
 * fecha de modificación. Corre después de los scripts de BBDD (que crean la columna); si falla, se anota y se reintenta al siguiente
 * arranque. Se enciende con {@code planificador.proyecto-id-relleno=true}.
 */
@Component
@ConditionalOnProperty(name = "planificador.proyecto-id-relleno", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ProyectoIdRelleno implements ApplicationRunner {

    public static final String CLAVE = "proyecto_id_relleno_v1";

    private final pl_PresupuestoRepository presupuestos;
    private final pl_EstudioRepository estudios;
    private final pl_MetaRepository meta;
    private final PresupuestoService presupuestoService;
    private final EstudioService estudioService;
    private final PlatformTransactionManager transacciones;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Todo en una transacción: o se rellena entero o nada (y se reintenta en el siguiente arranque)
            new TransactionTemplate(transacciones).executeWithoutResult(estado -> rellenar());
        } catch (Exception e) {
            LoggerFactory.getLogger("planificador").error("No se pudo rellenar el proyecto de presupuestos y estudios: {}", e.toString());
        }
    }

    /** Devuelve cuántos presupuestos y estudios ha asignado a una propuesta: {presupuestos, estudios}. */
    public int[] rellenar() {
        if (meta.existsById(CLAVE)) {
            return new int[]{0, 0};
        }
        int dePresupuestos = 0;
        for (pl_Presupuesto b : presupuestos.findAll()) {
            if (b.getProyectoId().isEmpty()) {
                presupuestoService.asignarProyecto(b);
                if (!b.getProyectoId().isEmpty()) {
                    presupuestos.save(b);
                    dePresupuestos++;
                }
            }
        }
        int deEstudios = 0;
        for (pl_Estudio e : estudios.findAll()) {
            if (e.getProyectoId().isEmpty()) {
                estudioService.asignarProyecto(e);
                if (!e.getProyectoId().isEmpty()) {
                    estudios.save(e);
                    deEstudios++;
                }
            }
        }
        meta.save(new pl_Meta(CLAVE, "ok"));
        LoggerFactory.getLogger("planificador").info("Propuesta asignada a {} presupuestos y {} estudios", dePresupuestos, deEstudios);
        return new int[]{dePresupuestos, deEstudios};
    }
}
