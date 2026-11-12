package api.aura.service.modulo_tareas;

import api.aura.dto.modulo_tareas.ActualizarNotaClienteRequest;
import api.aura.dto.modulo_tareas.ClienteNotaAdjuntoDTO;
import api.aura.dto.modulo_tareas.ClienteNotaDTO;
import api.aura.dto.modulo_tareas.CrearNotaClienteRequest;
import api.aura.entity.Cliente;
import api.aura.entity.Usuario;
import api.aura.entity.modulo_tareas.ClienteNota;
import api.aura.entity.modulo_tareas.ClienteNotaAdjunto;
import api.aura.repository.ClienteActividadRepository;
import api.aura.repository.ClienteRepository;
import api.aura.repository.UsuarioRepository;
import api.aura.repository.modulo_tareas.ClienteNotaAdjuntoRepository;
import api.aura.repository.modulo_tareas.ClienteNotaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClienteTimelineServiceTest {

    @TempDir
    Path carpetaNotas;

    private ClienteRepository clientes;
    private UsuarioRepository usuarios;
    private ClienteNotaRepository notas;
    private ClienteNotaAdjuntoRepository adjuntos;
    private ClienteTimelineService servicio;

    @BeforeEach
    void setUp() {
        clientes = mock(ClienteRepository.class);
        usuarios = mock(UsuarioRepository.class);
        notas = mock(ClienteNotaRepository.class);
        adjuntos = mock(ClienteNotaAdjuntoRepository.class);
        servicio = new ClienteTimelineService(mock(ClienteActividadRepository.class), clientes, usuarios,
                notas, adjuntos, mock(TareaService.class));
        ReflectionTestUtils.setField(servicio, "notasUploadPath", carpetaNotas.toString());
        when(clientes.existsById(7)).thenReturn(true);
        when(notas.save(any(ClienteNota.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ClienteNota notaConAdjunto() {
        ClienteNota nota = ClienteNota.builder().id(11L).comentario("Llamar el lunes")
                .usuarioAutor(Usuario.builder().id(1).nombre("Ana").build()).fijado(false).build();
        when(notas.findByIdAndCliente_Id(11L, 7)).thenReturn(Optional.of(nota));
        when(adjuntos.findByNota_IdOrderByFechaSubidaAsc(11L)).thenReturn(List.of(
                ClienteNotaAdjunto.builder().id(21L).nota(nota).nombreArchivo("factura.pdf")
                        .rutaArchivo("x").tipoMime("application/pdf").build()));
        return nota;
    }

    @Test
    void fijar_una_nota_devuelve_la_nota_con_sus_adjuntos() {
        notaConAdjunto();

        ClienteNotaDTO resultado = servicio.toggleFijadoNota(7, 11L);

        assertThat(resultado.getFijado()).isTrue();
        assertThat(resultado.getAdjuntos()).extracting(ClienteNotaAdjuntoDTO::getNombreArchivo)
                .containsExactly("factura.pdf");
    }

    @Test
    void editar_el_texto_de_una_nota_devuelve_la_nota_con_sus_adjuntos() {
        notaConAdjunto();
        ActualizarNotaClienteRequest cambio = new ActualizarNotaClienteRequest();
        cambio.setComentario("Llamar el martes");

        ClienteNotaDTO resultado = servicio.actualizarNota(7, 11L, cambio);

        assertThat(resultado.getComentario()).isEqualTo("Llamar el martes");
        assertThat(resultado.getAdjuntos()).hasSize(1);
    }

    @Test
    void al_guardar_un_adjunto_de_nota_se_cierra_el_archivo_recibido() {
        when(clientes.findById(7)).thenReturn(Optional.of(Cliente.builder().id(7).build()));
        when(usuarios.findById(1)).thenReturn(Optional.of(Usuario.builder().id(1).nombre("Ana").build()));
        when(notas.save(any(ClienteNota.class))).thenAnswer(inv -> {
            ClienteNota n = inv.getArgument(0);
            n.setId(11L);
            return n;
        });
        when(notas.findByIdAndCliente_Id(11L, 7)).thenAnswer(inv -> Optional.of(ClienteNota.builder().id(11L)
                .comentario("Con archivo").build()));
        ArchivoQueRegistraCierre archivo = new ArchivoQueRegistraCierre("acta.pdf");
        CrearNotaClienteRequest peticion = new CrearNotaClienteRequest();
        peticion.setUsuarioId(1);
        peticion.setComentario("Con archivo");

        servicio.crearNotaFijadaConArchivos(7, peticion, List.of(archivo));

        assertThat(archivo.flujoCerrado).isTrue();
    }

    /** MultipartFile cuyo flujo recuerda si se ha cerrado. */
    static class ArchivoQueRegistraCierre extends MockMultipartFile {
        boolean flujoCerrado;

        ArchivoQueRegistraCierre(String nombre) {
            super("archivos", nombre, "application/pdf", "%PDF-1.4 contenido".getBytes());
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream("%PDF-1.4 contenido".getBytes()) {
                @Override
                public void close() {
                    flujoCerrado = true;
                }
            };
        }
    }
}
