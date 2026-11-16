package api.aura.service.modulo_planificador.estudio;

import api.aura.config.ErrorApi;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Motor de los estudios fotovoltaicos: finanzas calibradas con un informe real, simulación y lectura de ficheros. */
class EstudioMotorTest {

    private static Curva laboral(double kwhDia) {
        double[] k = new double[Curva.HORAS];
        for (int i = 0; i < Curva.HORAS; i++) {
            int h = Curva.hora(i);
            k[i] = Curva.laborable(i) && h >= 8 && h < 18 ? kwhDia / 10.0 : 0.5;
        }
        return new Curva(k, false, "prueba");
    }

    // ------------------------------------------------------------------ finanzas

    @Test
    void lasFinanzasSalenComoEnElInformeDeReferencia() {
        // Eiffage: 1.000,3 kWp a 0,85 €/Wp, 814.705 kWh solares consumidos, 0,10 €/kWh, 8.330 €/año de mantenimiento
        var e = Finanzas.Entrada.conValoresPorDefecto(850_263.5, 814_705, 0, 0.10, 8_330, 0);
        var r = Finanzas.calcular(e);
        assertEquals(73_140, r.ahorroPrimerAno(), 5);
        assertEquals(10.3, r.retorno(), 0.05);
        assertEquals(14.1, r.retornoDescontado(), 0.05);
        assertEquals(495_000, r.van(), 1_000);
        assertEquals(0.096, r.tir(), 0.001);
        assertEquals(2_548_000, r.ahorroAcumulado(), 1_500);
        assertEquals(26, r.acumulado().length);
        assertEquals(-850_263.5, r.acumulado()[0], 0.01);
    }

    @Test
    void unPrecioDeEnergiaMasAltoAcortaElRetorno() {
        var e = Finanzas.Entrada.conValoresPorDefecto(850_263.5, 814_705, 0, 0.10, 8_330, 0);
        assertEquals(8.3, Finanzas.calcular(e.conPrecio(0.125)).retorno(), 0.1);
        assertEquals(12.5, Finanzas.calcular(e.conPrecio(0.125)).tir() * 100, 0.2);
    }

    @Test
    void siNuncaSeRecuperaNoHayRetorno() {
        var r = Finanzas.calcular(Finanzas.Entrada.conValoresPorDefecto(1_000_000, 1_000, 0, 0.10, 500, 0));
        assertTrue(Double.isNaN(r.retorno()));
        assertNull(Finanzas.aMapa(Finanzas.Entrada.conValoresPorDefecto(1_000_000, 1_000, 0, 0.10, 500, 0), r).get("retorno"));
    }

    // ------------------------------------------------------------------ simulación

    @Test
    void laProduccionAnualEsLaEspecificaPorLosKwp() {
        double[] p = Simulacion.produccionPorKwp(1420, "este-oeste");
        double suma = 0;
        for (double v : p) suma += v;
        assertEquals(1420, suma, 0.5);
        assertEquals(0, p[Curva.indice(5, 15, 2)], 1e-9, "de noche no hay producción");
        assertTrue(p[Curva.indice(5, 15, 13)] > p[Curva.indice(11, 15, 13)], "en junio produce más que en diciembre");
    }

    @Test
    void sinBateriaLoQueNoSeConsumeSeRecorta() {
        Curva c = laboral(400);
        var r = Simulacion.simular(c, Simulacion.Instalacion.de(100, 0, "sur", null));
        assertEquals(r.produccion(), r.directa() + r.recortada(), 1e-6);
        assertTrue(r.directa() <= c.total());
        assertEquals(0, r.desdeBateria(), 1e-9);
        assertEquals(0, r.ciclos(), 1e-9);
        assertEquals(100 * 1685, r.produccion(), 5);
    }

