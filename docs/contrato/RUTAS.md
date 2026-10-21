# Rutas de la API de Django

Las **202 rutas** bajo `/api/v1/` que expone el servidor de `clinube`, con
la vista que las implementa y el archivo y la línea donde está.

Generado con `scripts/exportar-rutas.py` recorriendo el resolvedor de URL
de Django, no leyendo los `urls.py` a mano: así no se escapa ninguna ruta
incluida indirectamente. Se regenera cuando `clinube` avance.

Todas llevan **barra final**. Los `<uuid:pk>` son los conversores de
Django tal como los declara.

## `common` — fase 0: Cimientos y Plataforma

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET | `/api/v1/health/` | `HealthView` | `django-api/apps/common/health.py:44` |
| GET | `/api/v1/legal/condiciones/` | `CondicionesView` | `django-api/apps/common/legal_views.py:41` |
| POST | `/api/v1/legal/condiciones/aceptar/` | `AceptarCondicionesView` | `django-api/apps/common/legal_views.py:60` |
| GET | `/api/v1/legal/privacidad/` | `AvisoPrivacidadView` | `django-api/apps/common/legal_views.py:36` |
| GET | `/api/v1/platform/audit/` | `PlatformAuditView` | `django-api/apps/common/platform_views.py:433` |
| GET POST | `/api/v1/platform/clinics/` | `ClinicListCreateView` | `django-api/apps/common/platform_views.py:144` |
| GET PUT PATCH | `/api/v1/platform/clinics/<uuid:pk>/` | `ClinicDetailView` | `django-api/apps/common/platform_views.py:211` |
| GET POST PATCH | `/api/v1/platform/clinics/<uuid:pk>/admin/` | `ClinicAdminView` | `django-api/apps/common/platform_views.py:293` |
| POST | `/api/v1/platform/clinics/<uuid:pk>/admin/reset-password/` | `ClinicAdminResetPasswordView` | `django-api/apps/common/platform_views.py:383` |
| GET PATCH | `/api/v1/platform/clinics/<uuid:pk>/identidad/` | `ClinicIdentidadView` | `django-api/apps/common/platform_views.py:234` |
| GET PATCH | `/api/v1/platform/config/` | `PlatformConfigView` | `django-api/apps/common/platform_views.py:560` |
| POST | `/api/v1/platform/config/correo-de-prueba/` | `CorreoDePruebaView` | `django-api/apps/common/platform_views.py:576` |
| GET | `/api/v1/platform/funcionalidades/` | `FuncionalidadesView` | `django-api/apps/common/platform_views.py:507` |
| PATCH | `/api/v1/platform/funcionalidades/<str:clave>/` | `FuncionalidadPublicacionView` | `django-api/apps/common/platform_views.py:533` |
| GET | `/api/v1/platform/overview/` | `PlatformOverviewView` | `django-api/apps/common/platform_views.py:416` |
| GET | `/api/v1/platform/solicitudes/` | `SolicitudesPlataformaView` | `django-api/apps/common/solicitudes.py:209` |
| PUT PATCH | `/api/v1/platform/solicitudes/<uuid:pk>/` | `SolicitudPlataformaView` | `django-api/apps/common/solicitudes.py:219` |
| POST | `/api/v1/publico/solicitudes/` | `SolicitudPublicaView` | `django-api/apps/common/solicitudes.py:87` |
| POST | `/api/v1/publico/solicitudes/<uuid:pk>/confirmar/` | `ConfirmarSolicitudView` | `django-api/apps/common/solicitudes.py:126` |
| GET | `/api/v1/ready/` | `ReadyView` | `django-api/apps/common/health.py:55` |

