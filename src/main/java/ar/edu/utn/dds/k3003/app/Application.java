package ar.edu.utn.dds.k3003.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ar.edu.utn.dds.k3003")
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
