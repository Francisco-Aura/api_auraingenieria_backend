package api.aura.service.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Meta;
import api.aura.service.modulo_planificador.estudio.Curva;
import api.aura.repository.modulo_planificador.pl_EstudioRepository;
import api.aura.repository.modulo_planificador.pl_MetaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deja guardado un estudio completo para tener con qué probar la pantalla, el cálculo y el informe: una nave industrial en Valencia con
 * tarifa 3.0TD, un año de lecturas del SIPS, precios por periodo y reducción de potencia. Se crea una sola vez (queda anotado en pl_meta)
 * y solo si el estudio no existe; si se borra a mano no se vuelve a crear. Se enciende con {@code planificador.catalogo-inicial=true}.
 * Los datos son verosímiles pero de ejemplo: no pertenecen a ningún cliente real.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "planificador.catalogo-inicial", havingValue = "true")
@RequiredArgsConstructor
public class EstudioDeMuestra implements ApplicationRunner {

    static final String CLAVE = "estudio_muestra_v2";
    static final String ID = "muestra-nave-valencia-0001";

    /** Reparto del consumo anual entre P1..P6 en una nave que trabaja de lunes a viernes de 7 a 19 h. */
    private static final double[] REPARTO = {0.09, 0.15, 0.10, 0.16, 0.07, 0.43};
    /** Peso de cada mes (enero..diciembre): más consumo en verano por el frío. */
    private static final double[] ESTACIONAL = {0.95, 0.90, 0.92, 0.88, 0.95, 1.10, 1.25, 1.15, 1.05, 0.95, 0.92, 0.98};
    private static final double KWH_ANUAL = 148_000;

