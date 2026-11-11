package api.aura.service.modulo_registro_horario;

import api.aura.dto.modulo_registro_horario.EmpresaDTO;
import api.aura.dto.modulo_registro_horario.EmpresasRespuesta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista de empresas del desplegable: viene de un archivo JSON del servidor, fuera del repositorio (datos reales de
 * empresa: CIF, CCC, centro de trabajo). La ruta es la propiedad {@code registro-horario.empresas.ruta} o la variable de
 * entorno {@code REGISTRO_EMPRESAS} (la misma del servicio anterior). Se lee en cada petición (es pequeño): cambiar la
 * lista no exige reiniciar. En el log nunca se vuelca el contenido del archivo.
 */
@Service
public class EmpresasConfig {

    public static final String RUTA_POR_DEFECTO = "/etc/registro-horario/empresas.json";
    public static final String LOCALIDAD_POR_DEFECTO = "LORCA";
    private static final List<String> DATOS_EMPRESA = List.of("empresa", "cif", "ccc", "centro_trabajo");
    private static final Logger log = LoggerFactory.getLogger(EmpresasConfig.class);

    private final ObjectMapper mapper;
    private final Path ruta;

    public EmpresasConfig(ObjectMapper mapper,
                          @Value("${registro-horario.empresas.ruta:${REGISTRO_EMPRESAS:" + RUTA_POR_DEFECTO + "}}") String ruta) {
        this.mapper = mapper;
        this.ruta = Path.of(ruta);
    }

    /** Contenido del archivo ({@code null} si no existe, está mal escrito o no es un objeto JSON). */
    JsonNode leerConfig() {
        try {
            String texto = new String(Files.readAllBytes(ruta), StandardCharsets.UTF_8);
            if (texto.startsWith("﻿")) { // el Bloc de notas de Windows añade la marca BOM
                texto = texto.substring(1);
            }
            JsonNode raiz = mapper.readTree(texto);
            return raiz != null && raiz.isObject() ? raiz : null;
        } catch (NoSuchFileException e) {
            log.warn("No existe el archivo de empresas {}", ruta);
        } catch (IOException e) {
            log.error("No se pudo leer el archivo de empresas {} ({})", ruta, e.getClass().getSimpleName());
        }
        return null;
    }

    /** Localidad general (la de la configuración o la de por defecto). */
    public String localidad() {
        return localidad(leerConfig());
    }

    /** Fechas extra de festivo de la configuración («AAAA-MM-DD» o «MM-DD»); solo se admiten textos dentro de una lista. */
    public List<String> festivosExtra() {
        return festivosExtra(leerConfig());
    }

    /** Empresas válidas: cada una necesita empresa, cif, ccc y centro_trabajo (y puede traer su «localidad»); las incompletas se descartan. */
    public List<EmpresaDTO> empresas() {
        return empresas(leerConfig());
    }

    /** Localidad y empresas con una sola lectura del archivo. */
    public EmpresasRespuesta respuesta() {
        JsonNode config = leerConfig();
        return new EmpresasRespuesta(localidad(config), empresas(config));
    }

    private static String localidad(JsonNode config) {
        JsonNode valor = config == null ? null : config.get("localidad");
        return verdadero(valor) ? texto(valor) : LOCALIDAD_POR_DEFECTO;
    }

    private static List<String> festivosExtra(JsonNode config) {
        List<String> extra = new ArrayList<>();
        JsonNode lista = config == null ? null : config.get("festivos_extra");
        if (lista != null && lista.isArray()) {
            lista.forEach(x -> {
                if (x.isTextual()) {
                    extra.add(x.asText());
                }
            });
        }
        return extra;
    }

    private List<EmpresaDTO> empresas(JsonNode config) {
        JsonNode brutas = config == null ? null : config.get("empresas");
        List<Map<String, String>> validas = new ArrayList<>();
        int n = 0;
        if (brutas != null && brutas.isArray()) {
            for (JsonNode e : brutas) {
                n++;
                Map<String, String> limpia = new LinkedHashMap<>();
                for (String k : List.of("empresa", "cif", "ccc", "centro_trabajo", "localidad")) {
                    JsonNode v = e.isObject() ? e.get(k) : null;
                    limpia.put(k, verdadero(v) ? texto(v).strip() : "");
                }
                List<String> falta = DATOS_EMPRESA.stream().filter(k -> limpia.get(k).isEmpty()).toList();
                if (!falta.isEmpty()) {
                    log.error("Empresa nº {} descartada: falta {}", n, String.join(", ", falta));
                    continue;
                }
                validas.add(limpia);
            }
        }
        List<EmpresaDTO> resultado = new ArrayList<>();
        for (Map<String, String> e : validas) {
            long repetidas = validas.stream().filter(o -> o.get("empresa").equals(e.get("empresa"))).count();
            String texto = repetidas == 1 ? e.get("empresa") : e.get("empresa") + " (" + e.get("centro_trabajo") + ")";
            resultado.add(new EmpresaDTO(texto, e.get("empresa"), e.get("cif"), e.get("ccc"),
                    e.get("centro_trabajo"), e.get("localidad")));
        }
        return resultado;
    }

    /** «Verdadero» como en Python: no vale null, vacío, 0, false ni lista u objeto vacíos. */
    private static boolean verdadero(JsonNode v) {
        if (v == null || v.isNull()) {
            return false;
        }
        if (v.isTextual()) {
            return !v.asText().isEmpty();
        }
        if (v.isNumber()) {
            return v.doubleValue() != 0;
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        return v.size() > 0;
    }

    private static String texto(JsonNode v) {
        return v.isValueNode() ? v.asText() : v.toString();
    }
}
