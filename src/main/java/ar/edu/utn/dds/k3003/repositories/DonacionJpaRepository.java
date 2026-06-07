package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.model.Donacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonacionJpaRepository extends JpaRepository<Donacion, Long> {
  List<Donacion> findByDonadorId(String donadorId);
}
