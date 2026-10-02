package ar.edu.utn.dds.k3003.exceptions;

/**
 * El donador existe pero Donadores dice que no puede donar (por ejemplo, quedó baneado por
 * quejas). Se responde 403: el pedido es válido, lo que falta es el permiso.
 *
 * <p>El mensaje tiene que seguir conteniendo «No puede donar»: el bot de Telegram lo busca en el
 * cuerpo de la respuesta para explicarle al usuario por qué lo rechazaron.
 */
public class DonadorNoHabilitadoException extends RuntimeException {
  public DonadorNoHabilitadoException(String mensaje) {
    super(mensaje);
  }
}
