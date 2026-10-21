# Modelo de datos

Las **78 tablas** del esquema de Django, leídas de una base con
todas sus migraciones aplicadas. La línea base de Flyway
(`backend/src/main/resources/db/migration/V1__linea_base_django.sql`) crea
exactamente esto, y Hibernate corre en modo `validate` contra ello: si una
entidad no cuadra con su tabla, el servidor no arranca.

Se lee la base y no los `models.py` a propósito: lo que hay que cumplir es
la tabla. Se regenera con `scripts/exportar-modelo-de-datos.py`.

Las tablas `django_*` y `auth_*` se conservan; el motivo está en la
cabecera de la línea base.

## Convenciones

- Las claves primarias de las tablas del dominio son `uuid`, no enteros:
  un identificador que no revela cuántos pacientes tiene una clínica ni en
  qué orden se crearon.
- Toda tabla del dominio lleva `tenant_id`: es por donde se filtra el
  aislamiento entre clínicas.
- Las marcas de tiempo son `timestamptz` y se guardan en UTC.
- Las tablas clínicas y financieras llevan borrado lógico (`is_active`,
  `deleted_at`): nunca se hace `DELETE` físico sobre datos de salud o de
  pagos.
- Los `JSONField` de Django son `jsonb`; en Kotlin se mapean con
  `@JdbcTypeCode(SqlTypes.JSON)`.

## `accounts_auditlog`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `action` | varchar(50) | no | — |
| `entity_type` | varchar(100) | no | — |
| `entity_id` | varchar(64) | no | — |
| `ip_address` | inet | sí | — |
| `metadata` | jsonb | no | — |
| `created_at` | timestamptz | no | — |
| `tenant_id` | uuid | sí | — |
| `user_id` | uuid | sí | — |

Claves foráneas: `tenant_id` → `common_tenant.id`, `user_id` → `accounts_user.id`

Índices (4):

- `accounts_au_entity__473722_idx`: btree (entity_type, entity_id)
- `accounts_au_user_id_7b1069_idx`: btree (user_id, created_at)
- `accounts_auditlog_tenant_id_4dd191ed`: btree (tenant_id)
- `accounts_auditlog_user_id_1dfbca79`: btree (user_id)

## `accounts_devicetoken`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `fcm_token` | varchar(255) | no | — |
| `platform` | varchar(10) | no | — |
| `is_active` | boolean | no | — |
| `created_at` | timestamptz | no | — |
| `user_id` | uuid | no | — |

Claves foráneas: `user_id` → `accounts_user.id`

Índices (3):

- `accounts_devicetoken_fcm_token_6b09e2ef_like`: btree (fcm_token varchar_pattern_ops)
- `accounts_devicetoken_fcm_token_key`: ÚNICO btree (fcm_token)
- `accounts_devicetoken_user_id_f7979080`: btree (user_id)

## `accounts_otpcode`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `code_hash` | varchar(128) | no | — |
| `purpose` | varchar(10) | no | — |
| `expires_at` | timestamptz | no | — |
| `used_at` | timestamptz | sí | — |
| `created_at` | timestamptz | no | — |
| `user_id` | uuid | no | — |

Claves foráneas: `user_id` → `accounts_user.id`

Índices (2):

- `accounts_ot_user_id_c9a585_idx`: btree (user_id, purpose, expires_at)
- `accounts_otpcode_user_id_c115171d`: btree (user_id)

## `accounts_passwordresettoken`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `token_hash` | varchar(128) | no | — |
| `expires_at` | timestamptz | no | — |
| `used_at` | timestamptz | sí | — |
| `created_at` | timestamptz | no | — |
| `user_id` | uuid | no | — |

Claves foráneas: `user_id` → `accounts_user.id`

Índices (1):

- `accounts_passwordresettoken_user_id_2789bc5c`: btree (user_id)

## `accounts_user`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `password` | varchar(128) | no | — |
| `last_login` | timestamptz | sí | — |
| `is_superuser` | boolean | no | — |
| `id` | uuid | no | — |
| `email` | varchar(254) | sí | — |
| `phone` | varchar(20) | sí | — |
| `role` | varchar(20) | no | — |
| `is_active` | boolean | no | — |
| `is_staff` | boolean | no | — |
| `created_at` | timestamptz | no | — |
| `tenant_id` | uuid | sí | — |
| `full_name` | varchar(150) | no | — |
| `must_change_password` | boolean | no | — |
| `preferencias` | jsonb | no | — |
| `funciones` | jsonb | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (5):

- `accounts_user_email_b2644a56_like`: btree (email varchar_pattern_ops)
- `accounts_user_email_key`: ÚNICO btree (email)
- `accounts_user_phone_c603acdd_like`: btree (phone varchar_pattern_ops)
- `accounts_user_phone_key`: ÚNICO btree (phone)
- `accounts_user_tenant_id_1906c0a8`: btree (tenant_id)

## `accounts_user_groups`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `user_id` | uuid | no | — |
| `group_id` | integer(32,0) | no | — |

Claves foráneas: `group_id` → `auth_group.id`, `user_id` → `accounts_user.id`

Índices (3):

- `accounts_user_groups_group_id_bd11a704`: btree (group_id)
- `accounts_user_groups_user_id_52b62117`: btree (user_id)
- `accounts_user_groups_user_id_group_id_59c0b32f_uniq`: ÚNICO btree (user_id, group_id)

## `accounts_user_user_permissions`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `user_id` | uuid | no | — |
| `permission_id` | integer(32,0) | no | — |

Claves foráneas: `permission_id` → `auth_permission.id`, `user_id` → `accounts_user.id`

Índices (3):

- `accounts_user_user_permi_user_id_permission_id_2ab516c2_uniq`: ÚNICO btree (user_id, permission_id)
- `accounts_user_user_permissions_permission_id_113bb443`: btree (permission_id)
- `accounts_user_user_permissions_user_id_e4f0a161`: btree (user_id)

## `agenda_appointment`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `scheduled_start` | timestamptz | no | — |
| `scheduled_end` | timestamptz | no | — |
| `status` | varchar(15) | no | — |
| `notes` | text | no | — |
| `checkin_at` | timestamptz | sí | — |
| `checkout_at` | timestamptz | sí | — |
| `google_calendar_event_id` | varchar(255) | no | — |
| `created_by_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `treatment_id` | uuid | sí | — |
| `doctor_id` | uuid | no | — |
| `attention_started_at` | timestamptz | sí | — |
| `reminder_sent_at` | timestamptz | sí | — |

Claves foráneas: `created_by_id` → `accounts_user.id`, `doctor_id` → `agenda_doctor.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`, `treatment_id` → `configuration_treatment.id`

Índices (8):

- `agenda_appo_status_8e291c_idx`: btree (status)
- `agenda_appo_tenant__8286f0_idx`: btree (tenant_id, scheduled_start)
- `agenda_appo_tenant__b5db2c_idx`: btree (tenant_id, doctor_id, scheduled_start)
- `agenda_appointment_created_by_id_984bcb6e`: btree (created_by_id)
- `agenda_appointment_doctor_id_115ba29e`: btree (doctor_id)
- `agenda_appointment_patient_id_3609bb1c`: btree (patient_id)
- `agenda_appointment_tenant_id_42c9eb59`: btree (tenant_id)
- `agenda_appointment_treatment_id_039e22c5`: btree (treatment_id)

## `agenda_doctor`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `calendar_color` | varchar(7) | no | — |
| `google_calendar_token` | jsonb | sí | — |
| `is_active` | boolean | no | — |
| `tenant_id` | uuid | no | — |
| `user_id` | uuid | no | — |
| `calendar_token` | uuid | no | — |
| `license_number` | varchar(50) | no | — |
| `signature_image` | text | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`, `user_id` → `accounts_user.id`

