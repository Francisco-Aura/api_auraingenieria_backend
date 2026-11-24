package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Equipo;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * El inventario del presupuestador en un Excel: una hoja con todo, una hoja por categoría (con las características propias de cada
 * una, en columnas) y otra con los precios de cada proveedor. Es una copia para leer o filtrar: no se vuelve a importar.
 */
@Component
public class InventarioExcel {

    private static final String PREFIJO_EXTRA = "x:";
    private static final String SEPARADOR_EXTRA = "::";
    private static final Pattern NUMERO = Pattern.compile("^-?(0|[1-9]\\d*)([.,]\\d+)?$");
    private static final int ANCHO_MAX = 55;

    /** Una columna: su título y cómo se saca su valor de cada producto. */
    private record Columna(String titulo, java.util.function.Function<pl_Equipo, Object> valor) {
    }

    public byte[] generar(List<pl_Equipo> equipos) {
        List<Map<String, Object>> categorias = CatalogoEquipos.categorias();
        Map<String, String> etiquetas = new LinkedHashMap<>();
        for (Map<String, Object> c : categorias) etiquetas.put(String.valueOf(c.get("key")), String.valueOf(c.get("label")));
        Map<String, String> partidas = new HashMap<>();
        CatalogoEquipos.PARTIDAS.forEach(p -> partidas.put(p.key(), p.label()));

        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);

            // 1) Todo, con las columnas comunes
            List<Columna> comunes = columnasComunes(etiquetas, partidas);
            hoja(libro, estilos, "Todo", comunes, equipos);

            // 2) Una hoja por categoría, con sus características
            for (Map<String, Object> c : categorias) {
                String clave = String.valueOf(c.get("key"));
                List<pl_Equipo> deLaCategoria = equipos.stream().filter(e -> clave.equals(e.getCategoria())).toList();
                if (deLaCategoria.isEmpty()) continue;
                List<Columna> columnas = new ArrayList<>(List.of(
                        new Columna("Marca", pl_Equipo::getMarca), new Columna("Modelo", pl_Equipo::getModelo),
                        new Columna("Proveedor", pl_Equipo::getProveedor), new Columna("Unidad", pl_Equipo::getUnidad),
                        new Columna("Precio unitario (€)", pl_Equipo::getPrecioUnitario), new Columna("Stock", pl_Equipo::getStock)));
                Set<String> usadas = new HashSet<>();
                // Primero las características por las que se filtra (potencia, tipo…) y luego las demás de la ficha
                List<Map<String, Object>> campos = new ArrayList<>();
                @SuppressWarnings("unchecked") List<Map<String, Object>> filtros = (List<Map<String, Object>>) c.get("filters");
                @SuppressWarnings("unchecked") List<Map<String, Object>> specs = (List<Map<String, Object>>) c.get("specs");
                campos.addAll(filtros);
                campos.addAll(specs);
                for (Map<String, Object> campo : campos) {
                    String k = String.valueOf(campo.get("key"));
                    if (!usadas.add(k)) continue;
                    columnas.add(new Columna(String.valueOf(campo.get("label")), e -> e.getAtributos().get(k)));
                }
                // Características añadidas a mano por quien da de alta el producto (claves «x:Grupo::Nombre»)
                Set<String> extras = new TreeSet<>();
                for (pl_Equipo e : deLaCategoria) {
                    for (String k : e.getAtributos().keySet()) {
                        if (!usadas.contains(k) && !vacio(e.getAtributos().get(k))) extras.add(k);
                    }
                }
                for (String k : extras) columnas.add(new Columna(tituloExtra(k), e -> e.getAtributos().get(k)));
                columnas.add(new Columna("Notas", pl_Equipo::getNotas));
                columnas.add(new Columna("Ficha técnica", e -> nombreFicha(e.getFichaTecnica())));
                hoja(libro, estilos, nombreHoja(clave.equals("") ? "Otros" : etiquetas.getOrDefault(clave, clave), libro), columnas, deLaCategoria);
            }
            // Productos de una categoría que ya no existe
            List<pl_Equipo> huerfanos = equipos.stream().filter(e -> !etiquetas.containsKey(e.getCategoria())).toList();
            if (!huerfanos.isEmpty()) hoja(libro, estilos, nombreHoja("Otros", libro), comunes, huerfanos);

