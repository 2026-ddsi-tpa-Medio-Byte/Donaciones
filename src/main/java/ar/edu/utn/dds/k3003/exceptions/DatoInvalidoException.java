package ar.edu.utn.dds.k3003.exceptions;

/** El pedido vino incompleto o mal formado. Se responde 400 con un mensaje que nombra el dato. */
public class DatoInvalidoException extends RuntimeException {
  public DatoInvalidoException(String mensaje) {
    super(mensaje);
  }
}