Índices (3):

- `agenda_doctor_calendar_token_3e5386ef_uniq`: ÚNICO btree (calendar_token)
- `agenda_doctor_tenant_id_747b7614`: btree (tenant_id)
- `agenda_doctor_user_id_key`: ÚNICO btree (user_id)

## `agenda_doctor_specialties`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `doctor_id` | uuid | no | — |
| `specialty_id` | uuid | no | — |

Claves foráneas: `doctor_id` → `agenda_doctor.id`, `specialty_id` → `specialties_specialty.id`

Índices (3):

- `agenda_doctor_specialties_doctor_id_e7113908`: btree (doctor_id)
- `agenda_doctor_specialties_doctor_id_specialty_id_b62d43ff_uniq`: ÚNICO btree (doctor_id, specialty_id)
- `agenda_doctor_specialties_specialty_id_d5da6d62`: btree (specialty_id)

## `app_paciente_mensajeconsultorio`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `texto` | text | no | — |
| `respuesta` | text | no | — |
| `respondido_en` | timestamptz | sí | — |
| `patient_id` | uuid | no | — |
| `respondido_por_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `patient_id` → `patients_patient.id`, `respondido_por_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `app_paciente_mensajeconsultorio_patient_id_3746b618`: btree (patient_id)
- `app_paciente_mensajeconsultorio_respondido_por_id_89ff00c5`: btree (respondido_por_id)
- `app_paciente_mensajeconsultorio_tenant_id_de9bfae2`: btree (tenant_id)

## `app_paciente_solicitudcita`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `fecha_preferida` | date | no | — |
| `franja` | varchar(12) | no | — |
| `motivo` | varchar(300) | no | — |
| `estado` | varchar(12) | no | — |
| `respuesta` | varchar(300) | no | — |
| `atendida_en` | timestamptz | sí | — |
| `atendida_por_id` | uuid | sí | — |
| `cita_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `atendida_por_id` → `accounts_user.id`, `cita_id` → `agenda_appointment.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (6):

- `app_paciente_solicitudcita_atendida_por_id_3cfaeeb5`: btree (atendida_por_id)
- `app_paciente_solicitudcita_cita_id_67e66216`: btree (cita_id)
- `app_paciente_solicitudcita_estado_76d8ecb7`: btree (estado)
- `app_paciente_solicitudcita_estado_76d8ecb7_like`: btree (estado varchar_pattern_ops)
- `app_paciente_solicitudcita_patient_id_aac8f5f2`: btree (patient_id)
- `app_paciente_solicitudcita_tenant_id_e577bf38`: btree (tenant_id)

## `auth_group`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | integer(32,0) | no | — |
| `name` | varchar(150) | no | — |

Índices (2):

- `auth_group_name_a6ea08ec_like`: btree (name varchar_pattern_ops)
- `auth_group_name_key`: ÚNICO btree (name)

## `auth_group_permissions`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `group_id` | integer(32,0) | no | — |
| `permission_id` | integer(32,0) | no | — |

Claves foráneas: `group_id` → `auth_group.id`, `permission_id` → `auth_permission.id`

Índices (3):

- `auth_group_permissions_group_id_b120cbf9`: btree (group_id)
- `auth_group_permissions_group_id_permission_id_0cd325b0_uniq`: ÚNICO btree (group_id, permission_id)
- `auth_group_permissions_permission_id_84c5c92e`: btree (permission_id)

## `auth_permission`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | integer(32,0) | no | — |
| `name` | varchar(255) | no | — |
| `content_type_id` | integer(32,0) | no | — |
| `codename` | varchar(100) | no | — |

Claves foráneas: `content_type_id` → `django_content_type.id`

Índices (2):

- `auth_permission_content_type_id_2f476e4b`: btree (content_type_id)
- `auth_permission_content_type_id_codename_01ab375a_uniq`: ÚNICO btree (content_type_id, codename)

## `billing_budget`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `status` | varchar(10) | no | — |
| `total_amount` | numeric(12,2) | no | — |
| `notes` | text | no | — |
| `created_at` | timestamptz | no | — |
| `created_by_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `created_by_id` → `accounts_user.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `billing_budget_created_by_id_d00a81a1`: btree (created_by_id)
- `billing_budget_patient_id_0383c5f8`: btree (patient_id)
- `billing_budget_tenant_id_b0255143`: btree (tenant_id)

## `billing_budgetitem`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `tooth_fdi_code` | varchar(2) | no | — |
| `quantity` | integer(32,0) | no | — |
| `unit_price` | numeric(10,2) | no | — |
| `budget_id` | uuid | no | — |
| `treatment_id` | uuid | no | — |

Claves foráneas: `budget_id` → `billing_budget.id`, `treatment_id` → `configuration_treatment.id`

Índices (2):

- `billing_budgetitem_budget_id_5222098d`: btree (budget_id)
- `billing_budgetitem_treatment_id_b4f48b97`: btree (treatment_id)

## `billing_doctorfee`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `fee_type` | varchar(10) | no | — |
| `value` | numeric(10,2) | no | — |
| `doctor_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `treatment_id` | uuid | sí | — |

Claves foráneas: `doctor_id` → `agenda_doctor.id`, `tenant_id` → `common_tenant.id`, `treatment_id` → `configuration_treatment.id`

Índices (3):

- `billing_doctorfee_doctor_id_ec7d8b5c`: btree (doctor_id)
- `billing_doctorfee_tenant_id_caddf9ac`: btree (tenant_id)
- `billing_doctorfee_treatment_id_4b03571d`: btree (treatment_id)

## `billing_installment`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `number` | integer(32,0) | no | — |
| `due_date` | date | no | — |
| `amount` | numeric(10,2) | no | — |
| `status` | varchar(10) | no | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `payment_plan_id` | uuid | no | — |

Claves foráneas: `patient_id` → `patients_patient.id`, `payment_plan_id` → `billing_paymentplan.id`, `tenant_id` → `common_tenant.id`

Índices (5):

- `billing_ins_due_dat_6d48cd_idx`: btree (due_date, status)
- `billing_ins_patient_bf9d1f_idx`: btree (patient_id, status)
- `billing_installment_patient_id_cb6ce6c4`: btree (patient_id)
- `billing_installment_payment_plan_id_ef8ce779`: btree (payment_plan_id)
- `billing_installment_tenant_id_2ade54b9`: btree (tenant_id)

## `billing_payment`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `amount` | numeric(10,2) | no | — |
| `method` | varchar(10) | no | — |
| `date` | date | no | — |
| `created_at` | timestamptz | no | — |
| `installment_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `registered_by_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |
| `numero_comprobante` | integer(32,0) | sí | — |
| `profesional_id` | uuid | sí | — |

Claves foráneas: `installment_id` → `billing_installment.id`, `patient_id` → `patients_patient.id`, `profesional_id` → `accounts_user.id`, `registered_by_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (6):

