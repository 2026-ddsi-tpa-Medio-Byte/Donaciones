package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.controllers.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Tests de las nuevas funcionalidades de la Entrega 4 para el módulo Donaciones. */
class Entrega4Test {

  private Fachada fachada;

  @BeforeEach
  void setUp() {
    fachada = new Fachada();
  }

  @Test
  @DisplayName("buscarProductoPorID inexistente lanza NoSuchElementException (lo que el handler mapea a 404)")
  void productoInexistenteLanzaNoSuchElement() {
    assertThrows(NoSuchElementException.class, () -> fachada.buscarProductoPorID("999"));
  }

  @Test
  @DisplayName("GlobalExceptionHandler mapea NoSuchElementException a HTTP 404")
  void handlerMapeaNotFoundA404() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    ResponseEntity<Map<String, String>> resp =
        handler.handleNotFound(new NoSuchElementException("Producto no encontrado: 999"));
    assertEquals(404, resp.getStatusCode().value());
  }

  @Test
  @DisplayName("GlobalExceptionHandler mapea RuntimeException a HTTP 400")
  void handlerMapeaRuntimeA400() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    ResponseEntity<Map<String, String>> resp =
        handler.handleRuntime(new RuntimeException("No puede donar"));
    assertEquals(400, resp.getStatusCode().value());
  }

  @Test
  @DisplayName("buscarTodosProductos devuelve todos los productos cargados")
  void listarProductos() {
    IdentificadorDTO id =
        fachada.agregarIdentificador(
            new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo de barras"));
    fachada.agregarProducto(
        new ProductoDTO(null, "Arroz", "Arroz blanco largo fino", "alimentos", id.id()));
    fachada.agregarProducto(
        new ProductoDTO(null, "Fideos", "Fideos secos tipo tirabuzon", "alimentos", id.id()));

    List<ProductoDTO> productos = fachada.buscarTodosProductos();
    assertEquals(2, productos.size());
  }

  @Test
  @DisplayName("buscarTodosIdentificadores devuelve todos los identificadores cargados")
  void listarIdentificadores() {
    fachada.agregarIdentificador(
        new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "uno dos tres"));
    fachada.agregarIdentificador(
        new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "cuatro cinco seis"));

    List<IdentificadorDTO> ids = fachada.buscarTodosIdentificadores();
    assertEquals(2, ids.size());
  }
}
