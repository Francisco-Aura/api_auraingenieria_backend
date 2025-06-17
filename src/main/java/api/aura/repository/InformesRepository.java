package api.aura.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import api.aura.entity.Incidencia;

import java.util.List;

@Repository
public interface InformesRepository extends JpaRepository<Incidencia, Integer> {

    @Query(value = """
        SELECT 
            contratos.id AS codigoContrato,
            contratos.fecha,
            contratos.fecha_envio,
            contratos.fecha_inicio,
            contratos.fecha_fin,
            contratos.id_cliente AS idCliente,
            TRIM(clientes.nombre) AS cliente,
            punto_suministro.ID AS idPuntoSuministro,
            TRIM(punto_suministro.CUPSluz) AS cupsLuz,
            TRIM(punto_suministro.CUPSgas) AS cupsGas,
            TRIM(clientes.user_telemedida) AS userTelemedida,
            TRIM(clientes.pass_telemedida) AS passTelemedida,
            TRIM(contrato) AS contrato,
            tbtarifas_luz.ID AS idTarifaLuz,
            TRIM(tbtarifas_luz.nombre) AS tarifaLuz,
            tbtarifas_gas.ID AS idTarifaGas,
            TRIM(tbtarifas_gas.nombre) AS tarifaGas,
            potencia1 AS pot1,
            potencia2 AS pot2,
            potencia3 AS pot3,
            potencia4 AS pot4,
            potencia5 AS pot5,
            potencia6 AS pot6,
            consumo_anual AS consumoAnual,
            exencion_impuesto_electricidad AS exencionIE,
            autoconsumo AS autoconsumo,
            id_comercializadora_actual AS idComercializadoraActual,
            TRIM(ANT.nombre) AS comercializadoraActual,
            id_comercializadora_anterior AS idComercializadoraAnterior,
            TRIM(ACT.nombre) AS comercializadoraAnterior,
            TRIM(contratos.observaciones) AS observaciones
        FROM 
            contratos
            JOIN punto_suministro ON contratos.id_punto_suministro = punto_suministro.ID
            JOIN tbtarifas_gas ON contratos.id_tarifa_gas = tbtarifas_gas.ID
            JOIN tbtarifas_luz ON contratos.id_tarifa_luz = tbtarifas_luz.ID
            JOIN clientes ON contratos.id_cliente = clientes.ID
            JOIN tbcomercializadoras AS ACT ON contratos.id_comercializadora_actual = ACT.ID
            JOIN tbcomercializadoras AS ANT ON contratos.id_comercializadora_anterior = ANT.ID
        WHERE 
            (contratos.fecha_baja = '0001-01-01' OR contratos.fecha_baja IS NULL) 
            AND (contratos.fecha_fin BETWEEN DATE_SUB(CURDATE(), INTERVAL 30 DAY) AND DATE_ADD(CURDATE(), INTERVAL 90 DAY))
        ORDER BY 
            fecha_fin ASC
        """, nativeQuery = true)    
    List<Object[]> findContratosPendientesRenovacion();


    @Query(value = """
        SELECT 
            LTRIM(RTRIM(punto_suministro.id)) as idPS,
            LTRIM(RTRIM(id_cliente)) as idCliente,
            LTRIM(RTRIM(clientes.nombre)) as cliente,
            LTRIM(RTRIM(CUPSluz)) as cupsLuz,
            LTRIM(RTRIM(CUPSgas)) as cupsGas,
            LTRIM(RTRIM(punto_suministro.direccion)) as direccion,
            LTRIM(RTRIM(punto_suministro.codigo_postal)) as codigoPostal,
            LTRIM(RTRIM(punto_suministro.poblacion)) as poblacion,
            LTRIM(RTRIM(punto_suministro.provincia)) as provincia
        FROM 
            punto_suministro 
            LEFT JOIN clientes ON punto_suministro.id_cliente = clientes.id
        WHERE 
            NOT EXISTS (SELECT id_punto_suministro FROM contratos WHERE contratos.id_punto_suministro = punto_suministro.id)
        """, nativeQuery = true)
    List<Object[]> findPuntosSuministroSinContrato();

    @Query(value = """
        SELECT 
            s.id AS idServicio,
            c.nombre AS cliente,
            s.descripcion AS servicio,
            s.fecha_fin_servicio AS fechaVencimiento,
            sp.importe_final AS importeTotal,
            sp.primer_pago_pagado,
            sp.segundo_pago_pagado,
            sp.tercer_pago_pagado,
            sp.cuarto_pago_pagado,
            sp.quinto_pago_pagado,
            sp.sexto_pago_pagado,
            sp.septimo_pago_pagado,
            sp.octavo_pago_pagado,
            sp.noveno_pago_pagado,
            sp.decimo_pago_pagado,
            sp.decimoprimer_pago_pagado,
            sp.decimosegundo_pago_pagado,
            CASE 
                WHEN s.fecha_fin_servicio < CURDATE() THEN 'Vencido'
                WHEN s.fecha_fin_servicio <= DATE_ADD(CURDATE(), INTERVAL 7 DAY) THEN 'Próximo a vencer'
                ELSE 'Pendiente'
            END AS estadoVencimiento,
            CASE
                WHEN (sp.primer_pago_pagado = 0 OR sp.segundo_pago_pagado = 0 OR sp.tercer_pago_pagado = 0 OR
                      sp.cuarto_pago_pagado = 0 OR sp.quinto_pago_pagado = 0 OR sp.sexto_pago_pagado = 0 OR
                      sp.septimo_pago_pagado = 0 OR sp.octavo_pago_pagado = 0 OR sp.noveno_pago_pagado = 0 OR
                      sp.decimo_pago_pagado = 0 OR sp.decimoprimer_pago_pagado = 0 OR sp.decimosegundo_pago_pagado = 0)
                THEN 'Pendiente'
                ELSE 'Pagado'
            END AS estadoPago,
            tbp.numero_cuotas AS numeroCuotas
        FROM servicios s
        INNER JOIN clientes c ON s.id_cliente = c.id
        INNER JOIN servicios_pagos sp ON sp.id_servicio = s.id
        INNER JOIN tbpago_plazos tbp ON sp.id_pago_plazos = tbp.id
        WHERE 
            (sp.primer_pago_pagado = 0 OR
             sp.segundo_pago_pagado = 0 OR
             sp.tercer_pago_pagado = 0 OR
             sp.cuarto_pago_pagado = 0 OR
             sp.quinto_pago_pagado = 0 OR
             sp.sexto_pago_pagado = 0 OR
             sp.septimo_pago_pagado = 0 OR
             sp.octavo_pago_pagado = 0 OR
             sp.noveno_pago_pagado = 0 OR
             sp.decimo_pago_pagado = 0 OR
             sp.decimoprimer_pago_pagado = 0 OR
             sp.decimosegundo_pago_pagado = 0)
            AND (:fechaDesde IS NULL OR s.fecha_fin_servicio >= :fechaDesde)
            AND (:fechaHasta IS NULL OR s.fecha_fin_servicio <= :fechaHasta)
        ORDER BY s.fecha_fin_servicio ASC
        """, nativeQuery = true)
    List<Object[]> findServiciosPendientesPago(
        @Param("fechaDesde") java.time.LocalDate fechaDesde,
        @Param("fechaHasta") java.time.LocalDate fechaHasta
    );

}    