- `billing_pay_patient_70b51e_idx`: btree (patient_id, date)
- `billing_payment_installment_id_4fdad367`: btree (installment_id)
- `billing_payment_patient_id_0e796341`: btree (patient_id)
- `billing_payment_profesional_id_c1515951`: btree (profesional_id)
- `billing_payment_registered_by_id_bb0b6efa`: btree (registered_by_id)
- `billing_payment_tenant_id_d6eb4faf`: btree (tenant_id)

## `billing_paymentplan`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `total_amount` | numeric(12,2) | no | — |
| `installment_count` | integer(32,0) | no | — |
| `created_at` | timestamptz | no | — |
| `budget_id` | uuid | no | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `budget_id` → `billing_budget.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `billing_paymentplan_budget_id_key`: ÚNICO btree (budget_id)
- `billing_paymentplan_patient_id_342d19e8`: btree (patient_id)
- `billing_paymentplan_tenant_id_0bb000f0`: btree (tenant_id)

## `clinical_cie10`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `code` | varchar(10) | no | — |
| `description` | varchar(255) | no | — |

Índices (2):

- `clinical_cie10_code_eaba2c83_like`: btree (code varchar_pattern_ops)
- `clinical_cie10_code_key`: ÚNICO btree (code)

## `clinical_clinicalrecord`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `general_notes` | text | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `patient_id` | uuid | no | — |

Claves foráneas: `patient_id` → `patients_patient.id`

Índices (1):

- `clinical_clinicalrecord_patient_id_key`: ÚNICO btree (patient_id)

## `clinical_consentauditlog`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `action` | varchar(40) | no | — |
| `detail` | varchar(255) | no | — |
| `at` | timestamptz | no | — |
| `actor_id` | uuid | sí | — |
| `consent_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `actor_id` → `accounts_user.id`, `consent_id` → `clinical_informedconsent.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `clinical_consentauditlog_actor_id_f2d15210`: btree (actor_id)
- `clinical_consentauditlog_consent_id_92b19f6f`: btree (consent_id)
- `clinical_consentauditlog_tenant_id_a58193a3`: btree (tenant_id)

## `clinical_consenttemplate`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `procedure` | varchar(20) | no | — |
| `title` | varchar(200) | no | — |
| `procedure_text` | text | no | — |
| `benefits` | text | no | — |
| `risks` | text | no | — |
| `alternatives` | text | no | — |
| `body_text` | text | no | — |
| `created_by_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `created_by_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (2):

- `clinical_consenttemplate_created_by_id_a693be52`: btree (created_by_id)
- `clinical_consenttemplate_tenant_id_33e5cc2f`: btree (tenant_id)

## `clinical_diagnosis`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `tooth_fdi_code` | varchar(2) | no | — |
| `code` | varchar(20) | no | — |
| `description` | text | no | — |
| `date` | date | no | — |
| `created_at` | timestamptz | no | — |
| `doctor_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `diagnosis_kind` | varchar(3) | no | — |

Claves foráneas: `doctor_id` → `agenda_doctor.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `clinical_diagnosis_doctor_id_a6b8d033`: btree (doctor_id)
- `clinical_diagnosis_patient_id_15b106d0`: btree (patient_id)
- `clinical_diagnosis_tenant_id_9d2c5aac`: btree (tenant_id)

## `clinical_evolution`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `type` | varchar(20) | no | — |
| `date` | date | no | — |
| `notes` | text | no | — |
| `visible_to_patient` | boolean | no | — |
| `created_at` | timestamptz | no | — |
| `appointment_id` | uuid | sí | — |
| `doctor_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `created_by_id` | uuid | sí | — |
| `follow_up_date` | date | sí | — |

Claves foráneas: `appointment_id` → `agenda_appointment.id`, `created_by_id` → `accounts_user.id`, `doctor_id` → `agenda_doctor.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (7):

- `clinical_ev_patient_6ad850_idx`: btree (patient_id, type, visible_to_patient)
- `clinical_ev_patient_7671fb_idx`: btree (patient_id, date)
- `clinical_evolution_appointment_id_68693879`: btree (appointment_id)
- `clinical_evolution_created_by_id_e211279b`: btree (created_by_id)
- `clinical_evolution_doctor_id_5e0d91ed`: btree (doctor_id)
- `clinical_evolution_patient_id_970d0de3`: btree (patient_id)
- `clinical_evolution_tenant_id_714d4a7d`: btree (tenant_id)

## `clinical_examrequest`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `category` | varchar(20) | no | — |
| `detail` | varchar(255) | no | — |
| `status` | varchar(10) | no | — |
| `report_notes` | text | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `requested_at` | timestamptz | no | — |
| `reported_at` | timestamptz | sí | — |
| `patient_id` | uuid | no | — |
| `report_document_id` | bigint(64,0) | sí | — |
| `requested_by_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |
| `justification` | text | no | — |
| `observations` | text | no | — |
| `priority` | varchar(10) | no | — |

