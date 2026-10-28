package ar.edu.utn.dds.k3003.eventos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.Fachada;
import ar.edu.utn.dds.k3003.MetricasService;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import ar.edu.utn.dds.k3003.dtos.ProductoConSubcategoriaDTO;
import ar.edu.utn.dds.k3003.exceptions.DonacionNoQuejableException;
import ar.edu.utn.dds.k3003.exceptions.DonadorNoHabilitadoException;
import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.Producto;
import ar.edu.utn.dds.k3003.repositories.DonacionJpaRepository;
import ar.edu.utn.dds.k3003.repositories.IdentificadorJpaRepository;
import ar.edu.utn.dds.k3003.repositories.ProductoJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Qué evento deja cada operación de la fachada. Usa los repositorios en memoria y junta los
 * eventos en una lista, en lugar del publicador de Spring: lo que se prueba es qué se publica y
 * con qué datos, no el transporte.
 */
class EventosDeLaFachadaTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** UTC, con milisegundos y {@code Z}: {@code 2026-10-26T18:32:10.123Z}. */
  private static final Pattern FECHA_UTC =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");

  private FachadaDonadoresYEntidades donadores;
  private FachadaLogistica logistica;
  private Fachada fachada;
  private List<Evento> publicados;
  private String identificadorID;
  private String productoID;

  @BeforeEach
  void setUp() {
    donadores = mock(FachadaDonadoresYEntidades.class);
    logistica = mock(FachadaLogistica.class);
    when(donadores.puedeDonar("donador1")).thenReturn(true);

    publicados = new ArrayList<>();
    fachada = new Fachada();
    fachada.setFachadaDonadoresYEntidades(donadores);
    fachada.setFachadaLogistica(logistica);
    fachada.setApplicationEventPublisher(evento -> publicados.add((Evento) evento));

    identificadorID =
        fachada
            .agregarIdentificador(
                new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo"))
            .id();
    productoID =
        fachada
            .registrarProducto(
                new ProductoConSubcategoriaDTO(
                    null,
                    "Fideos",
                    "Fideos secos tipo tirabuzon",
                    "alimentos",
                    "pastas",
                    identificadorID))
            .id();
  }

  private static JsonNode json(Evento evento) throws Exception {
    return JSON.readTree(PublicadorEventos.serializar(evento));
  }

  private static List<String> campos(JsonNode nodo) {
    List<String> campos = new ArrayList<>();
    nodo.fieldNames().forEachRemaining(campos::add);
    return campos;
  }

  private List<TipoEvento> tipos() {
    return publicados.stream().map(Evento::tipo).toList();
  }

  private Evento unico(TipoEvento tipo) {
    List<Evento> deEseTipo = publicados.stream().filter(e -> e.tipo() == tipo).toList();
    assertEquals(1, deEseTipo.size(), "eventos publicados: " + tipos());
    return deEseTipo.get(0);
  }

  private static void assertFechaUtc(JsonNode fecha) {
    assertTrue(FECHA_UTC.matcher(fecha.asText()).matches(), "no está en UTC con Z: " + fecha);
  }

  private DonacionDTO donar() {
    return fachada.registrarDonacion(
        new DonacionDTO(null, "donador1", "DEP-1", "Fideos para el comedor", productoID, 5, null));
  }

  // ── Productos ─────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Productos")
  class Productos {

    @Test
    @DisplayName("Crear un producto publica PRODUCTO_CREADO con todos sus datos, subcategoría incluida")
    void productoCreado() throws Exception {
      Evento evento = unico(TipoEvento.PRODUCTO_CREADO);
      JsonNode sobre = json(evento);
      JsonNode datos = sobre.get("datos");

      assertEquals(productoID, evento.entidadId());
      assertEquals("donaciones", sobre.get("modulo").asText());
      assertFechaUtc(sobre.get("ocurridoEn"));
      assertEquals(
          List.of("id", "nombre", "descripcion", "categoria", "subcategoria", "identificadorID"),
          campos(datos));
      assertEquals(productoID, datos.get("id").asText());
      assertEquals("Fideos", datos.get("nombre").asText());
      assertEquals("Fideos secos tipo tirabuzon", datos.get("descripcion").asText());
      assertEquals("alimentos", datos.get("categoria").asText());
      assertEquals("pastas", datos.get("subcategoria").asText());
      assertEquals(identificadorID, datos.get("identificadorID").asText());
    }

    @Test
    @DisplayName("Por la interfaz de cátedra el producto sale sin subcategoría, con el campo en null")
    void productoSinSubcategoria() throws Exception {
      publicados.clear();

      fachada.agregarProducto(
          new ProductoDTO(null, "Arroz", "Arroz blanco largo fino", "alimentos", identificadorID));

      JsonNode datos = json(unico(TipoEvento.PRODUCTO_CREADO)).get("datos");
      assertTrue(datos.has("subcategoria"), "el campo tiene que viajar aunque esté vacío");
      assertTrue(datos.get("subcategoria").isNull());
    }

    @Test
    @DisplayName("Un producto rechazado por las reglas de la consigna no publica nada")
    void productoRechazado() {
      publicados.clear();

      assertThrows(
          RuntimeException.class,
          () ->
              fachada.agregarProducto(
                  new ProductoDTO(null, "Yerba", "Yerba", "alimentos", identificadorID)));

      assertTrue(publicados.isEmpty());
    }
  }

  // ── Alta de una donación ──────────────────────────────────────────────────

  @Nested
  @DisplayName("Alta de una donación")
  class AltaDeDonacion {

    @BeforeEach
    void sinLosEventosDelSetUp() {
      publicados.clear();
    }

    @Test
    @DisplayName("Si Logística la acepta, publica DONACION_REGISTRADA con el estado completo")
    void donacionRegistrada() throws Exception {
      Instant antes = Instant.now().truncatedTo(ChronoUnit.MILLIS);
      DonacionDTO donacion = donar();
      Instant despues = Instant.now();

      Evento evento = unico(TipoEvento.DONACION_REGISTRADA);
      JsonNode datos = json(evento).get("datos");
      assertEquals(donacion.id(), evento.entidadId());
      assertEquals(
          List.of(
              "id",
              "donadorID",
              "depositoID",
              "productoID",
              "cantidad",
              "descripcion",
              "estado",
              "fechaIngreso",
              "historialEstados"),
          campos(datos));
      assertEquals(donacion.id(), datos.get("id").asText());
      assertEquals("donador1", datos.get("donadorID").asText());
      assertEquals("DEP-1", datos.get("depositoID").asText());
      assertEquals(productoID, datos.get("productoID").asText());
      assertEquals(5, datos.get("cantidad").asInt());
      assertEquals("Fideos para el comedor", datos.get("descripcion").asText());
      assertEquals("INGRESADA", datos.get("estado").asText());

      // Si la fecha local se publicara como si fuera UTC, en una máquina de Argentina quedaría
      // tres horas corrida y fuera de este intervalo.
      assertFechaUtc(datos.get("fechaIngreso"));
      Instant fechaIngreso = Instant.parse(datos.get("fechaIngreso").asText());
      assertFalse(fechaIngreso.isBefore(antes) || fechaIngreso.isAfter(despues), fechaIngreso.toString());

      JsonNode historial = datos.get("historialEstados");
      assertEquals(1, historial.size());
      assertEquals(List.of("estado", "fecha"), campos(historial.get(0)));
      assertEquals("INGRESADA", historial.get(0).get("estado").asText());
      assertFechaUtc(historial.get(0).get("fecha"));
    }

    @Test
    @DisplayName("DONACION_REGISTRADA sale recién después de que Logística aceptó la donación")
    void saleDespuesDeLogistica() {
      List<TipoEvento> publicadosAlAvisar = new ArrayList<>();
      when(logistica.gestionarDonacion(any(), any(), any(), anyInt()))
          .thenAnswer(
              invocacion -> {
                publicadosAlAvisar.addAll(tipos());
                return null;
              });

      donar();

      assertTrue(publicadosAlAvisar.isEmpty(), "ya había salido: " + publicadosAlAvisar);
      assertEquals(List.of(TipoEvento.DONACION_REGISTRADA), tipos());
    }

    @Test
    @DisplayName("Si Logística falla, no queda publicada la donación: sale DONACION_ELIMINADA con su id")
    void compensacion() throws Exception {
      when(logistica.gestionarDonacion(any(), any(), any(), anyInt()))
          .thenThrow(new RuntimeException("Logística caída"));

      assertThrows(RuntimeException.class, EventosDeLaFachadaTest.this::donar);

      assertEquals(List.of(TipoEvento.DONACION_ELIMINADA), tipos());
      Evento eliminada = publicados.get(0);
      assertEquals("1", eliminada.entidadId());
      JsonNode sobre = json(eliminada);
      assertEquals(List.of("id"), campos(sobre.get("datos")));
      assertEquals("1", sobre.get("datos").get("id").asText());
      assertFechaUtc(sobre.get("ocurridoEn"));
    }

    @Test
    @DisplayName("Un donador no habilitado no publica nada")
    void donadorNoHabilitado() {
      when(donadores.puedeDonar("donador1")).thenReturn(false);

      assertThrows(DonadorNoHabilitadoException.class, EventosDeLaFachadaTest.this::donar);

      assertTrue(publicados.isEmpty());
    }
  }

  // ── Cambios de estado ─────────────────────────────────────────────────────

  @Nested
  @DisplayName("Cambios de estado")
  class CambiosDeEstado {

    private DonacionDTO donacion;

    @BeforeEach
    void unaDonacion() {
      donacion = donar();
      publicados.clear();
    }

    @Test
    @DisplayName("La entrega que reporta Logística publica DONACION_ESTADO_CAMBIADO con el historial")
    void entrega() throws Exception {
      fachada.cambiarEstadoDeDonacion(donacion.id(), EstadoDonacionEnum.ACEPTADA);

      Evento evento = unico(TipoEvento.DONACION_ESTADO_CAMBIADO);
      JsonNode datos = json(evento).get("datos");
      assertEquals(donacion.id(), evento.entidadId());
      assertEquals("ACEPTADA", datos.get("estado").asText());
      assertEquals(5, datos.get("cantidad").asInt());
      assertFechaUtc(datos.get("fechaIngreso"));
      JsonNode historial = datos.get("historialEstados");
      assertEquals(2, historial.size());
      assertEquals("INGRESADA", historial.get(0).get("estado").asText());
      assertEquals("ACEPTADA", historial.get(1).get("estado").asText());
      assertFechaUtc(historial.get(1).get("fecha"));
    }

    @Test
    @DisplayName("La queja publica el paso a CONQUEJA")
    void queja() throws Exception {
      fachada.cambiarEstadoDeDonacion(donacion.id(), EstadoDonacionEnum.ACEPTADA);
      publicados.clear();

      fachada.registrarQuejaEnDonacion(donacion.id(), "Llegó roto");

      JsonNode datos = json(unico(TipoEvento.DONACION_ESTADO_CAMBIADO)).get("datos");
      assertEquals("CONQUEJA", datos.get("estado").asText());
      assertEquals(3, datos.get("historialEstados").size());
    }

    @Test
    @DisplayName("Una queja rechazada por el estado no publica nada")
    void quejaRechazada() {
      assertThrows(
          DonacionNoQuejableException.class,
          () -> fachada.registrarQuejaEnDonacion(donacion.id(), "Llegó roto"));

      assertTrue(publicados.isEmpty());
    }
  }

  // ── Reset y republicación ─────────────────────────────────────────────────

  @Test
  @DisplayName("El reset publica MODULO_RESETEADO con entidadId \"*\" y datos vacíos")
  void reset() throws Exception {
    publicados.clear();

    fachada.resetBaseDeDatos();

    JsonNode sobre = json(unico(TipoEvento.MODULO_RESETEADO));
    assertEquals("*", sobre.get("entidadId").asText());
    assertTrue(sobre.get("datos").isObject() && sobre.get("datos").isEmpty());
  }

  @Test
  @DisplayName("El estado actual sale como un evento por producto y por donación, todos con la misma hora")
  void estadoActual() throws Exception {
    DonacionDTO donacion = donar();

    List<Evento> eventos = fachada.estadoActualComoEventos();

    assertEquals(
        List.of(TipoEvento.PRODUCTO_MODIFICADO, TipoEvento.DONACION_ESTADO_CAMBIADO),
        eventos.stream().map(Evento::tipo).toList());
    assertEquals(productoID, eventos.get(0).entidadId());
    assertEquals(donacion.id(), eventos.get(1).entidadId());
    assertEquals(eventos.get(0).ocurridoEn(), eventos.get(1).ocurridoEn());
    assertEquals("pastas", json(eventos.get(0)).get("datos").get("subcategoria").asText());
    assertEquals(1, json(eventos.get(1)).get("datos").get("historialEstados").size());
  }

  @Test
  @DisplayName("Cada evento tiene su propio eventoId")
  void eventoIdUnico() {
    donar();
    fachada.resetBaseDeDatos();

    assertEquals(
        publicados.size(), publicados.stream().map(Evento::eventoId).distinct().count());
  }

  // ── Publicar nunca rompe la operación ─────────────────────────────────────

  @Test
  @DisplayName("Si entregar el evento falla, la operación responde igual")
  void publicadorQueFalla() {
    fachada.setApplicationEventPublisher(
        evento -> {
          throw new IllegalStateException("el publicador explotó");
        });

    ProductoConSubcategoriaDTO producto =
        fachada.registrarProducto(
            new ProductoConSubcategoriaDTO(
                null, "Arroz", "Arroz blanco largo fino", "alimentos", null, identificadorID));
    DonacionDTO donacion = donar();
    fachada.cambiarEstadoDeDonacion(donacion.id(), EstadoDonacionEnum.ACEPTADA);
    fachada.resetBaseDeDatos();

    assertEquals("Arroz", producto.nombre());
    assertEquals(EstadoDonacionEnum.INGRESADA, donacion.estado());
  }

  @Test
  @DisplayName("Si la compensación no pudo borrar la donación, no se publica que se eliminó")
  void compensacionFallidaNoPublica() {
    DonacionJpaRepository repoDonaciones = mock(DonacionJpaRepository.class);
    ProductoJpaRepository repoProductos = mock(ProductoJpaRepository.class);
    when(repoProductos.findById(1L))
        .thenReturn(Optional.of(new Producto("Fideos", "Fideos secos tipo tirabuzon", null, 1L)));
    when(repoDonaciones.save(any()))
        .thenAnswer(
            inv -> {
              Donacion d = inv.getArgument(0);
              d.setId(42L);
              return d;
            });
    doThrow(new RuntimeException("la base no responde")).when(repoDonaciones).deleteById(42L);
    when(logistica.gestionarDonacion(any(), any(), any(), anyInt()))
        .thenThrow(new RuntimeException("Logística caída"));
    Fachada conJpa =
        new Fachada(
            repoDonaciones,
            repoProductos,
            mock(IdentificadorJpaRepository.class),
            donadores,
            logistica,
            new MetricasService(new SimpleMeterRegistry()));
    List<Evento> eventosJpa = new ArrayList<>();
    conJpa.setApplicationEventPublisher(evento -> eventosJpa.add((Evento) evento));

    assertThrows(
        RuntimeException.class,
        () ->
            conJpa.registrarDonacion(
                new DonacionDTO(null, "donador1", "DEP-1", "Fideos", "1", 5, null)));

    assertTrue(eventosJpa.isEmpty(), "publicados: " + eventosJpa);
  }

  @Test
  @DisplayName("Con el constructor de cátedra, sin publicador, todo funciona y no se publica nada")
  void sinPublicador() {
    Fachada deCatedra = new Fachada();
    deCatedra.setFachadaDonadoresYEntidades(donadores);
    deCatedra.setFachadaLogistica(logistica);
    String identificador =
        deCatedra
            .agregarIdentificador(
                new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo"))
            .id();
    String producto =
        deCatedra
            .agregarProducto(
                new ProductoDTO(null, "Arroz", "Arroz blanco largo fino", null, identificador))
            .id();

    DonacionDTO donacion =
        deCatedra.registrarDonacion(
            new DonacionDTO(null, "donador1", "DEP-1", "Arroz", producto, 2, null));
    DonacionDTO entregada =
        deCatedra.cambiarEstadoDeDonacion(donacion.id(), EstadoDonacionEnum.ACEPTADA);
    deCatedra.resetBaseDeDatos();

    assertEquals(EstadoDonacionEnum.ACEPTADA, entregada.estado());
    assertTrue(deCatedra.buscarTodosProductos().isEmpty());
  }
}
