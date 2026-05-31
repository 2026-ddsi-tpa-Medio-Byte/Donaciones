package ar.edu.utn.dds.k3003;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

  @Value("${APP.URL_DONADORES:http://localhost:8081}")
  private String urlDonadores;

  @Value("${URL_LOGISTICA:http://localhost:8082}")
  private String urlLogistica;

  @Bean
  public Fachada fachada() {
    Fachada fachada = new Fachada();
    // Cuando se integren los módulos, acá se instanciarán los clientes HTTP
    // y se llamará a fachada.setFachadaDonadoresYEntidades(...) y setFachadaLogistica(...)
    // Por ahora las URLs quedan disponibles para cuando llegue esa etapa
    return fachada;
  }
}