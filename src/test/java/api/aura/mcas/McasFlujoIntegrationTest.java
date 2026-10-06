package api.aura.mcas;

import api.aura.dto.mcas.McasSimulacionDTO;
import api.aura.entity.Cliente;
import api.aura.entity.Contrato;
import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import api.aura.entity.mcas.McasConjuntoReglas;
import api.aura.entity.mcas.McasPropuesta;
import api.aura.entity.mcas.McasReglaComision;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.entity.tablas_maestras.TBProductos;
import api.aura.entity.tablas_maestras.TBTarifasLuz;
import api.aura.entity.mcas.McasProductoComercial;
import api.aura.repository.ClienteRepository;
import api.aura.repository.ContratoRepository;
import api.aura.repository.ServicioRepository;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.mcas.McasConjuntoReglasRepository;
import api.aura.repository.mcas.McasPropuestaRepository;
import api.aura.repository.mcas.McasReglaComisionRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;
import api.aura.repository.tablas_maestras.TBProductosRepository;
import api.aura.repository.tablas_maestras.TBTarifasLuzRepository;
import api.aura.repository.mcas.McasProductoComercialRepository;
import api.aura.service.ContratoService;
import api.aura.service.ModuloConfiguracionService;
import api.aura.service.mcas.McasProposalService;
import api.aura.service.mcas.McasSimulacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flujo MCAS completo contra base de datos real (H2) y transacciones reales,
 * sin mocks: guardar el contrato, generar la propuesta tras el commit, aprobarla
 * y comprobar que un cambio posterior del contrato no produce otra comisión.
 *
 * <p>No es {@code @Transactional} a propósito: la propuesta se genera en la fase
 * AFTER_COMMIT del guardado del contrato, así que el test necesita commits
 * reales para reproducir lo que ocurre en producción.</p>
 */
@SpringBootTest
class McasFlujoIntegrationTest {

    private static final int COMERCIALIZADORA = 914;
    private static final String CONJUNTO = "IT-FIJO-914";

    @Autowired private ContratoService contratoService;
    @Autowired private McasProposalService mcas;
    @Autowired private McasSimulacionService simulacion;
    @Autowired private ModuloConfiguracionService modulos;
    @Autowired private ClienteRepository clientes;
    @Autowired private ContratoRepository contratos;
    @Autowired private McasPropuestaRepository propuestas;
    @Autowired private McasConjuntoReglasRepository conjuntos;
    @Autowired private McasReglaComisionRepository reglas;
    @Autowired private ServicioRepository servicios;
    @Autowired private ServiciosPagosRepository serviciosPagos;
    @Autowired private TBProductosRepository productosServicio;
    @Autowired private TBPagoPlazosRepository pagoPlazos;
    @Autowired private TBTarifasLuzRepository tarifasLuz;
    @Autowired private McasProductoComercialRepository productosComerciales;

    private Integer idSwitching;
    private Integer idUnPlazo;

    @BeforeEach
    void datosBase() {
        modulos.activar(McasProposalService.FLAG_ENABLED);
        modulos.activar(McasProposalService.FLAG_FIJO);
        idSwitching = productosServicio.findFirstByNombreContainingIgnoreCaseOrderByIdAsc("switching")
                .orElseGet(() -> {
                    productosServicio.save(TBProductos.builder().nombre("VENTA DE PRODUCTOS").build());
                    return productosServicio.save(TBProductos.builder().nombre("SWITCHING ").build());
                }).getId();
        idUnPlazo = pagoPlazos.findFirstByNumeroCuotas(1)
                .orElseGet(() -> pagoPlazos.save(TBPagoPlazos.builder().nombre("1 PLAZO").numeroCuotas(1).build()))
                .getId();
        if (conjuntos.findByCodigo(CONJUNTO).isEmpty()) {
            McasConjuntoReglas conjunto = conjuntos.save(McasConjuntoReglas.builder()
                    .codigo(CONJUNTO).version("1").idComercializadora(COMERCIALIZADORA)
                    .fasePrecio("FIJO").vigenciaDesde(LocalDate.of(2026, 1, 1))
                    .estado("PUBLICADO").creadoPor("test").build());
            reglas.save(McasReglaComision.builder().idConjuntoReglas(conjunto.getId())
                    .codigo("IT_IMPORTE_FIJO").prioridad(10).metodoCalculo("FIXED_AMOUNT")
                    .condicionesJson("{\"tipo_suministro\":\"LUZ\"}")
                    .calculoJson("{\"importe\":120}").activo(true).build());
        }
    }

    @Test
    void guardar_un_contrato_genera_y_persiste_la_propuesta_tras_el_commit() {
        Contrato contrato = nuevoContrato("IT-MCAS-1");

        List<McasPropuesta> generadas = propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId());

