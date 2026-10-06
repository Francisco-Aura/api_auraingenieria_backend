package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.ClienteActividadDTO;
import api.aura.dto.modulo_tareas.ClienteNotaDTO;
import api.aura.dto.modulo_tareas.ClienteNotaAdjuntoDTO;
import api.aura.dto.modulo_tareas.ActualizarNotaClienteRequest;
import api.aura.dto.modulo_tareas.CrearNotaClienteRequest;
import api.aura.dto.modulo_tareas.TareaDTO;
import api.aura.entity.Cliente;
import api.aura.entity.ClienteActividadEvento;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.ClienteNota;
import api.aura.entity.modulo_tareas.ClienteNotaAdjunto;
import api.aura.repository.ClienteActividadRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.ClienteNotaAdjuntoRepository;
import api.aura.repository.modulo_tareas.ClienteNotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteTimelineService {

    private final ClienteActividadRepository actividadRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteNotaRepository clienteNotaRepository;
    private final ClienteNotaAdjuntoRepository clienteNotaAdjuntoRepository;
    private final TareaService tareaService;

    @Value("${aura.uploads.notas-path:uploads/notas}")
    private String notasUploadPath;

    @Transactional(readOnly = true)
    public List<ClienteActividadDTO> obtenerActividad(Integer clienteId, Integer nivelAcceso) {
        validarCliente(clienteId);
        return actividadRepository.findActividadVisibleByCliente(clienteId, nivelAcceso,
                PageRequest.of(0, 200)).stream()
                .map(this::toActividadDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClienteNotaDTO> obtenerNotasFijadas(Integer clienteId) {
        validarCliente(clienteId);
        List<ClienteNota> notas = clienteNotaRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId);
        Map<Long, List<ClienteNotaAdjunto>> adjuntosPorNota = cargarAdjuntos(notas);
        return notas.stream()
                .map(nota -> toNotaDto(nota, adjuntosPorNota.getOrDefault(nota.getId(), List.of())))
                .toList();
    }

    public ClienteNotaDTO crearNotaFijada(Integer clienteId, CrearNotaClienteRequest request) {
        validarRequestNota(request);

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        String cups = normalizarCups(request.getCups());
        ClienteNota nota = ClienteNota.builder()
            .cliente(cliente)
                .usuarioAutor(usuario)
            .cups(cups)
                .comentario(request.getComentario().trim())
                .fijado(Boolean.TRUE.equals(request.getFijado()))
                .build();

        return toNotaDto(clienteNotaRepository.save(nota));
    }

    public ClienteNotaDTO crearNotaFijadaConArchivos(Integer clienteId, CrearNotaClienteRequest request, List<MultipartFile> archivos) {
        validarRequestNota(request);

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));

        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        String cups = normalizarCups(request.getCups());
        ClienteNota nota = ClienteNota.builder()
            .cliente(cliente)
            .usuarioAutor(usuario)
            .cups(cups)
            .comentario(request.getComentario().trim())
            .fijado(Boolean.TRUE.equals(request.getFijado()))
            .build();

        nota = clienteNotaRepository.save(nota);
        guardarArchivosEnNota(nota, filtrarArchivosValidos(archivos), clienteId);

        return obtenerNotaDto(clienteId, nota.getId());
    }

    public ClienteNotaDTO actualizarNota(Integer clienteId, Long notaId, ActualizarNotaClienteRequest request) {
        validarCliente(clienteId);
        validarRequestActualizacionNota(request);

        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));

        nota.setComentario(request.getComentario().trim());
        nota.setCups(normalizarCups(request.getCups()));
        nota.setEditado(true);
        nota.setFechaEdicion(LocalDateTime.now());

        return conAdjuntos(clienteNotaRepository.save(nota));
    }

    /**
     * Actualiza la nota y añade los archivos recibidos como nuevos adjuntos
     * (los adjuntos existentes se conservan; se pueden eliminar individualmente).
     */
    public ClienteNotaDTO actualizarNotaConArchivos(Integer clienteId, Long notaId,
            ActualizarNotaClienteRequest request, List<MultipartFile> archivos) {
        validarCliente(clienteId);
        validarRequestActualizacionNota(request);

        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));

        nota.setComentario(request.getComentario().trim());
        nota.setCups(normalizarCups(request.getCups()));
        nota.setEditado(true);
        nota.setFechaEdicion(LocalDateTime.now());

        nota = clienteNotaRepository.save(nota);
        guardarArchivosEnNota(nota, filtrarArchivosValidos(archivos), clienteId);

        return obtenerNotaDto(clienteId, nota.getId());
    }

    public ClienteNotaDTO eliminarArchivoNota(Integer clienteId, Long notaId) {
        validarCliente(clienteId);

        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));

        if (nota.getRutaArchivo() == null) {
            throw new IllegalArgumentException("Esta nota no tiene archivo adjunto");
        }

        eliminarArchivoFisico(nota.getRutaArchivo());
        // Eliminar también la fila correspondiente en la tabla de adjuntos
        clienteNotaAdjuntoRepository.findByNota_IdOrderByFechaSubidaAsc(nota.getId()).stream()
                .filter(a -> nota.getRutaArchivo().equals(a.getRutaArchivo()))
                .forEach(clienteNotaAdjuntoRepository::delete);
        nota.setNombreArchivo(null);
        nota.setRutaArchivo(null);
        nota.setTipoMime(null);
        nota.setEditado(true);
        nota.setFechaEdicion(LocalDateTime.now());

        return toNotaDto(clienteNotaRepository.save(nota));
    }

    @Transactional(readOnly = true)
    public ClienteNotaDTO obtenerNotaDto(Integer clienteId, Long notaId) {
        validarCliente(clienteId);
        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));
        return toNotaDto(nota, clienteNotaAdjuntoRepository.findByNota_IdOrderByFechaSubidaAsc(nota.getId()));
    }

    public void eliminarNota(Integer clienteId, Long notaId) {
        validarCliente(clienteId);

        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));

        // Eliminar todos los adjuntos (físicos y filas)
        for (ClienteNotaAdjunto adjunto : clienteNotaAdjuntoRepository.findByNota_IdOrderByFechaSubidaAsc(nota.getId())) {
            eliminarArchivoFisico(adjunto.getRutaArchivo());
            clienteNotaAdjuntoRepository.delete(adjunto);
        }
        // Compatibilidad con el campo único antiguo
        eliminarArchivoFisico(nota.getRutaArchivo());

        clienteNotaRepository.delete(nota);
    }

    /**
     * Elimina un adjunto concreto de una nota.
     */
    public ClienteNotaDTO eliminarAdjuntoNota(Integer clienteId, Long notaId, Long adjuntoId) {
        validarCliente(clienteId);

        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));

        ClienteNotaAdjunto adjunto = clienteNotaAdjuntoRepository.findByIdAndNota_Id(adjuntoId, notaId)
                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado para esta nota"));

        eliminarArchivoFisico(adjunto.getRutaArchivo());
        clienteNotaAdjuntoRepository.delete(adjunto);

        // Si era el archivo del campo antiguo, limpiarlo también
        if (adjunto.getRutaArchivo().equals(nota.getRutaArchivo())) {
            nota.setNombreArchivo(null);
            nota.setRutaArchivo(null);
            nota.setTipoMime(null);
            nota.setEditado(true);
            nota.setFechaEdicion(LocalDateTime.now());
            clienteNotaRepository.save(nota);
        } else {
            nota.setEditado(true);
            nota.setFechaEdicion(LocalDateTime.now());
            clienteNotaRepository.save(nota);
        }

        return obtenerNotaDto(clienteId, notaId);
    }

    /** Adjunto concreto validado (existe la nota, el adjunto y el fichero en disco). */
    private ClienteNotaAdjunto obtenerAdjuntoParaDescarga(Integer clienteId, Long notaId, Long adjuntoId) {
        validarCliente(clienteId);
        if (!clienteNotaRepository.existsByIdAndCliente_Id(notaId, clienteId)) {
            throw new IllegalArgumentException("Nota no encontrada para este cliente");
        }
        ClienteNotaAdjunto adjunto = clienteNotaAdjuntoRepository.findByIdAndNota_Id(adjuntoId, notaId)
                .orElseThrow(() -> new IllegalArgumentException("Adjunto no encontrado para esta nota"));
        Path path = Paths.get(adjunto.getRutaArchivo());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("El archivo adjunto no se encontró en el servidor");
        }
        return adjunto;
    }

    public record ClienteNotaAdjuntoInfo(Path ruta, String nombreArchivo) {}

    @Transactional(readOnly = true)
    public ClienteNotaAdjuntoInfo obtenerAdjuntoParaDescargaInfo(Integer clienteId, Long notaId, Long adjuntoId) {
        ClienteNotaAdjunto adjunto = obtenerAdjuntoParaDescarga(clienteId, notaId, adjuntoId);
        return new ClienteNotaAdjuntoInfo(Paths.get(adjunto.getRutaArchivo()), adjunto.getNombreArchivo());
    }

    public Path obtenerRutaArchivoNota(Integer clienteId, Long notaId) {
        validarCliente(clienteId);
        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));
        if (nota.getRutaArchivo() == null) {
            throw new IllegalArgumentException("Esta nota no tiene archivo adjunto");
        }
        Path path = Paths.get(nota.getRutaArchivo());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("El archivo adjunto no se encontró en el servidor");
        }
        return path;
    }

    public String obtenerNombreArchivoNota(Integer clienteId, Long notaId) {
        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));
        return nota.getNombreArchivo();
    }

    private List<MultipartFile> filtrarArchivosValidos(List<MultipartFile> archivos) {
        if (archivos == null) return List.of();
        return archivos.stream()
                .filter(a -> a != null && !a.isEmpty())
                .toList();
    }

    /**
     * Guarda físicamente cada archivo y crea su fila en clientes_notas_adjuntos.
     * Mantiene los campos antiguos (nombre_archivo/ruta_archivo/tipo_mime) de la
     * primera nota sincronizados por compatibilidad.
     */
    private void guardarArchivosEnNota(ClienteNota nota, List<MultipartFile> archivos, Integer clienteId) {
        for (MultipartFile archivo : archivos) {
            try {
                Path dirPath = Paths.get(notasUploadPath, "cliente_" + clienteId);
                Files.createDirectories(dirPath);
                String nombreOriginal = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "archivo";
                // Extraer y sanitizar la extensión para prevenir path traversal
                String rawExtension = nombreOriginal.contains(".") ? nombreOriginal.substring(nombreOriginal.lastIndexOf('.')) : "";
                String extension = rawExtension.replaceAll("[^a-zA-Z0-9.]", "");
                if (extension.length() > 10) extension = "";
                String nombreFisico = UUID.randomUUID().toString() + extension;
                Path destino = dirPath.resolve(nombreFisico).normalize();
                // Verificar que el destino sigue dentro del directorio permitido
                if (!destino.startsWith(dirPath.normalize())) {
                    throw new SecurityException("Ruta de archivo no permitida");
                }
                try (InputStream contenido = archivo.getInputStream()) {
                    Files.copy(contenido, destino);
                }

                ClienteNotaAdjunto adjunto = ClienteNotaAdjunto.builder()
                        .nota(nota)
                        .nombreArchivo(nombreOriginal)
                        .rutaArchivo(destino.toString())
                        .tipoMime(archivo.getContentType())
                        .build();
                clienteNotaAdjuntoRepository.save(adjunto);

                // Compatibilidad con el campo único antiguo
                if (nota.getRutaArchivo() == null) {
                    nota.setNombreArchivo(nombreOriginal);
                    nota.setRutaArchivo(destino.toString());
                    nota.setTipoMime(archivo.getContentType());
                    clienteNotaRepository.save(nota);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error al guardar el archivo adjunto: " + e.getMessage(), e);
            }
        }
    }

    private Map<Long, List<ClienteNotaAdjunto>> cargarAdjuntos(List<ClienteNota> notas) {
        List<Long> ids = notas.stream().map(ClienteNota::getId).toList();
        if (ids.isEmpty()) return Map.of();
        return clienteNotaAdjuntoRepository.findByNota_IdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getNota().getId()));
    }

    private ClienteNotaAdjuntoDTO toAdjuntoDto(ClienteNotaAdjunto adjunto) {
        return ClienteNotaAdjuntoDTO.builder()
                .id(adjunto.getId())
                .nombreArchivo(adjunto.getNombreArchivo())
                .tipoMime(adjunto.getTipoMime())
                .build();
    }

    private void eliminarArchivoFisico(String ruta) {
        if (ruta == null) return;
        try {
            Path path = Paths.get(ruta);
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // No crítico si falla la eliminación física
        }
    }

    public ClienteNotaDTO toggleFijadoNota(Integer clienteId, Long notaId) {
        validarCliente(clienteId);

        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));

        nota.setFijado(!Boolean.TRUE.equals(nota.getFijado()));
        return conAdjuntos(clienteNotaRepository.save(nota));
    }

    /** DTO de la nota con sus adjuntos: la pantalla sustituye la nota por la respuesta. */
    private ClienteNotaDTO conAdjuntos(ClienteNota nota) {
        return toNotaDto(nota, clienteNotaAdjuntoRepository.findByNota_IdOrderByFechaSubidaAsc(nota.getId()));
    }

    @Transactional(readOnly = true)
    public List<TareaDTO> obtenerIncidencias(Integer clienteId) {
        validarCliente(clienteId);
        return tareaService.obtenerIncidenciasPorCliente(clienteId);
    }

    private void validarCliente(Integer clienteId) {
        if (clienteId == null || !clienteRepository.existsById(clienteId)) {
            throw new IllegalArgumentException("Cliente no encontrado");
        }
    }

    private void validarRequestNota(CrearNotaClienteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La petición de nota es obligatoria");
        }
        if (request.getUsuarioId() == null) {
            throw new IllegalArgumentException("El usuarioId es obligatorio");
        }
        if (request.getComentario() == null || request.getComentario().isBlank()) {
            throw new IllegalArgumentException("El comentario no puede estar vacío");
        }
    }

    private void validarRequestActualizacionNota(ActualizarNotaClienteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La petición de actualización es obligatoria");
        }
        if (request.getComentario() == null || request.getComentario().isBlank()) {
            throw new IllegalArgumentException("El comentario no puede estar vacío");
        }
    }

    private String normalizarCups(String cups) {
        if (cups == null) return null;
        String trimmed = cups.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ClienteActividadDTO toActividadDto(ClienteActividadEvento evento) {
        return ClienteActividadDTO.builder()
                .id(evento.getId())
                .tipoEvento(evento.getTipoEvento())
                .descripcion(evento.getDescripcion())
                .origen(evento.getOrigen())
                .cups(evento.getCups())
                .soloNivelUno(evento.getSoloNivelUno())
                .fechaEvento(evento.getFechaEvento())
                .usuarioNombre(evento.getUsuarioNombre())
                .build();
    }

    private ClienteNotaDTO toNotaDto(ClienteNota nota) {
        String cups = nota.getCups() != null && !nota.getCups().isBlank()
                ? nota.getCups().trim()
                : null;

        return ClienteNotaDTO.builder()
                .comentarioId(nota.getId())
                .tareaId(null)
                .tituloTarea(null)
                .cups(cups)
                .comentario(nota.getComentario())
                .usuarioNombre(nota.getUsuarioAutor() != null ? nota.getUsuarioAutor().getNombre() : null)
                .fechaCreacion(nota.getFechaCreacion())
            .fechaEdicion(nota.getFechaEdicion())
            .editado(nota.getEditado() != null ? nota.getEditado() : false)
                .fijado(Boolean.TRUE.equals(nota.getFijado()))
                .nombreArchivo(nota.getNombreArchivo())
                .tipoMime(nota.getTipoMime())
                .adjuntos(List.of())
                .build();
    }

    private ClienteNotaDTO toNotaDto(ClienteNota nota, List<ClienteNotaAdjunto> adjuntos) {
        ClienteNotaDTO dto = toNotaDto(nota);
        // Si no hay filas nuevas pero sí el adjunto antiguo, sintetizarlo
        if (adjuntos == null || adjuntos.isEmpty()) {
            if (nota.getRutaArchivo() != null) {
                dto.setAdjuntos(List.of(ClienteNotaAdjuntoDTO.builder()
                        .id(-nota.getId()) // ID negativo = adjunto legacy
                        .nombreArchivo(nota.getNombreArchivo())
                        .tipoMime(nota.getTipoMime())
                        .build()));
            }
            return dto;
        }
        dto.setAdjuntos(adjuntos.stream().map(this::toAdjuntoDto).toList());
        return dto;
    }
}
