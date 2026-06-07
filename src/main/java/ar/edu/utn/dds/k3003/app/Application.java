package ar.edu.utn.dds.k3003.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "ar.edu.utn.dds.k3003")
@EnableJpaRepositories(basePackages = "ar.edu.utn.dds.k3003.repositories")
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
