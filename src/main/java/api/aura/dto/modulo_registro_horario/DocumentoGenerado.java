package api.aura.dto.modulo_registro_horario;

/** El documento ya hecho: bytes, tipo, nombre de archivo y el resumen que va en las cabeceras de la respuesta. */
public record DocumentoGenerado(byte[] contenido, String tipo, String nombreArchivo, String extension, int diasTrabajados,
                                String horas) {
}