## `accounts` — fase 1: Cuentas

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET | `/api/v1/audit-logs/` | `AuditLogListView` | `django-api/apps/accounts/views.py:453` |
| POST | `/api/v1/auth/change-password/` | `CambiarMiContrasenaView` | `django-api/apps/accounts/password_views.py:17` |
| POST | `/api/v1/auth/google/` | `GoogleLoginView` | `django-api/apps/accounts/google.py:83` |
| GET | `/api/v1/auth/google/config/` | `GoogleConfigView` | `django-api/apps/accounts/google.py:75` |
| POST | `/api/v1/auth/login/` | `StaffLoginView` | `django-api/apps/accounts/views.py:53` |
| GET | `/api/v1/auth/me/` | `MeView` | `django-api/apps/accounts/views.py:483` |
| GET PATCH | `/api/v1/auth/me/preferencias/` | `PreferenciasView` | `django-api/apps/accounts/views.py:548` |
| GET | `/api/v1/auth/otp/canal/` | `OTPCanalView` | `django-api/apps/accounts/views.py:59` |
| POST | `/api/v1/auth/otp/request/` | `OTPRequestView` | `django-api/apps/accounts/views.py:72` |
| POST | `/api/v1/auth/otp/verify/` | `OTPVerifyView` | `django-api/apps/accounts/views.py:209` |
| POST | `/api/v1/auth/recovery/confirm/` | `StaffRecoveryConfirmView` | `django-api/apps/accounts/views.py:307` |
| POST | `/api/v1/auth/recovery/request/` | `StaffRecoveryRequestView` | `django-api/apps/accounts/views.py:260` |
| POST | `/api/v1/me/device-tokens/` | `DeviceTokenRegisterView` | `django-api/apps/accounts/views.py:464` |
| GET POST | `/api/v1/users/` | `UserListCreateView` | `django-api/apps/accounts/views.py:348` |
| GET PUT PATCH | `/api/v1/users/<uuid:pk>/` | `UserDetailView` | `django-api/apps/accounts/views.py:434` |
| POST | `/api/v1/users/<uuid:pk>/invitar/` | `InvitarUsuarioView` | `django-api/apps/accounts/views.py:398` |
| GET | `/api/v1/users/funciones/` | `CatalogoFuncionesView` | `django-api/apps/accounts/views.py:518` |

## `configuration` — fase 2: Configuración y catálogos

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET POST | `/api/v1/config/agreements/` | `AgreementListCreateView` | `django-api/apps/configuration/views.py:59` |
| GET PUT PATCH DELETE | `/api/v1/config/agreements/<uuid:pk>/` | `AgreementDetailView` | `django-api/apps/configuration/views.py:76` |
| POST | `/api/v1/config/backup/` | `TenantBackupView` | `django-api/apps/configuration/views.py:376` |
| POST | `/api/v1/config/backup/decrypt/` | `TenantBackupDecryptView` | `django-api/apps/configuration/views.py:455` |
| GET | `/api/v1/config/backup/herramientas/` | `TenantBackupToolsView` | `django-api/apps/configuration/views.py:436` |
| GET PATCH | `/api/v1/config/branding/` | `ClinicBrandingView` | `django-api/apps/configuration/views.py:192` |
| GET PATCH DELETE | `/api/v1/config/document-appearance/` | `DocumentAppearanceView` | `django-api/apps/configuration/views.py:277` |
| GET PATCH | `/api/v1/config/modulos/` | `ModulosClinicaView` | `django-api/apps/configuration/views.py:538` |
| GET | `/api/v1/config/parameters/` | `SystemParameterListView` | `django-api/apps/configuration/views.py:172` |
| GET PUT PATCH | `/api/v1/config/parameters/<uuid:pk>/` | `SystemParameterDetailView` | `django-api/apps/configuration/views.py:182` |
| GET PUT | `/api/v1/config/price-matrix/` | `PriceMatrixView` | `django-api/apps/configuration/views.py:100` |
| GET POST | `/api/v1/config/tarifarios/` | `TariffListCreateView` | `django-api/apps/configuration/views.py:83` |
| GET PUT PATCH DELETE | `/api/v1/config/tarifarios/<uuid:pk>/` | `TariffDetailView` | `django-api/apps/configuration/views.py:93` |
| POST | `/api/v1/config/temas/previsualizar/` | `TemasPrevisualizarView` | `django-api/apps/configuration/views.py:250` |
| GET POST | `/api/v1/config/treatments/` | `TreatmentListCreateView` | `django-api/apps/configuration/views.py:38` |
| GET PUT PATCH DELETE | `/api/v1/config/treatments/<uuid:pk>/` | `TreatmentDetailView` | `django-api/apps/configuration/views.py:52` |

