# Módulo Donaciones — DonaTrack

Uno de los cuatro microservicios del TP anual de DDS (K3003, UTN 2026). Registra donaciones,
productos e identificadores, y coordina con Donadores y Logística.

El contexto completo del sistema está en `AGENTS.md` de la carpeta padre (`TP DDSI/`).

## Reglas que no se rompen

- **No modificar `catedra/` ni `.github/`**: implica desaprobar la instancia de entrega.
- Los tests de cátedra tienen que seguir en verde: **10/10 en `DonacionesTest`**.
- Commits con la identidad del alumno, sin `Co-Authored-By`.
- Credenciales solo en variables de entorno de Render.

## Qué hace este módulo

Es el que sabe que una donación existe. Al registrar una:

1. Valida que `donadorID`, `depositoID` y `productoID` vengan, que la cantidad sea mayor a cero
   y que el producto exista (validaciones locales).
2. Le pregunta a **Donadores** si el donador existe y si está habilitado.
3. Guarda la donación en estado `INGRESADA`.
4. Le avisa a **Logística**, que la encola para asignarla a una necesidad. Si Logística falla,
   **borra la donación** que acaba de guardar y devuelve el error.

El orden importa: valida **antes** de persistir y avisa a Logística **después** de guardar. Si
avisara primero y después fallara una validación, quedaría una asignación encolada para una
donación inexistente.

**La compensación del paso 4.** Se guarda antes de avisar porque Logística necesita el id, y la
llamada HTTP no forma parte de la transacción: un rollback no la deshace. Por eso el borrado es
explícito (`avisarALogisticaOCompensar`). Sin él, si Logística fallaba la donación quedaba
`INGRESADA` para siempre, sin asignación que la hiciera avanzar. Lo que no cubre es un timeout en
el que Logística sí la procesó: del otro lado queda una asignación apuntando a una donación
borrada. Por eso se loguea en `ERROR` con el id, para reconciliar a mano.

También expone lo que otros módulos necesitan: `GET /productos/{id}` con 404 (lo usa Donadores
para validar necesidades), el listado por donador (lo usa Incentivos para las misiones) y
`PATCH /donaciones/{id}/estado` (lo usa Logística al reportar la entrega).

## Reglas de negocio no evidentes

- Con identificador `CODIGODEBARRAS`, la descripción del producto necesita **3+ palabras**.
- Con `QR`, el **nombre** necesita **cantidad par de letras**.
- No puede haber dos productos con el mismo nombre (ignorando mayúsculas).
- Un recurso inexistente devuelve **404, no 400**: es lo que permite a los otros módulos
  distinguir «no está» de «pediste mal».
- **La queja solo se acepta sobre una donación `ACEPTADA`** (entregada: la pone Logística al
  reportar la entrega), y **una sola por donación**. Sobre una `INGRESADA` o una `CONQUEJA`
  responde 409, y lo decide **antes** de llamar a Donadores para que la queja rechazada no le
  sume al donador. El estado pasa a `CONQUEJA` recién cuando Donadores aceptó la queja (lo exige
  `testRegistrarQuejaFallido` de cátedra), y la respuesta devuelve lo que quedó guardado.
- `PATCH /donaciones/{id}/estado` anota el cambio en el historial de estados. No valida
  transiciones: Logística depende de ese endpoint tal como está.
- `GET /donaciones?donadorID=X` filtra por donador **haya o no fecha** (antes, sin fecha devolvía
  las de todo el sistema). Sin donador lista todas. Una fecha mal formada es 400.

## Códigos HTTP

Cada motivo de rechazo tiene su código, porque quien llama decide con él. El cuerpo es siempre
`{"error": "<mensaje>"}`. Lo resuelve `controllers/GlobalExceptionHandler`.

| Código | Cuándo | Excepción |
|---|---|---|
| 400 | campos vacíos, cantidad ≤ 0, queja sin descripción, fecha mal formada, reglas de producto | `DatoInvalidoException` y el resto de `RuntimeException` |
| 403 | el donador no está habilitado; el mensaje empieza con «No puede donar» | `DonadorNoHabilitadoException` |
| 404 | donación, producto, identificador o donador inexistente | `NoSuchElementException`, `DonadorNoEncontradoException` |
| 409 | queja sobre una donación que no está `ACEPTADA` | `DonacionNoQuejableException` |
| 502 | Donadores o Logística caídos o con 5xx; el mensaje nombra el módulo | `ModuloNoDisponibleException` |

Los clientes HTTP (`DonadoresYEntidadesClient`, `LogisticaClient`) traducen una caída o un 5xx a
`ModuloNoDisponibleException`; un 4xx de Logística se deja pasar y sale como 400.

## Estructura

```
Fachada.java                    lógica de negocio
controllers/DonacionController  todos los endpoints
logging/DatadogLogAppender      envía logs a Datadog en lotes, nunca bloquea
logging/TrazabilidadFilter      pone traceId y requestId en el MDC
config/OpenApiConfig            agrupa el Swagger por propósito
MetricasService                 contadores y métricas de integración
```

## Swagger

Los endpoints se agrupan por para qué sirven, con `@Tag` a **nivel de método**:
`Flujo principal`, `Administración`, `Precondiciones`, `Consultas` (sin descripciones ni números en los tags).
Si la clase también tuviera `@Tag`, cada endpoint aparecería duplicado.

## Antes de terminar un cambio

```bash
mvn test
```

165 tests, 0 fallos. Corren 123: los 42 saltados son los tests de cátedra de los otros módulos,
que se desactivan solos. Si algo de cátedra se pone en rojo, el cambio está mal.
