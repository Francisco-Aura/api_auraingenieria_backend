package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.CambioEstadoRequest;
import api.aura.dto.modulo_tareas.TiempoDTO;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.entity.modulo_tareas.mt_TareaTramo;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import api.aura.repository.modulo_tareas.mt_TareaTramoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Control de tiempo por tarea. Un usuario solo tiene un tramo corriendo a la vez: empezar otra tarea cierra la anterior en la
 * misma transacción. No toca el contador {@code duracionActivaSegundos} de la tarea.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TareaTiempoService {

    /** Un tramo abierto más de estas horas se da por olvidado y se cierra al llegar a ellas. */
    static final long MAX_HORAS_TRAMO = 12;

    /** Rango máximo que admite el resumen. */
    static final long MAX_DIAS_RESUMEN = 366;

    private final mt_TareaTramoRepository tramoRepository;
    private final mt_TareaRepository tareaRepository;
    private final mt_TareaEstadoRepository estadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TareaService tareaService;

    /** Empieza a contar en la tarea, parando la que corriera. Si ya corre en esa misma tarea no hace nada. */
    @Transactional
    public TiempoDTO.Estado iniciar(Long tareaId, Integer usuarioId) {
        LocalDateTime ahora = LocalDateTime.now();
        tareaRepository.bloquear(tareaId);
        mt_Tarea tarea = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));
        if (tarea.getUsuarioAsignado() == null || !usuarioId.equals(tarea.getUsuarioAsignado().getId())) {
            throw new SecurityException("Solo se puede controlar el tiempo de las tareas asignadas a uno mismo");
        }
        if (tarea.getEstado() != null && Boolean.TRUE.equals(tarea.getEstado().getEstadoFinal())) {
            throw new IllegalStateException("La tarea ya está completada");
        }

        Optional<mt_TareaTramo> abierto = tramoRepository.buscarAbiertoBloqueando(usuarioId);
        if (abierto.isPresent()) {
            mt_TareaTramo actual = abierto.get();
            if (!terminado(actual, ahora) && actual.getTarea().getId().equals(tareaId)) {
                return estado(usuarioId, ahora);
            }
            cerrar(actual, ahora, mt_TareaTramo.CIERRE_CAMBIO_TAREA);
            tramoRepository.flush();
        }

        // Trabajar en una tarea la pone en curso (la misma regla que «Reanudar»)
        if (tarea.getEstado() == null || !Boolean.TRUE.equals(tarea.getEstado().getCuentaTiempo())) {
            mt_TareaEstado enCurso = estadoRepository.findByCuentaTiempoTrueAndEstadoFinalFalseOrderByOrdenAsc().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No hay un estado «en progreso» configurado"));
            CambioEstadoRequest cambio = new CambioEstadoRequest();
            cambio.setEstadoId(enCurso.getId());
            cambio.setUsuarioAccionId(usuarioId);
            tareaService.cambiarEstado(tareaId, cambio);
        }

        tramoRepository.save(mt_TareaTramo.builder()
                .tarea(tarea)
                .usuario(usuarioRepository.getReferenceById(usuarioId))
                .inicio(ahora)
                .build());
        tramoRepository.flush();
        return estado(usuarioId, ahora);
    }

    /** Para el cronómetro. Sin nada corriendo no hace nada. */
    @Transactional
    public TiempoDTO.Estado pausar(Integer usuarioId) {
        return pausar(usuarioId, 0);
    }

    /**
     * Para el cronómetro por inactividad: el tramo termina {@code inactivoSegundos} antes de ahora (cuando el usuario dejó
     * de trabajar), sin pasar de su inicio. Con 0 es una pausa normal.
     */
    @Transactional
    public TiempoDTO.Estado pausar(Integer usuarioId, long inactivoSegundos) {
        LocalDateTime ahora = LocalDateTime.now();
        long inactivo = Math.max(0, inactivoSegundos);
        tramoRepository.buscarAbiertoBloqueando(usuarioId).ifPresent(t -> {
            if (inactivo == 0) {
                cerrar(t, ahora, mt_TareaTramo.CIERRE_MANUAL);
                return;
            }
            LocalDateTime fin = ahora.minusSeconds(inactivo);
            if (fin.isBefore(t.getInicio())) {
                fin = t.getInicio();
            }
            if (terminado(t, fin)) {
                cerrarPorOlvido(t, ahora);
            } else {
                t.setFin(fin);
                t.setCierre(mt_TareaTramo.CIERRE_INACTIVIDAD);
                tramoRepository.save(t);
            }
        });
        tramoRepository.flush();
        return estado(usuarioId, ahora);
    }

    /** Vuelve a la última tarea con la que se trabajó. Si ya hay una corriendo no hace nada. */
    @Transactional
    public TiempoDTO.Estado reanudar(Integer usuarioId) {
        TiempoDTO.Estado actual = estado(usuarioId, LocalDateTime.now());
        if (actual.isActivo()) {
            return actual;
        }
        if (actual.getUltimaTareaId() == null) {
            throw new IllegalStateException("No hay ninguna tarea anterior que reanudar");
        }
        return iniciar(actual.getUltimaTareaId(), usuarioId);
    }

    /** Estado actual. Cierra por el camino el tramo olvidado o el de una tarea ya completada. */
    @Transactional
    public TiempoDTO.Estado estadoActual(Integer usuarioId) {
        return estado(usuarioId, LocalDateTime.now());
    }

    /** Tiempo trabajado entre dos fechas (inclusivas), por tarea. */
    public TiempoDTO.Resumen resumen(Integer usuarioId, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango de fechas no es válido");
        }
        if (Duration.between(desde.atStartOfDay(), hasta.atStartOfDay()).toDays() > MAX_DIAS_RESUMEN) {
            throw new IllegalArgumentException("El rango no puede superar " + MAX_DIAS_RESUMEN + " días");
        }
        LocalDateTime ini = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        LocalDateTime ahora = LocalDateTime.now();
        Map<Long, long[]> porTarea = new LinkedHashMap<>();
        Map<Long, String> titulos = new LinkedHashMap<>();
        Map<LocalDate, Long> porDia = new TreeMap<>();
        long total = 0;
        for (mt_TareaTramo t : tramoRepository.solapados(usuarioId, ini, fin)) {
            long seg = segundosEn(t, ini, fin, ahora);
            if (seg <= 0) {
                continue;
            }
            for (LocalDate d = t.getInicio().toLocalDate().isAfter(desde) ? t.getInicio().toLocalDate() : desde;
                 !d.isAfter(hasta); d = d.plusDays(1)) {
                long segDia = segundosEn(t, d.atStartOfDay(), d.plusDays(1).atStartOfDay(), ahora);
                if (segDia > 0) {
                    porDia.merge(d, segDia, Long::sum);
                } else if (d.atStartOfDay().isAfter(t.getFin() != null ? t.getFin() : ahora)) {
                    break;
                }
            }
            total += seg;
            porTarea.computeIfAbsent(t.getTarea().getId(), k -> new long[1])[0] += seg;
            titulos.put(t.getTarea().getId(), t.getTarea().getTitulo());
        }
        List<TiempoDTO.PorTarea> tareas = porTarea.entrySet().stream()
                .map(e -> new TiempoDTO.PorTarea(e.getKey(), titulos.get(e.getKey()), e.getValue()[0]))
                .sorted((a, b) -> Long.compare(b.getSegundos(), a.getSegundos()))
                .toList();
        List<TiempoDTO.PorDia> dias = porDia.entrySet().stream()
                .map(e -> new TiempoDTO.PorDia(e.getKey(), e.getValue()))
                .toList();
        return new TiempoDTO.Resumen(usuarioId, ini, fin.minusSeconds(1), total, tareas, dias);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private TiempoDTO.Estado estado(Integer usuarioId, LocalDateTime ahora) {
        Optional<mt_TareaTramo> abierto = tramoRepository.buscarAbierto(usuarioId);
        if (abierto.isPresent() && terminado(abierto.get(), ahora)) {
            cerrarPorOlvido(abierto.get(), ahora);
            abierto = Optional.empty();
        }
        List<mt_TareaTramo> ultimos = tramoRepository.ultimosCerrados(usuarioId, PageRequest.of(0, 1));
        mt_Tarea ultima = ultimos.isEmpty() ? null : ultimos.get(0).getTarea();
        boolean ultimaValida = ultima != null && !completada(ultima);
        Long ultimaId = ultimaValida ? ultima.getId() : null;
        String ultimaTitulo = ultimaValida ? ultima.getTitulo() : null;

        if (abierto.isEmpty()) {
            return new TiempoDTO.Estado(false, null, null, null, 0, 0, ultimaId, ultimaTitulo);
        }
        mt_TareaTramo t = abierto.get();
        LocalDateTime hoy = LocalDate.now().atStartOfDay();
        long hoyTarea = tramoRepository.solapados(usuarioId, hoy, hoy.plusDays(1)).stream()
                .filter(x -> x.getTarea().getId().equals(t.getTarea().getId()))
                .mapToLong(x -> segundosEn(x, hoy, hoy.plusDays(1), ahora))
                .filter(s -> s > 0)
                .sum();
        long enTramo = Math.max(0, Duration.between(t.getInicio(), ahora).getSeconds());
        return new TiempoDTO.Estado(true, t.getTarea().getId(), t.getTarea().getTitulo(), t.getInicio(),
                enTramo, hoyTarea, ultimaId, ultimaTitulo);
    }

    private static boolean completada(mt_Tarea tarea) {
        return tarea.getEstado() != null && Boolean.TRUE.equals(tarea.getEstado().getEstadoFinal());
    }

    /** Hasta cuándo puede llegar un tramo abierto: su máximo, o antes si la tarea se completó. */
    private static LocalDateTime limiteCierre(mt_TareaTramo t) {
        LocalDateTime max = t.getInicio().plusHours(MAX_HORAS_TRAMO);
        mt_Tarea tarea = t.getTarea();
        if (!completada(tarea)) {
            return max;
        }
        LocalDateTime fin = tarea.getFechaFinalizacion();
        if (fin == null || fin.isBefore(t.getInicio())) {
            return t.getInicio();
        }
        return fin.isBefore(max) ? fin : max;
    }

    /** Segundos totales de los tramos de una tarea (el abierto cuenta hasta ahora o hasta su límite). */
    static long segundosTotales(List<mt_TareaTramo> tramos, LocalDateTime ahora) {
        LocalDateTime desde = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime hasta = LocalDateTime.of(2999, 12, 31, 0, 0);
        return tramos.stream()
                .mapToLong(t -> segundosEn(t, desde, hasta, ahora))
                .filter(s -> s > 0)
                .sum();
    }

    /** Un tramo abierto que ya no debería seguir corriendo (olvidado o de una tarea completada). */
    static boolean terminado(mt_TareaTramo t, LocalDateTime ahora) {
        return !limiteCierre(t).isAfter(ahora);
    }

    private void cerrarPorOlvido(mt_TareaTramo t, LocalDateTime ahora) {
        LocalDateTime limite = limiteCierre(t);
        t.setFin(limite.isAfter(ahora) ? ahora : limite);
        t.setCierre(completada(t.getTarea()) ? mt_TareaTramo.CIERRE_COMPLETADA : mt_TareaTramo.CIERRE_CADUCADO);
        tramoRepository.saveAndFlush(t);
    }

    private void cerrar(mt_TareaTramo t, LocalDateTime ahora, String motivo) {
        if (terminado(t, ahora)) {
            cerrarPorOlvido(t, ahora);
            return;
        }
        t.setFin(ahora);
        t.setCierre(motivo);
        tramoRepository.save(t);
    }

    /** Segundos del tramo dentro de [ini, fin). Un tramo abierto cuenta hasta su límite o hasta ahora. */
    static long segundosEn(mt_TareaTramo t, LocalDateTime ini, LocalDateTime fin, LocalDateTime ahora) {
        LocalDateTime finTramo = t.getFin() != null ? t.getFin() : min(ahora, limiteCierre(t));
        LocalDateTime a = t.getInicio().isAfter(ini) ? t.getInicio() : ini;
        LocalDateTime b = min(finTramo, fin);
        return Duration.between(a, b).getSeconds();
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
