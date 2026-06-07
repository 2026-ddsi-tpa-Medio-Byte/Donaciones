package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Identificador;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class IdentificadorRepository {
  private final AtomicLong idCounter = new AtomicLong(0);
  private final List<Identificador> identificadores = new ArrayList<>();

  public Identificador save(Identificador identificador) {
    if (identificador.getId() == null) {
      identificador.setId(idCounter.incrementAndGet());
    }
    this.identificadores.add(identificador);
    return identificador;
  }

  public Optional<Identificador> findById(String id) {
    if (id == null || id.isBlank()) return Optional.empty();
    return this.identificadores.stream()
        .filter(i -> i.getId() != null && i.getId().toString().equals(id.trim()))
        .findFirst();
  }

  public void deleteAll() {
    this.identificadores.clear();
  }
}
