package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Producto {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String nombre;
  private String descripcion;
  private String categoriaID;

  /**
   * Texto libre y opcional, igual que la categoría. Lo agregó la Entrega 6 porque las consultas
   * de urgencias y de calidad filtran por categoría <b>y</b> subcategoría. Es un dato, no una
   * regla: no se valida. Con {@code ddl-auto=update} la columna se crea sola en Render, y los
   * productos que ya existían quedan sin subcategoría.
   */
  private String subcategoria;

  private Long identificadorID;

  public Producto() {}

  public Producto(String nombre, String descripcion, String categoriaID, Long identificadorID) {
    this(nombre, descripcion, categoriaID, null, identificadorID);
  }

  public Producto(
      String nombre,
      String descripcion,
      String categoriaID,
      String subcategoria,
      Long identificadorID) {
    this.nombre = nombre;
    this.descripcion = descripcion;
    this.categoriaID = categoriaID;
    this.subcategoria = subcategoria;
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

  public String getCategoriaID() {
    return categoriaID;
  }

  public String getSubcategoria() {
    return subcategoria;
  }

  public Long getIdentificadorID() {
    return identificadorID;
  }
}
