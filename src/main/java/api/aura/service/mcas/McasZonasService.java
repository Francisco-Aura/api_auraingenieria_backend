package api.aura.service.mcas;

import api.aura.config.AuraUserHolder;
import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.entity.mcas.McasZonaComision;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.mcas.McasZonaComisionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.util.Provincias;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Zona de comisión de un contrato (reunión del 04/11/2026 y respuesta del cliente a la duda 5).
 *
 * <p>Ya no se escribe en el contrato: sale de la provincia del punto de suministro, con las
 * zonas que cada comercializadora tenga en Configuración MCAS (en Iberdrola, las provincias de
 * la ZAR y la ZBR como zona del resto). Una comercializadora sin zonas configuradas sigue con
 * la regla de su hoja del Excel: el CUPS de luz que empieza por ES0021 es ZAR y el resto, ZBR.
 *
 * <p>Es la única pieza que decide la zona: la usan la propuesta, la vista previa y la
 * simulación, para que las tres den siempre lo mismo.
 */
@Service
public class McasZonasService {

    static final String PREFIJO_CUPS_ZAR = "ES0021";
    static final String ZAR = "ZAR";
    static final String ZBR = "ZBR";
    static final int LARGO_ZONA = 20;

    /**
     * La zona de un contrato o, si no se puede saber, el motivo en palabras de la pantalla
     * (para decir qué hay que corregir). Las dos partes pueden ir vacías: un contrato de gas
     * de una comercializadora sin zonas no tiene zona y no falta nada.
     */
    public record Resuelta(String zona, String motivo) {
        static final Resuelta SIN_ZONA = new Resuelta(null, null);

        static Resuelta de(String zona) {
            return new Resuelta(zona, null);
        }

        static Resuelta falta(String motivo) {
            return new Resuelta(null, motivo);
        }
    }

    private final McasZonaComisionRepository repo;
    private final TBComercializadorasRepository comercializadoras;
    private final PuntoSuministroRepository puntos;

    public McasZonasService(McasZonaComisionRepository repo, TBComercializadorasRepository comercializadoras,
                            PuntoSuministroRepository puntos) {
        this.repo = repo;
        this.comercializadoras = comercializadoras;
        this.puntos = puntos;
    }

    // ------------------------------------------------------------------ consulta

    public List<McasZonaComision> listar() {
        return repo.findAll(Sort.by("idComercializadora", "zona"));
    }

    /** Zona de un contrato de esa comercializadora en un punto con esa provincia y ese CUPS de luz. */
    public Resuelta resolver(Integer idComercializadora, String tipoSuministro, String provincia, String cupsLuz) {
        if (idComercializadora == null || idComercializadora == 0) return Resuelta.SIN_ZONA;
        List<McasZonaComision> zonas = repo.findByIdComercializadora(idComercializadora);
        if (zonas.isEmpty()) return porCups(tipoSuministro, cupsLuz);

        Optional<String> codigo = Provincias.codigoDe(provincia);
        if (codigo.isEmpty())
            return Resuelta.falta(provincia == null || provincia.isBlank()
                    ? "El punto de suministro no tiene provincia: elíjala en el punto para saber su zona de comisión."
                    : "No se reconoce la provincia del punto de suministro (" + provincia.trim()
                            + "): elíjala de la lista en el punto para saber su zona de comisión.");
        return zonas.stream().filter(z -> z.getProvincias() != null && z.getProvincias().contains(codigo.get()))
                .findFirst()
                .or(() -> zonas.stream().filter(z -> Boolean.TRUE.equals(z.getEsResto())).findFirst())
                .map(z -> Resuelta.de(z.getZona()))
                .orElseGet(() -> Resuelta.falta(Provincias.nombre(codigo.get())
                        + " no está en ninguna zona de comisión de la comercializadora: añádala en Configuración MCAS, pestaña Zonas."));
    }

    /** Sin zonas por provincia: la regla de la hoja IBERDROLA del Excel (solo luz). */
    private static Resuelta porCups(String tipoSuministro, String cupsLuz) {
        if ("GAS".equalsIgnoreCase(tipoSuministro) || cupsLuz == null || cupsLuz.isBlank()) return Resuelta.SIN_ZONA;
        return Resuelta.de(cupsLuz.trim().toUpperCase(Locale.ROOT).startsWith(PREFIJO_CUPS_ZAR) ? ZAR : ZBR);
    }

    /** Zona del contrato, leyendo la provincia y el CUPS de su punto de suministro. */
    public Resuelta deContrato(Contrato c) {
        if (c == null) return Resuelta.SIN_ZONA;
        PuntoSuministro punto = punto(c);
        return resolver(c.getIdComercializadoraActual(), c.getMcasTipoSuministro(),
                punto != null ? punto.getProvincia() : null, punto != null ? punto.getCUPSluz() : null);
    }

