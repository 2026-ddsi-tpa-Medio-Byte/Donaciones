package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacionEn;
import ar.edu.utn.dds.k3003.repositories.DonacionJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests de las métricas que describen el estado del dominio. */
@ExtendWith(MockitoExtension.class)
class MetricasDominioTest {

  @Mock private DonacionJpaRepository repo;

  private MeterRegistry registry;
  private MetricasDominio metricas;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metricas = new MetricasDominio(registry, repo);
  }

  private Donacion donacion(EstadoDonacionEn estado, int cantidad) {
    Donacion d = new Donacion("1", cantidad, "DEP-1", "1", "una donacion");
    d.setEstado(estado);
    return d;
  }

  @Test
  @DisplayName("Cuenta cuántas donaciones hay en cada estado")
  void cuentaPorEstado() {
    when(repo.findAll())
        .thenReturn(
            List.of(
                donacion(EstadoDonacionEn.INGRESADA, 5),
                donacion(EstadoDonacionEn.INGRESADA, 3),
                donacion(EstadoDonacionEn.ACEPTADA, 10),
                donacion(EstadoDonacionEn.CONQUEJA, 2)));

    assertEquals(2, metricas.contarEn(EstadoDonacionEn.INGRESADA));
    assertEquals(1, metricas.contarEn(EstadoDonacionEn.ACEPTADA));
    assertEquals(1, metricas.contarEn(EstadoDonacionEn.CONQUEJA));
  }

  @Test
  @DisplayName("Las unidades en tránsito son solo las de donaciones sin asignar")
  void unidadesEnTransito() {
    when(repo.findAll())
        .thenReturn(
            List.of(
                donacion(EstadoDonacionEn.INGRESADA, 5),
                donacion(EstadoDonacionEn.INGRESADA, 3),
                donacion(EstadoDonacionEn.ACEPTADA, 100)));

    // Las aceptadas ya llegaron: no están en tránsito.
    assertEquals(8, metricas.unidadesEnTransito());
  }

  @Test
  @DisplayName("La tasa de aceptación ignora las que todavía están en curso")
  void tasaIgnoraLasEnCurso() {
    when(repo.findAll())
        .thenReturn(
            List.of(
                donacion(EstadoDonacionEn.ACEPTADA, 1),
                donacion(EstadoDonacionEn.ACEPTADA, 1),
                donacion(EstadoDonacionEn.ACEPTADA, 1),
                donacion(EstadoDonacionEn.CONQUEJA, 1),
                // estas cinco están en curso: si contaran, la tasa daría 37,5% en vez de 75%
                donacion(EstadoDonacionEn.INGRESADA, 1),
                donacion(EstadoDonacionEn.INGRESADA, 1),
                donacion(EstadoDonacionEn.INGRESADA, 1),
                donacion(EstadoDonacionEn.INGRESADA, 1)));

    assertEquals(75.0, metricas.tasaDeAceptacion(), 0.01);
  }

  @Test
  @DisplayName("Sin donaciones resueltas la tasa es 100, no una división por cero")
  void tasaSinResueltas() {
    when(repo.findAll()).thenReturn(List.of(donacion(EstadoDonacionEn.INGRESADA, 5)));

    assertEquals(100.0, metricas.tasaDeAceptacion(), 0.01);
  }

  @Test
  @DisplayName("Si la base falla, la métrica devuelve cero en vez de tumbar el scrape")
  void baseCaida() {
    when(repo.findAll()).thenThrow(new RuntimeException("la base no responde"));

    // Datadog consulta las métricas cada pocos segundos: si esto lanzara, se perderían
    // todas las métricas del servicio, no solo esta.
    assertEquals(0, metricas.contarEn(EstadoDonacionEn.INGRESADA));
    assertEquals(0, metricas.unidadesEnTransito());
  }

  @Test
  @DisplayName("Los medidores quedan registrados con su etiqueta de estado")
  void seRegistranLosMedidores() {
    // Sin stub del repositorio: los medidores son perezosos, solo consultan la base
    // cuando alguien los lee. Registrarlos no dispara ninguna consulta.
    metricas.registrar();

    List<String> estados =
        registry.find("dominio.donaciones.por_estado").gauges().stream()
            .map(g -> g.getId().getTag("estado"))
            .sorted()
            .toList();

    assertEquals(List.of("aceptada", "conqueja", "ingresada"), estados);
  }

  @Test
  @DisplayName("Sin repositorio no registra nada: es el caso de los tests de cátedra")
  void sinRepositorioNoRegistra() {
    MeterRegistry vacio = new SimpleMeterRegistry();
    new MetricasDominio(vacio, null).registrar();

    assertEquals(0, vacio.getMeters().size());
  }
}
