package ar.edu.utn.dds.k3003;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class MetricasService {

  private final Counter donacionesRegistradas;
  private final Counter donacionesErrores;
  private final Counter donacionesCambioEstado;
  private final Counter donacionesQuejas;

  public MetricasService(MeterRegistry meterRegistry) {
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
}
