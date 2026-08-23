package api.aura.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import api.aura.entity.Contrato;

@Service
public class ContratoFileStorageService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String PDF_SIGNATURE = "%PDF-";

    private final Path uploadBasePath;

    public ContratoFileStorageService(@Value("${aura.uploads.contratos-path:uploads/contratos}") String uploadPath) {
        this.uploadBasePath = Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    public void guardarPdf(Contrato contrato, MultipartFile archivo) {
        validarPdf(archivo);

        Path directorioContrato = uploadBasePath.resolve("contrato_" + contrato.getId()).normalize();
        if (!directorioContrato.startsWith(uploadBasePath)) {
            throw new IllegalArgumentException("Ruta de archivo no permitida");
        }

        try {
            Files.createDirectories(directorioContrato);
            String nombreOriginal = nombreOriginalSeguro(archivo);
            Path destino = directorioContrato.resolve(UUID.randomUUID() + ".pdf").normalize();
            if (!destino.startsWith(directorioContrato)) {
                throw new IllegalArgumentException("Ruta de archivo no permitida");
            }

            try (InputStream contenido = archivo.getInputStream()) {
                Files.copy(contenido, destino, StandardCopyOption.REPLACE_EXISTING);
            }

            eliminarArchivoAnterior(contrato.getRutaArchivoContrato());
            contrato.setNombreArchivo(nombreOriginal);
            contrato.setRutaArchivoContrato(destino.toString());
            contrato.setTipoMimeArchivo(PDF_CONTENT_TYPE);
            contrato.setTamanioArchivo(archivo.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el PDF del contrato", e);
        }
    }

    public Path obtenerPdf(Contrato contrato) {
        if (contrato.getRutaArchivoContrato() == null || contrato.getRutaArchivoContrato().isBlank()) {
            throw new IllegalArgumentException("El contrato no tiene un PDF adjunto");
        }

        Path ruta = Paths.get(contrato.getRutaArchivoContrato()).toAbsolutePath().normalize();
        if (!ruta.startsWith(uploadBasePath) || !Files.isRegularFile(ruta)) {
            throw new IllegalArgumentException("El PDF del contrato no está disponible");
        }
        return ruta;
    }

    private void validarPdf(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar un archivo PDF");
        }

        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nombre.endsWith(".pdf")) {
            throw new IllegalArgumentException("El archivo seleccionado debe ser un PDF");
        }

        String contentType = archivo.getContentType();
        if (contentType != null && !PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException("El archivo seleccionado debe tener tipo PDF");
        }

        try (InputStream contenido = archivo.getInputStream()) {
            byte[] cabecera = contenido.readNBytes(PDF_SIGNATURE.length());
            if (!PDF_SIGNATURE.equals(new String(cabecera))) {
                throw new IllegalArgumentException("El contenido del archivo no corresponde a un PDF válido");
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo validar el PDF seleccionado", e);
        }
    }

    private String nombreOriginalSeguro(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename() == null ? "contrato.pdf" : archivo.getOriginalFilename();
        String seguro = Paths.get(nombre).getFileName().toString().replaceAll("[\\r\\n]", "");
        return seguro.isBlank() ? "contrato.pdf" : seguro;
    }

    private void eliminarArchivoAnterior(String rutaAnterior) {
        if (rutaAnterior == null || rutaAnterior.isBlank()) {
            return;
        }

        try {
            Path ruta = Paths.get(rutaAnterior).toAbsolutePath().normalize();
            if (ruta.startsWith(uploadBasePath)) {
                Files.deleteIfExists(ruta);
            }
        } catch (IOException ignored) {
        }
    }
}
