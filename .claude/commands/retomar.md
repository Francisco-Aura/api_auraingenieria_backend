---
description: Retoma el trabajo desde .claude/relevo.md leyendo solo lo imprescindible
---

Retoma el trabajo de la sesión anterior. Objetivo: ponerte al día gastando pocos tokens.

Indicación extra del usuario (opcional): $ARGUMENTS

Pasos:
1. Lee `C:\Programas\api_auraingenieria_backend\.claude\relevo.md`. Si no existe, dilo y pregunta qué hacer; no explores el repo a ciegas.
2. Contrasta con la realidad en una sola pasada: `git status --short` y `git log --oneline -3` (backend y `C:\Programas\api_auraingenieria_front_vue`). Si algo no coincide con el relevo, señálalo.
3. Lee solo los archivos de "Archivos clave" y lo que diga "Leer antes de seguir", y solo las partes que necesites (con offset/limit). No hagas barridos amplios del repo.
4. Responde al usuario con un resumen de máximo 6 líneas: objetivo, qué falta y el siguiente paso que vas a dar.
5. Si el siguiente paso es claro y no destructivo, empieza; si hay una decisión del usuario pendiente, pregúntala antes.
