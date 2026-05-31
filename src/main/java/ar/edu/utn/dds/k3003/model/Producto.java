package ar.edu.utn.dds.k3003.model;

public class Producto {
  private Long id;
  private String nombre;
  private String descripcion;
  private String categoriaID;
  private Long identificadorID;
  private Subcategoria subcategoria;

  public Producto(String nombre, String descripcion, String categoriaID, Long identificadorID) {
    this.nombre = nombre;
    this.descripcion = descripcion;
    this.categoriaID = categoriaID;
    this.identificadorID = identificadorID;
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

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public Subcategoria getSubcategoria() {
    return subcategoria;
  }

  public void setSubcategoria(Subcategoria subcategoria) {
    this.subcategoria = subcategoria;
  }

  public String getCategoriaID() {
    return categoriaID;
  }

  public Long getIdentificadorID() {
    return identificadorID;
  }
}