Claves foráneas: `patient_id` → `patients_patient.id`, `report_document_id` → `patients_patientdocument.id`, `requested_by_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (4):

- `clinical_examrequest_patient_id_406f07c7`: btree (patient_id)
- `clinical_examrequest_report_document_id_b1a74152`: btree (report_document_id)
- `clinical_examrequest_requested_by_id_1309b8e6`: btree (requested_by_id)
- `clinical_examrequest_tenant_id_daddf50d`: btree (tenant_id)

## `clinical_form033record`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `motivo_consulta` | text | no | — |
| `embarazada` | boolean | sí | — |
| `enfermedad_actual` | text | no | — |
| `antecedentes_personales` | jsonb | no | — |
| `antecedentes_familiares` | jsonb | no | — |
| `temperatura` | numeric(4,1) | sí | — |
| `pulso` | smallint(16,0) | sí | — |
| `frecuencia_respiratoria` | smallint(16,0) | sí | — |
| `presion_arterial` | varchar(15) | no | — |
| `examen_estomatognatico` | jsonb | no | — |
| `indicadores_salud_bucal` | jsonb | no | — |
| `professional_snapshot` | jsonb | no | — |
| `date` | date | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `created_by_id` | uuid | sí | — |
| `doctor_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `created_by_id` → `accounts_user.id`, `doctor_id` → `agenda_doctor.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (4):

- `clinical_form033record_created_by_id_64c3c7a6`: btree (created_by_id)
- `clinical_form033record_doctor_id_6010a448`: btree (doctor_id)
- `clinical_form033record_patient_id_ff06fe10`: btree (patient_id)
- `clinical_form033record_tenant_id_6f48777f`: btree (tenant_id)

## `clinical_informedconsent`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `title` | varchar(200) | no | — |
| `body_text` | text | no | — |
| `pdf_file` | varchar(100) | sí | — |
| `signature_image` | varchar(100) | sí | — |
| `signed_at` | timestamptz | sí | — |
| `ip_address` | inet | sí | — |
| `device_info` | varchar(255) | no | — |
| `created_at` | timestamptz | no | — |
| `created_by_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `treatment_plan_id` | uuid | sí | — |
| `alternatives` | text | no | — |
| `benefits` | text | no | — |
| `observations` | text | no | — |
| `procedure` | text | no | — |
| `risks` | text | no | — |
| `status` | varchar(10) | no | — |

Claves foráneas: `created_by_id` → `accounts_user.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`, `treatment_plan_id` → `clinical_treatmentplan.id`

Índices (4):

- `clinical_informedconsent_created_by_id_157f498d`: btree (created_by_id)
- `clinical_informedconsent_patient_id_7c2fa52c`: btree (patient_id)
- `clinical_informedconsent_tenant_id_89c0e952`: btree (tenant_id)
- `clinical_informedconsent_treatment_plan_id_b55155d2`: btree (treatment_plan_id)

## `clinical_odontogramstate`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `code` | varchar(30) | no | — |
| `label` | varchar(100) | no | — |
| `color` | varchar(7) | no | — |
| `order` | integer(32,0) | no | — |
| `is_active` | boolean | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `clinical_odontogramstate_tenant_id_39b1347c`: btree (tenant_id)
- `unique_odontogram_state_code_per_tenant`: ÚNICO btree (tenant_id, code)

## `clinical_periodontalexam`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `date` | date | no | — |
| `notes` | text | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `created_by_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `created_by_id` → `accounts_user.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `clinical_periodontalexam_created_by_id_20adf4bb`: btree (created_by_id)
- `clinical_periodontalexam_patient_id_5913c67d`: btree (patient_id)
- `clinical_periodontalexam_tenant_id_4848a548`: btree (tenant_id)

## `clinical_periodontaltooth`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `tooth_code` | varchar(2) | no | — |
| `is_present` | boolean | no | — |
| `has_implant` | boolean | no | — |
| `mobility` | smallint(16,0) | no | — |
| `furcation_buccal` | smallint(16,0) | no | — |
| `furcation_lingual` | smallint(16,0) | no | — |
| `gum_width` | smallint(16,0) | no | — |
| `probing_depth` | jsonb | no | — |
| `gingival_margin` | jsonb | no | — |
| `bleeding` | jsonb | no | — |
| `plaque` | jsonb | no | — |
| `notes` | varchar(255) | no | — |
| `exam_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `exam_id` → `clinical_periodontalexam.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `clinical_periodontaltooth_exam_id_1edb94d1`: btree (exam_id)
- `clinical_periodontaltooth_tenant_id_edfaa989`: btree (tenant_id)
- `unique_tooth_per_periodontal_exam`: ÚNICO btree (exam_id, tooth_code)

## `clinical_radiographphoto`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `tooth_fdi_code` | varchar(2) | no | — |
| `file` | varchar(100) | no | — |
| `type` | varchar(15) | no | — |
| `description` | varchar(255) | no | — |
| `date` | date | no | — |
| `created_at` | timestamptz | no | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `uploaded_by_id` | uuid | sí | — |

Claves foráneas: `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`, `uploaded_by_id` → `accounts_user.id`

Índices (3):

- `clinical_radiographphoto_patient_id_0171f964`: btree (patient_id)
- `clinical_radiographphoto_tenant_id_3967014f`: btree (tenant_id)
- `clinical_radiographphoto_uploaded_by_id_4c2bd168`: btree (uploaded_by_id)

## `clinical_toothrecord`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `tooth_fdi_code` | varchar(2) | no | — |
| `surface` | varchar(20) | no | — |
| `date` | date | no | — |
| `notes` | text | no | — |
| `created_at` | timestamptz | no | — |
| `doctor_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `state_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `treatment_plan_item_id` | uuid | sí | — |
| `mobility` | smallint(16,0) | sí | — |
| `recession` | smallint(16,0) | sí | — |

Claves foráneas: `doctor_id` → `agenda_doctor.id`, `patient_id` → `patients_patient.id`, `state_id` → `clinical_odontogramstate.id`, `tenant_id` → `common_tenant.id`, `treatment_plan_item_id` → `clinical_treatmentplanitem.id`

Índices (7):

- `clinical_to_patient_00aef7_idx`: btree (patient_id, created_at DESC)
- `clinical_to_patient_12eb96_idx`: btree (patient_id, tooth_fdi_code, surface)
- `clinical_toothrecord_doctor_id_2dcf5382`: btree (doctor_id)
- `clinical_toothrecord_patient_id_26fe4e34`: btree (patient_id)
- `clinical_toothrecord_state_id_0d9019f9`: btree (state_id)
- `clinical_toothrecord_tenant_id_59fa4927`: btree (tenant_id)
- `clinical_toothrecord_treatment_plan_item_id_eb438185`: btree (treatment_plan_item_id)

## `clinical_treatmentplan`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `status` | varchar(10) | no | — |
| `notes` | text | no | — |
| `created_at` | timestamptz | no | — |
| `created_by_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `created_by_id` → `agenda_doctor.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `clinical_treatmentplan_created_by_id_58a54d1c`: btree (created_by_id)
- `clinical_treatmentplan_patient_id_348d43af`: btree (patient_id)
- `clinical_treatmentplan_tenant_id_c38d4324`: btree (tenant_id)

## `clinical_treatmentplanitem`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `tooth_fdi_code` | varchar(2) | no | — |
| `order` | integer(32,0) | no | — |
| `status` | varchar(15) | no | — |
| `estimated_price` | numeric(10,2) | no | — |
| `treatment_id` | uuid | no | — |
| `treatment_plan_id` | uuid | no | — |
| `price_is_manual` | boolean | no | — |

Claves foráneas: `treatment_id` → `configuration_treatment.id`, `treatment_plan_id` → `clinical_treatmentplan.id`

Índices (2):

