package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_DevolucionMaterial;
import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.entity.modulo_planificador.pl_TareaProyecto;
import api.aura.repository.modulo_planificador.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static api.aura.service.modulo_planificador.TareaProyectoService.invalido;

/**
 * Revisión de material: lo que sobra de la instalación vuelve al almacén (equivale a material.py).
 *
 * <p>Solo puede sobrar material de los productos que lleva el presupuesto del proyecto (el inicial más sus cambios), y como máximo lo que
 * el proyecto emplea de cada uno. Cada devolución suma al stock del producto en el inventario y queda registrada en el proyecto; quitarla
 * la resta otra vez, así que el stock siempre cuadra con lo devuelto.
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    /** La tarea que enseña esta pantalla en el panel (`tipo`). */
    static final String TIPO_MATERIAL = "material";
    /** Categorías que son trabajo o gestión, no material que se pueda guardar en el almacén. */
    private static final Set<String> CATEGORIAS_DE_SERVICIO = Set.of("mano_obra", "logistica", "proyecto", "administrativos");
    private static final String SIN_IDENTIFICAR = "Sin identificar";

    private final pl_ProyectoRepository proyectos;
    private final pl_EquipoRepository equipos;
    private final pl_TareaProyectoRepository tareas;
    private final pl_DevolucionMaterialRepository devoluciones;
    private final PresupuestoProyectoService presupuestoProyecto;
    private final ActividadService actividad;

    private static double r3(double v) {
        return Numeros.redondear(v, 3);
    }

    private pl_Proyecto activo(String id) {
        pl_Proyecto p = proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        if (p.getEliminadoEl() != null) {
            throw new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado");
        }
        return p;
    }

    private record Usado(pl_Equipo equipo, double usado) {
    }

    /** Productos del inventario que emplea el proyecto, con la cantidad vigente del presupuesto (sumada por producto). */
    @SuppressWarnings("unchecked")
    private List<Usado> usados(pl_Proyecto p) {
        Map<String, Object> presupuesto;
        try {
            presupuesto = presupuestoProyecto.calcular(p.getPresupuesto(), p.getCambios());
        } catch (ErrorApi e) {
            return List.of();
        }
        if (!Boolean.TRUE.equals(presupuesto.get("configured"))) {
            return List.of();
        }
        Map<String, Double> cantidades = new LinkedHashMap<>();
        for (Map<String, Object> linea : (List<Map<String, Object>>) presupuesto.get("lines")) {
            Object itemId = linea.get("itemId");
            double actual = ((Number) linea.get("current_qty")).doubleValue();
            if (itemId != null && !"".equals(itemId) && actual > 0) {
                cantidades.merge(String.valueOf(itemId), actual, Double::sum);
            }
        }
        Map<String, pl_Equipo> articulos = new HashMap<>();
        if (!cantidades.isEmpty()) {
            equipos.findAllById(cantidades.keySet()).forEach(e -> articulos.put(e.getId(), e));
        }
        List<Usado> filas = new ArrayList<>();
        cantidades.forEach((id, cantidad) -> {
            pl_Equipo e = articulos.get(id);
            if (e != null && !CATEGORIAS_DE_SERVICIO.contains(e.getCategoria())) {
                filas.add(new Usado(e, r3(cantidad)));
            }
        });
        filas.sort(Comparator.comparingInt((Usado u) -> u.equipo().getPosicion()).thenComparing(u -> u.equipo().getModelo()));
        return filas;
    }

    /** Lo que enseña la pantalla: cada producto usado con lo que emplea, lo devuelto y lo que aún puede devolverse. */
    Map<String, Object> vista(pl_Proyecto p) {
        List<pl_DevolucionMaterial> entradas = devoluciones.findByProyectoIdOrderByFechaDesc(p.getId());
        Map<String, Double> devuelto = new HashMap<>();
        for (pl_DevolucionMaterial e : entradas) {
            devuelto.merge(e.getEquipoId(), e.getCantidad(), Double::sum);
        }
        List<Map<String, Object>> productos = new ArrayList<>();
        for (Usado u : usados(p)) {
            pl_Equipo e = u.equipo();
            double de_vuelta = r3(devuelto.getOrDefault(e.getId(), 0.0));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("brand", e.getMarca());
            m.put("model", e.getModelo());
            m.put("category", e.getCategoria());
            m.put("unit", unidad(e.getUnidad()));
            m.put("used", u.usado());
            m.put("returned", de_vuelta);
            m.put("available", r3(Math.max(0.0, u.usado() - de_vuelta)));
            m.put("stock", r3(e.getStock()));
            productos.add(m);
        }
        List<Map<String, Object>> filas = new ArrayList<>();
        for (pl_DevolucionMaterial e : entradas) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("equipment_id", e.getEquipoId());
            m.put("label", e.getEtiqueta());
            m.put("unit", e.getUnidad());
            m.put("qty", e.getCantidad());
            m.put("note", e.getNota());
            m.put("by", e.getPor());
            m.put("at", Mapas.hora(e.getFecha()));
            filas.add(m);
        }
        Map<String, Object> salida = new LinkedHashMap<>();
        boolean conLineas = p.getPresupuesto() != null && p.getPresupuesto().get("lines") instanceof List<?> l && !l.isEmpty();
        salida.put("configured", !productos.isEmpty() || conLineas);
        salida.put("products", productos);
        salida.put("returns", filas);
        return salida;
    }

    private static String unidad(String u) {
        return u == null || u.isEmpty() ? "ud" : u;
    }

    private static String etiqueta(pl_Equipo e) {
        if (!e.getMarca().isEmpty() && e.getModelo().toLowerCase().startsWith(e.getMarca().toLowerCase())) {
            return e.getModelo();
        }
        String t = String.join(" ", Arrays.stream(new String[]{e.getMarca(), e.getModelo()}).filter(s -> !s.isEmpty()).toList());
        return t.isEmpty() ? e.getId() : t;
    }

    private pl_TareaProyecto tareaDeMaterial(String proyectoId) {
        return tareas.findByProyectoId(proyectoId).stream().filter(t -> TIPO_MATERIAL.equals(t.getTipo())).findFirst().orElse(null);
    }

    private static String actor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null || u.nombre().isBlank() ? SIN_IDENTIFICAR : u.nombre();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ver(String proyectoId) {
        return vista(activo(proyectoId));
    }

    /** Devuelve al almacén material sobrante: suma al stock del producto, solo de productos del proyecto y como máximo lo que emplea. */
    @Transactional
    public Map<String, Object> devolver(String proyectoId, Map<String, Object> cuerpo) {
        pl_Proyecto p = activo(proyectoId);
        Map<String, Object> vista = vista(p);
        String equipoId = String.valueOf(cuerpo.get("equipment_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> producto = ((List<Map<String, Object>>) vista.get("products")).stream()
                .filter(x -> equipoId.equals(x.get("id"))).findFirst().orElse(null);
        if (producto == null) {
            throw invalido("Ese producto no se emplea en este proyecto: solo puede sobrar material de lo que lleva su presupuesto");
        }
        double cantidad;
        try {
            cantidad = r3(Numeros.aNumero(cuerpo.get("qty")));
        } catch (NumberFormatException e) {
            throw invalido("La cantidad tiene que ser un número");
        }
        if (cantidad <= 0) {
            throw invalido("La cantidad tiene que ser mayor que cero");
        }
        double disponible = ((Number) producto.get("available")).doubleValue();
        if (cantidad > disponible + 1e-9) {
            throw invalido("No puede sobrar más de lo que se ha empleado: el proyecto lleva " + Numeros.g(((Number) producto.get("used")).doubleValue()) + " "
                    + producto.get("unit") + ", ya se han devuelto " + Numeros.g(((Number) producto.get("returned")).doubleValue()) + " y quedan "
                    + Numeros.g(disponible));
        }
        pl_Equipo articulo = equipos.findById(equipoId).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Producto no encontrado en el inventario"));
        String actor = actor();
        articulo.setStock(r3(articulo.getStock() + cantidad));
        pl_DevolucionMaterial entrada = new pl_DevolucionMaterial();
        entrada.setId(UUID.randomUUID().toString());
        entrada.setProyectoId(p.getId());
        entrada.setEquipoId(articulo.getId());
        entrada.setEtiqueta(etiqueta(articulo));
        entrada.setUnidad(unidad(articulo.getUnidad()));
        entrada.setCantidad(cantidad);
        String nota = cuerpo.get("note") == null ? "" : String.valueOf(cuerpo.get("note")).strip();
        entrada.setNota(nota);
        entrada.setPor(actor);
        entrada.setFecha(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        equipos.save(articulo);
        devoluciones.save(entrada);
        actividad.registrar(p.getId(), actor, "material",
                "Devolvió al almacén " + Numeros.g(cantidad) + " " + entrada.getUnidad() + " de " + entrada.getEtiqueta(),
                tareaDeMaterial(p.getId()), "Stock: " + Numeros.g(articulo.getStock()) + (nota.isEmpty() ? "" : " · " + nota));
        return vista(p);
    }

    /** Deshace una devolución: resta del stock lo que sumó (no se puede si el producto ya no tiene tanto en el almacén). */
    @Transactional
    public Map<String, Object> deshacer(String proyectoId, String devolucionId) {
        pl_Proyecto p = activo(proyectoId);
        pl_DevolucionMaterial entrada = devoluciones.findById(devolucionId).filter(e -> e.getProyectoId().equals(p.getId()))
                .orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Devolución no encontrada"));
        pl_Equipo articulo = equipos.findById(entrada.getEquipoId()).orElse(null);
        if (articulo != null) {
            if (articulo.getStock() + 1e-9 < entrada.getCantidad()) {
                throw new ErrorApi(HttpStatus.CONFLICT, "No se puede deshacer: en el almacén hay " + Numeros.g(articulo.getStock()) + " " + entrada.getUnidad()
                        + " y esta devolución sumó " + Numeros.g(entrada.getCantidad()) + " (parte ya se ha gastado)");
            }
            articulo.setStock(r3(Math.max(0.0, articulo.getStock() - entrada.getCantidad())));
            equipos.save(articulo);
        }
        actividad.registrar(p.getId(), actor(), "material",
                "Deshizo la devolución de " + Numeros.g(entrada.getCantidad()) + " " + entrada.getUnidad() + " de " + entrada.getEtiqueta(),
                tareaDeMaterial(p.getId()), articulo != null ? "Stock: " + Numeros.g(articulo.getStock()) : "");
        devoluciones.delete(entrada);
        return vista(p);
    }
}
