package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Donaciones", description = "API de gestión de donaciones")
public class DonacionController {

  private static final Logger log = LoggerFactory.getLogger(DonacionController.class);

  private final Fachada fachada;

  public DonacionController(Fachada fachada) {
    this.fachada = fachada;
  }

  // ── Endpoints obligatorios ────────────────────────────────────────────────

  @Operation(summary = "Registrar una nueva donación")
  @PostMapping("/donaciones")
  public ResponseEntity<DonacionDTO> registrarDonacion(@RequestBody DonacionDTO donacionDTO) {
    log.info(
        "[API] POST /donaciones (donadorID={}, productoID={}, cantidad={})",
        donacionDTO != null ? donacionDTO.donadorID() : null,
        donacionDTO != null ? donacionDTO.productoID() : null,
        donacionDTO != null ? donacionDTO.cantidad() : null);
    return ResponseEntity.status(HttpStatus.CREATED).body(fachada.registrarDonacion(donacionDTO));
  }

  @Operation(summary = "Buscar donaciones por donador y fecha")
  @GetMapping("/donaciones")
  public ResponseEntity<List<DonacionDTO>> buscarDonaciones(
      @RequestParam(name = "donadorID", required = false) String donadorID,
      @RequestParam(name = "donadorId", required = false) String donadorIdAlias,
      @RequestParam(required = false) String fecha) {
    String donador = donadorID != null ? donadorID : donadorIdAlias;
    log.info("[API] GET /donaciones (donador={}, fecha={})", donador, fecha);
    if (donador == null || fecha == null || fecha.isBlank()) {
      return ResponseEntity.ok(fachada.buscarTodasDonaciones());
    }
    try {
      return ResponseEntity.ok(
          fachada.buscarPorDonadorYFechaInicio(donador, LocalDate.parse(fecha)));
    } catch (RuntimeException e) {
      // Donador sin donaciones desde esa fecha → lista vacía en vez de 500
      log.warn(
          "[API] GET /donaciones sin resultados para donador={}: {}", donador, e.getMessage());
      return ResponseEntity.ok(List.of());
    }
  }

  @Operation(summary = "Resetear todas las donaciones, productos e identificadores")
  @DeleteMapping("/donaciones/reset")
  public ResponseEntity<String> resetDonaciones() {
    log.info("[API] DELETE /donaciones/reset");
    fachada.resetBaseDeDatos();
    return ResponseEntity.ok("Base de datos limpiada");
  }

  @Operation(summary = "Cargar datos de prueba en la base de datos")
  @PostMapping("/seed")
  public ResponseEntity<String> seedBaseDeDatos() {
    log.info("[API] POST /seed");
    return ResponseEntity.ok(fachada.seedBaseDeDatos());
  }

  @Operation(summary = "Buscar donación por ID")
  @GetMapping("/donaciones/{id}")
  public ResponseEntity<DonacionDTO> buscarDonacionPorID(@PathVariable String id) {
    log.info("[API] GET /donaciones/{}", id);
    return ResponseEntity.ok(fachada.buscarDonacionPorID(id));
  }

  // ── Endpoints adicionales ─────────────────────────────────────────────────

  @Operation(summary = "Cambiar estado de una donación")
  @PatchMapping("/donaciones/{id}/estado")
  public ResponseEntity<DonacionDTO> cambiarEstado(
      @PathVariable String id, @RequestBody EstadoDonacionEnum estado) {
    log.info("[API] PATCH /donaciones/{}/estado -> {}", id, estado);
    return ResponseEntity.ok(fachada.cambiarEstadoDeDonacion(id, estado));
  }

  @Operation(summary = "Registrar queja en una donación")
  @PostMapping("/donaciones/{id}/quejas")
  public ResponseEntity<DonacionDTO> registrarQueja(
      @PathVariable String id, @RequestBody String descripcion) {
    log.info("[API] POST /donaciones/{}/quejas", id);
    return ResponseEntity.ok(fachada.registrarQuejaEnDonacion(id, limpiarTexto(descripcion)));
  }

  /**
   * El body de la queja llega como texto plano. Si el cliente lo envía como string JSON (por
   * ejemplo desde Swagger, que agrega comillas), se quitan las comillas envolventes para no
   * persistirlas dentro de la descripción.
   */
  private String limpiarTexto(String texto) {
    if (texto == null) {
      return null;
    }
    String limpio = texto.trim();
    if (limpio.length() >= 2 && limpio.startsWith("\"") && limpio.endsWith("\"")) {
      limpio = limpio.substring(1, limpio.length() - 1);
    }
    return limpio;
  }

  @Operation(summary = "Agregar un producto")
  @PostMapping("/productos")
  public ResponseEntity<ProductoDTO> agregarProducto(@RequestBody ProductoDTO productoDTO) {
    log.info(
        "[API] POST /productos (nombre={})", productoDTO != null ? productoDTO.nombre() : null);
    return ResponseEntity.status(HttpStatus.CREATED).body(fachada.agregarProducto(productoDTO));
  }

  @Operation(summary = "Listar todos los productos")
  @GetMapping("/productos")
  public ResponseEntity<List<ProductoDTO>> listarProductos() {
    log.info("[API] GET /productos");
    return ResponseEntity.ok(fachada.buscarTodosProductos());
  }

  @Operation(summary = "Buscar producto por ID")
  @GetMapping("/productos/{id}")
  public ResponseEntity<ProductoDTO> buscarProductoPorID(@PathVariable String id) {
    log.info("[API] GET /productos/{}", id);
    return ResponseEntity.ok(fachada.buscarProductoPorID(id));
  }

  @Operation(summary = "Agregar un identificador")
  @PostMapping("/identificadores")
  public ResponseEntity<IdentificadorDTO> agregarIdentificador(
      @RequestBody IdentificadorDTO identificadorDTO) {
    log.info("[API] POST /identificadores");
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(fachada.agregarIdentificador(identificadorDTO));
  }

  @Operation(summary = "Listar todos los identificadores")
  @GetMapping("/identificadores")
  public ResponseEntity<List<IdentificadorDTO>> listarIdentificadores() {
    log.info("[API] GET /identificadores");
    return ResponseEntity.ok(fachada.buscarTodosIdentificadores());
  }

  @Operation(summary = "Buscar identificador por ID")
  @GetMapping("/identificadores/{id}")
  public ResponseEntity<IdentificadorDTO> buscarIdentificadorPorID(@PathVariable String id) {
    log.info("[API] GET /identificadores/{}", id);
    return ResponseEntity.ok(fachada.buscarIdentificadorPorID(id));
  }
}
