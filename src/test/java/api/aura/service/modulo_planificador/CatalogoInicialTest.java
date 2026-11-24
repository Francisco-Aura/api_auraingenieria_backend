package api.aura.service.modulo_planificador;

import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import api.aura.repository.modulo_planificador.pl_MarcaRepository;
import api.aura.repository.modulo_planificador.pl_MetaRepository;
import api.aura.repository.modulo_planificador.pl_ProveedorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/** El catálogo con el que arranca una instalación nueva: productos, marcas con logo y proveedores, una sola vez. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class, properties = {
        "planificador.catalogo-inicial=true", "spring.datasource.url=jdbc:h2:mem:catalogo;MODE=MariaDB;DB_CLOSE_DELAY=-1"})
@org.springframework.test.context.ActiveProfiles("planificador")
class CatalogoInicialTest {

    @Autowired
    private CatalogoInicial catalogo;
    @Autowired
    private pl_EquipoRepository equipos;
    @Autowired
    private pl_MarcaRepository marcas;
    @Autowired
    private pl_ProveedorRepository proveedores;
    @Autowired
    private pl_MetaRepository meta;

    @Test
    void unaInstalacionNuevaArrancaConElCatalogoDeEjemplo() throws Exception {
        assertEquals(150, equipos.count(), "productos del catálogo");
        var aiko = equipos.findById("mod-aiko-615").orElseThrow();
        assertEquals("AIKO 615", aiko.getModelo());
        assertEquals("módulo", aiko.getUnidad());
        assertEquals(76.15, aiko.getPrecioUnitario(), 1e-9);
        assertEquals(615, ((Number) aiko.getAtributos().get("potencia_wp")).intValue());
        assertTrue(aiko.isPorModulo());
        assertTrue(marcas.count() >= 50, "marcas de la lista");
        long conLogo = marcas.findAll().stream().filter(m -> m.getLogo() != null && m.getLogo().startsWith("data:image/")).count();
        assertTrue(conLogo >= 45, "marcas con logo: " + conLogo);
        assertTrue(proveedores.findById("SONEPAR").isPresent(), "proveedores con los que se trabaja");
        assertTrue(equipos.findAll().stream().noneMatch(e -> e.getProveedor().equals("SUMEBA")), "los que no están en la lista se quedan sin proveedor");
        for (String clave : new String[]{"equipment_seed_v1", "brands_seed_v1", "suppliers_seed_v1"}) {
            assertTrue(meta.existsById(clave), clave);
        }
    }

    @Test
    void repetirElArranqueNoDuplicaNiPisaNada() throws Exception {
        long productos = equipos.count();
        long listaMarcas = marcas.count();
        long listaProveedores = proveedores.count();
        var huawei = marcas.findById("HUAWEI").orElseThrow();
        huawei.setLogo("data:image/png;base64,SUBIDO");
        marcas.save(huawei);
        catalogo.run(new DefaultApplicationArguments());
        assertEquals(productos, equipos.count());
        assertEquals(listaMarcas, marcas.count());
        assertEquals(listaProveedores, proveedores.count());
        assertEquals("data:image/png;base64,SUBIDO", marcas.findById("HUAWEI").orElseThrow().getLogo(), "un logo subido a mano no se pisa");
    }

    @Test
    void laRevisionDePreciosSeReiniciaUnaSolaVezYNingunPrecioFiguraComoRevisado() throws Exception {
        assertTrue(equipos.findAll().stream().allMatch(e -> e.getPrecioRevisadoEl() == null), "tras el arranque ninguno figura como revisado");
        var e = equipos.findById("mod-aiko-615").orElseThrow();
        e.setPrecioRevisadoEl(java.time.LocalDate.of(2026, 11, 9));
        equipos.save(e);
        catalogo.run(new DefaultApplicationArguments());
        assertNotNull(equipos.findById("mod-aiko-615").orElseThrow().getPrecioRevisadoEl(), "ya hecho: no se vuelve a reiniciar");
        meta.deleteById(CatalogoInicial.CLAVE_REVISIONES);
        meta.deleteById(RevisionPreciosService.META_AVISADO);
        catalogo.run(new DefaultApplicationArguments());
        assertNull(equipos.findById("mod-aiko-615").orElseThrow().getPrecioRevisadoEl());
        assertTrue(meta.existsById(CatalogoInicial.CLAVE_REVISIONES));
    }
}
