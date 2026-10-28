package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.dtos.ProductoConSubcategoriaDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * La subcategoría del producto por HTTP, con la base H2: se guarda y se devuelve, y es opcional,
 * así que los clientes que ya usaban la API (que mandan el {@code ProductoDTO} de cátedra) siguen
 * funcionando igual.
 *
 * <p>Usa la misma configuración que los demás tests de integración para reutilizar el contexto.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubcategoriaDeProductoHttpTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private Fachada fachada;

  private String identificadorID;

  @BeforeEach
  void setUp() {
    fachada.resetBaseDeDatos();
    identificadorID =
        fachada
            .agregarIdentificador(
                new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo"))
            .id();
  }

  @AfterEach
  void tearDown() {
    fachada.resetBaseDeDatos();
  }

  private Map<String, Object> producto(String nombre, String subcategoria) {
    Map<String, Object> body = new HashMap<>();
    body.put("nombre", nombre);
    body.put("descripcion", nombre + " en paquete de un kilo");
    body.put("categoriaID", "alimentos");
    if (subcategoria != null) {
      body.put("subcategoria", subcategoria);
    }
    body.put("identificadorID", identificadorID);
    return body;
  }

  @Test
  @DisplayName("Se guarda y la devuelven el alta, GET /productos/{id} y GET /productos")
  void seGuardaYSeDevuelve() {
    ResponseEntity<ProductoConSubcategoriaDTO> alta =
        rest.postForEntity(
            "/productos", producto("Lentejas", "legumbres"), ProductoConSubcategoriaDTO.class);

    assertEquals(HttpStatus.CREATED, alta.getStatusCode());
    assertEquals("legumbres", alta.getBody().subcategoria());
    String id = alta.getBody().id();
    assertEquals(
        "legumbres",
        rest.getForObject("/productos/" + id, ProductoConSubcategoriaDTO.class).subcategoria());
    ProductoConSubcategoriaDTO[] todos =
        rest.getForObject("/productos", ProductoConSubcategoriaDTO[].class);
    assertEquals(List.of("legumbres"), List.of(todos[0].subcategoria()));
    assertEquals("alimentos", todos[0].categoriaID());
  }

  @Test
  @DisplayName("Un alta sin subcategoría, como la de los clientes actuales, sigue funcionando")
  void sinSubcategoria() {
    ResponseEntity<ProductoDTO> alta =
        rest.postForEntity(
            "/productos",
            new ProductoDTO(
                null, "Garbanzos", "Garbanzos secos en paquete", "alimentos", identificadorID),
            ProductoDTO.class);

    assertEquals(HttpStatus.CREATED, alta.getStatusCode());
    assertEquals("Garbanzos", alta.getBody().nombre());
    assertNull(
        rest.getForObject("/productos/" + alta.getBody().id(), ProductoConSubcategoriaDTO.class)
            .subcategoria());
  }

  @Test
  @DisplayName("Una subcategoría en blanco se guarda como «sin subcategoría»")
  void enBlanco() {
    ResponseEntity<ProductoConSubcategoriaDTO> alta =
        rest.postForEntity(
            "/productos", producto("Porotos", "   "), ProductoConSubcategoriaDTO.class);

    assertEquals(HttpStatus.CREATED, alta.getStatusCode());
    assertNull(alta.getBody().subcategoria());
  }

  @Test
  @DisplayName("La respuesta suma el campo subcategoria al JSON de siempre")
  void jsonDeLaRespuesta() {
    String id =
        rest.postForEntity(
                "/productos", producto("Lentejas", "legumbres"), ProductoConSubcategoriaDTO.class)
            .getBody()
            .id();

    Map<?, ?> json = rest.getForObject("/productos/" + id, Map.class);

    assertEquals(
        List.of("id", "nombre", "descripcion", "categoriaID", "subcategoria", "identificadorID"),
        List.copyOf(json.keySet()));
  }

  @Test
  @DisplayName("La semilla carga el producto de prueba con categoría y subcategoría")
  void semilla() {
    ResponseEntity<String> respuesta = rest.postForEntity("/seed", null, String.class);

    assertEquals(HttpStatus.OK, respuesta.getStatusCode(), respuesta.getBody());
    ProductoConSubcategoriaDTO[] productos =
        rest.getForObject("/productos", ProductoConSubcategoriaDTO[].class);
    assertEquals(1, productos.length);
    assertEquals("alimentos", productos[0].categoriaID());
    assertEquals("cereales", productos[0].subcategoria());
  }
}