## `specialties` — fase 2: Configuración y catálogos

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET POST | `/api/v1/patients/<uuid:pk>/specialty-forms/` | `PatientSpecialtyFormListCreateView` | `django-api/apps/specialties/views.py:70` |
| GET POST | `/api/v1/specialties/` | `SpecialtyListCreateView` | `django-api/apps/specialties/views.py:23` |
| GET PUT PATCH DELETE | `/api/v1/specialties/<uuid:pk>/` | `SpecialtyDetailView` | `django-api/apps/specialties/views.py:38` |
| GET | `/api/v1/specialties/<uuid:pk>/form-template/` | `SpecialtyFormTemplateView` | `django-api/apps/specialties/views.py:52` |
| GET PUT PATCH | `/api/v1/specialty-forms/<uuid:pk>/` | `SpecialtyFormDetailView` | `django-api/apps/specialties/views.py:95` |

## `patients` — fase 3: Pacientes

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET POST | `/api/v1/patients/` | `PatientListCreateView` | `django-api/apps/patients/views.py:22` |
| GET PUT PATCH DELETE | `/api/v1/patients/<uuid:pk>/` | `PatientDetailView` | `django-api/apps/patients/views.py:83` |
| GET POST DELETE | `/api/v1/patients/<uuid:pk>/app-access/` | `AccesoAppView` | `django-api/apps/patients/acceso_app.py:85` |
| GET POST | `/api/v1/patients/<uuid:pk>/aviso-privacidad/` | `AvisoPrivacidadView` | `django-api/apps/patients/views.py:239` |
| GET POST | `/api/v1/patients/<uuid:pk>/documents/` | `PatientDocumentListCreateView` | `django-api/apps/patients/views.py:129` |
| DELETE | `/api/v1/patients/<uuid:pk>/documents/<int:doc_id>/` | `PatientDocumentDeleteView` | `django-api/apps/patients/views.py:188` |
| GET | `/api/v1/patients/<uuid:pk>/documents/<int:doc_id>/file/` | `PatientDocumentFileView` | `django-api/apps/patients/views.py:149` |
| GET PUT PATCH | `/api/v1/patients/<uuid:pk>/medical-background/` | `MedicalBackgroundView` | `django-api/apps/patients/views.py:100` |
| GET | `/api/v1/patients/<uuid:pk>/treatment-history/` | `PatientTreatmentHistoryView` | `django-api/apps/patients/views.py:212` |

## `agenda` — fase 4: Agenda

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET | `/api/v1/agenda/view/` | `AgendaViewList` | `django-api/apps/agenda/views.py:220` |
| GET POST | `/api/v1/appointments/` | `AppointmentListCreateView` | `django-api/apps/agenda/views.py:44` |
| GET PUT PATCH | `/api/v1/appointments/<uuid:pk>/` | `AppointmentDetailView` | `django-api/apps/agenda/views.py:106` |
| POST | `/api/v1/appointments/<uuid:pk>/cancel/` | `AppointmentCancelView` | `django-api/apps/agenda/views.py:139` |
| POST | `/api/v1/appointments/<uuid:pk>/checkin/` | `AppointmentCheckinView` | `django-api/apps/agenda/views.py:178` |
| POST | `/api/v1/appointments/<uuid:pk>/checkout/` | `AppointmentCheckoutView` | `django-api/apps/agenda/views.py:207` |
| POST | `/api/v1/appointments/<uuid:pk>/confirm/` | `AppointmentConfirmView` | `django-api/apps/agenda/views.py:129` |
| POST | `/api/v1/appointments/<uuid:pk>/reschedule/` | `AppointmentRescheduleView` | `django-api/apps/agenda/views.py:149` |
| POST | `/api/v1/appointments/<uuid:pk>/start/` | `AppointmentStartView` | `django-api/apps/agenda/views.py:263` |
| GET | `/api/v1/appointments/waiting/` | `WaitingPatientsView` | `django-api/apps/agenda/views.py:363` |
| ? | `/api/v1/calendar/<uuid:token>.ics` | `doctor_calendar_feed` | `django-api/apps/agenda/calendar_feed.py:34` |
| GET | `/api/v1/doctors/` | `DoctorListView` | `django-api/apps/agenda/views.py:27` |
| GET | `/api/v1/doctors/<uuid:pk>/calendar-url/` | `DoctorCalendarURLView` | `django-api/apps/agenda/views.py:292` |
| GET POST | `/api/v1/doctors/me/signature/` | `DoctorMySignatureView` | `django-api/apps/agenda/views.py:325` |

