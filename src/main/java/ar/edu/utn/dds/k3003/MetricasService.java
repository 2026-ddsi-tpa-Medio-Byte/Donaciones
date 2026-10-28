package ar.edu.utn.dds.k3003;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MetricasService {

  /** Lo necesitamos guardado para poder registrar métricas con tags variables. */
  private final MeterRegistry meterRegistry;

  private final Counter donacionesRegistradas;
  private final Counter donacionesErrores;
  private final Counter donacionesCambioEstado;
  private final Counter donacionesQuejas;
  private final Counter donacionesConsultas;
  private final Counter productosRegistrados;
  private final Counter identificadoresRegistrados;

  public MetricasService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    this.donacionesRegistradas =
        Counter.builder("donaciones.registradas")
            .description("Cantidad de donaciones registradas exitosamente")
            .tag("modulo", "donaciones")
            .register(meterRegistry);

    this.donacionesErrores =
        Counter.builder("donaciones.errores")
            .description("Cantidad de errores al operar con donaciones")
            .tag("modulo", "donaciones")
            .register(meterRegistry);

    this.donacionesCambioEstado =
        Counter.builder("donaciones.cambio_estado")
            .description("Cantidad de cambios de estado de donaciones")
            .tag("modulo", "donaciones")
            .register(meterRegistry);

    this.donacionesQuejas =
        Counter.builder("donaciones.quejas")
            .description("Cantidad de quejas registradas en donaciones")
            .tag("modulo", "donaciones")
            .register(meterRegistry);

    this.donacionesConsultas =
        Counter.builder("donaciones.consultas")
            .description("Cantidad de consultas/búsquedas de donaciones")
            .tag("modulo", "donaciones")
            .register(meterRegistry);

    this.productosRegistrados =
        Counter.builder("productos.registrados")
            .description("Cantidad de productos dados de alta")
            .tag("modulo", "donaciones")
            .register(meterRegistry);

    this.identificadoresRegistrados =
        Counter.builder("identificadores.registrados")
            .description("Cantidad de identificadores dados de alta")
            .tag("modulo", "donaciones")
            .register(meterRegistry);
  }

  public void incrementarDonacionesRegistradas() {
    donacionesRegistradas.increment();
  }

  public void incrementarDonacionesErrores() {
    donacionesErrores.increment();
  }

  public void incrementarDonacionesCambioEstado() {
    donacionesCambioEstado.increment();
  }

  public void incrementarDonacionesQuejas() {
    donacionesQuejas.increment();
  }

  public void incrementarDonacionesConsultas() {
    donacionesConsultas.increment();
  }

  public void incrementarProductosRegistrados() {
    productosRegistrados.increment();
  }

  public void incrementarIdentificadoresRegistrados() {
    identificadoresRegistrados.increment();
  }

  // ── Impacto en los otros módulos ──────────────────────────────────────────
  //
  // Los contadores de arriba dicen qué pasó *dentro* de Donaciones. Estos dicen a quién
  // salimos a llamar por cada operación, y cómo nos fue. Agrupando por la etiqueta
  // "operacion" en Datadog se ve el efecto dominó completo: un POST /donaciones dispara
  // dos llamadas a Donadores y una a Logística.

  /**
   * Registra una llamada saliente a otro módulo.
   *
   * @param operacion qué operación nuestra la disparó, por ejemplo {@code registrar_donacion}
   * @param destino módulo al que le pegamos: {@code donadores} o {@code logistica}
   * @param accion qué le pedimos, por ejemplo {@code validar_donador}
   * @param exito si respondió bien
   * @param nanos cuánto tardó
   */
  public void registrarLlamadaSaliente(
      String operacion, String destino, String accion, boolean exito, long nanos) {
    Counter.builder("integracion.llamadas")
        .description("Llamadas salientes de Donaciones a otros módulos")
        .tag("modulo", "donaciones")
        .tag("operacion", operacion)
        .tag("destino", destino)
        .tag("accion", accion)
        .tag("resultado", exito ? "ok" : "error")
        .register(meterRegistry)
        .increment();

    Timer.builder("integracion.duracion")
        .description("Cuánto tardan las llamadas salientes de Donaciones")
        .tag("modulo", "donaciones")
        .tag("operacion", operacion)
        .tag("destino", destino)
        .tag("accion", accion)
        .register(meterRegistry)
        .record(nanos, TimeUnit.NANOSECONDS);
  }

  // ── Eventos para el modelo de lectura (Entrega 6) ─────────────────────────
  //
  // Un evento que no sale no hace fallar la operación: solo queda en el log y acá. Por eso
  // hace falta contarlo, para poder alarmar si Mongo se está quedando atrás.

  /** Un evento que llegó a RabbitMQ. */
  public void registrarEventoPublicado(String tipo) {
    contadorDeEventos("eventos.publicados", "Eventos publicados en RabbitMQ", tipo).increment();
  }

  /** Un evento que no se pudo publicar: hay que republicar para que Consultas se ponga al día. */
  public void registrarEventoFallido(String tipo) {
    contadorDeEventos("eventos.fallidos", "Eventos que no se pudieron publicar", tipo).increment();
  }

  private Counter contadorDeEventos(String nombre, String descripcion, String tipo) {
    return Counter.builder(nombre)
        .description(descripcion)
        .tag("modulo", "donaciones")
        .tag("tipo", tipo)
        .register(meterRegistry);
  }
}
