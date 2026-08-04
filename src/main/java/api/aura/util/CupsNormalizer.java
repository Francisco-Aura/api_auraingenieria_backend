package api.aura.util;

/**
 * Utility class for normalizing CUPS (Código Unificado de Punto de Suministro) values.
 * Handles null, blank, "null" string and "undefined" string inputs from the frontend.
 */
public final class CupsNormalizer {

    private CupsNormalizer() {
        // Utility class — no instantiation
    }

    /**
     * Normalizes a CUPS value.
     * Returns null for null, blank, "null", or "undefined" inputs.
     * Returns the trimmed value otherwise.
     */
    public static String normalizar(String cups) {
        if (cups == null) {
            return null;
        }
        String trimmed = cups.trim();
        if (trimmed.isEmpty() || "null".equals(trimmed) || "undefined".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
