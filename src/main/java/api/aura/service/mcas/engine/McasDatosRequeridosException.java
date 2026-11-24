package api.aura.service.mcas.engine;

import java.util.List;
import java.util.Objects;

/** Dato del contrato ausente para la estrategia seleccionada; no es un fallo técnico. */
public class McasDatosRequeridosException extends RuntimeException {
    /** Códigos técnicos de los datos que faltan (consumo_kwh, fee...); vacío si no se conocen. */
    private final List<String> campos;

    public McasDatosRequeridosException(String message) { this(message, List.of()); }

    public McasDatosRequeridosException(String message, List<String> campos) {
        super(message);
        this.campos = campos == null ? List.of() : campos.stream().filter(Objects::nonNull).toList();
    }

    public List<String> getCampos() { return campos; }
}
