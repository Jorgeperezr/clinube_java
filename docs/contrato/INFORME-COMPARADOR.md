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
| _comentario: pantallas de Pacientes y Agenda; primero lo que solo lee | `GET /api/v1/health/` | igual a Django |
| ficha | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| ficha inexistente | `GET /api/v1/patients/00000000-0000-4000-8000-000000000000/` | igual a Django |
| ficha de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/` | igual a Django |
| ficha, doctora | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| convenios | `GET /api/v1/config/agreements/` | igual a Django |
| acceso a la app | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/app-access/` | igual a Django |
| acceso a la app con teléfono y correo | `GET /api/v1/patients/b6993fcd-fc33-55b8-9059-d7eb18482ee3/app-access/` | igual a Django |
| acceso a la app, doctora | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/app-access/` | igual a Django |
| doctores | `GET /api/v1/doctors/` | igual a Django |
| citas | `GET /api/v1/appointments/` | igual a Django |
| citas pendientes | `GET /api/v1/appointments/?status=pending` | igual a Django |
| citas de un doctor | `GET /api/v1/appointments/?doctor=33030969-3883-589d-bc77-8a3916a2253c` | igual a Django |
| citas de un paciente | `GET /api/v1/appointments/?patient=b6993fcd-fc33-55b8-9059-d7eb18482ee3` | igual a Django |
| citas con filtros malos | `GET /api/v1/appointments/?doctor=abc&status=raro&patient=xyz` | igual a Django |
| citas de un doctor inexistente | `GET /api/v1/appointments/?doctor=00000000-0000-4000-8000-000000000000` | igual a Django |
| citas de la doctora | `GET /api/v1/appointments/` | igual a Django |
| cita | `GET /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/` | igual a Django |
| cita inexistente | `GET /api/v1/appointments/00000000-0000-4000-8000-000000000000/` | igual a Django |
| calendario de la doctora (.ics) | `GET /api/v1/calendar/5fa9c0cb-b38e-5347-9cd6-61b41ba03aab.ics` | igual a Django |
| dirección del calendario | `GET /api/v1/doctors/db326626-1f49-5458-b448-7b9e96fa589e/calendar-url/` | igual a Django |
| dirección del calendario de otro, doctora | `GET /api/v1/doctors/33030969-3883-589d-bc77-8a3916a2253c/calendar-url/` | igual a Django |
| dirección del calendario inexistente | `GET /api/v1/doctors/00000000-0000-4000-8000-000000000000/calendar-url/` | igual a Django |
| dirección del calendario, recepción | `GET /api/v1/doctors/db326626-1f49-5458-b448-7b9e96fa589e/calendar-url/` | igual a Django |
| _comentario: desde aquí se escribe | `GET /api/v1/health/` | igual a Django |
| alta vacía | `POST /api/v1/patients/` | igual a Django |
| alta con todo mal | `POST /api/v1/patients/` | igual a Django |
| alta con fecha vacía y convenio raro | `POST /api/v1/patients/` | igual a Django |
| alta con convenio inexistente | `POST /api/v1/patients/` | igual a Django |
| alta con convenio ajeno | `POST /api/v1/patients/` | igual a Django |
| alta con cédula repetida | `POST /api/v1/patients/` | igual a Django |
| alta, doctora | `POST /api/v1/patients/` | igual a Django |
| alta con cuerpo lista | `POST /api/v1/patients/` | igual a Django |
| alta buena | `POST /api/v1/patients/` | igual a Django |
| alta sin fecha, teléfono número | `POST /api/v1/patients/` | igual a Django |
| alta con correos raros | `POST /api/v1/patients/` | igual a Django |
| patch nombre vacío | `PATCH /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| patch cédula repetida | `PATCH /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| patch quita el convenio (sin agreement_name) | `PATCH /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| put incompleto | `PUT /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| put completo (con agreement_name nulo) | `PUT /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| patch, doctora | `PATCH /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| antecedentes, recepción | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/medical-background/` | igual a Django |
| antecedentes (los crea) | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/medical-background/` | igual a Django |
| antecedentes put malo | `PUT /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/medical-background/` | igual a Django |
| antecedentes patch | `PATCH /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/medical-background/` | igual a Django |
| antecedentes booleano «y» | `PATCH /api/v1/patients/1ef52a3a-b8a2-5758-896d-57935d332c51/medical-background/` | igual a Django |
| antecedentes «NULL» y número | `PATCH /api/v1/patients/1ef52a3a-b8a2-5758-896d-57935d332c51/medical-background/` | igual a Django |
| antecedentes de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/medical-background/` | igual a Django |
| aviso | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/aviso-privacidad/` | igual a Django |
| aviso con medio malo | `POST /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/aviso-privacidad/` | igual a Django |
| aviso registrado | `POST /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/aviso-privacidad/` | igual a Django |
| aviso otra vez | `POST /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/aviso-privacidad/` | igual a Django |
| aviso, doctora | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/aviso-privacidad/` | igual a Django |
| cita: crear vacía | `POST /api/v1/appointments/` | igual a Django |
| cita: fin antes del inicio | `POST /api/v1/appointments/` | igual a Django |
| cita: en el pasado | `POST /api/v1/appointments/` | igual a Django |
| cita: fecha mala | `POST /api/v1/appointments/` | igual a Django |
| cita: paciente ajeno | `POST /api/v1/appointments/` | igual a Django |
| cita: claves número, lista y bool | `POST /api/v1/appointments/` | igual a Django |
| cita: nulos | `POST /api/v1/appointments/` | igual a Django |
| cita: paciente moroso | `POST /api/v1/appointments/` | igual a Django |
| cita: moroso con override | `POST /api/v1/appointments/` | igual a Django |
| cita: cruzada | `POST /api/v1/appointments/` | igual a Django |
| cita: separador raro y siete decimales | `POST /api/v1/appointments/` | igual a Django |
| cita: notas nulas | `POST /api/v1/appointments/` | igual a Django |
| cita: doctora sin la función | `POST /api/v1/appointments/` | igual a Django |
| cita: patch notas (el estado no cambia) | `PATCH /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/` | igual a Django |
| cita: patch solo el inicio, en el pasado | `PATCH /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/` | igual a Django |
| cita: put con el fin antes | `PUT /api/v1/appointments/8473c095-6409-54cf-a976-d8505d656fee/` | igual a Django |
| cita: patch inexistente | `PATCH /api/v1/appointments/00000000-0000-4000-8000-000000000000/` | igual a Django |
| confirmar | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/confirm/` | igual a Django |
| confirmar inexistente | `POST /api/v1/appointments/00000000-0000-4000-8000-000000000000/confirm/` | igual a Django |
| reagendar con datos malos | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/reschedule/` | igual a Django |
| reagendar al revés | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/reschedule/` | igual a Django |
| reagendar encima de otra | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/reschedule/` | igual a Django |
| reagendar | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/reschedule/` | igual a Django |
| iniciar sin llegada | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/start/` | igual a Django |
| llegada | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/checkin/` | igual a Django |
| iniciar | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/start/` | igual a Django |
| iniciar otra vez | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/start/` | igual a Django |
| iniciar inexistente | `POST /api/v1/appointments/00000000-0000-4000-8000-000000000000/start/` | igual a Django |
| finalizar, auxiliar | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/checkout/` | igual a Django |
| finalizar, recepción | `POST /api/v1/appointments/fe6d5606-7bb8-5de6-b4fd-30fd9b945dcd/checkout/` | igual a Django |
| cancelar | `POST /api/v1/appointments/35fa5eb9-7647-580c-b5bd-7bbe514b6cce/cancel/` | igual a Django |
| baja, recepción | `DELETE /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
| ficha tras la baja | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/` | igual a Django |
