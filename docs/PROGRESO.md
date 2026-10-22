# Progreso

Estado de las **202 rutas** de `/api/v1/`. Tres estados, y la
diferencia entre los dos últimos importa:

| Estado | Qué significa |
|---|---|
| **pendiente** | Todavía no existe en Kotlin. |
| **hecha** | Implementada y con sus pruebas en verde. **No basta:** las pruebas propias pueden pasar y la respuesta no ser la de Django. |
| **igual a Django** | Comprobada con `scripts/comparar-contrato`: mismo código HTTP, mismas claves, mismo orden, mismos valores. |

**31 de 202** igual a Django (15 %), y **4 más en parte** (falta un método o una exportación),
comprobadas con `scripts/comparar-contrato` (158 de 158 peticiones iguales y las 8 tablas tocadas idénticas en las dos bases, informe en `docs/contrato/INFORME-COMPARADOR.md`).

> **Estado a 2026-10-21:** el servidor compila sin errores ni avisos y la
> suite pasa entera dos veces (126 pruebas en UTC y 126 en
> Pacific/Kiritimati), incluidas las de integración contra PostgreSQL 16 con
> la línea base de Flyway y Hibernate en `validate`.
>
> **El panel entra, el escritorio se carga entero y las pantallas de
> Pacientes y Agenda funcionan** contra Kotlin (comprobado en el navegador con
> el panel de `frontend/` sin tocar): alta de un paciente con convenio, su
> ficha, el registro del aviso de privacidad, el cambio de convenio, y en la
> agenda confirmar, marcar la llegada, iniciar y finalizar la atención y
> crear una cita. La ficha pide además rutas de la historia clínica
> (odontograma, diagnósticos, formulario 033, alertas clínicas) y de WhatsApp,
> que son de las fases 5 y 13. **Los antecedentes** responden igual que Django
> por la API, pero el panel solo enseña su editor dentro de las alertas
> clínicas (`/patients/{id}/alertas/`, fase 5), así que hasta portarlas no se
> ven en la ficha.

> Este archivo dice la verdad. Si una ruta no responde igual que Django, lo
> dice aunque las pruebas propias pasen.

## Fase 0 — Cimientos

| Pieza | Estado |
|---|---|
| Proyecto Gradle con el módulo `backend` | hecha (Gradle 8.14.3 con wrapper) |
| `CLAUDE.md` con las convenciones | hecha |
| `docker-compose.yml` (PostgreSQL 16, Redis 7, Mailpit) | hecha |
| `scripts/comprobar-entorno.sh` | hecha |
| `scripts/start-local.sh` y `stop-local.sh` | hecha |
| Copia de `frontend/` y `movil/` desde clinube | hecha |
| Línea base de Flyway desde el esquema de Django (78 tablas) | **verificada**: reproduce el esquema byte a byte |
| Jackson como DRF (snake_case, decimales como texto, fechas en la zona de la clínica con microsegundos) | hecha |
| Formato de errores en las dos formas de Django | hecha |
| Paginación `{count, next, previous, results}` | hecha |
| Zona de la clínica y filtros por intervalos de instantes | hecha |
| Clínica por usuario, desde la base y no desde el token | hecha |
| Contraseñas `pbkdf2_sha256` de Django | hecha; probada contra hashes reales de Django (1.000, 390.000 y 1.000.000 iteraciones, y contraseña vacía) |
| JWT compatible con SimpleJWT, con las tablas `token_blacklist_*` | hecha |
| Comprobación de producción (no arranca con claves de ejemplo) | hecha |
| Comparador de contrato | hecha: 71 de 71 iguales; cada servidor con su copia de la base, sembrada con el ORM de Django |
| Respuestas de Django capturadas como fixtures (43, más el estado de la base tras aceptar) | hecha |
| Auditoría automática de las escrituras (el `AuditLogMiddleware` de Django) | hecha |
| Límites de intentos en Redis (600/min, 20/min, 5/min, 10/hora) | **pendiente** |

## Rutas

### Fase 0 · `common` — Cimientos y Plataforma (5/20)

