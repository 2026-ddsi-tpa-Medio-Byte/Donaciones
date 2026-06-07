package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class EstadoDonacion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private EstadoDonacionEnum estado;
  private LocalDateTime fecha;

  public EstadoDonacion() {}

  public EstadoDonacion(EstadoDonacionEnum estado, LocalDateTime fecha) {
    this.estado = estado;
    this.fecha = fecha;
  }

  public EstadoDonacionEnum getEstado() {
    return this.estado;
  }

  public void setEstado(EstadoDonacionEnum estado) {
    this.estado = estado;
  }

  public LocalDateTime getFecha() {
    return this.fecha;
  }

  public void setFecha(LocalDateTime fecha) {
    this.fecha = fecha;
  }
}
