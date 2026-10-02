package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.exceptions.DatoInvalidoException;
import ar.edu.utn.dds.k3003.exceptions.DonacionNoQuejableException;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoHabilitadoException;
import ar.edu.utn.dds.k3003.exceptions.ModuloNoDisponibleException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Manejador global de excepciones para devolver códigos HTTP correctos.
 *
 * <p>Clave para la integración de Entrega 4: el flujo "Donadores y Entidades → Donaciones" valida
 * si el producto solicitado existe consultando {@code GET /productos/{id}}. Sin este handler, un
 * producto inexistente devolvía 500; ahora devuelve 404, que es lo que el módulo consumidor espera.
 *
 * <p>Cada motivo de rechazo tiene su código porque quien llama decide con él: «no existe» (404),
 * «no tiene permiso» (403), «choca con el estado actual» (409) y «falló otro módulo» (502) piden
 * reacciones distintas. El cuerpo es siempre {@code {"error": "<mensaje>"}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
    return responder(HttpStatus.NOT_FOUND, e);
  }

  @ExceptionHandler(DonadorNoEncontradoException.class)
  public ResponseEntity<Map<String, String>> handleDonadorNoEncontrado(
      DonadorNoEncontradoException e) {
    return responder(HttpStatus.NOT_FOUND, e);
  }

  @ExceptionHandler(DonadorNoHabilitadoException.class)
  public ResponseEntity<Map<String, String>> handleNoHabilitado(DonadorNoHabilitadoException e) {
    return responder(HttpStatus.FORBIDDEN, e);
  }

  @ExceptionHandler(DonacionNoQuejableException.class)
  public ResponseEntity<Map<String, String>> handleConflicto(DonacionNoQuejableException e) {
    return responder(HttpStatus.CONFLICT, e);
  }

  @ExceptionHandler(ModuloNoDisponibleException.class)
  public ResponseEntity<Map<String, String>> handleModuloNoDisponible(
      ModuloNoDisponibleException e) {
    log.error("[Donaciones] Falló el módulo {}: {}", e.getModulo(), e.getMessage());
    return responder(HttpStatus.BAD_GATEWAY, e);
  }

  /**
   * Red de seguridad: los clientes HTTP ya traducen estas excepciones a {@link
   * ModuloNoDisponibleException}, pero si alguna se escapara sin traducir sigue siendo un problema
   * de otro módulo, no un pedido mal hecho.
   */
  @ExceptionHandler({ResourceAccessException.class, HttpServerErrorException.class})
  public ResponseEntity<Map<String, String>> handleOtroModuloSinTraducir(RuntimeException e) {
    log.error("[Donaciones] Falló otro módulo: {}", e.getMessage());
    return responder(HttpStatus.BAD_GATEWAY, e);
  }

  @ExceptionHandler({DatoInvalidoException.class, IllegalArgumentException.class})
  public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException e) {
    return responder(HttpStatus.BAD_REQUEST, e);
  }

  /**
   * El resto de las reglas de negocio (producto con nombre repetido, descripción corta, etc.)
   * sigue saliendo como 400: hay validaciones y consumidores que dependen de ese código.
   */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
    log.warn("[Donaciones] Solicitud rechazada: {}", e.getMessage());
    return responder(HttpStatus.BAD_REQUEST, e);
  }

  private ResponseEntity<Map<String, String>> responder(HttpStatus status, RuntimeException e) {
    return ResponseEntity.status(status).body(Map.of("error", String.valueOf(e.getMessage())));
  }
}
