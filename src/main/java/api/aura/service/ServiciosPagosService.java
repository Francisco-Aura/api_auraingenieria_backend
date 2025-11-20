package api.aura.service;

import api.aura.dto.CuotaServicioDTO;
import api.aura.entity.ServiciosPagos;
import api.aura.entity.tablas_maestras.TBPagoPlazos;
import api.aura.repository.ServiciosPagosRepository;
import api.aura.repository.tablas_maestras.TBPagoPlazosRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

@Service
public class ServiciosPagosService {

    @Autowired
    private ServiciosPagosRepository serviciosPagosRepository;

    @Autowired
    private TBPagoPlazosRepository pagoPlazosRepository;

    public ServiciosPagos obtenerPagosPorServicio(Integer idServicio) {
        return serviciosPagosRepository.findByServicioId(idServicio)
                .orElse(new ServiciosPagos());
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
        String[] prefijos = { "primer", "segundo", "tercer", "cuarto", "quinto", "sexto",
                "septimo", "octavo", "noveno", "decimo", "decimoprimer", "decimosegundo" };

        for (int i = 0; i < numeroCuotas && i < prefijos.length; i++) {
            try {
                String prefijo = prefijos[i];
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
                // Log error and continue
                System.err.println("Error procesando cuota " + (i + 1) + ": " + e.getMessage());
            }
        }

        return cuotas;
    }

    public ServiciosPagos actualizarPagosServicio(Integer idServicio, ServiciosPagos pagos) {
        Optional<ServiciosPagos> existente = serviciosPagosRepository.findByServicioId(idServicio);

        if (existente.isPresent()) {
            ServiciosPagos pagosExistente = existente.get();
            actualizarCampos(pagosExistente, pagos);
            return serviciosPagosRepository.save(pagosExistente);
        } else {
            return serviciosPagosRepository.save(pagos);
        }
    }

    public CuotaServicioDTO actualizarCuota(Integer idCuota, CuotaServicioDTO cuotaActualizada) {
        Optional<ServiciosPagos> pagosOpt = serviciosPagosRepository.findByServicioId(cuotaActualizada.getIdServicio());

        if (pagosOpt.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron pagos para el servicio ID: " + cuotaActualizada.getIdServicio());
        }

        ServiciosPagos pagos = pagosOpt.get();
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

            return serviciosPagosRepository.save(pagosExistente);
        } else {
            // Create new record
            return serviciosPagosRepository.save(serviciosPagos);
        }
    }

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

        // Reset existing installments
        resetearCuotas(serviciosPagos);

        // Calculate amount per installment (divide evenly for now, could be modified
        // for specific cases)
        Double importePorCuota = Math.round((importeTotal / numeroCuotas) * 100.0) / 100.0;

        // Set start date (default to current date if not provided)
        Date fechaInicio = new Date();
        if (serviciosPagos.getPrimerPagoFecha() != null) {
            fechaInicio = serviciosPagos.getPrimerPagoFecha();
        }

        // Generate installment schedule
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(fechaInicio);

        for (int i = 1; i <= Math.min(numeroCuotas, 12); i++) {
            String sufijo = obtenerSufijoCuota(i);

            try {
                // Set date
                Field fechaField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoFecha");
                fechaField.setAccessible(true);
                fechaField.set(serviciosPagos, calendar.getTime());

                // Set amount
                Field importeField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoImporte");
                importeField.setAccessible(true);
                importeField.set(serviciosPagos, importePorCuota);

                // Initialize as unpaid
                Field pagadoField = ServiciosPagos.class.getDeclaredField(sufijo + "PagoPagado");
                pagadoField.setAccessible(true);
                pagadoField.set(serviciosPagos, false);

                // Move to next month
                calendar.add(Calendar.MONTH, 1);
            } catch (Exception e) {
                // Log error and continue
                System.err.println("Error setting installment " + i + ": " + e.getMessage());
            }
        }

        // Helper para inicializar cuotas no usadas como pagadas
        inicializarCuotasNoUsadasComoPagadas(serviciosPagos, numeroCuotas);

        return serviciosPagos;
    }

    // Helper method to update payment installments
    private void actualizarCuotas(ServiciosPagos destino, ServiciosPagos origen) {
        // Update all 12 possible installments
        String[] prefijos = { "primer", "segundo", "tercer", "cuarto", "quinto", "sexto",
                "septimo", "octavo", "noveno", "decimo", "decimoprimer", "decimosegundo" };

        for (String prefijo : prefijos) {
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
                System.err.println("Error actualizando campo " + campo + ": " + e.getMessage());
            }
        }
    }

    private void actualizarCampos(ServiciosPagos destino, ServiciosPagos origen) {
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
                // Continue to next installment
            }
        }
    }

    private String obtenerSufijoCuota(int numero) {
        String[] sufijos = { "primer", "segundo", "tercer", "cuarto", "quinto", "sexto",
                "septimo", "octavo", "noveno", "decimo", "decimoprimer", "decimosegundo" };

        if (numero >= 1 && numero <= 12) {
            return sufijos[numero - 1];
        }

        throw new IllegalArgumentException("Número de cuota inválido: " + numero);
    }

    // Helper para inicializar cuotas no usadas como pagadas
    private void inicializarCuotasNoUsadasComoPagadas(ServiciosPagos serviciosPagos, int numeroCuotas) {
        String[] prefijos = { "primer", "segundo", "tercer", "cuarto", "quinto", "sexto",
                "septimo", "octavo", "noveno", "decimo", "decimoprimer", "decimosegundo" };
        for (int i = numeroCuotas; i < 12; i++) {
            try {
                Field pagadoField = ServiciosPagos.class.getDeclaredField(prefijos[i] + "PagoPagado");
                pagadoField.setAccessible(true);
                pagadoField.set(serviciosPagos, true); // Marcar como pagado
            } catch (Exception e) {
                // Ignorar errores
            }
        }
    }

    public void eliminarPagosPorServicio(Integer idServicio) {
        Optional<ServiciosPagos> pagosOpt = serviciosPagosRepository.findByServicioId(idServicio);
        if (pagosOpt.isPresent()) {
            serviciosPagosRepository.delete(pagosOpt.get());
        }
    }
}