    @Test
    void laBateriaAumentaLaCoberturaYGuardaLaEnergia() {
        Curva c = laboral(300);
        var sin = Simulacion.simular(c, Simulacion.Instalacion.de(150, 0, "sur", null));
        var con = Simulacion.simular(c, Simulacion.Instalacion.de(150, 200, "sur", null));
        assertTrue(con.cobertura() > sin.cobertura());
        assertTrue(con.desdeBateria() > 0);
        assertTrue(con.ciclos() > 0 && con.ciclos() < 400);
        // Lo que sale de la batería nunca supera lo que entró por el rendimiento de ida y vuelta
        double entrada = sin.recortada() - con.recortada();
        assertTrue(con.desdeBateria() <= entrada * 0.90 + 1e-6, "rendimiento de ida y vuelta");
        assertTrue(con.solarConsumida() <= con.produccion() + 1e-6);
    }

    @Test
    void masPanelesNuncaBajanLaProduccionAprovechadaTotal() {
        Curva c = laboral(500);
        double antes = 0;
        for (double kwp : new double[]{50, 100, 150, 200, 300}) {
            double v = Simulacion.simular(c, Simulacion.Instalacion.de(kwp, 0, "este-oeste", null)).solarConsumida();
            assertTrue(v >= antes);
            antes = v;
        }
    }

    // ------------------------------------------------------------------ curva real

