# Aura Ingeniería — backend

Responder y comentar en español.

## Stack
- Java 17, Spring Boot 3.5, Maven (`mvnw.cmd`), MariaDB (`AURA_DB_URL`, por defecto `127.0.0.1:3306/aura`), H2 en tests.
- JPA con `ddl-auto=none`: el esquema NO se genera solo. Referencia en `aura_esquema.sql`, `src/main/resources/BBDD` y `ScriptsBBDD.java`.
- PDF/Office: openpdf, pdfbox, poi-ooxml. Además: security, websocket, actuator.
- Paquete raíz `api.aura`: `config`, `controller`, `dto`, `entity`, `repository`, `service`, `util`.

## Frontend
Vue CLI en `C:\Programas\api_auraingenieria_front_vue` (`npm run serve`, `npm test`, `npm run test:planificador`, `npm run lint`).

## Comandos
```
./mvnw.cmd test                       # todos los tests
./mvnw.cmd test -Dtest=EstudioMotorTest
```

## Módulo Planificador (`modulo_planificador`)
- Mismo nombre de subpaquete en `controller`, `entity` (clases `pl_*`), `repository` (`pl_*Repository`) y `service`.
- Motor de estudio fotovoltaico: `service/modulo_planificador/estudio/` (Calculo, Curva, Simulacion, Finanzas, Informe, Pvgis, Sips, PreciosRegulados…). Orquesta `EstudioService`.
- Catálogo de equipos: `CatalogoEquipos` + `src/main/resources/catalogo/equipment_seed.json`.
- Documentos: Presupuesto/Propuesta/Oferta (`*Pdf`, `*Html`, `*Pptx`), plantillas en `src/main/resources/plantillas` y `propuesta`.
- Tests de contrato en `src/test/.../controller/modulo_planificador/*ContratoTest`.

## Reglas de trabajo
- Cambios pequeños y verificables; correr el test afectado antes de dar algo por hecho.
- No commitear ni hacer push sin que se pida.
- Sesiones largas: usar `/relevo` al cerrar y `/retomar` al empezar la siguiente (estado en `.claude/relevo.md`).
