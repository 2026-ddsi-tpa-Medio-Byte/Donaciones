package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.QuejaDTO;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.exceptions.DatoInvalidoException;
import ar.edu.utn.dds.k3003.exceptions.DonacionNoQuejableException;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoHabilitadoException;
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

  private void deleteDonacionById(Long id) {
    if (donacionJpaRepository != null) {
      donacionJpaRepository.deleteById(id);
      return;
    }
    inMemoryDonacionRepository.deleteById(id);
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

  /**
   * Ejecuta una llamada a otro módulo midiéndola: cuenta si salió bien o mal y cuánto tardó.
   *
   * <p>Sirve para poder ver, desde Datadog, el efecto dominó de cada operación: agrupando por la
   * etiqueta {@code operacion} se ve a qué módulos salimos a pegarle en cada POST y con qué
   * resultado. No cambia el comportamiento: la excepción se vuelve a lanzar tal cual.
   */
  private <T> T medirLlamada(
      String operacion, String destino, String accion, java.util.function.Supplier<T> llamada) {
    long inicio = System.nanoTime();
    try {
      T resultado = llamada.get();
      metricasService.registrarLlamadaSaliente(
          operacion, destino, accion, true, System.nanoTime() - inicio);
      return resultado;
    } catch (RuntimeException e) {
      metricasService.registrarLlamadaSaliente(
          operacion, destino, accion, false, System.nanoTime() - inicio);
      log.warn("[Donaciones -> {}] fallo {}: {}", destino, accion, e.getMessage());
      throw e;
    }
  }

  /*------------------------------------------------------------Entrega 1--------------------------------------------------------------------------------- */

  public DonacionDTO registrarDonacion(DonacionDTO donacionDTO) {
    // Todo el registro va dentro del try para que cada fallo cuente exactamente un error: antes
    // las validaciones sumaban el suyo y el catch volvía a sumar el mismo, así que un
    // "No puede donar" contaba doble en la alarma de donaciones rechazadas.
    try {
      validarDatosDeDonacion(donacionDTO);

      log.info(
          "[Donaciones] Registrando donacion (donadorID={}, productoID={}, cantidad={})",
          donacionDTO.donadorID(),
          donacionDTO.productoID(),
          donacionDTO.cantidad());

      // Donadores ya valida el producto al registrar una necesidad; acá faltaba el mismo
      // control: sin esto se donaba contra un productoID inexistente y el fantasma llegaba
      // hasta Logística, que después no podía asignarlo a ninguna necesidad.
      findProductoById(donacionDTO.productoID());

      medirLlamada(
          "registrar_donacion",
          "donadores",
          "validar_donador",
          () -> this.fachadaDonadores.buscarDonadorPorID(donacionDTO.donadorID()));

      boolean puedeDonar =
          medirLlamada(
              "registrar_donacion",
              "donadores",
              "puede_donar",
              () -> this.fachadaDonadores.puedeDonar(donacionDTO.donadorID()));

      if (!puedeDonar) {
        log.warn("[Donaciones] Donador {} NO puede donar, rechazando", donacionDTO.donadorID());
        throw new DonadorNoHabilitadoException(
            "No puede donar: el donador "
                + donacionDTO.donadorID()
                + " no está habilitado para donar");
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

      avisarALogisticaOCompensar(donacionDTO, guardada);

      metricasService.incrementarDonacionesRegistradas();

      return mapearADTO(guardada);

    } catch (RuntimeException e) {

      metricasService.incrementarDonacionesErrores();
      log.error("[Donaciones] Error registrando donacion: {}", e.getMessage());

      throw e;
    }
  }

  /**
   * Controles locales, antes de llamar a cualquier otro módulo: un pedido incompleto no tiene por
   * qué despertar a Donadores ni a Logística. El depósito no se valida contra Logística porque
   * hoy responde 500 en vez de 404 cuando no existe.
   */
  private void validarDatosDeDonacion(DonacionDTO donacionDTO) {
    if (donacionDTO == null) {
      throw new DatoInvalidoException("DTO nulo");
    }

    if (donacionDTO.id() != null) {
      throw new DatoInvalidoException("La donación ya tiene un ID asignado.");
    }

    // Sin estos tres, la donación se guardaba igual y el hueco aparecía recién en Logística
    // (o nunca, si el productoID venía null y se salteaba la validación del producto).
    exigirCampo(donacionDTO.donadorID(), "donadorID");
    exigirCampo(donacionDTO.depositoID(), "depositoID");
    exigirCampo(donacionDTO.productoID(), "productoID");

    // La consigna pide que la cantidad donada sea mayor a 0: sin esta validación se
    // registraban donaciones de 0 o negativas que después Logística tenía que descartar.
    if (donacionDTO.cantidad() == null || donacionDTO.cantidad() <= 0) {
      throw new DatoInvalidoException("La cantidad donada debe ser mayor a 0");
    }
  }

  private void exigirCampo(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new DatoInvalidoException("El campo " + campo + " es obligatorio");
    }
  }

  /**
   * Le avisa a Logística de la donación recién guardada y, si el aviso falla, la borra.
   *
   * <p>Se guarda antes de avisar porque Logística necesita el ID. Deshacer con
   * {@code @Transactional} no alcanza: la llamada HTTP no forma parte de la transacción de la
   * base y un rollback no la revierte. Por eso es una compensación explícita. Sin ella, si
   * Logística fallaba el cliente recibía un error pero la donación quedaba INGRESADA para siempre,
   * sin ninguna asignación que la hiciera avanzar.
   *
   * <p>Riesgo que queda: si Logística sí procesó el pedido y lo que se perdió fue la respuesta
   * (un timeout), del otro lado queda una asignación que apunta a una donación que acá ya no
   * existe. Por eso se loguea en ERROR con el ID, para poder reconciliar a mano.
   */
  private void avisarALogisticaOCompensar(DonacionDTO donacionDTO, Donacion guardada) {
    try {
      medirLlamada(
          "registrar_donacion",
          "logistica",
          "gestionar_donacion",
          () -> {
            this.fachadaLogistica.gestionarDonacion(
                donacionDTO.depositoID(),
                guardada.getId().toString(),
                donacionDTO.productoID(),
                donacionDTO.cantidad());
            return null;
          });
    } catch (RuntimeException falloDeLogistica) {
      compensarAlta(guardada.getId(), falloDeLogistica);
      throw falloDeLogistica;
    }
  }

  private void compensarAlta(Long donacionId, RuntimeException falloDeLogistica) {
    try {
      deleteDonacionById(donacionId);
    } catch (RuntimeException falloAlBorrar) {
      // Se informa y se sigue: quien llamó tiene que enterarse de lo de Logística, que es la
      // causa, no de este segundo problema.
      log.error(
          "[Donaciones] No se pudo deshacer la donacion id={} despues del fallo de Logistica"
              + " ({}): quedo INGRESADA sin asignacion, revisar a mano",
          donacionId,
          falloAlBorrar.getMessage());
      return;
    }
    log.error(
        "[Donaciones] Logistica fallo al recibir la donacion id={} ({}); se borro para no dejarla"
            + " huerfana. Si Logistica llego a procesarla (por ejemplo, un timeout), queda una"
            + " asignacion con donacionID={} sin donacion: reconciliar",
        donacionId,
        falloDeLogistica.getMessage(),
        donacionId);
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
    // setHistorialEstados y no setEstado: además del estado deja la entrada en el historial de
    // auditoría, que si no quedaba solo con el INGRESADA inicial.
    donacion.setHistorialEstados(EstadoDonacionEn.valueOf(estado.name()));
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

    return desdeLaFecha(donaciones, fecha);
  }

  /**
   * Las donaciones de un donador, opcionalmente desde una fecha. Es la que usa el endpoint.
   *
   * <p>A diferencia de {@link #buscarPorDonadorYFechaInicio}, que por contrato de cátedra lanza
   * excepción si el donador no donó nunca, acá «no donó nada» es una respuesta válida: lista
   * vacía. Así el controller no tiene que capturar excepciones genéricas para distinguirlo.
   *
   * @param desde null para traerlas todas, sin filtrar por fecha
   */
  public List<DonacionDTO> buscarDonacionesDeDonador(String donadorID, LocalDate desde) {
    metricasService.incrementarDonacionesConsultas();
    return desdeLaFecha(findDonacionesByDonador(donadorID), desde);
  }

  private List<DonacionDTO> desdeLaFecha(List<Donacion> donaciones, LocalDate desde) {
    return donaciones.stream()
        .filter(d -> desde == null || !d.getFecha().toLocalDate().isBefore(desde))
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

    // La descripción va antes que el estado: un pedido mal armado es 400 sea cual sea el estado.
    // Y se controla acá aunque Donadores también la rechace: así no se gasta una llamada, y quien
    // llama recibe un mensaje limpio en vez del «400 Bad Request: ...» crudo de Donadores.
    if (descripcion == null || descripcion.isBlank()) {
      throw new DatoInvalidoException("La queja tiene que tener una descripción");
    }

    // Se controla antes de llamar a Donadores, para que una queja que acá se rechaza no le quede
    // sumada al donador del otro lado.
    validarQueSeaQuejable(donacion);

    QuejaDTO quejaDTO = new QuejaDTO(null, donacionID, donacion.getDonadorId(), null, descripcion);
    medirLlamada(
        "registrar_queja",
        "donadores",
        "agregar_queja",
        () -> this.fachadaDonadores.agregarQueja(quejaDTO));
    // El estado cambia recién después de que Donadores aceptó la queja: si Donadores falla, la
    // donación tiene que seguir ACEPTADA (lo exige testRegistrarQuejaFallido de cátedra).
    DonacionDTO actualizada =
        cambiarEstadoDeDonacion(donacion.getId().toString(), EstadoDonacionEnum.CONQUEJA);

    metricasService.incrementarDonacionesQuejas();

    // Se devuelve lo que quedó guardado y no la entidad leída al principio: con JPA esa es otra
    // instancia, y la respuesta mostraba ACEPTADA aunque la donación ya estuviera CONQUEJA.
    return actualizada;
  }

  /**
   * La consigna pide quejas sobre donaciones ya entregadas, y entregada es ACEPTADA: el estado
   * que pone Logística al reportar la entrega. Una CONQUEJA ya tiene su queja; aceptar otra
   * sumaría dos quejas al donador por la misma donación.
   */
  private void validarQueSeaQuejable(Donacion donacion) {
    EstadoDonacionEn estado = donacion.getEstado();
    if (estado == EstadoDonacionEn.ACEPTADA) {
      return;
    }
    String motivo =
        estado == EstadoDonacionEn.CONQUEJA
            ? "ya tiene una queja registrada"
            : "todavía no fue entregada";
    throw new DonacionNoQuejableException(
        "No se puede registrar la queja: la donación "
            + donacion.getId()
            + " "
            + motivo
            + " (estado actual: "
            + estado
            + "). Solo se aceptan quejas sobre donaciones entregadas (ACEPTADA).");
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
