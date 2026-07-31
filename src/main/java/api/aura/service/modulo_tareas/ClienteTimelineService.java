package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.ClienteActividadDTO;
import api.aura.dto.modulo_tareas.ClienteNotaDTO;
import api.aura.dto.modulo_tareas.ActualizarNotaClienteRequest;
import api.aura.dto.modulo_tareas.CrearNotaClienteRequest;
import api.aura.dto.modulo_tareas.TareaDTO;
import api.aura.entity.Cliente;
import api.aura.entity.ClienteActividadEvento;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.ClienteNota;
import api.aura.repository.ClienteActividadRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.ClienteNotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteTimelineService {

    private final ClienteActividadRepository actividadRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteNotaRepository clienteNotaRepository;
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
        return clienteNotaRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId).stream()
                .map(this::toNotaDto)
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

    public ClienteNotaDTO crearNotaFijadaConArchivo(Integer clienteId, CrearNotaClienteRequest request, MultipartFile archivo) {
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

        if (archivo != null && !archivo.isEmpty()) {
            guardarArchivoEnNota(nota, archivo, clienteId);
        }

        return toNotaDto(clienteNotaRepository.save(nota));
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

        return toNotaDto(clienteNotaRepository.save(nota));
    }

    public ClienteNotaDTO actualizarNotaConArchivo(Integer clienteId, Long notaId, ActualizarNotaClienteRequest request, MultipartFile archivo) {
        validarCliente(clienteId);
        validarRequestActualizacionNota(request);

        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));

        nota.setComentario(request.getComentario().trim());
        nota.setCups(normalizarCups(request.getCups()));
        nota.setEditado(true);
        nota.setFechaEdicion(LocalDateTime.now());

        if (archivo != null && !archivo.isEmpty()) {
            // Eliminar archivo anterior si existe
            eliminarArchivoFisico(nota.getRutaArchivo());
            guardarArchivoEnNota(nota, archivo, clienteId);
        }

        return toNotaDto(clienteNotaRepository.save(nota));
    }

    public void eliminarNota(Integer clienteId, Long notaId) {
        validarCliente(clienteId);

        ClienteNota nota = clienteNotaRepository.findByIdAndCliente_Id(notaId, clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada para este cliente"));

        // Eliminar archivo físico si existe
        eliminarArchivoFisico(nota.getRutaArchivo());

        clienteNotaRepository.delete(nota);
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

    private void guardarArchivoEnNota(ClienteNota nota, MultipartFile archivo, Integer clienteId) {
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
            Files.copy(archivo.getInputStream(), destino);
            nota.setNombreArchivo(nombreOriginal);
            nota.setRutaArchivo(destino.toString());
            nota.setTipoMime(archivo.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo adjunto: " + e.getMessage(), e);
        }
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
        return toNotaDto(clienteNotaRepository.save(nota));
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
                .build();
    }
}
