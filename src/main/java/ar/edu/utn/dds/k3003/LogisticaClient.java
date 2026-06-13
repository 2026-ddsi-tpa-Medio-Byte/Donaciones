package ar.edu.utn.dds.k3003;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.PaqueteDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.TipoAlgoritmoEnum;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonadoresYEntidades;
import ar.edu.utn.dds.k3003.catedra.fachadas.FachadaLogistica;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LogisticaClient implements FachadaLogistica {

  private final RestTemplate restTemplate;
  private final String baseUrl;

  public LogisticaClient(
      RestTemplate restTemplate,
      @Value("${APP.URL_LOGISTICA:http://localhost:8082}") String baseUrl) {
    this.restTemplate = restTemplate;
    this.baseUrl = baseUrl;
  }

  @Override
  public DepositoDTO agregarDeposito(DepositoDTO deposito) {
    throw new UnsupportedOperationException("Operación no implementada en LogisticaClient");
  }

  @Override
  public DepositoDTO buscarDepositoPorID(String depositoID) {
    String url = String.format("%s/depositos/%s", baseUrl, depositoID);
    return restTemplate.getForObject(url, DepositoDTO.class);
  }

  @Override
  public AsignacionDTO buscarAsignacionPorPaqueteID(String paqueteID) {
    String url = String.format("%s/asignaciones/%s", baseUrl, paqueteID);
    return restTemplate.getForObject(url, AsignacionDTO.class);
  }

  @Override
  public DepositoDTO gestionarDonacion(
      String depositoID, String donacionID, String productoID, Integer cantidad) {
    String url = String.format("%s/asignaciones", baseUrl);
    var body = new java.util.HashMap<String, Object>();
    body.put("depositoID", depositoID);
    body.put("donacionID", donacionID);
    body.put("productoID", productoID);
    body.put("cantidad", cantidad);
    return restTemplate.postForObject(url, body, DepositoDTO.class);
  }

  @Override
  public void setAlgoritmoMM(String depositoID, TipoAlgoritmoEnum tipoAlgoritmo) {
    throw new UnsupportedOperationException("Operación no implementada en LogisticaClient");
  }

  @Override
  public AsignacionDTO ejecutarMatchmaking(
      String depositoID, PaqueteDTO paqueteDTO, List<NecesidadMaterialDTO> necesidades) {
    throw new UnsupportedOperationException("Operación no implementada en LogisticaClient");
  }

  @Override
  public void reportarEntrega(PaqueteDTO paqueteDTO) {
    throw new UnsupportedOperationException("Operación no implementada en LogisticaClient");
  }

  @Override
  public void setFachadaDonadoresYEntidades(FachadaDonadoresYEntidades fachadaDonadoresYEntidades) {
    // No aplica para el cliente HTTP
  }

  @Override
  public void setFachadaDonaciones(
      ar.edu.utn.dds.k3003.catedra.fachadas.FachadaDonaciones fachadaDonaciones) {
    // No aplica para el cliente HTTP
  }
}
