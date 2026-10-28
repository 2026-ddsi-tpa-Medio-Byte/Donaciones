package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.dtos.ProductoConSubcategoriaDTO;
import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacionEn;
import ar.edu.utn.dds.k3003.repositories.DonacionJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Los eventos en el contexto real de Spring, con el publicador habilitado y el {@link
 * RabbitTemplate} simulado: el camino completo controller → fachada → evento de aplicación →
 * listener después del commit → RabbitMQ, sin broker.
 *
 * <p>La dirección del broker es inventada y nunca se usa, porque el {@code RabbitTemplate} es un
 * mock. Este contexto es distinto del de los otros tests de integración (cambia una propiedad), así
 * que Spring levanta uno aparte.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.rabbitmq.addresses=amqp://invitado:invitado@broker-de-prueba:5672")
@ActiveProfiles("test")
class EventosConBrokerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @MockitoBean private RabbitTemplate rabbit;

  @Autowired private TestRestTemplate rest;
  @Autowired private RestTemplate restTemplate;
  @Autowired private Fachada fachada;
  @Autowired private PublicadorEventos publicador;
  @Autowired private ApplicationEventPublisher eventosDeSpring;
  @Autowired private PlatformTransactionManager transacciones;
  @Autowired private DonacionJpaRepository donaciones;
  @Autowired private MeterRegistry registry;

  @Value("${APP.URL_DONADORES}")
  private String urlDonadores;

  @Value("${APP.URL_LOGISTICA}")
  private String urlLogistica;

  private MockRestServiceServer otrosModulos;
  private String identificadorID;
  private String productoID;

  @BeforeEach
  void setUp() {
    fachada.resetBaseDeDatos();
    otrosModulos = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    identificadorID =
        fachada
            .agregarIdentificador(
                new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo"))
            .id();
    productoID =
        fachada
            .registrarProducto(
                new ProductoConSubcategoriaDTO(
                    null, "Yerba", "Yerba mate con palo", "alimentos", "infusiones",
                    identificadorID))
            .id();
    clearInvocations(rabbit);
  }

  @AfterEach
  void tearDown() {
    fachada.resetBaseDeDatos();
    restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory());
  }

  // ── Ayudas ─────────────────────────────────────────────────────────────────

  private List<Message> mensajesEnviados() {
    ArgumentCaptor<Message> mensajes = ArgumentCaptor.forClass(Message.class);
    verify(rabbit, atLeastOnce())
        .send(eq(PublicadorEventos.EXCHANGE), anyString(), mensajes.capture());
    return mensajes.getAllValues();
  }

  private List<String> routingKeysEnviadas() {
    ArgumentCaptor<String> claves = ArgumentCaptor.forClass(String.class);
    verify(rabbit, atLeastOnce())
        .send(eq(PublicadorEventos.EXCHANGE), claves.capture(), any(Message.class));
    return claves.getAllValues();
  }

  private static JsonNode cuerpo(Message mensaje) throws Exception {
    return JSON.readTree(mensaje.getBody());
  }

  private void nadaEnviado() {
    verify(rabbit, never()).send(anyString(), anyString(), any(Message.class));
  }

  private double fallidos(String tipo) {
    Counter contador = registry.find("eventos.fallidos").tag("tipo", tipo).counter();
    return contador == null ? 0 : contador.count();
  }

  private void donadoresAceptan(String donador) {
    otrosModulos
        .expect(requestTo(urlDonadores + "/donadores/" + donador))
        .andRespond(
            withSuccess("{\"id\":\"" + donador + "\",\"nombre\":\"Ana\"}", MediaType.APPLICATION_JSON));
    otrosModulos
        .expect(requestTo(urlDonadores + "/donadores/" + donador + "/puede-donar"))
        .andRespond(withSuccess("{\"puedeDonar\":true}", MediaType.APPLICATION_JSON));
  }

  private ResponseEntity<DonacionDTO> donar(String donador) {
    Map<String, Object> body = new HashMap<>();
    body.put("donadorID", donador);
    body.put("depositoID", "DEP-1");
    body.put("descripcion", "Yerba para el comedor");
    body.put("productoID", productoID);
    body.put("cantidad", 5);
    return rest.postForEntity("/donaciones", body, DonacionDTO.class);
  }

  // ── Después del commit ────────────────────────────────────────────────────

  @Test
  @DisplayName("Sin transacción abierta el evento sale en el momento: el save del repositorio ya confirmó")
  void sinTransaccionSaleEnElMomento() throws Exception {
    ResponseEntity<ProductoConSubcategoriaDTO> alta =
        rest.postForEntity(
            "/productos",
            new ProductoConSubcategoriaDTO(
                null, "Lentejas", "Lentejas secas en paquete", "alimentos", "legumbres",
                identificadorID),
            ProductoConSubcategoriaDTO.class);

    assertEquals(HttpStatus.CREATED, alta.getStatusCode());
    assertEquals(List.of("donaciones.producto.creado"), routingKeysEnviadas());
    JsonNode evento = cuerpo(mensajesEnviados().get(0));
    assertEquals("PRODUCTO_CREADO", evento.get("tipo").asText());
    assertEquals(alta.getBody().id(), evento.get("entidadId").asText());
    assertEquals("legumbres", evento.get("datos").get("subcategoria").asText());
  }

  @Test
  @DisplayName("Dentro de una transacción el evento espera al commit")
  void esperaAlCommit() {
    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              eventosDeSpring.publishEvent(EventosDonaciones.moduloReseteado(FechasUtc.ahora()));
              nadaEnviado();
            });

    assertEquals(List.of("donaciones.modulo.reseteado"), routingKeysEnviadas());
  }

  @Test
  @DisplayName("Si la transacción se revierte, el evento no sale nunca")
  void rollbackNoPublica() {
    new TransactionTemplate(transacciones)
        .executeWithoutResult(
            estado -> {
              eventosDeSpring.publishEvent(EventosDonaciones.moduloReseteado(FechasUtc.ahora()));
              estado.setRollbackOnly();
            });

    nadaEnviado();
  }

  @Test
  @DisplayName("El reset, que es transaccional, publica MODULO_RESETEADO al confirmar")
  void reset() throws Exception {
    rest.delete("/donaciones/reset");

    assertEquals(List.of("donaciones.modulo.reseteado"), routingKeysEnviadas());
    JsonNode evento = cuerpo(mensajesEnviados().get(0));
    assertEquals("*", evento.get("entidadId").asText());
  }

  @Test
  @DisplayName("La semilla publica el reset y el producto, en ese orden, al confirmar")
  void semilla() throws Exception {
    ResponseEntity<String> respuesta = rest.postForEntity("/seed", null, String.class);

    assertEquals(HttpStatus.OK, respuesta.getStatusCode(), respuesta.getBody());
    assertEquals(
        List.of("donaciones.modulo.reseteado", "donaciones.producto.creado"),
        routingKeysEnviadas());
    assertEquals(
        "cereales", cuerpo(mensajesEnviados().get(1)).get("datos").get("subcategoria").asText());
  }

  // ── Donaciones ─────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Una donación publica DONACION_REGISTRADA y su entrega DONACION_ESTADO_CAMBIADO con el historial")
  void altaYEntrega() throws Exception {
    donadoresAceptan("ana");
    otrosModulos
        .expect(requestTo(urlLogistica + "/asignaciones"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    String id = donar("ana").getBody().id();
    HttpHeaders json = new HttpHeaders();
    json.setContentType(MediaType.APPLICATION_JSON);
    rest.exchange(
        "/donaciones/" + id + "/estado",
        HttpMethod.PATCH,
        new HttpEntity<>("\"ACEPTADA\"", json),
        DonacionDTO.class);

    assertEquals(
        List.of("donaciones.donacion.registrada", "donaciones.donacion.estado_cambiado"),
        routingKeysEnviadas());
    List<Message> mensajes = mensajesEnviados();
    JsonNode registrada = cuerpo(mensajes.get(0)).get("datos");
    assertEquals("INGRESADA", registrada.get("estado").asText());
    assertEquals("ana", registrada.get("donadorID").asText());
    JsonNode entregada = cuerpo(mensajes.get(1));
    assertEquals(id, entregada.get("entidadId").asText());
    assertEquals("ACEPTADA", entregada.get("datos").get("estado").asText());
    assertEquals(2, entregada.get("datos").get("historialEstados").size());
    assertTrue(
        cuerpo(mensajes.get(1)).get("ocurridoEn").asText().compareTo(
                cuerpo(mensajes.get(0)).get("ocurridoEn").asText())
            >= 0,
        "la entrega no puede quedar como más vieja que el alta");
  }

  @Test
  @DisplayName("Si Logística responde 500, la donación no queda publicada: solo sale DONACION_ELIMINADA")
  void compensacion() throws Exception {
    donadoresAceptan("ana");
    otrosModulos.expect(requestTo(urlLogistica + "/asignaciones")).andRespond(withServerError());

    ResponseEntity<Map> respuesta =
        rest.postForEntity(
            "/donaciones",
            Map.of(
                "donadorID", "ana",
                "depositoID", "DEP-1",
                "descripcion", "Yerba",
                "productoID", productoID,
                "cantidad", 5),
            Map.class);

    assertEquals(HttpStatus.BAD_GATEWAY, respuesta.getStatusCode());
    assertEquals(0, donaciones.count());
    assertEquals(List.of("donaciones.donacion.eliminada"), routingKeysEnviadas());
    assertEquals("1", cuerpo(mensajesEnviados().get(0)).get("datos").get("id").asText());
  }

  // ── Publicar nunca rompe la operación ─────────────────────────────────────

  @Test
  @DisplayName("Si RabbitMQ falla, el alta responde 201 igual, el producto queda y se cuenta el fallo")
  void rabbitCaido() {
    doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class));
    double fallidosAntes = fallidos("PRODUCTO_CREADO");

    ResponseEntity<ProductoConSubcategoriaDTO> alta =
        rest.postForEntity(
            "/productos",
            new ProductoConSubcategoriaDTO(
                null, "Lentejas", "Lentejas secas en paquete", "alimentos", null, identificadorID),
            ProductoConSubcategoriaDTO.class);

    assertEquals(HttpStatus.CREATED, alta.getStatusCode());
    assertEquals(
        HttpStatus.OK,
        rest.getForEntity("/productos/" + alta.getBody().id(), String.class).getStatusCode());
    assertEquals(fallidosAntes + 1, fallidos("PRODUCTO_CREADO"));
  }

  // ── Republicar ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /admin/republicar-eventos publica cada producto y cada donación y responde cuántos")
  void republicar() throws Exception {
    Donacion entregada = new Donacion("ana", 5, "DEP-1", productoID, "Yerba");
    entregada.setHistorialEstados(EstadoDonacionEn.ACEPTADA);
    String donacionID = donaciones.save(entregada).getId().toString();

    ResponseEntity<ResultadoRepublicacion> respuesta =
        rest.postForEntity("/admin/republicar-eventos", null, ResultadoRepublicacion.class);

    assertEquals(HttpStatus.OK, respuesta.getStatusCode());
    assertEquals(new ResultadoRepublicacion(true, 2, 2, 0), respuesta.getBody());
    assertEquals(
        List.of("donaciones.producto.modificado", "donaciones.donacion.estado_cambiado"),
        routingKeysEnviadas());
    JsonNode donacion = cuerpo(mensajesEnviados().get(1));
    assertEquals(donacionID, donacion.get("entidadId").asText());
    assertEquals("ACEPTADA", donacion.get("datos").get("estado").asText());
    assertEquals(2, donacion.get("datos").get("historialEstados").size());
  }

  @Test
  @DisplayName("El estado actual se arma también fuera de un pedido HTTP, con el historial completo")
  void estadoActualFueraDeUnPedido() throws Exception {
    Donacion entregada = new Donacion("ana", 5, "DEP-1", productoID, "Yerba");
    entregada.setHistorialEstados(EstadoDonacionEn.ACEPTADA);
    donaciones.save(entregada);

    // Sin pedido HTTP no hay sesión abierta: el historial, que es perezoso, se tiene que leer
    // dentro de la transacción de la fachada.
    List<Evento> eventos = fachada.estadoActualComoEventos();

    JsonNode donacion = JSON.readTree(PublicadorEventos.serializar(eventos.get(1)));
    assertEquals(2, donacion.get("datos").get("historialEstados").size());
    assertTrue(publicador.estaHabilitado());
  }
}
