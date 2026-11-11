package api.aura.service.modulo_registro_horario;

import api.aura.dto.modulo_registro_horario.EmpresaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paridad con empresas.py del Registro Horario anterior: cada caso de src/test/resources/registro-horario-oro/empresas.json
 * trae el archivo de entrada y lo que el original devolvió con él (empresas, localidad y festivos de 2026).
 */
class EmpresasConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path carpeta;

    private EmpresasConfig con(String contenido) throws IOException {
        Path archivo = carpeta.resolve("empresas.json");
        Files.write(archivo, contenido.getBytes(StandardCharsets.UTF_8));
        return new EmpresasConfig(MAPPER, archivo.toString());
    }

    private static String texto(JsonNode n, String campo) {
        return n.get(campo).asText();
    }

    @Test
    void cadaCasoDelOriginalDaLoMismo() throws IOException {
        JsonNode oro = CalendarioFestivosTest.oro("empresas.json");
        assertEquals(7, oro.size());
        for (var it = oro.fields(); it.hasNext(); ) {
            var caso = it.next();
            EmpresasConfig config = con(MAPPER.writeValueAsString(caso.getValue().get("entrada")));
            String nombre = caso.getKey();

            assertEquals(texto(caso.getValue(), "localidad"), config.localidad(), nombre + ": localidad");

            List<EmpresaDTO> esperadas = new ArrayList<>();
            for (JsonNode e : caso.getValue().get("empresas")) {
                esperadas.add(new EmpresaDTO(texto(e, "texto"), texto(e, "empresa"), texto(e, "cif"), texto(e, "ccc"),
                        texto(e, "centro_trabajo"), texto(e, "localidad")));
            }
            assertEquals(esperadas, config.empresas(), nombre + ": empresas");

            List<String> festivos = CalendarioFestivos.festivosDelAnio(2026, config.festivosExtra()).stream()
                    .map(LocalDate::toString).toList();
            List<String> festivosOro = new ArrayList<>();
            caso.getValue().get("festivos_2026").forEach(f -> festivosOro.add(f.asText()));
            assertEquals(festivosOro, festivos, nombre + ": festivos 2026");
        }
    }

    @Test
    void sinArchivoNoHayEmpresasYLaLocalidadEsLaDePorDefecto() {
        EmpresasConfig config = new EmpresasConfig(MAPPER, carpeta.resolve("no-existe.json").toString());
        assertTrue(config.empresas().isEmpty());
        assertTrue(config.festivosExtra().isEmpty());
        assertEquals("LORCA", config.localidad());
    }

    @Test
    void archivoMalEscritoONoObjetoSeTrataComoVacio() throws IOException {
        for (String contenido : new String[]{"{esto no es json", "[1, 2]", "", "\"texto\""}) {
            EmpresasConfig config = con(contenido);
            assertTrue(config.empresas().isEmpty(), contenido);
            assertEquals("LORCA", config.localidad(), contenido);
        }
    }

    @Test
    void admiteLaMarcaBomDelBlocDeNotas() throws IOException {
        EmpresasConfig config = con("﻿{\"localidad\": \"MURCIA\", \"empresas\": []}");
        assertEquals("MURCIA", config.localidad());
    }

    @Test
    void sePuedeCambiarLaListaSinReiniciar() throws IOException {
        EmpresasConfig config = con("{\"empresas\": []}");
        assertTrue(config.empresas().isEmpty());
        Files.writeString(carpeta.resolve("empresas.json"),
                "{\"empresas\": [{\"empresa\": \"A\", \"cif\": \"1\", \"ccc\": \"2\", \"centro_trabajo\": \"3\"}]}");
        assertEquals(1, config.empresas().size());
    }
}
