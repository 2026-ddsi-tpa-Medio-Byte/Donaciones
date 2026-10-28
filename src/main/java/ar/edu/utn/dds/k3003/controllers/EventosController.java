package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.eventos.PublicadorEventos;
import ar.edu.utn.dds.k3003.eventos.ResultadoRepublicacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de los eventos del modelo de lectura.
 *
 * <p>Va aparte de {@link DonacionController} porque no es una operación de negocio: no cambia
 * nada en Donaciones, solo vuelve a contarle a Consultas lo que ya hay.
 */
@RestController
public class EventosController {

  private static final Logger log = LoggerFactory.getLogger(EventosController.class);

  private final Fachada fachada;
  private final PublicadorEventos publicador;

  public EventosController(Fachada fachada, PublicadorEventos publicador) {
    this.fachada = fachada;
    this.publicador = publicador;
  }

  @Operation(
      summary =
          "Volver a publicar el estado actual de todos los productos y donaciones, para cargar o"
              + " resincronizar el modelo de lectura")
  @Tag(name = "Administración")
  @PostMapping("/admin/republicar-eventos")
  public ResponseEntity<ResultadoRepublicacion> republicarEventos() {
    log.info("[API] POST /admin/republicar-eventos");
    return ResponseEntity.ok(publicador.republicar(fachada.estadoActualComoEventos()));
  }
}
