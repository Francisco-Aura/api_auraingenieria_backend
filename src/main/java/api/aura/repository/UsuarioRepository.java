package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import api.aura.entity.Usuario;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

        /** Usuarios con ese login; la contraseña se comprueba en UsuarioService (cifrada con AES-GCM). */
        List<Usuario> findAllByUser(String user);

        List<Usuario> findByNivelAcceso(Integer nivelAcceso);

        Optional<Usuario> findFirstByNombreIgnoreCase(String nombre);
}
