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

**Subcategoría de producto (Entrega 6).** `Producto` tiene `subcategoria`, texto libre y opcional
como la categoría, porque las consultas de urgencias y de calidad filtran por las dos. Es un dato:
no se valida (un vacío se guarda como null, para que «sin subcategoría» sea un solo grupo).
`ProductoDTO` es de cátedra y no la tiene, así que la API usa `dtos/ProductoConSubcategoriaDTO`:
`POST /productos` la acepta y los `GET /productos` la devuelven. Sin el campo, el alta funciona
igual que antes. `agregarProducto(ProductoDTO)` de la interfaz de cátedra crea el producto sin
subcategoría. La semilla (`POST /seed`) carga «Arroz», `alimentos` / `cereales`.

## Eventos para el modelo de lectura (Entrega 6, CQRS)

Cada cambio de estado se publica en RabbitMQ para que el Servicio de Consultas arme su copia en
MongoDB. El formato es el de `guias-entrega-6/CONTRATO-EVENTOS.md`: sobre común (`eventoId`,
`tipo`, `modulo`, `entidadId`, `ocurridoEn`, `datos`), `datos` con el estado **completo** y todas
las fechas en UTC con milisegundos y `Z`. Exchange `donatrack.eventos` (topic, lo creó Logística);
el módulo **solo publica**: no declara colas ni el exchange.

| Evento | Routing key | Cuándo |
|---|---|---|
| `PRODUCTO_CREADO` | `donaciones.producto.creado` | alta de producto |
| `PRODUCTO_MODIFICADO` | `donaciones.producto.modificado` | solo al republicar (no hay forma de modificar productos) |
| `DONACION_REGISTRADA` | `donaciones.donacion.registrada` | alta de donación, **después** de que Logística la aceptó |
| `DONACION_ESTADO_CAMBIADO` | `donaciones.donacion.estado_cambiado` | `PATCH /estado` (entrega) y paso a `CONQUEJA`; también al republicar |
| `DONACION_ELIMINADA` | `donaciones.donacion.eliminada` | la compensación borró la donación porque Logística falló |
| `MODULO_RESETEADO` | `donaciones.modulo.reseteado` | `DELETE /donaciones/reset` y `POST /seed` (`entidadId` `"*"`, `datos` `{}`) |

- **Después del commit.** La `Fachada` entrega el evento a Spring y `eventos/PublicadorEventos` lo
  recibe con `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. Casi ninguna
  operación abre transacción propia (cada `save` confirma la suya): ahí el evento sale en el
  momento, que ya es después del commit. El reset y la semilla sí son transaccionales y publican al
  confirmar; si se revierten, no sale nada.
- **Publicar nunca rompe la operación.** Si RabbitMQ falla: WARN en el log, `eventos.fallidos`
  (con tag `tipo`) en las métricas, y la operación responde igual. Lo mismo si falla armar el
  evento. Los que salen bien suman `eventos.publicados`. Conexión con 5 s de límite, porque se
  publica en el hilo del pedido, y sin health check de Rabbit, para que un broker caído no deje al
  módulo `DOWN`.
- **El alta y la compensación.** `DONACION_REGISTRADA` se arma con la hora del alta pero se envía
  recién cuando Logística aceptó: si Logística falla, la donación se borra y Mongo nunca la vio. La
  compensación igual publica `DONACION_ELIMINADA`, porque un republicar que corriera mientras se
  esperaba a Logística pudo haberla mandado, y un borrado de algo que Consultas no conoce no tiene
  efecto. Si el borrado falla, no se publica nada (la donación sigue existiendo).
- **`RABBITMQ_URL`** (`amqps://usuario:password@host/vhost`, la misma de Logística) se mapea a
  `spring.rabbitmq.addresses`. Va **solo** en las variables de entorno de Render. Sin ella el
  publicador queda deshabilitado: lo avisa una vez al arrancar y no se conecta nunca. Así corre en
  local y en los tests (`application-test.properties` la deja vacía a propósito).
- **`POST /admin/republicar-eventos`** (tag `Administración`) vuelve a publicar todos los productos
  (`PRODUCTO_MODIFICADO`) y todas las donaciones (`DONACION_ESTADO_CAMBIADO`) con `ocurridoEn` =
  ahora. Es la carga inicial de Mongo y la forma de resincronizar. Responde
  `{eventosHabilitados, total, publicados, fallidos}`.
- El `traceId` del pedido viaja en el header `X-Trace-Id` del mensaje.

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
Fachada.java                    lógica de negocio; arma y emite los eventos
controllers/DonacionController  los endpoints de negocio, productos e identificadores
controllers/EventosController   POST /admin/republicar-eventos
dtos/ProductoConSubcategoriaDTO el producto de la API, con subcategoría
eventos/                        catálogo (TipoEvento), sobre (Evento), armado (EventosDonaciones),
                                fechas en UTC (FechasUtc) y envío a RabbitMQ (PublicadorEventos)
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

225 tests, 0 fallos. Corren 183: los 42 saltados son los tests de cátedra de los otros módulos,
que se desactivan solos (`DonacionesTest` corre entero, 10/10). Si algo de cátedra se pone en
rojo, el cambio está mal. Los de eventos están en `src/test/.../eventos/`: `EventosConBrokerTest`
levanta un contexto aparte, con el publicador habilitado y el `RabbitTemplate` simulado, así que la
suite tarda unos segundos más.
