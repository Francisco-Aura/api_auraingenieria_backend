package api.aura.util;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Las 52 provincias, con su código (las dos primeras cifras de sus códigos postales) y el
 * nombre que enseña la aplicación en el desplegable «Provincia».
 *
 * <p>La provincia se guarda como texto en puntos de suministro y clientes, y durante años se
 * escribió a mano: «MURCIA», «almeria», «Alacant», «Vizcaya»... {@link #codigoDe(String)}
 * reconoce esas formas (sin mirar mayúsculas, tildes ni signos, y con los nombres en las otras
 * lenguas y los antiguos), de modo que la zona de comisión por provincia funciona también con
 * los datos que aún no se han regularizado.
 */
public final class Provincias {

    /** Código y nombre de una provincia. */
    public record Provincia(String codigo, String nombre) {}

    private static final Map<String, String> NOMBRE_POR_CODIGO = new LinkedHashMap<>();
    private static final Map<String, String> CODIGO_POR_CLAVE = new LinkedHashMap<>();

    static {
        alta("01", "Álava", "Araba", "Araba/Álava", "Vitoria", "Vitoria-Gasteiz");
        alta("02", "Albacete");
        alta("03", "Alicante", "Alacant", "Alicante/Alacant");
        alta("04", "Almería");
        alta("05", "Ávila");
        alta("06", "Badajoz");
        alta("07", "Baleares", "Illes Balears", "Islas Baleares", "Balears", "Mallorca", "Palma de Mallorca", "Palma");
        alta("08", "Barcelona");
        alta("09", "Burgos");
        alta("10", "Cáceres");
        alta("11", "Cádiz");
        alta("12", "Castellón", "Castelló", "Castellón de la Plana", "Castellón/Castelló");
        alta("13", "Ciudad Real");
        alta("14", "Córdoba");
        alta("15", "A Coruña", "La Coruña", "Coruña");
        alta("16", "Cuenca");
        alta("17", "Girona", "Gerona");
        alta("18", "Granada");
        alta("19", "Guadalajara");
        alta("20", "Gipuzkoa", "Guipúzcoa", "San Sebastián", "Donostia");
        alta("21", "Huelva");
        alta("22", "Huesca");
        alta("23", "Jaén");
        alta("24", "León");
        alta("25", "Lleida", "Lérida");
        alta("26", "La Rioja", "Rioja", "Logroño");
        alta("27", "Lugo");
        alta("28", "Madrid", "Comunidad de Madrid");
        alta("29", "Málaga");
        alta("30", "Murcia", "Región de Murcia", "Murcia (Región de)");
        alta("31", "Navarra", "Nafarroa", "Comunidad Foral de Navarra", "Pamplona");
        alta("32", "Ourense", "Orense");
        alta("33", "Asturias", "Principado de Asturias", "Oviedo");
        alta("34", "Palencia");
        alta("35", "Las Palmas", "Palmas", "Las Palmas de Gran Canaria", "Gran Canaria");
        alta("36", "Pontevedra");
        alta("37", "Salamanca");
        alta("38", "Santa Cruz de Tenerife", "Tenerife", "S.C. Tenerife", "Sta. Cruz de Tenerife");
        alta("39", "Cantabria", "Santander");
        alta("40", "Segovia");
        alta("41", "Sevilla");
        alta("42", "Soria");
        alta("43", "Tarragona");
        alta("44", "Teruel");
        alta("45", "Toledo");
        alta("46", "Valencia", "València", "Valencia/València");
        alta("47", "Valladolid");
        alta("48", "Bizkaia", "Vizcaya", "Bilbao");
        alta("49", "Zamora");
        alta("50", "Zaragoza");
        alta("51", "Ceuta");
        alta("52", "Melilla");
    }

    private Provincias() {}

    private static void alta(String codigo, String nombre, String... otrasFormas) {
        NOMBRE_POR_CODIGO.put(codigo, nombre);
        CODIGO_POR_CLAVE.put(clave(nombre), codigo);
        for (String forma : otrasFormas) CODIGO_POR_CLAVE.put(clave(forma), codigo);
    }

    /** Solo letras, en mayúsculas y sin tildes: «  Alicante / Alacant » y «ALICANTEALACANT» son lo mismo. */
    private static String clave(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(Locale.ROOT).replaceAll("[^A-ZÑ]", "");
    }

    /** Las 52, por orden alfabético del nombre (como salen en el desplegable). */
    public static List<Provincia> todas() {
        java.text.Collator orden = java.text.Collator.getInstance(Locale.forLanguageTag("es-ES"));
        return NOMBRE_POR_CODIGO.entrySet().stream()
                .map(e -> new Provincia(e.getKey(), e.getValue()))
                .sorted((a, b) -> orden.compare(a.nombre(), b.nombre()))
                .toList();
    }

    public static boolean existe(String codigo) {
        return codigo != null && NOMBRE_POR_CODIGO.containsKey(codigo);
    }

    /** Nombre de la provincia con ese código; el propio código si no existe. */
    public static String nombre(String codigo) {
        return NOMBRE_POR_CODIGO.getOrDefault(codigo, codigo);
    }

    /** Código de la provincia escrita de cualquiera de las formas conocidas; vacío si no se reconoce. */
    public static Optional<String> codigoDe(String texto) {
        if (texto == null || texto.isBlank()) return Optional.empty();
        return Optional.ofNullable(CODIGO_POR_CLAVE.get(clave(texto)));
    }

    /**
     * El texto con el nombre del desplegable si se reconoce la provincia («MURCIA» pasa a
     * «Murcia»); si no, tal cual (sin espacios sobrantes), para no perder lo que hubiera.
     */
    public static String nombreOficial(String texto) {
        if (texto == null) return null;
        return codigoDe(texto).map(Provincias::nombre).orElse(texto.trim());
    }
}
