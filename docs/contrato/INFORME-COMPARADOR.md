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
| renovar | `POST /api/v1/auth/token/refresh/` | igual a Django |
| renovar otra vez con el mismo | `POST /api/v1/auth/token/refresh/` | igual a Django |
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
| alertas sin sesión | `GET /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/` | igual a Django |
| alertas, recepción | `GET /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/` | igual a Django |
| alertas de los antecedentes | `GET /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/` | igual a Django |
| alertas del último formulario 033 | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/alertas/` | igual a Django |
| alertas, solo negaciones | `GET /api/v1/patients/90ccff49-5d1a-5058-bc43-f5c67cde4a51/alertas/` | igual a Django |
| alertas sin antecedentes | `GET /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/alertas/` | igual a Django |
| alertas tras el patch de antecedentes | `GET /api/v1/patients/ad39aa87-911f-5a6a-985b-7b36250cb51f/alertas/` | igual a Django |
| alertas de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/alertas/` | igual a Django |
| alertas de un paciente de baja | `GET /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/alertas/` | igual a Django |
| alertas de un paciente que no existe | `GET /api/v1/patients/00000000-0000-4000-8000-000000000000/alertas/` | igual a Django |
| alertas con POST | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/` | igual a Django |
| revisar receta con choques | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| revisar receta del formulario 033 | `POST /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/alertas/revisar/` | igual a Django |
| revisar receta compatible | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| revisar, recepción | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| revisar sin texto | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| revisar texto en blanco | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| revisar texto que no es texto | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| revisar receta larga | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| revisar 5000 emojis | `POST /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| revisar sin texto y sin paciente | `POST /api/v1/patients/00000000-0000-4000-8000-000000000000/alertas/revisar/` | igual a Django |
| revisar, paciente de otra clínica | `POST /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/alertas/revisar/` | igual a Django |
| revisar con GET | `GET /api/v1/patients/f6ebcb43-a2a8-55ab-9a94-aa1ac29f9cfa/alertas/revisar/` | igual a Django |
| 033: lista | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/form033/` | igual a Django |
| 033: lista, recepción | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/form033/` | igual a Django |
| 033: lista sin sesión | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/form033/` | igual a Django |
| 033: lista de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/form033/` | igual a Django |
| 033: lista de un paciente que no existe | `GET /api/v1/patients/00000000-0000-4000-8000-000000000000/form033/` | igual a Django |
| 033: alta completa | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/form033/` | igual a Django |
| 033: alta con registro y firma | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/form033/` | igual a Django |
| 033: alta con todo mal | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/form033/` | igual a Django |
| 033: alta sin fecha | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/form033/` | igual a Django |
| 033: alta, recepción | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/form033/` | igual a Django |
| 033: alta con una lista | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/form033/` | igual a Django |
| 033: alta de un paciente de baja | `POST /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/form033/` | igual a Django |
| 033: lista tras las altas | `GET /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/form033/` | igual a Django |
| 033: ver | `GET /api/v1/form033/dd94da3e-1d9f-59be-ae1e-57194765a1cc/` | igual a Django |
| 033: ver sin constancia | `GET /api/v1/form033/80a490e0-f686-52ad-ac56-deba09b291ff/` | igual a Django |
| 033: ver, recepción | `GET /api/v1/form033/dd94da3e-1d9f-59be-ae1e-57194765a1cc/` | igual a Django |
| 033: ver de otra clínica | `GET /api/v1/form033/8aff5c9d-20a5-537a-851f-b26a59695280/` | igual a Django |
| 033: ver uno que no existe | `GET /api/v1/form033/00000000-0000-4000-8000-000000000000/` | igual a Django |
| 033: patch malo | `PATCH /api/v1/form033/dd94da3e-1d9f-59be-ae1e-57194765a1cc/` | igual a Django |
| 033: patch | `PATCH /api/v1/form033/dd94da3e-1d9f-59be-ae1e-57194765a1cc/` | igual a Django |
| 033: ver tras el patch | `GET /api/v1/form033/dd94da3e-1d9f-59be-ae1e-57194765a1cc/` | igual a Django |
| 033: put sin fecha | `PUT /api/v1/form033/46ca9603-dcf9-5208-a0d3-59391dce4f3d/` | igual a Django |
| 033: put | `PUT /api/v1/form033/46ca9603-dcf9-5208-a0d3-59391dce4f3d/` | igual a Django |
| 033: delete | `DELETE /api/v1/form033/46ca9603-dcf9-5208-a0d3-59391dce4f3d/` | igual a Django |
| diagnósticos | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/diagnoses/` | igual a Django |
| diagnósticos, página 2 | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/diagnoses/?page=2` | igual a Django |
| diagnósticos, página 3 | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/diagnoses/?page=3` | igual a Django |
| diagnósticos, recepción | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/diagnoses/` | igual a Django |
| diagnósticos de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/diagnoses/` | igual a Django |
| diagnóstico nuevo, doctora | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/diagnoses/` | igual a Django |
| diagnóstico nuevo, admin | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/diagnoses/` | igual a Django |
| diagnóstico con todo mal | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/diagnoses/` | igual a Django |
| diagnóstico vacío | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/diagnoses/` | igual a Django |
| diagnóstico con tipo nulo | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/diagnoses/` | igual a Django |
| diagnóstico de un paciente de baja | `POST /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/diagnoses/` | igual a Django |
| diagnóstico de un paciente que no existe | `POST /api/v1/patients/00000000-0000-4000-8000-000000000000/diagnoses/` | igual a Django |
| diagnóstico de otra clínica | `POST /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/diagnoses/` | igual a Django |
| diagnósticos tras las altas | `GET /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/diagnoses/` | igual a Django |
| cie10 sin q | `GET /api/v1/cie10/` | igual a Django |
| cie10 corta | `GET /api/v1/cie10/?q=k` | igual a Django |
| cie10 por código | `GET /api/v1/cie10/?q=+k0+` | igual a Django |
| cie10 por nombre | `GET /api/v1/cie10/?q=CARIES` | igual a Django |
| cie10 muchas | `GET /api/v1/cie10/?q=de` | igual a Django |
| cie10 con tilde | `GET /api/v1/cie10/?q=ER%C3%93SI` | igual a Django |
| cie10 comodines | `GET /api/v1/cie10/?q=%25%25` | igual a Django |
| cie10 guion bajo | `GET /api/v1/cie10/?q=K_` | igual a Django |
| cie10 repetida | `GET /api/v1/cie10/?q=xx&q=pulpa` | igual a Django |
| cie10 sin sesión | `GET /api/v1/cie10/?q=caries` | igual a Django |
| estados del odontograma | `GET /api/v1/odontogram-states/` | igual a Django |
| estados, recepción | `GET /api/v1/odontogram-states/` | igual a Django |
| estados sin sesión | `GET /api/v1/odontogram-states/` | igual a Django |
| historial de piezas | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/` | igual a Django |
| historial, página 2 | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/?page=2` | igual a Django |
| historial de una pieza | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/?tooth_fdi_code=16` | igual a Django |
| historial de una pieza con espacios | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/?tooth_fdi_code=+21+` | igual a Django |
| historial por superficie | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/?surface=occlusal` | igual a Django |
| historial con superficie mala | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/?surface=arriba` | igual a Django |
| historial con pieza y superficie | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/?surface=whole&tooth_fdi_code=48` | igual a Django |
| historial repetido | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/?tooth_fdi_code=11&tooth_fdi_code=26` | igual a Django |
| historial, recepción | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/tooth-records/` | igual a Django |
| historial de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/tooth-records/` | igual a Django |
| odontograma actual | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/odontogram/current/` | igual a Django |
| odontograma actual, recepción | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/odontogram/current/` | igual a Django |
| odontograma de un paciente de baja | `GET /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/odontogram/current/` | igual a Django |
| odontograma de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/odontogram/current/` | igual a Django |
| cpo-ceo | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/cpo-ceo/` | igual a Django |
| cpo-ceo sin registros | `GET /api/v1/patients/90ccff49-5d1a-5058-bc43-f5c67cde4a51/cpo-ceo/` | igual a Django |
| cpo-ceo de un paciente de baja | `GET /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/cpo-ceo/` | igual a Django |
| cpo-ceo sin sesión | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/cpo-ceo/` | igual a Django |
| registrar una superficie | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/tooth-records/` | igual a Django |
| registrar movilidad como el panel | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/tooth-records/` | igual a Django |
| registrar con un estado inactivo | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/tooth-records/` | igual a Django |
| registrar con todo mal | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/tooth-records/` | igual a Django |
| registrar vacío | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/tooth-records/` | igual a Django |
| registrar con estado numérico | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/tooth-records/` | igual a Django |
| registrar, recepción | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/tooth-records/` | igual a Django |
| registrar en un paciente de baja | `POST /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/tooth-records/` | igual a Django |
| registrar en un paciente que no existe | `POST /api/v1/patients/00000000-0000-4000-8000-000000000000/tooth-records/` | igual a Django |
| odontograma tras registrar | `GET /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/odontogram/current/` | igual a Django |
| cpo-ceo tras registrar | `GET /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/cpo-ceo/` | igual a Django |
| borrar un registro | `DELETE /api/v1/tooth-records/ddf86b06-4049-501f-99e0-c48b6a76f13f/` | igual a Django |
| borrar el mismo otra vez | `DELETE /api/v1/tooth-records/ddf86b06-4049-501f-99e0-c48b6a76f13f/` | igual a Django |
| borrar, recepción | `DELETE /api/v1/tooth-records/8a5fed60-6c1a-5d2c-8ad8-a5c30fe8f04e/` | igual a Django |
| borrar uno de otra clínica | `DELETE /api/v1/tooth-records/90ee5bac-4db3-55d8-89a0-7d02c21c3c68/` | igual a Django |
| ver un registro | `GET /api/v1/tooth-records/8a5fed60-6c1a-5d2c-8ad8-a5c30fe8f04e/` | igual a Django |
| odontograma tras borrar | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/odontogram/current/` | igual a Django |
| cpo-ceo tras borrar | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/cpo-ceo/` | igual a Django |
| evoluciones | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/evolutions/` | igual a Django |
| evoluciones, solo recetas | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/evolutions/?type=prescription` | igual a Django |
| evoluciones visibles | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/evolutions/?visible_to_patient=TRUE` | igual a Django |
| evoluciones no visibles | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/evolutions/?visible_to_patient=0` | igual a Django |
| evoluciones con visibilidad rara | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/evolutions/?visible_to_patient=si` | igual a Django |
| evoluciones con tipo malo | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/evolutions/?type=receta` | igual a Django |
| evoluciones, recepción | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/evolutions/` | igual a Django |
| evoluciones de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/evolutions/` | igual a Django |
| evolución nueva, doctora | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/evolutions/` | igual a Django |
| evolución nueva, admin | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/evolutions/` | igual a Django |
| evolución con todo mal | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/evolutions/` | igual a Django |
| evolución vacía | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/evolutions/` | igual a Django |
| evolución con cita inexistente | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/evolutions/` | igual a Django |
| evolución en un paciente de baja | `POST /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/evolutions/` | igual a Django |
| evolución, recepción | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/evolutions/` | igual a Django |
| evoluciones tras las altas | `GET /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/evolutions/` | igual a Django |
| ver una evolución | `GET /api/v1/evolutions/c59e9a25-fced-531c-aaab-4e7ce1805e37/` | igual a Django |
| ver una evolución sin doctor ni autor | `GET /api/v1/evolutions/e9627638-242b-5476-a356-eb72184748e6/` | igual a Django |
| ver una evolución de otra clínica | `GET /api/v1/evolutions/5d9394ce-52f9-57f7-a4a3-a0a6dad55456/` | igual a Django |
| patch de evolución malo | `PATCH /api/v1/evolutions/c59e9a25-fced-531c-aaab-4e7ce1805e37/` | igual a Django |
| patch de evolución | `PATCH /api/v1/evolutions/c59e9a25-fced-531c-aaab-4e7ce1805e37/` | igual a Django |
| put de evolución sin fecha | `PUT /api/v1/evolutions/eb8eb993-3b70-5226-bed6-d4bd6d2728ee/` | igual a Django |
| put de evolución | `PUT /api/v1/evolutions/eb8eb993-3b70-5226-bed6-d4bd6d2728ee/` | igual a Django |
| borrar una evolución | `DELETE /api/v1/evolutions/e9627638-242b-5476-a356-eb72184748e6/` | igual a Django |
| borrar la misma otra vez | `DELETE /api/v1/evolutions/e9627638-242b-5476-a356-eb72184748e6/` | igual a Django |
| borrar una evolución, recepción | `DELETE /api/v1/evolutions/eb8eb993-3b70-5226-bed6-d4bd6d2728ee/` | igual a Django |
| evoluciones tras editar y borrar | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/evolutions/` | igual a Django |
| tratamientos | `GET /api/v1/config/treatments/` | igual a Django |
| tratamientos activos | `GET /api/v1/config/treatments/?is_active=true` | igual a Django |
| tratamientos de una especialidad | `GET /api/v1/config/treatments/?specialty=07bf893d-7e20-56e3-b1e3-97308ad0b4d6` | igual a Django |
| tratamientos de una especialidad ajena | `GET /api/v1/config/treatments/?specialty=a02e0e01-008b-5ccb-be53-cabfd18c2d79` | igual a Django |
| tratamientos con especialidad inexistente | `GET /api/v1/config/treatments/?specialty=00000000-0000-4000-8000-000000000000` | igual a Django |
| tratamientos con especialidad mala | `GET /api/v1/config/treatments/?specialty=abc` | igual a Django |
| planes | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/treatment-plans/` | igual a Django |
| planes, recepción | `GET /api/v1/patients/df2a8872-1a27-5f9c-b59b-4e674c15f420/treatment-plans/` | igual a Django |
| planes de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/treatment-plans/` | igual a Django |
| plan nuevo | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/treatment-plans/` | igual a Django |
| plan nuevo de admin, activo | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/treatment-plans/` | igual a Django |
| plan con estado malo | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/treatment-plans/` | igual a Django |
| plan en un paciente de baja | `POST /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/treatment-plans/` | igual a Django |
| ver un plan | `GET /api/v1/treatment-plans/54498eb3-850a-5895-8ace-aa91eaefeabc/` | igual a Django |
| ver un plan de otra clínica | `GET /api/v1/treatment-plans/46d2e556-a547-5f73-af5b-979bfdc72a96/` | igual a Django |
| patch de plan | `PATCH /api/v1/treatment-plans/c1782109-70b1-5149-b940-046766785c93/` | igual a Django |
| put de plan | `PUT /api/v1/treatment-plans/c1782109-70b1-5149-b940-046766785c93/` | igual a Django |
| línea nueva sin precio | `POST /api/v1/treatment-plans/c1782109-70b1-5149-b940-046766785c93/items/` | igual a Django |
| línea nueva con precio y hecha | `POST /api/v1/treatment-plans/c1782109-70b1-5149-b940-046766785c93/items/` | igual a Django |
| línea nueva con tratamiento inactivo | `POST /api/v1/treatment-plans/c1782109-70b1-5149-b940-046766785c93/items/` | igual a Django |
| línea con todo mal | `POST /api/v1/treatment-plans/c1782109-70b1-5149-b940-046766785c93/items/` | igual a Django |
| línea en un plan que no existe | `POST /api/v1/treatment-plans/00000000-0000-4000-8000-000000000000/items/` | igual a Django |
| línea con datos malos en un plan que no existe | `POST /api/v1/treatment-plans/00000000-0000-4000-8000-000000000000/items/` | igual a Django |
| plan tras las líneas | `GET /api/v1/treatment-plans/c1782109-70b1-5149-b940-046766785c93/` | igual a Django |
| línea a realizada (descuenta inventario) | `PATCH /api/v1/treatment-plan-items/88df10fd-7580-5e56-9009-5ef28aad8448/` | igual a Django |
| precio de una línea realizada | `PATCH /api/v1/treatment-plan-items/88df10fd-7580-5e56-9009-5ef28aad8448/` | igual a Django |
| línea en progreso | `PATCH /api/v1/treatment-plan-items/51aac537-f87e-5fed-a5c6-e8c521c2f902/` | igual a Django |
| put de línea sin tratamiento | `PUT /api/v1/treatment-plan-items/51aac537-f87e-5fed-a5c6-e8c521c2f902/` | igual a Django |
| línea de otra clínica | `PATCH /api/v1/treatment-plan-items/e1a5a3eb-f6cc-5549-a202-6999c22bef2a/` | igual a Django |
| línea, recepción | `PATCH /api/v1/treatment-plan-items/4317e2a7-7343-5f60-b104-81c688f5cdd4/` | igual a Django |
| plan con su avance | `GET /api/v1/treatment-plans/54498eb3-850a-5895-8ace-aa91eaefeabc/` | igual a Django |
| ver una línea | `GET /api/v1/treatment-plan-items/4317e2a7-7343-5f60-b104-81c688f5cdd4/` | igual a Django |
| plantillas | `GET /api/v1/clinical/plan-templates/` | igual a Django |
| plantillas sin sesión | `GET /api/v1/clinical/plan-templates/` | igual a Django |
| plantilla nueva con líneas | `POST /api/v1/clinical/plan-templates/` | igual a Django |
| plantilla nueva sin líneas | `POST /api/v1/clinical/plan-templates/` | igual a Django |
| plantilla sin nombre | `POST /api/v1/clinical/plan-templates/` | igual a Django |
| plantilla, auxiliar | `POST /api/v1/clinical/plan-templates/` | igual a Django |
| plantillas tras las altas | `GET /api/v1/clinical/plan-templates/` | igual a Django |
| línea de plantilla | `POST /api/v1/clinical/plan-templates/5bd6ac98-53d6-50ac-b48c-b4afa69aa7b3/items/` | igual a Django |
| línea de plantilla con orden | `POST /api/v1/clinical/plan-templates/5bd6ac98-53d6-50ac-b48c-b4afa69aa7b3/items/` | igual a Django |
| línea de plantilla con orden cero | `POST /api/v1/clinical/plan-templates/5bd6ac98-53d6-50ac-b48c-b4afa69aa7b3/items/` | igual a Django |
| línea de plantilla sin tratamiento | `POST /api/v1/clinical/plan-templates/5bd6ac98-53d6-50ac-b48c-b4afa69aa7b3/items/` | igual a Django |
| línea de plantilla con tratamiento ajeno | `POST /api/v1/clinical/plan-templates/5bd6ac98-53d6-50ac-b48c-b4afa69aa7b3/items/` | igual a Django |
| línea en plantilla ajena | `POST /api/v1/clinical/plan-templates/9742dff4-734e-51b1-b152-0330072e088e/items/` | igual a Django |
| línea de plantilla, auxiliar | `POST /api/v1/clinical/plan-templates/5bd6ac98-53d6-50ac-b48c-b4afa69aa7b3/items/` | igual a Django |
| borrar línea de plantilla | `DELETE /api/v1/clinical/plan-template-items/9703814f-62de-5e14-ae12-9724d1e797b8/` | igual a Django |
| borrar la misma otra vez | `DELETE /api/v1/clinical/plan-template-items/9703814f-62de-5e14-ae12-9724d1e797b8/` | igual a Django |
| borrar línea de plantilla ajena | `DELETE /api/v1/clinical/plan-template-items/cac131cf-841e-5a56-8360-6d23e91cfc1f/` | igual a Django |
| aplicar a paciente con convenio | `POST /api/v1/patients/e7dfdf4c-2811-5220-a173-a52a739f812d/apply-plan-template/` | igual a Django |
| aplicar a paciente particular | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/apply-plan-template/` | igual a Django |
| aplicar plantilla inactiva | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/apply-plan-template/` | igual a Django |
| aplicar plantilla que no existe | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/apply-plan-template/` | igual a Django |
| aplicar sin plantilla | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/apply-plan-template/` | igual a Django |
| aplicar plantilla numérica | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/apply-plan-template/` | igual a Django |
| aplicar plantilla ajena | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/apply-plan-template/` | igual a Django |
| aplicar a paciente de baja | `POST /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/apply-plan-template/` | igual a Django |
| aplicar, recepción | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/apply-plan-template/` | igual a Django |
| planes tras aplicar | `GET /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/treatment-plans/` | igual a Django |
| presupuesto desde el plan | `POST /api/v1/treatment-plans/54498eb3-850a-5895-8ace-aa91eaefeabc/generate-budget/` | igual a Django |
| presupuesto con convenio y precios a mano | `POST /api/v1/treatment-plans/d8ebfd41-99da-56c9-b1ae-e198446d681a/generate-budget/` | igual a Django |
| presupuesto de un plan vacío | `POST /api/v1/treatment-plans/c49e336c-c0e5-5820-b023-145eff2f3dfb/generate-budget/` | igual a Django |
| presupuesto de un plan ajeno | `POST /api/v1/treatment-plans/46d2e556-a547-5f73-af5b-979bfdc72a96/generate-budget/` | igual a Django |
| presupuesto, auxiliar | `POST /api/v1/treatment-plans/54498eb3-850a-5895-8ace-aa91eaefeabc/generate-budget/` | igual a Django |
| presupuesto con GET | `GET /api/v1/treatment-plans/54498eb3-850a-5895-8ace-aa91eaefeabc/generate-budget/` | igual a Django |
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
| presupuestos del paciente | `GET /api/v1/patients/04efc017-d7d3-546e-9b43-46b5a47e9002/budgets/` | igual a Django |
| presupuestos, auxiliar | `GET /api/v1/patients/04efc017-d7d3-546e-9b43-46b5a47e9002/budgets/` | igual a Django |
| presupuestos de otra clínica | `GET /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/budgets/` | igual a Django |
| presupuesto nuevo como el panel | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/budgets/` | igual a Django |
| presupuesto nuevo con otro paciente en el cuerpo | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/budgets/` | igual a Django |
| presupuesto sin paciente | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/budgets/` | igual a Django |
| presupuesto con paciente ajeno | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/budgets/` | igual a Django |
| presupuesto con paciente inexistente | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/budgets/` | igual a Django |
| presupuesto de un paciente de baja | `POST /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/budgets/` | igual a Django |
| presupuesto, auxiliar | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/budgets/` | igual a Django |
| ver presupuesto | `GET /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/` | igual a Django |
| ver presupuesto, doctora sin la función | `GET /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/` | igual a Django |
| ver presupuesto ajeno | `GET /api/v1/budgets/dd678dd2-d565-50a0-91ea-d6618b72cf29/` | igual a Django |
| patch de presupuesto | `PATCH /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/` | igual a Django |
| put de presupuesto sin paciente | `PUT /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/` | igual a Django |
| línea de presupuesto como el panel | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea de presupuesto con cantidad | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea de presupuesto con todo mal | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea de presupuesto sin precio | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea en presupuesto ajeno | `POST /api/v1/budgets/dd678dd2-d565-50a0-91ea-d6618b72cf29/items/` | igual a Django |
| presupuesto tras las líneas | `GET /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/` | igual a Django |
| plan de un presupuesto sin aprobar | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/generate-payment-plan/` | igual a Django |
| aprobar presupuesto | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/approve/` | igual a Django |
| aprobar un rechazado | `POST /api/v1/budgets/3a315ead-9b9a-5b84-a2b2-db888512420d/approve/` | igual a Django |
| aprobar ajeno | `POST /api/v1/budgets/dd678dd2-d565-50a0-91ea-d6618b72cf29/approve/` | igual a Django |
| aprobar, doctora | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/approve/` | igual a Django |
| plan con datos malos | `POST /api/v1/budgets/6c51e480-c019-51bd-9f65-d8f57aa71c68/generate-payment-plan/` | igual a Django |
| plan sin datos | `POST /api/v1/budgets/6c51e480-c019-51bd-9f65-d8f57aa71c68/generate-payment-plan/` | igual a Django |
| plan de 3 cuotas desde un 31 | `POST /api/v1/budgets/6c51e480-c019-51bd-9f65-d8f57aa71c68/generate-payment-plan/` | igual a Django |
| plan otra vez | `POST /api/v1/budgets/6c51e480-c019-51bd-9f65-d8f57aa71c68/generate-payment-plan/` | igual a Django |
| plan de total cero | `POST /api/v1/budgets/d5ae5000-5476-5981-824e-2273ebe07e88/generate-payment-plan/` | igual a Django |
| plan del presupuesto aprobado en el panel | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/generate-payment-plan/` | igual a Django |
| presupuesto con su plan | `GET /api/v1/budgets/6c51e480-c019-51bd-9f65-d8f57aa71c68/` | igual a Django |
| cuotas de un plan | `GET /api/v1/payment-plans/8f75e6fe-9903-577a-b2a2-267890d61c2f/installments/` | igual a Django |
| cuotas de un plan, auxiliar | `GET /api/v1/payment-plans/8f75e6fe-9903-577a-b2a2-267890d61c2f/installments/` | igual a Django |
| cuotas de un plan ajeno | `GET /api/v1/payment-plans/0bf53add-40e8-5a33-8324-42fd4ee7bad0/installments/` | igual a Django |
| pagar parte de una cuota | `POST /api/v1/installments/ff59ba29-e1f6-5420-8747-1eba430b217f/pay/` | igual a Django |
| pagar el resto de la cuota | `POST /api/v1/installments/ff59ba29-e1f6-5420-8747-1eba430b217f/pay/` | igual a Django |
| pagar de más una cuota pagada | `POST /api/v1/installments/fb16ca87-e75d-5b1a-8812-fb2df7135da9/pay/` | igual a Django |
| pagar con todo mal | `POST /api/v1/installments/a368806d-34f2-5dd3-86fa-01329071ef27/pay/` | igual a Django |
| pagar con profesional de otra clínica | `POST /api/v1/installments/a368806d-34f2-5dd3-86fa-01329071ef27/pay/` | igual a Django |
| pagar con profesional recepción | `POST /api/v1/installments/a368806d-34f2-5dd3-86fa-01329071ef27/pay/` | igual a Django |
| pagar sin datos | `POST /api/v1/installments/a368806d-34f2-5dd3-86fa-01329071ef27/pay/` | igual a Django |
| pagar cuota ajena | `POST /api/v1/installments/4f8608e4-aff3-524a-84e4-e2f6fe87ca0e/pay/` | igual a Django |
| pagar, doctora | `POST /api/v1/installments/a368806d-34f2-5dd3-86fa-01329071ef27/pay/` | igual a Django |
| cuotas tras pagar | `GET /api/v1/payment-plans/8f75e6fe-9903-577a-b2a2-267890d61c2f/installments/` | igual a Django |
| pagos del paciente | `GET /api/v1/patients/e94cdd80-71c7-5e48-ba65-27d342d0cf1e/payments/` | igual a Django |
| pagos, doctora | `GET /api/v1/patients/e94cdd80-71c7-5e48-ba65-27d342d0cf1e/payments/` | igual a Django |
| pago directo | `POST /api/v1/patients/98bda393-fdd2-510c-b7fc-5934d5e02762/payments/` | igual a Django |
| pago directo a una cuota | `POST /api/v1/patients/98bda393-fdd2-510c-b7fc-5934d5e02762/payments/` | igual a Django |
| pago directo con todo mal | `POST /api/v1/patients/98bda393-fdd2-510c-b7fc-5934d5e02762/payments/` | igual a Django |
| pago directo vacío | `POST /api/v1/patients/98bda393-fdd2-510c-b7fc-5934d5e02762/payments/` | igual a Django |
| pago directo a un paciente de baja | `POST /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/payments/` | igual a Django |
| pago directo a un paciente ajeno | `POST /api/v1/patients/92f3248c-fcd2-5d23-991f-58410f9ce47c/payments/` | igual a Django |
| pagos tras los cobros | `GET /api/v1/patients/98bda393-fdd2-510c-b7fc-5934d5e02762/payments/` | igual a Django |
| estado de cuenta | `GET /api/v1/patients/e94cdd80-71c7-5e48-ba65-27d342d0cf1e/account-statement/` | igual a Django |
| estado de cuenta con saldo a favor | `GET /api/v1/patients/98bda393-fdd2-510c-b7fc-5934d5e02762/account-statement/` | igual a Django |
| estado de cuenta sin cuotas | `GET /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/account-statement/` | igual a Django |
| estado de cuenta, auxiliar | `GET /api/v1/patients/e94cdd80-71c7-5e48-ba65-27d342d0cf1e/account-statement/` | igual a Django |
| estado de cuenta de un paciente de baja | `GET /api/v1/patients/f392b107-da73-5a13-948a-727cbcaa6cb2/account-statement/` | igual a Django |
| línea con subtotal -0.00 | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea con precio -0 | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea con tratamiento entre llaves y sin guiones | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea con tratamiento urn:uuid | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea con tratamiento con 0x y guiones raros | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea con tratamiento vacío | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea con tratamiento numérico | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea con tratamiento negativo | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| línea con tratamiento decimal | `POST /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/items/` | igual a Django |
| presupuesto con las líneas raras | `GET /api/v1/budgets/a786946a-2cac-585c-bf75-0ab15f946cf9/` | igual a Django |
| pago directo con cuota vacía | `POST /api/v1/patients/3d639a68-903b-5007-a4bd-f63cb20310dd/payments/` | igual a Django |
| finanzas: resumen del mes | `GET /api/v1/finanzas/resumen/` | igual a Django |
| finanzas: resumen del mes anterior | `GET /api/v1/finanzas/resumen/?mes={mes-1}` | igual a Django |
| finanzas: resumen con el mes «2026-13» | `GET /api/v1/finanzas/resumen/?mes=2026-13` | igual a Django |
| finanzas: resumen con el mes «abc» | `GET /api/v1/finanzas/resumen/?mes=abc` | igual a Django |
| finanzas: resumen con el mes «2026» | `GET /api/v1/finanzas/resumen/?mes=2026` | igual a Django |
| finanzas: resumen con el mes «2026-1-5» | `GET /api/v1/finanzas/resumen/?mes=2026-1-5` | igual a Django |
| finanzas: resumen con el mes « 2026-10» | `GET /api/v1/finanzas/resumen/?mes= 2026-10` | igual a Django |
| finanzas: resumen sin la función | `GET /api/v1/finanzas/resumen/` | igual a Django |
| finanzas: resumen de recepción | `GET /api/v1/finanzas/resumen/` | igual a Django |
| finanzas: configuración | `GET /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: categorías | `GET /api/v1/finanzas/categorias/` | igual a Django |
| finanzas: egresos | `GET /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egresos del mes | `GET /api/v1/finanzas/egresos/?mes={mes}` | igual a Django |
| finanzas: egresos del mes con los anulados | `GET /api/v1/finanzas/egresos/?mes={mes}&anulados=1` | igual a Django |
| finanzas: cuentas por pagar (de cualquier mes) | `GET /api/v1/finanzas/egresos/?estado=por_pagar&mes={mes-1}` | igual a Django |
| finanzas: egresos de una categoría | `GET /api/v1/finanzas/egresos/?categoria={guardado:cat-arriendo}&mes=` | igual a Django |
| finanzas: un egreso | `GET /api/v1/finanzas/egresos/e0045330-1667-513a-92ff-2f14f295801c/` | igual a Django |
| finanzas: un egreso que no existe | `GET /api/v1/finanzas/egresos/00000000-0000-4000-8000-000000000000/` | igual a Django |
| finanzas: un egreso de otra clínica | `GET /api/v1/finanzas/egresos/1ce936a7-c589-535c-975a-1c889ad480a9/` | igual a Django |
| finanzas: comprobante de un egreso sin archivo | `GET /api/v1/finanzas/egresos/5f9c243b-d6f9-546c-a9c7-d53d60baa96f/comprobante/` | igual a Django |
| finanzas: caja de hoy | `GET /api/v1/finanzas/caja/` | igual a Django |
| finanzas: caja de ayer | `GET /api/v1/finanzas/caja/?fecha={hoy-1}` | igual a Django |
| finanzas: caja con fecha mala | `GET /api/v1/finanzas/caja/?fecha=ayer` | igual a Django |
| finanzas: caja con fecha compacta | `GET /api/v1/finanzas/caja/?fecha=20261020` | igual a Django |
| finanzas: equipo | `GET /api/v1/finanzas/equipo/` | igual a Django |
| finanzas: equipo, sin la función | `GET /api/v1/finanzas/equipo/` | igual a Django |
| finanzas: liquidaciones | `GET /api/v1/finanzas/liquidaciones/` | igual a Django |
| finanzas: liquidaciones de la doctora | `GET /api/v1/finanzas/liquidaciones/?profesional=4584919b-4cbf-5952-8857-0fd8c0b1d818` | igual a Django |
| finanzas: liquidación previa del mes | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=4584919b-4cbf-5952-8857-0fd8c0b1d818&desde={primero}&hasta={hoy}` | igual a Django |
| finanzas: liquidación previa con un anticipo | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=4584919b-4cbf-5952-8857-0fd8c0b1d818&desde={primero}&hasta={hoy}&ajustes=-10.555` | igual a Django |
| finanzas: liquidación previa con un bono | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=4584919b-4cbf-5952-8857-0fd8c0b1d818&desde={primero}&hasta={hoy}&ajustes=25` | igual a Django |
| finanzas: liquidación previa de un fijo | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=92f26c47-86f7-5ae7-b7a9-eaf818139685&desde={primero}&hasta={hoy}` | igual a Django |
| finanzas: liquidación previa desde antes de la primera | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=4584919b-4cbf-5952-8857-0fd8c0b1d818&desde=2026-01-01&hasta={hoy}` | igual a Django |
| finanzas: liquidación previa sin período | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=4584919b-4cbf-5952-8857-0fd8c0b1d818&desde={primero}` | igual a Django |
| finanzas: liquidación previa al revés | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=4584919b-4cbf-5952-8857-0fd8c0b1d818&desde={hoy}&hasta={primero}` | igual a Django |
| finanzas: liquidación previa con ajuste raro | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=4584919b-4cbf-5952-8857-0fd8c0b1d818&desde={primero}&hasta={hoy}&ajustes=diez` | igual a Django |
| finanzas: liquidación previa de alguien que no es un UUID | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=xyz` | igual a Django |
| finanzas: liquidación previa de alguien que no está | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=00000000-0000-4000-8000-000000000000` | igual a Django |
| finanzas: liquidación previa sin profesional | `GET /api/v1/finanzas/liquidaciones/previa/` | igual a Django |
| finanzas: liquidación previa de otra clínica | `GET /api/v1/finanzas/liquidaciones/previa/?profesional=f9c446be-930a-516d-974f-05bfce1c3fb5&desde={primero}&hasta={hoy}` | igual a Django |
| finanzas: roles | `GET /api/v1/finanzas/roles/` | igual a Django |
| finanzas: roles del mes anterior | `GET /api/v1/finanzas/roles/?mes={mes-1}` | igual a Django |
| finanzas: rol previo con horas | `GET /api/v1/finanzas/roles/previa/?empleado=22f0eb4e-baf2-588f-86f9-ba92a5e38f6a&mes={mes}&horas_suplementarias=10&horas_extraordinarias=4.5&otros_ingresos=25&anticipos=10&prestamos_iess=15.20&otros_descuentos=0.004` | igual a Django |
| finanzas: rol previo de 20 días, sin fondos | `GET /api/v1/finanzas/roles/previa/?empleado=c65e9374-efaf-505c-a880-c689d7378e84&mes={mes}&dias_trabajados=20.9` | igual a Django |
| finanzas: rol previo de un honorario | `GET /api/v1/finanzas/roles/previa/?empleado=4584919b-4cbf-5952-8857-0fd8c0b1d818` | igual a Django |
| finanzas: rol previo con 31 días | `GET /api/v1/finanzas/roles/previa/?empleado=22f0eb4e-baf2-588f-86f9-ba92a5e38f6a&dias_trabajados=31` | igual a Django |
| finanzas: rol previo con días negativos | `GET /api/v1/finanzas/roles/previa/?empleado=22f0eb4e-baf2-588f-86f9-ba92a5e38f6a&dias_trabajados=-1` | igual a Django |
| finanzas: rol previo con demasiadas horas | `GET /api/v1/finanzas/roles/previa/?empleado=22f0eb4e-baf2-588f-86f9-ba92a5e38f6a&horas_suplementarias=150&horas_extraordinarias=60` | igual a Django |
| finanzas: rol previo con mes malo | `GET /api/v1/finanzas/roles/previa/?empleado=22f0eb4e-baf2-588f-86f9-ba92a5e38f6a&mes=2026-13` | igual a Django |
| finanzas: rol previo con valor raro | `GET /api/v1/finanzas/roles/previa/?empleado=22f0eb4e-baf2-588f-86f9-ba92a5e38f6a&anticipos=mucho` | igual a Django |
| finanzas: rol previo sin empleado | `GET /api/v1/finanzas/roles/previa/` | igual a Django |
| finanzas: obligaciones del mes anterior | `GET /api/v1/finanzas/obligaciones/?mes={mes-1}` | igual a Django |
| finanzas: obligaciones del mes | `GET /api/v1/finanzas/obligaciones/` | igual a Django |
| finanzas: obligaciones con mes malo | `GET /api/v1/finanzas/obligaciones/?mes=x` | igual a Django |
| finanzas: décimo tercero | `GET /api/v1/finanzas/decimos/?tipo=tercero&anio={anio}` | igual a Django |
| finanzas: décimo cuarto, sin año | `GET /api/v1/finanzas/decimos/?tipo=cuarto` | igual a Django |
| finanzas: décimos con «tipo=quinto» | `GET /api/v1/finanzas/decimos/?tipo=quinto` | igual a Django |
| finanzas: décimos con «tipo=tercero&anio=1999» | `GET /api/v1/finanzas/decimos/?tipo=tercero&anio=1999` | igual a Django |
| finanzas: décimos con «tipo=tercero&anio=abc» | `GET /api/v1/finanzas/decimos/?tipo=tercero&anio=abc` | igual a Django |
| finanzas: décimos con «tipo=tercero&anio=2026.0» | `GET /api/v1/finanzas/decimos/?tipo=tercero&anio=2026.0` | igual a Django |
| finanzas: décimos con «» | `GET /api/v1/finanzas/decimos/?` | igual a Django |
| finanzas: cobros del mes | `GET /api/v1/finanzas/cobros/?mes={mes}` | igual a Django |
| finanzas: cobros del mes anterior | `GET /api/v1/finanzas/cobros/?mes={mes-1}` | igual a Django |
| finanzas: Excel del mes anterior | `GET /api/v1/finanzas/exportar/?mes={mes-1}` | igual a Django |
| finanzas: Excel con mes malo | `GET /api/v1/finanzas/exportar/?mes=octubre` | igual a Django |
| finanzas: PDF de la liquidación | `GET /api/v1/finanzas/liquidaciones/1a65f65b-7543-5632-a0ca-3503ed469297/pdf/` | igual a Django |
| finanzas: PDF de una liquidación que no existe | `GET /api/v1/finanzas/liquidaciones/00000000-0000-4000-8000-000000000000/pdf/` | igual a Django |
| finanzas: PDF del rol | `GET /api/v1/finanzas/roles/7b501ac9-5944-5e99-b1d5-6ac0b9db0718/pdf/` | igual a Django |
| finanzas: comprobante sin la función de cobros | `GET /api/v1/finanzas/cobros/c5124660-65fc-5a1d-a3b8-4a1d50eaea88/comprobante/` | igual a Django |
| finanzas: cambiar la configuración | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: configuración con porcentaje de más | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: configuración con recargo de más | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: configuración con salario cero | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: configuración con región rara | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: configuración con comprobante negativo | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: configuración con salario enorme | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: configuración con decimales de más | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: configuración con una lista | `PATCH /api/v1/finanzas/configuracion/` | igual a Django |
| finanzas: categoría nueva | `POST /api/v1/finanzas/categorias/` | igual a Django |
| finanzas: categoría sin nombre | `POST /api/v1/finanzas/categorias/` | igual a Django |
| finanzas: categoría con nombre largo | `POST /api/v1/finanzas/categorias/` | igual a Django |
| finanzas: desactivar la categoría | `PATCH /api/v1/finanzas/categorias/{guardado:cat-nueva}/` | igual a Django |
| finanzas: renombrar la categoría | `PUT /api/v1/finanzas/categorias/{guardado:cat-nueva}/` | igual a Django |
| finanzas: reemplazar la categoría sin nombre | `PUT /api/v1/finanzas/categorias/{guardado:cat-nueva}/` | igual a Django |
| finanzas: categoría que no existe | `PATCH /api/v1/finanzas/categorias/00000000-0000-4000-8000-000000000000/` | igual a Django |
| finanzas: categorías después | `GET /api/v1/finanzas/categorias/` | igual a Django |
| finanzas: egreso nuevo | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: cuenta por pagar sin vencimiento | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con vacío | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con monto cero | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con categoría de otra clínica | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con categoría que no existe | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con profesional de otra clínica | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con profesional que no existe | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con RUC corto | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con RUC con letras | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con forma rara | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con un archivo en JSON | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso con anticipo de un rol pagado | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso desde el formulario, con su factura | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: la factura subida | `GET /api/v1/finanzas/egresos/{guardado:egreso-factura}/comprobante/` | igual a Django |
| finanzas: egreso desde el formulario, por pagar sin vence | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso desde el formulario sin casilla | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: factura con otra extensión | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: factura vacía | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: formulario con errores | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: egreso en texto plano | `POST /api/v1/finanzas/egresos/` | igual a Django |
| finanzas: corregir el concepto | `PATCH /api/v1/finanzas/egresos/{guardado:egreso-nuevo}/` | igual a Django |
| finanzas: corregir desde el formulario | `PATCH /api/v1/finanzas/egresos/{guardado:egreso-factura}/` | igual a Django |
| finanzas: la factura sigue | `GET /api/v1/finanzas/egresos/{guardado:egreso-factura}/comprobante/` | igual a Django |
| finanzas: reemplazar sin campos | `PUT /api/v1/finanzas/egresos/{guardado:egreso-nuevo}/` | igual a Django |
| finanzas: corregir un anulado | `PATCH /api/v1/finanzas/egresos/75419b4d-d177-5f91-b84b-ad7255957a3d/` | igual a Django |
| finanzas: corregir un anulado con un monto malo | `PATCH /api/v1/finanzas/egresos/75419b4d-d177-5f91-b84b-ad7255957a3d/` | igual a Django |
| finanzas: corregir el pago de una liquidación | `PATCH /api/v1/finanzas/egresos/f2c73ae6-89e3-58be-b238-65ca07fec3e0/` | igual a Django |
| finanzas: corregir una compra de inventario | `PATCH /api/v1/finanzas/egresos/f4e6948e-ec09-5261-b3fb-a4e412bd6461/` | igual a Django |
| finanzas: pagar una cuenta | `POST /api/v1/finanzas/egresos/0fe27653-c618-5cb5-ad7d-eef0c7f7045b/pagar/` | igual a Django |
| finanzas: pagarla otra vez | `POST /api/v1/finanzas/egresos/0fe27653-c618-5cb5-ad7d-eef0c7f7045b/pagar/` | igual a Django |
| finanzas: pagar un anulado | `POST /api/v1/finanzas/egresos/75419b4d-d177-5f91-b84b-ad7255957a3d/pagar/` | igual a Django |
| finanzas: pagar con una forma rara | `POST /api/v1/finanzas/egresos/51d4de89-776f-5437-a77a-dd7b3b42c888/pagar/` | igual a Django |
| finanzas: pagar con su forma y fecha | `POST /api/v1/finanzas/egresos/51d4de89-776f-5437-a77a-dd7b3b42c888/pagar/` | igual a Django |
| finanzas: anular sin motivo | `POST /api/v1/finanzas/egresos/5f9c243b-d6f9-546c-a9c7-d53d60baa96f/anular/` | igual a Django |
| finanzas: anular con motivo en blanco | `POST /api/v1/finanzas/egresos/5f9c243b-d6f9-546c-a9c7-d53d60baa96f/anular/` | igual a Django |
| finanzas: anular | `POST /api/v1/finanzas/egresos/5f9c243b-d6f9-546c-a9c7-d53d60baa96f/anular/` | igual a Django |
| finanzas: anular el pago de un rol | `POST /api/v1/finanzas/egresos/b479edd6-e749-5900-9359-01a5f8ca381c/anular/` | igual a Django |
| finanzas: anular uno que no existe | `POST /api/v1/finanzas/egresos/00000000-0000-4000-8000-000000000000/anular/` | igual a Django |
| finanzas: cerrar la caja | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: cerrar la caja con retirar de más | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: cerrar la caja con contado negativo | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: cerrar la caja con un día que no llega | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: cerrar la caja con contado raro | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: cerrar la caja con sin contado | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: cerrar la caja con fecha mala | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: corregir el cierre | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: cerrar un día pasado | `POST /api/v1/finanzas/caja/` | igual a Django |
| finanzas: caja después de cerrar | `GET /api/v1/finanzas/caja/` | igual a Django |
| finanzas: perfil con cédula corta | `PATCH /api/v1/finanzas/equipo/c65e9374-efaf-505c-a880-c689d7378e84/` | igual a Django |
| finanzas: perfil con porcentaje de más | `PATCH /api/v1/finanzas/equipo/c65e9374-efaf-505c-a880-c689d7378e84/` | igual a Django |
| finanzas: perfil con modalidad rara | `PATCH /api/v1/finanzas/equipo/c65e9374-efaf-505c-a880-c689d7378e84/` | igual a Django |
| finanzas: perfil con fecha mala | `PATCH /api/v1/finanzas/equipo/c65e9374-efaf-505c-a880-c689d7378e84/` | igual a Django |
| finanzas: cambiar un perfil | `PATCH /api/v1/finanzas/equipo/c65e9374-efaf-505c-a880-c689d7378e84/` | igual a Django |
| finanzas: perfil nuevo | `PATCH /api/v1/finanzas/equipo/a35f7a96-7c8b-4954-8f61-0596bd5118ad/` | igual a Django |
| finanzas: perfil de otra clínica | `PATCH /api/v1/finanzas/equipo/f9c446be-930a-516d-974f-05bfce1c3fb5/` | igual a Django |
| finanzas: equipo después | `GET /api/v1/finanzas/equipo/` | igual a Django |
| finanzas: liquidar a la doctora | `POST /api/v1/finanzas/liquidaciones/` | igual a Django |
| finanzas: liquidarla otra vez | `POST /api/v1/finanzas/liquidaciones/` | igual a Django |
| finanzas: liquidar un fijo | `POST /api/v1/finanzas/liquidaciones/` | igual a Django |
| finanzas: liquidar al revés | `POST /api/v1/finanzas/liquidaciones/` | igual a Django |
| finanzas: liquidar a alguien con un número | `POST /api/v1/finanzas/liquidaciones/` | igual a Django |
| finanzas: corregir un gasto ya descontado | `PATCH /api/v1/finanzas/egresos/08002062-a33d-512b-8536-1f8ba3ac411b/` | igual a Django |
| finanzas: anular un gasto ya descontado | `POST /api/v1/finanzas/egresos/08002062-a33d-512b-8536-1f8ba3ac411b/anular/` | igual a Django |
| finanzas: reasignar un cobro ya liquidado | `PATCH /api/v1/finanzas/cobros/1880f81b-f035-51ea-bce7-e4319f30d164/` | igual a Django |
| finanzas: reasignar un cobro libre | `PATCH /api/v1/finanzas/cobros/91038bd6-fb7d-53ce-b390-a28bf58dbec0/` | igual a Django |
| finanzas: quitarle el profesional | `PATCH /api/v1/finanzas/cobros/91038bd6-fb7d-53ce-b390-a28bf58dbec0/` | igual a Django |
| finanzas: reasignar a alguien raro | `PATCH /api/v1/finanzas/cobros/91038bd6-fb7d-53ce-b390-a28bf58dbec0/` | igual a Django |
| finanzas: reasignar a otra clínica | `PATCH /api/v1/finanzas/cobros/91038bd6-fb7d-53ce-b390-a28bf58dbec0/` | igual a Django |
| finanzas: reasignar un cobro que no existe | `PATCH /api/v1/finanzas/cobros/00000000-0000-4000-8000-000000000000/` | igual a Django |
| finanzas: pagar la liquidación | `POST /api/v1/finanzas/liquidaciones/{guardado:liq-doctora}/pagar/` | igual a Django |
| finanzas: pagarla otra vez | `POST /api/v1/finanzas/liquidaciones/{guardado:liq-doctora}/pagar/` | igual a Django |
| finanzas: pagar con forma rara | `POST /api/v1/finanzas/liquidaciones/{guardado:liq-fijo}/pagar/` | igual a Django |
| finanzas: anular el fijo | `POST /api/v1/finanzas/liquidaciones/{guardado:liq-fijo}/anular/` | igual a Django |
| finanzas: anularlo otra vez | `POST /api/v1/finanzas/liquidaciones/{guardado:liq-fijo}/anular/` | igual a Django |
| finanzas: PDF de la liquidación del mes | `GET /api/v1/finanzas/liquidaciones/{guardado:liq-doctora}/pdf/` | igual a Django |
| finanzas: anular la del mes pasado | `POST /api/v1/finanzas/liquidaciones/1a65f65b-7543-5632-a0ca-3503ed469297/anular/` | igual a Django |
| finanzas: liquidaciones después | `GET /api/v1/finanzas/liquidaciones/` | igual a Django |
| finanzas: rol de recepción | `POST /api/v1/finanzas/roles/` | igual a Django |
| finanzas: rehacer el rol | `POST /api/v1/finanzas/roles/` | igual a Django |
| finanzas: rehacer un rol pagado | `POST /api/v1/finanzas/roles/` | igual a Django |
| finanzas: rol con mes raro | `POST /api/v1/finanzas/roles/` | igual a Django |
| finanzas: generar los roles | `POST /api/v1/finanzas/roles/generar/` | igual a Django |
| finanzas: generarlos otra vez | `POST /api/v1/finanzas/roles/generar/` | igual a Django |
| finanzas: generar con mes malo | `POST /api/v1/finanzas/roles/generar/` | igual a Django |
| finanzas: roles del mes | `GET /api/v1/finanzas/roles/?mes={mes}` | igual a Django |
| finanzas: pagar el rol | `POST /api/v1/finanzas/roles/{guardado:rol-recepcion}/pagar/` | igual a Django |
| finanzas: pagarlo otra vez | `POST /api/v1/finanzas/roles/{guardado:rol-recepcion}/pagar/` | igual a Django |
| finanzas: pagar un rol con forma rara | `POST /api/v1/finanzas/roles/c621c027-5ce6-5051-823a-4cce2ad82a36/pagar/` | igual a Django |
| finanzas: anular el rol | `POST /api/v1/finanzas/roles/c621c027-5ce6-5051-823a-4cce2ad82a36/anular/` | igual a Django |
| finanzas: anularlo otra vez | `POST /api/v1/finanzas/roles/c621c027-5ce6-5051-823a-4cce2ad82a36/anular/` | igual a Django |
| finanzas: anular un rol que no existe | `POST /api/v1/finanzas/roles/00000000-0000-4000-8000-000000000000/anular/` | igual a Django |
| finanzas: PDF del rol del mes | `GET /api/v1/finanzas/roles/{guardado:rol-recepcion}/pdf/` | igual a Django |
| finanzas: PDF del rol generado | `GET /api/v1/finanzas/roles/{guardado:rol-auxiliar}/pdf/` | igual a Django |
| finanzas: pagar el IESS del mes pasado | `POST /api/v1/finanzas/obligaciones/` | igual a Django |
| finanzas: pagarlo otra vez | `POST /api/v1/finanzas/obligaciones/` | igual a Django |
| finanzas: pagar el SRI anulado | `POST /api/v1/finanzas/obligaciones/` | igual a Django |
| finanzas: pagar el IESS con forma rara | `POST /api/v1/finanzas/obligaciones/` | igual a Django |
| finanzas: pagar el IESS del mes | `POST /api/v1/finanzas/obligaciones/` | igual a Django |
| finanzas: pagar el SRI del mes | `POST /api/v1/finanzas/obligaciones/` | igual a Django |
| finanzas: obligaciones después | `GET /api/v1/finanzas/obligaciones/` | igual a Django |
| finanzas: pagar el décimo tercero a una persona | `POST /api/v1/finanzas/decimos/` | igual a Django |
| finanzas: pagarlo a todos | `POST /api/v1/finanzas/decimos/` | igual a Django |
| finanzas: pagarlo otra vez | `POST /api/v1/finanzas/decimos/` | igual a Django |
| finanzas: décimo cuarto en la costa | `GET /api/v1/finanzas/decimos/?tipo=cuarto&anio={anio}` | igual a Django |
| finanzas: resumen después | `GET /api/v1/finanzas/resumen/` | igual a Django |
| finanzas: egresos después | `GET /api/v1/finanzas/egresos/?anulados=1` | igual a Django |
| finanzas: cobros después | `GET /api/v1/finanzas/cobros/` | igual a Django |
| finanzas: Excel del mes | `GET /api/v1/finanzas/exportar/` | igual a Django |