    private final EstudioService estudios;
    private final pl_EstudioRepository repositorio;
    private final pl_MetaRepository meta;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (meta.existsById(CLAVE)) {
                return;
            }
            crearSiFalta(ID, conUbicacion -> cuerpo(conUbicacion));
            for (Perfil p : Perfil.values()) {
                crearSiFalta(p.id, conUbicacion -> cuerpoConCurva(p, conUbicacion));
            }
            meta.save(new pl_Meta(CLAVE, "ok"));
        } catch (Exception e) {
            LoggerFactory.getLogger("planificador").error("No se pudo crear el estudio de muestra del Planificador: {}", e.toString());
        }
    }

    private void crearSiFalta(String id, java.util.function.Function<Boolean, Map<String, Object>> cuerpo) {
        if (repositorio.existsById(id)) return;
        try {
            estudios.crear(cuerpo.apply(true));
        } catch (RuntimeException sinRed) {
            // La producción solar real viene de PVGIS: sin conexión se guarda sin ubicación (modelo sencillo)
            LoggerFactory.getLogger("planificador").warn("Estudio de muestra {}: se guarda sin ubicación ({})", id, sinRed.toString());
            estudios.crear(cuerpo.apply(false));
        }
    }

    /** Ejemplos con curva cuartohoraria (la que descarga la distribuidora), cada uno con un perfil de consumo distinto. */
    enum Perfil {
        FRIO("muestra-frio-castellon-0002", "Frigoríficos del Mijares, S.A. (ejemplo)", "Cámaras frigoríficas, fotovoltaica",
                "1020 - Conservación de pescados", "Castellón de la Plana", "Castellón", 39.986, -0.037, 150, 0, 0.74, 7),
        OFICINAS("muestra-oficinas-madrid-0003", "Colegio San Isidro (ejemplo)", "Colegio, autoconsumo",
                "8520 - Educación primaria", "Alcobendas", "Madrid", 40.547, -3.642, 80, 0, 0.80, 11),
        HOTEL("muestra-hotel-malaga-0004", "Hotel Playa Dorada (ejemplo)", "Hotel con batería",
                "5510 - Hoteles y alojamientos similares", "Torremolinos", "Málaga", 36.62, -4.499, 100, 80, 0.82, 23);

        final String id, cliente, proyecto, cnae, localidad, provincia;
        final double lat, lon, precioWp;
        final int kwContratados, bateria, semilla;

        Perfil(String id, String cliente, String proyecto, String cnae, String localidad, String provincia,
               double lat, double lon, int kw, int bateria, double precioWp, int semilla) {
            this.id = id;
            this.cliente = cliente;
            this.proyecto = proyecto;
            this.cnae = cnae;
            this.localidad = localidad;
            this.provincia = provincia;
            this.lat = lat;
            this.lon = lon;
            this.kwContratados = kw;
            this.bateria = bateria;
            this.precioWp = precioWp;
            this.semilla = semilla;
        }
    }

    static Map<String, Object> cuerpoConCurva(Perfil p, boolean conUbicacion) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("kind", "potencial");
        cliente.put("name", p.cliente);
        cliente.put("cif", "");
        cliente.put("phone", "");
        cliente.put("email", "");

        Map<String, Object> ins = new LinkedHashMap<>();
        ins.put("kwp", 0);
        ins.put("bateriaKwh", p.bateria);
        ins.put("orientacion", "sur");
        if (conUbicacion) {
            ins.put("latitud", p.lat);
            ins.put("longitud", p.lon);
        }

        Map<String, Object> eco = new LinkedHashMap<>();
        eco.put("modoPrecio", "periodos");
        eco.put("incluyePeajes", false);
        eco.put("preciosPeriodo", List.of(0.148, 0.125, 0.101, 0.090, 0.078, 0.071));
        eco.put("reduccionPotencia", List.of(0, 0, 0, 0, 0, 0));
        eco.put("precioWp", p.precioWp);
        eco.put("mantenimientoPct", 1.0);

        List<Integer> potencias = List.of(p.kwContratados, p.kwContratados, p.kwContratados, p.kwContratados, p.kwContratados, p.kwContratados);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("cups", "ES0021000000000" + String.format("%03d", p.semilla) + "XX");
        datos.put("tarifa", "3.0TD");
        datos.put("localidad", p.localidad);
        datos.put("provincia", p.provincia);
        datos.put("cnae", p.cnae);
        datos.put("potencias", potencias);
        datos.put("instalacion", ins);
        datos.put("economia", eco);

        String nombre = "curva-cuartohoraria-" + p.id + ".csv";
        Curva curva = Curva.leer(csvCuartoHorario(p), nombre);
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("texto", curva.comoTexto());
        c.put("estimada", false);
        c.put("origen", nombre);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", p.id);
        cuerpo.put("customer", cliente);
        cuerpo.put("proyecto", p.proyecto);
        cuerpo.put("observaciones", "Estudio de ejemplo con curva cuartohoraria (35.040 lecturas): datos verosímiles, no de un cliente real.");
        cuerpo.put("datos", datos);
        cuerpo.put("curva", c);
        return cuerpo;
    }

    /** Un año (2025) de consumo cada 15 minutos, en el formato de un CSV de distribuidora: «Fecha;Consumo kWh». */
    static byte[] csvCuartoHorario(Perfil p) {
        java.util.Random azar = new java.util.Random(p.semilla);
        StringBuilder sb = new StringBuilder(35_040 * 28);
        sb.append("Fecha;Consumo kWh\n");
        java.time.LocalDateTime t = java.time.LocalDateTime.of(2025, 1, 1, 0, 0);
        java.time.LocalDateTime fin = java.time.LocalDateTime.of(2026, 1, 1, 0, 0);
        double ruidoDia = 1;
        while (t.isBefore(fin)) {
            if (t.getHour() == 0 && t.getMinute() == 0) ruidoDia = 0.92 + 0.16 * azar.nextDouble();
            double hora = t.getHour() + t.getMinute() / 60.0;
            boolean laborable = t.getDayOfWeek().getValue() <= 5;
            double estacion = ESTACIONAL[t.getMonthValue() - 1];
            double kw = switch (p) {
                case FRIO -> {
                    // Compresores que arrancan y paran cada hora más o menos, siempre encendidos; más carga en jornada laborable
                    double ciclo = (t.getMinute() / 15) % 3 == 0 ? 22 : 0;
                    double carga = laborable && hora >= 6 && hora < 20 ? 28 : 6;
                    yield (46 + ciclo + carga) * (0.7 + 0.5 * estacion);
                }
                case OFICINAS -> {
                    boolean lectivo = t.getMonthValue() != 8 && !(t.getMonthValue() == 7 && t.getDayOfMonth() > 20);
                    double actividad = laborable && lectivo ? rampa(hora, 7.5, 9, 17.5, 19) : 0;
                    yield 7 + 58 * actividad * (0.75 + 0.35 * estacion) + (laborable && lectivo ? 0 : 0.5);
                }
                case HOTEL -> {
                    double manana = Math.exp(-Math.pow((hora - 8.5) / 1.6, 2));
                    double noche = Math.exp(-Math.pow((hora - 21) / 2.2, 2));
                    double finde = laborable ? 1 : 1.12;
                    yield (24 + 30 * manana + 46 * noche) * finde * (0.55 + 0.75 * estacion);
                }
            };
            kw *= ruidoDia * (0.96 + 0.08 * azar.nextDouble());
            sb.append(String.format(java.util.Locale.ROOT, "%tY-%<tm-%<td %<tH:%<tM;%.3f%n", t, kw / 4));
            t = t.plusMinutes(15);
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 0 fuera del horario, 1 en plena jornada, con rampas de subida y de bajada. */
    private static double rampa(double h, double ini, double plena, double finPlena, double fin) {
        if (h <= ini || h >= fin) return 0;
        if (h < plena) return (h - ini) / (plena - ini);
        if (h > finPlena) return (fin - h) / (fin - finPlena);
        return 1;
    }

    static Map<String, Object> cuerpo(boolean conUbicacion) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("kind", "potencial");
        cliente.put("name", "Industrias Ribera, S.L. (ejemplo)");
        cliente.put("cif", "");
        cliente.put("phone", "");
        cliente.put("email", "");

        Map<String, Object> ins = new LinkedHashMap<>();
        ins.put("kwp", 0); // automática: la propone el cálculo según la curva
        ins.put("bateriaKwh", 0);
        ins.put("orientacion", "este-oeste");
        if (conUbicacion) {
            ins.put("latitud", 39.47);
            ins.put("longitud", -0.376);
        }

        Map<String, Object> eco = new LinkedHashMap<>();
        eco.put("modoPrecio", "periodos");
        eco.put("incluyePeajes", false);
        eco.put("preciosPeriodo", List.of(0.142, 0.121, 0.098, 0.087, 0.075, 0.069));
        eco.put("reduccionPotencia", List.of(0, 0, 0, 0, 0, 0));
        eco.put("precioWp", 0.78);
        eco.put("mantenimientoPct", 1.0);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("cups", "ES0021000000000001XX");
        datos.put("tarifa", "3.0TD");
        datos.put("localidad", "Paterna");
        datos.put("provincia", "Valencia");
        datos.put("cnae", "2511 - Fabricación de estructuras metálicas");
        datos.put("potencias", List.of(120, 120, 120, 120, 120, 160));
        datos.put("instalacion", ins);
        datos.put("economia", eco);
        datos.put("sips", sips());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", ID);
        cuerpo.put("customer", cliente);
        cuerpo.put("proyecto", "Nave de producción, fotovoltaica");
        cuerpo.put("observaciones", "Estudio de ejemplo para probar el Planificador: datos verosímiles, no de un cliente real.");
        cuerpo.put("datos", datos);
        return cuerpo;
    }

    /** Doce lecturas mensuales que terminan el primer día del mes actual, con el consumo repartido por periodos. */
    private static Map<String, Object> sips() {
        LocalDate fin = LocalDate.now().withDayOfMonth(1);
        double sumaPesos = 0;
        for (double p : ESTACIONAL) sumaPesos += p;
        List<Map<String, Object>> lecturas = new ArrayList<>();
        double total = 0;
        for (int i = 11; i >= 0; i--) {
            LocalDate hasta = fin.minusMonths(i);
            LocalDate desde = hasta.minusMonths(1);
            double mes = KWH_ANUAL * ESTACIONAL[desde.getMonthValue() - 1] / sumaPesos;
            List<Double> kwh = new ArrayList<>();
            for (double r : REPARTO) {
                double v = Math.round(mes * r);
                kwh.add(v);
                total += v;
            }
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("desde", desde.toString());
            l.put("hasta", hasta.toString());
            l.put("kwh", kwh);
            lecturas.add(l);
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("potencias", List.of(120, 120, 120, 120, 120, 160));
        s.put("kwhAnual", total);
        s.put("lecturas", lecturas);
        return s;
    }
}
