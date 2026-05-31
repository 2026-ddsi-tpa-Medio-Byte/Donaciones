package ar.edu.utn.dds.k3003.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MisDonacionesTest {

  private Fachada fachada;

  @Mock private FachadaDonadoresYEntidades mockDonadores;

  @Mock private FachadaLogistica mockLogistica;

  private DonacionDTO dtoEjemplo;

  @BeforeEach
  void setUp() {
    fachada = new Fachada();
    fachada.setFachadaDonadoresYEntidades(mockDonadores);
    fachada.setFachadaLogistica(mockLogistica);

    dtoEjemplo =
        new DonacionDTO(
            null, "donador1", "deposito1", "Desc", "prod1", 5, EstadoDonacionEnum.INGRESADA);
  }

  @Test
  @DisplayName("Debe registrar una donación correctamente cuando las validaciones pasan")
  void registrarDonacionOk() {
    // Configuración de Mocks
    when(mockDonadores.buscarDonadorPorID("donador1"))
        .thenReturn(new DonadorDTO("id", "nombre", "mail", 1, "dir", "tel", "cuit", null, "ubi"));
    when(mockDonadores.puedeDonar("donador1")).thenReturn(true);

    // Ejecución
    DonacionDTO resultado = fachada.registrarDonacion(dtoEjemplo);

    // Verificaciones
    Assertions.assertNotNull(resultado.id(), "El ID no debería ser nulo tras el registro");
    Assertions.assertEquals(EstadoDonacionEnum.INGRESADA, resultado.estado());
    verify(mockLogistica, times(1)).gestionarDonacion(any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("Debe fallar al cambiar estado si el nuevo estado es nulo (Robustez)")
  void cambiarEstadoNullFallido() {
    // Primero registramos una para tener un ID válido
    when(mockDonadores.buscarDonadorPorID(any()))
        .thenReturn(new DonadorDTO("id", "nombre", "mail", 1, "dir", "tel", "cuit", null, "ubi"));
    when(mockDonadores.puedeDonar(any())).thenReturn(true);
    DonacionDTO registrada = fachada.registrarDonacion(dtoEjemplo);

    // Verificamos que lance excepción ante estado null
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          fachada.cambiarEstadoDeDonacion(registrada.id(), null);
        });
  }

  @Test
  @DisplayName("Debe fallar al buscar una donación con ID inexistente")
  void buscarInexistenteFallido() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          fachada.buscarDonacionPorID("999999");
        });
  }

  @Test
  @DisplayName("No debe permitir registrar una donación si el donador no puede donar")
  void registrarDonacionProhibida() {
    when(mockDonadores.buscarDonadorPorID("donador1"))
        .thenReturn(new DonadorDTO("id", "nombre", "mail", 1, "dir", "tel", "cuit", null, "ubi"));
    when(mockDonadores.puedeDonar("donador1")).thenReturn(false);

    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          fachada.registrarDonacion(dtoEjemplo);
        });

    // Verificamos que NUNCA se llamó a logística porque falló antes
    verify(mockLogistica, never()).gestionarDonacion(any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("Debe lanzar excepción si se intenta registrar una donación nula")
  void registrarDonacionNula() {
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          fachada.registrarDonacion(null);
        });
  }

  public class TrazabilidadTest {

    @Test
    public void testHistorialDeEstados() {
      // 1. Crear la donación (Nace como INGRESADA)
      Donacion donacion = new Donacion("donador123", 5, "producto123", "Donación de fideos");

      // 2. Cambiar a ACEPTADA
      fachada.cambiarEstadoDeDonacion(donacion.getId().toString(), EstadoDonacionEnum.ACEPTADA);

      // 3. Cambiar a CONQUEJA
      fachada.cambiarEstadoDeDonacion(donacion.getId().toString(), EstadoDonacionEnum.CONQUEJA);

      // 4. Verificaciones (Assertions)
      List<EstadoDonacion> historial = donacion.getHistorialEstados();

      // Verificamos que haya 3 registros (INGRESADA, ACEPTADA, CON_QUEJA)
      assertEquals(3, historial.size(), "El historial debería tener 3 estados");

      // Verificamos el orden y los valores
      assertEquals(EstadoDonacionEn.INGRESADA.name(), historial.get(0).getEstado().name());
      assertEquals(EstadoDonacionEn.ACEPTADA.name(), historial.get(1).getEstado().name());
      assertEquals(EstadoDonacionEn.CONQUEJA.name(), historial.get(2).getEstado().name());

      // Verificamos que las fechas no sean nulas
      assertNotNull(historial.get(0).getFecha());
      assertNotNull(historial.get(1).getFecha());
      assertNotNull(historial.get(2).getFecha());
    }
  }
}
