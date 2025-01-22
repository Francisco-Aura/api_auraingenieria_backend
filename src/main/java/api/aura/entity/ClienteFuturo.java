package api.aura.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cliente_futuro")
public class ClienteFuturo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 255, nullable = true)
    private String actuacion;

    @Column(length = 255, nullable = true)
    private String comercializadora;

    @Column(length = 255, nullable = true)
    private String consumo;

    @Column(length = 255, nullable = true)
    private String cups;

    @Column(length = 255, nullable = true)
    private String emailContacto;

    @Column(name = "fecha_vencimiento", nullable = true)
    private LocalDateTime fechaVencimiento;

    @Column(length = 255, nullable = true)
    private String nombre;

    @Column(length = 255, nullable = true)
    private String notas;

    @Column(length = 255, nullable = true)
    private String tarifa;

    @Column(length = 255, nullable = true)
    private String telefonoContacto;
}