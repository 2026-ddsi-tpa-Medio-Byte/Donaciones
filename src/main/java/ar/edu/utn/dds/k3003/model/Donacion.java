package ar.edu.utn.dds.k3003.model;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Donacion {
  private Long id;
  private String donadorId;
  private Integer cantidad;
  private String productoId;
  private String descripcion;

  private EstadoDonacionEn estado;
  private LocalDateTime fecha;

  private List<EstadoDonacion> historialEstados = new ArrayList<>();

  public Donacion(String donadorId, Integer cantidad, String descripcion, String productoId) {
    this.donadorId = donadorId;
    this.cantidad = cantidad;
    this.productoId = productoId;
    this.descripcion = descripcion;
    this.estado = EstadoDonacionEn.INGRESADA;

    this.fecha = LocalDateTime.now();

    EstadoDonacionEnum estadoAuditable = EstadoDonacionEnum.valueOf(this.estado.name());
    this.historialEstados.add(new EstadoDonacion(estadoAuditable, this.fecha));
  }

  // Getters y Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getDonadorId() {
    return donadorId;
  }

  public Integer getCantidad() {
    return cantidad;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public EstadoDonacionEn getEstado() {
    return estado;
  }

  public void setEstado(EstadoDonacionEn estado) {
    this.estado = estado;
  }

  public LocalDateTime getFecha() {
    return fecha;
  }

  public List<EstadoDonacion> getHistorialEstados() {
    return historialEstados;
  }

  public void setHistorialEstados(EstadoDonacionEn estadoNuevo) {
    this.estado = estadoNuevo;

    EstadoDonacionEnum estadoAuditable = EstadoDonacionEnum.valueOf(estadoNuevo.name());
    EstadoDonacion nuevaAuditoria = new EstadoDonacion(estadoAuditable, LocalDateTime.now());

    // Lo sumamos al historial
    this.historialEstados.add(nuevaAuditoria);
  }

  public String getProductoId() {
    return productoId;
  }

  public void setProductoId(String productoId) {
    this.productoId = productoId;
  }
}
