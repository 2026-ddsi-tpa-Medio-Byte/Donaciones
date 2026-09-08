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

1. Valida que la cantidad sea mayor a cero y que el producto exista (validaciones locales).
2. Le pregunta a **Donadores** si el donador existe y si está habilitado.
3. Guarda la donación en estado `INGRESADA`.
4. Le avisa a **Logística**, que la encola para asignarla a una necesidad.

El orden importa: valida **antes** de persistir y avisa a Logística **después** de guardar. Si
avisara primero y después fallara una validación, quedaría una asignación encolada para una
donación inexistente.

También expone lo que otros módulos necesitan: `GET /productos/{id}` con 404 (lo usa Donadores
para validar necesidades), el listado por donador (lo usa Incentivos para las misiones) y
`PATCH /donaciones/{id}/estado` (lo usa Logística al reportar la entrega).

## Reglas de negocio no evidentes

- Con identificador `CODIGODEBARRAS`, la descripción del producto necesita **3+ palabras**.
- Con `QR`, el **nombre** necesita **cantidad par de letras**.
- No puede haber dos productos con el mismo nombre (ignorando mayúsculas).
- Un recurso inexistente devuelve **404, no 400**: es lo que permite a los otros módulos
  distinguir «no está» de «pediste mal».

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

100 tests, 0 fallos. Si algo de cátedra se pone en rojo, el cambio está mal.
