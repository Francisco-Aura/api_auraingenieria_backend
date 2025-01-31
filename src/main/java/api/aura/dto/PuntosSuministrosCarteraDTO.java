package api.aura.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor // Constructor vacío para que Spring o frameworks puedan inicializar
public class PuntosSuministrosCarteraDTO {
    private Integer id;
    private Integer idCliente;
    private String cliente;
    private String direccion;
    private String codigoPostal;
    private String poblacion;
    private String provincia;
    private String cupsluz;
    private String cupsgas;

    // Constructor específico para la consulta del repository findAllPuntosSuministro
    public PuntosSuministrosCarteraDTO(
        Integer id,
        Integer idCliente,
        String cliente,
        String direccion,
        String codigoPostal,
        String poblacion,
        String provincia,
        String cupsluz,
        String cupsgas
    ) {
        this.id = id;
        this.idCliente = idCliente;
        this.cliente = cliente;
        this.direccion = direccion;
        this.codigoPostal = codigoPostal;
        this.poblacion = poblacion;
        this.provincia = provincia;
        this.cupsluz = cupsluz;
        this.cupsgas = cupsgas;
    }
}
