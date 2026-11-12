package api.aura.service.tablas_maestras;

import api.aura.entity.tablas_maestras.TBNivelAcceso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los listados de tablas maestras se sirven desde caché: dar de alta un registro
 * en Administración tiene que verse en el listado siguiente.
 */
@SpringBootTest
class TablasMaestrasCacheTest {

    @Autowired
    private TBNivelAccesoService nivelesAcceso;

    @Test
    void un_nivel_de_acceso_nuevo_aparece_en_el_listado_aunque_estuviera_en_cache() {
        nivelesAcceso.obtenerTodosLosNivelesAcceso(); // queda en caché

        TBNivelAcceso nuevo = new TBNivelAcceso();
        nuevo.setNivel("Nivel 9");
        nuevo.setDescripcion("Nivel de prueba de caché");
        TBNivelAcceso creado = nivelesAcceso.crearNivelAcceso(nuevo);

        assertThat(nivelesAcceso.obtenerTodosLosNivelesAcceso())
                .extracting(TBNivelAcceso::getId)
                .contains(creado.getId());
    }
}
