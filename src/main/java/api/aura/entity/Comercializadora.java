package api.aura.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "comercializadoras")
public class Comercializadora {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column
    private String descripcion;

    @Column
    private Boolean activo = true;
} 