## `clinical` — fase 5: Historia clínica

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET | `/api/v1/cie10/` | `Cie10SearchView` | `django-api/apps/clinical/form033_views.py:101` |
| GET | `/api/v1/clinical/follow-ups/` | `FollowUpAlertsView` | `django-api/apps/clinical/sprint22_views.py:208` |
| DELETE | `/api/v1/clinical/plan-template-items/<uuid:pk>/` | `TemplateItemDeleteView` | `django-api/apps/clinical/sprint22_views.py:339` |
| GET POST | `/api/v1/clinical/plan-templates/` | `TemplateListCreateView` | `django-api/apps/clinical/sprint22_views.py:33` |
| POST | `/api/v1/clinical/plan-templates/<uuid:pk>/items/` | `TemplateItemCreateView` | `django-api/apps/clinical/sprint22_views.py:312` |
| GET POST | `/api/v1/consent-templates/` | `ConsentTemplateListCreateView` | `django-api/apps/clinical/views.py:553` |
| GET PUT PATCH DELETE | `/api/v1/consent-templates/<uuid:pk>/` | `ConsentTemplateDetailView` | `django-api/apps/clinical/views.py:574` |
| GET PUT PATCH | `/api/v1/consents/<uuid:pk>/detail/` | `ConsentDetailView` | `django-api/apps/clinical/views.py:588` |
| GET | `/api/v1/consents/<uuid:pk>/pdf/` | `ConsentPDFView` | `django-api/apps/clinical/form033_views.py:438` |
| POST | `/api/v1/consents/<uuid:pk>/sign/` | `ConsentSignView` | `django-api/apps/clinical/views.py:339` |
| GET PUT PATCH DELETE | `/api/v1/evolutions/<uuid:pk>/` | `EvolutionDetailView` | `django-api/apps/clinical/views.py:502` |
| GET | `/api/v1/evolutions/<uuid:pk>/prescription-pdf/` | `PrescriptionPDFView` | `django-api/apps/clinical/sprint22_views.py:239` |
| PATCH | `/api/v1/exam-requests/<uuid:pk>/report/` | `ExamRequestReportView` | `django-api/apps/clinical/form033_views.py:164` |
| GET PUT PATCH | `/api/v1/form033/<uuid:pk>/` | `Form033DetailView` | `django-api/apps/clinical/form033_views.py:86` |
| GET | `/api/v1/odontogram-states/` | `OdontogramStateListView` | `django-api/apps/clinical/views.py:208` |
| GET | `/api/v1/patients/<uuid:pk>/alertas/` | `AlertasPacienteView` | `django-api/apps/clinical/alertas_views.py:38` |
| POST | `/api/v1/patients/<uuid:pk>/alertas/revisar/` | `RevisarRecetaView` | `django-api/apps/clinical/alertas_views.py:45` |
| POST | `/api/v1/patients/<uuid:pk>/apply-plan-template/` | `ApplyTemplateView` | `django-api/apps/clinical/sprint22_views.py:72` |
| GET POST | `/api/v1/patients/<uuid:pk>/certificado-atencion/` | `CertificadoAtencionView` | `django-api/apps/clinical/certificado_views.py:52` |
| GET PUT PATCH | `/api/v1/patients/<uuid:pk>/clinical-record/` | `ClinicalRecordView` | `django-api/apps/clinical/views.py:53` |
| GET | `/api/v1/patients/<uuid:pk>/clinical-record/export-pdf/` | `ClinicalHistoryExportView` | `django-api/apps/clinical/views.py:428` |
| GET POST | `/api/v1/patients/<uuid:pk>/consents/` | `InformedConsentListCreateView` | `django-api/apps/clinical/views.py:311` |
| GET | `/api/v1/patients/<uuid:pk>/cpo-ceo/` | `CpoCeoIndexView` | `django-api/apps/clinical/form033_views.py:192` |
| GET POST | `/api/v1/patients/<uuid:pk>/diagnoses/` | `DiagnosisListCreateView` | `django-api/apps/clinical/views.py:89` |
| GET POST | `/api/v1/patients/<uuid:pk>/evolutions/` | `EvolutionListCreateView` | `django-api/apps/clinical/views.py:67` |
| GET POST | `/api/v1/patients/<uuid:pk>/exam-requests/` | `ExamRequestListCreateView` | `django-api/apps/clinical/form033_views.py:144` |
| GET | `/api/v1/patients/<uuid:pk>/exam-requests/<uuid:exam_id>/pdf/` | `ExamRequestPDFView` | `django-api/apps/clinical/form033_views.py:364` |
| GET POST | `/api/v1/patients/<uuid:pk>/form033/` | `Form033ListCreateView` | `django-api/apps/clinical/form033_views.py:62` |
| GET | `/api/v1/patients/<uuid:pk>/form033/export-pdf/` | `Form033PDFExportView` | `django-api/apps/clinical/form033_views.py:250` |
| GET | `/api/v1/patients/<uuid:pk>/form033/export-xlsx/` | `Form033XlsxExportView` | `django-api/apps/clinical/form033_views.py:326` |
| GET | `/api/v1/patients/<uuid:pk>/odontogram/current/` | `CurrentOdontogramView` | `django-api/apps/clinical/views.py:253` |
| GET POST | `/api/v1/patients/<uuid:pk>/periodontal-exams/` | `PeriodontalExamListCreateView` | `django-api/apps/clinical/views.py:623` |
| GET POST | `/api/v1/patients/<uuid:pk>/radiographs/` | `RadiographPhotoListCreateView` | `django-api/apps/clinical/views.py:288` |
| GET POST | `/api/v1/patients/<uuid:pk>/tooth-records/` | `ToothRecordListCreateView` | `django-api/apps/clinical/views.py:226` |
| GET POST | `/api/v1/patients/<uuid:pk>/treatment-plans/` | `TreatmentPlanListCreateView` | `django-api/apps/clinical/views.py:106` |
| GET PUT PATCH | `/api/v1/periodontal-teeth/<uuid:pk>/` | `PeriodontalToothUpdateView` | `django-api/apps/clinical/views.py:665` |
| DELETE | `/api/v1/tooth-records/<uuid:pk>/` | `ToothRecordDeleteView` | `django-api/apps/clinical/views.py:468` |
| PUT PATCH | `/api/v1/treatment-plan-items/<uuid:pk>/` | `TreatmentPlanItemUpdateView` | `django-api/apps/clinical/views.py:156` |
| GET PUT PATCH | `/api/v1/treatment-plans/<uuid:pk>/` | `TreatmentPlanDetailView` | `django-api/apps/clinical/views.py:123` |
| POST | `/api/v1/treatment-plans/<uuid:pk>/generate-budget/` | `PlanToBudgetView` | `django-api/apps/clinical/sprint22_views.py:143` |
| POST | `/api/v1/treatment-plans/<uuid:pk>/items/` | `TreatmentPlanItemCreateView` | `django-api/apps/clinical/views.py:135` |

