package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.config.UsuarioPlanificadorHolder;
import api.aura.entity.modulo_planificador.pl_Proyecto;
import api.aura.repository.modulo_planificador.pl_ProyectoRepository;
import api.aura.service.modulo_planificador.dropbox.DropboxAlmacen;
import api.aura.service.modulo_planificador.dropbox.DropboxError;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Carpetas de Dropbox de los proyectos y funciones encendidas del servidor (equivale a las rutas /dropbox y /features de routes.py). */
@Service
@RequiredArgsConstructor
public class DocumentosService {

    private static final String SIN_CONFIGURAR = "Dropbox no está configurado en este servidor";
    private static final String SIN_IDENTIFICAR = "Sin identificar";

    private final DropboxAlmacen dropbox;
    private final pl_ProyectoRepository proyectos;
    private final ActividadService actividad;
    @Value("${planificador.funciones.presupuestador:false}")
    private boolean presupuestador;
    @Value("${planificador.funciones.inventario:false}")
    private boolean inventario;

    /** Funciones que se pueden encender o apagar en cada servidor (apagadas por defecto). */
    public Map<String, Object> funciones() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgeter", presupuestador);
        // Los precios del presupuestador se editan en el inventario: con el presupuestador encendido, el inventario también
        m.put("inventory", inventario || presupuestador);
        // Solo para pruebas en local en el Planificador actual: aquí el servidor nunca guarda archivos de proyectos
        m.put("local_files", false);
        return m;
    }

    /** Si el servidor guarda los documentos en Dropbox (y en qué carpeta base, útil para distinguir pruebas). */
    public Map<String, Object> estado() {
        boolean activo = dropbox.habilitado();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", activo);
        m.put("base", activo ? dropbox.rutaBase() : "");
        m.put("datasheets", dropbox.fichasHabilitadas());
        return m;
    }

    private void exigirActivo() {
        if (!dropbox.habilitado()) {
            throw new ErrorApi(HttpStatus.CONFLICT, SIN_CONFIGURAR);
        }
    }

    private static ErrorApi deDropbox(DropboxError e, String prefijo) {
        return new ErrorApi(e.getEstado() != null && e.getEstado() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY, prefijo + e.getMessage());
    }

    private static ErrorApi invalido(IllegalArgumentException e) {
        return new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /** Subcarpetas de una carpeta de Dropbox (solo dentro de la carpeta base), para elegir una ya existente. */
    public Map<String, Object> carpetas(String ruta) {
        exigirActivo();
        try {
            return dropbox.listarCarpetas(ruta == null || ruta.isEmpty() ? null : ruta);
        } catch (IllegalArgumentException e) {
            throw invalido(e);
        } catch (DropboxError e) {
            throw deDropbox(e, "No se pudo consultar Dropbox: ");
        }
    }

    /** Cómo quedaría la carpeta del proyecto y si ya existe, para comprobarlo antes de crear el proyecto. */
    public Map<String, Object> vistaPrevia(String nombre, String fechaInicio) {
        exigirActivo();
        LocalDate dia;
        try {
            dia = LocalDate.parse(fechaInicio);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ErrorApi(HttpStatus.UNPROCESSABLE_ENTITY, "La fecha de inicio no es válida");
        }
        try {
            return dropbox.vistaPrevia(dia, nombre);
        } catch (IllegalArgumentException e) {
            throw invalido(e);
        } catch (DropboxError e) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudo consultar Dropbox: " + e.getMessage());
        }
    }

    private static String actor() {
        UsuarioPlanificadorHolder.Usuario u = UsuarioPlanificadorHolder.get();
        return u == null || u.nombre() == null || u.nombre().isBlank() ? SIN_IDENTIFICAR : u.nombre();
    }

    private pl_Proyecto activo(String id) {
        pl_Proyecto p = proyectos.findById(id).orElseThrow(() -> new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        if (p.getEliminadoEl() != null) {
            throw new ErrorApi(HttpStatus.NOT_FOUND, "Proyecto no encontrado");
        }
        return p;
    }

    /** Crea (o reutiliza) la carpeta de Dropbox de un proyecto que no la tiene (reintento, o proyecto antiguo). */
    @Transactional
    public Map<String, Object> crearCarpeta(String id, Map<String, Object> cuerpo) {
        pl_Proyecto p = activo(id);
        exigirActivo();
        if (!p.getRutaDropbox().isEmpty()) {
            throw new ErrorApi(HttpStatus.CONFLICT, "El proyecto ya tiene carpeta de Dropbox");
        }
        String existente = cuerpo.get("existing") == null ? "" : String.valueOf(cuerpo.get("existing")).strip();
        String nombre = cuerpo.get("name") == null ? "" : String.valueOf(cuerpo.get("name"));
        String resumen;
        try {
            if (!existente.isEmpty()) {
                // Una carpeta que ya existe, elegida a mano: se usa tal cual y no se crea nada
                p.setRutaDropbox(dropbox.usarCarpetaExistente(existente));
                resumen = "Usó una carpeta de Dropbox existente";
            } else {
                p.setRutaDropbox(dropbox.asegurarCarpetaProyecto(p.getFechaInicio() != null ? p.getFechaInicio() : LocalDate.now(), nombre));
                resumen = "Carpeta de Dropbox del proyecto";
            }
        } catch (IllegalArgumentException e) {
            throw invalido(e);
        } catch (DropboxError e) {
            throw deDropbox(e, "No se pudo usar la carpeta en Dropbox: ");
        }
        actividad.registrar(p.getId(), actor(), "docs", resumen, null, p.getRutaDropbox());
        proyectos.save(p);
        return Mapas.proyecto(p);
    }

    /** Crea las subcarpetas fijas que falten en la carpeta de Dropbox del proyecto (las que ya hay no se tocan). */
    @Transactional
    public Map<String, Object> completarSubcarpetas(String id) {
        pl_Proyecto p = activo(id);
        exigirActivo();
        if (p.getRutaDropbox().isEmpty()) {
            throw new ErrorApi(HttpStatus.CONFLICT, "El proyecto no tiene carpeta de Dropbox: créala primero");
        }
        List<String> creadas;
        try {
            creadas = dropbox.completarSubcarpetas(p.getRutaDropbox());
        } catch (DropboxError e) {
            throw new ErrorApi(HttpStatus.BAD_GATEWAY, "No se pudieron crear las subcarpetas en Dropbox: " + e.getMessage());
        }
        if (!creadas.isEmpty()) {
            actividad.registrar(p.getId(), actor(), "docs", "Completó las subcarpetas de Dropbox", null, String.join(", ", creadas));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("created", creadas);
        return m;
    }

    /**
     * Al crear un proyecto: usa una carpeta existente (`dropbox_existing`, que manda) o crea la del nombre dado. La carpeta de Dropbox
     * nunca impide crear el proyecto: si falla, queda anotado y se puede reintentar.
     */
    public void carpetaAlCrear(pl_Proyecto p, Map<String, Object> cuerpo) {
        String existente = cuerpo.get("dropbox_existing") == null ? "" : String.valueOf(cuerpo.get("dropbox_existing")).strip();
        String nombre = cuerpo.get("dropbox_name") == null ? "" : String.valueOf(cuerpo.get("dropbox_name")).strip();
        if (!dropbox.habilitado() || (existente.isEmpty() && nombre.isEmpty())) {
            return;
        }
        try {
            if (!existente.isEmpty()) {
                p.setRutaDropbox(dropbox.usarCarpetaExistente(existente));
                actividad.registrar(p.getId(), actor(), "docs", "Usó una carpeta de Dropbox existente", null, p.getRutaDropbox());
            } else {
                p.setRutaDropbox(dropbox.asegurarCarpetaProyecto(p.getFechaInicio(), nombre));
                actividad.registrar(p.getId(), actor(), "docs", "Carpeta de Dropbox del proyecto", null, p.getRutaDropbox());
            }
        } catch (IllegalArgumentException | DropboxError e) {
            actividad.registrar(p.getId(), actor(), "docs",
                    existente.isEmpty() ? "No se pudo crear la carpeta de Dropbox" : "No se pudo usar la carpeta de Dropbox elegida", null, e.getMessage());
        }
    }
}
