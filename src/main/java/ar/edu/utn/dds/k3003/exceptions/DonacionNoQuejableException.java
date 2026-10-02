package ar.edu.utn.dds.k3003.exceptions;

/**
 * La donación existe pero su estado no admite una queja: todavía no fue entregada, o ya tiene
 * una. Es un conflicto con el estado actual (409), no un pedido mal armado.
 */
public class DonacionNoQuejableException extends RuntimeException {
  public DonacionNoQuejableException(String mensaje) {
    super(mensaje);
  }
}
