package api.aura.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@NoArgsConstructor
public class PuntosSuministrosCarteraDTO {
    private Integer id;
    private Integer idCliente;
    private String cliente;
    private String direccion;
    private String codigoPostal;
    private String poblacion;
    private String provincia;
    private String identificacion;
    private String cupsluz;
    private String cupsgas;
    private String tarifaActual;
    private Double potencia1;
    private Double potencia2;
    private Double potencia3;
    private Double potencia4;
    private Double potencia5;
    private Double potencia6;
    private Date fechaBaja;
    private Date fechaFinUltimoContrato;

    public PuntosSuministrosCarteraDTO(
            Integer id,
            Integer idCliente,
            String cliente,
            String direccion,
            String codigoPostal,
            String poblacion,
            String provincia,
            String identificacion,
            String cupsluz,
            String cupsgas,
            Date fechaBaja) {
        this.id = id;
        this.idCliente = idCliente;
        this.cliente = cliente;
        this.direccion = direccion;
        this.codigoPostal = codigoPostal;
        this.poblacion = poblacion;
        this.provincia = provincia;
        this.identificacion = identificacion;
        this.cupsluz = cupsluz;
        this.cupsgas = cupsgas;
        this.fechaBaja = fechaBaja;
        this.tarifaActual = null;
        this.fechaFinUltimoContrato = null;
        this.potencia1 = null;
        this.potencia2 = null;
        this.potencia3 = null;
        this.potencia4 = null;
        this.potencia5 = null;
        this.potencia6 = null;
    }

    public PuntosSuministrosCarteraDTO(
            Integer id,
            Integer idCliente,
            String cliente,
            String direccion,
            String codigoPostal,
            String poblacion,
            String provincia,
            String identificacion,
            String cupsluz,
            String cupsgas,
            Date fechaBaja,
            String tarifaActual) {
        this.id = id;
        this.idCliente = idCliente;
        this.cliente = cliente;
        this.direccion = direccion;
        this.codigoPostal = codigoPostal;
        this.poblacion = poblacion;
        this.provincia = provincia;
        this.identificacion = identificacion;
        this.cupsluz = cupsluz;
        this.cupsgas = cupsgas;
        this.fechaBaja = fechaBaja;
        this.tarifaActual = tarifaActual;
        this.fechaFinUltimoContrato = null;
        this.potencia1 = null;
        this.potencia2 = null;
        this.potencia3 = null;
        this.potencia4 = null;
        this.potencia5 = null;
        this.potencia6 = null;
    }

    public PuntosSuministrosCarteraDTO(
            Integer id,
            Integer idCliente,
            String cliente,
            String direccion,
            String codigoPostal,
            String poblacion,
            String provincia,
            String identificacion,
            String cupsluz,
            String cupsgas,
            Date fechaBaja,
            String tarifaActual,
            Double potencia1,
            Double potencia2,
            Double potencia3,
            Double potencia4,
            Double potencia5,
            Double potencia6) {
        this.id = id;
        this.idCliente = idCliente;
        this.cliente = cliente;
        this.direccion = direccion;
        this.codigoPostal = codigoPostal;
        this.poblacion = poblacion;
        this.provincia = provincia;
        this.identificacion = identificacion;
        this.cupsluz = cupsluz;
        this.cupsgas = cupsgas;
        this.fechaBaja = fechaBaja;
        this.tarifaActual = tarifaActual;
        this.fechaFinUltimoContrato = null;
        this.potencia1 = potencia1;
        this.potencia2 = potencia2;
        this.potencia3 = potencia3;
        this.potencia4 = potencia4;
        this.potencia5 = potencia5;
        this.potencia6 = potencia6;
    }

    // Constructor completo con fecha fin del último contrato
    public PuntosSuministrosCarteraDTO(
            Integer id,
            Integer idCliente,
            String cliente,
            String direccion,
            String codigoPostal,
            String poblacion,
            String provincia,
            String identificacion,
            String cupsluz,
            String cupsgas,
            Date fechaBaja,
            String tarifaActual,
            Date fechaFinUltimoContrato,
            Double potencia1,
            Double potencia2,
            Double potencia3,
            Double potencia4,
            Double potencia5,
            Double potencia6) {
        this.id = id;
        this.idCliente = idCliente;
        this.cliente = cliente;
        this.direccion = direccion;
        this.codigoPostal = codigoPostal;
        this.poblacion = poblacion;
        this.provincia = provincia;
        this.identificacion = identificacion;
        this.cupsluz = cupsluz;
        this.cupsgas = cupsgas;
        this.fechaBaja = fechaBaja;
        this.tarifaActual = tarifaActual;
        this.fechaFinUltimoContrato = fechaFinUltimoContrato;
        this.potencia1 = potencia1;
        this.potencia2 = potencia2;
        this.potencia3 = potencia3;
        this.potencia4 = potencia4;
        this.potencia5 = potencia5;
        this.potencia6 = potencia6;
    }
}
