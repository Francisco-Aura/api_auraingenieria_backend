package api.aura.repository.modulo_tareas;

import api.aura.entity.modulo_tareas.mt_Comentario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface mt_ComentarioRepository extends JpaRepository<mt_Comentario, Long> {
    List<mt_Comentario> findByTarea_IdOrderByFechaCreacionAsc(Long tareaId);

    @Query("""
            SELECT c
            FROM mt_Comentario c
            JOIN FETCH c.tarea t
            JOIN FETCH c.usuarioAutor u
            WHERE t.clienteRelacionado.id = :clienteId
                AND c.fijado = true
            ORDER BY c.fechaCreacion DESC
            """)
    List<mt_Comentario> findFijadosByClienteId(@Param("clienteId") Integer clienteId);
}
