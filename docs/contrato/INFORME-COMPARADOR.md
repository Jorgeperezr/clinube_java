| Petición | Ruta | Resultado |
|---|---|---|
| salud | `GET /api/v1/health/` | igual a Django |
| disponibilidad | `GET /api/v1/ready/` | igual a Django |
| login correcto | `POST /api/v1/auth/login/` | igual a Django |
| login contraseña mala | `POST /api/v1/auth/login/` | igual a Django |
| login sin campos | `POST /api/v1/auth/login/` | igual a Django |
| login campos vacíos | `POST /api/v1/auth/login/` | igual a Django |
| login de un paciente | `POST /api/v1/auth/login/` | igual a Django |
| login usuario inactivo | `POST /api/v1/auth/login/` | igual a Django |
| login método GET | `GET /api/v1/auth/login/` | igual a Django |
| refresco sin campo | `POST /api/v1/auth/token/refresh/` | igual a Django |
| refresco con basura | `POST /api/v1/auth/token/refresh/` | igual a Django |
| perfil | `GET /api/v1/auth/me/` | igual a Django |
| perfil sin token | `GET /api/v1/auth/me/` | igual a Django |
| perfil token basura | `GET /api/v1/auth/me/` | igual a Django |
| cabecera mal formada | `GET /api/v1/auth/me/` | igual a Django |
| configuración de Google | `GET /api/v1/auth/google/config/` | igual a Django |
| aviso de privacidad | `GET /api/v1/legal/privacidad/` | igual a Django |
| condiciones de uso | `GET /api/v1/legal/condiciones/` | igual a Django |
| aviso con token basura | `GET /api/v1/legal/privacidad/` | igual a Django |
| aceptar sin token | `POST /api/v1/legal/condiciones/aceptar/` | igual a Django |
| aceptar versión vieja | `POST /api/v1/legal/condiciones/aceptar/` | igual a Django |
| marca (la crea) | `GET /api/v1/config/branding/` | igual a Django |
| marca otra vez | `GET /api/v1/config/branding/` | igual a Django |
| marca, recepción | `GET /api/v1/config/branding/` | igual a Django |
| marca sin token | `GET /api/v1/config/branding/` | igual a Django |
| pacientes | `GET /api/v1/patients/` | igual a Django |
| pacientes página 2 | `GET /api/v1/patients/?page=2` | igual a Django |
| pacientes página 3 (no existe) | `GET /api/v1/patients/?page=3` | igual a Django |
| pacientes buscando «lópez» | `GET /api/v1/patients/?search=l%C3%B3pez` | igual a Django |
| pacientes buscando «50%» | `GET /api/v1/patients/?search=50%25` | igual a Django |
| pacientes buscando «_» | `GET /api/v1/patients/?search=_` | igual a Django |
| pacientes buscando por cédula | `GET /api/v1/patients/?search=1700000001` | igual a Django |
| pacientes buscando vacío | `GET /api/v1/patients/?search=` | igual a Django |
| pacientes ordenados created_desc | `GET /api/v1/patients/?ordering=created_desc` | igual a Django |
| pacientes ordenados created_asc | `GET /api/v1/patients/?ordering=created_asc` | igual a Django |
| pacientes ordenados name_desc | `GET /api/v1/patients/?ordering=name_desc` | igual a Django |
| pacientes ordenados attention_desc | `GET /api/v1/patients/?ordering=attention_desc` | igual a Django |
| pacientes ordenados attention_asc | `GET /api/v1/patients/?ordering=attention_asc` | igual a Django |
| pacientes ordenados next_appt_asc | `GET /api/v1/patients/?ordering=next_appt_asc` | igual a Django |
| pacientes ordenados inventado | `GET /api/v1/patients/?ordering=inventado` | igual a Django |
| pacientes con búsqueda y página 2 | `GET /api/v1/patients/?search=a&page=2&ordering=name_desc` | igual a Django |
| pacientes, doctora | `GET /api/v1/patients/` | igual a Django |
| agenda de hoy | `GET /api/v1/agenda/view/?mode=daily` | igual a Django |
| agenda semanal | `GET /api/v1/agenda/view/?mode=weekly` | igual a Django |
| agenda mensual | `GET /api/v1/agenda/view/?mode=monthly` | igual a Django |
| agenda en un día sin citas | `GET /api/v1/agenda/view/?mode=daily&date=2020-01-01` | igual a Django |
| agenda con fecha de un dígito | `GET /api/v1/agenda/view/?mode=weekly&date=2026-1-5` | igual a Django |
| agenda modo desconocido | `GET /api/v1/agenda/view/?mode=raro` | igual a Django |
| agenda de la doctora | `GET /api/v1/agenda/view/?mode=monthly` | igual a Django |
| sala de espera | `GET /api/v1/appointments/waiting/` | igual a Django |
| sala de espera, doctora | `GET /api/v1/appointments/waiting/` | igual a Django |
| cumpleaños | `GET /api/v1/patients/birthdays/?days=7` | igual a Django |
| cumpleaños sin parámetro | `GET /api/v1/patients/birthdays/` | igual a Django |
| cumpleaños de hoy | `GET /api/v1/patients/birthdays/?days=0` | igual a Django |
| cumpleaños 60 días | `GET /api/v1/patients/birthdays/?days=999` | igual a Django |
| cumpleaños días no numéricos | `GET /api/v1/patients/birthdays/?days=abc` | igual a Django |
| ingresos del mes | `GET /api/v1/reports/financial/?date_from=2026-10-01&date_to=2026-10-31` | igual a Django |
| ingresos de siempre | `GET /api/v1/reports/financial/` | igual a Django |
| ingresos sin cobros | `GET /api/v1/reports/financial/?date_from=2020-01-01&date_to=2020-01-31` | igual a Django |
| ingresos, recepción (sin la función) | `GET /api/v1/reports/financial/` | igual a Django |
| morosidad | `GET /api/v1/reports/delinquency/` | igual a Django |
| morosidad, doctora (sin la función) | `GET /api/v1/reports/delinquency/` | igual a Django |
| morosidad, recepción | `GET /api/v1/reports/delinquency/` | igual a Django |
| seguimientos | `GET /api/v1/clinical/follow-ups/` | igual a Django |
| stock bajo | `GET /api/v1/inventory/alerts/low-stock/` | igual a Django |
| bandeja | `GET /api/v1/bandeja-app/resumen/` | igual a Django |
| bandeja, recepción | `GET /api/v1/bandeja-app/resumen/` | igual a Django |
| _comentario: desde aquí cambia el estado (acepta las condiciones) | `GET /api/v1/health/` | igual a Django |
| aceptar la primera vez | `POST /api/v1/legal/condiciones/aceptar/` | igual a Django |
| aceptar otra vez | `POST /api/v1/legal/condiciones/aceptar/` | igual a Django |
| perfil tras aceptar | `GET /api/v1/auth/me/` | igual a Django |