- `clinical_treatmentplanitem_treatment_id_b9c55d4c`: btree (treatment_id)
- `clinical_treatmentplanitem_treatment_plan_id_8233d3f8`: btree (treatment_plan_id)

## `clinical_treatmentplantemplate`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `name` | varchar(150) | no | — |
| `description` | text | no | — |
| `is_active` | boolean | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `clinical_treatmentplantemplate_tenant_id_b4ec6f54`: btree (tenant_id)
- `clinical_treatmentplantemplate_tenant_id_name_d9c57163_uniq`: ÚNICO btree (tenant_id, name)

## `clinical_treatmentplantemplateitem`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `order` | integer(32,0) | no | — |
| `notes` | varchar(255) | no | — |
| `template_id` | uuid | no | — |
| `treatment_id` | uuid | no | — |

Claves foráneas: `template_id` → `clinical_treatmentplantemplate.id`, `treatment_id` → `configuration_treatment.id`

Índices (2):

- `clinical_treatmentplantemplateitem_template_id_aa4360e6`: btree (template_id)
- `clinical_treatmentplantemplateitem_treatment_id_3e56ec95`: btree (treatment_id)

## `common_aceptacioncondiciones`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `version` | varchar(20) | no | — |
| `nombre` | varchar(255) | no | — |
| `correo` | varchar(254) | no | — |
| `ip` | inet | sí | — |
| `aceptada_por_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `aceptada_por_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (2):

- `common_aceptacioncondiciones_aceptada_por_id_ee11a290`: btree (aceptada_por_id)
- `common_aceptacioncondiciones_tenant_id_37440c87`: btree (tenant_id)

## `common_platformconfiguration`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `platform_name` | varchar(120) | no | — |
| `logo_url` | varchar(200) | no | — |
| `smtp_host` | varchar(255) | no | — |
| `smtp_port` | integer(32,0) | no | — |
| `smtp_user` | varchar(255) | no | — |
| `smtp_password` | varchar(255) | no | — |
| `timezone` | varchar(64) | no | — |
| `currency` | varchar(8) | no | — |
| `updated_at` | timestamptz | no | — |
| `proveedor_correo` | varchar(120) | no | — |
| `titular_correo` | varchar(254) | no | — |
| `titular_nombre` | varchar(200) | no | — |
| `titular_ruc` | varchar(20) | no | — |
| `funciones_publicadas` | jsonb | no | — |
| `canal_codigo_paciente` | varchar(10) | no | — |
| `google_client_id` | varchar(200) | no | — |

## `common_solicitudclinica`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `tipo` | varchar(12) | no | — |
| `estado` | varchar(12) | no | — |
| `clinica` | varchar(150) | no | — |
| `ruc` | varchar(13) | no | — |
| `ciudad` | varchar(80) | no | — |
| `nombre` | varchar(150) | no | — |
| `correo` | varchar(254) | no | — |
| `telefono` | varchar(20) | no | — |
| `mensaje` | text | no | — |
| `codigo_hash` | varchar(128) | no | — |
| `codigo_vence` | timestamptz | sí | — |
| `intentos` | smallint(16,0) | no | — |
| `ip` | inet | sí | — |
| `nota` | varchar(300) | no | — |
| `tenant_id` | uuid | sí | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (1):

- `common_solicitudclinica_tenant_id_8109a183`: btree (tenant_id)

## `common_tenant`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `name` | varchar(255) | no | — |
| `ruc` | varchar(20) | no | — |
| `is_active` | boolean | no | — |
| `created_at` | timestamptz | no | — |
| `address` | varchar(255) | no | — |
| `phone` | varchar(30) | no | — |
| `email` | varchar(254) | no | — |
| `funcionalidades` | jsonb | no | — |
| `modulos_clinica` | jsonb | no | — |
| `es_prueba` | boolean | no | — |

## `configuration_agreement`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `name` | varchar(150) | no | — |
| `discount_percentage` | numeric(5,2) | sí | — |
| `is_active` | boolean | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (1):

- `configuration_agreement_tenant_id_385126d9`: btree (tenant_id)

## `configuration_clinicbranding`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `logo` | varchar(100) | sí | — |
| `theme` | jsonb | no | — |
| `tenant_id` | uuid | no | — |
| `display_name` | varchar(120) | no | — |
| `short_name` | varchar(40) | no | — |
| `address` | varchar(200) | no | — |
| `email` | varchar(254) | no | — |
| `phone` | varchar(40) | no | — |
| `icono` | varchar(100) | sí | — |
| `logo_oscuro` | varchar(100) | sí | — |
| `paleta` | jsonb | no | — |
| `temas` | jsonb | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `configuration_clinicbranding_tenant_id_32c0fff9`: btree (tenant_id)
- `uniq_branding_per_tenant`: ÚNICO btree (tenant_id)

## `configuration_documentappearance`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `header` | jsonb | no | — |
| `footer` | jsonb | no | — |
| `typography` | jsonb | no | — |
| `palette` | jsonb | no | — |
| `tables` | jsonb | no | — |
| `page` | jsonb | no | — |
| `logo` | jsonb | no | — |
| `signature` | jsonb | no | — |
| `watermark` | jsonb | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `configuration_documentappearance_tenant_id_d1c8966d`: btree (tenant_id)
- `uniq_docappearance_per_tenant`: ÚNICO btree (tenant_id)

## `configuration_systemparameter`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `key` | varchar(100) | no | — |
| `value` | varchar(255) | no | — |
| `description` | text | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `configuration_systemparameter_tenant_id_876cfc1d`: btree (tenant_id)
- `unique_parameter_key_per_tenant`: ÚNICO btree (tenant_id, key)

