package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacionEn;
import ar.edu.utn.dds.k3003.repositories.DonacionJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Métricas que describen el estado del dominio, no la actividad.
 *
 * <p>Los contadores de {@link MetricasService} dicen cuántas donaciones se registraron desde que
 * arrancó el servicio. Estas dicen en qué estado están **ahora**: cuántas siguen esperando que
 * Logística las asigne, cuántas llegaron a destino, cuántas tienen quejas.
 *
 * <p>La más útil para alarmar es la de donaciones ingresadas: si ese número crece y no baja,
 * significa que se donó pero nada se está entregando, y eso no lo puede detectar ningún contador
 * acumulado.
 *
 * <p>Guardado por null: con el constructor sin argumentos que usan los tests de cátedra no hay
 * repositorio, y sin él no se registra ninguna métrica.
 */
@Component
public class MetricasDominio {

  private final MeterRegistry registry;
  private final DonacionJpaRepository donaciones;

  @Autowired(required = false)
  public MetricasDominio(MeterRegistry registry, DonacionJpaRepository donaciones) {
    this.registry = registry;
    this.donaciones = donaciones;
  }

  @PostConstruct
  void registrar() {
    if (registry == null || donaciones == null) {
      return;
    }

    // Un medidor por estado: así se puede alarmar sobre las ingresadas sin que las
    // demás metan ruido en el mismo gráfico.
    for (EstadoDonacionEn estado : EstadoDonacionEn.values()) {
      Gauge.builder("dominio.donaciones.por_estado", this, m -> m.contarEn(estado))
          .description("Donaciones en cada estado del circuito")
          .tag("modulo", "donaciones")
          .tag("estado", estado.name().toLowerCase())
          .register(registry);
    }

    Gauge.builder("dominio.donaciones.unidades_en_transito", this, m -> m.unidadesEnTransito())
        .description("Unidades donadas que todavía no llegaron a ninguna necesidad")
        .tag("modulo", "donaciones")
        .register(registry);

    Gauge.builder("dominio.donaciones.tasa_aceptacion", this, m -> m.tasaDeAceptacion())
        .description("Porcentaje de donaciones que terminaron aceptadas")
        .tag("modulo", "donaciones")
        .register(registry);
  }

  // ── Cálculos ───────────────────────────────────────────────────────────────

  double contarEn(EstadoDonacionEn estado) {
    return todas().stream().filter(d -> estado.equals(d.getEstado())).count();
  }

  /** Lo donado que sigue sin asignarse: si sube y no baja, el circuito se cortó. */
  double unidadesEnTransito() {
    return todas().stream()
        .filter(d -> EstadoDonacionEn.INGRESADA.equals(d.getEstado()))
        .mapToInt(d -> d.getCantidad() == null ? 0 : d.getCantidad())
        .sum();
  }

  /**
   * Qué proporción de lo donado terminó llegando.
   *
   * <p>Se excluyen las ingresadas del denominador: todavía están en curso y contarlas como
   * fracaso haría que la tasa se desplome apenas entra una donación nueva.
   */
  double tasaDeAceptacion() {
    List<Donacion> resueltas =
        todas().stream()
            .filter(d -> !EstadoDonacionEn.INGRESADA.equals(d.getEstado()))
            .toList();
    if (resueltas.isEmpty()) {
      return 100.0;
    }
    long aceptadas =
        resueltas.stream().filter(d -> EstadoDonacionEn.ACEPTADA.equals(d.getEstado())).count();
    return aceptadas * 100.0 / resueltas.size();
  }

  /** Si la base falla, la métrica devuelve cero en vez de tumbar el scrape de Datadog. */
  private List<Donacion> todas() {
    try {
      return donaciones.findAll();
    } catch (RuntimeException e) {
      return List.of();
    }
  }
}
