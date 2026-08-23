package api.aura.service.modulo_tareas;

import api.aura.entity.Cliente;
import api.aura.entity.modulo_tareas.mt_Adjunto;
import api.aura.entity.modulo_tareas.mt_Comentario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload.base-dir:uploads/tareas}")
    private String baseUploadDir;

    /**
     * Guarda un archivo adjunto asociado directamente a una tarea (sin comentario).
     * Usado al crear la tarea con adjuntos desde el formulario principal.
     */
    public mt_Adjunto guardarAdjuntoTarea(MultipartFile file, mt_Tarea tarea) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (tarea == null) {
            throw new IllegalArgumentException("La tarea no puede ser nula");
        }

        Cliente cliente = tarea.getClienteRelacionado();
        String directorio = construirRutaDirectorio(cliente, tarea);
        Path dirPath = Paths.get(directorio);
        Files.createDirectories(dirPath);

        String nombreOriginal = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreArchivo = timestamp + "_" + UUID.randomUUID() + "_" + nombreOriginal;
        Path rutaCompleta = dirPath.resolve(nombreArchivo);
        Files.copy(file.getInputStream(), rutaCompleta);

        log.info("Archivo de tarea guardado: {}", rutaCompleta);

        return mt_Adjunto.builder()
                .nombreArchivo(nombreOriginal)
                .rutaArchivo(rutaCompleta.toString())
                .tipoMime(file.getContentType())
                .tamanio(file.getSize())
                .tarea(tarea)
                .build();
    }

    /**
     * Guarda un archivo adjunto asociado a un comentario de tarea
     * Estructura:
     * uploads/tareas/{clienteNombre}_{dni}/{tareaId}/{timestamp}_{uuid}_{filename}
     */
    public mt_Adjunto guardarAdjunto(MultipartFile file, mt_Comentario comentario) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }

        if (comentario == null || comentario.getTarea() == null) {
            throw new IllegalArgumentException("El comentario o la tarea asociada son nulos");
        }

        mt_Tarea tarea = comentario.getTarea();
        Cliente cliente = tarea.getClienteRelacionado();

        // Construir la ruta del directorio
        String directorio = construirRutaDirectorio(cliente, tarea);
        Path dirPath = Paths.get(directorio);

        // Crear directorios si no existen
        Files.createDirectories(dirPath);

        // Limpiar y generar nombre de archivo único
        String nombreOriginal = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String nombreArchivo = timestamp + "_" + UUID.randomUUID() + "_" + nombreOriginal;

        // Ruta completa del archivo
        Path rutaCompleta = dirPath.resolve(nombreArchivo);

        // Guardar el archivo
        Files.copy(file.getInputStream(), rutaCompleta);

        log.info("Archivo guardado: {}", rutaCompleta.toString());

        // Crear entidad mt_Adjunto
        return mt_Adjunto.builder()
                .nombreArchivo(nombreOriginal)
                .rutaArchivo(rutaCompleta.toString())
                .tipoMime(file.getContentType())
                .tamanio(file.getSize())
                .comentario(comentario)
                .build();
    }

    /**
     * Construye la ruta del directorio según la estructura:
     * uploads/tareas/{clienteNombre}_{dni}/{tareaId}
     */
    private String construirRutaDirectorio(Cliente cliente, mt_Tarea tarea) {
        StringBuilder ruta = new StringBuilder(baseUploadDir);
        ruta.append("/");

        // Carpeta del cliente
        if (cliente != null) {
            String nombreCliente = limpiarNombre(cliente.getNombre());
            String cif = cliente.getCif();

            if (nombreCliente != null && !nombreCliente.isEmpty()) {
                ruta.append(nombreCliente);
            } else {
                ruta.append("cliente");
            }

            ruta.append("_");

            if (cif != null && !cif.trim().isEmpty()) {
                ruta.append(cif.replaceAll("[^a-zA-Z0-9]", ""));
            } else {
                // Si no hay CIF, usar ID del cliente
                ruta.append("id").append(cliente.getId());
            }
        } else {
            ruta.append("general");
        }

        // Subcarpeta de la tarea (solo ID)
        ruta.append("/");
        ruta.append(tarea.getId());

        return ruta.toString();
    }

    /**
     * Limpia un nombre para usarlo en rutas de archivo
     * Normaliza acentos y elimina caracteres especiales
     */
    private String limpiarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return "";
        }

        // Normalizar caracteres acentuados (á→a, ñ→n, ü→u, etc.)
        String normalizado = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Convertir a minúsculas, reemplazar espacios por guiones bajos
        // y eliminar caracteres especiales excepto letras, números y guiones
        return normalizado.toLowerCase()
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_-]", "")
                .replaceAll("_{2,}", "_"); // Eliminar múltiples guiones bajos consecutivos
    }

    /**
     * Obtiene un archivo para descarga
     */
    public Path cargarArchivo(String rutaArchivo) throws IOException {
        Path path = Paths.get(rutaArchivo);

        if (!Files.exists(path)) {
            throw new IOException("Archivo no encontrado: " + rutaArchivo);
        }

        return path;
    }

    /**
     * Elimina un archivo del sistema
     */
    public void eliminarArchivo(String rutaArchivo) throws IOException {
        Path path = Paths.get(rutaArchivo);

        if (Files.exists(path)) {
            Files.delete(path);
            log.info("Archivo eliminado: {}", rutaArchivo);
        }
    }
}
