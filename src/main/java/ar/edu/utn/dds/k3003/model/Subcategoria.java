package ar.edu.utn.dds.k3003.model;

public class Subcategoria {
  private Long id;
  private String nombre;
  private Categoria categoria; // La categoría a la que pertenece

  public Subcategoria(Long id, String nombre, Categoria categoria) {
    this.id = id;
    this.nombre = nombre;
    this.categoria = categoria;
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

  public Categoria getCategoria() {
    return categoria;
  }

  public void setCategoria(Categoria categoria) {
    this.categoria = categoria;
  }
}
