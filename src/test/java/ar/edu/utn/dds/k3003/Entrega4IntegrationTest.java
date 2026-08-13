package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de integración de la Entrega 4: levanta el contexto Spring completo sobre H2 y ejercita los
 * endpoints reales por HTTP (controller → fachada → repos JPA → GlobalExceptionHandler).
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Entrega4IntegrationTest {

  @Autowired private TestRestTemplate rest;

  @Test
  @DisplayName("Flujo HTTP: alta de identificador + producto, GET por id, listados")
  void flujoProductosEIdentificadores() {
    // 1. Alta de identificador
    IdentificadorDTO idReq =
        new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo de barras valido");
    ResponseEntity<IdentificadorDTO> idResp =
        rest.postForEntity("/identificadores", idReq, IdentificadorDTO.class);
    assertEquals(HttpStatus.CREATED, idResp.getStatusCode());
    String identificadorID = idResp.getBody().id();
    assertNotNull(identificadorID);

    // 2. Alta de producto
    ProductoDTO prodReq =
        new ProductoDTO(null, "Arroz", "Arroz blanco largo fino", "alimentos", identificadorID);
    ResponseEntity<ProductoDTO> prodResp =
        rest.postForEntity("/productos", prodReq, ProductoDTO.class);
    assertEquals(HttpStatus.CREATED, prodResp.getStatusCode());
    String productoID = prodResp.getBody().id();
    assertNotNull(productoID);

    // 3. GET /productos/{id} de un producto válido → 200
    ResponseEntity<ProductoDTO> getProd =
        rest.getForEntity("/productos/" + productoID, ProductoDTO.class);
    assertEquals(HttpStatus.OK, getProd.getStatusCode());
    assertEquals("Arroz", getProd.getBody().nombre());

    // 4. GET /productos (listar todos) → 200 con al menos 1
    ResponseEntity<ProductoDTO[]> listProd = rest.getForEntity("/productos", ProductoDTO[].class);
    assertEquals(HttpStatus.OK, listProd.getStatusCode());
    assertTrue(listProd.getBody().length >= 1);

    // 5. GET /identificadores (listar todos) → 200 con al menos 1
    ResponseEntity<IdentificadorDTO[]> listId =
        rest.getForEntity("/identificadores", IdentificadorDTO[].class);
    assertEquals(HttpStatus.OK, listId.getStatusCode());
    assertTrue(listId.getBody().length >= 1);
  }

  @Test
  @DisplayName("Validación de producto: GET /productos/{id} inexistente → HTTP 404 (no 500)")
  void productoInexistenteDevuelve404() {
    ResponseEntity<String> resp = rest.getForEntity("/productos/999999", String.class);
    assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
  }

  @Test
  @DisplayName("Tras el reset, las secuencias de ID vuelven a empezar en 1")
  void resetReiniciaLasSecuencias() {
    // Alta inicial: consume IDs de la secuencia
    IdentificadorDTO id1 =
        rest.postForEntity(
                "/identificadores",
                new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "primer codigo"),
                IdentificadorDTO.class)
            .getBody();
    rest.postForEntity(
        "/productos",
        new ProductoDTO(null, "Lentejas", "Lentejas secas en paquete", "alimentos", id1.id()),
        ProductoDTO.class);

    // Reset de la base
    rest.delete("/donaciones/reset");

    // Alta posterior: debe volver a numerar desde 1
    ResponseEntity<IdentificadorDTO> idResp =
        rest.postForEntity(
            "/identificadores",
            new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo tras reset"),
            IdentificadorDTO.class);

    assertEquals(HttpStatus.CREATED, idResp.getStatusCode());
    assertEquals("1", idResp.getBody().id(), "la secuencia deberia reiniciarse tras el reset");

    ResponseEntity<ProductoDTO> prodResp =
        rest.postForEntity(
            "/productos",
            new ProductoDTO(
                null, "Garbanzos", "Garbanzos secos en paquete", "alimentos",
                idResp.getBody().id()),
            ProductoDTO.class);

    assertEquals("1", prodResp.getBody().id(), "la secuencia deberia reiniciarse tras el reset");
  }

  @Test
  @DisplayName("Métricas nuevas de Entrega 4 expuestas en Actuator")
  void metricasNuevasExpuestas() {
    ResponseEntity<String> metrics = rest.getForEntity("/actuator/metrics", String.class);
    assertEquals(HttpStatus.OK, metrics.getStatusCode());
    assertTrue(metrics.getBody().contains("productos.registrados"));
    assertTrue(metrics.getBody().contains("identificadores.registrados"));
    assertTrue(metrics.getBody().contains("donaciones.consultas"));
  }
}
