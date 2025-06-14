package api.aura.dto;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class ServicioDTO {
    private Integer id;
    private Date fecha;
    private Date fechaInicioServicio;
    private Date fechaFinServicio;
    private Date fechaPago;
    private String descripcion;
    private Integer idGestora;
    private Integer idProducto;
    private Integer idComercializadora;
    private String observaciones;
    private String cups;
    private Integer idCliente;
    private Integer idPuntoSuministro;
    private List<Boolean> pagos;
} 