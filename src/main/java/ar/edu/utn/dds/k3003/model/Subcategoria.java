package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class Subcategoria {
  private Long id;
  private String nombre;
  public Subcategoria() {}

  public Subcategoria(Long id, String nombre) {
    this.id = id;
    this.nombre = nombre;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }
}