## `billing` — fase 7: Cobros

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET PUT PATCH | `/api/v1/budgets/<uuid:pk>/` | `BudgetDetailView` | `django-api/apps/billing/views.py:57` |
| POST | `/api/v1/budgets/<uuid:pk>/approve/` | `BudgetApproveView` | `django-api/apps/billing/views.py:81` |
| POST | `/api/v1/budgets/<uuid:pk>/generate-payment-plan/` | `GeneratePaymentPlanView` | `django-api/apps/billing/views.py:93` |
| POST | `/api/v1/budgets/<uuid:pk>/items/` | `BudgetItemCreateView` | `django-api/apps/billing/views.py:67` |
| GET POST | `/api/v1/doctors/<uuid:pk>/fees/` | `DoctorFeeListCreateView` | `django-api/apps/billing/views.py:244` |
| POST | `/api/v1/installments/<uuid:pk>/pay/` | `PayInstallmentView` | `django-api/apps/billing/views.py:172` |
| GET | `/api/v1/patients/<uuid:pk>/account-statement/` | `PatientAccountStatementView` | `django-api/apps/billing/views.py:209` |
| GET POST | `/api/v1/patients/<uuid:pk>/budgets/` | `BudgetListCreateView` | `django-api/apps/billing/views.py:37` |
| GET POST | `/api/v1/patients/<uuid:pk>/payments/` | `PatientPaymentListCreateView` | `django-api/apps/billing/views.py:564` |
| GET | `/api/v1/patients/birthdays/` | `BirthdaysView` | `django-api/apps/billing/views.py:594` |
| GET | `/api/v1/payment-plans/<uuid:pk>/installments/` | `PaymentPlanInstallmentsView` | `django-api/apps/billing/views.py:146` |
| GET | `/api/v1/reports/appointments-summary/` | `AppointmentsSummaryReportView` | `django-api/apps/billing/views.py:524` |
| GET | `/api/v1/reports/delinquency/` | `DelinquencyReportView` | `django-api/apps/billing/views.py:341` |
| GET | `/api/v1/reports/financial/` | `FinancialReportView` | `django-api/apps/billing/views.py:260` |
| GET | `/api/v1/reports/inventory/` | `InventoryReportView` | `django-api/apps/billing/views.py:478` |
| GET | `/api/v1/reports/new-patients/` | `NewPatientsReportView` | `django-api/apps/billing/views.py:426` |
| GET | `/api/v1/reports/production-by-doctor/` | `ProductionByDoctorReportView` | `django-api/apps/billing/views.py:392` |

