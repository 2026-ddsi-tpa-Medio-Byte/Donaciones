package ar.edu.utn.dds.k3003.eventos;

import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacion;
import ar.edu.utn.dds.k3003.model.Producto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arma los eventos de Donaciones con la forma del contrato ({@code CONTRATO-EVENTOS.md}).
 *
 * <p>Cada evento lleva el estado completo del objeto, copiado en el momento de armarlo: Consultas
 * hace un upsert por id que reemplaza el documento entero, así que un dato que no viaje se pierde
 * en Mongo. Los ids van como texto porque así los usan los otros módulos ({@code "15"}, no
 * {@code 15}).
 */
public final class EventosDonaciones {

  public static final String MODULO = "donaciones";

  /** El {@code entidadId} de un reset: Consultas borra todo lo del módulo anterior al evento. */
  public static final String TODAS_LAS_ENTIDADES = "*";

  public record DatosProducto(
      String id,
      String nombre,
      String descripcion,
      String categoria,
      String subcategoria,
      String identificadorID) {}

  /** La fecha de entrega no va aparte: es la del {@code ACEPTADA} en el historial. */
  public record DatosDonacion(
      String id,
      String donadorID,
      String depositoID,
      String productoID,
      Integer cantidad,
      String descripcion,
      String estado,
      Instant fechaIngreso,
      List<CambioDeEstado> historialEstados) {}

  public record CambioDeEstado(String estado, Instant fecha) {}

  public record DatosDonacionEliminada(String id) {}

  private EventosDonaciones() {}

  public static Evento producto(TipoEvento tipo, Producto producto, Instant ocurridoEn) {
    String id = texto(producto.getId());
    return evento(
        tipo,
        id,
        ocurridoEn,
        new DatosProducto(
            id,
            producto.getNombre(),
            producto.getDescripcion(),
            producto.getCategoriaID(),
            producto.getSubcategoria(),
            texto(producto.getIdentificadorID())));
  }

  public static Evento donacion(TipoEvento tipo, Donacion donacion, Instant ocurridoEn) {
    String id = texto(donacion.getId());
    return evento(
        tipo,
        id,
        ocurridoEn,
        new DatosDonacion(
            id,
            donacion.getDonadorId(),
            donacion.getDepositoId(),
            donacion.getProductoId(),
            donacion.getCantidad(),
            donacion.getDescripcion(),
            donacion.getEstado() == null ? null : donacion.getEstado().name(),
            FechasUtc.desdeLocal(donacion.getFecha()),
            historial(donacion.getHistorialEstados())));
  }

  public static Evento donacionEliminada(Long donacionId, Instant ocurridoEn) {
    String id = texto(donacionId);
    return evento(TipoEvento.DONACION_ELIMINADA, id, ocurridoEn, new DatosDonacionEliminada(id));
  }

  public static Evento moduloReseteado(Instant ocurridoEn) {
    return evento(TipoEvento.MODULO_RESETEADO, TODAS_LAS_ENTIDADES, ocurridoEn, Map.of());
  }

  /** Se copia a una lista nueva: el evento no puede cambiar si la entidad cambia después. */
  private static List<CambioDeEstado> historial(List<EstadoDonacion> historial) {
    if (historial == null) {
      return List.of();
    }
    return historial.stream()
        .map(
            cambio ->
                new CambioDeEstado(
                    cambio.getEstado() == null ? null : cambio.getEstado().name(),
                    FechasUtc.desdeLocal(cambio.getFecha())))
        .toList();
  }

  private static Evento evento(TipoEvento tipo, String entidadId, Instant ocurridoEn, Object datos) {
    return new Evento(UUID.randomUUID().toString(), tipo, MODULO, entidadId, ocurridoEn, datos);
  }

  private static String texto(Long id) {
    return id == null ? null : id.toString();
  }
}
