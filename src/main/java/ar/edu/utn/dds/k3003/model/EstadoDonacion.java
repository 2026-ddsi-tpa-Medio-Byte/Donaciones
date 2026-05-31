package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import java.time.LocalDateTime;

public class EstadoDonacion {
  private EstadoDonacionEnum estado;
  private LocalDateTime fecha;

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