## `inventory` — fase 8: Inventario y reportes

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET | `/api/v1/inventory/alerts/expiring/` | `ExpiringBatchAlertView` | `django-api/apps/inventory/views.py:115` |
| GET | `/api/v1/inventory/alerts/low-stock/` | `LowStockAlertView` | `django-api/apps/inventory/views.py:94` |
| GET | `/api/v1/inventory/movements/` | `InventoryMovementListView` | `django-api/apps/inventory/views.py:148` |
| GET POST | `/api/v1/products/` | `ProductListCreateView` | `django-api/apps/inventory/views.py:24` |
| GET PUT PATCH | `/api/v1/products/<uuid:pk>/` | `ProductDetailView` | `django-api/apps/inventory/views.py:42` |
| GET POST | `/api/v1/products/<uuid:pk>/batches/` | `BatchListCreateView` | `django-api/apps/inventory/views.py:50` |

## `finanzas` — fase 9: Finanzas

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET POST | `/api/v1/finanzas/caja/` | `CajaView` | `django-api/apps/finanzas/views.py:286` |
| GET POST | `/api/v1/finanzas/categorias/` | `CategoriasView` | `django-api/apps/finanzas/views.py:117` |
| PUT PATCH | `/api/v1/finanzas/categorias/<uuid:pk>/` | `CategoriaView` | `django-api/apps/finanzas/views.py:129` |
| GET PATCH | `/api/v1/finanzas/cobros/` | `CobrosView` | `django-api/apps/finanzas/views.py:748` |
| GET PATCH | `/api/v1/finanzas/cobros/<uuid:pk>/` | `CobrosView` | `django-api/apps/finanzas/views.py:748` |
| GET | `/api/v1/finanzas/cobros/<uuid:pk>/comprobante/` | `ComprobanteCobroView` | `django-api/apps/finanzas/views.py:721` |
| GET PATCH | `/api/v1/finanzas/configuracion/` | `ConfiguracionView` | `django-api/apps/finanzas/views.py:100` |
| GET POST | `/api/v1/finanzas/decimos/` | `DecimosView` | `django-api/apps/finanzas/views.py:676` |
| GET POST | `/api/v1/finanzas/egresos/` | `EgresosView` | `django-api/apps/finanzas/views.py:139` |
| GET PUT PATCH | `/api/v1/finanzas/egresos/<uuid:pk>/` | `EgresoView` | `django-api/apps/finanzas/views.py:202` |
| POST | `/api/v1/finanzas/egresos/<uuid:pk>/anular/` | `AnularEgresoView` | `django-api/apps/finanzas/views.py:230` |
| GET | `/api/v1/finanzas/egresos/<uuid:pk>/comprobante/` | `ComprobanteEgresoView` | `django-api/apps/finanzas/views.py:272` |
| POST | `/api/v1/finanzas/egresos/<uuid:pk>/pagar/` | `PagarEgresoView` | `django-api/apps/finanzas/views.py:252` |
| GET | `/api/v1/finanzas/equipo/` | `EquipoView` | `django-api/apps/finanzas/views.py:332` |
| PATCH | `/api/v1/finanzas/equipo/<uuid:pk>/` | `PerfilView` | `django-api/apps/finanzas/views.py:347` |
| GET | `/api/v1/finanzas/exportar/` | `ExportarView` | `django-api/apps/finanzas/views.py:795` |
| GET POST | `/api/v1/finanzas/liquidaciones/` | `LiquidacionesView` | `django-api/apps/finanzas/views.py:387` |
| POST | `/api/v1/finanzas/liquidaciones/<uuid:pk>/anular/` | `AnularLiquidacionView` | `django-api/apps/finanzas/views.py:443` |
| POST | `/api/v1/finanzas/liquidaciones/<uuid:pk>/pagar/` | `PagarLiquidacionView` | `django-api/apps/finanzas/views.py:418` |
| GET | `/api/v1/finanzas/liquidaciones/<uuid:pk>/pdf/` | `LiquidacionPdfView` | `django-api/apps/finanzas/views.py:462` |
| GET | `/api/v1/finanzas/liquidaciones/previa/` | `LiquidacionPreviaView` | `django-api/apps/finanzas/views.py:376` |
| GET POST | `/api/v1/finanzas/obligaciones/` | `ObligacionesView` | `django-api/apps/finanzas/views.py:638` |
| GET | `/api/v1/finanzas/resumen/` | `ResumenView` | `django-api/apps/finanzas/views.py:79` |
| GET POST | `/api/v1/finanzas/roles/` | `RolesView` | `django-api/apps/finanzas/views.py:506` |
| POST | `/api/v1/finanzas/roles/<uuid:pk>/anular/` | `AnularRolView` | `django-api/apps/finanzas/views.py:601` |
| POST | `/api/v1/finanzas/roles/<uuid:pk>/pagar/` | `PagarRolView` | `django-api/apps/finanzas/views.py:575` |
| GET | `/api/v1/finanzas/roles/<uuid:pk>/pdf/` | `RolPdfView` | `django-api/apps/finanzas/views.py:617` |
| POST | `/api/v1/finanzas/roles/generar/` | `GenerarRolesView` | `django-api/apps/finanzas/views.py:548` |
| GET | `/api/v1/finanzas/roles/previa/` | `RolPreviaView` | `django-api/apps/finanzas/views.py:496` |

