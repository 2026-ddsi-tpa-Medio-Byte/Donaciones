package ar.edu.utn.dds.k3003.exceptions;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Otro módulo no contestó o contestó con un 5xx. Se responde 502: el pedido estaba bien y
 * Donaciones también, el que falló es el de atrás.
 *
 * <p>Antes estos errores salían como 400, y quien llamaba no podía distinguir «pediste mal» de
 * «Logística está caída». El mensaje nombra el módulo para que se sepa a quién mirar.
 */
public class ModuloNoDisponibleException extends RuntimeException {

  private static final int MAXIMO_DETALLE = 150;

  private final String modulo;

  public ModuloNoDisponibleException(String modulo, String mensaje, Throwable causa) {
    super(mensaje, causa);
    this.modulo = modulo;
  }

  public String getModulo() {
    return modulo;
  }

  /**
   * Traduce una caída o un 5xx del módulo indicado; cualquier otro error se devuelve tal cual,
   * porque un 4xx tiene un significado propio que decide cada cliente.
   */
  public static RuntimeException siEsCaida(String modulo, RuntimeException error) {
    if (error instanceof ResourceAccessException sinRespuesta) {
      // La causa más específica distingue un timeout de una conexión rechazada.
      return new ModuloNoDisponibleException(
          modulo,
          "El módulo " + modulo + " no respondió ("
              + sinRespuesta.getMostSpecificCause().getMessage() + ")",
          error);
    }
    if (error instanceof HttpServerErrorException errorDelServidor) {
      return new ModuloNoDisponibleException(
          modulo,
          "El módulo " + modulo + " respondió con error "
              + errorDelServidor.getStatusCode().value()
              + resumir(errorDelServidor.getResponseBodyAsString()),
          error);
    }
    return error;
  }

  /**
   * Un pedazo del cuerpo del error ayuda a diagnosticar (por ejemplo, la excepción que tiró el
   * otro módulo), pero entero puede ser una página HTML o un stack trace largo.
   */
  private static String resumir(String cuerpo) {
    if (cuerpo == null || cuerpo.isBlank()) {
      return "";
    }
    String recortado =
        cuerpo.length() > MAXIMO_DETALLE ? cuerpo.substring(0, MAXIMO_DETALLE) + "..." : cuerpo;
    return ": " + recortado;
  }
}
