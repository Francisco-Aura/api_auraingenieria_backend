package api.aura.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import api.aura.entity.Incidencia;

import java.util.List;

@Repository
public interface InformesRepository extends JpaRepository<Incidencia, Integer> {

    @Query(value = """
        SELECT 
            TRIM(contratos.id) AS codigoContrato,
            contratos.fecha,
            contratos.fecha_envio,
            contratos.fecha_inicio,
            contratos.fecha_fin,
            TRIM(contratos.id_cliente) AS idCliente,
            TRIM(clientes.nombre) AS cliente,
            TRIM(punto_suministro.ID) AS idPuntoSuministro,
            TRIM(punto_suministro.CUPSluz) AS cupsLuz,
            TRIM(punto_suministro.CUPSgas) AS cupsGas,
            TRIM(clientes.user_telemedida) AS userTelemedida,
            TRIM(clientes.pass_telemedida) AS passTelemedida,
            TRIM(contrato) AS contrato,
            TRIM(tbtarifas_luz.ID) AS idTarifaLuz,
            TRIM(tbtarifas_luz.nombre) AS tarifaLuz,
            TRIM(tbtarifas_gas.ID) AS idTarifaGas,
            TRIM(tbtarifas_gas.nombre) AS tarifaGas,
            TRIM(potencia1) AS pot1,
            TRIM(potencia2) AS pot2,
            TRIM(potencia3) AS pot3,
            TRIM(potencia4) AS pot4,
            TRIM(potencia5) AS pot5,
            TRIM(potencia6) AS pot6,
            TRIM(consumo_anual) AS consumoAnual,
            TRIM(exencion_impuesto_electricidad) AS exencionIE,
            TRIM(autoconsumo) AS autoconsumo,
            TRIM(id_comercializadora_actual) AS idComercializadoraActual,
            TRIM(ANT.nombre) AS comercializadoraActual,
            TRIM(id_comercializadora_anterior) AS idComercializadoraAnterior,
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
}    
