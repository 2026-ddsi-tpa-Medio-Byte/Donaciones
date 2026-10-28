package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FechasUtcTest {

  @Test
  @DisplayName("Una fecha guardada en una máquina de Argentina sale tres horas más tarde, en UTC")
  void convierteConLaZonaDelSistema() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));

      Instant enUtc = FechasUtc.desdeLocal(LocalDateTime.of(2026, 10, 26, 15, 10, 0));

      assertEquals("2026-10-26T18:10:00.000Z", FechasUtc.formatear(enUtc));
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  @DisplayName("Con el sistema en UTC, como en Render, la hora no se mueve")
  void enUtcNoSeMueve() {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

      Instant enUtc = FechasUtc.desdeLocal(LocalDateTime.of(2026, 10, 26, 15, 10, 0, 123_456_789));

      assertEquals("2026-10-26T15:10:00.123Z", FechasUtc.formatear(enUtc));
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  @DisplayName("Sin fecha devuelve null en vez de fallar")
  void sinFecha() {
    assertNull(FechasUtc.desdeLocal(null));
  }

  @Test
  @DisplayName("ahora() tiene precisión de milisegundos")
  void ahoraEnMilisegundos() {
    assertEquals(0, FechasUtc.ahora().getNano() % 1_000_000);
  }
}
