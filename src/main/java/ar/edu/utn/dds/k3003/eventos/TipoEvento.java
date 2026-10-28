package ar.edu.utn.dds.k3003.eventos;

/**
 * Los eventos que publica Donaciones, con su routing key. Es el catálogo de Donaciones en
 * {@code guias-entrega-6/CONTRATO-EVENTOS.md}: si cambia acá, tiene que cambiar allá.
 *
 * <p>La routing key se escribe entera y no se deriva del nombre: el formato
 * {@code <modulo>.<objeto>.<acción>} lo definió Logística, y una regla de derivación que algún día
 * no coincida rompería la suscripción de Consultas sin dar ningún error.
 */
public enum TipoEvento {
  PRODUCTO_CREADO("donaciones.producto.creado"),
  /** Hoy no hay forma de modificar un producto: sale solo de {@code POST /admin/republicar-eventos}. */
  PRODUCTO_MODIFICADO("donaciones.producto.modificado"),
  DONACION_REGISTRADA("donaciones.donacion.registrada"),
  /** Cada cambio de estado, y también la republicación: lleva la donación completa. */
  DONACION_ESTADO_CAMBIADO("donaciones.donacion.estado_cambiado"),
  /** Lo publica la compensación, cuando Logística rechaza la donación y se borra. */
  DONACION_ELIMINADA("donaciones.donacion.eliminada"),
  MODULO_RESETEADO("donaciones.modulo.reseteado");

  private final String routingKey;

  TipoEvento(String routingKey) {
    this.routingKey = routingKey;
  }

  public String routingKey() {
    return routingKey;
  }
}
