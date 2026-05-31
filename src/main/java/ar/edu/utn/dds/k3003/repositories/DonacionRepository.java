package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Donacion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class DonacionRepository {

  private AtomicLong idCounter = new AtomicLong(0); // Para generar IDs numéricos
  private List<Donacion> donaciones = new ArrayList<>();

  public Donacion save(Donacion donacion) {
    // Si el ID es nulo, generamos uno nuevo.
    // Si NO es nulo, respetamos el que ya tiene (ej: "donacion1").
    if (donacion.getId() == null) {
      donacion.setId(idCounter.incrementAndGet());
    }
    this.donaciones.add(donacion);
    return donacion;
  }

  public Optional<Donacion> findById(String id) {
    // 1. Si el id es null o viene vacío, devolvemos Optional.empty() inmediatamente.
    // Esto evita que el stream intente procesar algo que no existe.
    if (id == null || id.isBlank()) return Optional.empty();

    // 2. Buscamos en la lista.
    // Usamos .trim() por si el test manda " 1" en lugar de "1".
    return this.donaciones.stream()
        .filter(d -> d.getId() != null && d.getId().toString().equals(id.trim()))
        .findFirst();
  }

  public List<Donacion> findByDonador(String donadorId) {
    return this.donaciones.stream().filter(d -> d.getDonadorId().equals(donadorId)).toList();
  }
}
