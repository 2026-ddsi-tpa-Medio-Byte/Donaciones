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

  private final Fachada fachada;

  public DonacionController(Fachada fachada) {
    this.fachada = fachada;
  }

    // ── Endpoints obligatorios ────────────────────────────────────────────────

  @Operation(summary = "Registrar una nueva donación")
  @PostMapping("/donaciones")
  public ResponseEntity<DonacionDTO> registrarDonacion(@RequestBody DonacionDTO donacionDTO) {
    return ResponseEntity.status(HttpStatus.CREATED).body(fachada.registrarDonacion(donacionDTO));
  }

  @Operation(summary = "Buscar donaciones por donador y fecha")
  @GetMapping("/donaciones")
  public ResponseEntity<List<DonacionDTO>> buscarDonaciones(
      @RequestParam(required = false) String donadorID,
      @RequestParam(required = false) String fecha) {
    if (donadorID == null || fecha == null) {
      return ResponseEntity.ok(fachada.buscarTodasDonaciones());
    }
    return ResponseEntity.ok(
        fachada.buscarPorDonadorYFechaInicio(donadorID, LocalDate.parse(fecha)));
  }

  @Operation(summary = "Resetear todas las donaciones, productos e identificadores")
  @DeleteMapping("/donaciones/reset")
  public ResponseEntity<String> resetDonaciones() {
    fachada.resetBaseDeDatos();
    return ResponseEntity.ok("Base de datos limpiada");
  }

  @Operation(summary = "Buscar donación por ID")
  @GetMapping("/donaciones/{id}")
  public ResponseEntity<DonacionDTO> buscarDonacionPorID(@PathVariable String id) {
    return ResponseEntity.ok(fachada.buscarDonacionPorID(id));
  }

  // ── Endpoints adicionales ─────────────────────────────────────────────────

  @Operation(summary = "Cambiar estado de una donación")
  @PatchMapping("/donaciones/{id}/estado")
  public ResponseEntity<DonacionDTO> cambiarEstado(
      @PathVariable String id, @RequestBody EstadoDonacionEnum estado) {
    return ResponseEntity.ok(fachada.cambiarEstadoDeDonacion(id, estado));
  }

  @Operation(summary = "Registrar queja en una donación")
  @PostMapping("/donaciones/{id}/quejas")
  public ResponseEntity<DonacionDTO> registrarQueja(
      @PathVariable String id, @RequestBody String descripcion) {
    return ResponseEntity.ok(fachada.registrarQuejaEnDonacion(id, descripcion));
  }

  @Operation(summary = "Agregar un producto")
  @PostMapping("/productos")
  public ResponseEntity<ProductoDTO> agregarProducto(@RequestBody ProductoDTO productoDTO) {
    return ResponseEntity.status(HttpStatus.CREATED).body(fachada.agregarProducto(productoDTO));
  }

  @Operation(summary = "Buscar producto por ID")
  @GetMapping("/productos/{id}")
  public ResponseEntity<ProductoDTO> buscarProductoPorID(@PathVariable String id) {
    return ResponseEntity.ok(fachada.buscarProductoPorID(id));
  }

  @Operation(summary = "Agregar un identificador")
  @PostMapping("/identificadores")
  public ResponseEntity<IdentificadorDTO> agregarIdentificador(
      @RequestBody IdentificadorDTO identificadorDTO) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(fachada.agregarIdentificador(identificadorDTO));
  }

  @Operation(summary = "Buscar identificador por ID")
  @GetMapping("/identificadores/{id}")
  public ResponseEntity<IdentificadorDTO> buscarIdentificadorPorID(@PathVariable String id) {
    return ResponseEntity.ok(fachada.buscarIdentificadorPorID(id));
  }
}