## `configuration_tariff`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `price` | numeric(10,2) | no | — |
| `agreement_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |
| `treatment_id` | uuid | no | — |

Claves foráneas: `agreement_id` → `configuration_agreement.id`, `tenant_id` → `common_tenant.id`, `treatment_id` → `configuration_treatment.id`

Índices (4):

- `configuration_tariff_agreement_id_c30ef6b1`: btree (agreement_id)
- `configuration_tariff_tenant_id_199b3541`: btree (tenant_id)
- `configuration_tariff_treatment_id_886df80c`: btree (treatment_id)
- `unique_tariff_per_treatment_agreement`: ÚNICO btree (treatment_id, agreement_id)

## `configuration_treatment`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `name` | varchar(150) | no | — |
| `base_price` | numeric(10,2) | no | — |
| `consumes_inventory` | boolean | no | — |
| `is_active` | boolean | no | — |
| `specialty_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `specialty_id` → `specialties_specialty.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `configurati_tenant__abcf51_idx`: btree (tenant_id, specialty_id)
- `configuration_treatment_specialty_id_e08704c6`: btree (specialty_id)
- `configuration_treatment_tenant_id_f1815804`: btree (tenant_id)

## `configuration_treatmentinventoryitem`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `quantity_used` | numeric(10,2) | no | — |
| `product_id` | uuid | no | — |
| `treatment_id` | uuid | no | — |

Claves foráneas: `product_id` → `inventory_product.id`, `treatment_id` → `configuration_treatment.id`

Índices (2):

- `configuration_treatmentinventoryitem_product_id_1ac4bf1e`: btree (product_id)
- `configuration_treatmentinventoryitem_treatment_id_6203501f`: btree (treatment_id)

## `django_admin_log`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | integer(32,0) | no | — |
| `action_time` | timestamptz | no | — |
| `object_id` | text | sí | — |
| `object_repr` | varchar(200) | no | — |
| `action_flag` | smallint(16,0) | no | — |
| `change_message` | text | no | — |
| `content_type_id` | integer(32,0) | sí | — |
| `user_id` | uuid | no | — |

Claves foráneas: `content_type_id` → `django_content_type.id`, `user_id` → `accounts_user.id`

Índices (2):

- `django_admin_log_content_type_id_c4bce8eb`: btree (content_type_id)
- `django_admin_log_user_id_c564eba6`: btree (user_id)

## `django_content_type`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | integer(32,0) | no | — |
| `app_label` | varchar(100) | no | — |
| `model` | varchar(100) | no | — |

Índices (1):

- `django_content_type_app_label_model_76bd3d3b_uniq`: ÚNICO btree (app_label, model)

## `django_migrations`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `app` | varchar(255) | no | — |
| `name` | varchar(255) | no | — |
| `applied` | timestamptz | no | — |

## `django_session`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `session_key` | varchar(40) | no | — |
| `session_data` | text | no | — |
| `expire_date` | timestamptz | no | — |

Índices (2):

- `django_session_expire_date_a5c62663`: btree (expire_date)
- `django_session_session_key_c0390e0f_like`: btree (session_key varchar_pattern_ops)

## `finanzas_categoriaegreso`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `nombre` | varchar(80) | no | — |
| `clave` | varchar(30) | no | — |
| `activa` | boolean | no | — |
| `orden` | smallint(16,0) | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (1):

- `finanzas_categoriaegreso_tenant_id_82f8c9b0`: btree (tenant_id)

## `finanzas_cierrecaja`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `fecha` | date | no | — |
| `saldo_inicial` | numeric(12,2) | no | — |
| `cobros_efectivo` | numeric(12,2) | no | — |
| `egresos_efectivo` | numeric(12,2) | no | — |
| `esperado` | numeric(12,2) | no | — |
| `contado` | numeric(12,2) | no | — |
| `diferencia` | numeric(12,2) | no | — |
| `retirado` | numeric(12,2) | no | — |
| `observaciones` | varchar(300) | no | — |
| `cerrado_por_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `cerrado_por_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `finanzas_cierrecaja_cerrado_por_id_3cc40077`: btree (cerrado_por_id)
- `finanzas_cierrecaja_tenant_id_4806a048`: btree (tenant_id)
- `un_cierre_por_dia`: ÚNICO btree (tenant_id, fecha)

## `finanzas_configuracionfinanzas`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `salario_basico` | numeric(8,2) | no | — |
| `aporte_personal_iess` | numeric(5,2) | no | — |
| `aporte_patronal_iess` | numeric(5,2) | no | — |
| `aporte_iece_secap` | numeric(5,2) | no | — |
| `fondos_reserva` | numeric(5,2) | no | — |
| `retencion_honorarios` | numeric(5,2) | no | — |
| `siguiente_comprobante` | integer(32,0) | no | — |
| `tenant_id` | uuid | no | — |
| `recargo_extraordinarias` | numeric(5,2) | no | — |
| `recargo_suplementarias` | numeric(5,2) | no | — |
| `region` | varchar(6) | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `finanzas_config_unica`: ÚNICO btree (tenant_id)
- `finanzas_configuracionfinanzas_tenant_id_e26caa3a`: btree (tenant_id)

## `finanzas_egreso`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `fecha` | date | no | — |
| `concepto` | varchar(200) | no | — |
| `monto` | numeric(12,2) | no | — |
| `forma_pago` | varchar(15) | no | — |
| `estado` | varchar(10) | no | — |
| `vence` | date | sí | — |
| `proveedor` | varchar(150) | no | — |
| `proveedor_ruc` | varchar(13) | no | — |
| `numero_factura` | varchar(20) | no | — |
| `comprobante` | varchar(100) | sí | — |
| `descontar_al_profesional` | boolean | no | — |
| `origen` | varchar(20) | no | — |
| `anulado` | boolean | no | — |
| `motivo_anulacion` | varchar(200) | no | — |
| `categoria_id` | uuid | no | — |
| `profesional_id` | uuid | sí | — |
| `registrado_por_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |
| `periodo` | date | sí | — |

Claves foráneas: `categoria_id` → `finanzas_categoriaegreso.id`, `profesional_id` → `accounts_user.id`, `registrado_por_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (5):

- `finanzas_eg_tenant__8e0b8f_idx`: btree (tenant_id, fecha)
- `finanzas_egreso_categoria_id_2bbeb881`: btree (categoria_id)
- `finanzas_egreso_profesional_id_f7b52ca1`: btree (profesional_id)
- `finanzas_egreso_registrado_por_id_6ea475e3`: btree (registrado_por_id)
- `finanzas_egreso_tenant_id_bdff3484`: btree (tenant_id)

## `finanzas_liquidacion`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `numero` | integer(32,0) | no | — |
| `desde` | date | no | — |
| `hasta` | date | no | — |
| `base` | numeric(12,2) | no | — |
| `porcentaje` | numeric(5,2) | no | — |
| `honorario` | numeric(12,2) | no | — |
| `descuentos` | numeric(12,2) | no | — |
| `ajustes` | numeric(12,2) | no | — |
| `retencion_porcentaje` | numeric(5,2) | no | — |
| `retencion` | numeric(12,2) | no | — |
| `a_pagar` | numeric(12,2) | no | — |
| `detalle` | jsonb | no | — |
| `nota` | varchar(300) | no | — |
| `estado` | varchar(10) | no | — |
| `creada_por_id` | uuid | sí | — |
| `egreso_id` | uuid | sí | — |
| `profesional_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `creada_por_id` → `accounts_user.id`, `egreso_id` → `finanzas_egreso.id`, `profesional_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (4):

- `finanzas_liquidacion_creada_por_id_f6325f8c`: btree (creada_por_id)
- `finanzas_liquidacion_egreso_id_key`: ÚNICO btree (egreso_id)
- `finanzas_liquidacion_profesional_id_657bfe1f`: btree (profesional_id)
- `finanzas_liquidacion_tenant_id_859ef846`: btree (tenant_id)

## `finanzas_perfillaboral`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `modalidad` | varchar(12) | no | — |
| `cedula` | varchar(13) | no | — |
| `cargo` | varchar(80) | no | — |
| `base_honorarios` | varchar(10) | no | — |
| `porcentaje` | numeric(5,2) | no | — |
| `monto_fijo` | numeric(10,2) | no | — |
| `emite_factura` | boolean | no | — |
| `sueldo` | numeric(10,2) | no | — |
| `fecha_ingreso` | date | sí | — |
| `decimo_tercero_mensual` | boolean | no | — |
| `decimo_cuarto_mensual` | boolean | no | — |
| `fondos_reserva_mensual` | boolean | no | — |
| `tenant_id` | uuid | no | — |
| `usuario_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`, `usuario_id` → `accounts_user.id`

Índices (2):

- `finanzas_perfillaboral_tenant_id_bcdd7d90`: btree (tenant_id)
- `finanzas_perfillaboral_usuario_id_key`: ÚNICO btree (usuario_id)

## `finanzas_rolpagos`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `mes` | date | no | — |
| `dias_trabajados` | smallint(16,0) | no | — |
| `horas_extra` | numeric(10,2) | no | — |
| `otros_ingresos` | numeric(10,2) | no | — |
| `anticipos` | numeric(10,2) | no | — |
| `prestamos_iess` | numeric(10,2) | no | — |
| `otros_descuentos` | numeric(10,2) | no | — |
| `calculo` | jsonb | no | — |
| `neto` | numeric(12,2) | no | — |
| `costo_empleador` | numeric(12,2) | no | — |
| `estado` | varchar(10) | no | — |
| `creado_por_id` | uuid | sí | — |
| `egreso_id` | uuid | sí | — |
| `empleado_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |
| `horas_extraordinarias` | numeric(6,2) | no | — |
| `horas_suplementarias` | numeric(6,2) | no | — |

