package api.aura.repository;

import api.aura.dto.ClienteCarteraDTO;
import api.aura.entity.Cliente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dirección fiscal de la cartera: dirección, población y provincia separadas por comas,
 * sin comas sueltas cuando falta alguna (antes, con una vacía salía todo vacío o «, , »).
 */
@SpringBootTest
@Transactional
class CarteraDireccionTest {

    @Autowired private ClienteRepository clientes;

    @Test
    void la_direccion_no_empieza_ni_acaba_con_coma_aunque_falte_algun_dato() {
        String marca = "CARTERA-" + UUID.randomUUID().toString().substring(0, 8);
        clientes.save(Cliente.builder().nombre(marca + " completa").cif(marca + "1")
                .direccion("Calle Mayor 1").poblacion("Soria").provincia("Soria").build());
        clientes.save(Cliente.builder().nombre(marca + " sin direccion").cif(marca + "2")
                .poblacion("Soria").provincia("Soria").build());
        clientes.save(Cliente.builder().nombre(marca + " solo provincia").cif(marca + "3")
                .direccion("").provincia("Soria").build());
        clientes.save(Cliente.builder().nombre(marca + " sin poblacion").cif(marca + "4")
                .direccion("Calle Mayor 1").poblacion("").provincia("Soria").build());
        clientes.save(Cliente.builder().nombre(marca + " nada").cif(marca + "5").build());

        Map<String, String> direccion = clientes.obtenerCarteraClientes().stream()
                .filter(c -> c.getNombre().startsWith(marca))
                .collect(Collectors.toMap(c -> c.getNombre().substring(marca.length() + 1), ClienteCarteraDTO::getDireccionFiscal));

        assertThat(direccion).containsEntry("completa", "Calle Mayor 1, Soria, Soria")
                .containsEntry("sin direccion", "Soria, Soria")
                .containsEntry("solo provincia", "Soria")
                .containsEntry("sin poblacion", "Calle Mayor 1, Soria")
                .containsEntry("nada", "");
    }
}
