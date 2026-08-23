package api.aura.dto;

import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;

/**
 * Comando aditivo para persistir un servicio y sus pagos en una sola
 * transaccion. Los endpoints historicos permanecen disponibles.
 */
public record GuardarServicioConPagosRequest(Servicio servicio, ServiciosPagos pagos) {
}
