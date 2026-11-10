package api.aura.service.modulo_planificador;

import java.util.List;
import java.util.Map;

/**
 * Clientes de la intranet. Al integrar el módulo en el monolito se implementa con el ClienteService de la intranet;
 * mientras tanto (módulo suelto) no hay conexión y solo se pueden usar clientes potenciales.
 */
public interface ClientesIntranetPort {

    boolean habilitado();

    /** Clientes que casan con el texto: id, name, trade_name, cif… */
    List<Map<String, Object>> buscar(String texto, int tamano);

    /** Da de alta un cliente (o devuelve el que ya tenga ese CIF): id, name, trade_name, cif. */
    Map<String, Object> crear(String nombre, String cif, String telefono, String email);
}
