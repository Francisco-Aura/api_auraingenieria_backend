package api.aura.service;

import api.aura.dto.CuotaServicioDTO;
import api.aura.entity.Servicio;
import api.aura.entity.ServiciosPagos;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ServiciosPagosService {

    private static final Logger log = LoggerFactory.getLogger(ServiciosPagosService.class);

    /**
     * Prefijo del campo de cada cuota en {@code ServiciosPagos} (primerPagoFecha,
     * segundoPagoImporte...). Estaba copiado en cuatro métodos: basta con que una
     * copia se quede corta o cambie de orden para perder cuotas sin ningún aviso.
     */
    private static final String[] PREFIJOS_CUOTA = { "primer", "segundo", "tercer", "cuarto", "quinto", "sexto",
            "septimo", "octavo", "noveno", "decimo", "decimoprimer", "decimosegundo" };

    @Autowired
    private ServiciosPagosRepository serviciosPagosRepository;

    @Autowired
    private TBPagoPlazosRepository pagoPlazosRepository;

    public ServiciosPagos obtenerPagosPorServicio(Integer idServicio) {
        return serviciosPagosRepository.findByServicioId(idServicio)
                .orElse(new ServiciosPagos());
    }

    /** Planes de pagos de varios servicios de una vez (los que no tienen plan no salen). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ServiciosPagos> obtenerPagosPorServicios(List<Integer> idsServicio) {
        if (idsServicio == null || idsServicio.isEmpty()) return List.of();
        return serviciosPagosRepository.findByServicioIdIn(idsServicio);
    }

    public List<CuotaServicioDTO> obtenerCuotasPorServicio(Integer idServicio) {
        Optional<ServiciosPagos> pagosOpt = serviciosPagosRepository.findByServicioId(idServicio);
        if (!pagosOpt.isPresent()) {
            return new ArrayList<>();
        }

        ServiciosPagos pagos = pagosOpt.get();
        List<CuotaServicioDTO> cuotas = new ArrayList<>();

        // Obtener el número de cuotas desde la modalidad de pago
        int numeroCuotas = 12;
        if (pagos.getPagoPlazos() != null && pagos.getPagoPlazos().getNumeroCuotas() != null) {
            numeroCuotas = pagos.getPagoPlazos().getNumeroCuotas();
        }

        // Crear cuotas solo hasta el número definido en la modalidad

        for (int i = 0; i < numeroCuotas && i < PREFIJOS_CUOTA.length; i++) {
            try {
                String prefijo = PREFIJOS_CUOTA[i];
                Field fechaField = ServiciosPagos.class.getDeclaredField(prefijo + "PagoFecha");
                Field importeField = ServiciosPagos.class.getDeclaredField(prefijo + "PagoImporte");
                Field pagadoField = ServiciosPagos.class.getDeclaredField(prefijo + "PagoPagado");

                fechaField.setAccessible(true);
                importeField.setAccessible(true);
                pagadoField.setAccessible(true);

                Date fecha = (Date) fechaField.get(pagos);
                Double importe = (Double) importeField.get(pagos);
                Boolean pagado = (Boolean) pagadoField.get(pagos);

                if (fecha != null || importe != null) {
                    CuotaServicioDTO cuota = CuotaServicioDTO.builder()
                            .id(i + 1)
                            .idServicio(idServicio)
                            .numeroPago(i + 1)
                            .fechaPago(fecha)
                            .importePago(importe)
                            .pagado(pagado != null ? pagado : false)
                            .build();
                    cuotas.add(cuota);
                }
            } catch (Exception e) {
                // Solo pasaría si cambia un nombre de campo de ServiciosPagos: que se vea
                log.error("Plan de pagos: no se pudo leer o escribir la cuota {}", i, e);
            }
        }

        return cuotas;
    }

    @Transactional
    public ServiciosPagos actualizarPagosServicio(Integer idServicio, ServiciosPagos pagos) {
        Optional<ServiciosPagos> existente = serviciosPagosRepository.findByServicioId(idServicio);

        if (existente.isPresent()) {
            ServiciosPagos pagosExistente = existente.get();
            actualizarCampos(pagosExistente, pagos);
            return serviciosPagosRepository.save(pagosExistente);
        } else {
            // Alta: siempre ligada al servicio de la ruta. Antes se guardaba el cuerpo tal
            // cual: sin servicio quedaba un registro de pagos huérfano y con el id de otro
            // registro se sobrescribían los pagos de otro servicio.
            pagos.setId(null);
            if (pagos.getServicio() == null || !idServicio.equals(pagos.getServicio().getId())) {
                pagos.setServicio(Servicio.builder().id(idServicio).build());
            }
            return serviciosPagosRepository.save(pagos);
        }
    }

    @Transactional
    public CuotaServicioDTO actualizarCuota(Integer idCuota, CuotaServicioDTO cuotaActualizada) {
        Optional<ServiciosPagos> pagosOpt = serviciosPagosRepository.findByServicioId(cuotaActualizada.getIdServicio());

        if (pagosOpt.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron pagos para el servicio ID: " + cuotaActualizada.getIdServicio());
        }

        ServiciosPagos pagos = pagosOpt.get();
        if (cuotaActualizada.getNumeroPago() == null) {
            throw new IllegalArgumentException("El número de pago de la cuota es obligatorio");
        }
        String prefijo = obtenerSufijoCuota(cuotaActualizada.getNumeroPago());

        try {
            // Actualizar el estado de pago
            Field pagadoField = ServiciosPagos.class.getDeclaredField(prefijo + "PagoPagado");
            pagadoField.setAccessible(true);
            pagadoField.set(pagos, cuotaActualizada.getPagado());

            // Opcional: Actualizar fecha e importe si se modifican desde el frontend
            if (cuotaActualizada.getFechaPago() != null) {
                Field fechaField = ServiciosPagos.class.getDeclaredField(prefijo + "PagoFecha");
                fechaField.setAccessible(true);
                fechaField.set(pagos, cuotaActualizada.getFechaPago());
            }
            if (cuotaActualizada.getImportePago() != null) {
                Field importeField = ServiciosPagos.class.getDeclaredField(prefijo + "PagoImporte");
                importeField.setAccessible(true);
                importeField.set(pagos, cuotaActualizada.getImportePago());
            }

            serviciosPagosRepository.save(pagos); // Guardar los cambios en la base de datos
            return cuotaActualizada; // Retornar la cuota actualizada

        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Campo de cuota inválido para el número de pago: " + cuotaActualizada.getNumeroPago(), e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error de acceso al campo al actualizar la cuota: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error al actualizar la cuota: " + e.getMessage(), e);
        }
    }

    @Transactional
    public ServiciosPagos guardar(ServiciosPagos serviciosPagos) {
        // Validate required fields
        if (serviciosPagos.getServicio() == null || serviciosPagos.getServicio().getId() == null) {
            throw new IllegalArgumentException("El servicio asociado es obligatorio");
        }

        // Check if there's already a payment record for this service
        Optional<ServiciosPagos> existente = serviciosPagosRepository
                .findByServicioId(serviciosPagos.getServicio().getId());

        if (existente.isPresent()) {
            // Update existing record
            ServiciosPagos pagosExistente = existente.get();
            Integer modalidadAnterior = idModalidad(pagosExistente.getPagoPlazos());

            // Update payment plan and method if provided
            if (serviciosPagos.getPagoPlazos() != null) {
                pagosExistente.setPagoPlazos(serviciosPagos.getPagoPlazos());
            }
            if (serviciosPagos.getMetodoPago() != null) {
                pagosExistente.setMetodoPago(serviciosPagos.getMetodoPago());
            }

            // Update amounts
            if (serviciosPagos.getImporte() != null) {
                pagosExistente.setImporte(serviciosPagos.getImporte());
            }
            if (serviciosPagos.getDescuento() != null) {
                pagosExistente.setDescuento(serviciosPagos.getDescuento());
            }
            if (serviciosPagos.getImporteFinal() != null) {
                pagosExistente.setImporteFinal(serviciosPagos.getImporteFinal());
            }

            // Update all payment installments
            actualizarCuotas(pagosExistente, serviciosPagos);
            if (cambiaDeModalidad(modalidadAnterior, serviciosPagos.getPagoPlazos())) {
                cerrarCuotasFueraDelPlan(pagosExistente);
            }

            return serviciosPagosRepository.save(pagosExistente);
        } else {
            // Create new record. Siempre como fila nueva: un id en el cuerpo convertía el
            // alta en una sobrescritura de los pagos de otro servicio
            serviciosPagos.setId(null);
            return serviciosPagosRepository.save(serviciosPagos);
        }
    }

    @Transactional
    public ServiciosPagos generarCuotasSegunModalidad(ServiciosPagos serviciosPagos, Integer idPagoPlazos,
            Double importeTotal) {
        if (idPagoPlazos == null || importeTotal == null) {
            return serviciosPagos; // No changes if required data is missing
        }

        // Get payment plan details
        Optional<TBPagoPlazos> planOptional = pagoPlazosRepository.findById(idPagoPlazos);
        if (!planOptional.isPresent()) {
            return serviciosPagos;
        }

        TBPagoPlazos plan = planOptional.get();
        Integer numeroCuotas = plan.getNumeroCuotas();

        if (numeroCuotas == null || numeroCuotas <= 0) {
            return serviciosPagos;
        }

        // Fecha de la primera cuota: la de inicio del servicio que envía el formulario
        // (llega en primerPagoFecha), o hoy si no viene. Se lee ANTES de vaciar las
        // cuotas: antes se leía después, ya borrada, y el plan empezaba siempre hoy.
        Date fechaInicio = serviciosPagos.getPrimerPagoFecha() != null
                ? serviciosPagos.getPrimerPagoFecha()
                : new Date();

        // Reset existing installments
        resetearCuotas(serviciosPagos);

        int numeroCuotasUsadas = Math.min(numeroCuotas, 12);
        long importeTotalCentimos = BigDecimal.valueOf(importeTotal)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        long importeBaseCentimos = importeTotalCentimos / numeroCuotasUsadas;
        long importeUltimaCuotaCentimos = importeTotalCentimos
                - importeBaseCentimos * (numeroCuotasUsadas - 1L);

        // Generate installment schedule
        for (int i = 1; i <= numeroCuotasUsadas; i++) {
            String sufijo = obtenerSufijoCuota(i);

            try {
                // Set date
                Field fechaField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoFecha");
                fechaField.setAccessible(true);
                fechaField.set(serviciosPagos, fechaCuota(fechaInicio, i));

                // Set amount
                Field importeField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoImporte");
                importeField.setAccessible(true);
                long importeCuotaCentimos = i == numeroCuotasUsadas
                        ? importeUltimaCuotaCentimos
                        : importeBaseCentimos;
                importeField.set(serviciosPagos, BigDecimal.valueOf(importeCuotaCentimos, 2).doubleValue());

                // Initialize as unpaid
                Field pagadoField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoPagado");
                pagadoField.setAccessible(true);
                pagadoField.set(serviciosPagos, false);
            } catch (Exception e) {
                // Solo pasaría si cambia un nombre de campo de ServiciosPagos: que se vea
                log.error("Plan de pagos: no se pudo leer o escribir la cuota {}", i, e);
            }
        }

        // Helper para inicializar cuotas no usadas como pagadas
        inicializarCuotasNoUsadasComoPagadas(serviciosPagos, numeroCuotas);

        return serviciosPagos;
    }

    /**
     * Fecha de la cuota {@code numero} (1 a 12): la de inicio más {@code numero - 1}
     * meses, igual que el calendario MCAS ({@code plusMonths} desde la primera).
     * Antes se sumaba un mes a la cuota anterior y el recorte de fin de mes se
     * arrastraba: un plan que empezaba el 31/01 seguía 28/02, 28/03, 28/04... en vez
     * de 28/02, 31/03, 30/04.
     */
    static Date fechaCuota(Date inicio, int numero) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(inicio);
        calendar.add(Calendar.MONTH, numero - 1);
        return calendar.getTime();
    }

    // Helper method to update payment installments
    private void actualizarCuotas(ServiciosPagos destino, ServiciosPagos origen) {
        // Update all 12 possible installments

        for (String prefijo : PREFIJOS_CUOTA) {
            actualizarCamposSiNoSonNulos(destino, origen, prefijo + "PagoFecha", prefijo + "PagoImporte",
                    prefijo + "PagoPagado");
        }
    }

    private void actualizarCamposSiNoSonNulos(ServiciosPagos destino, ServiciosPagos origen, String... campos) {
        for (String campo : campos) {
            try {
                Field field = ServiciosPagos.class.getDeclaredField(campo);
                field.setAccessible(true);
                Object valor = field.get(origen);
                if (valor != null) {
                    field.set(destino, valor);
                }
            } catch (Exception e) {
                log.error("Plan de pagos: no se pudo copiar el campo {}", campo, e);
            }
        }
    }

    private void actualizarCampos(ServiciosPagos destino, ServiciosPagos origen) {
        Integer modalidadAnterior = idModalidad(destino.getPagoPlazos());
        if (origen.getPagoPlazos() != null)
            destino.setPagoPlazos(origen.getPagoPlazos());
        if (origen.getMetodoPago() != null)
            destino.setMetodoPago(origen.getMetodoPago());
        if (origen.getImporte() != null)
            destino.setImporte(origen.getImporte());
        if (origen.getDescuento() != null)
            destino.setDescuento(origen.getDescuento());
        if (origen.getImporteFinal() != null)
            destino.setImporteFinal(origen.getImporteFinal());

        // Actualizar todas las cuotas
        actualizarCuotas(destino, origen);
        if (cambiaDeModalidad(modalidadAnterior, origen.getPagoPlazos())) {
            cerrarCuotasFueraDelPlan(destino);
        }
    }

    private static Integer idModalidad(TBPagoPlazos plan) {
        return plan == null ? null : plan.getId();
    }

    /**
     * Solo al cambiar de modalidad se cierran las cuotas que sobran. Guardar con la misma
     * no toca nada: si alguien cambia después el número de cuotas de una modalidad en
     * Administración, editar un servicio no borra las cuotas de su plan.
     */
    private static boolean cambiaDeModalidad(Integer modalidadAnterior, TBPagoPlazos nueva) {
        return nueva != null && !java.util.Objects.equals(modalidadAnterior, nueva.getId());
    }

    /**
     * Al pasar a una modalidad con menos cuotas, las que sobran se quedaban en la fila
     * con su importe y sin pagar (el formulario las envía vacías y aquí no se copian los
     * nulos): «Servicios pendientes de pago» las seguía reclamando y no había forma de
     * quitarlas. Las que sobran y no estaban pagadas quedan como las no usadas de un plan
     * nuevo (sin fecha ni importe, marcadas como pagadas); las ya pagadas no se tocan.
     */
    private void cerrarCuotasFueraDelPlan(ServiciosPagos pagos) {
        Integer numeroCuotas = numeroCuotasDelPlan(pagos.getPagoPlazos());
        if (numeroCuotas == null || numeroCuotas <= 0) return;
        for (int i = numeroCuotas; i < PREFIJOS_CUOTA.length; i++) {
            String prefijo = PREFIJOS_CUOTA[i];
            try {
                Field pagado = ServiciosPagos.class.getDeclaredField(prefijo + "PagoPagado");
                pagado.setAccessible(true);
                if (Boolean.TRUE.equals(pagado.get(pagos))) continue;
                Field fecha = ServiciosPagos.class.getDeclaredField(prefijo + "PagoFecha");
                Field importe = ServiciosPagos.class.getDeclaredField(prefijo + "PagoImporte");
                fecha.setAccessible(true);
                importe.setAccessible(true);
                fecha.set(pagos, null);
                importe.set(pagos, null);
                pagado.set(pagos, true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Campo de cuota no encontrado: " + prefijo, e);
            }
        }
    }

    private Integer numeroCuotasDelPlan(TBPagoPlazos plan) {
        if (plan == null) return null;
        if (plan.getNumeroCuotas() != null) return plan.getNumeroCuotas();
        if (plan.getId() == null) return null;
        return pagoPlazosRepository.findById(plan.getId()).map(TBPagoPlazos::getNumeroCuotas).orElse(null);
    }

    private void resetearCuotas(ServiciosPagos serviciosPagos) {
        for (int i = 1; i <= 12; i++) {
            String sufijo = obtenerSufijoCuota(i);
            try {
                Field fechaField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoFecha");
                Field importeField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoImporte");
                Field pagadoField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoPagado");

                fechaField.setAccessible(true);
                importeField.setAccessible(true);
                pagadoField.setAccessible(true);

                fechaField.set(serviciosPagos, null);
                importeField.set(serviciosPagos, null);
                pagadoField.set(serviciosPagos, false);
            } catch (Exception e) {
                log.error("Plan de pagos: no se pudo vaciar la cuota {}", i, e);
            }
        }
    }

    private String obtenerSufijoCuota(int numero) {

        if (numero >= 1 && numero <= 12) {
            return PREFIJOS_CUOTA[numero - 1];
        }

        throw new IllegalArgumentException("Número de cuota inválido: " + numero);
    }

    // Helper para inicializar cuotas no usadas como pagadas
    private void inicializarCuotasNoUsadasComoPagadas(ServiciosPagos serviciosPagos, int numeroCuotas) {
        for (int i = numeroCuotas; i < 12; i++) {
            try {
                Field pagadoField = ServiciosPagos.class.getDeclaredField(PREFIJOS_CUOTA[i] + "PagoPagado");
                pagadoField.setAccessible(true);
                pagadoField.set(serviciosPagos, true); // Marcar como pagado
            } catch (Exception e) {
                log.error("Plan de pagos: no se pudo cerrar la cuota {} fuera del plan", i + 1, e);
            }
        }
    }

    @Transactional
    public void eliminarPagosPorServicio(Integer idServicio) {
        Optional<ServiciosPagos> pagosOpt = serviciosPagosRepository.findByServicioId(idServicio);
        if (pagosOpt.isPresent()) {
            serviciosPagosRepository.delete(pagosOpt.get());
        }
    }
}
