package api.aura.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import api.aura.entity.Usuario;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

        @Query("SELECT u FROM Usuario u WHERE u.user = :username AND u.pass = :password")
        Usuario findByUser(@Param("username") String username, @Param("password") String password);

        List<Usuario> findByNivelAcceso(Integer nivelAcceso);
}
