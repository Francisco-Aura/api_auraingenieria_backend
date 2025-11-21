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
            contratos.autoconsumo AS autoconsumo,
            id_comercializadora_actual AS idComercializadoraActual,
            TRIM(ACT.nombre) AS comercializadoraActual,
            id_comercializadora_anterior AS idComercializadoraAnterior,
            TRIM(ANT.nombre) AS comercializadoraAnterior,
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
            AND (punto_suministro.fecha_baja = '0001-01-01' OR punto_suministro.fecha_baja IS NULL) 
            AND (
                -- Contratos próximos a vencer (30 días antes hasta 90 días después)
                (contratos.fecha_fin BETWEEN DATE_SUB(CURDATE(), INTERVAL 30 DAY) AND DATE_ADD(CURDATE(), INTERVAL 90 DAY))
                OR
                -- Contratos vencidos de los últimos 3 meses
                (contratos.fecha_fin BETWEEN DATE_SUB(CURDATE(), INTERVAL 3 MONTH) AND CURDATE())
            )
            -- Excluir contratos que ya tienen una renovación (otro contrato con fecha_inicio >= fecha_fin del actual)
            AND NOT EXISTS (
                SELECT 1 
                FROM contratos AS renovacion
                WHERE renovacion.id_punto_suministro = contratos.id_punto_suministro
                    AND renovacion.id != contratos.id
                    AND renovacion.fecha_inicio >= contratos.fecha_fin
                    AND (renovacion.fecha_baja = '0001-01-01' OR renovacion.fecha_baja IS NULL)
            )
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
            (punto_suministro.fecha_baja = '0001-01-01' OR punto_suministro.fecha_baja IS NULL)
            AND NOT EXISTS (SELECT id_punto_suministro FROM contratos WHERE contratos.id_punto_suministro = punto_suministro.id)
        """, nativeQuery = true)
    List<Object[]> findPuntosSuministroSinContrato();

    @Query(value = """
        SELECT 
            s.id AS idServicio,
            c.nombre AS cliente,
            tg.nombre AS gestora,
            COALESCE(s.cups, ps.CUPSluz, ps.CUPSgas) AS cups,
            CASE 
                WHEN COALESCE(sp.primer_pago_pagado, 0) = 0 AND sp.primer_pago_fecha <= CURDATE() AND COALESCE(sp.primer_pago_importe, 0) > 0 THEN sp.primer_pago_fecha
                WHEN COALESCE(sp.segundo_pago_pagado, 0) = 0 AND sp.segundo_pago_fecha <= CURDATE() AND COALESCE(sp.segundo_pago_importe, 0) > 0 THEN sp.segundo_pago_fecha
                WHEN COALESCE(sp.tercer_pago_pagado, 0) = 0 AND sp.tercer_pago_fecha <= CURDATE() AND COALESCE(sp.tercer_pago_importe, 0) > 0 THEN sp.tercer_pago_fecha
                WHEN COALESCE(sp.cuarto_pago_pagado, 0) = 0 AND sp.cuarto_pago_fecha <= CURDATE() AND COALESCE(sp.cuarto_pago_importe, 0) > 0 THEN sp.cuarto_pago_fecha
                WHEN COALESCE(sp.quinto_pago_pagado, 0) = 0 AND sp.quinto_pago_fecha <= CURDATE() AND COALESCE(sp.quinto_pago_importe, 0) > 0 THEN sp.quinto_pago_fecha
                WHEN COALESCE(sp.sexto_pago_pagado, 0) = 0 AND sp.sexto_pago_fecha <= CURDATE() AND COALESCE(sp.sexto_pago_importe, 0) > 0 THEN sp.sexto_pago_fecha
                WHEN COALESCE(sp.septimo_pago_pagado, 0) = 0 AND sp.septimo_pago_fecha <= CURDATE() AND COALESCE(sp.septimo_pago_importe, 0) > 0 THEN sp.septimo_pago_fecha
                WHEN COALESCE(sp.octavo_pago_pagado, 0) = 0 AND sp.octavo_pago_fecha <= CURDATE() AND COALESCE(sp.octavo_pago_importe, 0) > 0 THEN sp.octavo_pago_fecha
                WHEN COALESCE(sp.noveno_pago_pagado, 0) = 0 AND sp.noveno_pago_fecha <= CURDATE() AND COALESCE(sp.noveno_pago_importe, 0) > 0 THEN sp.noveno_pago_fecha
                WHEN COALESCE(sp.decimo_pago_pagado, 0) = 0 AND sp.decimo_pago_fecha <= CURDATE() AND COALESCE(sp.decimo_pago_importe, 0) > 0 THEN sp.decimo_pago_fecha
                WHEN COALESCE(sp.decimoprimer_pago_pagado, 0) = 0 AND sp.decimoprimer_pago_fecha <= CURDATE() AND COALESCE(sp.decimoprimer_pago_importe, 0) > 0 THEN sp.decimoprimer_pago_fecha
                WHEN COALESCE(sp.decimosegundo_pago_pagado, 0) = 0 AND sp.decimosegundo_pago_fecha <= CURDATE() AND COALESCE(sp.decimosegundo_pago_importe, 0) > 0 THEN sp.decimosegundo_pago_fecha
                ELSE NULL
            END AS fechaSiguientePago,
            sp.importe_final AS importeTotal,
            CASE 
                WHEN COALESCE(sp.primer_pago_pagado, 0) = 0 AND sp.primer_pago_fecha <= CURDATE() AND COALESCE(sp.primer_pago_importe, 0) > 0 THEN 1
                WHEN COALESCE(sp.segundo_pago_pagado, 0) = 0 AND sp.segundo_pago_fecha <= CURDATE() AND COALESCE(sp.segundo_pago_importe, 0) > 0 THEN 2
                WHEN COALESCE(sp.tercer_pago_pagado, 0) = 0 AND sp.tercer_pago_fecha <= CURDATE() AND COALESCE(sp.tercer_pago_importe, 0) > 0 THEN 3
                WHEN COALESCE(sp.cuarto_pago_pagado, 0) = 0 AND sp.cuarto_pago_fecha <= CURDATE() AND COALESCE(sp.cuarto_pago_importe, 0) > 0 THEN 4
                WHEN COALESCE(sp.quinto_pago_pagado, 0) = 0 AND sp.quinto_pago_fecha <= CURDATE() AND COALESCE(sp.quinto_pago_importe, 0) > 0 THEN 5
                WHEN COALESCE(sp.sexto_pago_pagado, 0) = 0 AND sp.sexto_pago_fecha <= CURDATE() AND COALESCE(sp.sexto_pago_importe, 0) > 0 THEN 6
                WHEN COALESCE(sp.septimo_pago_pagado, 0) = 0 AND sp.septimo_pago_fecha <= CURDATE() AND COALESCE(sp.septimo_pago_importe, 0) > 0 THEN 7
                WHEN COALESCE(sp.octavo_pago_pagado, 0) = 0 AND sp.octavo_pago_fecha <= CURDATE() AND COALESCE(sp.octavo_pago_importe, 0) > 0 THEN 8
                WHEN COALESCE(sp.noveno_pago_pagado, 0) = 0 AND sp.noveno_pago_fecha <= CURDATE() AND COALESCE(sp.noveno_pago_importe, 0) > 0 THEN 9
                WHEN COALESCE(sp.decimo_pago_pagado, 0) = 0 AND sp.decimo_pago_fecha <= CURDATE() AND COALESCE(sp.decimo_pago_importe, 0) > 0 THEN 10
                WHEN COALESCE(sp.decimoprimer_pago_pagado, 0) = 0 AND sp.decimoprimer_pago_fecha <= CURDATE() AND COALESCE(sp.decimoprimer_pago_importe, 0) > 0 THEN 11
                WHEN COALESCE(sp.decimosegundo_pago_pagado, 0) = 0 AND sp.decimosegundo_pago_fecha <= CURDATE() AND COALESCE(sp.decimosegundo_pago_importe, 0) > 0 THEN 12
                ELSE NULL
            END AS siguientePagoNumero,
            CASE 
                WHEN COALESCE(sp.primer_pago_pagado, 0) = 0 AND sp.primer_pago_fecha <= CURDATE() AND COALESCE(sp.primer_pago_importe, 0) > 0 THEN sp.primer_pago_importe
                WHEN COALESCE(sp.segundo_pago_pagado, 0) = 0 AND sp.segundo_pago_fecha <= CURDATE() AND COALESCE(sp.segundo_pago_importe, 0) > 0 THEN sp.segundo_pago_importe
                WHEN COALESCE(sp.tercer_pago_pagado, 0) = 0 AND sp.tercer_pago_fecha <= CURDATE() AND COALESCE(sp.tercer_pago_importe, 0) > 0 THEN sp.tercer_pago_importe
                WHEN COALESCE(sp.cuarto_pago_pagado, 0) = 0 AND sp.cuarto_pago_fecha <= CURDATE() AND COALESCE(sp.cuarto_pago_importe, 0) > 0 THEN sp.cuarto_pago_importe
                WHEN COALESCE(sp.quinto_pago_pagado, 0) = 0 AND sp.quinto_pago_fecha <= CURDATE() AND COALESCE(sp.quinto_pago_importe, 0) > 0 THEN sp.quinto_pago_importe
                WHEN COALESCE(sp.sexto_pago_pagado, 0) = 0 AND sp.sexto_pago_fecha <= CURDATE() AND COALESCE(sp.sexto_pago_importe, 0) > 0 THEN sp.sexto_pago_importe
                WHEN COALESCE(sp.septimo_pago_pagado, 0) = 0 AND sp.septimo_pago_fecha <= CURDATE() AND COALESCE(sp.septimo_pago_importe, 0) > 0 THEN sp.septimo_pago_importe
                WHEN COALESCE(sp.octavo_pago_pagado, 0) = 0 AND sp.octavo_pago_fecha <= CURDATE() AND COALESCE(sp.octavo_pago_importe, 0) > 0 THEN sp.octavo_pago_importe
                WHEN COALESCE(sp.noveno_pago_pagado, 0) = 0 AND sp.noveno_pago_fecha <= CURDATE() AND COALESCE(sp.noveno_pago_importe, 0) > 0 THEN sp.noveno_pago_importe
                WHEN COALESCE(sp.decimo_pago_pagado, 0) = 0 AND sp.decimo_pago_fecha <= CURDATE() AND COALESCE(sp.decimo_pago_importe, 0) > 0 THEN sp.decimo_pago_importe
                WHEN COALESCE(sp.decimoprimer_pago_pagado, 0) = 0 AND sp.decimoprimer_pago_fecha <= CURDATE() AND COALESCE(sp.decimoprimer_pago_importe, 0) > 0 THEN sp.decimoprimer_pago_importe
                WHEN COALESCE(sp.decimosegundo_pago_pagado, 0) = 0 AND sp.decimosegundo_pago_fecha <= CURDATE() AND COALESCE(sp.decimosegundo_pago_importe, 0) > 0 THEN sp.decimosegundo_pago_importe
                ELSE NULL
            END AS importeSiguientePago,
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
                WHEN EXISTS (
                    SELECT 1 WHERE 
                    (COALESCE(sp.primer_pago_pagado, 0) = 0 AND sp.primer_pago_fecha <= CURDATE() AND COALESCE(sp.primer_pago_importe, 0) > 0) OR
                    (COALESCE(sp.segundo_pago_pagado, 0) = 0 AND sp.segundo_pago_fecha <= CURDATE() AND COALESCE(sp.segundo_pago_importe, 0) > 0) OR
                    (COALESCE(sp.tercer_pago_pagado, 0) = 0 AND sp.tercer_pago_fecha <= CURDATE() AND COALESCE(sp.tercer_pago_importe, 0) > 0) OR
                    (COALESCE(sp.cuarto_pago_pagado, 0) = 0 AND sp.cuarto_pago_fecha <= CURDATE() AND COALESCE(sp.cuarto_pago_importe, 0) > 0) OR
                    (COALESCE(sp.quinto_pago_pagado, 0) = 0 AND sp.quinto_pago_fecha <= CURDATE() AND COALESCE(sp.quinto_pago_importe, 0) > 0) OR
                    (COALESCE(sp.sexto_pago_pagado, 0) = 0 AND sp.sexto_pago_fecha <= CURDATE() AND COALESCE(sp.sexto_pago_importe, 0) > 0) OR
                    (COALESCE(sp.septimo_pago_pagado, 0) = 0 AND sp.septimo_pago_fecha <= CURDATE() AND COALESCE(sp.septimo_pago_importe, 0) > 0) OR
                    (COALESCE(sp.octavo_pago_pagado, 0) = 0 AND sp.octavo_pago_fecha <= CURDATE() AND COALESCE(sp.octavo_pago_importe, 0) > 0) OR
                    (COALESCE(sp.noveno_pago_pagado, 0) = 0 AND sp.noveno_pago_fecha <= CURDATE() AND COALESCE(sp.noveno_pago_importe, 0) > 0) OR
                    (COALESCE(sp.decimo_pago_pagado, 0) = 0 AND sp.decimo_pago_fecha <= CURDATE() AND COALESCE(sp.decimo_pago_importe, 0) > 0) OR
                    (COALESCE(sp.decimoprimer_pago_pagado, 0) = 0 AND sp.decimoprimer_pago_fecha <= CURDATE() AND COALESCE(sp.decimoprimer_pago_importe, 0) > 0) OR
                    (COALESCE(sp.decimosegundo_pago_pagado, 0) = 0 AND sp.decimosegundo_pago_fecha <= CURDATE() AND COALESCE(sp.decimosegundo_pago_importe, 0) > 0)
                )
                THEN 'Pendiente'
                ELSE 'Pagado'
            END AS estadoPago,
            tbp.numero_cuotas AS numeroCuotas
        FROM servicios s
        INNER JOIN clientes c ON s.id_cliente = c.id
        INNER JOIN servicios_pagos sp ON sp.id_servicio = s.id
        INNER JOIN tbpago_plazos tbp ON sp.id_pago_plazos = tbp.id
        LEFT JOIN tbgestoras tg ON s.id_gestora = tg.id
        LEFT JOIN punto_suministro ps ON s.id_punto_suministro = ps.id
        WHERE 
            ((COALESCE(sp.primer_pago_pagado, 0) = 0 AND sp.primer_pago_fecha <= CURDATE() AND COALESCE(sp.primer_pago_importe, 0) > 0) OR
             (COALESCE(sp.segundo_pago_pagado, 0) = 0 AND sp.segundo_pago_fecha <= CURDATE() AND COALESCE(sp.segundo_pago_importe, 0) > 0) OR
             (COALESCE(sp.tercer_pago_pagado, 0) = 0 AND sp.tercer_pago_fecha <= CURDATE() AND COALESCE(sp.tercer_pago_importe, 0) > 0) OR
             (COALESCE(sp.cuarto_pago_pagado, 0) = 0 AND sp.cuarto_pago_fecha <= CURDATE() AND COALESCE(sp.cuarto_pago_importe, 0) > 0) OR
             (COALESCE(sp.quinto_pago_pagado, 0) = 0 AND sp.quinto_pago_fecha <= CURDATE() AND COALESCE(sp.quinto_pago_importe, 0) > 0) OR
             (COALESCE(sp.sexto_pago_pagado, 0) = 0 AND sp.sexto_pago_fecha <= CURDATE() AND COALESCE(sp.sexto_pago_importe, 0) > 0) OR
             (COALESCE(sp.septimo_pago_pagado, 0) = 0 AND sp.septimo_pago_fecha <= CURDATE() AND COALESCE(sp.septimo_pago_importe, 0) > 0) OR
             (COALESCE(sp.octavo_pago_pagado, 0) = 0 AND sp.octavo_pago_fecha <= CURDATE() AND COALESCE(sp.octavo_pago_importe, 0) > 0) OR
             (COALESCE(sp.noveno_pago_pagado, 0) = 0 AND sp.noveno_pago_fecha <= CURDATE() AND COALESCE(sp.noveno_pago_importe, 0) > 0) OR
             (COALESCE(sp.decimo_pago_pagado, 0) = 0 AND sp.decimo_pago_fecha <= CURDATE() AND COALESCE(sp.decimo_pago_importe, 0) > 0) OR
             (COALESCE(sp.decimoprimer_pago_pagado, 0) = 0 AND sp.decimoprimer_pago_fecha <= CURDATE() AND COALESCE(sp.decimoprimer_pago_importe, 0) > 0) OR
             (COALESCE(sp.decimosegundo_pago_pagado, 0) = 0 AND sp.decimosegundo_pago_fecha <= CURDATE() AND COALESCE(sp.decimosegundo_pago_importe, 0) > 0))
            AND (s.fecha_baja IS NULL OR s.fecha_baja = '0001-01-01')
            AND (ps.fecha_baja IS NULL OR ps.fecha_baja = '0001-01-01')
            AND (:fechaDesde IS NULL OR s.fecha_fin_servicio >= :fechaDesde)
            AND (:fechaHasta IS NULL OR s.fecha_fin_servicio <= :fechaHasta)
        ORDER BY fechaSiguientePago ASC
        """, nativeQuery = true)
    List<Object[]> findServiciosPendientesPago(
        @Param("fechaDesde") java.time.LocalDate fechaDesde,
        @Param("fechaHasta") java.time.LocalDate fechaHasta
    );

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
            TRIM(contrato) AS contrato,
            tbtarifas_luz.ID AS idTarifaLuz,
            TRIM(tbtarifas_luz.nombre) AS tarifaLuz,
            tbtarifas_gas.ID AS idTarifaGas,
            TRIM(tbtarifas_gas.nombre) AS tarifaGas,
            potencia1,
            potencia2,
            potencia3,
            potencia4,
            potencia5,
            potencia6,
            consumo_anual AS consumoAnual,
            exencion_impuesto_electricidad AS exencionIE,
            contratos.autoconsumo AS autoconsumo,
            id_comercializadora_actual AS idComercializadoraActual,
            TRIM(ACT.nombre) AS comercializadoraActual,
            id_comercializadora_anterior AS idComercializadoraAnterior,
            TRIM(ANT.nombre) AS comercializadoraAnterior,
            TRIM(contratos.observaciones) AS observaciones
        FROM 
            contratos
            JOIN punto_suministro ON contratos.id_punto_suministro = punto_suministro.ID
            JOIN tbtarifas_gas ON contratos.id_tarifa_gas = tbtarifas_gas.ID
            JOIN tbtarifas_luz ON contratos.id_tarifa_luz = tbtarifas_luz.ID
            JOIN clientes ON contratos.id_cliente = clientes.ID
            LEFT JOIN tbcomercializadoras AS ACT ON contratos.id_comercializadora_actual = ACT.ID
            LEFT JOIN tbcomercializadoras AS ANT ON contratos.id_comercializadora_anterior = ANT.ID
        WHERE 
            (contratos.fecha_baja = '0001-01-01' OR contratos.fecha_baja IS NULL) 
            AND (punto_suministro.fecha_baja = '0001-01-01' OR punto_suministro.fecha_baja IS NULL)
            AND contratos.fecha_fin > CURDATE()
        ORDER BY 
            fecha_fin ASC
        """, nativeQuery = true)    
    List<Object[]> findContratosVigentes();

    @Query(value = """
        SELECT 
            s.id AS idServicio,
            c.nombre AS cliente,
            s.descripcion AS servicio,
            s.fecha_inicio_servicio AS fechaInicioServicio,
            s.fecha_fin_servicio AS fechaFinServicio,
            s.fecha_pago AS fechaPago,
            tg.nombre AS gestora,
            tp.nombre AS producto,
            tc.nombre AS comercializadora,
            s.cups,
            s.importe,
            s.descuento,
            s.importe_final AS importeFinal,
            tmp.nombre AS metodoPago,
            tpp.nombre AS pagoPlazos,
            tpp.numero_cuotas AS numeroCuotas,
            s.observaciones,
            CASE 
                WHEN s.fecha_fin_servicio < CURDATE() THEN 'Vencido'
                WHEN s.fecha_fin_servicio <= DATE_ADD(CURDATE(), INTERVAL 7 DAY) THEN 'Próximo a vencer'
                ELSE 'Vigente'
            END AS estadoVencimiento,
            CASE
                WHEN sp.id IS NULL THEN 'Sin plan de pagos'
                WHEN (sp.primer_pago_pagado = 0 OR sp.segundo_pago_pagado = 0 OR sp.tercer_pago_pagado = 0 OR
                      sp.cuarto_pago_pagado = 0 OR sp.quinto_pago_pagado = 0 OR sp.sexto_pago_pagado = 0 OR
                      sp.septimo_pago_pagado = 0 OR sp.octavo_pago_pagado = 0 OR sp.noveno_pago_pagado = 0 OR
                      sp.decimo_pago_pagado = 0 OR sp.decimoprimer_pago_pagado = 0 OR sp.decimosegundo_pago_pagado = 0)
                THEN 'Pendiente'
                ELSE 'Pagado'
            END AS estadoPago
        FROM servicios s
        INNER JOIN clientes c ON s.id_cliente = c.id
        LEFT JOIN servicios_pagos sp ON sp.id_servicio = s.id
        LEFT JOIN tbgestoras tg ON s.id_gestora = tg.id
        LEFT JOIN tbproductos tp ON s.id_producto = tp.id
        LEFT JOIN tbcomercializadoras tc ON s.id_comercializadora = tc.id
        LEFT JOIN tbmetodo_pago tmp ON sp.id_metodo_pago = tmp.id
        LEFT JOIN tbpago_plazos tpp ON sp.id_pago_plazos = tpp.id
        WHERE 
            (s.fecha_baja IS NULL OR s.fecha_baja = '0001-01-01')
            AND (:fechaDesde IS NULL OR s.fecha_fin_servicio >= :fechaDesde)
            AND (:fechaHasta IS NULL OR s.fecha_fin_servicio <= :fechaHasta)
        ORDER BY s.fecha_fin_servicio ASC
        """, nativeQuery = true)
    List<Object[]> findServiciosVigentes(
        @Param("fechaDesde") java.time.LocalDate fechaDesde,
        @Param("fechaHasta") java.time.LocalDate fechaHasta
    );

}