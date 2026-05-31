package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.*;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacionEn;
import ar.edu.utn.dds.k3003.repositories.DonacionRepository;
import ar.edu.utn.dds.k3003.repositories.IdentificadorRepository;
import ar.edu.utn.dds.k3003.repositories.ProductoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

public class Fachada implements FachadaDonaciones {

  private final DonacionRepository donacionRepository;
  private final ProductoRepository productoRepository;
  private final IdentificadorRepository identificadorRepository;

  private FachadaLogistica fachadaLogistica;
  private FachadaDonadoresYEntidades fachadaDonadores;

  public Fachada() {
    this.donacionRepository = new DonacionRepository();
    this.productoRepository = new ProductoRepository();
    this.identificadorRepository = new IdentificadorRepository();
  }

  /*------------------------------------------------------------Entrega 1--------------------------------------------------------------------------------- */

  public DonacionDTO registrarDonacion(DonacionDTO donacionDTO) {
    if (donacionDTO == null) throw new RuntimeException("DTO nulo");

    // REGLA 1: Si el DTO ya trae ID, el test espera que falle.
    // Esto arregla el error "testRegistrarDonacionFallido"
    if (donacionDTO.id() != null) {
      throw new RuntimeException("La donación ya tiene un ID asignado.");
    }

    // Validaciones de fachadas
    this.fachadaDonadores.buscarDonadorPorID(donacionDTO.donadorID());

    if (!this.fachadaDonadores.puedeDonar(donacionDTO.donadorID())) {
      throw new RuntimeException("No puede donar");
    }

    // Logística antes que puedeDonar
    this.fachadaLogistica.gestionarDonacion(
        donacionDTO.donadorID(),
        donacionDTO.depositoID(),
        donacionDTO.productoID(),
        donacionDTO.cantidad());

    // Crear modelo
    Donacion nuevaDonacion =
        new Donacion(
            donacionDTO.donadorID(),
            donacionDTO.cantidad(),
            donacionDTO.depositoID(),
            donacionDTO.descripcion());

    // Guardar (Aquí el repo le pondrá el ID "donacion1" si el test lo pre-configuró)
    Donacion guardada = this.donacionRepository.save(nuevaDonacion);
    return mapearADTO(guardada);
  }

  @Override
  public DonacionDTO buscarDonacionPorID(String donacionID) {
    return donacionRepository
        .findById(donacionID)
        .map(this::mapearADTO)
        .orElseThrow(() -> new RuntimeException("No existe la donación"));
  }

  @Override
  public DonacionDTO cambiarEstadoDeDonacion(String donacionID, EstadoDonacionEnum estado) {

    if (estado == null) {
      throw new RuntimeException("El estado no puede ser nulo.");
    }

    Donacion donacion =
        this.donacionRepository
            .findById(donacionID)
            .orElseThrow(() -> new RuntimeException("Test: ID no encontrado"));

    donacion.setEstado(EstadoDonacionEn.valueOf(estado.name()));
    return mapearADTO(donacion);
  }

  @Override
  public List<DonacionDTO> buscarPorDonadorYFechaInicio(String donadorID, LocalDate fecha) {
    List<Donacion> donaciones = this.donacionRepository.findByDonador(donadorID);

    if (donaciones == null || donaciones.isEmpty()) {
      throw new RuntimeException("No hay donaciones para el donador: " + donadorID);
    }

    return donaciones.stream()
        .filter(d -> !d.getFecha().toLocalDate().isBefore(fecha))
        .map(this::mapearADTO)
        .collect(Collectors.toList());
  }

  @Override
  public DonacionDTO registrarQuejaEnDonacion(String donacionID, String descripcion) {
    Donacion donacion =
        this.donacionRepository
            .findById(donacionID)
            .orElseThrow(() -> new RuntimeException("No existe"));

    // 1. Primero armamos el DTO
    QuejaDTO quejaDTO = new QuejaDTO(null, donacionID, donacion.getDonadorId(), null, descripcion);

    this.fachadaDonadores.agregarQueja(quejaDTO);

    // 3. SI Y SOLO SI la queja se registró bien, cambiamos el estado
    cambiarEstadoDeDonacion(donacion.getId().toString(), EstadoDonacionEnum.CONQUEJA);

    return mapearADTO(donacion);
  }

