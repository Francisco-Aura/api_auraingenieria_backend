package api.aura.service.modulo_planificador;

import api.aura.repository.modulo_planificador.pl_EstudioRepository;
import api.aura.repository.modulo_planificador.pl_MetaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/** El estudio de ejemplo con el que arranca una instalación nueva: guardado, con curva y calculado. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class, properties = {
        "planificador.catalogo-inicial=true", "spring.datasource.url=jdbc:h2:mem:muestra;MODE=MariaDB;DB_CLOSE_DELAY=-1"})
@org.springframework.test.context.ActiveProfiles("planificador")
class EstudioDeMuestraTest {

    @Autowired
    private pl_EstudioRepository estudios;
    @Autowired
    private pl_MetaRepository meta;

    @Test
    void unaInstalacionNuevaTieneUnEstudioDeEjemploCalculado() {
        var e = estudios.findById(EstudioDeMuestra.ID).orElseThrow();
        assertEquals("3.0TD", e.getDatos().get("tarifa"));
        assertNotNull(e.getCurva(), "la curva se estima con las lecturas del SIPS");
        assertFalse(e.getResultado().isEmpty(), "el estudio queda calculado");
        assertTrue(meta.existsById(EstudioDeMuestra.CLAVE));
    }

    @Test
    void losEjemplosConCurvaCuartohorariaTienenCurvaRealYEstanCalculados() {
        for (EstudioDeMuestra.Perfil p : EstudioDeMuestra.Perfil.values()) {
            var e = estudios.findById(p.id).orElseThrow();
            assertFalse(e.isCurvaEstimada(), p.id + ": curva real, no estimada");
            assertNotNull(e.getCurva());
            assertFalse(e.getResultado().isEmpty(), p.id + " calculado");
        }
    }
}