| Métodos | Ruta | Estado |
|---|---|---|
| GET | `/api/v1/health/` | igual a Django |
| GET | `/api/v1/legal/condiciones/` | igual a Django |
| POST | `/api/v1/legal/condiciones/aceptar/` | igual a Django |
| GET | `/api/v1/legal/privacidad/` | igual a Django |
| GET | `/api/v1/platform/audit/` | pendiente |
| GET POST | `/api/v1/platform/clinics/` | pendiente |
| GET PUT PATCH | `/api/v1/platform/clinics/<uuid:pk>/` | pendiente |
| GET POST PATCH | `/api/v1/platform/clinics/<uuid:pk>/admin/` | pendiente |
| POST | `/api/v1/platform/clinics/<uuid:pk>/admin/reset-password/` | pendiente |
| GET PATCH | `/api/v1/platform/clinics/<uuid:pk>/identidad/` | pendiente |
| GET PATCH | `/api/v1/platform/config/` | pendiente |
| POST | `/api/v1/platform/config/correo-de-prueba/` | pendiente |
| GET | `/api/v1/platform/funcionalidades/` | pendiente |
| PATCH | `/api/v1/platform/funcionalidades/<str:clave>/` | pendiente |
| GET | `/api/v1/platform/overview/` | pendiente |
| GET | `/api/v1/platform/solicitudes/` | pendiente |
| PUT PATCH | `/api/v1/platform/solicitudes/<uuid:pk>/` | pendiente |
| POST | `/api/v1/publico/solicitudes/` | pendiente |
| POST | `/api/v1/publico/solicitudes/<uuid:pk>/confirmar/` | pendiente |
| GET | `/api/v1/ready/` | igual a Django |

### Fase 1 · `accounts` — Cuentas (3/17)

| Métodos | Ruta | Estado |
|---|---|---|
| GET | `/api/v1/audit-logs/` | pendiente |
| POST | `/api/v1/auth/change-password/` | pendiente |
| POST | `/api/v1/auth/google/` | pendiente |
| GET | `/api/v1/auth/google/config/` | igual a Django |
| POST | `/api/v1/auth/login/` | igual a Django |
| GET | `/api/v1/auth/me/` | igual a Django |
| GET PATCH | `/api/v1/auth/me/preferencias/` | pendiente |
| GET | `/api/v1/auth/otp/canal/` | pendiente |
| POST | `/api/v1/auth/otp/request/` | pendiente |
| POST | `/api/v1/auth/otp/verify/` | pendiente |
| POST | `/api/v1/auth/recovery/confirm/` | pendiente |
| POST | `/api/v1/auth/recovery/request/` | pendiente |
| POST | `/api/v1/me/device-tokens/` | pendiente |
| GET POST | `/api/v1/users/` | pendiente |
| GET PUT PATCH | `/api/v1/users/<uuid:pk>/` | pendiente |
| POST | `/api/v1/users/<uuid:pk>/invitar/` | pendiente |
| GET | `/api/v1/users/funciones/` | pendiente |

### Fase 2 · `configuration` — Configuración y catálogos (2/16)

| Métodos | Ruta | Estado |
|---|---|---|
| GET POST | `/api/v1/config/agreements/` | GET igual a Django · POST pendiente |
| GET PUT PATCH DELETE | `/api/v1/config/agreements/<uuid:pk>/` | pendiente |
| POST | `/api/v1/config/backup/` | pendiente |
| POST | `/api/v1/config/backup/decrypt/` | pendiente |
| GET | `/api/v1/config/backup/herramientas/` | pendiente |
| GET PATCH | `/api/v1/config/branding/` | GET igual a Django · PATCH pendiente |
| GET PATCH DELETE | `/api/v1/config/document-appearance/` | pendiente |
| GET PATCH | `/api/v1/config/modulos/` | pendiente |
| GET | `/api/v1/config/parameters/` | pendiente |
| GET PUT PATCH | `/api/v1/config/parameters/<uuid:pk>/` | pendiente |
| GET PUT | `/api/v1/config/price-matrix/` | pendiente |
| GET POST | `/api/v1/config/tarifarios/` | pendiente |
| GET PUT PATCH DELETE | `/api/v1/config/tarifarios/<uuid:pk>/` | pendiente |
| POST | `/api/v1/config/temas/previsualizar/` | pendiente |
| GET POST | `/api/v1/config/treatments/` | pendiente |
| GET PUT PATCH DELETE | `/api/v1/config/treatments/<uuid:pk>/` | pendiente |

### Fase 2 · `specialties` — Configuración y catálogos (0/5)

