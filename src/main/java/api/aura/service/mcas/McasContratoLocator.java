package api.aura.service.mcas;

import api.aura.dto.mcas.McasContratoEncontradoDTO;
import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.entity.tablas_maestras.TBComercializadoras;
import api.aura.repository.ContratoRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Localiza contratos por su número (el que ve el usuario) o por su id interno:
 * «Calcular propuesta» en Comisiones MCAS y «Probar con un contrato» en
 * Configuración MCAS.
 */
@Service
@RequiredArgsConstructor
public class McasContratoLocator {

    public static final int MAX_RESULTADOS = 10;

    private final ContratoRepository contratos;
    private final TBComercializadorasRepository comercializadoras;

    /**
     * Un único contrato: por número exacto (el más reciente si se repite) o, si la
     * referencia es numérica, por id.
     *
     * @throws IllegalArgumentException si no se indica referencia o no existe
     */
    public Contrato buscarUno(String referencia) {
        if (referencia == null || referencia.isBlank())
            throw new IllegalArgumentException("Indique el número o el ID del contrato");
        String ref = referencia.trim();
        Optional<Contrato> porNumero = contratos.findFirstByContratoOrderByIdDesc(ref);
        if (porNumero.isPresent()) return porNumero.get();
        if (esId(ref)) {
            Optional<Contrato> porId = contratos.findById(Integer.valueOf(ref));
            if (porId.isPresent()) return porId.get();
        }
        throw new IllegalArgumentException("No existe ningún contrato con número o ID «" + ref + "»");
    }

    /**
     * Hasta {@value #MAX_RESULTADOS} contratos para elegir: primero los de número
     * exacto, luego el de ese id (si la referencia es numérica) y después los que
     * empiezan por la referencia, los más recientes primero. Sin referencia, ninguno.
     */
    @Transactional(readOnly = true)
    public List<McasContratoEncontradoDTO> buscar(String referencia) {
        if (referencia == null || referencia.isBlank()) return List.of();
        String ref = referencia.trim();
        Map<Integer, Contrato> encontrados = new LinkedHashMap<>();
        contratos.findTop10ByContratoOrderByIdDesc(ref).forEach(c -> encontrados.putIfAbsent(c.getId(), c));
        if (esId(ref)) contratos.findById(Integer.valueOf(ref)).ifPresent(c -> encontrados.putIfAbsent(c.getId(), c));
        if (encontrados.size() < MAX_RESULTADOS)
            contratos.findTop10ByContratoStartingWithOrderByIdDesc(ref).forEach(c -> encontrados.putIfAbsent(c.getId(), c));
        List<Contrato> lista = encontrados.values().stream().limit(MAX_RESULTADOS).toList();

        Set<Integer> idsComercializadora = lista.stream().map(Contrato::getIdComercializadoraActual)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> nombres = idsComercializadora.isEmpty() ? Map.of()
                : comercializadoras.findAllById(idsComercializadora).stream()
                        .filter(c -> c.getNombre() != null)
                        .collect(Collectors.toMap(TBComercializadoras::getId, TBComercializadoras::getNombre));
        return lista.stream().map(c -> McasContratoEncontradoDTO.builder()
                .id(c.getId()).numero(c.getContrato())
                .cliente(c.getCliente() != null ? c.getCliente().getNombre() : null)
                .cups(cups(c.getPuntoSuministro()))
                .comercializadora(c.getIdComercializadoraActual() != null ? nombres.get(c.getIdComercializadoraActual()) : null)
                .fechaInicio(fecha(c.getFechaInicio()))
                .build()).toList();
    }

    private boolean esId(String ref) {
        return ref.matches("\\d{1,9}");
    }

    private String cups(PuntoSuministro punto) {
        if (punto == null) return null;
        String luz = punto.getCUPSluz();
        if (luz != null && !luz.isBlank()) return luz;
        String gas = punto.getCUPSgas();
        return gas != null && !gas.isBlank() ? gas : null;
    }

    private LocalDate fecha(Date valor) {
        return api.aura.util.FechaNegocio.aLocalDate(valor);
    }
}
