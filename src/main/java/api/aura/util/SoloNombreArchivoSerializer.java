package api.aura.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Una ruta de archivo del servidor se envía al navegador solo con el nombre del archivo:
 * la pantalla únicamente necesita saber si hay archivo, y la ruta completa enseñaba las
 * carpetas del servidor.
 */
public class SoloNombreArchivoSerializer extends StdSerializer<String> {

    public SoloNombreArchivoSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String ruta, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(soloNombre(ruta));
    }

    static String soloNombre(String ruta) {
        if (ruta == null || ruta.isBlank()) return ruta;
        try {
            Path nombre = Path.of(ruta.replace('\\', '/')).getFileName();
            return nombre != null ? nombre.toString() : "";
        } catch (InvalidPathException rutaRara) {
            return "archivo";
        }
    }
}
