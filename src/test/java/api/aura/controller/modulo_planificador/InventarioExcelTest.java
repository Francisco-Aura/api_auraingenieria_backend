package api.aura.controller.modulo_planificador;

import api.aura.entity.modulo_planificador.pl_Equipo;
import api.aura.repository.modulo_planificador.pl_EquipoRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exportar el inventario a Excel: hojas, columnas y valores de lo que hay guardado. */
@SpringBootTest(classes = planificador.pruebas.PlanificadorPruebasApp.class)
@org.springframework.test.context.ActiveProfiles("planificador")
@AutoConfigureMockMvc
class InventarioExcelTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private pl_EquipoRepository equipos;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.execute("DELETE FROM pl_historial_precios");
        jdbc.execute("DELETE FROM pl_equipos");
    }

    private pl_Equipo equipo(String id, int posicion, String categoria, String partida, String marca, String modelo, double precio, Map<String, Object> atributos) {
        pl_Equipo e = new pl_Equipo();
        e.setId(id);
        e.setPosicion(posicion);
        e.setCategoria(categoria);
        e.setPartida(partida);
        e.setMarca(marca);
        e.setModelo(modelo);
        e.setProveedor("SUMEBA");
        e.setPrecioUnitario(precio);
        e.setAtributos(new LinkedHashMap<>(atributos));
        e.setOfertas(List.of(new LinkedHashMap<>(Map.of("supplier", "SUMEBA", "price", precio, "ref", "R-1", "note", "")),
                new LinkedHashMap<>(Map.of("supplier", "OTRO", "price", precio + 1, "ref", "", "note", "tarifa 2026"))));
        e.setPrecioRevisadoEl(LocalDate.of(2026, 11, 1));
        return e;
    }

    private Workbook pedir() throws Exception {
        var respuesta = mvc.perform(get("/api/planificador/equipment/export")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Inventario%20Aura")))
                .andReturn().getResponse();
        return new XSSFWorkbook(new ByteArrayInputStream(respuesta.getContentAsByteArray()));
    }

    private static String texto(Row fila, int c) {
        var celda = fila.getCell(c);
        return celda == null ? "" : celda.getCellType() == CellType.NUMERIC ? String.valueOf(celda.getNumericCellValue()) : celda.getStringCellValue();
    }

    @Test
    void elExcelTieneTodoUnaHojaPorCategoriaYLosPreciosPorProveedor() throws Exception {
        equipos.save(equipo("m1", 1, "modulos", "modulos", "JA SOLAR", "JAM72 550", 95.5, Map.of("potencia_wp", 550, "x:Mecánicas::Peso (kg)", "28,5")));
        equipos.save(equipo("i1", 2, "inversores", "electricidad", "HUAWEI", "SUN2000-20KTL", 1800, Map.of("potencia_kw", "20")));
        try (Workbook libro = pedir()) {
            assertEquals("Todo", libro.getSheetName(0));
            assertNotNull(libro.getSheet("Módulos"), "una hoja por categoría con productos");
            assertNotNull(libro.getSheet("Inversores"));
            assertNotNull(libro.getSheet("Proveedores y precios"));

            Sheet todo = libro.getSheet("Todo");
            assertEquals(3, todo.getPhysicalNumberOfRows(), "cabecera y dos productos");
            assertEquals("Categoría", texto(todo.getRow(0), 0));
            assertEquals("Módulos", texto(todo.getRow(1), 0));
            assertEquals("JA SOLAR", texto(todo.getRow(1), 2));
            assertEquals(95.5, todo.getRow(1).getCell(6).getNumericCellValue(), 0.0001, "el precio es un número");

            Sheet modulos = libro.getSheet("Módulos");
            Row cabecera = modulos.getRow(0);
            int pot = -1;
            int peso = -1;
            for (int c = 0; c < cabecera.getLastCellNum(); c++) {
                String t = texto(cabecera, c);
                if (t.equals("Potencia (Wp)")) pot = c;
                if (t.equals("Mecánicas · Peso (kg)")) peso = c;
            }
            assertTrue(pot >= 0, "columna de la característica propia de la categoría");
            assertEquals(550.0, modulos.getRow(1).getCell(pot).getNumericCellValue(), 0.0001);
            assertTrue(peso >= 0, "las características añadidas a mano también salen");
            assertEquals(28.5, modulos.getRow(1).getCell(peso).getNumericCellValue(), 0.0001, "«28,5» se guarda como número");

            Sheet ofertas = libro.getSheet("Proveedores y precios");
            assertEquals(5, ofertas.getPhysicalNumberOfRows(), "cabecera y dos proveedores por cada uno de los dos productos");
            assertEquals("SUMEBA", texto(ofertas.getRow(1), 3));
            assertEquals("Sí", texto(ofertas.getRow(1), 8), "marca el proveedor del presupuestador");
            assertEquals("tarifa 2026", texto(ofertas.getRow(2), 7));
        }
    }

    @Test
    void sinProductosSaleElExcelConLaCabecera() throws Exception {
        try (Workbook libro = pedir()) {
            assertEquals(1, libro.getSheet("Todo").getPhysicalNumberOfRows());
        }
    }
}
