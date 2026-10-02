package ar.edu.utn.dds.k3003;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.utn.dds.k3003.app.Application;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.EstadoDonacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.IdentificadorDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.ProductoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.TipoIdentificadorEnum;
import ar.edu.utn.dds.k3003.model.Donacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacion;
import ar.edu.utn.dds.k3003.model.EstadoDonacionEn;
import ar.edu.utn.dds.k3003.repositories.DonacionJpaRepository;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Los endpoints de donaciones por HTTP, con la base H2 real y los otros dos módulos simulados en el
 * mismo {@link RestTemplate} que usan los clientes. Así se prueba el camino completo: cliente
 * HTTP → traducción del error → fachada → manejador global → código de respuesta.
 *
 * <p>Usa la misma configuración que {@link Entrega4IntegrationTest} para reutilizar el contexto, y
 * por eso deja la base vacía y el {@code RestTemplate} como estaba al terminar cada test.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DonacionesHttpIntegrationTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private RestTemplate restTemplate;
  @Autowired private Fachada fachada;
  @Autowired private DonacionJpaRepository donaciones;
  @Autowired private PlatformTransactionManager transacciones;

  @Value("${APP.URL_DONADORES}")
  private String urlDonadores;

  @Value("${APP.URL_LOGISTICA}")
  private String urlLogistica;

  private MockRestServiceServer otrosModulos;
  private String productoID;

  @BeforeEach
  void setUp() {
    fachada.resetBaseDeDatos();
    otrosModulos = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    IdentificadorDTO identificador =
        fachada.agregarIdentificador(
            new IdentificadorDTO(null, TipoIdentificadorEnum.CODIGODEBARRAS, "codigo de barras"));
    productoID =
        fachada
            .agregarProducto(
                new ProductoDTO(
                    null, "Yerba", "Yerba mate con palo", "alimentos", identificador.id()))
            .id();
  }

  @AfterEach
  void tearDown() {
    fachada.resetBaseDeDatos();
    restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory());
  }

  // ── Ayudas ─────────────────────────────────────────────────────────────────

  /** Guarda una donación directo en la base, sin pasar por los otros módulos. */
  private String donacionEnLaBase(String donador, EstadoDonacionEn estado) {
    Donacion donacion = new Donacion(donador, 5, "DEP-1", productoID, "Yerba");
    if (estado != EstadoDonacionEn.INGRESADA) {
      donacion.setHistorialEstados(estado);
    }
    return donaciones.save(donacion).getId().toString();
  }

  private Map<String, Object> pedidoDeDonacion(String donador) {
    Map<String, Object> body = new HashMap<>();
    body.put("donadorID", donador);
    body.put("depositoID", "DEP-1");
    body.put("descripcion", "Yerba para el comedor");
    body.put("productoID", productoID);
    body.put("cantidad", 5);
    return body;
  }

  private void donadorExiste(String donador) {
    otrosModulos
        .expect(requestTo(urlDonadores + "/donadores/" + donador))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":\"" + donador + "\",\"nombre\":\"Ana\"}", MediaType.APPLICATION_JSON));
  }

  private void donadorPuedeDonar(String donador, boolean puede) {
    otrosModulos
        .expect(requestTo(urlDonadores + "/donadores/" + donador + "/puede-donar"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"puedeDonar\":" + puede + "}", MediaType.APPLICATION_JSON));
  }

  private String error(ResponseEntity<Map> respuesta) {
    return String.valueOf(respuesta.getBody().get("error"));
  }

  // ── Listado de donaciones ─────────────────────────────────────────────────

  @Nested
  @DisplayName("GET /donaciones")
  class Listado {

    @BeforeEach
    void dosDonadores() {
      donacionEnLaBase("ana", EstadoDonacionEn.ACEPTADA);
      donacionEnLaBase("ana", EstadoDonacionEn.INGRESADA);
      donacionEnLaBase("bruno", EstadoDonacionEn.ACEPTADA);
    }

    private List<DonacionDTO> listar(String consulta) {
      ResponseEntity<DonacionDTO[]> respuesta =
          rest.getForEntity("/donaciones" + consulta, DonacionDTO[].class);
      assertEquals(HttpStatus.OK, respuesta.getStatusCode());
      return Arrays.asList(respuesta.getBody());
    }

    @Test
    @DisplayName("Sin donador devuelve todas las donaciones del sistema")
    void sinDonador() {
      assertEquals(3, listar("").size());
    }

    @Test
    @DisplayName("Con donador y sin fecha devuelve solo las de ese donador")
    void conDonadorSinFecha() {
      List<DonacionDTO> deAna = listar("?donadorID=ana");

      assertEquals(2, deAna.size());
      assertTrue(deAna.stream().allMatch(d -> "ana".equals(d.donadorID())));
    }

    @Test
    @DisplayName("Con la fecha vacía, como la manda Incentivos, devuelve solo las del donador")
    void conFechaVacia() {
      List<DonacionDTO> deAna = listar("?donadorID=ana&fecha=");

      assertEquals(2, deAna.size());
      assertTrue(deAna.stream().allMatch(d -> "ana".equals(d.donadorID())));
    }

    @Test
    @DisplayName("Con donador y fecha devuelve las de ese donador desde esa fecha")
    void conDonadorYFecha() {
      assertEquals(2, listar("?donadorID=ana&fecha=2020-01-01").size());
      assertEquals(0, listar("?donadorID=ana&fecha=" + LocalDate.now().plusDays(1)).size());
      assertEquals(1, listar("?donadorId=bruno&fecha=2020-01-01").size());
    }

    @Test
    @DisplayName("Un donador sin donaciones recibe 200 con la lista vacía")
    void donadorSinDonaciones() {
      assertEquals(0, listar("?donadorID=nadie").size());
      assertEquals(0, listar("?donadorID=nadie&fecha=2020-01-01").size());
    }

    @Test
    @DisplayName("Una fecha con formato inválido responde 400 explicando el formato esperado")
    void fechaInvalida() {
      ResponseEntity<Map> respuesta =
          rest.getForEntity("/donaciones?donadorID=ana&fecha=31-12-2026", Map.class);

      assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
      assertTrue(error(respuesta).contains("31-12-2026"), error(respuesta));
      assertTrue(error(respuesta).contains("AAAA-MM-DD"), error(respuesta));
    }
  }

  // ── Registro de una donación ──────────────────────────────────────────────

  @Nested
  @DisplayName("POST /donaciones")
  class Registro {

    @Test
    @DisplayName("Con los otros módulos respondiendo bien, responde 201 y la donación queda guardada")
    void registroExitoso() {
      donadorExiste("ana");
      donadorPuedeDonar("ana", true);
      otrosModulos
          .expect(requestTo(urlLogistica + "/asignaciones"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

      ResponseEntity<DonacionDTO> respuesta =
          rest.postForEntity("/donaciones", pedidoDeDonacion("ana"), DonacionDTO.class);

      assertEquals(HttpStatus.CREATED, respuesta.getStatusCode());
      assertEquals(EstadoDonacionEnum.INGRESADA, respuesta.getBody().estado());
      assertEquals(1, donaciones.count());
      otrosModulos.verify();
    }

    @Test
    @DisplayName("Un donador que no existe en Donadores responde 404 con un mensaje legible")
    void donadorInexistente() {
      otrosModulos
          .expect(requestTo(urlDonadores + "/donadores/fantasma"))
          .andRespond(withResourceNotFound());

      ResponseEntity<Map> respuesta =
          rest.postForEntity("/donaciones", pedidoDeDonacion("fantasma"), Map.class);

      assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
      assertEquals("No existe el donador fantasma", error(respuesta));
      assertEquals(0, donaciones.count());
    }

    @Test
    @DisplayName("Un donador no habilitado responde 403 y el cuerpo dice «No puede donar»")
    void donadorNoHabilitado() {
      donadorExiste("ana");
      donadorPuedeDonar("ana", false);

      ResponseEntity<Map> respuesta =
          rest.postForEntity("/donaciones", pedidoDeDonacion("ana"), Map.class);

      assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
      assertTrue(error(respuesta).contains("No puede donar"), error(respuesta));
      assertEquals(0, donaciones.count());
    }

    @Test
    @DisplayName("Si Donadores no responde, responde 502 nombrando a Donadores")
    void donadoresSinRespuesta() {
      otrosModulos
          .expect(requestTo(urlDonadores + "/donadores/ana"))
          .andRespond(withException(new SocketTimeoutException("Read timed out")));

      ResponseEntity<Map> respuesta =
          rest.postForEntity("/donaciones", pedidoDeDonacion("ana"), Map.class);

      assertEquals(HttpStatus.BAD_GATEWAY, respuesta.getStatusCode());
      assertTrue(error(respuesta).contains("Donadores"), error(respuesta));
    }

    @Test
    @DisplayName("Si Logística responde 500, responde 502 nombrando a Logística y no deja la donación")
    void logisticaConError() {
      donadorExiste("ana");
      donadorPuedeDonar("ana", true);
      otrosModulos
          .expect(requestTo(urlLogistica + "/asignaciones"))
          .andRespond(withServerError());

      ResponseEntity<Map> respuesta =
          rest.postForEntity("/donaciones", pedidoDeDonacion("ana"), Map.class);

      assertEquals(HttpStatus.BAD_GATEWAY, respuesta.getStatusCode());
      assertTrue(error(respuesta).contains("Logística"), error(respuesta));
      assertEquals(0, donaciones.count(), "la donación no puede quedar huérfana en la base");
    }

    @Test
    @DisplayName("Si Logística no responde, responde 502 y no deja la donación")
    void logisticaSinRespuesta() {
      donadorExiste("ana");
      donadorPuedeDonar("ana", true);
      otrosModulos
          .expect(requestTo(urlLogistica + "/asignaciones"))
          .andRespond(withException(new SocketTimeoutException("Read timed out")));

      ResponseEntity<Map> respuesta =
          rest.postForEntity("/donaciones", pedidoDeDonacion("ana"), Map.class);

      assertEquals(HttpStatus.BAD_GATEWAY, respuesta.getStatusCode());
      assertEquals(0, donaciones.count());
    }

    @Test
    @DisplayName("Sin depósito responde 400 nombrando el campo, sin llamar a otros módulos")
    void sinDeposito() {
      Map<String, Object> pedido = pedidoDeDonacion("ana");
      pedido.remove("depositoID");

      ResponseEntity<Map> respuesta = rest.postForEntity("/donaciones", pedido, Map.class);

      assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
      assertTrue(error(respuesta).contains("depositoID"), error(respuesta));
      otrosModulos.verify();
    }
  }

  // ── Quejas y estados ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("Quejas y cambios de estado")
  class QuejasYEstados {

    private ResponseEntity<String> quejarse(String donacion) {
      return rest.postForEntity(
          "/donaciones/" + donacion + "/quejas", "El paquete llegó roto", String.class);
    }

    @Test
    @DisplayName("Quejarse de una donación no entregada responde 409 sin avisar a Donadores")
    void quejaSobreDonacionNoEntregada() {
      String id = donacionEnLaBase("ana", EstadoDonacionEn.INGRESADA);

      ResponseEntity<String> respuesta = quejarse(id);

      assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
      assertTrue(respuesta.getBody().contains("INGRESADA"), respuesta.getBody());
      otrosModulos.verify();
    }

    @Test
    @DisplayName("La queja sobre una donación entregada responde CONQUEJA y una segunda da 409")
    void quejaSobreDonacionEntregada() {
      String id = donacionEnLaBase("ana", EstadoDonacionEn.ACEPTADA);
      otrosModulos
          .expect(requestTo(urlDonadores + "/donadores/ana/quejas"))
          .andExpect(method(HttpMethod.POST))
          .andRespond(
              withSuccess(
                  "{\"id\":\"1\",\"donacionID\":\"" + id + "\",\"donadorID\":\"ana\"}",
                  MediaType.APPLICATION_JSON));

      ResponseEntity<DonacionDTO> primera =
          rest.postForEntity(
              "/donaciones/" + id + "/quejas", "El paquete llegó roto", DonacionDTO.class);
      ResponseEntity<String> segunda = quejarse(id);

      assertEquals(HttpStatus.OK, primera.getStatusCode());
      assertEquals(EstadoDonacionEnum.CONQUEJA, primera.getBody().estado());
      assertEquals(HttpStatus.CONFLICT, segunda.getStatusCode());
      assertTrue(segunda.getBody().contains("CONQUEJA"), segunda.getBody());
      otrosModulos.verify();
    }

    private void assertSigueAceptada(String id) {
      assertEquals(
          EstadoDonacionEnum.ACEPTADA,
          rest.getForEntity("/donaciones/" + id, DonacionDTO.class).getBody().estado());
    }

    @Test
    @DisplayName("Una queja con descripción vacía, como la manda Swagger, da 400 limpio sin llegar a Donadores")
    void quejaConDescripcionVacia() {
      String id = donacionEnLaBase("ana", EstadoDonacionEn.ACEPTADA);
      HttpHeaders texto = new HttpHeaders();
      texto.setContentType(MediaType.TEXT_PLAIN);

      ResponseEntity<Map> respuesta =
          rest.exchange(
              "/donaciones/" + id + "/quejas",
              HttpMethod.POST,
              new HttpEntity<>("\"\"", texto),
              Map.class);

      assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
      assertEquals("La queja tiene que tener una descripción", error(respuesta));
      assertSigueAceptada(id);
      otrosModulos.verify();
    }

    @Test
    @DisplayName("Una queja sin cuerpo da el mismo 400 limpio, no el error interno de Spring")
    void quejaSinCuerpo() {
      String id = donacionEnLaBase("ana", EstadoDonacionEn.ACEPTADA);
      HttpHeaders texto = new HttpHeaders();
      texto.setContentType(MediaType.TEXT_PLAIN);

      ResponseEntity<Map> respuesta =
          rest.exchange(
              "/donaciones/" + id + "/quejas",
              HttpMethod.POST,
              new HttpEntity<>(texto),
              Map.class);

      assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
      assertEquals("La queja tiene que tener una descripción", error(respuesta));
      assertSigueAceptada(id);
      otrosModulos.verify();
    }

    @Test
    @DisplayName("El cambio de estado que reporta Logística queda guardado en el historial")
    void cambioDeEstadoQuedaEnElHistorial() {
      String id = donacionEnLaBase("ana", EstadoDonacionEn.INGRESADA);
      HttpHeaders json = new HttpHeaders();
      json.setContentType(MediaType.APPLICATION_JSON);

      ResponseEntity<DonacionDTO> respuesta =
          rest.exchange(
              "/donaciones/" + id + "/estado",
              HttpMethod.PATCH,
              new HttpEntity<>("\"ACEPTADA\"", json),
              DonacionDTO.class);

      assertEquals(HttpStatus.OK, respuesta.getStatusCode());
      List<EstadoDonacionEnum> historial =
          new TransactionTemplate(transacciones)
              .execute(
                  estado ->
                      donaciones.findById(Long.valueOf(id)).orElseThrow().getHistorialEstados()
                          .stream()
                          .map(EstadoDonacion::getEstado)
                          .toList());
      assertEquals(List.of(EstadoDonacionEnum.INGRESADA, EstadoDonacionEnum.ACEPTADA), historial);
    }
  }
}
