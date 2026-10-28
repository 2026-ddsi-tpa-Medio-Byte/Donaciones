package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.dds.k3003.MetricasService;
import ar.edu.utn.dds.k3003.logging.TrazabilidadFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

/** Cómo sale cada evento hacia RabbitMQ, y que un problema con el broker nunca se propague. */
class PublicadorEventosTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private AmqpTemplate rabbit;
  private SimpleMeterRegistry registry;
  private PublicadorEventos publicador;

  @BeforeEach
  void setUp() {
    rabbit = mock(AmqpTemplate.class);
    registry = new SimpleMeterRegistry();
    publicador = publicadorCon("amqps://usuario:clave@broker-de-prueba/vhost");
  }

  @AfterEach
  void limpiarMdc() {
    MDC.clear();
  }

  private PublicadorEventos publicadorCon(String direccion) {
    return new PublicadorEventos(rabbit, new MetricasService(registry), direccion);
  }

  private static Evento reseteo(Instant ocurridoEn) {
    return EventosDonaciones.moduloReseteado(ocurridoEn);
  }

  private Message unicoMensajeEnviado(String routingKey) {
    ArgumentCaptor<Message> mensaje = ArgumentCaptor.forClass(Message.class);
    verify(rabbit).send(eq(PublicadorEventos.EXCHANGE), eq(routingKey), mensaje.capture());
    return mensaje.getValue();
  }

  private double contador(String nombre, String tipo) {
    Counter contador = registry.find(nombre).tag("tipo", tipo).counter();
    return contador == null ? 0 : contador.count();
  }

  // ── El mensaje ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Publica en el exchange donatrack.eventos, como JSON persistente con el eventoId")
  void formaDelMensaje() {
    Evento evento = reseteo(FechasUtc.ahora());

    assertTrue(publicador.publicar(evento));

    Message mensaje = unicoMensajeEnviado("donaciones.modulo.reseteado");
    assertEquals("application/json", mensaje.getMessageProperties().getContentType());
    assertEquals("UTF-8", mensaje.getMessageProperties().getContentEncoding());
    assertEquals(
        MessageDeliveryMode.PERSISTENT, mensaje.getMessageProperties().getDeliveryMode());
    assertEquals(evento.eventoId(), mensaje.getMessageProperties().getMessageId());
  }

  @Test
  @DisplayName("El cuerpo es el sobre del contrato, en orden, con ocurridoEn en UTC con milisegundos")
  void cuerpoDelMensaje() throws Exception {
    publicador.publicar(reseteo(Instant.parse("2026-10-26T18:32:10.123Z")));

    JsonNode cuerpo =
        JSON.readTree(
            new String(
                unicoMensajeEnviado("donaciones.modulo.reseteado").getBody(),
                StandardCharsets.UTF_8));
    List<String> campos = new ArrayList<>();
    cuerpo.fieldNames().forEachRemaining(campos::add);
    assertEquals(
        List.of("eventoId", "tipo", "modulo", "entidadId", "ocurridoEn", "datos"), campos);
    assertEquals("MODULO_RESETEADO", cuerpo.get("tipo").asText());
    assertEquals("donaciones", cuerpo.get("modulo").asText());
    assertEquals("*", cuerpo.get("entidadId").asText());
    assertEquals("2026-10-26T18:32:10.123Z", cuerpo.get("ocurridoEn").asText());
    assertTrue(cuerpo.get("datos").isObject() && cuerpo.get("datos").isEmpty());
  }

  @Test
  @DisplayName("Una hora con cero milisegundos igual sale con los tres decimales")
  void milisegundosEnCero() throws Exception {
    JsonNode cuerpo =
        JSON.readTree(
            PublicadorEventos.serializar(reseteo(Instant.parse("2026-10-26T15:10:00Z"))));

    assertEquals("2026-10-26T15:10:00.000Z", cuerpo.get("ocurridoEn").asText());
  }

  @Test
  @DisplayName("El traceId de la operación viaja en el header X-Trace-Id del mensaje")
  void propagaElTraceId() {
    MDC.put("traceId", "abc123def456");

    publicador.publicar(reseteo(FechasUtc.ahora()));

    assertEquals(
        "abc123def456",
        unicoMensajeEnviado("donaciones.modulo.reseteado")
            .getMessageProperties()
            .getHeader(TrazabilidadFilter.HEADER_TRACE));
  }

  @Test
  @DisplayName("Sin traceId en el MDC el mensaje sale igual, sin el header")
  void sinTraceId() {
    publicador.publicar(reseteo(FechasUtc.ahora()));

    assertNull(
        unicoMensajeEnviado("donaciones.modulo.reseteado")
            .getMessageProperties()
            .getHeader(TrazabilidadFilter.HEADER_TRACE));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "PRODUCTO_CREADO, donaciones.producto.creado",
    "PRODUCTO_MODIFICADO, donaciones.producto.modificado",
    "DONACION_REGISTRADA, donaciones.donacion.registrada",
    "DONACION_ESTADO_CAMBIADO, donaciones.donacion.estado_cambiado",
    "DONACION_ELIMINADA, donaciones.donacion.eliminada",
    "MODULO_RESETEADO, donaciones.modulo.reseteado"
  })
  @DisplayName("Cada tipo sale con la routing key exacta del contrato")
  void routingKeys(TipoEvento tipo, String routingKey) {
    publicador.publicar(new Evento("id", tipo, "donaciones", "1", FechasUtc.ahora(), Map.of()));

    verify(rabbit).send(eq(PublicadorEventos.EXCHANGE), eq(routingKey), any(Message.class));
  }

  @Test
  @DisplayName("El catálogo tiene exactamente los seis tipos del contrato")
  void catalogoCompleto() {
    assertEquals(6, TipoEvento.values().length);
  }

  // ── Fallas ────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Si RabbitMQ falla no lanza: devuelve false y lo cuenta en eventos.fallidos")
  void rabbitCaido() {
    doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class));

    boolean salio = publicador.publicar(reseteo(FechasUtc.ahora()));

    assertFalse(salio);
    assertEquals(1.0, contador("eventos.fallidos", "MODULO_RESETEADO"));
    assertEquals(0.0, contador("eventos.publicados", "MODULO_RESETEADO"));
  }

  @Test
  @DisplayName("Un evento que sale bien se cuenta en eventos.publicados")
  void cuentaLosPublicados() {
    publicador.publicar(reseteo(FechasUtc.ahora()));

    assertEquals(1.0, contador("eventos.publicados", "MODULO_RESETEADO"));
    assertEquals(0.0, contador("eventos.fallidos", "MODULO_RESETEADO"));
  }

  // ── Sin RABBITMQ_URL ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("Sin RABBITMQ_URL")
  class Deshabilitado {

    @Test
    @DisplayName("Con la dirección vacía no toca RabbitMQ y publicar devuelve false")
    void direccionVacia() {
      PublicadorEventos apagado = publicadorCon("");

      assertFalse(apagado.estaHabilitado());
      assertFalse(apagado.publicar(reseteo(FechasUtc.ahora())));
      verifyNoInteractions(rabbit);
    }

    @Test
    @DisplayName("Sin la propiedad, tampoco")
    void sinPropiedad() {
      PublicadorEventos apagado = publicadorCon(null);

      assertFalse(apagado.publicar(reseteo(FechasUtc.ahora())));
      verifyNoInteractions(rabbit);
    }

    @Test
    @DisplayName("Republicar responde que los eventos están deshabilitados, sin intentar nada")
    void republicarDeshabilitado() {
      PublicadorEventos apagado = publicadorCon("  ");

      ResultadoRepublicacion resultado =
          apagado.republicar(List.of(reseteo(FechasUtc.ahora()), reseteo(FechasUtc.ahora())));

      assertEquals(new ResultadoRepublicacion(false, 2, 0, 0), resultado);
      verifyNoInteractions(rabbit);
    }
  }

  // ── Republicar ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Republicar cuenta los que salieron y los que fallaron")
  void republicarCuenta() {
    Evento sale = reseteo(FechasUtc.ahora());
    Evento falla =
        new Evento(
            "otro", TipoEvento.PRODUCTO_MODIFICADO, "donaciones", "1", FechasUtc.ahora(), Map.of());
    doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
        .when(rabbit)
        .send(anyString(), eq("donaciones.producto.modificado"), any(Message.class));

    ResultadoRepublicacion resultado = publicador.republicar(List.of(sale, falla, sale));

    assertEquals(new ResultadoRepublicacion(true, 3, 2, 1), resultado);
    verify(rabbit, times(3)).send(anyString(), anyString(), any(Message.class));
  }
}
