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

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(Fachada.class);

  private final DonacionRepository inMemoryDonacionRepository;
  private final ProductoRepository inMemoryProductoRepository;
  private final IdentificadorRepository inMemoryIdentificadorRepository;

  private final DonacionJpaRepository donacionJpaRepository;
  private final ProductoJpaRepository productoJpaRepository;
  private final IdentificadorJpaRepository identificadorJpaRepository;

  private final MetricasService metricasService;

  /** Se usa para reiniciar las secuencias de IDs en el reset. Null con repositorios in-memory. */
  @jakarta.persistence.PersistenceContext
  private jakarta.persistence.EntityManager entityManager;

  /** Determina la sintaxis SQL del reset (PostgreSQL en producción, H2 en los tests). */
  @org.springframework.beans.factory.annotation.Value("${spring.jpa.database-platform:}")
  private String dialecto;

  private FachadaLogistica fachadaLogistica;
  private FachadaDonadoresYEntidades fachadaDonadores;

  public Fachada() {
    this.inMemoryDonacionRepository = new DonacionRepository();
    this.inMemoryProductoRepository = new ProductoRepository();
    this.inMemoryIdentificadorRepository = new IdentificadorRepository();
    this.donacionJpaRepository = null;
    this.productoJpaRepository = null;
    this.identificadorJpaRepository = null;
    this.metricasService =
        new MetricasService(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    ;
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

    metricasService.incrementarDonacionesErrores();
  }

  /*------------------------------------------------------------Entrega 1--------------------------------------------------------------------------------- */

  public DonacionDTO registrarDonacion(DonacionDTO donacionDTO) {
    if (donacionDTO == null) {
      metricasService.incrementarDonacionesErrores();
      throw new RuntimeException("DTO nulo");
    }

    if (donacionDTO.id() != null) {
      metricasService.incrementarDonacionesErrores();
      throw new RuntimeException("La donación ya tiene un ID asignado.");
    }

    // La consigna pide que la cantidad donada sea mayor a 0: sin esta validación se
    // registraban donaciones de 0 o negativas que después Logística tenía que descartar.
    if (donacionDTO.cantidad() == null || donacionDTO.cantidad() <= 0) {
      metricasService.incrementarDonacionesErrores();
      throw new RuntimeException("La cantidad donada debe ser mayor a 0");
    }

    try {
      log.info(
          "[Donaciones] Registrando donacion (donadorID={}, productoID={}, cantidad={})",
          donacionDTO.donadorID(),
          donacionDTO.productoID(),
          donacionDTO.cantidad());

      // Donadores ya valida el producto al registrar una necesidad; acá faltaba el mismo
      // control: sin esto se donaba contra un productoID inexistente y el fantasma llegaba
      // hasta Logística, que después no podía asignarlo a ninguna necesidad.
      if (donacionDTO.productoID() != null) {
        findProductoById(donacionDTO.productoID());
      }

      this.fachadaDonadores.buscarDonadorPorID(donacionDTO.donadorID());

      if (!this.fachadaDonadores.puedeDonar(donacionDTO.donadorID())) {
        metricasService.incrementarDonacionesErrores();
        log.warn("[Donaciones] Donador {} NO puede donar, rechazando", donacionDTO.donadorID());
        throw new RuntimeException("No puede donar");
      }

      Donacion nuevaDonacion =
          new Donacion(
              donacionDTO.donadorID(),
              donacionDTO.cantidad(),
              donacionDTO.depositoID(),
              donacionDTO.productoID(),
              donacionDTO.descripcion());

      Donacion guardada = saveDonacion(nuevaDonacion);
      log.info("[Donaciones] Donacion guardada en BD (id={})", guardada.getId());

      this.fachadaLogistica.gestionarDonacion(
          donacionDTO.depositoID(),
          guardada.getId().toString(),
          donacionDTO.productoID(),
          donacionDTO.cantidad());

      metricasService.incrementarDonacionesRegistradas();

      return mapearADTO(guardada);

    } catch (RuntimeException e) {

      metricasService.incrementarDonacionesErrores();
      log.error("[Donaciones] Error registrando donacion: {}", e.getMessage());

      throw e;
    }
  }

  @Override
  public DonacionDTO buscarDonacionPorID(String donacionID) {
    metricasService.incrementarDonacionesConsultas();
    return findDonacionById(donacionID)
        .map(this::mapearADTO)
        .orElseThrow(() -> new NoSuchElementException("No existe la donación: " + donacionID));
  }

  @Override
  public DonacionDTO cambiarEstadoDeDonacion(String donacionID, EstadoDonacionEnum estado) {
    if (estado == null) {
      throw new RuntimeException("El estado no puede ser nulo.");
    }

    log.info("[Donaciones] Cambiando estado de donacion id={} -> {}", donacionID, estado);

    Donacion donacion =
        findDonacionById(donacionID)
            .orElseThrow(() -> new NoSuchElementException("No existe la donación: " + donacionID));
    donacion.setEstado(EstadoDonacionEn.valueOf(estado.name()));
    Donacion actualizada = saveDonacion(donacion);

    metricasService.incrementarDonacionesCambioEstado();

    return mapearADTO(actualizada);
  }

  @Override
  public List<DonacionDTO> buscarPorDonadorYFechaInicio(String donadorID, LocalDate fecha) {
    metricasService.incrementarDonacionesConsultas();
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
    metricasService.incrementarDonacionesConsultas();
    return findAllDonaciones().stream().map(this::mapearADTO).collect(Collectors.toList());
  }

  @Override
  public DonacionDTO registrarQuejaEnDonacion(String donacionID, String descripcion) {
    log.info("[Donaciones] Registrando queja en donacion id={}", donacionID);
    Donacion donacion =
        findDonacionById(donacionID)
            .orElseThrow(() -> new NoSuchElementException("No existe la donación: " + donacionID));

    QuejaDTO quejaDTO = new QuejaDTO(null, donacionID, donacion.getDonadorId(), null, descripcion);
    this.fachadaDonadores.agregarQueja(quejaDTO);
    cambiarEstadoDeDonacion(donacion.getId().toString(), EstadoDonacionEnum.CONQUEJA);

    metricasService.incrementarDonacionesQuejas();

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

  private static final List<String> TABLAS_CON_SECUENCIA =
      List.of("estado_donacion", "donacion", "producto", "identificador");

  /**
   * Vacía la base y reinicia las secuencias de IDs, de modo que tras un reset la numeración vuelva
   * a empezar en 1. Un {@code deleteAll()} borra las filas pero deja la secuencia donde estaba, y
   * las altas siguientes continuaban desde el último ID usado.
   *
   * <p>La sentencia depende del motor: PostgreSQL admite truncar varias tablas de una vez, mientras
   * que H2 (usado en los tests) requiere reiniciar cada columna identidad por separado.
   */
  @org.springframework.transaction.annotation.Transactional
  public void resetBaseDeDatos() {
    if (donacionJpaRepository == null) {
      inMemoryProductoRepository.deleteAll();
      inMemoryIdentificadorRepository.deleteAll();
      inMemoryDonacionRepository.deleteAll();
      return;
    }

    if (entityManager == null) {
      borrarTodoConRepositorios();
      return;
    }

    if (dialecto != null && dialecto.contains("PostgreSQL")) {
      entityManager
          .createNativeQuery(
              "TRUNCATE TABLE " + String.join(", ", TABLAS_CON_SECUENCIA)
                  + " RESTART IDENTITY CASCADE")
          .executeUpdate();
    } else {
      borrarTodoConRepositorios();
      entityManager.flush();
      for (String tabla : TABLAS_CON_SECUENCIA) {
        entityManager
            .createNativeQuery("ALTER TABLE " + tabla + " ALTER COLUMN id RESTART WITH 1")
            .executeUpdate();
      }
    }

    log.info("[Donaciones] Base reseteada y secuencias de ID reiniciadas");
  }

  private void borrarTodoConRepositorios() {
    productoJpaRepository.deleteAll();
    identificadorJpaRepository.deleteAll();
    donacionJpaRepository.deleteAll();
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
            null, "Arroz", "Arroz blanco largo fino", "alimentos", identificadorDTO.id()));

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
    metricasService.incrementarIdentificadoresRegistrados();
    return new IdentificadorDTO(
        guardado.getId().toString(), guardado.getTipo(), guardado.getDescripcion());
  }

  @Override
  public IdentificadorDTO buscarIdentificadorPorID(String identificadorID) {
    ar.edu.utn.dds.k3003.model.Identificador identificador = findIdentificadorById(identificadorID);
    return new IdentificadorDTO(
        identificador.getId().toString(), identificador.getTipo(), identificador.getDescripcion());
  }

  public List<IdentificadorDTO> buscarTodosIdentificadores() {
    List<ar.edu.utn.dds.k3003.model.Identificador> identificadores =
        identificadorJpaRepository != null
            ? identificadorJpaRepository.findAll()
            : inMemoryIdentificadorRepository.findAll();
    return identificadores.stream()
        .map(i -> new IdentificadorDTO(i.getId().toString(), i.getTipo(), i.getDescripcion()))
        .collect(Collectors.toList());
  }

  @Override
  public ProductoDTO agregarProducto(ProductoDTO dto) {
    ar.edu.utn.dds.k3003.model.Identificador identificador =
        findIdentificadorById(dto.identificadorID());
    validarNombreDeProductoNoRepetido(dto.nombre());
    validarProducto(dto, identificador);

    ar.edu.utn.dds.k3003.model.Producto producto =
        new ar.edu.utn.dds.k3003.model.Producto(
            dto.nombre(), dto.descripcion(), dto.categoriaID(), identificador.getId());
    ar.edu.utn.dds.k3003.model.Producto guardado =
        productoJpaRepository != null
            ? productoJpaRepository.save(producto)
            : inMemoryProductoRepository.save(producto);

    metricasService.incrementarProductosRegistrados();

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

  public List<ProductoDTO> buscarTodosProductos() {
    List<ar.edu.utn.dds.k3003.model.Producto> productos =
        productoJpaRepository != null
            ? productoJpaRepository.findAll()
            : inMemoryProductoRepository.findAll();
    return productos.stream()
        .map(
            p ->
                new ProductoDTO(
                    p.getId().toString(),
                    p.getNombre(),
                    p.getDescripcion(),
                    p.getCategoriaID(),
                    p.getIdentificadorID().toString()))
        .collect(Collectors.toList());
  }

  /** Verifica que no exista otro producto registrado con el mismo nombre. */
  private void validarNombreDeProductoNoRepetido(String nombre) {
    if (nombre == null || nombre.isBlank()) {
      throw new RuntimeException("El nombre del producto es obligatorio");
    }
    boolean yaExiste =
        productoJpaRepository != null
            ? productoJpaRepository.existsByNombreIgnoreCase(nombre.trim())
            : inMemoryProductoRepository.existsByNombreIgnoreCase(nombre);
    if (yaExiste) {
      throw new RuntimeException("Ya existe un producto con el nombre: " + nombre);
    }
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
        d.getDepositoId(),
        d.getDescripcion(),
        d.getProductoId(),
        d.getCantidad(),
        EstadoDonacionEnum.valueOf(d.getEstado().name()));
  }
}
