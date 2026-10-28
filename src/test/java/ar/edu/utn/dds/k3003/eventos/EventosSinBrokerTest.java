package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.dtos.ProductoConSubcategoriaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * El módulo sin {@code RABBITMQ_URL}, como corre en local y en los tests: arranca, opera y nunca
 * abre una conexión al broker.
 *
 * <p>Usa la misma configuración que los demás tests de integración para reutilizar el contexto.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EventosSinBrokerTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private Fachada fachada;
  @Autowired private PublicadorEventos publicador;
  @Autowired private ConnectionFactory conexionesRabbit;

  @AfterEach
  void tearDown() {
    fachada.resetBaseDeDatos();
  }

  @Test
  @DisplayName("El contexto arranca sin RABBITMQ_URL y el publicador queda deshabilitado")
  void arrancaDeshabilitado() {
    assertFalse(publicador.estaHabilitado());
  }

  @Test
  @DisplayName("Las operaciones que publican eventos funcionan sin abrir ninguna conexión a RabbitMQ")
  void noIntentaConectar() {
    AtomicInteger conexionesAbiertas = new AtomicInteger();
    // Si ya hubiera una conexión abierta, Spring AMQP avisa en el momento de registrar el listener.
    conexionesRabbit.addConnectionListener(
        new ConnectionListener() {
          @Override
          public void onCreate(Connection conexion) {
            conexionesAbiertas.incrementAndGet();
          }
        });

    IdentificadorDTO identificador =
        fachada.agregarIdentificador(
            new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo"));
    ResponseEntity<ProductoConSubcategoriaDTO> alta =
        rest.postForEntity(
            "/productos",
            new ProductoConSubcategoriaDTO(
                null, "Arroz", "Arroz blanco largo fino", "alimentos", "cereales",
                identificador.id()),
            ProductoConSubcategoriaDTO.class);
    rest.delete("/donaciones/reset");
    ResponseEntity<ResultadoRepublicacion> republicacion =
        rest.postForEntity("/admin/republicar-eventos", null, ResultadoRepublicacion.class);

    assertEquals(HttpStatus.CREATED, alta.getStatusCode());
    assertEquals(HttpStatus.OK, republicacion.getStatusCode());
    assertEquals(0, conexionesAbiertas.get(), "no tenía que conectarse a RabbitMQ");
  }

  @Test
  @DisplayName("El Swagger publica POST /admin/republicar-eventos bajo el tag Administración")
  void swagger() throws Exception {
    JsonNode documento = new ObjectMapper().readTree(rest.getForObject("/api-docs", String.class));

    JsonNode operacion = documento.path("paths").path("/admin/republicar-eventos").path("post");
    assertEquals("[\"Administración\"]", operacion.path("tags").toString());
  }

  @Test
  @DisplayName("Republicar sin broker responde 200 con los eventos deshabilitados y nada publicado")
  void republicarSinBroker() {
    IdentificadorDTO identificador =
        fachada.agregarIdentificador(
            new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo"));
    fachada.registrarProducto(
        new ProductoConSubcategoriaDTO(
            null, "Arroz", "Arroz blanco largo fino", "alimentos", null, identificador.id()));

    ResponseEntity<ResultadoRepublicacion> respuesta =
        rest.postForEntity("/admin/republicar-eventos", null, ResultadoRepublicacion.class);

    assertEquals(HttpStatus.OK, respuesta.getStatusCode());
    assertEquals(new ResultadoRepublicacion(false, 1, 0, 0), respuesta.getBody());
  }
}
