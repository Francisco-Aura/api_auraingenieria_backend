package api.aura.service.modulo_planificador;

import api.aura.config.ErrorApi;
import api.aura.entity.modulo_planificador.pl_PvgisSerie;
import api.aura.repository.modulo_planificador.pl_PvgisSerieRepository;
import api.aura.service.modulo_planificador.estudio.PerfilSolar;
import api.aura.service.modulo_planificador.estudio.Pvgis;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Producción solar real de una ubicación y orientación: la guarda tras pedirla a PVGIS, así que cada combinación se consulta una vez. */
@Service
@RequiredArgsConstructor
public class PvgisService {

    private final PvgisCliente cliente;
    private final pl_PvgisSerieRepository series;

    /**
     * Perfil del estudio. «sur» es 15° al sur, «este-oeste» 10° mitad al este y mitad al oeste, y «personalizada» usa la inclinación y el
     * azimut dados. Sin ubicación, o si PVGIS no responde, devuelve un perfil vacío (con un aviso si lo que se pidió no se puede
     * resolver con el modelo sencillo).
     */
    public PerfilSolar perfil(Double latitud, Double longitud, String orientacion, double inclinacion, double azimut) {
        boolean libre = "personalizada".equals(orientacion);
        if (latitud == null || longitud == null) {
            return PerfilSolar.sinPerfil(libre ? "Indica la ubicación para calcular la producción con PVGIS: mientras tanto se usa el modelo de sur o este-oeste." : null);
        }
        try {
            double[] v;
            if (libre) v = serie(latitud, longitud, (int) Math.round(inclinacion), (int) Math.round(azimut));
            else if ("sur".equals(orientacion)) v = serie(latitud, longitud, 15, 0);
            else v = Pvgis.media(serie(latitud, longitud, 10, -90), serie(latitud, longitud, 10, 90));
            return new PerfilSolar(v, null);
        } catch (ErrorApi e) {
            return PerfilSolar.sinPerfil("No se pudo consultar PVGIS (" + e.getMessage() + ") y se usa el modelo de sur o este-oeste.");
        }
    }

    /**
     * Producción anual (kWh por kWp) con una inclinación dada: al sur, con el azimut indicado (orientación libre) o repartida entre
     * este y oeste. Usa las series guardadas y solo consulta PVGIS lo que no tiene.
     */
    public double produccionAnual(double latitud, double longitud, String orientacion, int inclinacion, int azimut) {
        double[] v = "este-oeste".equals(orientacion)
                ? Pvgis.media(serie(latitud, longitud, inclinacion, -90), serie(latitud, longitud, inclinacion, 90))
                : serie(latitud, longitud, inclinacion, "sur".equals(orientacion) ? 0 : azimut);
        double s = 0;
        for (double x : v) s += x;
        return s;
    }

    /** Lugares que coinciden con un texto, para poner la ubicación del estudio. */
    public List<Map<String, Object>> buscarLugar(String texto) {
        if (texto == null || texto.isBlank()) throw TareaProyectoService.invalido("Escribe el lugar de la instalación.");
        return cliente.buscarLugar(texto.strip());
    }

    private double[] serie(double lat, double lon, int angulo, int azimut) {
        int latC = (int) Math.round(lat * 100), lonC = (int) Math.round(lon * 100);
        Optional<pl_PvgisSerie> guardada = series.findByLatCAndLonCAndAnguloAndAzimut(latC, lonC, angulo, azimut);
        if (guardada.isPresent()) return Pvgis.desdeTexto(guardada.get().getDatos());
        double[] v = cliente.serie(latC / 100.0, lonC / 100.0, angulo, azimut);
        pl_PvgisSerie s = new pl_PvgisSerie();
        s.setLatC(latC);
        s.setLonC(lonC);
        s.setAngulo(angulo);
        s.setAzimut(azimut);
        s.setDatos(Pvgis.comoTexto(v));
        try {
            series.save(s);
        } catch (DataIntegrityViolationException e) {
            // Otro cálculo la guardó a la vez: da igual
        }
        return v;
    }
}
