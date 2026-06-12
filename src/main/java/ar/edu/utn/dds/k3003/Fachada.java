package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacionEn;
import ar.edu.utn.dds.k3003.repositories.DonacionJpaRepository;
import ar.edu.utn.dds.k3003.repositories.DonacionRepository;
import ar.edu.utn.dds.k3003.repositories.IdentificadorJpaRepository;
import ar.edu.utn.dds.k3003.repositories.IdentificadorRepository;
import ar.edu.utn.dds.k3003.repositories.ProductoJpaRepository;
import ar.edu.utn.dds.k3003.repositories.ProductoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Fachada implements FachadaDonaciones {

  private final DonacionRepository inMemoryDonacionRepository;
  private final ProductoRepository inMemoryProductoRepository;
  private final IdentificadorRepository inMemoryIdentificadorRepository;

  private final DonacionJpaRepository donacionJpaRepository;
  private final ProductoJpaRepository productoJpaRepository;
  private final IdentificadorJpaRepository identificadorJpaRepository;

  private final MetricasService metricasService;

  private FachadaLogistica fachadaLogistica;
  private FachadaDonadoresYEntidades fachadaDonadores;

  public Fachada() {
    this.inMemoryDonacionRepository = new DonacionRepository();
    this.inMemoryProductoRepository = new ProductoRepository();
    this.inMemoryIdentificadorRepository = new IdentificadorRepository();
    this.donacionJpaRepository = null;
    this.productoJpaRepository = null;
    this.identificadorJpaRepository = null;
    this.metricasService = null;
    this.fachadaDonadores = null;
    this.fachadaLogistica = null;
  }

  @Autowired
  public Fachada(
      DonacionJpaRepository donacionJpaRepository,
      ProductoJpaRepository productoJpaRepository,
      IdentificadorJpaRepository identificadorJpaRepository,
      FachadaDonadoresYEntidades fachadaDonadores,
      FachadaLogistica fachadaLogistica,
      MetricasService metricasService) {
    this.inMemoryDonacionRepository = null;
    this.inMemoryProductoRepository = null;
    this.inMemoryIdentificadorRepository = null;
    this.donacionJpaRepository = donacionJpaRepository;
    this.productoJpaRepository = productoJpaRepository;
    this.identificadorJpaRepository = identificadorJpaRepository;
    this.fachadaDonadores = fachadaDonadores;
    this.fachadaLogistica = fachadaLogistica;
    this.metricasService = metricasService;
  }

  private Optional<Long> parseLongId(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(id.trim()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private Donacion saveDonacion(Donacion donacion) {
    if (donacionJpaRepository != null) {
      return donacionJpaRepository.save(donacion);
    }
    return inMemoryDonacionRepository.save(donacion);
  }

  private Optional<Donacion> findDonacionById(String id) {
    if (donacionJpaRepository != null) {
      return parseLongId(id).flatMap(donacionJpaRepository::findById);
    }
    return inMemoryDonacionRepository.findById(id);
  }

  private List<Donacion> findDonacionesByDonador(String donadorId) {
    if (donacionJpaRepository != null) {
      return donacionJpaRepository.findByDonadorId(donadorId);
    }
    return inMemoryDonacionRepository.findByDonador(donadorId);
  }

  private List<Donacion> findAllDonaciones() {
    if (donacionJpaRepository != null) {
      return donacionJpaRepository.findAll();
    }
    return inMemoryDonacionRepository.findAll();
  }

  private ar.edu.utn.dds.k3003.model.Identificador findIdentificadorById(String id) {
    if (identificadorJpaRepository != null) {
      return parseLongId(id)
          .flatMap(identificadorJpaRepository::findById)
          .orElseThrow(() -> new NoSuchElementException("Identificador no encontrado: " + id));
    }
    return inMemoryIdentificadorRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Identificador no encontrado: " + id));
  }

  private ar.edu.utn.dds.k3003.model.Producto findProductoById(String id) {
    if (productoJpaRepository != null) {
      return parseLongId(id)
          .flatMap(productoJpaRepository::findById)
          .orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + id));
    }
    return inMemoryProductoRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + id));
  }

  private void registrarMetricasError() {
    if (metricasService != null) {
      metricasService.incrementarDonacionesErrores();
    }
  }

  /*------------------------------------------------------------Entrega 1--------------------------------------------------------------------------------- */

  public DonacionDTO registrarDonacion(DonacionDTO donacionDTO) {
    if (donacionDTO == null) {
      if (metricasService != null) metricasService.incrementarDonacionesErrores();
      throw new RuntimeException("DTO nulo");
    }

    if (donacionDTO.id() != null) {
      if (metricasService != null) metricasService.incrementarDonacionesErrores();
      throw new RuntimeException("La donación ya tiene un ID asignado.");
    }

    try {
      this.fachadaDonadores.buscarDonadorPorID(donacionDTO.donadorID());

      if (!this.fachadaDonadores.puedeDonar(donacionDTO.donadorID())) {
        if (metricasService != null) metricasService.incrementarDonacionesErrores();
        throw new RuntimeException("No puede donar");
      }

      Donacion nuevaDonacion = new Donacion(
          donacionDTO.donadorID(),
          donacionDTO.cantidad(),
          donacionDTO.depositoID(),
          donacionDTO.descripcion());

      Donacion guardada = saveDonacion(nuevaDonacion);

      this.fachadaLogistica.gestionarDonacion(
          donacionDTO.depositoID(),
          guardada.getId().toString(),
          donacionDTO.productoID(),
          donacionDTO.cantidad());

      if (metricasService != null) {
        metricasService.incrementarDonacionesRegistradas();
      }
      return mapearADTO(guardada);

    } catch (RuntimeException e) {
      if (metricasService != null) {
        metricasService.incrementarDonacionesErrores();
      }
      throw e;
    }
  }

  @Override
  public DonacionDTO buscarDonacionPorID(String donacionID) {
    return findDonacionById(donacionID)
        .map(this::mapearADTO)
        .orElseThrow(() -> new RuntimeException("No existe la donación"));
  }

  @Override
  public DonacionDTO cambiarEstadoDeDonacion(String donacionID, EstadoDonacionEnum estado) {
    if (estado == null) {
      throw new RuntimeException("El estado no puede ser nulo.");
    }

    Donacion donacion =
        findDonacionById(donacionID).orElseThrow(() -> new RuntimeException("Test: ID no encontrado"));
    donacion.setEstado(EstadoDonacionEn.valueOf(estado.name()));
    Donacion actualizada = saveDonacion(donacion);
    if (metricasService != null) {
      metricasService.incrementarDonacionesCambioEstado();
    }
    return mapearADTO(actualizada);
  }

  @Override
  public List<DonacionDTO> buscarPorDonadorYFechaInicio(String donadorID, LocalDate fecha) {
    List<Donacion> donaciones = findDonacionesByDonador(donadorID);

    if (donaciones == null || donaciones.isEmpty()) {
      throw new RuntimeException("No hay donaciones para el donador: " + donadorID);
    }

    return donaciones.stream()
        .filter(d -> !d.getFecha().toLocalDate().isBefore(fecha))
        .map(this::mapearADTO)
        .collect(Collectors.toList());
  }

  public List<DonacionDTO> buscarTodasDonaciones() {
    return findAllDonaciones().stream().map(this::mapearADTO).collect(Collectors.toList());
  }

  @Override
  public DonacionDTO registrarQuejaEnDonacion(String donacionID, String descripcion) {
    Donacion donacion =
        findDonacionById(donacionID).orElseThrow(() -> new RuntimeException("No existe"));

    QuejaDTO quejaDTO = new QuejaDTO(null, donacionID, donacion.getDonadorId(), null, descripcion);
    this.fachadaDonadores.agregarQueja(quejaDTO);
    cambiarEstadoDeDonacion(donacion.getId().toString(), EstadoDonacionEnum.CONQUEJA);
    if (metricasService != null) {
      metricasService.incrementarDonacionesQuejas();
    }
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

  public void resetBaseDeDatos() {
    if (donacionJpaRepository != null) {
      productoJpaRepository.deleteAll();
      identificadorJpaRepository.deleteAll();
      donacionJpaRepository.deleteAll();
      return;
    }
    inMemoryProductoRepository.deleteAll();
    inMemoryIdentificadorRepository.deleteAll();
    inMemoryDonacionRepository.deleteAll();
  }

  public String seedBaseDeDatos() {
    resetBaseDeDatos();

    IdentificadorDTO identificadorDTO =
        agregarIdentificador(
            new IdentificadorDTO(
                null,
                ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum.CODIGODEBARRAS,
                "Identificador semilla"));

    agregarProducto(
        new ProductoDTO(
            null,
            "Arroz",
            "Arroz blanco largo fino",
            "alimentos",
            identificadorDTO.id()));

    return "Datos de prueba cargados correctamente";
  }

  /*------------------------------------------------------------Entrega 2--------------------------------------------------------------------------------- */

  @Override
  public IdentificadorDTO agregarIdentificador(IdentificadorDTO dto) {
    ar.edu.utn.dds.k3003.model.Identificador identificador =
        new ar.edu.utn.dds.k3003.model.Identificador(dto.tipo(), dto.descripcion());
    ar.edu.utn.dds.k3003.model.Identificador guardado =
        identificadorJpaRepository != null
            ? identificadorJpaRepository.save(identificador)
            : inMemoryIdentificadorRepository.save(identificador);
    return new IdentificadorDTO(
        guardado.getId().toString(), guardado.getTipo(), guardado.getDescripcion());
  }

  @Override
  public IdentificadorDTO buscarIdentificadorPorID(String identificadorID) {
    ar.edu.utn.dds.k3003.model.Identificador identificador = findIdentificadorById(identificadorID);
    return new IdentificadorDTO(
        identificador.getId().toString(), identificador.getTipo(), identificador.getDescripcion());
  }

  @Override
  public ProductoDTO agregarProducto(ProductoDTO dto) {
    ar.edu.utn.dds.k3003.model.Identificador identificador = findIdentificadorById(dto.identificadorID());
    validarProducto(dto, identificador);

    ar.edu.utn.dds.k3003.model.Producto producto =
        new ar.edu.utn.dds.k3003.model.Producto(
            dto.nombre(), dto.descripcion(), dto.categoriaID(), identificador.getId());
    ar.edu.utn.dds.k3003.model.Producto guardado =
        productoJpaRepository != null
            ? productoJpaRepository.save(producto)
            : inMemoryProductoRepository.save(producto);

    return new ProductoDTO(
        guardado.getId().toString(),
        guardado.getNombre(),
        guardado.getDescripcion(),
        guardado.getCategoriaID(),
        dto.identificadorID());
  }

  @Override
  public ProductoDTO buscarProductoPorID(String productoID) {
    ar.edu.utn.dds.k3003.model.Producto producto = findProductoById(productoID);
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