| Métodos | Ruta | Estado |
|---|---|---|
| GET POST | `/api/v1/patients/<uuid:pk>/specialty-forms/` | pendiente |
| GET POST | `/api/v1/specialties/` | pendiente |
| GET PUT PATCH DELETE | `/api/v1/specialties/<uuid:pk>/` | pendiente |
| GET | `/api/v1/specialties/<uuid:pk>/form-template/` | pendiente |
| GET PUT PATCH | `/api/v1/specialty-forms/<uuid:pk>/` | pendiente |

### Fase 3 · `patients` — Pacientes (5/9)

| Métodos | Ruta | Estado |
|---|---|---|
| GET POST | `/api/v1/patients/` | igual a Django |
| GET PUT PATCH DELETE | `/api/v1/patients/<uuid:pk>/` | igual a Django |
| GET POST DELETE | `/api/v1/patients/<uuid:pk>/app-access/` | GET igual a Django · POST y DELETE pendientes |
| GET POST | `/api/v1/patients/<uuid:pk>/aviso-privacidad/` | igual a Django |
| GET POST | `/api/v1/patients/<uuid:pk>/documents/` | pendiente |
| DELETE | `/api/v1/patients/<uuid:pk>/documents/<int:doc_id>/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/documents/<int:doc_id>/file/` | pendiente |
| GET PUT PATCH | `/api/v1/patients/<uuid:pk>/medical-background/` | igual a Django |
| GET | `/api/v1/patients/<uuid:pk>/treatment-history/` | pendiente |

### Fase 4 · `agenda` — Agenda (13/14)

| Métodos | Ruta | Estado |
|---|---|---|
| GET | `/api/v1/agenda/view/` | igual a Django |
| GET POST | `/api/v1/appointments/` | igual a Django, salvo el doctor o tratamiento de otra clínica (diferencia deliberada) |
| GET PUT PATCH | `/api/v1/appointments/<uuid:pk>/` | igual a Django, salvo el doctor o tratamiento de otra clínica (diferencia deliberada) |
| POST | `/api/v1/appointments/<uuid:pk>/cancel/` | igual a Django |
| POST | `/api/v1/appointments/<uuid:pk>/checkin/` | igual a Django; sin el aviso por WhatsApp al doctor (fase 13) |
| POST | `/api/v1/appointments/<uuid:pk>/checkout/` | igual a Django |
| POST | `/api/v1/appointments/<uuid:pk>/confirm/` | igual a Django |
| POST | `/api/v1/appointments/<uuid:pk>/reschedule/` | igual a Django |
| POST | `/api/v1/appointments/<uuid:pk>/start/` | igual a Django |
| GET | `/api/v1/appointments/waiting/` | igual a Django |
| ? | `/api/v1/calendar/<uuid:token>.ics` | igual a Django (el 404 va en JSON, no en HTML) |
| GET | `/api/v1/doctors/` | igual a Django |
| GET | `/api/v1/doctors/<uuid:pk>/calendar-url/` | igual a Django |
| GET POST | `/api/v1/doctors/me/signature/` | pendiente |

### Fase 5 · `clinical` — Historia clínica (1/41)

