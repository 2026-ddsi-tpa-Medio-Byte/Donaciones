package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoJpaRepository extends JpaRepository<Producto, Long> {

  /** Un producto no puede repetir el nombre de otro ya registrado. */
  boolean existsByNombreIgnoreCase(String nombre);
}
