package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DonadoresYEntidadesClient implements FachadaDonadoresYEntidades {

  private static final Logger log = LoggerFactory.getLogger(DonadoresYEntidadesClient.class);

  private final RestTemplate restTemplate;
  private final String baseUrl;

  public DonadoresYEntidadesClient(
      RestTemplate restTemplate,
      @Value("${APP.URL_DONADORES:http://localhost:8081}") String baseUrl) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
  }

  @Override
  public DonadorDTO agregarDonador(DonadorDTO donadorDTO) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public DonadorDTO buscarDonadorPorID(String donadorID) {
    String url = String.format("%s/donadores/%s", baseUrl, donadorID);
    log.info("[Donaciones -> Donadores] GET donador (donadorID={}) -> {}", donadorID, url);
    try {
      DonadorDTO resp = restTemplate.getForObject(url, DonadorDTO.class);
      log.info("[Donaciones <- Donadores] donador OK (donadorID={})", donadorID);
      return resp;
    } catch (RuntimeException e) {
      log.error(
          "[Donaciones <- Donadores] FALLO al buscar donador (donadorID={}): {}",
          donadorID,
          e.getMessage());
      throw e;
    }
  }

  @Override
  public EntidadBeneficaDTO agregarEntidad(EntidadBeneficaDTO entidadBeneficaDTO) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public EntidadBeneficaDTO buscarEntidadPorID(String entidadID) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public NecesidadMaterialDTO registrarNecesidad(NecesidadMaterialDTO necesidadMaterialDTO) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public QuejaDTO agregarQueja(QuejaDTO quejaDTO) {
    String url = String.format("%s/donadores/%s/quejas", baseUrl, quejaDTO.donadorID());
    log.info(
        "[Donaciones -> Donadores] Enviando agregarQueja (donadorID={}, donacionID={})",
        quejaDTO.donadorID(),
        quejaDTO.donacionID());
    try {
      QuejaDTO resp = restTemplate.postForObject(url, quejaDTO, QuejaDTO.class);
      log.info("[Donaciones <- Donadores] agregarQueja OK (donadorID={})", quejaDTO.donadorID());
      return resp;
    } catch (RuntimeException e) {
      log.error(
          "[Donaciones <- Donadores] FALLO agregarQueja (donadorID={}): {}",
          quejaDTO.donadorID(),
          e.getMessage());
      throw e;
    }
  }

  @Override
  public Boolean puedeDonar(String donadorID) {
    String url = String.format("%s/donadores/%s/puede-donar", baseUrl, donadorID);
    log.info("[Donaciones -> Donadores] Consultando puede-donar (donadorID={})", donadorID);
    try {
      Map<String, Object> respuesta = restTemplate.getForObject(url, Map.class);
      boolean puede =
          respuesta != null
              && respuesta.get("puedeDonar") != null
              && Boolean.parseBoolean(respuesta.get("puedeDonar").toString());
      log.info(
          "[Donaciones <- Donadores] puede-donar OK (donadorID={}, puedeDonar={})",
          donadorID,
          puede);
      return puede;
    } catch (RuntimeException e) {
      log.error(
          "[Donaciones <- Donadores] FALLO puede-donar (donadorID={}): {}",
          donadorID,
          e.getMessage());
      throw e;
    }
  }

  @Override
  public List<QuejaDTO> obtenerQuejasDe(String donadorID) {
    String url = String.format("%s/donadores/%s/quejas", baseUrl, donadorID);
    log.info("[Donaciones -> Donadores] GET quejas (donadorID={})", donadorID);
    try {
      List<QuejaDTO> resp = restTemplate.getForObject(url, List.class);
      log.info(
          "[Donaciones <- Donadores] quejas OK (donadorID={}, cantidad={})",
          donadorID,
          resp != null ? resp.size() : 0);
      return resp;
    } catch (RuntimeException e) {
      log.error(
          "[Donaciones <- Donadores] FALLO al obtener quejas (donadorID={}): {}",
          donadorID,
          e.getMessage());
      throw e;
    }
  }

  @Override
  public DonadorDTO modificarEstado(String donadorID, EstadoDonadorEnum estado) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public DonadorDTO modifcarCategoria(String donadorID, String categoria) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public List<NecesidadMaterialDTO> obtenerNecesidadesInsatisfechasDe(String productoSolicitadoID) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public NecesidadMaterialDTO satisfacerNecesidad(String necesidadID, Integer cantidad) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public DonadorStatsDTO estadisticasDonador(String donadorID) {
    throw new UnsupportedOperationException(
        "Operación no implementada en DonadoresYEntidadesClient");
  }

  @Override
  public void setFachadaIncentivos(FachadaIncentivos fachadaIncentivos) {
    // No aplica para el cliente HTTP
  }
}
