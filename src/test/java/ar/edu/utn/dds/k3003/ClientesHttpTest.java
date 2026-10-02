package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.ModuloNoDisponibleException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/** Cómo traducen los clientes HTTP los errores de Donadores y Logística. */
class ClientesHttpTest {

  private static final String DONADORES = "http://donadores";
  private static final String LOGISTICA = "http://logistica";

  private MockRestServiceServer servidor;
  private DonadoresYEntidadesClient clienteDonadores;
  private LogisticaClient clienteLogistica;

  @BeforeEach
  void setUp() {
    RestTemplate rest = new RestTemplate();
    servidor = MockRestServiceServer.bindTo(rest).build();
    clienteDonadores = new DonadoresYEntidadesClient(rest, DONADORES);
    clienteLogistica = new LogisticaClient(rest, LOGISTICA);
  }

  @Test
  @DisplayName("Un 404 de Donadores se informa como donador inexistente, con un mensaje legible")
  void donadorInexistente() {
    servidor
        .expect(requestTo(DONADORES + "/donadores/7"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withResourceNotFound());

    DonadorNoEncontradoException e =
        assertThrows(
            DonadorNoEncontradoException.class, () -> clienteDonadores.buscarDonadorPorID("7"));

    assertEquals("No existe el donador 7", e.getMessage());
  }

  @Test
  @DisplayName("Un 404 al consultar si puede donar también es un donador inexistente")
  void puedeDonarDeDonadorInexistente() {
    servidor
        .expect(requestTo(DONADORES + "/donadores/7/puede-donar"))
        .andRespond(withResourceNotFound());

    assertThrows(DonadorNoEncontradoException.class, () -> clienteDonadores.puedeDonar("7"));
  }

  @Test
  @DisplayName("Si Donadores no responde, el error nombra al módulo caído")
  void donadoresSinRespuesta() {
    servidor
        .expect(requestTo(DONADORES + "/donadores/7"))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    ModuloNoDisponibleException e =
        assertThrows(
            ModuloNoDisponibleException.class, () -> clienteDonadores.buscarDonadorPorID("7"));

    assertEquals("Donadores y Entidades", e.getModulo());
    assertTrue(e.getMessage().contains("Donadores y Entidades"), e.getMessage());
    assertTrue(e.getMessage().contains("Read timed out"), e.getMessage());
  }

  @Test
  @DisplayName("Un 5xx de Donadores al registrar la queja es un módulo no disponible")
  void quejaConDonadoresCaido() {
    servidor
        .expect(requestTo(DONADORES + "/donadores/7/quejas"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServiceUnavailable());

    ModuloNoDisponibleException e =
        assertThrows(
            ModuloNoDisponibleException.class,
            () -> clienteDonadores.agregarQueja(new QuejaDTO(null, "1", "7", null, "roto")));

    assertTrue(e.getMessage().contains("503"), e.getMessage());
  }

  @Test
  @DisplayName("Un 500 de Logística al avisar la donación nombra al módulo y el código")
  void logisticaConError() {
    servidor
        .expect(requestTo(LOGISTICA + "/asignaciones"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError().body("NullPointerException: deposito"));

    ModuloNoDisponibleException e =
        assertThrows(
            ModuloNoDisponibleException.class,
            () -> clienteLogistica.gestionarDonacion("DEP-X", "1", "1", 5));

    assertEquals("Logística", e.getModulo());
    assertTrue(e.getMessage().contains("Logística"), e.getMessage());
    assertTrue(e.getMessage().contains("500"), e.getMessage());
    assertTrue(e.getMessage().contains("NullPointerException"), e.getMessage());
  }

  @Test
  @DisplayName("Un rechazo 4xx de Logística no es una caída: se deja pasar tal cual")
  void rechazoDeLogisticaNoEsCaida() {
    servidor.expect(requestTo(LOGISTICA + "/asignaciones")).andRespond(withBadRequest());

    assertThrows(
        HttpClientErrorException.BadRequest.class,
        () -> clienteLogistica.gestionarDonacion("DEP-1", "1", "1", 5));
  }
}
