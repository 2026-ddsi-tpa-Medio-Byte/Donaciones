package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;

public class Identificador {
  private Long id;
  private TipoIdentificadorEnum tipo;
  private String descripcion;

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
