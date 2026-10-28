package ar.edu.utn.dds.k3003.eventos;

/**
 * Lo que responde {@code POST /admin/republicar-eventos}.
 *
 * @param eventosHabilitados false si falta {@code RABBITMQ_URL}: entonces no se intentó nada
 * @param total cuántos objetos tiene el módulo (productos más donaciones)
 * @param publicados cuántos eventos llegaron a RabbitMQ
 * @param fallidos cuántos se intentaron y fallaron; se puede volver a llamar al endpoint
 */
public record ResultadoRepublicacion(
    boolean eventosHabilitados, int total, int publicados, int fallidos) {}
