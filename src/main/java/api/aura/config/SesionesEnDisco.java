package api.aura.config;

import org.apache.catalina.session.FileStore;
import org.apache.catalina.session.PersistentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Las sesiones del login se guardan en disco según se usan (como mucho unos segundos
 * después), no solo al parar el backend de forma ordenada. Así sobreviven a cualquier
 * reinicio, también a un kill -9 al desplegar, y nadie tiene que volver a entrar.
 * La seguridad no cambia: cerrar sesión, cambiar la contraseña o borrar el usuario
 * siguen anulándola (el fichero se borra al cerrar sesión y AuraUserFilter revalida
 * al usuario contra la BD).
 *
 * Carpeta: aura.sesiones.directorio (por defecto ~/.aura/sesiones), con una subcarpeta
 * por nombre de cookie (PRE y PRO no se mezclan) y permisos solo para el propio usuario.
 * Si no se puede usar, se queda la gestión estándar (server.servlet.session.persistent).
 * aura.sesiones.en-disco=false lo desactiva.
 */
@Configuration
public class SesionesEnDisco {

    private static final Logger log = LoggerFactory.getLogger(SesionesEnDisco.class);

    /** Lo único que la aplicación guarda en la sesión (UsuarioSesion, huella y marca de tiempo). */
    static final String CLASES_PERMITIDAS =
            "java\\.lang\\.(?:Boolean|Integer|Long|Number|String)|api\\.aura\\.config\\.UsuarioSesion";

    @Bean
    TomcatContextCustomizer sesionesEnDiscoCustomizer(
            @Value("${aura.sesiones.en-disco:true}") boolean activo,
            @Value("${aura.sesiones.directorio:${user.home}/.aura/sesiones}") String directorio,
            @Value("${server.servlet.session.cookie.name:AURA_SESSION}") String cookie) {
        return context -> {
            if (!activo) return;
            Path carpeta = preparar(Path.of(directorio, cookie.replaceAll("[^A-Za-z0-9_-]", "_")));
            if (carpeta == null) return;

            FileStore almacen = new FileStore();
            almacen.setDirectory(carpeta.toString());
            PersistentManager gestor = new PersistentManager();
            gestor.setStore(almacen);
            // Toda sesión usada desde la última pasada se escribe en la siguiente
            gestor.setMaxIdleBackup(0);
            // Una pasada en cada ciclo de Tomcat (cada 10 s) en vez de cada 6
            gestor.setProcessExpiresFrequency(1);
            gestor.setSessionAttributeValueClassNameFilter(CLASES_PERMITIDAS);
            context.setManager(gestor);
        };
    }

    private static Path preparar(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
            if (Files.getFileAttributeView(carpeta, PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(carpeta, PosixFilePermissions.fromString("rwx------"));
            }
            if (!Files.isWritable(carpeta)) throw new IOException("sin permiso de escritura");
            return carpeta.toAbsolutePath();
        } catch (IOException | RuntimeException e) {
            log.warn("Sesiones en disco desactivadas, no se puede usar {}: {}", carpeta, e.getMessage());
            return null;
        }
    }
}
