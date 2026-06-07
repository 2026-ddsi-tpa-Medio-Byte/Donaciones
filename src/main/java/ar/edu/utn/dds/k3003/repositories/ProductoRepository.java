package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Producto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class ProductoRepository {
  private AtomicLong idCounter = new AtomicLong(0); // Para generar IDs numéricos
  private List<Producto> productos = new ArrayList<>();

  public Producto save(Producto producto) {
    if (producto.getId() == null) {
      producto.setId(idCounter.incrementAndGet());
    }
    this.productos.add(producto);
    return producto;
  }

  public Optional<Producto> findById(String id) {
    if (id == null || id.isBlank()) return Optional.empty();
    return this.productos.stream()
        .filter(d -> d.getId() != null && d.getId().toString().equals(id.trim()))
        .findFirst();
  }

  public void deleteAll() {
    this.productos.clear();
  }
}
