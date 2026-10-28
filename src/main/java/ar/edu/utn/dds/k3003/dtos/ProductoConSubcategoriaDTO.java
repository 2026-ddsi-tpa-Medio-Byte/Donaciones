package ar.edu.utn.dds.k3003.dtos;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;

/**
 * El producto tal como lo recibe y lo devuelve la API REST: los campos de {@link ProductoDTO} más
 * la subcategoría.
 *
 * <p>Existe porque {@code ProductoDTO} es de cátedra y no se puede tocar. Para quien ya usaba la
 * API no cambia nada: un POST sin {@code subcategoria} la deja vacía, y un campo de más en la
 * respuesta lo ignoran los clientes actuales (Donadores lee con el {@code ObjectMapper} de Spring,
 * que no falla por campos desconocidos; el bot y el MCP leen el JSON como árbol).
 */
public record ProductoConSubcategoriaDTO(
    String id,
    String nombre,
    String descripcion,
    String categoriaID,
    String subcategoria,
    String identificadorID) {

  /** Lo que llega por la interfaz de cátedra, que no tiene subcategoría. */
  public static ProductoConSubcategoriaDTO desde(ProductoDTO dto) {
    return new ProductoConSubcategoriaDTO(
        dto.id(), dto.nombre(), dto.descripcion(), dto.categoriaID(), null, dto.identificadorID());
  }

  /** Para responder por la interfaz de cátedra: la subcategoría se pierde porque no tiene dónde ir. */
  public ProductoDTO aProductoDTO() {
    return new ProductoDTO(id, nombre, descripcion, categoriaID, identificadorID);
  }
}
