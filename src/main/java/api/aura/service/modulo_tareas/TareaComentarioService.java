package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.ComentarioDTO;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Adjunto;
import api.aura.entity.modulo_tareas.mt_Comentario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_ComentarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import api.aura.config.AuraUserHolder;
import api.aura.util.ArchivosTransaccion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TareaComentarioService {

        private final mt_ComentarioRepository comentarioRepository;
        private final mt_TareaRepository tareaRepository;
        private final UsuarioRepository usuarioRepository;
        private final TareaNotificacionService notificacionService;
        private final AdjuntoRepository adjuntoRepository;
        private final FileStorageService fileStorageService;

        // @NombreSinEspacios tal como lo inserta el desplegable de menciones. UNICODE_CHARACTER_CLASS
        // para que \w incluya tildes y ñ (María, Núñez...): sin él la mención se cortaba y no notificaba.
        // También guion y apóstrofo (José-Luis, O'Brien), que cortaban el nombre.
        private static final Pattern PATRON_MENCION = Pattern.compile("@([\\w.'’-]+)", Pattern.UNICODE_CHARACTER_CLASS);
        // Signos con los que puede acabar la frase tras la mención («Gracias @Ana.»)
        private static final Pattern PUNTUACION_FINAL = Pattern.compile("[.'’-]+$");

        // Solo lectura: sin instantáneas ni revisión de cambios de cada comentario al cerrar
        @Transactional(readOnly = true)
        public List<ComentarioDTO> obtenerPorTarea(Long tareaId) {
                List<mt_Comentario> comentarios = comentarioRepository.findByTarea_IdOrderByFechaCreacionAsc(tareaId);
                // Ordenar: primero fijados, luego por fecha descendente (más reciente primero)
                comentarios.sort((a, b) -> {
                        // Fijados primero
                        if (Boolean.TRUE.equals(a.getFijado()) && !Boolean.TRUE.equals(b.getFijado()))
                                return -1;
                        if (!Boolean.TRUE.equals(a.getFijado()) && Boolean.TRUE.equals(b.getFijado()))
                                return 1;
                        // Luego por fecha descendente (más reciente primero)
                        return b.getFechaCreacion().compareTo(a.getFechaCreacion());
                });
                return comentarios.stream().map(this::toDto).collect(Collectors.toList());
        }

        public ComentarioDTO crearComentario(ComentarioDTO dto) {
                mt_Tarea tarea = tareaRepository.findById(dto.getTareaId())
                                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));
                Usuario usuario = usuarioRepository.findById(dto.getUsuarioId())
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

                mt_Comentario comentario = mt_Comentario.builder()
                                .tarea(tarea)
                                .usuarioAutor(usuario)
                                .comentario(dto.getComentario())
                                .fijado(false)
                                .build();
                comentarioRepository.save(comentario);

                notificacionService.notificarComentario(tarea, usuario, dto.getComentario());
                procesarMenciones(dto.getComentario(), tarea, usuario);
                return toDto(comentario);
        }

        /**
         * Extrae menciones @username del texto del comentario y dispara notificaciones
         * a los usuarios mencionados.
         */
        private void procesarMenciones(String texto, mt_Tarea tarea, Usuario autor) {
                procesarMenciones(texto, tarea, autor, java.util.Set.of());
        }

        /** Igual, sin volver a avisar a quien ya estaba mencionado (al editar un comentario). */
        private void procesarMenciones(String texto, mt_Tarea tarea, Usuario autor, java.util.Set<Integer> yaMencionados) {
                for (Usuario mencionado : usuariosMencionados(texto)) {
                        if (!yaMencionados.contains(mencionado.getId())) {
                                notificacionService.notificarMencion(tarea, mencionado, autor);
                        }
                }
        }

        private java.util.List<Usuario> usuariosMencionados(String texto) {
                if (texto == null || texto.isBlank()) return java.util.List.of();
                Matcher matcher = PATRON_MENCION.matcher(texto);
                java.util.List<Usuario> todosLosUsuarios = null;
                // Nombrar dos veces a la misma persona en un comentario es un solo aviso
                java.util.Map<Integer, Usuario> mencionados = new java.util.LinkedHashMap<>();
                while (matcher.find()) {
                        String username = matcher.group(1);
                        String sinPuntuacion = PUNTUACION_FINAL.matcher(username).replaceAll("");
                        // Cargar el listado de usuarios una sola vez por comentario, no por mención
                        if (todosLosUsuarios == null) {
                                todosLosUsuarios = usuarioRepository.findAll();
                        }
                        // Buscar usuario por nombre (tolerante a mayúsculas)
                        todosLosUsuarios.stream()
                                .filter(u -> u.getNombre() != null && esElNombre(u.getNombre(), username, sinPuntuacion))
                                .findFirst()
                                .ifPresent(mencionado -> mencionados.putIfAbsent(mencionado.getId(), mencionado));
                }
                return new java.util.ArrayList<>(mencionados.values());
        }

        private static boolean esElNombre(String nombre, String mencion, String mencionSinPuntuacion) {
                String clave = nombre.replaceAll("\\s+", "");
                return clave.equalsIgnoreCase(mencion) || clave.equalsIgnoreCase(mencionSinPuntuacion);
        }

        public ComentarioDTO actualizarComentario(Long id, String nuevoComentario, Integer usuarioIdActual) {
                if (nuevoComentario == null || nuevoComentario.trim().isEmpty()) {
                        throw new IllegalArgumentException("El comentario no puede estar vacío");
                }
                mt_Comentario comentario = comentarioRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));
                
                // Validar que el usuario sea el autor
                if (!comentario.getUsuarioAutor().getId().equals(usuarioIdActual)) {
                        throw new IllegalArgumentException("No tienes permiso para editar este comentario. Solo el autor puede editarlo.");
                }
                
                // Al corregir el texto solo se avisa a quien se menciona por primera vez
                java.util.Set<Integer> yaMencionados = usuariosMencionados(comentario.getComentario()).stream()
                                .map(Usuario::getId).collect(Collectors.toSet());
                comentario.setComentario(nuevoComentario);
                comentario.setFechaEdicion(java.time.LocalDateTime.now());
                comentario.setEditado(true);
                mt_Comentario actualizado = comentarioRepository.save(comentario);
                procesarMenciones(nuevoComentario, actualizado.getTarea(), actualizado.getUsuarioAutor(), yaMencionados);
                return toDto(actualizado);
        }

        public ComentarioDTO toggleFijado(Long id) {
                mt_Comentario comentario = comentarioRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));
                comentario.setFijado(!Boolean.TRUE.equals(comentario.getFijado()));
                return toDto(comentarioRepository.save(comentario));
        }

        public void eliminarComentario(Long id, Integer usuarioIdActual) {
                mt_Comentario comentario = comentarioRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));
                
                // Obtener el usuario actual para verificar su nivel de acceso
                Usuario usuarioActual = usuarioRepository.findById(usuarioIdActual)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
                
                // Verificar si es administrador (nivelAcceso = 1)
                if (usuarioActual.getNivelAcceso() == null || !usuarioActual.getNivelAcceso().equals(1)) {
                        throw new IllegalArgumentException("No tienes permiso para eliminar comentarios. Solo administradores pueden eliminarlos.");
                }

                // Las filas de sus adjuntos se borran en cascada; sus archivos se quedaban
                // huérfanos en disco. Se borran cuando el borrado queda confirmado.
                for (mt_Adjunto adjunto : comentario.getAdjuntos()) {
                        String ruta = adjunto.getRutaArchivo();
                        if (ruta == null || ruta.isBlank()) continue;
                        try {
                                ArchivosTransaccion.borrarTrasConfirmar(ruta, () -> fileStorageService.eliminarArchivo(ruta));
                        } catch (IOException e) {
                                log.warn("No se pudo eliminar el archivo físico: {}", ruta, e);
                        }
                }

                comentarioRepository.deleteById(id);
        }

        public mt_Adjunto agregarAdjunto(Long comentarioId, MultipartFile file) throws IOException {
                mt_Comentario comentario = comentarioRepository.findById(comentarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));

                mt_Adjunto adjunto = fileStorageService.guardarAdjunto(file, comentario);
                return adjuntoRepository.save(adjunto);
        }

        /** Tarea a la que pertenece un comentario; null si no tiene. */
        @Transactional(readOnly = true)
        public Long tareaDelComentario(Long comentarioId) {
                mt_Comentario comentario = comentarioRepository.findById(comentarioId)
                                .orElseThrow(() -> new IllegalArgumentException("Comentario no encontrado"));
                return comentario.getTarea() != null ? comentario.getTarea().getId() : null;
        }

        /** Tarea a la que pertenece un adjunto (directo o de un comentario); null si no hay. */
        @Transactional(readOnly = true)
        public Long tareaDelAdjunto(Long adjuntoId) {
                mt_Adjunto adjunto = obtenerAdjunto(adjuntoId);
                if (adjunto.getTarea() != null) return adjunto.getTarea().getId();
                if (adjunto.getComentario() != null && adjunto.getComentario().getTarea() != null)
                        return adjunto.getComentario().getTarea().getId();
                return null;
        }

        public mt_Adjunto obtenerAdjunto(Long adjuntoId) {
                return adjuntoRepository.findById(adjuntoId)
                                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado"));
        }

        public void eliminarAdjunto(Long adjuntoId) throws IOException {
                mt_Adjunto adjunto = adjuntoRepository.findById(adjuntoId)
                                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado"));

                // Adjunto directo de una tarea creada por un administrador: solo se toca
                // desde su edición, que es solo para administradores (como TareaController)
                if (adjunto.getComentario() == null && adjunto.getTarea() != null && !AuraUserHolder.esAdmin()
                                && adjunto.getTarea().getUsuarioCreador() != null
                                && Integer.valueOf(1).equals(adjunto.getTarea().getUsuarioCreador().getNivelAcceso())) {
                        throw new SecurityException("Solo un administrador puede modificar una tarea creada por un administrador");
                }

                // Archivo físico: se borra cuando el borrado de la fila queda confirmado
                String ruta = adjunto.getRutaArchivo();
                ArchivosTransaccion.borrarTrasConfirmar(ruta, () -> fileStorageService.eliminarArchivo(ruta));

                // Eliminar registro de base de datos
                adjuntoRepository.delete(adjunto);
        }

        public mt_Adjunto actualizarAdjunto(Long adjuntoId, MultipartFile file) throws IOException {
                mt_Adjunto adjunto = adjuntoRepository.findById(adjuntoId)
                                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado"));
                String rutaAnterior = adjunto.getRutaArchivo();

                // Primero el archivo nuevo. Antes se borraba el anterior y, si el nuevo no
                // se podía guardar (archivo vacío, error de disco...), el adjunto se
                // quedaba apuntando a un archivo que ya no existía.
                mt_Comentario comentario = adjunto.getComentario();
                mt_Adjunto nuevoAdjunto = fileStorageService.guardarAdjunto(file, comentario);

                // Actualizar datos del adjunto existente
                adjunto.setNombreArchivo(nuevoAdjunto.getNombreArchivo());
                adjunto.setRutaArchivo(nuevoAdjunto.getRutaArchivo());
                adjunto.setTipoMime(nuevoAdjunto.getTipoMime());
                adjunto.setTamanio(nuevoAdjunto.getTamanio());
                mt_Adjunto guardado = adjuntoRepository.save(adjunto);

                // El archivo anterior se borra cuando el cambio queda confirmado
                ArchivosTransaccion.borrarTrasConfirmar(rutaAnterior,
                                () -> fileStorageService.eliminarArchivo(rutaAnterior));
                return guardado;
        }

        private ComentarioDTO toDto(mt_Comentario comentario) {
                List<ComentarioDTO.AdjuntoDTO> adjuntosDTO = comentario.getAdjuntos().stream()
                                .map(adj -> ComentarioDTO.AdjuntoDTO.builder()
                                                .id(adj.getId())
                                                .nombreArchivo(adj.getNombreArchivo())
                                                .tamanio(adj.getTamanio())
                                                .tipoMime(adj.getTipoMime())
                                                .fechaSubida(adj.getFechaSubida())
                                                .build())
                                .collect(Collectors.toList());

                return ComentarioDTO.builder()
                                .id(comentario.getId())
                                .tareaId(comentario.getTarea().getId())
                                .usuarioId(comentario.getUsuarioAutor().getId())
                                .usuarioNombre(comentario.getUsuarioAutor().getNombre())
                                .comentario(comentario.getComentario())
                                .fechaCreacion(comentario.getFechaCreacion())
                                .fechaEdicion(comentario.getFechaEdicion())
                                .editado(comentario.getEditado() != null ? comentario.getEditado() : false)
                                .fijado(comentario.getFijado() != null ? comentario.getFijado() : false)
                                .adjuntos(adjuntosDTO)
                                .build();
        }
}