## `app_paciente` — fase 10: App del paciente

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET | `/api/v1/app/citas/` | `MisCitasView` | `django-api/apps/app_paciente/views.py:101` |
| POST | `/api/v1/app/citas/<uuid:pk>/confirmar/` | `ConfirmarCitaView` | `django-api/apps/app_paciente/views.py:335` |
| GET | `/api/v1/app/clinica/` | `MiClinicaView` | `django-api/apps/app_paciente/views.py:231` |
| GET | `/api/v1/app/cuenta/` | `MiCuentaView` | `django-api/apps/app_paciente/views.py:38` |
| GET | `/api/v1/app/indicaciones/` | `MisIndicacionesView` | `django-api/apps/app_paciente/views.py:150` |
| GET | `/api/v1/app/logros/` | `MisLogrosView` | `django-api/apps/app_paciente/views.py:179` |
| GET | `/api/v1/app/me/` | `MiPerfilView` | `django-api/apps/app_paciente/views.py:65` |
| GET POST | `/api/v1/app/mensajes/` | `MisMensajesView` | `django-api/apps/app_paciente/contacto.py:141` |
| GET | `/api/v1/app/saldo/` | `MiSaldoView` | `django-api/apps/app_paciente/views.py:121` |
| GET POST | `/api/v1/app/solicitudes-cita/` | `MisSolicitudesCitaView` | `django-api/apps/app_paciente/contacto.py:97` |
| GET | `/api/v1/bandeja-app/mensajes/` | `MensajesBandejaView` | `django-api/apps/app_paciente/contacto.py:284` |
| POST | `/api/v1/bandeja-app/mensajes/<uuid:pk>/responder/` | `ResponderMensajeView` | `django-api/apps/app_paciente/contacto.py:296` |
| GET | `/api/v1/bandeja-app/resumen/` | `ResumenBandejaView` | `django-api/apps/app_paciente/contacto.py:168` |
| GET | `/api/v1/bandeja-app/solicitudes/` | `SolicitudesBandejaView` | `django-api/apps/app_paciente/contacto.py:182` |
| POST | `/api/v1/bandeja-app/solicitudes/<uuid:pk>/agendar/` | `AgendarSolicitudView` | `django-api/apps/app_paciente/contacto.py:205` |
| POST | `/api/v1/bandeja-app/solicitudes/<uuid:pk>/rechazar/` | `RechazarSolicitudView` | `django-api/apps/app_paciente/contacto.py:264` |