        assertThat(generadas).hasSize(1);
        assertThat(generadas.get(0).getEstado()).isEqualTo("PENDIENTE_VALIDACION");
        assertThat(generadas.get(0).getImporteCalculado()).isEqualByComparingTo("120.00");
    }

    @Test
    void aprobar_crea_un_unico_servicio_switching_y_los_cambios_posteriores_van_a_revision_manual() {
        Contrato contrato = nuevoContrato("IT-MCAS-2");
        McasPropuesta propuesta = propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId()).get(0);

        McasPropuesta aprobada = mcas.aprobar(propuesta.getId());

        Servicio servicio = servicios.findById(aprobada.getIdServicioCreado()).orElseThrow();
        assertThat(servicio.getIdProducto()).isEqualTo(idSwitching);
        assertThat(servicio.getIdPagoPlazos()).isEqualTo(idUnPlazo);
        assertThat(servicio.getImporte()).isEqualTo(120.0);
        ServiciosPagos pagos = serviciosPagos.findByServicioId(servicio.getId()).orElseThrow();
        assertThat(pagos.getPagoPlazos().getId()).isEqualTo(idUnPlazo);
        assertThat(pagos.getPrimerPagoImporte()).isEqualTo(120.0);

        // El contrato cambia después de aprobar la comisión (otro consumo anual).
        Contrato cambio = contratos.findById(contrato.getId()).orElseThrow();
        cambio.setConsumoAnual(70_000d);
        contratoService.guardarContrato(cambio);

        List<McasPropuesta> todas = propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId());
        assertThat(todas).hasSize(2);
        McasPropuesta nueva = todas.stream().filter(p -> !p.getId().equals(propuesta.getId())).findFirst().orElseThrow();
        assertThat(nueva.getEstado()).isEqualTo("REQUIERE_REVISION_MANUAL");
        assertThat(nueva.getAdvertenciasJson())
                .contains("propuesta #" + propuesta.getId())
                .contains("servicio #" + servicio.getId());
        assertThat(servicios.findAll().stream()
                .filter(s -> s.getCliente() != null && s.getCliente().getId().equals(contrato.getCliente().getId())))
                .hasSize(1);
        assertThatThrownBy(() -> mcas.aprobar(nueva.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void probar_un_conjunto_en_borrador_calcula_con_el_contrato_real_sin_guardar_nada() {
        McasConjuntoReglas borrador = conjuntos.save(McasConjuntoReglas.builder()
                .codigo("IT-BORRADOR-" + System.nanoTime()).version("1").idComercializadora(COMERCIALIZADORA)
                .fasePrecio("FIJO").vigenciaDesde(LocalDate.of(2026, 1, 1)).estado("BORRADOR").creadoPor("test").build());
        reglas.save(McasReglaComision.builder().idConjuntoReglas(borrador.getId())
                .codigo("IT_FEE_44").prioridad(10).metodoCalculo("FEE_PERCENTAGE")
                .condicionesJson("{\"tipo_suministro\":\"LUZ\",\"tarifa_acceso\":\"3.0TD\",\"codigo_producto\":\"IT_PLAN\"}")
                .calculoJson("{\"porcentaje_aplicacion_fee\":0.44}")
                .politicaPagoJson("{\"numero_pagos_sugerido\":2}").activo(true).build());
        // Tarifa y producto reales: la simulación los resuelve a través de McasProposalService.
        TBTarifasLuz tarifa = tarifasLuz.save(TBTarifasLuz.builder().nombre("3.0TD").build());
        McasProductoComercial producto = productosComerciales.save(McasProductoComercial.builder()
                .idComercializadora(COMERCIALIZADORA).codigo("IT_PLAN").nombre("Plan IT")
                .tipoPrecio("FIJO").tipoSuministro("LUZ").activo(true).build());
        Cliente cliente = clientes.save(Cliente.builder().nombre("Cliente simulación").cif("IT-SIM-1").build());
        Contrato contrato = contratoService.guardarContrato(Contrato.builder()
                .cliente(cliente).contrato("IT-SIM-1").idTarifaLuz(tarifa.getId())
                .mcasIdProductoComercial(producto.getId())
                .fechaInicio(Date.valueOf("2026-03-16")).idComercializadoraActual(COMERCIALIZADORA)
                .consumoAnual(58_500d).mcasFeeEurMwh(new java.math.BigDecimal("10")).mcasFeeUnidad("EUR_MWH")
                .mcasTipoPrecio("FIJO").mcasTipoSuministro("LUZ").build());
        long propuestasAntes = propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId()).size();
        long serviciosAntes = servicios.count();

        McasSimulacionDTO prueba = simulacion.simular(borrador.getId(), "IT-SIM-1");

        assertThat(prueba.getResultado()).isEqualTo("CALCULADO");
        assertThat(prueba.getReglaAplicada()).isEqualTo("IT_FEE_44");
        assertThat(prueba.getImporte()).isEqualByComparingTo("257.40");     // 58,5 MWh × 10 €/MWh × 44 %
        assertThat(prueba.getCalendario()).hasSize(2);
        assertThat(prueba.getLineas().get(0).descripcion()).isEqualTo("58,5 MWh × 10 €/MWh × 44 %");
        assertThat(prueba.getAvisos()).anyMatch(a -> a.contains("BORRADOR"));
        assertThat(prueba.getDatosContrato()).containsEntry("tarifaAcceso", "3.0TD").containsEntry("codigoProducto", "IT_PLAN");
        assertThat(propuestas.findByIdContratoOrderByCreadoEnDesc(contrato.getId())).hasSize((int) propuestasAntes);
        assertThat(servicios.count()).isEqualTo(serviciosAntes);

        // También por ID interno, y con mensaje claro si el contrato no existe.
        assertThat(simulacion.simular(borrador.getId(), String.valueOf(contrato.getId())).getIdContrato())
                .isEqualTo(contrato.getId());
        assertThatThrownBy(() -> simulacion.simular(borrador.getId(), "NO-EXISTE"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NO-EXISTE");
    }

    private Contrato nuevoContrato(String numero) {
        Cliente cliente = clientes.save(Cliente.builder().nombre("Cliente " + numero).cif(numero).build());
        return contratoService.guardarContrato(Contrato.builder()
                .cliente(cliente).contrato(numero)
                .fechaInicio(Date.valueOf("2026-03-16")).fechaFin(Date.valueOf("2027-03-15"))
                .idComercializadoraActual(COMERCIALIZADORA).consumoAnual(58_500d)
                .mcasTipoPrecio("FIJO").mcasTipoSuministro("LUZ").mcasNumeroPagos(1)
                .build());
    }
}
