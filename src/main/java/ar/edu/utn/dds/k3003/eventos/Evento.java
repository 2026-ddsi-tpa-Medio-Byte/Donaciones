package ar.edu.utn.dds.k3003.eventos;

import java.time.Instant;

/**
 * El sobre común a los eventos de los cuatro módulos. Se serializa tal cual, en este orden.
 *
 * <p>También es el evento de aplicación que la {@code Fachada} entrega a Spring: así el envío a
 * RabbitMQ espera a que la transacción confirme (ver {@link PublicadorEventos}).
 *
 * @param eventoId UUID único; Consultas lo usa para descartar duplicados
 * @param entidadId el documento a actualizar en Mongo: el id del producto o de la donación, o
 *     {@code "*"} en el reset
 * @param ocurridoEn cuándo pasó el cambio, en UTC; Consultas se queda con la versión más nueva
 * @param datos el estado completo del objeto después del cambio, nunca la diferencia
 */
public record Evento(
    String eventoId,
    TipoEvento tipo,
    String modulo,
    String entidadId,
    Instant ocurridoEn,
    Object datos) {}
