package api.aura.service.mcas;

import api.aura.dto.mcas.McasIncidenciaDTO;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.mcas.McasValorRango;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Tabla completa de una regla (editor en rejilla de Configuración MCAS): se lee
 * con una firma y se guarda entera, de una vez.
 *
 * <p>La firma es el SHA-256 de las filas en forma canónica (ordenadas y con los
 * números sin ceros sobrantes): si otra persona guardó la tabla después de
 * abrirla, la firma ya no coincide y no se pisa su trabajo. El guardado valida
 * en memoria con {@link McasRuleSetValidator#validarTabla} y sustituye todas las
 * filas en una sola transacción: o queda la tabla nueva entera o la anterior
 * intacta.</p>
 */
@Service
@RequiredArgsConstructor
public class McasValoresRangoService {

    private static final int DECIMALES = 6;   // DECIMAL(18,6) en mcas_valores_rango

    private final McasReglaComisionRepository reglaRepo;
    private final McasValorRangoRepository rangoRepo;
    private final McasConjuntoReglasRepository conjuntoRepo;
    private final McasRuleSetValidator validator;

    /** Tabla de una regla: firma para guardar después, método de cálculo y filas ordenadas. */
    public record Tabla(String firma, String metodo, List<McasValorRango> valores) {}

    /** La tabla cambió desde que se leyó: el controlador responde 409. */
    public static class FirmaObsoletaException extends RuntimeException {
        public FirmaObsoletaException() {
            super("La tabla ha cambiado desde que la abrió (otra persona la ha guardado). "
                    + "Vuelva a cargarla y repita sus cambios.");
        }
    }

    @Transactional(readOnly = true)
    public Tabla leer(Integer idRegla) {
        McasReglaComision regla = regla(idRegla);
        List<McasValorRango> valores = rangoRepo.findByIdReglaOrderByOrdenAsc(idRegla);
        return new Tabla(firma(valores), regla.getMetodoCalculo(), valores);
    }

    /**
     * Sustituye todas las filas de la tabla. Solo en tablas de comisiones en
     * borrador y en reglas que usan tabla; {@code firma} debe ser la de la
     * lectura. Las incidencias llevan el {@code orden} recibido (la celda de la
     * rejilla); al guardar, el orden se renumera 1..n sin huecos respetando el
     * recibido (en las matrices, las filas con el mismo orden siguen formando una celda).
     *
     * <p>La regla se lee con bloqueo de escritura antes de comparar la firma: dos
     * guardados a la vez se hacen uno detrás de otro y el segundo recibe la firma
     * obsoleta (409) en lugar de mezclar sus filas con las del primero.</p>
     *
     * @throws java.util.NoSuchElementException si la regla no existe
     * @throws IllegalStateException            si la tabla no se puede editar
     * @throws FirmaObsoletaException           si la tabla cambió desde la lectura
     * @throws McasRuleSetValidator.IncidenciasException si la tabla nueva no es válida
     */
    @Transactional
    public Tabla reemplazar(Integer idRegla, String firma, List<McasValorRango> valores) {
        McasReglaComision regla = reglaRepo.findByIdParaActualizar(idRegla)
                .orElseThrow(() -> new NoSuchElementException("No existe la regla " + idRegla + "."));
        McasConjuntoReglas conjunto = conjuntoRepo.findById(regla.getIdConjuntoReglas()).orElse(null);
        if (conjunto == null || !"BORRADOR".equals(conjunto.getEstado()))
            throw new IllegalStateException("Solo se puede cambiar la tabla de una tabla de comisiones en borrador; "
                    + "cree una nueva versión para cambiarla.");
        if (!McasRuleSetValidator.METODOS_TABLA.contains(regla.getMetodoCalculo()))
            throw new IllegalStateException("La regla «" + McasRuleSetValidator.nombreRegla(regla)
                    + "» no usa una tabla de tramos.");
        if (firma == null || !firma.equals(firma(rangoRepo.findByIdReglaOrderByOrdenAsc(idRegla))))
            throw new FirmaObsoletaException();

        List<McasValorRango> nuevas = normalizar(idRegla, valores == null ? List.of() : valores);
        List<McasIncidenciaDTO> incidencias = validator.validarTabla(regla, nuevas);
        if (!incidencias.isEmpty())
            throw new McasRuleSetValidator.IncidenciasException(
                    "La tabla tiene " + (incidencias.size() == 1 ? "1 problema" : incidencias.size() + " problemas")
                            + "; corríjalos antes de guardar.", incidencias);
        renumerar(nuevas, !"RANGE_LOOKUP".equals(regla.getMetodoCalculo()));

        rangoRepo.deleteByIdRegla(idRegla);
        rangoRepo.flush();
        List<McasValorRango> guardadas = rangoRepo.saveAll(nuevas);
        return new Tabla(firma(guardadas), regla.getMetodoCalculo(), guardadas);
    }

    /**
     * Firma de una tabla: SHA-256 (hexadecimal en minúsculas) de sus filas en
     * forma canónica, sin id ni regla. No depende del orden de la lista ni de los
     * ceros decimales (243.36 = 243.360000).
     */
    public static String firma(Collection<McasValorRango> valores) {
        List<String> filas = new ArrayList<>();
        for (McasValorRango v : valores) {
            filas.add(String.join("|", texto(v.getDimension()), numero(v.getValorMin()), numero(v.getValorMax()),
                    String.valueOf(v.getMinInclusivo() == null || v.getMinInclusivo()),
                    String.valueOf(v.getMaxInclusivo() != null && v.getMaxInclusivo()),
                    texto(v.getValorCategoria()), numero(v.getValorResultado()), texto(v.getUnidadResultado()),
                    String.valueOf(v.getOrden())));
        }
        Collections.sort(filas);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", filas).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private McasReglaComision regla(Integer idRegla) {
        return reglaRepo.findById(idRegla)
                .orElseThrow(() -> new NoSuchElementException("No existe la regla " + idRegla + "."));
    }

    /**
     * Copias nuevas (sin id), ordenadas por el orden recibido, con los valores por
     * defecto de la columna y los números a 6 decimales. Conservan el orden
     * recibido para que las incidencias señalen la celda que ve la pantalla.
     */
    private List<McasValorRango> normalizar(Integer idRegla, List<McasValorRango> recibidas) {
        List<McasValorRango> ordenadas = new ArrayList<>(recibidas);
        ordenadas.removeIf(Objects::isNull);
        ordenadas.sort(Comparator.comparing(McasValorRango::getOrden, Comparator.nullsLast(Comparator.naturalOrder())));
        List<McasValorRango> nuevas = new ArrayList<>();
        for (McasValorRango v : ordenadas) {
            nuevas.add(McasValorRango.builder()
                    .idRegla(idRegla)
                    .dimension(v.getDimension() == null ? null : v.getDimension().trim())
                    .valorMin(escala(v.getValorMin()))
                    .valorMax(escala(v.getValorMax()))
                    .minInclusivo(v.getMinInclusivo() == null || v.getMinInclusivo())
                    .maxInclusivo(v.getMaxInclusivo() != null && v.getMaxInclusivo())
                    .valorCategoria(v.getValorCategoria() == null || v.getValorCategoria().isBlank()
                            ? null : v.getValorCategoria().trim())
                    .valorResultado(escala(v.getValorResultado()))
                    .unidadResultado(v.getUnidadResultado() == null || v.getUnidadResultado().isBlank()
                            ? "EUR" : v.getUnidadResultado().trim())
                    .orden(v.getOrden())
                    .build());
        }
        return nuevas;
    }

    /**
     * Orden 1..n sin huecos, en el orden de la lista (ya ordenada). En una matriz,
     * las filas con el mismo orden forman una celda y siguen juntas; en una tabla
     * de tramos cada fila es un tramo.
     */
    private static void renumerar(List<McasValorRango> filas, boolean agruparCeldas) {
        int orden = 0;
        Integer anterior = null;
        for (McasValorRango v : filas) {
            if (orden == 0 || !agruparCeldas || v.getOrden() == null || !v.getOrden().equals(anterior)) orden++;
            anterior = v.getOrden();
            v.setOrden(orden);
        }
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor == null ? null : valor.setScale(DECIMALES, RoundingMode.HALF_UP);
    }

    private static String numero(BigDecimal valor) {
        return valor == null ? "" : valor.stripTrailingZeros().toPlainString();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
