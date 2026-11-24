package api.aura.service.modulo_planificador.estudio;

/**
 * Producción de 1 kWp hora a hora (8760 valores en kWh) de la ubicación y orientación reales de la instalación, calculada con PVGIS.
 * {@code aviso} es lo que hay que decirle al consultor si no se pudo calcular (entonces {@code kwhPorKwp} es null y se usa el modelo
 * sencillo de sur / este-oeste).
 */
public record PerfilSolar(double[] kwhPorKwp, String aviso) {

    public static PerfilSolar sinPerfil(String aviso) {
        return new PerfilSolar(null, aviso);
    }

    public double anual() {
        double s = 0;
        if (kwhPorKwp != null) for (double v : kwhPorKwp) s += v;
        return s;
    }
}
