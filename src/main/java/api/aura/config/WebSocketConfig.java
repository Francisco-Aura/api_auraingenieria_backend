package api.aura.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Arrays;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Orígenes admitidos para /ws (lista separada por comas). El handshake ya exige
     * la sesión de Aura (SecurityConfig) y la cookie es SameSite, así que otra web
     * no puede abrir el canal con la sesión del usuario. Se deja configurable para
     * restringirlo al origen del frontend en cada servidor.
     */
    private final String[] origenes;

    public WebSocketConfig(@Value("${aura.websocket.allowed-origin-patterns:*}") String origenes) {
        this.origenes = Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broker simple en memoria; /topic/notificaciones/{id} para cada usuario (ver CanalCliente)
        config.enableSimpleBroker("/topic");
        // Prefijo para mensajes enviados desde el cliente al servidor (no usado actualmente)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint SockJS accesible desde el frontend en /ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origenes)
                // Copia el usuario de la sesión HTTP a la sesión WebSocket (ver canalCliente)
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new CanalCliente());
    }

    static final String TOPIC_NOTIFICACIONES = "/topic/notificaciones/";

    /**
     * Cada usuario solo se suscribe a sus propias notificaciones, y ningún cliente
     * publica en /topic (lo hace solo el servidor): antes cualquiera podía escuchar
     * las notificaciones de otro usuario o enviarle notificaciones falsas.
     */
    static class CanalCliente implements ChannelInterceptor {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            SimpMessageType tipo = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
            String destino = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            if (tipo == SimpMessageType.MESSAGE && destino != null && destino.startsWith("/topic")) {
                throw new IllegalArgumentException("Destino no permitido");
            }
            // El broker trata la suscripción como patrón (/topic/**, /topic/*/8...): se exige el
            // destino exacto del propio usuario para cualquier cosa bajo /topic.
            if (tipo == SimpMessageType.SUBSCRIBE && destino != null && destino.startsWith("/topic")) {
                Map<String, Object> atributos = SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
                Object usuario = atributos == null ? null : atributos.get(UsuarioSesion.ATRIBUTO);
                if (!(usuario instanceof UsuarioSesion u) || u.id() == null
                        || !destino.equals(TOPIC_NOTIFICACIONES + u.id())) {
                    throw new IllegalArgumentException("Suscripción no permitida");
                }
            }
            return message;
        }
    }
}