Claves foráneas: `creado_por_id` → `accounts_user.id`, `egreso_id` → `finanzas_egreso.id`, `empleado_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (5):

- `finanzas_rolpagos_creado_por_id_afd5ede5`: btree (creado_por_id)
- `finanzas_rolpagos_egreso_id_key`: ÚNICO btree (egreso_id)
- `finanzas_rolpagos_empleado_id_601fdebf`: btree (empleado_id)
- `finanzas_rolpagos_tenant_id_06774cef`: btree (tenant_id)
- `un_rol_por_mes`: ÚNICO btree (tenant_id, empleado_id, mes) WHERE (NOT ((estado)::text = 'anulado'::text))

## `inventory_batch`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `batch_number` | varchar(100) | no | — |
| `quantity` | numeric(10,2) | no | — |
| `expiration_date` | date | sí | — |
| `received_at` | date | no | — |
| `tenant_id` | uuid | no | — |
| `product_id` | uuid | no | — |

Claves foráneas: `product_id` → `inventory_product.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `inventory_b_expirat_f9736f_idx`: btree (expiration_date)
- `inventory_batch_product_id_4a06f58c`: btree (product_id)
- `inventory_batch_tenant_id_6620a07d`: btree (tenant_id)

## `inventory_inventorymovement`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `id` | uuid | no | — |
| `movement_type` | varchar(5) | no | — |
| `quantity` | numeric(10,2) | no | — |
| `reason` | varchar(15) | no | — |
| `date` | timestamptz | no | — |
| `batch_id` | uuid | sí | — |
| `related_treatment_plan_item_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |
| `product_id` | uuid | no | — |

Claves foráneas: `batch_id` → `inventory_batch.id`, `product_id` → `inventory_product.id`, `related_treatment_plan_item_id` → `clinical_treatmentplanitem.id`, `tenant_id` → `common_tenant.id`

Índices (4):

- `inventory_inventorymovemen_related_treatment_plan_ite_276dbaff`: btree (related_treatment_plan_item_id)
- `inventory_inventorymovement_batch_id_ebcccf29`: btree (batch_id)
- `inventory_inventorymovement_product_id_74123ceb`: btree (product_id)
- `inventory_inventorymovement_tenant_id_11492cc9`: btree (tenant_id)

## `inventory_product`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `name` | varchar(150) | no | — |
| `unit` | varchar(30) | no | — |
| `min_stock` | numeric(10,2) | no | — |
| `is_active` | boolean | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (1):

- `inventory_product_tenant_id_a7ffb676`: btree (tenant_id)

## `logros_logro`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `nombre` | varchar(80) | no | — |
| `descripcion` | varchar(200) | no | — |
| `icono` | varchar(30) | no | — |
| `beneficio` | varchar(200) | no | — |
| `regla` | varchar(25) | no | — |
| `meses_requeridos` | smallint(16,0) | no | — |
| `activo` | boolean | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `logro_unico_por_clinica`: ÚNICO btree (tenant_id, nombre)
- `logros_logro_tenant_id_d6d6f7ed`: btree (tenant_id)

## `logros_logrodepaciente`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `otorgado_en` | timestamptz | no | — |
| `periodo` | date | sí | — |
| `nota` | varchar(200) | no | — |
| `logro_id` | uuid | no | — |
| `otorgado_por_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `logro_id` → `logros_logro.id`, `otorgado_por_id` → `accounts_user.id`, `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (5):

- `logros_logrodepaciente_logro_id_a1e96671`: btree (logro_id)
- `logros_logrodepaciente_otorgado_por_id_a23a08f0`: btree (otorgado_por_id)
- `logros_logrodepaciente_patient_id_54195ee6`: btree (patient_id)
- `logros_logrodepaciente_tenant_id_2f8f80bc`: btree (tenant_id)
- `un_logro_por_paciente_y_periodo`: ÚNICO btree (patient_id, logro_id, periodo) WHERE (periodo IS NOT NULL)

## `patients_avisoprivacidadentregado`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `version` | varchar(20) | no | — |
| `medio` | varchar(20) | no | — |
| `patient_id` | uuid | no | — |
| `registrado_por_id` | uuid | sí | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `patient_id` → `patients_patient.id`, `registrado_por_id` → `accounts_user.id`, `tenant_id` → `common_tenant.id`

Índices (3):

- `patients_avisoprivacidadentregado_patient_id_a77f75f2`: btree (patient_id)
- `patients_avisoprivacidadentregado_registrado_por_id_0b6a69bc`: btree (registrado_por_id)
- `patients_avisoprivacidadentregado_tenant_id_b49c0fca`: btree (tenant_id)

## `patients_medicalbackground`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `allergies` | text | no | — |
| `medications` | text | no | — |
| `conditions` | text | no | — |
| `is_pregnant` | boolean | sí | — |
| `updated_at` | timestamptz | no | — |
| `updated_by_id` | uuid | sí | — |
| `patient_id` | uuid | no | — |

Claves foráneas: `patient_id` → `patients_patient.id`, `updated_by_id` → `accounts_user.id`

Índices (2):

- `patients_medicalbackground_patient_id_key`: ÚNICO btree (patient_id)
- `patients_medicalbackground_updated_by_id_b9b0b468`: btree (updated_by_id)

## `patients_patient`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `is_active` | boolean | no | — |
| `deleted_at` | timestamptz | sí | — |
| `first_name` | varchar(100) | no | — |
| `last_name` | varchar(100) | no | — |
| `national_id` | varchar(20) | no | — |
| `birth_date` | date | sí | — |
| `phone` | varchar(20) | no | — |
| `email` | varchar(254) | no | — |
| `address` | varchar(255) | no | — |
| `photo` | varchar(100) | sí | — |
| `tenant_id` | uuid | no | — |
| `user_id` | uuid | sí | — |
| `sex` | varchar(1) | no | — |
| `agreement_id` | uuid | sí | — |

Claves foráneas: `agreement_id` → `configuration_agreement.id`, `tenant_id` → `common_tenant.id`, `user_id` → `accounts_user.id`

Índices (6):

- `patients_pa_tenant__23a062_idx`: btree (tenant_id, last_name, first_name)
- `patients_pa_tenant__d2d66d_idx`: btree (tenant_id, national_id)
- `patients_patient_agreement_id_a1526dde`: btree (agreement_id)
- `patients_patient_tenant_id_36ccbf54`: btree (tenant_id)
- `patients_patient_user_id_key`: ÚNICO btree (user_id)
- `unique_national_id_per_tenant`: ÚNICO btree (tenant_id, national_id) WHERE is_active

## `patients_patientdocument`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `doc_type` | varchar(20) | no | — |
| `file` | varchar(100) | no | — |
| `description` | varchar(255) | no | — |
| `uploaded_at` | timestamptz | no | — |
| `is_active` | boolean | no | — |
| `patient_id` | uuid | no | — |
| `uploaded_by_id` | uuid | sí | — |

Claves foráneas: `patient_id` → `patients_patient.id`, `uploaded_by_id` → `accounts_user.id`

Índices (2):

- `patients_patientdocument_patient_id_992efd30`: btree (patient_id)
- `patients_patientdocument_uploaded_by_id_aab77fbc`: btree (uploaded_by_id)

## `specialties_specialty`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `name` | varchar(100) | no | — |
| `description` | text | no | — |
| `is_active` | boolean | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `specialties_specialty_tenant_id_7fb41799`: btree (tenant_id)
- `unique_specialty_name_per_tenant`: ÚNICO btree (tenant_id, name)

## `specialties_specialtyform`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `updated_at` | timestamptz | no | — |
| `date` | date | no | — |
| `form_data` | jsonb | no | — |
| `created_at` | timestamptz | no | — |
| `doctor_id` | uuid | no | — |
| `patient_id` | uuid | no | — |
| `specialty_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `doctor_id` → `agenda_doctor.id`, `patient_id` → `patients_patient.id`, `specialty_id` → `specialties_specialty.id`, `tenant_id` → `common_tenant.id`

