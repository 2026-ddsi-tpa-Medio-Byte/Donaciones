[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/q5A4m_h4)
# 🧪 2026 - Trabajo Práctico Anual

## 👤 Datos del Alumno
- **Nombre:** Julian
- **Apellido:** Tettamanti

---

🧩 Componente Desarrollado
-Donaciones

---

🧩 Link al despliegue en Render
-- **Despliegue en Render:** https://donatrack-donaciones.onrender.com
- **Swagger UI:**  https://donatrack-donaciones.onrender.com/swagger-ui.html
- **API Docs (JSON):**

---

## Endpoints implementados

### Obligatorios
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | /donaciones | Registrar nueva donación |
| GET | /donaciones | Buscar por donadorID y fecha |
| GET | /donaciones/{id} | Buscar donación por ID |

### Adicionales
| Método | Ruta | Descripción |
|--------|------|-------------|
| PATCH | /donaciones/{id}/estado | Cambiar estado de donación |
| POST | /donaciones/{id}/quejas | Registrar queja en donación |
| POST | /productos | Agregar producto |
| GET | /productos | Listar todos los productos |
| GET | /productos/{id} | Buscar producto por ID (valida existencia → 404 si no existe) |
| POST | /identificadores | Agregar identificador |
| GET | /identificadores | Listar todos los identificadores |
| GET | /identificadores/{id} | Buscar identificador por ID |

---

## 🆕 Entrega 4 — cambios en Donaciones

> Detalle completo de flujos y scope del grupo en [docs/ENTREGA4.md](docs/ENTREGA4.md).

- **Validación de producto para Donadores y Entidades**: al crear una necesidad, el módulo "Donadores y Entidades" valida contra Donaciones que el producto exista vía `GET /productos/{id}`. Se agregó un `GlobalExceptionHandler` para que un producto inexistente devuelva **404** (antes 500).
- **Endpoints de consulta**: `GET /productos` y `GET /identificadores` (listar todos), útiles para administración y el bot.
- **Métricas extendidas**: nuevos contadores `donaciones.consultas`, `productos.registrados`, `identificadores.registrados` (además de los de Entrega 3).
- **Logs de trazabilidad**: cada acción y cada llamada saliente a otros módulos se loguea en consola (visible en Render).

### ⚠️ Importante

**ARCHIVOS PROTEGIDOS:**

> Los archivos de las carpetas "/catedra" y ".github/" están PROTEGIDOS, es decir, **NO PUEDEN MODIFICARLOS**.
Modificar estos archivos implica desaprobar inmediatamente la instancia de entrega del TPA.
