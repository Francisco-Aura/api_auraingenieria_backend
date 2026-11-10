package api.aura.service.modulo_planificador;

import java.util.Map;

/**
 * Salida de los avisos hacia la intranet (equivale al envío de notify.py). Cada aviso es un mapa con los campos que lee el
 * endpoint de notificaciones de la intranet (source, group, groupName, title, message, link, projectId…).
 * Al integrar el módulo en el monolito se implementa con el servicio de notificaciones de la intranet; mientras tanto hay una
 * implementación que no envía nada y otra que lo manda por HTTP si se configura una dirección.
 */
public interface AvisosPort {

    /** Si hay alguna conexión configurada. Sin ella no se avisa a nadie. */
    boolean activo();

    /** Entrega un aviso. Lanza {@link AvisoError} si no se puede. */
    void enviar(Map<String, Object> aviso);
}
