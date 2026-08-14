package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.controllers.DonacionController;
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
  @DisplayName("Queja enviada desde Swagger (con comillas JSON) se guarda sin las comillas")
  void quejaDesdeSwaggerSeLimpia() {
    Fachada fachadaMock = mock(Fachada.class);
    DonacionController controller = new DonacionController(fachadaMock);

    controller.registrarQueja("1", "\"Llego en mal estado\"");

    verify(fachadaMock).registrarQuejaEnDonacion("1", "Llego en mal estado");
  }

  @Test
  @DisplayName("Queja enviada como texto plano se mantiene intacta")
  void quejaTextoPlanoIntacta() {
    Fachada fachadaMock = mock(Fachada.class);
    DonacionController controller = new DonacionController(fachadaMock);

    controller.registrarQueja("1", "Llego en mal estado");

    verify(fachadaMock).registrarQuejaEnDonacion("1", "Llego en mal estado");
  }

  @Test
  @DisplayName("No se puede donar una cantidad de 0 o negativa")
  void cantidadInvalidaEnDonacion() {
    ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO cero =
        new ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO(
            null, "1", "DEP-1", "cantidad cero", "1", 0, null);
    ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO negativa =
        new ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO(
            null, "1", "DEP-1", "cantidad negativa", "1", -5, null);
    ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO nula =
        new ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO(
            null, "1", "DEP-1", "cantidad nula", "1", null, null);

    for (var dto : java.util.List.of(cero, negativa, nula)) {
      RuntimeException e =
          assertThrows(RuntimeException.class, () -> fachada.registrarDonacion(dto));
      assertEquals("La cantidad donada debe ser mayor a 0", e.getMessage());
    }
  }

  @Test
  @DisplayName("No se puede crear un producto con un nombre ya usado")
  void nombreDeProductoDuplicado() {
    IdentificadorDTO id =
        fachada.agregarIdentificador(
            new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo de barras"));
    fachada.agregarProducto(
        new ProductoDTO(null, "Arroz", "Arroz blanco largo fino", "alimentos", id.id()));

    RuntimeException e =
        assertThrows(
            RuntimeException.class,
            () ->
                fachada.agregarProducto(
                    new ProductoDTO(
                        null, "Arroz", "Otra descripcion cualquiera", "alimentos", id.id())));

    assertEquals("Ya existe un producto con el nombre: Arroz", e.getMessage());
  }

  @Test
  @DisplayName("La comparación de nombres de producto ignora mayúsculas")
  void nombreDeProductoDuplicadoIgnoraMayusculas() {
    IdentificadorDTO id =
        fachada.agregarIdentificador(
            new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo de barras"));
    fachada.agregarProducto(
        new ProductoDTO(null, "Arroz", "Arroz blanco largo fino", "alimentos", id.id()));

    assertThrows(
        RuntimeException.class,
        () ->
            fachada.agregarProducto(
                new ProductoDTO(null, "ARROZ", "Arroz de otra marca", "alimentos", id.id())));
  }

  @Test
  @DisplayName("No se puede donar contra un producto que no existe")
  void donacionConProductoInexistente() {
    ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO dto =
        new ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO(
            null, "1", "DEP-1", "producto fantasma", "999999", 5, null);

    assertThrows(NoSuchElementException.class, () -> fachada.registrarDonacion(dto));
  }

  @Test
  @DisplayName("Buscar una donación inexistente da 404, no 400")
  void donacionInexistenteEs404() {
    // Antes lanzaba RuntimeException genérica y el handler devolvía 400: una donación que no
    // existe tiene que responder 404 para que los otros módulos distingan "no está" de "pediste mal".
    assertThrows(NoSuchElementException.class, () -> fachada.buscarDonacionPorID("999999"));
    assertThrows(
        NoSuchElementException.class,
        () ->
            fachada.cambiarEstadoDeDonacion(
                "999999", ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum.ACEPTADA));
    assertThrows(
        NoSuchElementException.class, () -> fachada.registrarQuejaEnDonacion("999999", "queja"));
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