    @Test
    void leeLaCurvaHorariaDeUnCsvConHoraDel1Al24() {
        StringBuilder sb = new StringBuilder("CUPS;Fecha;Hora;Consumo_kWh;Metodo\n");
        for (LocalDate d = LocalDate.of(2025, 1, 1); d.isBefore(LocalDate.of(2026, 1, 1)); d = d.plusDays(1)) {
            for (int h = 1; h <= 24; h++) {
                sb.append("ES001;").append(String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()))
                        .append(';').append(h).append(';').append("1,5;R\n");
            }
        }
        Curva c = Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "curva.csv");
        assertEquals(8760 * 1.5, c.total(), 1e-6);
        assertFalse(c.estimada());
    }

    @Test
    void sumaLosCuartosDeHoraYSeQuedaConElUltimoAno() {
        StringBuilder sb = new StringBuilder("Fecha,Consumo kWh\n");
        for (LocalDate d = LocalDate.of(2024, 6, 1); d.isBefore(LocalDate.of(2026, 1, 1)); d = d.plusDays(1)) {
            for (int q = 0; q < 96; q++) {
                sb.append(d).append(' ').append(String.format("%d:%02d", q / 4, (q % 4) * 15)).append(",0.25\n");
            }
        }
        Curva c = Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "cuartos.csv");
        assertEquals(8760.0, c.total(), 30.0, "24 kWh al día durante un año (sin el 29 de febrero)");
    }

    @Test
    void unaCurvaIncompletaSeRechazaConUnMensaje() {
        StringBuilder sb = new StringBuilder("Fecha;Hora;kWh\n");
        for (int d = 1; d <= 20; d++) for (int h = 1; h <= 24; h++) sb.append(String.format("%02d/01/2025;%d;1\n", d, h));
        ErrorApi e = assertThrows(ErrorApi.class, () -> Curva.leer(sb.toString().getBytes(StandardCharsets.UTF_8), "x.csv"));
        assertTrue(e.getMessage().contains("cubre"));
        ErrorApi sinColumnas = assertThrows(ErrorApi.class, () -> Curva.leer("a;b\n1;2\n".getBytes(StandardCharsets.UTF_8), "y.csv"));
        assertTrue(sinColumnas.getMessage().contains("columnas"));
    }

    @Test
    void laCurvaSeGuardaYSeRecuperaSinPerdidaUtil() {
        Curva c = laboral(400);
        Curva otra = Curva.desdeTexto(c.comoTexto(), false, "x");
        assertEquals(c.total(), otra.total(), 5);
    }

    // ------------------------------------------------------------------ SIPS

    private static byte[] sipsDePrueba(int lecturas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet h = wb.createSheet("sips");
            h.createRow(0).createCell(0).setCellValue("Datos suministro");
            Row cab = h.createRow(1);
            String[] nombres = {"", "Distribuidora", "Tarifa", "Potencia Contratada P1", "Potencia Contratada P2", "Potencia Contratada P3",
                    "Potencia Contratada P4", "Potencia Contratada P5", "Potencia Contratada P6", "kWh Anual"};
            for (int i = 0; i < nombres.length; i++) cab.createCell(i).setCellValue(nombres[i]);
            Row val = h.createRow(2);
            val.createCell(0).setCellValue("ES0000000000000000AA");
            val.createCell(1).setCellValue("i-DE");
            val.createCell(2).setCellValue("3.0TD");
            for (int p = 0; p < 6; p++) val.createCell(3 + p).setCellValue(21);
            val.createCell(9).setCellValue(124494);
            h.createRow(3).createCell(0).setCellValue("Datos lecturas");
            Row cl = h.createRow(4);
            cl.createCell(1).setCellValue("Fecha Lectura");
            cl.createCell(2).setCellValue("Fecha Lectura Anterior");
            for (int p = 0; p < 6; p++) cl.createCell(4 + p).setCellValue("Consumo Activa P" + (p + 1));
            LocalDate fin = LocalDate.of(2025, 12, 31);
            for (int m = 0; m < lecturas; m++) {
                LocalDate hasta = fin.minusMonths(m).withDayOfMonth(fin.minusMonths(m).lengthOfMonth());
                LocalDate desde = hasta.minusMonths(1).withDayOfMonth(hasta.minusMonths(1).lengthOfMonth());
                Row r = h.createRow(5 + m);
                r.createCell(0).setCellValue("ES0000000000000000AA");
                r.createCell(1).setCellValue(hasta.toEpochDay() + 25569);
                r.createCell(2).setCellValue(desde.toEpochDay() + 25569);
                for (int p = 0; p < 6; p++) r.createCell(4 + p).setCellValue(p == 2 ? 3000 : 1000);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void leeElSipsYEstimaLaCurvaConSusLecturas() throws Exception {
        Sips s = Sips.leer(sipsDePrueba(12));
        assertEquals("3.0TD", s.suministro().get("Tarifa"));
        assertEquals("ES0000000000000000AA", s.suministro().get("CUPS"));
        assertEquals(21, s.potencias()[5], 1e-9);
        assertEquals(124494, s.kwhAnual(), 1e-9);
        assertEquals(12, s.lecturas().size());
        List<Sips.Lectura> otra = Sips.lecturasDeMapa(s.aMapa().get("lecturas"));
        assertEquals(12, otra.size());
        Curva c = CurvaEstimada.crear(s.lecturas(), s.kwhAnual(), CurvaEstimada.Horario.porDefecto());
        assertTrue(c.estimada());
        assertEquals(12 * 8000, c.total(), 12 * 8000 * 0.01);
        assertTrue(c.fraccionDiurna() > 0.6, "con horario de 7 a 19 h casi todo cae de día");
    }

    @Test
    void conPocasLecturasNoSeInventaLaCurva() throws Exception {
        Sips s = Sips.leer(sipsDePrueba(3));
        ErrorApi e = assertThrows(ErrorApi.class, () -> CurvaEstimada.crear(s.lecturas(), s.kwhAnual(), CurvaEstimada.Horario.porDefecto()));
        assertTrue(e.getMessage().contains("faltan meses"));
        assertThrows(ErrorApi.class, () -> Sips.leer("no es un excel".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void unHorarioAbsurdoSeRechaza() throws Exception {
        Sips s = Sips.leer(sipsDePrueba(12));
        assertThrows(ErrorApi.class, () -> CurvaEstimada.crear(s.lecturas(), 0, new CurvaEstimada.Horario(19, 7, 0.1, 0.1)));
        List<Sips.Lectura> sin = new ArrayList<>();
        assertEquals(124494, CurvaEstimada.crear(sin, 124494, CurvaEstimada.Horario.porDefecto()).total(), 1);
    }
}
