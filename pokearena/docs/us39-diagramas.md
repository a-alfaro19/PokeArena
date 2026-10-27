# Diagramas de secuencia de US 39

Fuentes editables Mermaid: [búsqueda inicial](diagrams/us39-initial-search.mmd), [siguiente página](diagrams/us39-next-page.mmd) y [sin coincidencias](diagrams/us39-no-matches.mmd). El escenario sin filtros utiliza el diagrama inicial con cero criterios activos; los filtros individuales y combinados comparten ese mismo flujo. Se pueden pegar en bloques Mermaid en la wiki de Azure DevOps.

## Declaración de uso de IA

Estos diagramas fueron redactados con asistencia de Codex el 27 de octubre de 2026, tomando el requerimiento aportado por el usuario y contrastando las clases implementadas. No se utilizó otro generador ni una conversación independiente de diagramación. El equipo debe revisarlos antes de presentarlos como evidencia académica.

El prompt utilizado es la especificación íntegra recibida, conservada en [us39-solicitud.txt](us39-solicitud.txt), particularmente: «Diagramas de secuencia editables para los escenarios de éxito, coherentes con el código» y «Declaración de uso de IA y prompts utilizados en los diagramas, como exige el sprint». También se aplicaron las instrucciones AGENTS.md aportadas por el usuario sobre simplicidad, comentarios y documentación en prosa. No hay prompts adicionales ocultos de generación de diagramas.

Decisiones reflejadas: autorización antes de la consulta, puerto dedicado, primaria PostgreSQL, instantánea materializada, orden por código, capacidad y caducidad, token persistido por página y resultado vacío con HTTP 200. Los diagramas describen escenarios satisfactorios; los errores se documentan en el contrato OpenAPI.

## Corrección posterior sobre el commit 8b1c72c

El mismo día se actualizaron solo los flujos inicial y vacío para reflejar el sondeo sin reserva y la validación de repeticiones después de autenticar. El flujo de continuación no cambió: retiene el recorrido incluso tras su última página. Prompt aplicado: solicitud del usuario de corregir capacidad, protección de pruebas, precedencia de autenticación y ejemplo PowerShell; en particular «Actualiza OpenAPI y diagramas solo si cambia el comportamiento que describen». Se utilizó Codex en esta conversación, sin otro generador.