Índices (5):

- `specialties_patient_83a62b_idx`: btree (patient_id, specialty_id)
- `specialties_specialtyform_doctor_id_056fef94`: btree (doctor_id)
- `specialties_specialtyform_patient_id_fa2d3c63`: btree (patient_id)
- `specialties_specialtyform_specialty_id_24b274fd`: btree (specialty_id)
- `specialties_specialtyform_tenant_id_cd68dd62`: btree (tenant_id)

## `token_blacklist_blacklistedtoken`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `blacklisted_at` | timestamptz | no | — |
| `token_id` | bigint(64,0) | no | — |

Claves foráneas: `token_id` → `token_blacklist_outstandingtoken.id`

Índices (1):

- `token_blacklist_blacklistedtoken_token_id_key`: ÚNICO btree (token_id)

## `token_blacklist_outstandingtoken`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | bigint(64,0) | no | — |
| `token` | text | no | — |
| `created_at` | timestamptz | sí | — |
| `expires_at` | timestamptz | no | — |
| `user_id` | uuid | sí | — |
| `jti` | varchar(255) | no | — |

Claves foráneas: `user_id` → `accounts_user.id`

Índices (3):

- `token_blacklist_outstandingtoken_jti_hex_d9bdf6f7_like`: btree (jti varchar_pattern_ops)
- `token_blacklist_outstandingtoken_jti_hex_d9bdf6f7_uniq`: ÚNICO btree (jti)
- `token_blacklist_outstandingtoken_user_id_83bc629a`: btree (user_id)

## `whatsapp_configuracionwhatsapp`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `phone_number_id` | varchar(60) | no | — |
| `numero_visible` | varchar(30) | no | — |
| `access_token_cifrado` | text | no | — |
| `plantilla_recordatorio` | varchar(80) | no | — |
| `activo` | boolean | no | — |
| `comprobado_en` | timestamptz | sí | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (2):

- `una_configuracion_whatsapp_por_clinica`: ÚNICO btree (tenant_id)
- `whatsapp_configuracionwhatsapp_tenant_id_fcce079d`: btree (tenant_id)

## `whatsapp_whatsappmessagelog`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `patient_phone` | varchar(20) | no | — |
| `status` | varchar(20) | no | — |
| `provider_message_id` | varchar(100) | no | — |
| `raw_payload` | jsonb | no | — |
| `tenant_id` | uuid | no | — |
| `template_id` | uuid | sí | — |

Claves foráneas: `template_id` → `whatsapp_whatsapptemplate.id`, `tenant_id` → `common_tenant.id`

Índices (2):

- `whatsapp_whatsappmessagelog_template_id_5dc20bca`: btree (template_id)
- `whatsapp_whatsappmessagelog_tenant_id_dc096b44`: btree (tenant_id)

## `whatsapp_whatsappoptin`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `opted_in_at` | timestamptz | no | — |
| `channel` | varchar(50) | no | — |
| `revoked_at` | timestamptz | sí | — |
| `patient_id` | uuid | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `patient_id` → `patients_patient.id`, `tenant_id` → `common_tenant.id`

Índices (2):

- `whatsapp_whatsappoptin_patient_id_852b52be`: btree (patient_id)
- `whatsapp_whatsappoptin_tenant_id_543bcf8d`: btree (tenant_id)

## `whatsapp_whatsapptemplate`

| Columna | Tipo | Nulable | Por omisión |
|---|---|---|---|
| `id` | uuid | no | — |
| `created_at` | timestamptz | no | — |
| `updated_at` | timestamptz | no | — |
| `name` | varchar(100) | no | — |
| `meta_template_name` | varchar(100) | no | — |
| `category` | varchar(20) | no | — |
| `language` | varchar(10) | no | — |
| `variables` | jsonb | no | — |
| `is_active` | boolean | no | — |
| `tenant_id` | uuid | no | — |

Claves foráneas: `tenant_id` → `common_tenant.id`

Índices (1):

- `whatsapp_whatsapptemplate_tenant_id_8c9e1d7a`: btree (tenant_id)

