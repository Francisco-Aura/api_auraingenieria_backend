package api.aura.mcas;

import api.aura.controller.mcas.McasReglasController;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.mcas.McasValorRangoRepository;
import api.aura.service.mcas.McasValoresRangoService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Editar una regla con la prioridad vacía conserva la que tenía (antes, error de datos). */
class McasReglaPrioridadTest {

    @Test
    void editar_con_la_prioridad_vacia_conserva_la_anterior() {
        McasReglaComisionRepository reglas = mock(McasReglaComisionRepository.class);
        McasConjuntoReglasRepository conjuntos = mock(McasConjuntoReglasRepository.class);
        McasReglaComision regla = new McasReglaComision();
        regla.setId(5);
        regla.setIdConjuntoReglas(2);
        regla.setPrioridad(40);
        McasConjuntoReglas borrador = new McasConjuntoReglas();
        borrador.setId(2);
        borrador.setEstado("BORRADOR");
        when(reglas.findById(5)).thenReturn(Optional.of(regla));
        when(reglas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conjuntos.findById(2)).thenReturn(Optional.of(borrador));
        McasReglasController controlador = new McasReglasController(reglas, mock(McasValorRangoRepository.class),
                conjuntos, mock(McasValoresRangoService.class));

        McasReglaComision datos = new McasReglaComision();
        datos.setNombre("Regla editada");
        datos.setPrioridad(null);
        controlador.actualizar(5, datos);

        assertThat(regla.getPrioridad()).isEqualTo(40);
        assertThat(regla.getNombre()).isEqualTo("Regla editada");
    }
}
