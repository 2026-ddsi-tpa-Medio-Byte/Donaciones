package ar.edu.utn.dds.k3003.eventos;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;

/**
 * Las fechas de los eventos: siempre UTC, con milisegundos y {@code Z}.
 *
 * <p>Consultas calcula tiempos entre eventos de módulos distintos (por ejemplo, de la donación a
 * la entrega). Una fecha sin zona no se puede comparar con la de otro módulo, por eso el contrato
 * exige {@code Z} también dentro de {@code datos}.
 */
public final class FechasUtc {

  /**
   * Siempre tres decimales, también cuando los milisegundos son cero. El formato por defecto de
   * {@code Instant} los omite en ese caso, y entonces dos fechas del mismo segundo dejarían de
   * ordenarse bien si alguien las compara como texto.
   */
  private static final DateTimeFormatter ISO_CON_MILISEGUNDOS =
      new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

  private FechasUtc() {}

  /** El instante actual, con la precisión que viaja en los eventos. */
  public static Instant ahora() {
    return Instant.now().truncatedTo(ChronoUnit.MILLIS);
  }

  /**
   * Las fechas de la donación y de su historial se guardan como {@code LocalDateTime}, sin zona,
   * armadas con {@code LocalDateTime.now()}: están en la zona del sistema. Se convierten con esa
   * misma zona. En Render el sistema corre en UTC, así que ahí la hora no se mueve; en una máquina
   * en Argentina se le suman las tres horas que corresponden, en vez de publicarla corrida.
   */
  public static Instant desdeLocal(LocalDateTime fecha) {
    if (fecha == null) {
      return null;
    }
    return fecha.atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.MILLIS);
  }

  /** Por ejemplo {@code 2026-10-26T18:32:10.123Z}. */
  public static String formatear(Instant instante) {
    return ISO_CON_MILISEGUNDOS.format(instante);
  }
}
