package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.*;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaIncentivos;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DonadoresYEntidadesClient implements FachadaDonadoresYEntidades {

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
    return restTemplate.getForObject(url, DonadorDTO.class);
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
    return restTemplate.postForObject(url, quejaDTO, QuejaDTO.class);
  }

  @Override
  public Boolean puedeDonar(String donadorID) {
    String url = String.format("%s/donadores/%s/puede-donar", baseUrl, donadorID);
    Map<String, Object> respuesta = restTemplate.getForObject(url, Map.class);
    if (respuesta == null || respuesta.get("puedeDonar") == null) {
      return false;
    }
    return Boolean.parseBoolean(respuesta.get("puedeDonar").toString());
  }

  @Override
  public List<QuejaDTO> obtenerQuejasDe(String donadorID) {
    String url = String.format("%s/donadores/%s/quejas", baseUrl, donadorID);
    return restTemplate.getForObject(url, List.class);
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
