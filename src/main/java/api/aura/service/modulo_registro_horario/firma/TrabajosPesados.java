package api.aura.service.modulo_registro_horario.firma;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Generar un documento o extraer una firma son trabajos pesados y el servidor tiene poca memoria: se hacen de dos en
 * dos como mucho (el resto espera su turno), igual que en el Registro Horario anterior.
 */
@Component
public class TrabajosPesados {

    static final int SIMULTANEOS = 2;

    private final Semaphore turnos = new Semaphore(SIMULTANEOS, true);

    public <T> T ejecutar(Supplier<T> trabajo) {
        turnos.acquireUninterruptibly();
        try {
            return trabajo.get();
        } finally {
            turnos.release();
        }
    }
}
