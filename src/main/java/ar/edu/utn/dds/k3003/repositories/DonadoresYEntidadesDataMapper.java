package ar.edu.utn.dds.k3003.repositories;

import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.DonadorDTO;
import ar.edu.utn.dds.k3003.model.Donador;

public class DonadoresYEntidadesDataMapper {

  public DonadorDTO toDonadorDTO(Donador donador) {
    return new DonadorDTO(
        donador.getId(),
        donador.getNombre(),
        donador.getApellido(),
        donador.getEdad(),
        donador.getEmail(),
        donador.getNroDocumento(),
        donador.getDomicilio(),
        donador.getEstado(),
        donador.getCategoria());
  }

  public Donador toDonador(DonadorDTO donadorDTO) {
    return new Donador(
        donadorDTO.nombre(),
        donadorDTO.apellido(),
        donadorDTO.edad(),
        donadorDTO.email(),
        donadorDTO.nroDocumento(),
        donadorDTO.domicilio());
  }
}
