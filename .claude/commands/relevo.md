---
description: Guarda el estado de la sesión en .claude/relevo.md para que otra sesión pueda continuar
---

Prepara el relevo para una sesión nueva. Objetivo: que quien continúe gaste el mínimo de tokens en ponerse al día.

Argumento opcional del usuario (nota extra a incluir): $ARGUMENTS

Pasos:
1. Mira el estado real, no te fíes solo de la conversación: `git status --short` y `git log --oneline -5` en el backend y en `C:\Programas\api_auraingenieria_front_vue`.
2. Sobrescribe `C:\Programas\api_auraingenieria_backend\.claude\relevo.md` (no acumules versiones antiguas) con esta estructura, breve (máx. ~60 líneas):

```
# Relevo — <fecha> — <rama>

## Objetivo
Una o dos frases: qué se está intentando conseguir.

## Hecho
- Lo terminado y verificado (con el test o comprobación que lo respalda).

## Pendiente
- Siguiente paso concreto primero. Lo demás por orden.

## Decisiones y por qué
- Lo que se decidió y no se deduce del código (descartes incluidos).

## Archivos clave
- ruta:línea — para qué importa. Solo los necesarios; no copies contenido.

## Estado de verificación
- Comando exacto para comprobar y su último resultado (pasa/falla, cuántos tests).
- Qué NO se ha verificado.

## Leer antes de seguir
- Memorias, guías o skills concretas a consultar (solo las que hagan falta).

## Cuidado
- Cambios sin commitear que no deben perderse, trampas conocidas, cosas a medias.
```
3. No incluyas secretos, contraseñas ni datos personales.
4. No hagas commit ni push.
5. Termina diciendo al usuario, en 2–3 líneas, qué quedó guardado y que en la sesión nueva ejecute `/retomar`.
