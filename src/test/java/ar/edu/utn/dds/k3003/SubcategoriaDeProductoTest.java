package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.dtos.ProductoConSubcategoriaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La subcategoría vista desde la fachada: la interfaz de cátedra sigue igual, sin subcategoría, y
 * convive con los productos que sí la tienen.
 */
class SubcategoriaDeProductoTest {

  private Fachada fachada;
  private String identificadorID;

  @BeforeEach
  void setUp() {
    fachada = new Fachada();
    identificadorID =
        fachada
            .agregarIdentificador(
                new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo"))
            .id();
  }

  @Test
  @DisplayName("agregarProducto sigue devolviendo el ProductoDTO de siempre, sin subcategoría")
  void agregarProducto() {
    ProductoDTO creado =
        fachada.agregarProducto(
            new ProductoDTO(
                null, "Arroz", "Arroz blanco largo fino", "alimentos", identificadorID));

    assertEquals(
        new ProductoDTO(
            creado.id(), "Arroz", "Arroz blanco largo fino", "alimentos", identificadorID),
        creado);
    assertEquals(creado, fachada.buscarProductoPorID(creado.id()));
    assertNull(fachada.buscarProducto(creado.id()).subcategoria());
  }

  @Test
  @DisplayName("Un producto con subcategoría se sigue leyendo por la interfaz de cátedra")
  void conSubcategoriaPorCatedra() {
    String id =
        fachada
            .registrarProducto(
                new ProductoConSubcategoriaDTO(
                    null, "Fideos", "Fideos secos tipo tirabuzon", "alimentos", "pastas",
                    identificadorID))
            .id();

    assertEquals("Fideos", fachada.buscarProductoPorID(id).nombre());
    assertEquals("pastas", fachada.buscarProducto(id).subcategoria());
  }

  @Test
  @DisplayName("La semilla en memoria también carga la subcategoría")
  void semillaEnMemoria() {
    fachada.seedBaseDeDatos();

    assertEquals("cereales", fachada.buscarTodosProductos().get(0).subcategoria());
  }
}
