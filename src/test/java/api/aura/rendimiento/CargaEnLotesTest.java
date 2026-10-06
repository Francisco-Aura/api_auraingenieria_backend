package api.aura.rendimiento;

import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import api.aura.service.ContratoService;
import api.aura.service.modulo_tareas.TareaService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La BBDD es remota: cada consulta es un viaje de red. Las relaciones de las
 * tareas (usuarios, cliente, estado...) y de los contratos (cliente, punto de
 * suministro y sus tablas maestras) se cargan en lotes y no una por fila.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class CargaEnLotesTest {

    private static final int FILAS = 20;

    @Autowired private EntityManagerFactory emf;
    @Autowired private TareaService tareaService;
    @Autowired private ContratoService contratoService;
    @Autowired private mt_TareaRepository tareas;
    @Autowired private mt_TareaEstadoRepository estados;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ClienteRepository clientes;
    @Autowired private ContratoRepository contratos;
    @Autowired private PuntoSuministroRepository puntos;

    private Statistics estadisticas;

    @BeforeEach
    void preparar() {
        estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        if (tareas.count() > 0) {
            return;
        }
        mt_TareaEstado pendiente = estados.save(mt_TareaEstado.builder().nombre("PENDIENTE-LOTES")
                .estadoFinal(false).orden(1).cuentaTiempo(false).esBloqueo(false).build());
        for (int i = 0; i < FILAS; i++) {
            Usuario usuario = usuarios.save(Usuario.builder().nombre("Usuario lote " + i).build());
            Cliente cliente = clientes.save(Cliente.builder().nombre("Cliente lote " + i).cif("LOTE-" + i)
                    .usuario(usuario).build());
            tareas.save(mt_Tarea.builder().titulo("Tarea lote " + i).prioridad(2).notificar(false)
                    .enviarCorreo(false).estado(pendiente).usuarioAsignado(usuario).usuarioCreador(usuario)
                    .clienteRelacionado(cliente).build());
            PuntoSuministro punto = puntos.save(PuntoSuministro.builder().cliente(cliente).direccion("Calle " + i)
                    .codigoPostal("28001").poblacion("Madrid").provincia("Madrid").build());
            contratos.save(Contrato.builder().cliente(cliente).puntoSuministro(punto).contrato("LOTE-" + i).build());
        }
    }

    private long consultasDurante(Runnable accion) {
        estadisticas.clear();
        accion.run();
        return estadisticas.getPrepareStatementCount();
    }

    @Test
    void el_listado_de_tareas_no_lanza_una_consulta_por_tarea() {
        long consultas = consultasDurante(() -> assertThat(tareaService.obtenerTodas()).hasSizeGreaterThanOrEqualTo(FILAS));

        assertThat(consultas).isLessThan(10);
    }

    @Test
    void el_listado_de_contratos_no_lanza_una_consulta_por_contrato() {
        long consultas = consultasDurante(() -> assertThat(contratoService.obtenerTodos()).hasSizeGreaterThanOrEqualTo(FILAS));

        assertThat(consultas).isLessThan(10);
    }

    @Test
    void la_configuracion_de_la_aplicacion_activa_la_carga_en_lotes() throws Exception {
        Properties aplicacion = new Properties();
        try (InputStream entrada = new FileInputStream("src/main/resources/application.properties")) {
            aplicacion.load(entrada);
        }

        assertThat(aplicacion.getProperty("spring.jpa.properties.hibernate.default_batch_fetch_size"))
                .isNotBlank()
                .satisfies(valor -> assertThat(Integer.parseInt(valor.trim())).isGreaterThanOrEqualTo(16));
    }
}
