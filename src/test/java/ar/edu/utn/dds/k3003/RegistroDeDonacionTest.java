package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.exceptions.DatoInvalidoException;
import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.Producto;
import ar.edu.utn.dds.k3003.repositories.DonacionJpaRepository;
import ar.edu.utn.dds.k3003.repositories.IdentificadorJpaRepository;
import ar.edu.utn.dds.k3003.repositories.ProductoJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Registro de una donación: validación de entrada, compensación cuando Logística falla y conteo
 * de errores en las métricas.
 */
class RegistroDeDonacionTest {

  private FachadaDonadoresYEntidades donadores;
  private FachadaLogistica logistica;

  @BeforeEach
  void mocksDeLosOtrosModulos() {
    donadores = mock(FachadaDonadoresYEntidades.class);
    logistica = mock(FachadaLogistica.class);
  }

  private static DonacionDTO donacion(
      String donadorID, String depositoID, String productoID, Integer cantidad) {
    return new DonacionDTO(null, donadorID, depositoID, "Fideos", productoID, cantidad, null);
  }

  // ── Con los repositorios en memoria (los que usan los tests de cátedra) ───────

  @Nested
  @DisplayName("Con repositorios en memoria")
  class EnMemoria {

    private Fachada fachada;
    private String productoID;

