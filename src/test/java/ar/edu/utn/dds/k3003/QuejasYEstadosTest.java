package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.exceptions.DatoInvalidoException;
import ar.edu.utn.dds.k3003.exceptions.DonacionNoQuejableException;
import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacionEn;
import ar.edu.utn.dds.k3003.repositories.DonacionJpaRepository;
import ar.edu.utn.dds.k3003.repositories.IdentificadorJpaRepository;
import ar.edu.utn.dds.k3003.repositories.ProductoJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Quejas sobre donaciones y cambios de estado con su historial de auditoría. */
class QuejasYEstadosTest {

  private FachadaDonadoresYEntidades donadores;
  private FachadaLogistica logistica;
  private Fachada fachada;
  private DonacionDTO donacionNueva;

  @BeforeEach
  void setUp() {
    donadores = mock(FachadaDonadoresYEntidades.class);
    logistica = mock(FachadaLogistica.class);
    when(donadores.puedeDonar("donador1")).thenReturn(true);

    fachada = new Fachada();
    fachada.setFachadaDonadoresYEntidades(donadores);
    fachada.setFachadaLogistica(logistica);
    IdentificadorDTO identificador =
        fachada.agregarIdentificador(
            new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo de barras"));
    ProductoDTO producto =
        fachada.agregarProducto(
            new ProductoDTO(
                null, "Fideos", "Fideos secos tipo tirabuzon", "alimentos", identificador.id()));
    donacionNueva =
        new DonacionDTO(null, "donador1", "deposito1", "Fideos", producto.id(), 5, null);
  }

  /** Una donación con el ID y el estado dados, como la traería la base. */
  private static Donacion donacionGuardada(Long id, EstadoDonacionEn estado) {
    Donacion d = new Donacion("donador1", 5, "deposito1", "1", "Fideos");
    d.setId(id);
    d.setEstado(estado);
    return d;
  }

  private Fachada fachadaConJpa(DonacionJpaRepository repo) {
    return new Fachada(
        repo,
        mock(ProductoJpaRepository.class),
        mock(IdentificadorJpaRepository.class),
        donadores,
        logistica,
        new MetricasService(new SimpleMeterRegistry()));
  }

  // ── Quejas ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("Rechaza la queja sobre una donación todavía no entregada, sin avisar a Donadores")
  void quejaSobreDonacionNoEntregada() {
    DonacionDTO donacion = fachada.registrarDonacion(donacionNueva);

    DonacionNoQuejableException e =
        assertThrows(
            DonacionNoQuejableException.class,
            () -> fachada.registrarQuejaEnDonacion(donacion.id(), "Llegó roto"));

    assertTrue(e.getMessage().contains("INGRESADA"), e.getMessage());
    assertEquals(
        EstadoDonacionEnum.INGRESADA, fachada.buscarDonacionPorID(donacion.id()).estado());
    verify(donadores, never()).agregarQueja(any());
  }

  @Test
  @DisplayName("Una donación entregada admite una sola queja: la segunda se rechaza")
  void segundaQuejaSobreLaMismaDonacion() {
    DonacionDTO donacion = fachada.registrarDonacion(donacionNueva);
    fachada.cambiarEstadoDeDonacion(donacion.id(), EstadoDonacionEnum.ACEPTADA);
    fachada.registrarQuejaEnDonacion(donacion.id(), "Llegó roto");

    DonacionNoQuejableException e =
        assertThrows(
            DonacionNoQuejableException.class,
            () -> fachada.registrarQuejaEnDonacion(donacion.id(), "Sigue roto"));

    assertTrue(e.getMessage().contains("CONQUEJA"), e.getMessage());
    verify(donadores, times(1)).agregarQueja(any());
  }

  @Test
  @DisplayName("La queja sobre una donación entregada la deja CONQUEJA y llega a Donadores")
  void quejaSobreDonacionEntregada() {
    DonacionDTO donacion = fachada.registrarDonacion(donacionNueva);
    fachada.cambiarEstadoDeDonacion(donacion.id(), EstadoDonacionEnum.ACEPTADA);

    DonacionDTO respuesta = fachada.registrarQuejaEnDonacion(donacion.id(), "Llegó roto");

    assertEquals(EstadoDonacionEnum.CONQUEJA, respuesta.estado());
    assertEquals(
        EstadoDonacionEnum.CONQUEJA, fachada.buscarDonacionPorID(donacion.id()).estado());
    verify(donadores, times(1)).agregarQueja(any());
  }

  @ParameterizedTest(name = "descripción [{0}]")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Una queja sin descripción se rechaza acá, sin llegar a Donadores, y la donación sigue ACEPTADA")
  void quejaSinDescripcion(String descripcion) {
    DonacionDTO donacion = fachada.registrarDonacion(donacionNueva);
    fachada.cambiarEstadoDeDonacion(donacion.id(), EstadoDonacionEnum.ACEPTADA);

    DatoInvalidoException e =
        assertThrows(
            DatoInvalidoException.class,
            () -> fachada.registrarQuejaEnDonacion(donacion.id(), descripcion));

    assertTrue(e.getMessage().contains("descripción"), e.getMessage());
    assertEquals(
        EstadoDonacionEnum.ACEPTADA, fachada.buscarDonacionPorID(donacion.id()).estado());
    verify(donadores, never()).agregarQueja(any());
  }

  @Test
  @DisplayName("Sin descripción, primero se informa que la donación no existe y después el dato faltante")
  void ordenDeLosControlesDeLaQueja() {
    DonacionDTO ingresada = fachada.registrarDonacion(donacionNueva);

    // 404 antes que 400: no tiene sentido pedir que complete la descripción de algo que no existe.
    assertThrows(
        NoSuchElementException.class, () -> fachada.registrarQuejaEnDonacion("999", " "));
    // 400 antes que 409: el pedido está mal armado sea cual sea el estado de la donación.
    assertThrows(
        DatoInvalidoException.class, () -> fachada.registrarQuejaEnDonacion(ingresada.id(), ""));
    verify(donadores, never()).agregarQueja(any());
  }

  @Test
  @DisplayName("La respuesta de la queja trae el estado nuevo aunque cada lectura de la base sea otra instancia")
  void respuestaDeLaQuejaConElEstadoActualizado() {
    // Así se comporta JPA fuera de una transacción: cada findById arma una instancia nueva con
    // lo que haya guardado. Con el repositorio en memoria siempre es la misma y el error no se ve.
    DonacionJpaRepository repo = mock(DonacionJpaRepository.class);
    EstadoDonacionEn[] enLaBase = {EstadoDonacionEn.ACEPTADA};
    when(repo.findById(1L)).thenAnswer(inv -> Optional.of(donacionGuardada(1L, enLaBase[0])));
    when(repo.save(any()))
        .thenAnswer(
            inv -> {
              Donacion d = inv.getArgument(0);
              enLaBase[0] = d.getEstado();
              return d;
            });

    DonacionDTO respuesta = fachadaConJpa(repo).registrarQuejaEnDonacion("1", "Llegó roto");

    assertEquals(EstadoDonacionEnum.CONQUEJA, respuesta.estado());
  }

  // ── Historial de estados ──────────────────────────────────────────────────

  @Test
  @DisplayName("Cambiar el estado agrega la nueva entrada al historial de auditoría")
  void cambioDeEstadoQuedaEnElHistorial() {
    DonacionJpaRepository repo = mock(DonacionJpaRepository.class);
    Donacion donacion = donacionGuardada(1L, EstadoDonacionEn.INGRESADA);
    when(repo.findById(1L)).thenReturn(Optional.of(donacion));
    when(repo.save(donacion)).thenReturn(donacion);

    fachadaConJpa(repo).cambiarEstadoDeDonacion("1", EstadoDonacionEnum.ACEPTADA);

    List<EstadoDonacionEnum> historial =
        donacion.getHistorialEstados().stream().map(EstadoDonacion::getEstado).toList();
    assertEquals(List.of(EstadoDonacionEnum.INGRESADA, EstadoDonacionEnum.ACEPTADA), historial);
    assertNotNull(donacion.getHistorialEstados().get(1).getFecha());
    assertEquals(EstadoDonacionEn.ACEPTADA, donacion.getEstado());
  }
}
