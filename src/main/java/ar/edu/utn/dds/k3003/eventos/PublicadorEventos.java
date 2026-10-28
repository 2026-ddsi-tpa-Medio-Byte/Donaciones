package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.MetricasService;
import ar.edu.utn.dds.k3003.logging.TrazabilidadFilter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica los eventos de Donaciones en el exchange {@code donatrack.eventos} de RabbitMQ, del que
 * los consume el Servicio de Consultas para armar el modelo de lectura en MongoDB.
 *
 * <p>Tres reglas, las mismas del appender de logs:
 *
 * <ul>
 *   <li><b>Después del commit.</b> La {@code Fachada} entrega cada evento a Spring y este listener
 *       lo recibe recién cuando la transacción confirmó: nunca sale un cambio que no se guardó. Casi
 *       todas las operaciones de la fachada no abren una transacción propia, porque cada {@code
 *       save} del repositorio confirma la suya; para esas, {@code fallbackExecution} publica en el
 *       momento, que ya es después del commit.
 *   <li><b>Nunca hace fallar la operación.</b> Si RabbitMQ no responde, se loguea en WARN, se cuenta
 *       en {@code eventos.fallidos} y la operación sigue. Lo que se pierde se recupera con {@code
 *       POST /admin/republicar-eventos}.
 *   <li><b>Sin {@code RABBITMQ_URL} queda apagado.</b> En local y en los tests no hay broker: se
 *       avisa una vez al arrancar y no se intenta conectar nunca. La conexión de Spring AMQP es
 *       perezosa, así que si este publicador no la usa, no se abre.
 * </ul>
 *
 * <p>Solo publica: no declara colas ni el exchange. El exchange lo creó Logística (topic, durable)
 * y la cola la crea Consultas; redeclararlo con otros argumentos haría que el broker cierre el
 * canal.
 */
@Component
public class PublicadorEventos {

  /** Compartido por los cuatro módulos y aparte de la cola de trabajo de Logística. */
  public static final String EXCHANGE = "donatrack.eventos";

  private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

  /**
   * Un mapper propio y no el de Spring: el formato del evento es un contrato con Consultas y no
   * puede cambiar porque alguien ajuste cómo responde la API REST. Las fechas salen siempre como
   * {@code 2026-10-26T18:32:10.123Z}.
   */
  private static final ObjectMapper JSON =
      JsonMapper.builder()
          .addModule(
              new SimpleModule("fechas-utc").addSerializer(Instant.class, new InstanteEnUtc()))
          .build();

  private final AmqpTemplate rabbit;
  private final MetricasService metricas;
  private final boolean habilitado;

  public PublicadorEventos(
      AmqpTemplate rabbit,
      MetricasService metricas,
      @Value("${spring.rabbitmq.addresses:}") String direccionesRabbit) {
    this.rabbit = rabbit;
    this.metricas = metricas;
    this.habilitado = direccionesRabbit != null && !direccionesRabbit.isBlank();
    if (habilitado) {
      // La URL no se loguea: trae usuario y contraseña.
      log.info("[Eventos] Eventos habilitados: se publican en el exchange {}", EXCHANGE);
    } else {
      log.warn(
          "[Eventos] RABBITMQ_URL no está configurada: eventos deshabilitados. Es lo esperado en"
              + " local y en los tests; en Render, Consultas no se va a enterar de ningún cambio.");
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void alConfirmarse(Evento evento) {
    publicar(evento);
  }

  /**
   * Envía un evento. Nunca lanza.
   *
   * @return si llegó a RabbitMQ; false también cuando los eventos están deshabilitados
   */
  public boolean publicar(Evento evento) {
    if (!habilitado) {
      return false;
    }
    try {
      rabbit.send(EXCHANGE, evento.tipo().routingKey(), mensaje(evento));
      metricas.registrarEventoPublicado(evento.tipo().name());
      return true;
    } catch (JsonProcessingException | RuntimeException e) {
      metricas.registrarEventoFallido(evento.tipo().name());
      log.warn(
          "[Eventos] No se pudo publicar {} de la entidad {} ({}). La operación siguió igual;"
              + " para resincronizar Consultas: POST /admin/republicar-eventos",
          evento.tipo(),
          evento.entidadId(),
          e.getMessage());
      return false;
    }
  }

  /**
   * Publica una tanda (la de {@code POST /admin/republicar-eventos}) y cuenta cómo salió. No pasa
   * por el listener: republicar no modifica nada, así que no hay commit que esperar.
   */
  public ResultadoRepublicacion republicar(List<Evento> eventos) {
    if (!habilitado) {
      return new ResultadoRepublicacion(false, eventos.size(), 0, 0);
    }
    int publicados = 0;
    for (Evento evento : eventos) {
      if (publicar(evento)) {
        publicados++;
      }
    }
    log.info("[Eventos] Republicados {} de {} eventos", publicados, eventos.size());
    return new ResultadoRepublicacion(
        true, eventos.size(), publicados, eventos.size() - publicados);
  }

  public boolean estaHabilitado() {
    return habilitado;
  }

  /** El cuerpo tal como viaja. Visible para los tests. */
  static byte[] serializar(Evento evento) throws JsonProcessingException {
    return JSON.writeValueAsBytes(evento);
  }

  private static Message mensaje(Evento evento) throws JsonProcessingException {
    MessageProperties propiedades = new MessageProperties();
    propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    propiedades.setContentEncoding(StandardCharsets.UTF_8.name());
    // Persistente: si el broker se reinicia antes de que Consultas lo lea, el evento no se pierde.
    propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    propiedades.setMessageId(evento.eventoId());
    // El mismo traceId de los logs: permite ir del evento en la cola a la operación que lo causó.
    String traceId = MDC.get("traceId");
    if (traceId != null && !traceId.isBlank()) {
      propiedades.setHeader(TrazabilidadFilter.HEADER_TRACE, traceId);
    }
    return new Message(serializar(evento), propiedades);
  }

  private static final class InstanteEnUtc extends StdSerializer<Instant> {
    InstanteEnUtc() {
      super(Instant.class);
    }

    @Override
    public void serialize(Instant valor, JsonGenerator json, SerializerProvider proveedor)
        throws IOException {
      json.writeString(FechasUtc.formatear(valor));
    }
  }
}
