package api.aura.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Regla única de «dado de baja» para clientes, puntos de suministro, contratos y
 * servicios.
 *
 * <p>Los datos heredados guardan «sin baja» de tres formas: {@code NULL}, el año 1
 * ({@code 0001-01-01}) y el año 1900. Cada servicio tenía su propia versión de esta
 * comprobación —y {@code PuntoSuministroService} usaba un simple {@code != null}—,
 * de modo que un registro con la fecha 1900 salía activo en unas pantallas y de baja
 * en otras. Las consultas nativas de informes aplican el mismo criterio con
 * {@code YEAR(fecha_baja) IN (1, 1900)}.</p>
 */
public final class BajaEfectiva {

    private BajaEfectiva() {}

    /** true solo si la fecha representa una baja real (ni nula ni histórica). */
    public static boolean tieneBaja(Date fechaBaja) {
        if (fechaBaja == null) return false;
        Calendar calendario = Calendar.getInstance();
        calendario.setTime(fechaBaja);
        int anio = calendario.get(Calendar.YEAR);
        return anio != 1 && anio != 1900;
    }

    /** Inverso de {@link #tieneBaja(Date)}, para leer mejor en las condiciones. */
    public static boolean estaActivo(Date fechaBaja) {
        return !tieneBaja(fechaBaja);
    }
}