| Métodos | Ruta | Estado |
|---|---|---|
| GET | `/api/v1/cie10/` | pendiente |
| GET | `/api/v1/clinical/follow-ups/` | igual a Django |
| DELETE | `/api/v1/clinical/plan-template-items/<uuid:pk>/` | pendiente |
| GET POST | `/api/v1/clinical/plan-templates/` | pendiente |
| POST | `/api/v1/clinical/plan-templates/<uuid:pk>/items/` | pendiente |
| GET POST | `/api/v1/consent-templates/` | pendiente |
| GET PUT PATCH DELETE | `/api/v1/consent-templates/<uuid:pk>/` | pendiente |
| GET PUT PATCH | `/api/v1/consents/<uuid:pk>/detail/` | pendiente |
| GET | `/api/v1/consents/<uuid:pk>/pdf/` | pendiente |
| POST | `/api/v1/consents/<uuid:pk>/sign/` | pendiente |
| GET PUT PATCH DELETE | `/api/v1/evolutions/<uuid:pk>/` | pendiente |
| GET | `/api/v1/evolutions/<uuid:pk>/prescription-pdf/` | pendiente |
| PATCH | `/api/v1/exam-requests/<uuid:pk>/report/` | pendiente |
| GET PUT PATCH | `/api/v1/form033/<uuid:pk>/` | pendiente |
| GET | `/api/v1/odontogram-states/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/alertas/` | pendiente |
| POST | `/api/v1/patients/<uuid:pk>/alertas/revisar/` | pendiente |
| POST | `/api/v1/patients/<uuid:pk>/apply-plan-template/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/certificado-atencion/` | pendiente |
| GET PUT PATCH | `/api/v1/patients/<uuid:pk>/clinical-record/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/clinical-record/export-pdf/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/consents/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/cpo-ceo/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/diagnoses/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/evolutions/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/exam-requests/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/exam-requests/<uuid:exam_id>/pdf/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/form033/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/form033/export-pdf/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/form033/export-xlsx/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/odontogram/current/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/periodontal-exams/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/radiographs/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/tooth-records/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/treatment-plans/` | pendiente |
| GET PUT PATCH | `/api/v1/periodontal-teeth/<uuid:pk>/` | pendiente |
| DELETE | `/api/v1/tooth-records/<uuid:pk>/` | pendiente |
| PUT PATCH | `/api/v1/treatment-plan-items/<uuid:pk>/` | pendiente |
| GET PUT PATCH | `/api/v1/treatment-plans/<uuid:pk>/` | pendiente |
| POST | `/api/v1/treatment-plans/<uuid:pk>/generate-budget/` | pendiente |
| POST | `/api/v1/treatment-plans/<uuid:pk>/items/` | pendiente |

### Fase 7 · `billing` — Cobros (3/17)

| Métodos | Ruta | Estado |
|---|---|---|
| GET PUT PATCH | `/api/v1/budgets/<uuid:pk>/` | pendiente |
| POST | `/api/v1/budgets/<uuid:pk>/approve/` | pendiente |
| POST | `/api/v1/budgets/<uuid:pk>/generate-payment-plan/` | pendiente |
| POST | `/api/v1/budgets/<uuid:pk>/items/` | pendiente |
| GET POST | `/api/v1/doctors/<uuid:pk>/fees/` | pendiente |
| POST | `/api/v1/installments/<uuid:pk>/pay/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/account-statement/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/budgets/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/payments/` | pendiente |
| GET | `/api/v1/patients/birthdays/` | igual a Django |
| GET | `/api/v1/payment-plans/<uuid:pk>/installments/` | pendiente |
| GET | `/api/v1/reports/appointments-summary/` | pendiente |
| GET | `/api/v1/reports/delinquency/` | igual a Django |
| GET | `/api/v1/reports/financial/` | igual a Django salvo `?export=excel` (pendiente: responde 501) |
| GET | `/api/v1/reports/inventory/` | pendiente |
| GET | `/api/v1/reports/new-patients/` | pendiente |
| GET | `/api/v1/reports/production-by-doctor/` | pendiente |

### Fase 8 · `inventory` — Inventario y reportes (1/6)

| Métodos | Ruta | Estado |
|---|---|---|
| GET | `/api/v1/inventory/alerts/expiring/` | pendiente |
| GET | `/api/v1/inventory/alerts/low-stock/` | igual a Django |
| GET | `/api/v1/inventory/movements/` | pendiente |
| GET POST | `/api/v1/products/` | pendiente |
| GET PUT PATCH | `/api/v1/products/<uuid:pk>/` | pendiente |
| GET POST | `/api/v1/products/<uuid:pk>/batches/` | pendiente |

### Fase 9 · `finanzas` — Finanzas (0/29)

