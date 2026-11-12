package api.aura.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cada usuario solo escucha sus notificaciones y nadie publica en /topic desde el cliente. */
class WebSocketCanalClienteTest {

    private final WebSocketConfig.CanalCliente canal = new WebSocketConfig.CanalCliente();

    private static Message<byte[]> mensaje(SimpMessageType tipo, String destino, UsuarioSesion usuario) {
        SimpMessageHeaderAccessor h = SimpMessageHeaderAccessor.create(tipo);
        h.setDestination(destino);
        Map<String, Object> atributos = new HashMap<>();
        if (usuario != null) atributos.put(UsuarioSesion.ATRIBUTO, usuario);
        h.setSessionAttributes(atributos);
        h.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders());
    }

    @Test
    void se_puede_suscribir_a_sus_propias_notificaciones() {
        Message<byte[]> m = mensaje(SimpMessageType.SUBSCRIBE, "/topic/notificaciones/7", new UsuarioSesion(7, "Ana", 2));
        assertThat(canal.preSend(m, null)).isSameAs(m);
    }

    @Test
    void no_se_puede_suscribir_a_las_de_otro_ni_sin_sesion() {
        assertThatThrownBy(() -> canal.preSend(
                mensaje(SimpMessageType.SUBSCRIBE, "/topic/notificaciones/8", new UsuarioSesion(7, "Ana", 1)), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> canal.preSend(
                mensaje(SimpMessageType.SUBSCRIBE, "/topic/notificaciones/7", null), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void no_se_admiten_patrones_ni_otros_destinos_bajo_topic() {
        UsuarioSesion ana = new UsuarioSesion(7, "Ana", 1);
        for (String destino : new String[]{"/topic/**", "/topic/*/8", "/topic/notificaciones*/*",
                "/topic/notificaciones/7/../8", "/topic/notificaciones/7*", "/topic/otra"}) {
            assertThatThrownBy(() -> canal.preSend(mensaje(SimpMessageType.SUBSCRIBE, destino, ana), null))
                    .as(destino).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void el_cliente_no_publica_en_topic() {
        assertThatThrownBy(() -> canal.preSend(
                mensaje(SimpMessageType.MESSAGE, "/topic/notificaciones/8", new UsuarioSesion(7, "Ana", 1)), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
