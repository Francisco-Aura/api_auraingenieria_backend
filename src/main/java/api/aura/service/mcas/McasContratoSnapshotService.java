package api.aura.service.mcas;

import api.aura.entity.Contrato;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Construye la fotografia inmutable y el hash de los datos que afectan al calculo MCAS. */
@Component
public class McasContratoSnapshotService {

    private final ObjectMapper mapper;

    public McasContratoSnapshotService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Snapshot capturar(Contrato c) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("contrato_id", c.getId());
        datos.put("numero_contrato", c.getContrato());
        datos.put("cliente_id", c.getCliente() != null ? c.getCliente().getId() : null);
        datos.put("punto_suministro_id", c.getPuntoSuministro() != null ? c.getPuntoSuministro().getId() : null);
        datos.put("cups_luz", c.getPuntoSuministro() != null ? c.getPuntoSuministro().getCUPSluz() : null);
        datos.put("cups_gas", c.getPuntoSuministro() != null ? c.getPuntoSuministro().getCUPSgas() : null);
        datos.put("fecha_inicio", fecha(c.getFechaInicio()));
        datos.put("fecha_fin", fecha(c.getFechaFin()));
        datos.put("comercializadora_id", c.getIdComercializadoraActual());
        datos.put("tarifa_luz_id", ceroANull(c.getIdTarifaLuz()));
        datos.put("tarifa_gas_id", ceroANull(c.getIdTarifaGas()));
        datos.put("consumo_anual_kwh", decimal(c.getConsumoAnual()));
        datos.put("potencia_1_kw", decimal(c.getPotencia1()));
        datos.put("potencia_2_kw", decimal(c.getPotencia2()));
        datos.put("potencia_3_kw", decimal(c.getPotencia3()));
        datos.put("potencia_4_kw", decimal(c.getPotencia4()));
        datos.put("potencia_5_kw", decimal(c.getPotencia5()));
        datos.put("potencia_6_kw", decimal(c.getPotencia6()));
        datos.put("fee", normalizar(c.getMcasFeeEurMwh()));
        datos.put("fee_unidad", c.getMcasFeeUnidad());
        datos.put("tipo_precio", c.getMcasTipoPrecio());
        datos.put("tipo_suministro", c.getMcasTipoSuministro());
        datos.put("segmento", c.getMcasSegmento());
        datos.put("es_renovacion", c.getMcasEsRenovacion());
        datos.put("tipo_renovacion", c.getMcasTipoRenovacion());
        datos.put("ambito", c.getMcasAmbito());
        datos.put("gestora_id", c.getMcasIdGestora());
        datos.put("producto_comercial_id", c.getMcasIdProductoComercial());
        datos.put("zona_comision", c.getMcasZonaComision());
        datos.put("numero_pagos", c.getMcasNumeroPagos());
        datos.put("alta_nueva", c.getMcasAltaNueva());

        try {
            String json = mapper.writeValueAsString(datos);
            return new Snapshot(json, sha256(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo crear la instantanea MCAS", e);
        }
    }

    private Integer ceroANull(int valor) {
        return valor == 0 ? null : valor;
    }

    private BigDecimal decimal(Double valor) {
        return valor == null ? null : normalizar(BigDecimal.valueOf(valor));
    }

    private BigDecimal normalizar(BigDecimal valor) {
        return valor == null ? null : valor.stripTrailingZeros();
    }

    private String fecha(java.util.Date valor) {
        return valor == null ? null : api.aura.util.FechaNegocio.aLocalDate(valor).toString();
    }

    private String sha256(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public record Snapshot(String json, String hash) {}
}