| Métodos | Ruta | Estado |
|---|---|---|
| GET POST | `/api/v1/finanzas/caja/` | pendiente |
| GET POST | `/api/v1/finanzas/categorias/` | pendiente |
| PUT PATCH | `/api/v1/finanzas/categorias/<uuid:pk>/` | pendiente |
| GET PATCH | `/api/v1/finanzas/cobros/` | pendiente |
| GET PATCH | `/api/v1/finanzas/cobros/<uuid:pk>/` | pendiente |
| GET | `/api/v1/finanzas/cobros/<uuid:pk>/comprobante/` | pendiente |
| GET PATCH | `/api/v1/finanzas/configuracion/` | pendiente |
| GET POST | `/api/v1/finanzas/decimos/` | pendiente |
| GET POST | `/api/v1/finanzas/egresos/` | pendiente |
| GET PUT PATCH | `/api/v1/finanzas/egresos/<uuid:pk>/` | pendiente |
| POST | `/api/v1/finanzas/egresos/<uuid:pk>/anular/` | pendiente |
| GET | `/api/v1/finanzas/egresos/<uuid:pk>/comprobante/` | pendiente |
| POST | `/api/v1/finanzas/egresos/<uuid:pk>/pagar/` | pendiente |
| GET | `/api/v1/finanzas/equipo/` | pendiente |
| PATCH | `/api/v1/finanzas/equipo/<uuid:pk>/` | pendiente |
| GET | `/api/v1/finanzas/exportar/` | pendiente |
| GET POST | `/api/v1/finanzas/liquidaciones/` | pendiente |
| POST | `/api/v1/finanzas/liquidaciones/<uuid:pk>/anular/` | pendiente |
| POST | `/api/v1/finanzas/liquidaciones/<uuid:pk>/pagar/` | pendiente |
| GET | `/api/v1/finanzas/liquidaciones/<uuid:pk>/pdf/` | pendiente |
| GET | `/api/v1/finanzas/liquidaciones/previa/` | pendiente |
| GET POST | `/api/v1/finanzas/obligaciones/` | pendiente |
| GET | `/api/v1/finanzas/resumen/` | pendiente |
| GET POST | `/api/v1/finanzas/roles/` | pendiente |
| POST | `/api/v1/finanzas/roles/<uuid:pk>/anular/` | pendiente |
| POST | `/api/v1/finanzas/roles/<uuid:pk>/pagar/` | pendiente |
| GET | `/api/v1/finanzas/roles/<uuid:pk>/pdf/` | pendiente |
| POST | `/api/v1/finanzas/roles/generar/` | pendiente |
| GET | `/api/v1/finanzas/roles/previa/` | pendiente |

### Fase 10 · `app_paciente` — App del paciente (1/16)

| Métodos | Ruta | Estado |
|---|---|---|
| GET | `/api/v1/app/citas/` | pendiente |
| POST | `/api/v1/app/citas/<uuid:pk>/confirmar/` | pendiente |
| GET | `/api/v1/app/clinica/` | pendiente |
| GET | `/api/v1/app/cuenta/` | pendiente |
| GET | `/api/v1/app/indicaciones/` | pendiente |
| GET | `/api/v1/app/logros/` | pendiente |
| GET | `/api/v1/app/me/` | pendiente |
| GET POST | `/api/v1/app/mensajes/` | pendiente |
| GET | `/api/v1/app/saldo/` | pendiente |
| GET POST | `/api/v1/app/solicitudes-cita/` | pendiente |
| GET | `/api/v1/bandeja-app/mensajes/` | pendiente |
| POST | `/api/v1/bandeja-app/mensajes/<uuid:pk>/responder/` | pendiente |
| GET | `/api/v1/bandeja-app/resumen/` | igual a Django |
| GET | `/api/v1/bandeja-app/solicitudes/` | pendiente |
| POST | `/api/v1/bandeja-app/solicitudes/<uuid:pk>/agendar/` | pendiente |
| POST | `/api/v1/bandeja-app/solicitudes/<uuid:pk>/rechazar/` | pendiente |

### Fase 10 · `logros` — App del paciente (0/5)

| Métodos | Ruta | Estado |
|---|---|---|
| GET POST | `/api/v1/logros/` | pendiente |
| GET PUT PATCH DELETE | `/api/v1/logros/<uuid:pk>/` | pendiente |
| POST | `/api/v1/logros/evaluar/` | pendiente |
| POST | `/api/v1/logros/otorgar/` | pendiente |
| GET | `/api/v1/patients/<uuid:pk>/logros/` | pendiente |

### Fase 13 · `whatsapp` — WhatsApp (0/4)

| Métodos | Ruta | Estado |
|---|---|---|
| GET POST | `/api/v1/config/whatsapp-templates/` | pendiente |
| GET PATCH | `/api/v1/config/whatsapp/` | pendiente |
| GET POST | `/api/v1/patients/<uuid:pk>/whatsapp-optin/` | pendiente |
| POST | `/api/v1/whatsapp-optin/<uuid:pk>/revoke/` | pendiente |

### Fase — · `terceros` — Terceros (1/3)

| Métodos | Ruta | Estado |
|---|---|---|
| POST | `/api/v1/auth/token/refresh/` | igual a Django |
| GET | `/api/v1/schema/` | pendiente |
| GET | `/api/v1/schema/swagger-ui/` | pendiente |