## `logros` — fase 10: App del paciente

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET POST | `/api/v1/logros/` | `LogroListCreateView` | `django-api/apps/logros/views.py:28` |
| GET PUT PATCH DELETE | `/api/v1/logros/<uuid:pk>/` | `LogroDetailView` | `django-api/apps/logros/views.py:42` |
| POST | `/api/v1/logros/evaluar/` | `EvaluarLogrosView` | `django-api/apps/logros/views.py:105` |
| POST | `/api/v1/logros/otorgar/` | `OtorgarLogroView` | `django-api/apps/logros/views.py:60` |
| GET | `/api/v1/patients/<uuid:pk>/logros/` | `LogrosDelPacienteView` | `django-api/apps/logros/views.py:92` |

## `whatsapp` — fase 13: WhatsApp

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| GET POST | `/api/v1/config/whatsapp-templates/` | `WhatsAppTemplateListCreateView` | `django-api/apps/whatsapp/views.py:16` |
| GET PATCH | `/api/v1/config/whatsapp/` | `ConfiguracionWhatsAppView` | `django-api/apps/whatsapp/config_views.py:42` |
| GET POST | `/api/v1/patients/<uuid:pk>/whatsapp-optin/` | `WhatsAppOptInListCreateView` | `django-api/apps/whatsapp/views.py:29` |
| POST | `/api/v1/whatsapp-optin/<uuid:pk>/revoke/` | `WhatsAppOptInRevokeView` | `django-api/apps/whatsapp/views.py:66` |

## `terceros` — fase —: Terceros

| Métodos | Ruta | Vista | Archivo |
|---|---|---|---|
| POST | `/api/v1/auth/token/refresh/` | `TokenRefreshView` | `.venv/lib/python3.12/site-packages/rest_framework_simplejwt/views.py:65` |
| GET | `/api/v1/schema/` | `SpectacularAPIView` | `.venv/lib/python3.12/site-packages/drf_spectacular/views.py:44` |
| GET | `/api/v1/schema/swagger-ui/` | `SpectacularSwaggerView` | `.venv/lib/python3.12/site-packages/drf_spectacular/views.py:122` |

