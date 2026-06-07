package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Identificador {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private TipoIdentificadorEnum tipo;
  private String descripcion;

  public Identificador() {}

  public Identificador(TipoIdentificadorEnum tipo, String descripcion) {
    this.tipo = tipo;
    this.descripcion = descripcion;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public TipoIdentificadorEnum getTipo() {
    return tipo;
  }

  public void setTipo(TipoIdentificadorEnum tipo) {
    this.tipo = tipo;
  }

  public String getDescripcion() {
    return descripcion;
  }
}
