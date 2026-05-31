package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.Fachada;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DonadoresYEntidadesTest {

  Fachada instancia;

  @SneakyThrows
  @BeforeEach
  void setUp() {
    instancia = new Fachada();
  }

  @Test
  void testSiempreTrue() {
    Assertions.assertTrue(true);
  }

  @Test
  void testSiempreEquals() {
    Assertions.assertEquals(1, 1);
  }
}
