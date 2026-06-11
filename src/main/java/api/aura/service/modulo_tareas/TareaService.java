package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.*;
import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.*;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TareaService {

    private final mt_TareaRepository tareaRepository;
    private final mt_TareaEstadoRepository estadoRepository;
    private final mt_CategoriaRepository categoriaRepository;
    private final mt_SubCategoriaRepository subCategoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TareaNotificacionService notificacionService;
    private final TareaComentarioService comentarioService;
    private final mt_TareaHistorialRepository historialRepository;
    private final AdjuntoRepository adjuntoRepository;
    private final FileStorageService fileStorageService;

    public List<TareaDTO> obtenerTodas() {
        return tareaRepository.findAll().stream()
                .map(tarea -> toDto(tarea, false)).collect(Collectors.toList());
    }

    public List<TareaDTO> obtenerPorUsuario(Integer usuarioId) {
        return tareaRepository.findByUsuarioAsignado_Id(usuarioId).stream()
                .map(tarea -> toDto(tarea, false)).collect(Collectors.toList());
    }

    public List<TareaDTO> obtenerPorCliente(Integer clienteId) {
        return tareaRepository.findByClienteRelacionado_Id(clienteId).stream()
                .map(tarea -> toDto(tarea, false)).collect(Collectors.toList());
    }

    public List<TareaDTO> obtenerIncidenciasPorCliente(Integer clienteId) {
        return tareaRepository.findIncidenciasByClienteId(clienteId).stream()
                .map(tarea -> toDto(tarea, false)).collect(Collectors.toList());
    }

    public TareaDTO obtenerPorId(Long id) {
        mt_Tarea tarea = tareaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));
        return toDto(tarea, true);
    }

    public void actualizarFijada(Long id, Boolean fijada) {
        mt_Tarea tarea = tareaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));
        tarea.setFijada(fijada);
        tareaRepository.save(tarea);
    }

    public TareaDTO crear(TareaDTO dto) {
        mt_Tarea tarea = mapearDesdeDto(new mt_Tarea(), dto, true);
        if (tarea.getEstado() == null) {
            estadoRepository.findByEstadoFinalFalseOrderByOrdenAsc().stream().findFirst()
                    .ifPresent(tarea::setEstado);
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (tarea.getEstado() != null && Boolean.TRUE.equals(tarea.getEstado().getCuentaTiempo())) {
            tarea.setFechaInicioProgreso(ahora);
        }
        mt_Tarea guardada = tareaRepository.save(tarea);
        registrarHistorial(guardada, guardada.getEstado(),
                obtenerUsuarioAccion(dto.getUsuarioCreadorId(), guardada.getUsuarioCreador()),
                ahora, null, "Creación de la tarea");
        notificacionService.notificarAsignacion(guardada);
        return toDto(guardada, true);
    }

    public TareaDTO actualizar(Long id, TareaDTO dto) {
        mt_Tarea tarea = tareaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));

        // Guardar valores anteriores para detectar cambios
        Integer usuarioAsignadoAnterior = tarea.getUsuarioAsignado() != null ? tarea.getUsuarioAsignado().getId()
                : null;
        String usuarioAsignadoNombreAnterior = tarea.getUsuarioAsignado() != null
                ? tarea.getUsuarioAsignado().getNombre()
                : null;
        Long estadoAnterior = tarea.getEstado() != null ? tarea.getEstado().getId() : null;
        String estadoNombreAnterior = tarea.getEstado() != null ? tarea.getEstado().getNombre() : null;
        String tituloAnterior = tarea.getTitulo();
        String descripcionAnterior = tarea.getDescripcion();
        LocalDateTime fechaVencimientoAnterior = tarea.getFechaVencimiento();
        Integer prioridadAnterior = tarea.getPrioridad();
        Long categoriaAnterior = tarea.getCategoria() != null ? tarea.getCategoria().getId() : null;
        String categoriaNombreAnterior = tarea.getCategoria() != null ? tarea.getCategoria().getNombre() : null;

        mt_Tarea actualizada = tareaRepository.save(mapearDesdeDto(tarea, dto, false));

        // Detectar cada cambio específico
        Integer usuarioAsignadoNuevo = actualizada.getUsuarioAsignado() != null
                ? actualizada.getUsuarioAsignado().getId()
                : null;
        String usuarioAsignadoNombreNuevo = actualizada.getUsuarioAsignado() != null
                ? actualizada.getUsuarioAsignado().getNombre()
                : null;
        Long estadoNuevo = actualizada.getEstado() != null ? actualizada.getEstado().getId() : null;
        String estadoNombreNuevo = actualizada.getEstado() != null ? actualizada.getEstado().getNombre() : null;

        // ID del usuario que realiza la acción (para no auto-notificar)
        Integer usuarioAccionId = dto.getUsuarioCreadorId();

        // Si el usuario asignado cambió
        if (!java.util.Objects.equals(usuarioAsignadoNuevo, usuarioAsignadoAnterior)) {
            if (usuarioAsignadoNuevo != null) {
                log.debug("Notificando asignación de tarea a usuario {}", usuarioAsignadoNuevo);
                notificacionService.notificarAsignacion(actualizada, usuarioAccionId);
            }
        }

        // Si el estado cambió
        if (!java.util.Objects.equals(estadoNuevo, estadoAnterior)) {
            log.debug("Notificando cambio de estado de {} a {}", estadoNombreAnterior, estadoNombreNuevo);
            notificacionService.notificarCambioEstado(actualizada, estadoNombreNuevo, usuarioAccionId);
        }

        // Si el título cambió
        if (!java.util.Objects.equals(actualizada.getTitulo(), tituloAnterior)) {
            log.debug("Notificando cambio de título");
            notificacionService.notificarCampoEspecifico(actualizada, "TITULO",
                    String.format("Título modificado: '%s'", actualizada.getTitulo()), usuarioAccionId);
        }

        // Si la descripción cambió
        if (!java.util.Objects.equals(actualizada.getDescripcion(), descripcionAnterior)) {
            log.debug("Notificando cambio de descripción");
            notificacionService.notificarCampoEspecifico(actualizada, "DESCRIPCION",
                    "La descripción de la tarea fue actualizada", usuarioAccionId);
        }

        // Si la fecha de vencimiento cambió (comparar ignorando milisegundos)
        if (!sonFechasIguales(actualizada.getFechaVencimiento(), fechaVencimientoAnterior)) {
            log.debug("Notificando cambio de fecha de vencimiento");
            String fechaFormato = actualizada.getFechaVencimiento() != null ? actualizada.getFechaVencimiento()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "sin fecha";
            notificacionService.notificarCampoEspecifico(actualizada, "FECHA_VENCIMIENTO",
                    String.format("Fecha de vencimiento: %s", fechaFormato), usuarioAccionId);

            // Reiniciar flag de notificación para que se notifique de nuevo 1h antes de la
            // nueva fecha
            actualizada.setNotificadoProximoVencimiento(false);
            tareaRepository.save(actualizada);
        }

        // Si la prioridad cambió
        if (!java.util.Objects.equals(actualizada.getPrioridad(), prioridadAnterior)) {
            log.debug("Notificando cambio de prioridad");
            String prioridad = obtenerNombrePrioridad(actualizada.getPrioridad());
            notificacionService.notificarCampoEspecifico(actualizada, "PRIORIDAD",
                    String.format("Prioridad cambiada a: %s", prioridad), usuarioAccionId);
        }

        // Si la categoría cambió
        Long categoriaNueva = actualizada.getCategoria() != null ? actualizada.getCategoria().getId() : null;
        String categoriaNombreNueva = actualizada.getCategoria() != null ? actualizada.getCategoria().getNombre()
                : null;
        if (!java.util.Objects.equals(categoriaNueva, categoriaAnterior)) {
            log.debug("Notificando cambio de categoría");
            String categoriaTexto = categoriaNombreNueva != null ? categoriaNombreNueva : "sin categoría";
            notificacionService.notificarCampoEspecifico(actualizada, "CATEGORIA",
                    String.format("Categoría: %s", categoriaTexto), usuarioAccionId);
        }

        return toDto(actualizada, true);
    }

    private String obtenerNombrePrioridad(Integer prioridad) {
        if (prioridad == null)
            return "Media";
        return switch (prioridad) {
            case 0 -> "Urgente";
            case 1 -> "Alta";
            case 2 -> "Media";
            case 3 -> "Baja";
            default -> "Media";
        };
    }

    private boolean sonFechasIguales(LocalDateTime fecha1, LocalDateTime fecha2) {
        // Comparar solo la fecha y hora (ignorar segundos y milisegundos)
        if (fecha1 == null && fecha2 == null)
            return true;
        if (fecha1 == null || fecha2 == null)
            return false;

        // Permitir hasta 1 minuto de diferencia para considerar iguales
        long diferenciaMinutos = java.time.temporal.ChronoUnit.MINUTES.between(fecha2, fecha1);
        return Math.abs(diferenciaMinutos) <= 1;
    }

    public void eliminar(Long id) {
        // Primero eliminar archivos físicos de todos los adjuntos asociados a la tarea
        List<mt_Adjunto> adjuntos = adjuntoRepository.findByComentario_Tarea_Id(id);
        adjuntos.addAll(adjuntoRepository.findByTareaIdAndComentarioIsNull(id));
        for (mt_Adjunto adjunto : adjuntos) {
            try {
                fileStorageService.eliminarArchivo(adjunto.getRutaArchivo());
            } catch (IOException e) {
                log.warn("No se pudo eliminar el archivo físico: {}", adjunto.getRutaArchivo(), e);
            }
        }
        // Eliminar la tarea (cascada eliminará comentarios y adjuntos en BD)
        tareaRepository.deleteById(id);
    }

    public mt_Adjunto agregarAdjuntoDirecto(Long tareaId, org.springframework.web.multipart.MultipartFile file) throws IOException {
        mt_Tarea tarea = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));
        mt_Adjunto adjunto = fileStorageService.guardarAdjuntoTarea(file, tarea);
        return adjuntoRepository.save(adjunto);
    }

    public TareaDTO cambiarEstado(Long tareaId, Long estadoId) {
        CambioEstadoRequest request = new CambioEstadoRequest();
        request.setEstadoId(estadoId);
        return cambiarEstado(tareaId, request);
    }

    public TareaDTO cambiarEstado(Long tareaId, CambioEstadoRequest request) {
        if (request == null || request.getEstadoId() == null) {
            throw new IllegalArgumentException("El estado destino es obligatorio");
        }
        mt_Tarea tarea = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));
        mt_TareaEstado estado = estadoRepository.findById(request.getEstadoId())
                .orElseThrow(() -> new IllegalArgumentException("Estado no encontrado"));

        if (tarea.getEstado() != null && tarea.getEstado().getId().equals(estado.getId())) {
            return toDto(tarea, true);
        }

        Usuario usuarioAccion = obtenerUsuarioAccion(request.getUsuarioAccionId(), tarea.getUsuarioAsignado());
        aplicarCambioEstado(tarea, estado, usuarioAccion, request.getMotivoBloqueo(), request.getComentario());
        mt_Tarea guardada = tareaRepository.save(tarea);
        notificacionService.notificarCambioEstado(guardada, estado.getNombre());
        // Si el estado es final, notificar a los administradores
        if (Boolean.TRUE.equals(estado.getEstadoFinal())) {
            notificacionService.notificarCompletada(guardada, usuarioAccion);
        }
        return toDto(guardada, true);
    }

    public long contarActivasPorUsuario(Integer usuarioId) {
        return tareaRepository.countByUsuarioAsignado_IdAndEstado_EstadoFinalFalse(usuarioId);
    }

    public List<TareaDTO> obtenerVencidas() {
        LocalDateTime ahora = LocalDateTime.now();
        return tareaRepository.findByEstado_EstadoFinalFalseAndFechaVencimientoBefore(ahora)
                .stream().map(tarea -> toDto(tarea, false)).collect(Collectors.toList());
    }

    public List<TareaDTO> obtenerVencenHoy() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime finDia = LocalDate.now().atTime(23, 59, 59);
        return tareaRepository.findByEstado_EstadoFinalFalseAndFechaVencimientoBetween(inicioDia, finDia)
                .stream().map(tarea -> toDto(tarea, false)).collect(Collectors.toList());
    }

    public List<TareaDTO> obtenerPorVencer(int dias) {
        LocalDateTime desde = LocalDateTime.now();
        LocalDateTime hasta = LocalDate.now().plusDays(dias).atTime(23, 59, 59);
        return tareaRepository.findByEstado_EstadoFinalFalseAndFechaVencimientoBetween(desde, hasta)
                .stream().map(tarea -> toDto(tarea, false)).collect(Collectors.toList());
    }

    public EstadisticasTareasDTO obtenerEstadisticas(Integer usuarioId) {
        return obtenerEstadisticas(usuarioId, null);
    }

    public EstadisticasTareasDTO obtenerEstadisticas(Integer usuarioId, Integer nivelAcceso) {
        log.debug("Obteniendo estadísticas - Usuario ID: {}, Nivel de acceso: {}", usuarioId, nivelAcceso);

        // Si el usuario tiene nivel 1, ve TODAS las tareas
        List<mt_Tarea> tareas;
        if (nivelAcceso != null && nivelAcceso == 1) {
            log.debug("Nivel 1 detectado - mostrando TODAS las tareas");
            tareas = tareaRepository.findAll();
        } else {
            tareas = tareaRepository.findByUsuarioAsignado_Id(usuarioId);
        }

        log.debug("Tareas encontradas: {}", tareas.size());

        long total = tareas.size();
        LocalDateTime ahora = LocalDateTime.now();

        // Métricas básicas
        long vencidas = tareas.stream()
                .filter(t -> Boolean.FALSE.equals(valorEstadoFinal(t)) && esVencida(t, ahora))
                .count();
        long enProgreso = tareas.stream()
                .filter(t -> Boolean.FALSE.equals(valorEstadoFinal(t)) && estadoCuentaTiempo(t))
                .count();
        long pendientes = tareas.stream()
                .filter(t -> Boolean.FALSE.equals(valorEstadoFinal(t)) && !estadoCuentaTiempo(t))
                .count();
        long tareasBloqueadas = tareas.stream()
                .filter(t -> t.getEstado() != null && Boolean.TRUE.equals(t.getEstado().getEsBloqueo()))
                .count();
        long completadas = tareas.stream()
                .filter(t -> Boolean.TRUE.equals(valorEstadoFinal(t)))
                .count();

        log.debug(
                "Métricas Básicas - Total: {}, Vencidas: {}, En Progreso: {}, Pendientes: {}, Completadas: {}, Bloqueadas: {}",
                total, vencidas, enProgreso, pendientes, completadas, tareasBloqueadas);

        // Calcular estadísticas semanales (lunes a domingo actual)
        LocalDateTime inicioSemana = ahora.minusDays(ahora.getDayOfWeek().getValue() - 1).toLocalDate().atStartOfDay();
        LocalDateTime finSemana = inicioSemana.plusDays(6).withHour(23).withMinute(59).withSecond(59);

        // Tareas completadas ESTA SEMANA (por fecha de finalización)
        long completadasSemana = tareas.stream()
                .filter(t -> Boolean.TRUE.equals(valorEstadoFinal(t)) &&
                        t.getFechaFinalizacion() != null &&
                        t.getFechaFinalizacion().isAfter(inicioSemana.minusDays(1)) &&
                        t.getFechaFinalizacion().isBefore(finSemana.plusDays(1)))
                .count();

        // Total de tareas de esta semana
        long totalSemana = completadasSemana + tareas.stream()
                .filter(t -> Boolean.FALSE.equals(valorEstadoFinal(t)) &&
                        t.getFechaVencimiento() != null &&
                        t.getFechaVencimiento().isAfter(inicioSemana.minusDays(1)) &&
                        t.getFechaVencimiento().isBefore(finSemana.plusDays(1)))
                .count();

        // Métricas de tiempo
        long tiempoActivoTotal = tareas.stream()
                .mapToLong(t -> calcularDuracionActiva(t, ahora))
                .sum();
        long tiempoBloqueadoTotal = tareas.stream()
                .mapToLong(t -> {
                    long acumulado = Optional.ofNullable(t.getTiempoBloqueadoSegundos()).orElse(0L);
                    if (t.getFechaBloqueo() != null && t.getEstado() != null
                            && Boolean.TRUE.equals(t.getEstado().getEsBloqueo())) {
                        acumulado += segundosEntre(t.getFechaBloqueo(), ahora);
                    }
                    return acumulado;
                })
                .sum();
        long tiempoActivoPromedio = total == 0 ? 0 : tiempoActivoTotal / total;

        // NUEVAS MÉTRICAS DE PRODUCTIVIDAD

        // Tasa de completación: (completadas / (completadas + pendientes + enProgreso))
        // * 100
        long tareasAsignadas = completadas + pendientes + enProgreso;
        double tasaCompletacion = tareasAsignadas == 0 ? 0 : (double) completadas / tareasAsignadas * 100;
        log.debug("Tasa Completación: {}/{} = {}%", completadas, tareasAsignadas, tasaCompletacion);

        // Eficiencia: tareas completadas a tiempo vs tareas completadas
        long completadasATiempo = tareas.stream()
                .filter(t -> Boolean.TRUE.equals(valorEstadoFinal(t)) &&
                        t.getFechaFinalizacion() != null &&
                        t.getFechaVencimiento() != null &&
                        (t.getFechaFinalizacion().isBefore(t.getFechaVencimiento()) ||
                                t.getFechaFinalizacion().isEqual(t.getFechaVencimiento())))
                .count();
        double eficiencia = completadas == 0 ? 0 : (double) completadasATiempo / completadas * 100;

        // Delay promedio en días para tareas vencidas
        List<mt_Tarea> tareasVencidasList = tareas.stream()
                .filter(t -> Boolean.FALSE.equals(valorEstadoFinal(t)) && esVencida(t, ahora))
                .collect(Collectors.toList());
        double delayPromedioDias = tareasVencidasList.isEmpty() ? 0
                : tareasVencidasList.stream()
                        .mapToLong(t -> Duration.between(t.getFechaVencimiento(), ahora).toDays())
                        .average()
                        .orElse(0);
        log.debug("Tareas Vencidas: {}, Delay Promedio: {} días", tareasVencidasList.size(), delayPromedioDias);

        // Tareas bloqueadas en el período actual
        long tareasBloqueadasPeriodo = tareas.stream()
                .filter(t -> t.getEstado() != null && Boolean.TRUE.equals(t.getEstado().getEsBloqueo()))
                .count();
        log.debug("Tareas Bloqueadas: {}", tareasBloqueadasPeriodo);

        // Tareas próximas a vencer (próximos 3 días)
        LocalDateTime proximosTresDias = ahora.plusDays(3);
        long tareasProximasAVencer = tareas.stream()
                .filter(t -> Boolean.FALSE.equals(valorEstadoFinal(t)) &&
                        t.getFechaVencimiento() != null &&
                        t.getFechaVencimiento().isAfter(ahora) &&
                        t.getFechaVencimiento().isBefore(proximosTresDias))
                .count();
        log.debug("Tareas Próximas a Vencer: {}", tareasProximasAVencer);

        // Prioridad promedio de las tareas pendientes
        double prioridadPromedioPendientes = tareas.stream()
                .filter(t -> Boolean.FALSE.equals(valorEstadoFinal(t)))
                .mapToInt(t -> Optional.ofNullable(t.getPrioridad()).orElse(2))
                .average()
                .orElse(2.0);

        // Tareas sin asignar (no completadas y sin usuario asignado)
        long tareasSinAsignar = tareas.stream()
                .filter(t -> Boolean.FALSE.equals(valorEstadoFinal(t)) && t.getUsuarioAsignado() == null)
                .count();
        log.debug("Tareas Sin Asignar: {}", tareasSinAsignar);

        return EstadisticasTareasDTO.builder()
                .totalTareas(total)
                .tareasPendientes(pendientes)
                .tareasEnProgreso(enProgreso)
                .tareasCompletadas(completadas)
                .tareasVencidas(vencidas)
                .tareasBloqueadas(tareasBloqueadas)
                .tareasCompletadasSemana(completadasSemana)
                .totalTareasSemana(totalSemana)
                .tiempoActivoTotalSegundos(tiempoActivoTotal)
                .tiempoActivoPromedioSegundos(tiempoActivoPromedio)
                .tiempoBloqueadoTotalSegundos(tiempoBloqueadoTotal)
                // Nuevas métricas
                .tasaCompletacion(tasaCompletacion)
                .eficiencia(eficiencia)
                .delayPromedioDias(delayPromedioDias)
                .tareasBloqueadasPeriodo(tareasBloqueadasPeriodo)
                .tareasProximasAVencer(tareasProximasAVencer)
                .prioridadPromedioPendientes(prioridadPromedioPendientes)
                .tareasSinAsignar(tareasSinAsignar)
                .build();
    }

    public List<EstadoTareaDTO> obtenerEstados(boolean soloNoFinales) {
        List<mt_TareaEstado> estados = soloNoFinales ? estadoRepository.findByEstadoFinalFalseOrderByOrdenAsc()
                : estadoRepository.findAllByOrderByOrdenAsc();
        return estados.stream().map(this::toDtoEstado).collect(Collectors.toList());
    }

    public List<CategoriaDTO> obtenerCategorias() {
        return categoriaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(this::toDtoCategoria)
                .collect(Collectors.toList());
    }

    public CategoriaDTO obtenerCategoria(Long id) {
        mt_Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));
        return toDtoCategoria(categoria);
    }

    public List<SubCategoriaDTO> obtenerSubCategoriasPorCategoria(Long categoriaId) {
        return categoriaRepository.findById(categoriaId)
                .map(mt_Categoria::getSubcategorias)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"))
                .stream().filter(mt_SubCategoria::getActivo)
                .map(this::toDtoSubCategoria).collect(Collectors.toList());
    }

    public List<SubCategoriaDTO> obtenerTodasSubcategorias() {
        return subCategoriaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(this::toDtoSubCategoria).collect(Collectors.toList());
    }

    public List<TareaHistorialDTO> obtenerHistorial(Long tareaId) {
        if (!tareaRepository.existsById(tareaId)) {
            throw new IllegalArgumentException("Tarea no encontrada");
        }
        return historialRepository.findByTarea_IdOrderByFechaInicioDesc(tareaId).stream()
                .map(this::toDtoHistorial)
                .collect(Collectors.toList());
    }

    private mt_Tarea mapearDesdeDto(mt_Tarea tarea, TareaDTO dto, boolean esCreacion) {
        tarea.setTitulo(dto.getTitulo());
        tarea.setDescripcion(dto.getDescripcion());
        tarea.setPrioridad(Optional.ofNullable(dto.getPrioridad()).orElse(2));
        tarea.setNotificar(Boolean.TRUE.equals(dto.getNotificar()));
        tarea.setEnviarCorreo(Boolean.TRUE.equals(dto.getEnviarCorreo()));
        tarea.setFijada(Boolean.TRUE.equals(dto.getFijada()));
        tarea.setRepetir(dto.getRepetir());
        tarea.setFechaInicio(dto.getFechaInicio());
        tarea.setFechaVencimiento(dto.getFechaVencimiento());

        tarea.setUsuarioAsignado(dto.getUsuarioAsignadoId() != null
                ? usuarioRepository.findById(dto.getUsuarioAsignadoId())
                        .orElseThrow(() -> new IllegalArgumentException("Usuario asignado inexistente"))
                : null);

        if (dto.getUsuarioCreadorId() != null) {
            Usuario creador = usuarioRepository.findById(dto.getUsuarioCreadorId())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario creador inexistente"));
            tarea.setUsuarioCreador(creador);
        }
        if (dto.getClienteRelacionadoId() != null) {
            Cliente cliente = clienteRepository.findById(dto.getClienteRelacionadoId())
                    .orElseThrow(() -> new IllegalArgumentException("Cliente inexistente"));
            tarea.setClienteRelacionado(cliente);
            tarea.setClientePotencialNombre(null);
        } else {
            tarea.setClienteRelacionado(null);
            // Nombre libre para cliente potencial (no registrado en BD)
            tarea.setClientePotencialNombre(
                    dto.getClientePotencialNombre() != null && !dto.getClientePotencialNombre().isBlank()
                            ? dto.getClientePotencialNombre().trim()
                            : null);
        }
        // CUPS asociado al punto de suministro (null = GENERAL)
        tarea.setCups(dto.getCups() != null && !dto.getCups().isBlank() ? dto.getCups().trim() : null);
        if (esCreacion) {
            if (dto.getEstadoId() != null) {
                mt_TareaEstado estado = estadoRepository.findById(dto.getEstadoId())
                        .orElseThrow(() -> new IllegalArgumentException("Estado inexistente"));
                tarea.setEstado(estado);
            } else if (tarea.getEstado() == null) {
                estadoRepository.findByEstadoFinalFalseOrderByOrdenAsc().stream().findFirst()
                        .ifPresent(tarea::setEstado);
            }
        }
        if (dto.getCategoriaId() != null) {
            mt_Categoria categoria = categoriaRepository.findById(dto.getCategoriaId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoría inexistente"));
            tarea.setCategoria(categoria);
        } else {
            tarea.setCategoria(null);
        }
        if (dto.getSubcategoriaId() != null) {
            mt_SubCategoria subCategoria = subCategoriaRepository.findById(dto.getSubcategoriaId())
                    .orElseThrow(() -> new IllegalArgumentException("Subcategoría inexistente"));
            // Validar que la subcategoría pertenece a la categoría seleccionada (relación
            // ManyToMany)
            if (tarea.getCategoria() != null && subCategoria.getCategorias() != null
                    && !subCategoria.getCategorias().isEmpty()
                    && subCategoria.getCategorias().stream()
                            .noneMatch(cat -> cat.getId().equals(tarea.getCategoria().getId()))) {
                throw new IllegalArgumentException("La subcategoría no pertenece a la categoría seleccionada");
            }
            tarea.setSubcategoria(subCategoria);
        } else {
            tarea.setSubcategoria(null);
        }
        return tarea;
    }

    private TareaDTO toDto(mt_Tarea tarea, boolean includeComentarios) {
        LocalDateTime ahora = LocalDateTime.now();
        List<ComentarioDTO> comentarios = includeComentarios && tarea.getId() != null
                ? comentarioService.obtenerPorTarea(tarea.getId())
                : List.of();
        long duracionActiva = calcularDuracionActiva(tarea, ahora);
        Long duracionTotal = tarea.getFechaCreacion() == null ? null : calcularDuracionTotal(tarea, ahora);
        long tiempoBloqueado = Optional.ofNullable(tarea.getTiempoBloqueadoSegundos()).orElse(0L);
        if (tarea.getFechaBloqueo() != null && tarea.getEstado() != null
                && Boolean.TRUE.equals(tarea.getEstado().getEsBloqueo())) {
            tiempoBloqueado += segundosEntre(tarea.getFechaBloqueo(), ahora);
        }
        return TareaDTO.builder()
                .id(tarea.getId())
                .titulo(tarea.getTitulo())
                .descripcion(tarea.getDescripcion())
                .prioridad(tarea.getPrioridad())
                .notificar(tarea.getNotificar())
                .enviarCorreo(tarea.getEnviarCorreo())
                .repetir(tarea.getRepetir())
                .fechaInicio(tarea.getFechaInicio())
                .fechaVencimiento(tarea.getFechaVencimiento())
                .fechaFinalizacion(tarea.getFechaFinalizacion())
                .fechaCreacion(tarea.getFechaCreacion())
                .fechaActualizacion(tarea.getFechaActualizacion())
                .estadoId(tarea.getEstado() != null ? tarea.getEstado().getId() : null)
                .estadoNombre(tarea.getEstado() != null ? tarea.getEstado().getNombre() : null)
                .estadoColor(tarea.getEstado() != null ? tarea.getEstado().getColorHex() : null)
                .estadoFinal(valorEstadoFinal(tarea))
                .estadoBloqueo(tarea.getEstado() != null ? tarea.getEstado().getEsBloqueo() : null)
                .usuarioAsignadoId(tarea.getUsuarioAsignado() != null ? tarea.getUsuarioAsignado().getId() : null)
                .usuarioAsignadoNombre(
                        tarea.getUsuarioAsignado() != null ? tarea.getUsuarioAsignado().getNombre() : null)
                .usuarioCreadorId(tarea.getUsuarioCreador() != null ? tarea.getUsuarioCreador().getId() : null)
                .usuarioCreadorNombre(tarea.getUsuarioCreador() != null ? tarea.getUsuarioCreador().getNombre() : null)
                .nivelAccesoCreador(
                        tarea.getUsuarioCreador() != null ? tarea.getUsuarioCreador().getNivelAcceso() : null)
                .clienteRelacionadoId(
                        tarea.getClienteRelacionado() != null ? tarea.getClienteRelacionado().getId() : null)
                .clienteRelacionadoNombre(
                        tarea.getClienteRelacionado() != null ? tarea.getClienteRelacionado().getNombre() : null)
                .clientePotencialNombre(tarea.getClientePotencialNombre())
                .cups(tarea.getCups())
                .categoriaId(tarea.getCategoria() != null ? tarea.getCategoria().getId() : null)
                .categoriaNombre(tarea.getCategoria() != null ? tarea.getCategoria().getNombre() : null)
                .subcategoriaId(tarea.getSubcategoria() != null ? tarea.getSubcategoria().getId() : null)
                .subcategoriaNombre(tarea.getSubcategoria() != null ? tarea.getSubcategoria().getNombre() : null)
                .vencida(esVencida(tarea, ahora))
                .fijada(tarea.getFijada())
                .comentarios(comentarios)
                .duracionActivaSegundos(duracionActiva)
                .duracionTotalSegundos(duracionTotal)
                .tiempoBloqueadoSegundos(tiempoBloqueado)
                .totalBloqueos(tarea.getTotalBloqueos())
                .fechaInicioProgreso(tarea.getFechaInicioProgreso())
                .motivoBloqueo(tarea.getMotivoBloqueo())
                .fechaBloqueo(tarea.getFechaBloqueo())
                .usuarioBloqueoId(tarea.getUsuarioBloqueo() != null ? tarea.getUsuarioBloqueo().getId() : null)
                .usuarioBloqueoNombre(tarea.getUsuarioBloqueo() != null ? tarea.getUsuarioBloqueo().getNombre() : null)
                .build();
    }

    private boolean esVencida(mt_Tarea tarea, LocalDateTime referencia) {
        return tarea.getFechaVencimiento() != null && tarea.getFechaVencimiento().isBefore(referencia)
                && Boolean.FALSE.equals(valorEstadoFinal(tarea));
    }

    private boolean valorEstadoFinal(mt_Tarea tarea) {
        return tarea.getEstado() != null && Boolean.TRUE.equals(tarea.getEstado().getEstadoFinal());
    }

    private boolean estadoCuentaTiempo(mt_Tarea tarea) {
        return tarea.getEstado() != null && Boolean.TRUE.equals(tarea.getEstado().getCuentaTiempo());
    }

    private long calcularDuracionActiva(mt_Tarea tarea, LocalDateTime referencia) {
        long base = Optional.ofNullable(tarea.getDuracionActivaSegundos()).orElse(0L);
        if (tarea.getFechaInicioProgreso() != null && estadoCuentaTiempo(tarea)) {
            base += segundosEntre(tarea.getFechaInicioProgreso(), referencia);
        }
        return base;
    }

    private long calcularDuracionTotal(mt_Tarea tarea, LocalDateTime referencia) {
        if (tarea.getFechaCreacion() == null) {
            return 0L;
        }
        LocalDateTime fin = tarea.getFechaFinalizacion() != null ? tarea.getFechaFinalizacion() : referencia;
        return segundosEntre(tarea.getFechaCreacion(), fin);
    }

    private EstadoTareaDTO toDtoEstado(mt_TareaEstado estado) {
        return EstadoTareaDTO.builder()
                .id(estado.getId())
                .nombre(estado.getNombre())
                .colorHex(estado.getColorHex())
                .estadoFinal(estado.getEstadoFinal())
                .cuentaTiempo(estado.getCuentaTiempo())
                .estadoBloqueo(estado.getEsBloqueo())
                .build();
    }

    private CategoriaDTO toDtoCategoria(mt_Categoria categoria) {
        List<SubCategoriaDTO> subCategorias = categoria.getSubcategorias() == null ? List.of()
                : categoria.getSubcategorias().stream()
                        .filter(mt_SubCategoria::getActivo)
                        .map(this::toDtoSubCategoria)
                        .collect(Collectors.toList());
        return CategoriaDTO.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .codigo(categoria.getCodigo())
                .descripcion(categoria.getDescripcion())
                .subcategorias(subCategorias)
                .build();
    }

    private SubCategoriaDTO toDtoSubCategoria(mt_SubCategoria subCategoria) {
        return SubCategoriaDTO.builder()
                .id(subCategoria.getId())
                .nombre(subCategoria.getNombre())
                .codigo(subCategoria.getCodigo())
                .build();
    }

    private void aplicarCambioEstado(mt_Tarea tarea, mt_TareaEstado nuevoEstado, Usuario usuarioAccion,
            String motivoBloqueo, String comentario) {
        if (Boolean.TRUE.equals(nuevoEstado.getEsBloqueo()) && (motivoBloqueo == null || motivoBloqueo.isBlank())) {
            throw new IllegalArgumentException("Es necesario detallar el motivo del bloqueo");
        }

        LocalDateTime ahora = LocalDateTime.now();
        cerrarHistorialAbierto(tarea, ahora);

        tarea.setEstado(nuevoEstado);
        if (Boolean.TRUE.equals(nuevoEstado.getEstadoFinal())) {
            tarea.setFechaFinalizacion(ahora);
        } else if (tarea.getFechaFinalizacion() != null && !Boolean.TRUE.equals(nuevoEstado.getEstadoFinal())) {
            tarea.setFechaFinalizacion(null);
        }

        if (Boolean.TRUE.equals(nuevoEstado.getCuentaTiempo())) {
            tarea.setFechaInicioProgreso(ahora);
        } else {
            tarea.setFechaInicioProgreso(null);
        }

        if (Boolean.TRUE.equals(nuevoEstado.getEsBloqueo())) {
            tarea.setMotivoBloqueo(motivoBloqueo);
            tarea.setFechaBloqueo(ahora);
            tarea.setTotalBloqueos(Optional.ofNullable(tarea.getTotalBloqueos()).orElse(0) + 1);
            tarea.setUsuarioBloqueo(usuarioAccion);
        } else {
            tarea.setMotivoBloqueo(null);
            tarea.setFechaBloqueo(null);
            tarea.setUsuarioBloqueo(null);
        }

        registrarHistorial(tarea, nuevoEstado, usuarioAccion, ahora, motivoBloqueo, comentario);
    }

    private void cerrarHistorialAbierto(mt_Tarea tarea, LocalDateTime referencia) {
        historialRepository.findTopByTarea_IdOrderByFechaInicioDesc(tarea.getId()).ifPresent(historial -> {
            if (historial.getFechaFin() == null) {
                historial.setFechaFin(referencia);
                if (Boolean.TRUE.equals(historial.getEstado().getCuentaTiempo())
                        && historial.getFechaInicio() != null) {
                    long segundos = segundosEntre(historial.getFechaInicio(), referencia);
                    historial.setDuracionActivaSegundos(segundos);
                    tarea.setDuracionActivaSegundos(
                            Optional.ofNullable(tarea.getDuracionActivaSegundos()).orElse(0L) + segundos);
                }
                if (Boolean.TRUE.equals(historial.getEstado().getEsBloqueo()) && historial.getFechaInicio() != null) {
                    long segundosBloqueo = segundosEntre(historial.getFechaInicio(), referencia);
                    historial.setTiempoBloqueoSegundos(segundosBloqueo);
                    tarea.setTiempoBloqueadoSegundos(
                            Optional.ofNullable(tarea.getTiempoBloqueadoSegundos()).orElse(0L) + segundosBloqueo);
                }
                historialRepository.save(historial);
            }
        });
    }

    private void registrarHistorial(mt_Tarea tarea, mt_TareaEstado estado, Usuario usuarioAccion, LocalDateTime fecha,
            String motivoBloqueo, String comentario) {
        if (estado == null) {
            return;
        }
        mt_TareaHistorial nuevo = mt_TareaHistorial.builder()
                .tarea(tarea)
                .estado(estado)
                .usuarioAccion(usuarioAccion)
                .fechaInicio(fecha)
                .motivoBloqueo(Boolean.TRUE.equals(estado.getEsBloqueo()) ? motivoBloqueo : null)
                .comentario(comentario)
                .build();
        historialRepository.save(nuevo);
    }

    private Usuario obtenerUsuarioAccion(Integer id, Usuario fallback) {
        if (id == null) {
            return fallback;
        }
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private long segundosEntre(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null || fin == null) {
            return 0L;
        }
        return Math.max(Duration.between(inicio, fin).getSeconds(), 0);
    }

    private TareaHistorialDTO toDtoHistorial(mt_TareaHistorial historial) {
        mt_TareaEstado estado = historial.getEstado();
        Usuario usuario = historial.getUsuarioAccion();
        return TareaHistorialDTO.builder()
                .id(historial.getId())
                .estadoId(estado != null ? estado.getId() : null)
                .estadoNombre(estado != null ? estado.getNombre() : null)
                .estadoColor(estado != null ? estado.getColorHex() : null)
                .estadoCuentaTiempo(estado != null ? estado.getCuentaTiempo() : null)
                .estadoBloqueo(estado != null ? estado.getEsBloqueo() : null)
                .fechaInicio(historial.getFechaInicio())
                .fechaFin(historial.getFechaFin())
                .duracionActivaSegundos(historial.getDuracionActivaSegundos())
                .tiempoBloqueoSegundos(historial.getTiempoBloqueoSegundos())
                .motivoBloqueo(historial.getMotivoBloqueo())
                .comentario(historial.getComentario())
                .usuarioId(usuario != null ? usuario.getId() : null)
                .usuarioNombre(usuario != null ? usuario.getNombre() : null)
                .build();
    }
}
