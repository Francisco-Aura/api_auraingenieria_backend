package api.aura.mcas;

import api.aura.entity.Contrato;
import api.aura.entity.PuntoSuministro;
import api.aura.entity.mcas.McasZonaComision;
import api.aura.repository.PuntoSuministroRepository;
import api.aura.repository.mcas.McasZonaComisionRepository;
import api.aura.repository.tablas_maestras.TBComercializadorasRepository;
import api.aura.service.mcas.McasZonasService;
import api.aura.service.mcas.McasZonasService.Resuelta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Zona de comisión por la provincia del punto de suministro (reunión del 04/11/2026 y
 * respuesta del cliente a la duda 5).
 */
class McasZonasServiceTest {

    private static final int IBERDROLA = 5;
    private static final int ENDESA = 2;

    private final McasZonaComisionRepository repo = mock(McasZonaComisionRepository.class);
    private final TBComercializadorasRepository comercializadoras = mock(TBComercializadorasRepository.class);
    private final PuntoSuministroRepository puntos = mock(PuntoSuministroRepository.class);
    private final McasZonasService servicio = new McasZonasService(repo, comercializadoras, puntos);

    @BeforeEach
    void maestros() {
        when(comercializadoras.existsById(any())).thenReturn(true);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of(
                zona(1, "ZAR", false, "30", "03", "46"), zona(2, "ZBR", true)));
        when(repo.findByIdComercializadora(ENDESA)).thenReturn(List.of());
    }

    @Test
    void con_zonas_manda_la_provincia_del_punto_y_el_resto_va_a_la_zona_del_resto() {
        assertThat(servicio.resolver(IBERDROLA, "LUZ", "Murcia", "ES0031000000000001AA")).isEqualTo(new Resuelta("ZAR", null));
        assertThat(servicio.resolver(IBERDROLA, "LUZ", "ALICANTE", null).zona()).isEqualTo("ZAR");
        assertThat(servicio.resolver(IBERDROLA, "LUZ", "Almería", "ES0021000000000001AA").zona()).isEqualTo("ZBR");
        // Con zonas por provincia vale igual para el gas: ya no depende del CUPS de luz
        assertThat(servicio.resolver(IBERDROLA, "GAS", "valència", null).zona()).isEqualTo("ZAR");
    }

    @Test
    void con_zonas_una_provincia_vacia_o_que_no_se_reconoce_no_cae_en_el_resto_se_dice_que_falta() {
        Resuelta vacia = servicio.resolver(IBERDROLA, "LUZ", " ", "ES0021000000000001AA");
        assertThat(vacia.zona()).isNull();
        assertThat(vacia.motivo()).isEqualTo(
                "El punto de suministro no tiene provincia: elíjala en el punto para saber su zona de comisión.");

        Resuelta rara = servicio.resolver(IBERDROLA, "LUZ", "Mursia", null);
        assertThat(rara.zona()).isNull();
        assertThat(rara.motivo()).isEqualTo("No se reconoce la provincia del punto de suministro (Mursia): "
                + "elíjala de la lista en el punto para saber su zona de comisión.");
    }

    @Test
    void sin_zona_del_resto_una_provincia_que_no_esta_en_ninguna_se_avisa_con_su_nombre() {
        when(repo.findByIdComercializadora(IBERDROLA)).thenReturn(List.of(zona(1, "ZAR", false, "30")));

        Resuelta r = servicio.resolver(IBERDROLA, "LUZ", "almeria", null);

        assertThat(r.zona()).isNull();
        assertThat(r.motivo()).isEqualTo("Almería no está en ninguna zona de comisión de la comercializadora: "
                + "añádala en Configuración MCAS, pestaña Zonas.");
    }

    @Test
    void sin_zonas_sigue_la_regla_del_cups_de_la_hoja_de_iberdrola_y_solo_en_luz() {
        assertThat(servicio.resolver(ENDESA, "LUZ", "Murcia", "es0021000000000001aa").zona()).isEqualTo("ZAR");
        assertThat(servicio.resolver(ENDESA, "LUZ", "Murcia", "ES0031000000000001AA").zona()).isEqualTo("ZBR");
        assertThat(servicio.resolver(ENDESA, "GAS", "Murcia", "ES0021000000000001AA")).isEqualTo(new Resuelta(null, null));
        assertThat(servicio.resolver(ENDESA, "LUZ", "Murcia", " ")).isEqualTo(new Resuelta(null, null));
        assertThat(servicio.resolver(null, "LUZ", "Murcia", "ES0021000000000001AA")).isEqualTo(new Resuelta(null, null));
    }

    @Test
    void del_contrato_lee_el_punto_aunque_de_la_pantalla_solo_llegue_su_id_y_pisa_lo_escrito_a_mano() {
        PuntoSuministro guardado = PuntoSuministro.builder().id(9).provincia("MURCIA").CUPSluz("ES0031000000000001AA").build();
        when(puntos.findById(9)).thenReturn(Optional.of(guardado));
        Contrato c = Contrato.builder().idComercializadoraActual(IBERDROLA).mcasTipoSuministro("LUZ")
                .mcasZonaComision("ZBR").puntoSuministro(PuntoSuministro.builder().id(9).build()).build();

        assertThat(servicio.aplicar(c).zona()).isEqualTo("ZAR");
        assertThat(c.getMcasZonaComision()).isEqualTo("ZAR");

        Contrato sinPunto = Contrato.builder().idComercializadoraActual(IBERDROLA).mcasZonaComision("ZAR").build();
        assertThat(servicio.aplicar(sinPunto).zona()).isNull();
        assertThat(sinPunto.getMcasZonaComision()).isNull();
    }

    @Test
    void se_guarda_en_mayusculas_con_las_provincias_ordenadas_y_la_del_resto_sin_provincias() {
        when(repo.findByIdComercializadora(ENDESA)).thenReturn(List.of());

        McasZonaComision zar = servicio.crear(McasZonaComision.builder().idComercializadora(ENDESA).zona(" zar ")
                .provincias(new ArrayList<>(List.of("46", "03", "46"))).build());
        assertThat(zar.getZona()).isEqualTo("ZAR");
        assertThat(zar.getProvincias()).containsExactly("03", "46");
        assertThat(zar.getEsResto()).isFalse();
        assertThat(zar.getActualizadoEn()).isNotNull();

        McasZonaComision resto = servicio.crear(McasZonaComision.builder().idComercializadora(ENDESA).zona("zbr")
                .esResto(true).provincias(new ArrayList<>(List.of("30"))).build());
        assertThat(resto.getProvincias()).isEmpty();
        assertThat(resto.getEsResto()).isTrue();
    }

    @Test
    void se_valida_con_las_palabras_del_formulario() {
        assertThatThrownBy(() -> servicio.crear(McasZonaComision.builder().zona("ZAR").build()))
                .hasMessage("Elija la comercializadora.");
        assertThatThrownBy(() -> servicio.crear(nueva(ENDESA, " ", false, "30")))
                .hasMessage("Escriba el nombre de la zona (por ejemplo, ZAR).");
        assertThatThrownBy(() -> servicio.crear(nueva(ENDESA, "UNA ZONA CON NOMBRE LARGO", false, "30")))
                .hasMessage("El nombre de la zona no puede tener más de 20 caracteres.");
        assertThatThrownBy(() -> servicio.crear(nueva(ENDESA, "ZAR", false)))
                .hasMessage("Marque las provincias de la zona, o «Son todas las demás provincias».");
        assertThatThrownBy(() -> servicio.crear(nueva(ENDESA, "ZAR", false, "99")))
                .hasMessage("Hay una provincia que no es válida: 99.");
    }

    @Test
    void una_provincia_solo_puede_estar_en_una_zona_y_solo_hay_una_zona_del_resto() {
        assertThatThrownBy(() -> servicio.crear(nueva(IBERDROLA, "zar", false, "04")))
                .hasMessage("Esa comercializadora ya tiene la zona ZAR: edítela en vez de crear otra.");
        assertThatThrownBy(() -> servicio.crear(nueva(IBERDROLA, "ZCR", false, "04", "30")))
                .hasMessage("Murcia ya está en la zona ZAR de esta comercializadora: una provincia solo puede estar en una zona.");
        assertThatThrownBy(() -> servicio.crear(nueva(IBERDROLA, "OTRA", true)))
                .hasMessage("Las demás provincias ya son la zona ZBR: solo una zona puede tenerlas.");

        // Editar la propia zona no choca consigo misma
        when(repo.findById(1)).thenReturn(Optional.of(zona(1, "ZAR", false, "30", "03", "46")));
        assertThat(servicio.actualizar(1, nueva(IBERDROLA, "ZAR", false, "30", "02")).getProvincias()).containsExactly("02", "30");
        assertThatThrownBy(() -> servicio.actualizar(77, nueva(IBERDROLA, "ZDR", false, "04")))
                .hasMessage("Esa zona ya no existe.");
    }

    private static McasZonaComision zona(int id, String nombre, boolean resto, String... provincias) {
        McasZonaComision z = nueva(IBERDROLA, nombre, resto, provincias);
        z.setId(id);
        return z;
    }

    private static McasZonaComision nueva(Integer comercializadora, String nombre, boolean resto, String... provincias) {
        return McasZonaComision.builder().idComercializadora(comercializadora).zona(nombre).esResto(resto)
                .provincias(new ArrayList<>(List.of(provincias))).build();
    }
}
