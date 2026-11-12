package api.aura.rendimiento;

import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.mt_Adjunto;
import api.aura.entity.modulo_tareas.mt_Categoria;
import api.aura.entity.modulo_tareas.mt_Comentario;
import api.aura.entity.modulo_tareas.mt_SubCategoria;
import api.aura.entity.modulo_tareas.mt_Tarea;
import api.aura.entity.modulo_tareas.mt_TareaEstado;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.AdjuntoRepository;
import api.aura.repository.modulo_tareas.mt_CategoriaRepository;
import api.aura.repository.modulo_tareas.mt_ComentarioRepository;
import api.aura.repository.modulo_tareas.mt_SubCategoriaRepository;
import api.aura.repository.modulo_tareas.mt_TareaEstadoRepository;
import api.aura.repository.modulo_tareas.mt_TareaRepository;
import api.aura.service.modulo_tareas.TareaComentarioService;
import api.aura.service.modulo_tareas.TareaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listados del módulo de tareas con relaciones distintas en cada fila (creador,
 * categoría, subcategoría, autor del comentario, adjuntos...): con la carga en
 * lotes ({@code default_batch_fetch_size}) el número de consultas no crece con el
 * número de filas. Una consulta por fila daría más de {@value #FILAS}.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ConsultasModuloTareasTest {

    private static final int FILAS = 20;
    private static final int MAX_CONSULTAS = 10;

    @Autowired private EntityManagerFactory emf;
    @Autowired private EntityManager em;
    @Autowired private TareaService tareaService;
    @Autowired private TareaComentarioService comentarioService;
    @Autowired private mt_TareaRepository tareas;
    @Autowired private mt_TareaEstadoRepository estados;
    @Autowired private mt_CategoriaRepository categorias;
    @Autowired private mt_SubCategoriaRepository subcategorias;
    @Autowired private mt_ComentarioRepository comentarios;
    @Autowired private AdjuntoRepository adjuntos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ClienteRepository clientes;

    private Statistics estadisticas;
    private Usuario responsable;
    private Cliente cliente;
    private mt_Tarea conComentarios;

    @BeforeEach
    void preparar() {
        estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        String marca = UUID.randomUUID().toString().substring(0, 6);
        mt_TareaEstado pendiente = estados.save(mt_TareaEstado.builder().nombre("PEND-" + marca)
                .estadoFinal(false).orden(1).cuentaTiempo(false).esBloqueo(false).build());
        responsable = usuarios.save(Usuario.builder().nombre("Responsable " + marca).build());
        cliente = clientes.save(Cliente.builder().nombre("Cliente " + marca).cif("C-" + marca)
                .usuario(responsable).build());
        for (int i = 0; i < FILAS; i++) {
            Usuario creador = usuarios.save(Usuario.builder().nombre("Creador " + marca + i).build());
            mt_Categoria categoria = categorias.save(mt_Categoria.builder().nombre("Cat " + marca + i)
                    .codigo(marca + i).activo(true).build());
            mt_SubCategoria sub = subcategorias.save(mt_SubCategoria.builder().nombre("Sub " + marca + i)
                    .codigo("S" + i).activo(true).build());
            conComentarios = tareas.save(mt_Tarea.builder().titulo("Tarea " + marca + i).prioridad(2)
                    .notificar(false).enviarCorreo(false).estado(pendiente).usuarioAsignado(responsable)
                    .usuarioCreador(creador).clienteRelacionado(cliente).categoria(categoria).subcategoria(sub)
                    .fechaVencimiento(LocalDateTime.now().plusDays(1)).build());
        }
        for (int i = 0; i < FILAS; i++) {
            Usuario autor = usuarios.save(Usuario.builder().nombre("Autor " + marca + i).build());
            mt_Comentario comentario = comentarios.save(mt_Comentario.builder().tarea(conComentarios)
                    .usuarioAutor(autor).comentario("Comentario " + i).build());
            adjuntos.save(mt_Adjunto.builder().comentario(comentario).nombreArchivo("a" + i + ".pdf")
                    .rutaArchivo("x/a" + i + ".pdf").tamanio(1L).build());
        }
        // Nada en memoria: las lecturas van a la BD como en una petición real
        em.flush();
        em.clear();
    }

    private <T> long consultasDurante(Supplier<T> accion) {
        estadisticas.clear();
        accion.get();
        long consultas = estadisticas.getPrepareStatementCount();
        em.clear();
        return consultas;
    }

    @Test
    void los_listados_de_tareas_no_lanzan_una_consulta_por_tarea() {
        assertThat(consultasDurante(() -> tareaService.obtenerPorUsuario(responsable.getId())))
                .isLessThan(MAX_CONSULTAS);
        assertThat(consultasDurante(() -> tareaService.obtenerPorCliente(cliente.getId())))
                .isLessThan(MAX_CONSULTAS);
        assertThat(consultasDurante(() -> tareaService.obtenerPorVencer(7))).isLessThan(MAX_CONSULTAS);
    }

    @Test
    void las_estadisticas_no_lanzan_una_consulta_por_tarea() {
        assertThat(consultasDurante(() -> tareaService.obtenerEstadisticas(responsable.getId(), 1)))
                .isLessThan(MAX_CONSULTAS);
        assertThat(consultasDurante(() -> tareaService.obtenerEstadisticas(responsable.getId(), 2)))
                .isLessThan(MAX_CONSULTAS);
    }

    @Test
    void los_comentarios_y_sus_adjuntos_no_lanzan_una_consulta_por_comentario() {
        assertThat(comentarioService.obtenerPorTarea(conComentarios.getId()))
                .hasSize(FILAS)
                .allSatisfy(c -> assertThat(c.getAdjuntos()).hasSize(1));
        em.clear();

        assertThat(consultasDurante(() -> comentarioService.obtenerPorTarea(conComentarios.getId())))
                .isLessThan(MAX_CONSULTAS);
        assertThat(consultasDurante(() -> tareaService.obtenerPorId(conComentarios.getId())))
                .isLessThan(MAX_CONSULTAS);
    }
}
