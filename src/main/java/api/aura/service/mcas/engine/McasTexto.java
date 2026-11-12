package api.aura.service.mcas.engine;

import java.util.Locale;

/** Normalización de textos que se comparan en las reglas MCAS. */
public final class McasTexto {

    private McasTexto() {}

    /**
     * Forma canónica de una tarifa de acceso para compararla: mayúsculas, sin
     * espacios, guiones ni guiones bajos, coma decimal como punto y sin puntos que
     * no separen decimales. «2.0 TD», «2.0TD» y «2,0TD» ⇒ «2.0TD»; «RL.1» y «RL1» ⇒
     * «RL1». Así el nombre de la tarifa en el catálogo no deja sin regla a nadie.
     */
    public static String normalizarTarifa(String tarifa) {
        if (tarifa == null) return null;
        String t = tarifa.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_-]", "");
        t = t.replaceAll("(?<=\\d),(?=\\d)", ".");
        t = t.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", "");
        return t.replace(",", "");
    }

    /** Igualdad de tarifas de acceso tras normalizarlas. */
    public static boolean mismaTarifa(String a, String b) {
        return a != null && b != null && normalizarTarifa(a).equals(normalizarTarifa(b));
    }

    /**
     * Código interno a partir de un texto: mayúsculas, sin acentos y con «_» entre
     * palabras («Plan Online 15» ⇒ «PLAN_ONLINE_15», «Plan Ñ» ⇒ «PLAN_N»). Vacío si
     * el texto no tiene letras ni números.
     */
    public static String codigo(String texto) {
        if (texto == null) return "";
        return sinAcentos(texto).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Primera palabra del texto, en mayúsculas y sin acentos («IBERDROLA CLIENTES» ⇒ «IBERDROLA»). */
    public static String primeraPalabra(String texto) {
        if (texto == null) return "";
        for (String parte : sinAcentos(texto).split("[^A-Z0-9]+"))
            if (!parte.isEmpty()) return parte;
        return "";
    }

    private static String sinAcentos(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }
}