            // 3) Precios de cada proveedor
            hojaOfertas(libro, estilos, equipos, etiquetas);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel: " + e.getMessage(), e);
        }
    }

    private List<Columna> columnasComunes(Map<String, String> etiquetas, Map<String, String> partidas) {
        return List.of(
                new Columna("Categoría", e -> etiquetas.getOrDefault(e.getCategoria(), e.getCategoria())),
                new Columna("Partida del presupuesto", e -> partidas.getOrDefault(e.getPartida(), e.getPartida())),
                new Columna("Marca", pl_Equipo::getMarca), new Columna("Modelo", pl_Equipo::getModelo),
                new Columna("Proveedor", pl_Equipo::getProveedor), new Columna("Unidad", pl_Equipo::getUnidad),
                new Columna("Precio unitario (€)", pl_Equipo::getPrecioUnitario),
                new Columna("Por módulo", e -> e.isPorModulo() ? "Sí" : "No"),
                new Columna("Cantidad por defecto", pl_Equipo::getCantidadDefecto),
                new Columna("Stock", pl_Equipo::getStock), new Columna("Stock mínimo", pl_Equipo::getStockMinimo),
                new Columna("Proveedores con precio", e -> e.getOfertas() == null ? 0 : e.getOfertas().size()),
                new Columna("Precio revisado el", e -> e.getPrecioRevisadoEl() == null ? "" : e.getPrecioRevisadoEl().toString()),
                new Columna("Ficha técnica", e -> nombreFicha(e.getFichaTecnica())),
                new Columna("Muestra", e -> e.isMuestra() ? "Sí" : ""),
                new Columna("Notas", pl_Equipo::getNotas));
    }

    private void hoja(XSSFWorkbook libro, Estilos estilos, String nombre, List<Columna> columnas, List<pl_Equipo> filas) {
        Sheet h = libro.createSheet(nombre);
        Row cab = h.createRow(0);
        int[] ancho = new int[columnas.size()];
        for (int c = 0; c < columnas.size(); c++) {
            Cell celda = cab.createCell(c);
            celda.setCellValue(columnas.get(c).titulo());
            celda.setCellStyle(estilos.cabecera);
            ancho[c] = columnas.get(c).titulo().length();
        }
        int r = 1;
        for (pl_Equipo e : filas) {
            Row fila = h.createRow(r++);
            for (int c = 0; c < columnas.size(); c++) {
                ancho[c] = Math.max(ancho[c], escribir(fila.createCell(c), columnas.get(c).valor().apply(e), estilos));
            }
        }
        cerrarHoja(h, columnas.size(), r, ancho);
    }

    private void hojaOfertas(XSSFWorkbook libro, Estilos estilos, List<pl_Equipo> equipos, Map<String, String> etiquetas) {
        String[] titulos = {"Categoría", "Marca", "Modelo", "Proveedor", "Precio (€)", "Unidad", "Referencia del proveedor", "Nota", "Es el del presupuestador"};
        Sheet h = libro.createSheet("Proveedores y precios");
        Row cab = h.createRow(0);
        int[] ancho = new int[titulos.length];
        for (int c = 0; c < titulos.length; c++) {
            Cell celda = cab.createCell(c);
            celda.setCellValue(titulos[c]);
            celda.setCellStyle(estilos.cabecera);
            ancho[c] = titulos[c].length();
        }
        int r = 1;
        for (pl_Equipo e : equipos) {
            List<Map<String, Object>> ofertas = e.getOfertas() == null ? List.of() : e.getOfertas();
            for (Map<String, Object> o : ofertas) {
                Row fila = h.createRow(r++);
                Object[] valores = {etiquetas.getOrDefault(e.getCategoria(), e.getCategoria()), e.getMarca(), e.getModelo(), o.get("supplier"), o.get("price"),
                        e.getUnidad(), o.get("ref"), o.get("note"), String.valueOf(o.get("supplier")).equals(e.getProveedor()) ? "Sí" : ""};
                for (int c = 0; c < valores.length; c++) {
                    ancho[c] = Math.max(ancho[c], escribir(fila.createCell(c), valores[c], estilos));
                }
            }
        }
        cerrarHoja(h, titulos.length, r, ancho);
    }

    private static void cerrarHoja(Sheet h, int columnas, int filas, int[] ancho) {
        for (int c = 0; c < columnas; c++) h.setColumnWidth(c, (Math.min(ancho[c], ANCHO_MAX) + 2) * 256);
        h.createFreezePane(0, 1);
        if (filas > 1) h.setAutoFilter(new CellRangeAddress(0, filas - 1, 0, columnas - 1));
    }

    /** Escribe un valor (número si lo es) y devuelve cuánto ocupa, para el ancho de la columna. */
    private static int escribir(Cell celda, Object valor, Estilos estilos) {
        if (valor == null || "".equals(valor)) return 0;
        if (valor instanceof Number n) {
            celda.setCellValue(n.doubleValue());
            celda.setCellStyle(estilos.numero);
            return String.valueOf(n).length();
        }
        String texto = String.valueOf(valor).strip();
        if (NUMERO.matcher(texto).matches() && texto.length() < 15) {
            celda.setCellValue(Double.parseDouble(texto.replace(',', '.')));
            celda.setCellStyle(estilos.numero);
            return texto.length();
        }
        celda.setCellValue(texto.length() > 32000 ? texto.substring(0, 32000) : texto);
        return Math.min(texto.length(), ANCHO_MAX);
    }

    private static boolean vacio(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    private static String tituloExtra(String clave) {
        String cuerpo = clave.startsWith(PREFIJO_EXTRA) ? clave.substring(PREFIJO_EXTRA.length()) : clave;
        int en = cuerpo.indexOf(SEPARADOR_EXTRA);
        return en > 0 ? cuerpo.substring(0, en) + " · " + cuerpo.substring(en + SEPARADOR_EXTRA.length()) : cuerpo;
    }

    private static String nombreFicha(String ficha) {
        if (ficha == null || ficha.isBlank()) return "";
        return ficha.substring(ficha.lastIndexOf('/') + 1);
    }

    /** Nombre de hoja válido (hasta 31 caracteres, sin : \ / ? * [ ]) y que no se repita. */
    private static String nombreHoja(String nombre, Workbook libro) {
        String limpio = nombre.replaceAll("[:\\\\/?*\\[\\]]", " ").strip();
        if (limpio.isEmpty()) limpio = "Hoja";
        if (limpio.length() > 31) limpio = limpio.substring(0, 31);
        String candidato = limpio;
        for (int i = 2; libro.getSheet(candidato) != null; i++) {
            String sufijo = " " + i;
            candidato = limpio.substring(0, Math.min(limpio.length(), 31 - sufijo.length())) + sufijo;
        }
        return candidato;
    }

    private static final class Estilos {
        final CellStyle cabecera;
        final CellStyle numero;

        Estilos(Workbook libro) {
            Font negrita = libro.createFont();
            negrita.setBold(true);
            cabecera = libro.createCellStyle();
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setBorderBottom(BorderStyle.THIN);
            cabecera.setWrapText(true);
            cabecera.setVerticalAlignment(VerticalAlignment.CENTER);
            numero = libro.createCellStyle();
            numero.setDataFormat(libro.createDataFormat().getFormat("#,##0.00##"));
        }
    }
}
