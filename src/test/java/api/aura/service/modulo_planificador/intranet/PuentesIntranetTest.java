package api.aura.service.modulo_planificador.intranet;

import api.aura.dto.ClienteDTO;
import api.aura.dto.modulo_tareas.PlanificadorAvisoRequest;
import api.aura.entity.Cliente;
import api.aura.service.ClienteService;
import api.aura.service.modulo_planificador.AvisoError;
import api.aura.service.modulo_tareas.TareaNotificacionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Los puentes del Planificador con la intranet: avisos como notificaciones y clientes con el servicio de clientes. */
class PuentesIntranetTest {

    private final ObjectMapper json = new ObjectMapper();

    private static ClienteDTO cliente(int id, String nombre, String comercial, String cif) {
        ClienteDTO c = new ClienteDTO();
        c.setId(id);
        c.setNombre(nombre);
        c.setNombreComercial(comercial);
        c.setCif(cif);
        return c;
    }

    @Test
    void elAvisoLlegaAlServicioDeNotificacionesConTodosSusCampos() {
        TareaNotificacionService notificaciones = mock(TareaNotificacionService.class);
        AvisosIntranet avisos = new AvisosIntranet(notificaciones, json);
        assertTrue(avisos.activo());
        Map<String, Object> aviso = new LinkedHashMap<>();
        aviso.put("group", "ingenieria");
        aviso.put("groupName", "Ingeniería");
        aviso.put("excludeUserId", 7);
        aviso.put("excludeUser", "Ana");
        aviso.put("title", "Planificador · Proyecto");
        aviso.put("message", "Ana ha terminado «A». Ya puede empezar «B».");
        aviso.put("link", "proyecto=p1&tarea=t2");
        aviso.put("projectId", "p1");
        aviso.put("nextTasks", List.of(Map.of("id", "t2", "name", "B"))); // campo que la intranet no conoce: se ignora
        avisos.enviar(aviso);
        ArgumentCaptor<PlanificadorAvisoRequest> enviado = ArgumentCaptor.forClass(PlanificadorAvisoRequest.class);
        verify(notificaciones).notificarDesdePlanificador(enviado.capture());
        assertEquals("ingenieria", enviado.getValue().getGroup());
        assertEquals(7, enviado.getValue().getExcludeUserId());
        assertEquals("proyecto=p1&tarea=t2", enviado.getValue().getLink());
        assertEquals("Ana ha terminado «A». Ya puede empezar «B».", enviado.getValue().getMessage());
    }

    @Test
    void sinGrupoOSinMensajeSeRechazaYUnFalloDeLaIntranetSeTraduce() {
        TareaNotificacionService notificaciones = mock(TareaNotificacionService.class);
        AvisosIntranet avisos = new AvisosIntranet(notificaciones, json);
        assertEquals("Faltan el grupo o el mensaje", assertThrows(AvisoError.class, () -> avisos.enviar(Map.of("group", "ingenieria"))).getMessage());
        verifyNoInteractions(notificaciones);
        doThrow(new IllegalStateException("tabla bloqueada")).when(notificaciones).notificarDesdePlanificador(any());
        AvisoError e = assertThrows(AvisoError.class, () -> avisos.enviar(Map.of("group", "ingenieria", "message", "x")));
        assertEquals("No se pudo crear la notificación en la intranet", e.getMessage());
    }

    @Test
    void buscarClientesDevuelveSoloLoImprescindibleYAcotaElTamano() {
        ClienteService servicio = mock(ClienteService.class);
        when(servicio.buscarClientes(anyString(), any())).thenReturn(new PageImpl<>(List.of(cliente(3, "Solar Vega S.L.", "Solar Vega", "B12345678"))));
        ClientesIntranet clientes = new ClientesIntranet(servicio);
        assertTrue(clientes.habilitado());
        assertEquals(List.of(Map.of("id", 3, "name", "Solar Vega S.L.", "trade_name", "Solar Vega", "cif", "B12345678")), clientes.buscar("vega", 500));
        verify(servicio).buscarClientes("vega", PageRequest.of(0, 50));
    }

    @Test
    void altaDeClienteNoDuplicaUnCifYaExistenteAunqueVengaConOtroFormato() {
        ClienteService servicio = mock(ClienteService.class);
        when(servicio.buscarClientes(anyString(), any())).thenReturn(new PageImpl<>(List.of(cliente(3, "Solar Vega S.L.", null, "B-123.456 78"))));
        Map<String, Object> r = new ClientesIntranet(servicio).crear("Otro nombre", "b12345678", "600", "a@b.es");
        assertEquals(3, r.get("id"));
        verify(servicio, never()).crearCliente(any());
    }

    @Test
    void altaDeClienteNuevoLoCreaConLaDireccionVaciaNoNula() {
        ClienteService servicio = mock(ClienteService.class);
        when(servicio.buscarClientes(anyString(), any())).thenReturn(new PageImpl<>(List.of()));
        Cliente creado = mock(Cliente.class);
        when(servicio.crearCliente(any())).thenReturn(creado);
        when(servicio.toDTO(creado)).thenReturn(cliente(9, "Nuevo S.L.", null, "B99999999"));
        Map<String, Object> r = new ClientesIntranet(servicio).crear("Nuevo S.L.", "B99999999", " 600123123 ", "");
        assertEquals(9, r.get("id"));
        ArgumentCaptor<ClienteDTO> dto = ArgumentCaptor.forClass(ClienteDTO.class);
        verify(servicio).crearCliente(dto.capture());
        assertEquals("", dto.getValue().getDireccion());
        assertEquals("", dto.getValue().getPoblacion());
        assertEquals("600123123", dto.getValue().getTelefonoContacto1());
        assertNull(dto.getValue().getEmailContacto1());
        assertNotNull(dto.getValue().getFechaAlta());
    }
}