    /**
     * Deja en el contrato su zona calculada (sustituye a lo que se escribía a mano) y la
     * devuelve con el motivo si no se ha podido saber.
     */
    public Resuelta aplicar(Contrato c) {
        Resuelta resuelta = deContrato(c);
        if (c != null) c.setMcasZonaComision(resuelta.zona());
        return resuelta;
    }

    /** El punto del contrato con sus datos: desde la pantalla solo llega su id. */
    private PuntoSuministro punto(Contrato c) {
        PuntoSuministro punto = c.getPuntoSuministro();
        if (punto == null) return null;
        boolean incompleto = punto.getProvincia() == null && (punto.getCUPSluz() == null || punto.getCUPSluz().isBlank());
        if (incompleto && punto.getId() != null) return puntos.findById(punto.getId()).orElse(punto);
        return punto;
    }

    // ------------------------------------------------------------------ alta, cambio y baja

    @Transactional
    public McasZonaComision crear(McasZonaComision datos) {
        return guardar(new McasZonaComision(), datos);
    }

    @Transactional
    public McasZonaComision actualizar(Integer id, McasZonaComision datos) {
        McasZonaComision actual = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Esa zona ya no existe."));
        return guardar(actual, datos);
    }

    /** Borra la zona y devuelve la que había (para avisar de las comisiones pendientes afectadas). */
    @Transactional
    public McasZonaComision eliminar(Integer id) {
        McasZonaComision borrada = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Esa zona ya no existe."));
        repo.delete(borrada);
        return borrada;
    }

    private McasZonaComision guardar(McasZonaComision destino, McasZonaComision datos) {
        if (datos == null) throw new IllegalArgumentException("Faltan los datos de la zona.");
        String zona = datos.getZona() == null ? "" : datos.getZona().trim().toUpperCase(Locale.ROOT);
        boolean esResto = Boolean.TRUE.equals(datos.getEsResto());
        List<String> provincias = esResto ? List.of() : codigosValidos(datos.getProvincias());
        validar(destino.getId(), datos.getIdComercializadora(), zona, esResto, provincias);

        destino.setIdComercializadora(datos.getIdComercializadora());
        destino.setZona(zona);
        destino.setEsResto(esResto);
        destino.setProvincias(new ArrayList<>(provincias));
        String usuario = AuraUserHolder.get();
        destino.setActualizadoPor(usuario != null ? usuario : "sistema");
        destino.setActualizadoEn(LocalDateTime.now());
        return repo.save(destino);
    }

    /** Sin repetidos y en orden; lanza si alguno no es una provincia. */
    private static List<String> codigosValidos(List<String> provincias) {
        LinkedHashSet<String> codigos = new LinkedHashSet<>();
        for (String p : provincias == null ? List.<String>of() : provincias) {
            String codigo = p == null ? "" : p.trim();
            if (!Provincias.existe(codigo)) throw new IllegalArgumentException("Hay una provincia que no es válida: " + codigo + ".");
            codigos.add(codigo);
        }
        return codigos.stream().sorted().toList();
    }

    /** Lanza IllegalArgumentException con el motivo, con las palabras del formulario. */
    private void validar(Integer id, Integer idComercializadora, String zona, boolean esResto, List<String> provincias) {
        if (idComercializadora == null || !comercializadoras.existsById(idComercializadora))
            throw new IllegalArgumentException("Elija la comercializadora.");
        if (zona.isEmpty()) throw new IllegalArgumentException("Escriba el nombre de la zona (por ejemplo, ZAR).");
        if (zona.length() > LARGO_ZONA)
            throw new IllegalArgumentException("El nombre de la zona no puede tener más de " + LARGO_ZONA + " caracteres.");
        if (!esResto && provincias.isEmpty())
            throw new IllegalArgumentException("Marque las provincias de la zona, o «Son todas las demás provincias».");

        for (McasZonaComision otra : repo.findByIdComercializadora(idComercializadora)) {
            if (Objects.equals(otra.getId(), id)) continue;
            if (zona.equalsIgnoreCase(otra.getZona()))
                throw new IllegalArgumentException("Esa comercializadora ya tiene la zona " + zona + ": edítela en vez de crear otra.");
            if (esResto && Boolean.TRUE.equals(otra.getEsResto()))
                throw new IllegalArgumentException("Las demás provincias ya son la zona " + otra.getZona()
                        + ": solo una zona puede tenerlas.");
            Optional<String> repetida = provincias.stream()
                    .filter(p -> otra.getProvincias() != null && otra.getProvincias().contains(p)).findFirst();
            if (repetida.isPresent())
                throw new IllegalArgumentException(Provincias.nombre(repetida.get()) + " ya está en la zona " + otra.getZona()
                        + " de esta comercializadora: una provincia solo puede estar en una zona.");
        }
    }
}
