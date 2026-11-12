package api.aura.rendimiento;

import api.aura.dto.informes.ContactoClienteDTO;
import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import api.aura.entity.tablas_maestras.TBSectores;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.tablas_maestras.TBSectoresRepository;
import api.aura.service.InformesService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Informe «Contactos de clientes»: recorre todos los clientes. Antes cargaba cada
 * entidad Cliente completa (notas, IBAN, credenciales de telemedida...) y su
 * usuario asignado para usar solo nombre, baja, sector y los datos de contacto.
 * Ahora lee solo esas columnas; las filas del informe y su orden no cambian.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class InformeContactosClientesTest {

    @Autowired private EntityManagerFactory emf;
    @Autowired private EntityManager em;
    @Autowired private InformesService informes;
    @Autowired private ClienteRepository clientes;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private TBSectoresRepository sectores;

    @Test
    void el_informe_sale_igual_sin_cargar_los_clientes_enteros() {
        String marca = "CONTACTOS-" + UUID.randomUUID().toString().substring(0, 8);
        TBSectores sector = sectores.save(TBSectores.builder().nombre("Sector " + marca).build());
        String nombreSector = sector.getNombre();
        for (int i = 0; i < 5; i++) {
            Usuario comercial = usuarios.save(Usuario.builder().nombre("Comercial " + marca + i).build());
            clientes.save(Cliente.builder().nombre(marca + " relleno " + i).cif(marca + "R" + i)
                    .usuario(comercial).notas("Notas largas ".repeat(500)).build());
        }
        clientes.save(Cliente.builder().nombre(marca + " b-activo").cif(marca + "1").codigoSector(sector.getId())
                .nombreAutorizado("Ana").telefonoAutorizado("600111222").emailAutorizado("ana@x.es")
                .nombreContacto1("Luis").cargoContacto1("Gerente")
                .nombreContacto2("  ").telefonoContacto2("").emailContacto2(null)
                .emailContacto3("tercero@x.es").notas("Notas ".repeat(1000)).build());
        clientes.save(Cliente.builder().nombre(marca + " a-baja").cif(marca + "2")
                .fechaBaja(Date.valueOf("2025-05-01")).telefonoContacto1("699").build());
        clientes.save(Cliente.builder().nombre(marca + " C-historico").cif(marca + "3")
                .fechaBaja(Date.valueOf("1900-01-01")).codigoSector(999_999).build());
        em.flush();
        em.clear();

        Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        List<ContactoClienteDTO> filas = informes.obtenerContactosClientes().stream()
                .filter(f -> f.getNombreCliente().startsWith(marca + " ") && !f.getNombreCliente().contains("relleno"))
                .toList();

        assertThat(filas).containsExactly(
                new ContactoClienteDTO(marca + " b-activo", "Datos Principales", "Ana", "", "600111222",
                        "ana@x.es", nombreSector, "NO"),
                new ContactoClienteDTO(marca + " b-activo", "Contacto 1", "Luis", "Gerente", "", "",
                        nombreSector, "NO"),
                new ContactoClienteDTO(marca + " b-activo", "Contacto 3", "", "", "", "tercero@x.es",
                        nombreSector, "NO"),
                new ContactoClienteDTO(marca + " a-baja", "Datos Principales", "", "", "", "", "", "SI"),
                new ContactoClienteDTO(marca + " a-baja", "Contacto 1", "", "", "699", "", "", "SI"),
                // Cualquier fecha de baja cuenta como baja, también 1900 (igual que la pantalla de Clientes)
                new ContactoClienteDTO(marca + " C-historico", "Datos Principales", "", "", "", "", "", "SI"));
        assertThat(estadisticas.getEntityStatistics(Cliente.class.getName()).getLoadCount())
                .as("clientes cargados como entidad").isZero();
        assertThat(estadisticas.getEntityStatistics(Usuario.class.getName()).getLoadCount())
                .as("usuarios asignados cargados").isZero();
        assertThat(estadisticas.getPrepareStatementCount()).as("consultas").isLessThanOrEqualTo(2);
    }
}
