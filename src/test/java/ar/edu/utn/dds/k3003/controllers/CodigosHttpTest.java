package ar.edu.utn.dds.k3003.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.exceptions.DatoInvalidoException;
import ar.edu.utn.dds.k3003.exceptions.DonacionNoQuejableException;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoHabilitadoException;
import ar.edu.utn.dds.k3003.exceptions.ModuloNoDisponibleException;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Código HTTP que recibe quien llama según el motivo del rechazo. Los otros módulos, el bot y el
 * MCP deciden qué hacer mirando el código: «no existe», «no tenés permiso», «choca con el estado
 * actual» y «el que falló es otro módulo» tienen que poder distinguirse.
 */
class CodigosHttpTest {

  private Fachada fachada;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    fachada = mock(Fachada.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new DonacionController(fachada))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private ResultActions donar() throws Exception {
    return mvc.perform(
        post("/donaciones")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(
                "{\"donadorID\":\"1\",\"depositoID\":\"DEP-1\",\"productoID\":\"1\",\"cantidad\":5}"));
  }

  private ResultActions quejarse() throws Exception {
    return mvc.perform(
        post("/donaciones/1/quejas")
            .contentType(MediaType.TEXT_PLAIN)
            .accept(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8)
            .content("Llegó roto"));
  }

  @Test
  @DisplayName("Un recurso inexistente responde 404")
  void recursoInexistente() throws Exception {
    when(fachada.registrarDonacion(any()))
        .thenThrow(new NoSuchElementException("Producto no encontrado: 9"));

    donar()
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Producto no encontrado: 9"));
  }

  @Test
  @DisplayName("Un donador inexistente responde 404 con un mensaje legible")
  void donadorInexistente() throws Exception {
    when(fachada.registrarDonacion(any()))
        .thenThrow(new DonadorNoEncontradoException("No existe el donador 1"));

    donar()
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("No existe el donador 1"));
  }

  @Test
  @DisplayName("Un donador no habilitado responde 403 y el cuerpo sigue diciendo «No puede donar»")
  void donadorNoHabilitado() throws Exception {
    when(fachada.registrarDonacion(any()))
        .thenThrow(new DonadorNoHabilitadoException("No puede donar: el donador 1 está BANEADO"));

    donar()
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value(containsString("No puede donar")));
  }

  @Test
  @DisplayName("Una queja sobre una donación no entregada o ya con queja responde 409")
  void quejaEnConflictoConElEstado() throws Exception {
    when(fachada.registrarQuejaEnDonacion(anyString(), anyString()))
        .thenThrow(new DonacionNoQuejableException("La donación 1 todavía no fue entregada"));

    quejarse()
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("La donación 1 todavía no fue entregada"));
  }

  @Test
  @DisplayName("Si otro módulo no está disponible responde 502 nombrando al módulo")
  void moduloNoDisponible() throws Exception {
    when(fachada.registrarDonacion(any()))
        .thenThrow(
            new ModuloNoDisponibleException(
                "Logística", "El módulo Logística respondió con error 500", null));

    donar()
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value(containsString("Logística")));
  }

  @Test
  @DisplayName("Una caída de otro módulo sin traducir también responde 502, no 400")
  void caidaSinTraducir() throws Exception {
    when(fachada.registrarDonacion(any()))
        .thenThrow(new ResourceAccessException("I/O error on POST request: Connection refused"));

    donar().andExpect(status().isBadGateway());
  }

  @Test
  @DisplayName("Un 5xx de otro módulo sin traducir también responde 502, no 400")
  void errorDelServidorSinTraducir() throws Exception {
    when(fachada.registrarDonacion(any()))
        .thenThrow(
            HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                HttpHeaders.EMPTY, null, null));

    donar().andExpect(status().isBadGateway());
  }

  @Test
  @DisplayName("Una validación de entrada responde 400 nombrando el dato")
  void validacionDeEntrada() throws Exception {
    when(fachada.registrarDonacion(any()))
        .thenThrow(new DatoInvalidoException("El campo depositoID es obligatorio"));

    donar()
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("El campo depositoID es obligatorio"));
  }

  @Test
  @DisplayName("Cualquier otra regla de negocio sigue respondiendo 400")
  void reglaDeNegocioGenerica() throws Exception {
    when(fachada.registrarDonacion(any()))
        .thenThrow(new RuntimeException("Regla de negocio violada"));

    donar()
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Regla de negocio violada"));
  }
}