    @BeforeEach
    void setUp() {
      fachada = new Fachada();
      fachada.setFachadaDonadoresYEntidades(donadores);
      fachada.setFachadaLogistica(logistica);
      IdentificadorDTO identificador =
          fachada.agregarIdentificador(
              new IdentificadorDTO(
                  null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo de barras"));
      productoID =
          fachada
              .agregarProducto(
                  new ProductoDTO(
                      null, "Fideos", "Fideos secos tipo tirabuzon", "alimentos",
                      identificador.id()))
              .id();
    }

    @Test
    @DisplayName("Si Logística falla, la donación recién guardada se borra y el error llega igual")
    void logisticaFallaYLaDonacionNoQueda() {
      when(donadores.puedeDonar("donador1")).thenReturn(true);
      when(logistica.gestionarDonacion(any(), any(), any(), anyInt()))
          .thenThrow(new RuntimeException("Logística caída"));

      RuntimeException e =
          assertThrows(
              RuntimeException.class,
              () -> fachada.registrarDonacion(donacion("donador1", "DEP-1", productoID, 5)));

      assertEquals("Logística caída", e.getMessage());
      assertTrue(fachada.buscarTodasDonaciones().isEmpty(), "no debe quedar la donación huérfana");
      assertThrows(NoSuchElementException.class, () -> fachada.buscarDonacionPorID("1"));
    }

    @Test
    @DisplayName("Si Logística responde bien, la donación queda guardada")
    void logisticaRespondeYLaDonacionQueda() {
      when(donadores.puedeDonar("donador1")).thenReturn(true);

      DonacionDTO registrada =
          fachada.registrarDonacion(donacion("donador1", "DEP-1", productoID, 5));

      assertEquals(registrada.id(), fachada.buscarDonacionPorID(registrada.id()).id());
    }

    static Stream<Arguments> camposObligatoriosVacios() {
      return Stream.of(
          Arguments.of("donadorID", donacion(null, "DEP-1", "1", 5)),
          Arguments.of("donadorID", donacion("  ", "DEP-1", "1", 5)),
          Arguments.of("depositoID", donacion("donador1", null, "1", 5)),
          Arguments.of("depositoID", donacion("donador1", "", "1", 5)),
          Arguments.of("productoID", donacion("donador1", "DEP-1", null, 5)),
          Arguments.of("productoID", donacion("donador1", "DEP-1", " ", 5)));
    }

    @ParameterizedTest(name = "falta {0}")
    @MethodSource("camposObligatoriosVacios")
    @DisplayName("Un campo obligatorio vacío se rechaza nombrándolo, sin consultar a otros módulos")
    void campoObligatorioVacio(String campo, DonacionDTO dto) {
      DatoInvalidoException e =
          assertThrows(DatoInvalidoException.class, () -> fachada.registrarDonacion(dto));

      assertTrue(e.getMessage().contains(campo), e.getMessage());
      verifyNoInteractions(donadores, logistica);
    }
  }

  // ── Con repositorios JPA simulados ──────────────────────────────────────────

  @Nested
  @DisplayName("Con repositorios JPA")
  class ConJpa {

    private DonacionJpaRepository repoDonaciones;
    private MeterRegistry registry;
    private Fachada fachada;

    @BeforeEach
    void setUp() {
      repoDonaciones = mock(DonacionJpaRepository.class);
      ProductoJpaRepository repoProductos = mock(ProductoJpaRepository.class);
      when(repoProductos.findById(1L))
          .thenReturn(Optional.of(new Producto("Fideos", "Fideos secos tipo tirabuzon", null, 1L)));
      when(repoDonaciones.save(any()))
          .thenAnswer(
              inv -> {
                Donacion d = inv.getArgument(0);
                if (d.getId() == null) {
                  d.setId(42L);
                }
                return d;
              });
      registry = new SimpleMeterRegistry();
      fachada =
          new Fachada(
              repoDonaciones,
              repoProductos,
              mock(IdentificadorJpaRepository.class),
              donadores,
              logistica,
              new MetricasService(registry));
    }

    private double contador(String nombre) {
      return registry.get(nombre).counter().count();
    }

    @Test
    @DisplayName("Si Logística falla, se borra por ID la donación que se acababa de guardar")
    void compensacionBorraLaDonacion() {
      when(donadores.puedeDonar("donador1")).thenReturn(true);
      when(logistica.gestionarDonacion(any(), any(), any(), anyInt()))
          .thenThrow(new RuntimeException("Logística caída"));

      assertThrows(
          RuntimeException.class,
          () -> fachada.registrarDonacion(donacion("donador1", "DEP-1", "1", 5)));

      verify(repoDonaciones).deleteById(42L);
    }

    @Test
    @DisplayName("Si además falla el borrado, el cliente recibe el error de Logística y no el de la base")
    void compensacionFallidaNoTapaElErrorOriginal() {
      when(donadores.puedeDonar("donador1")).thenReturn(true);
      when(logistica.gestionarDonacion(any(), any(), any(), anyInt()))
          .thenThrow(new RuntimeException("Logística caída"));
      doThrow(new RuntimeException("la base no responde")).when(repoDonaciones).deleteById(42L);

      RuntimeException e =
          assertThrows(
              RuntimeException.class,
              () -> fachada.registrarDonacion(donacion("donador1", "DEP-1", "1", 5)));

      assertEquals("Logística caída", e.getMessage());
    }

    @Test
    @DisplayName("Si Logística responde bien, no se borra nada")
    void sinFallaNoHayCompensacion() {
      when(donadores.puedeDonar("donador1")).thenReturn(true);

      fachada.registrarDonacion(donacion("donador1", "DEP-1", "1", 5));

      verify(repoDonaciones, never()).deleteById(any());
    }

    // ── Métricas: cada registro fallido cuenta exactamente un error ──────────

    @Test
    @DisplayName("Un donador no habilitado cuenta un solo error, no dos")
    void noPuedeDonarCuentaUnError() {
      when(donadores.puedeDonar("donador1")).thenReturn(false);

      assertThrows(
          RuntimeException.class,
          () -> fachada.registrarDonacion(donacion("donador1", "DEP-1", "1", 5)));

      assertEquals(1.0, contador("donaciones.errores"));
    }

    @Test
    @DisplayName("Una validación de entrada fallida cuenta un solo error")
    void validacionCuentaUnError() {
      assertThrows(
          RuntimeException.class,
          () -> fachada.registrarDonacion(donacion("donador1", "DEP-1", "1", 0)));

      assertEquals(1.0, contador("donaciones.errores"));
    }

    @Test
    @DisplayName("Un producto inexistente cuenta un solo error")
    void productoInexistenteCuentaUnError() {
      assertThrows(
          RuntimeException.class,
          () -> fachada.registrarDonacion(donacion("donador1", "DEP-1", "999", 5)));

      assertEquals(1.0, contador("donaciones.errores"));
    }

    @Test
    @DisplayName("Si Logística falla, cuenta un error y ninguna donación registrada")
    void logisticaFallaCuentaUnError() {
      when(donadores.puedeDonar("donador1")).thenReturn(true);
      when(logistica.gestionarDonacion(any(), any(), any(), anyInt()))
          .thenThrow(new RuntimeException("Logística caída"));

      assertThrows(
          RuntimeException.class,
          () -> fachada.registrarDonacion(donacion("donador1", "DEP-1", "1", 5)));

      assertEquals(1.0, contador("donaciones.errores"));
      assertEquals(0.0, contador("donaciones.registradas"));
    }

    @Test
    @DisplayName("Una donación exitosa cuenta como registrada y no suma errores")
    void exitoNoCuentaErrores() {
      when(donadores.puedeDonar("donador1")).thenReturn(true);

      fachada.registrarDonacion(donacion("donador1", "DEP-1", "1", 5));

      assertEquals(0.0, contador("donaciones.errores"));
      assertEquals(1.0, contador("donaciones.registradas"));
    }
  }
}