  @Override
  public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades) {
    this.fachadaDonadores = fachadaDonadoresYEntidades;
  }

  @Override
  public void setFachadaLogistica(FachadaLogistica fachadaLogistica) {
    this.fachadaLogistica = fachadaLogistica;
  }

  /*------------------------------------------------------------Entrega 2--------------------------------------------------------------------------------- */

  @Override
  public IdentificadorDTO agregarIdentificador(IdentificadorDTO dto) {
    ar.edu.utn.dds.k3003.model.Identificador identificador =
        new ar.edu.utn.dds.k3003.model.Identificador(dto.tipo(), dto.descripcion());
    ar.edu.utn.dds.k3003.model.Identificador guardado =
        this.identificadorRepository.save(identificador);
    return new IdentificadorDTO(
        guardado.getId().toString(), guardado.getTipo(), guardado.getDescripcion());
  }

  @Override
  public IdentificadorDTO buscarIdentificadorPorID(String identificadorID) {
    ar.edu.utn.dds.k3003.model.Identificador identificador =
        this.identificadorRepository
            .findById(identificadorID)
            .orElseThrow(
                () ->
                    new NoSuchElementException("Identificador no encontrado: " + identificadorID));
    return new IdentificadorDTO(
        identificador.getId().toString(), identificador.getTipo(), identificador.getDescripcion());
  }

  @Override
  public ProductoDTO agregarProducto(ProductoDTO dto) {
    ar.edu.utn.dds.k3003.model.Identificador identificador =
        this.identificadorRepository
            .findById(dto.identificadorID())
            .orElseThrow(() -> new NoSuchElementException("Identificador no encontrado"));

    validarProducto(dto, identificador);

    ar.edu.utn.dds.k3003.model.Producto producto =
        new ar.edu.utn.dds.k3003.model.Producto(
            dto.nombre(), dto.descripcion(), dto.categoriaID(), identificador.getId());
    ar.edu.utn.dds.k3003.model.Producto guardado = this.productoRepository.save(producto);

    return new ProductoDTO(
        guardado.getId().toString(),
        guardado.getNombre(),
        guardado.getDescripcion(),
        guardado.getCategoriaID(),
        dto.identificadorID());
  }

  @Override
  public ProductoDTO buscarProductoPorID(String productoID) {
    ar.edu.utn.dds.k3003.model.Producto producto =
        this.productoRepository
            .findById(productoID)
            .orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + productoID));
    return new ProductoDTO(
        producto.getId().toString(),
        producto.getNombre(),
        producto.getDescripcion(),
        producto.getCategoriaID(),
        producto.getIdentificadorID().toString());
  }

  private void validarProducto(
      ProductoDTO dto, ar.edu.utn.dds.k3003.model.Identificador identificador) {
    switch (identificador.getTipo()) {
      case CODIGODEBARRAS -> {
        String[] palabras = dto.descripcion().trim().split("\\s+");
        if (palabras.length < 3) {
          throw new RuntimeException(
              "Descripción debe tener al menos 3 palabras para CODIGODEBARRAS");
        }
      }
      case QR -> {
        long cantLetras = dto.nombre().chars().filter(Character::isLetter).count();
        if (cantLetras % 2 != 0) {
          throw new RuntimeException("Nombre debe tener cantidad par de letras para QR");
        }
      }
    }
  }

  /*------------------------------------------------------------Mapper--------------------------------------------------------------------------------- */

  private DonacionDTO mapearADTO(Donacion d) {
    return new DonacionDTO(
        d.getId().toString(),
        d.getDonadorId(),
        null,
        d.getDescripcion(),
        null,
        d.getCantidad(),
        EstadoDonacionEnum.valueOf(d.getEstado().name()));
  }
}
