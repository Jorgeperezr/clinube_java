#!/usr/bin/env python3
"""
Siembra datos de demostración en la clínica demo, CON EL ORM DE DJANGO y
con fechas relativas a hoy, para lo que pide el escritorio del panel:
pacientes, citas de hoy y sala de espera, cumpleaños, cobros, cuotas
vencidas, seguimientos clínicos, inventario y la bandeja de la app.

Se siembra con Django —no con SQL a mano— para que los datos sean
exactamente los que Django crearía, con sus valores por defecto. El
comparador lo ejecuta sobre la copia de Django ANTES de copiarla para
Kotlin, así que los dos servidores ven los mismos datos.

Nunca contra la base de referencia ni contra nada con datos reales: es una
clínica de demostración con pacientes inventados.

    BASE=clinube_cmp_django CLINUBE=../clinube python3 scripts/sembrar-escritorio.py
"""
import os
import sys
import uuid
from datetime import datetime, time, timedelta
from decimal import Decimal

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLINUBE = os.path.abspath(os.environ.get("CLINUBE", os.path.join(RAIZ, "..", "clinube")))
BASE = os.environ.get("BASE")
if not BASE or BASE == "clinube_ref":
    sys.exit("Indica BASE=<copia desechable>. Nunca la base de referencia.")
sys.path.insert(0, os.path.join(CLINUBE, "django-api"))
for clave, valor in {"POSTGRES_HOST": "localhost", "POSTGRES_DB": BASE, "POSTGRES_USER": "clinica",
                     "POSTGRES_PASSWORD": "clinica", "DJANGO_DEBUG": "True", "DJANGO_LOG_LEVEL": "WARNING"}.items():
    os.environ[clave] = valor
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

import django  # noqa: E402
django.setup()
from django.db import transaction  # noqa: E402
from django.utils import timezone  # noqa: E402

from apps.accounts.models import User  # noqa: E402
from apps.agenda.models import Appointment, Doctor  # noqa: E402
from apps.app_paciente.models import MensajeConsultorio, SolicitudCita  # noqa: E402
from apps.billing.models import Budget, Installment, Payment, PaymentPlan  # noqa: E402
from apps.clinical.models import (  # noqa: E402
    Diagnosis, Evolution, Form033Record, OdontogramState, ToothRecord, TreatmentPlan, TreatmentPlanItem,
    TreatmentPlanTemplate, TreatmentPlanTemplateItem,
)
from apps.configuration.models import Tariff, Treatment, TreatmentInventoryItem  # noqa: E402
from apps.specialties.models import Specialty  # noqa: E402
from apps.common.models import Tenant  # noqa: E402
from apps.inventory.models import Batch, Product  # noqa: E402
from apps.patients.models import MedicalBackground, Patient  # noqa: E402

CLAVE = "OdontoDemo2026"

# Identificadores FIJOS para lo que el comparador necesita nombrar
# (`/patients/<id>/`, `/appointments/<id>/confirm/`…): uuid5 de un nombre,
# así son los mismos en cada siembra y se pueden escribir en peticiones.json.
ESPACIO = uuid.UUID("6f1c2a5e-0000-4000-8000-c1b0be000000")


def fijo(nombre):
    return uuid.uuid5(ESPACIO, nombre)


@transaction.atomic
def sembrar():
    demo = Tenant.objects.get(name="Clínica Demo")
    if Patient.objects.filter(tenant=demo).exists():
        sys.exit("La clínica demo ya tiene pacientes: usa una copia recién hecha.")
    admin = User.objects.get(email="admin@clinube.local")
    zona = timezone.get_current_timezone()
    hoy = timezone.localdate()
    ahora = timezone.now()

    def a_las(dia, hora, minuto=0):
        return timezone.make_aware(datetime.combine(dia, time(hora, minuto)), zona)

    # ── Personal ─────────────────────────────────────────────────────
    def usuario(correo, rol, nombre):
        u = User(id=fijo(correo), email=correo, role=rol, tenant=demo, full_name=nombre)
        u.set_password(CLAVE)
        u.save()
        return u
    doctora = Doctor.objects.create(id=fijo("doctor-doctora"), calendar_token=fijo("calendario-doctora"), tenant=demo,
                                    user=usuario("doctora@clinube.local", "doctor", "Dra. Elena Mora"))
    # Sin nombre completo: el nombre del doctor cae en su correo.
    doctor2 = Doctor.objects.create(id=fijo("doctor-doctor2"), calendar_token=fijo("calendario-doctor2"), tenant=demo,
                                    user=usuario("doctor2@clinube.local", "doctor", ""),
                                    license_number="MSP-0002", signature_image="firmas/doctor2.png")
    usuario("recepcion@clinube.local", "reception", "Laura Paz")
    usuario("auxiliar@clinube.local", "auxiliary", "Carlos Auxiliar")

    # ── Convenios: el formulario del paciente los pide para su desplegable ──
    from apps.configuration.models import Agreement
    seguro = Agreement.objects.create(id=fijo("convenio-seguro"), tenant=demo, name="Seguro Andino",
                                      discount_percentage=Decimal("15.00"))
    Agreement.objects.create(id=fijo("convenio-empresa"), tenant=demo, name="Empresa Austral",
                             discount_percentage=Decimal("10.50"))
    Agreement.objects.create(id=fijo("convenio-retirado"), tenant=demo, name="Antiguo convenio",
                             discount_percentage=Decimal("5"), is_active=False)

    # ── Pacientes: más de 20, para que la lista pagine ───────────────
    nombres = [
        ("Ana", "López"), ("ana", "lópez"), ("Ángel", "Álvarez"), ("José", "Pérez"), ("Zoe", "Ñúñez"),
        ("María", "García"), ("Luis", "Mora"), ("Carmen", "Vega"), ("Pedro", "Ruiz"), ("Lucía", "Torres"),
        ("Jorge", "Castro"), ("Elena", "Ortiz"), ("Diego", "Silva"), ("Rosa", "Díaz"), ("Pablo", "Rojas"),
        ("Sofía", "Herrera"), ("Mateo", "Flores"), ("Valeria", "Romero"), ("Andrés", "Medina"),
        ("Camila", "Aguilar"), ("Tomás", "Navarro"), ("Isabel", "Cruz"), ("Hugo", "Reyes"), ("Nora", "Paredes"),
        ("Iván", "50% Descuento_"),
    ]
    pacientes = []
    for i, (nombre, apellido) in enumerate(nombres):
        pacientes.append(Patient.objects.create(
            id=fijo(f"paciente-{i}"), tenant=demo, first_name=nombre, last_name=apellido, national_id=f"17{i:08d}",
            phone=f"09{i:08d}" if i % 3 else "", email=f"paciente{i}@correo.ec" if i % 2 else "",
            agreement=seguro if i in (0, 5) else None,
        ))
    # Cumpleaños: hoy, dentro de 3 días, 29 de febrero, 1 de enero, y alguien sin fecha.
    en_3 = hoy + timedelta(days=3)
    for p, nacimiento in zip(pacientes, [
        datetime(1988, 1, 1).date().replace(month=hoy.month, day=min(hoy.day, 28)) if (hoy.month, hoy.day) == (2, 29)
        else datetime(1988, hoy.month, hoy.day).date(),
        datetime(1984, en_3.month, en_3.day).date() if (en_3.month, en_3.day) != (2, 29) else datetime(1984, 2, 29).date(),
        datetime(2000, 2, 29).date(),
        datetime(1980, 1, 1).date(),
    ]):
        p.birth_date = nacimiento
        p.save(update_fields=["birth_date"])
    Patient.objects.create(id=fijo("paciente-baja"), tenant=demo, first_name="Dado", last_name="De Baja", national_id="1799999999",
                           is_active=False, birth_date=datetime(1990, hoy.month, min(hoy.day, 28)).date())

    # ── Antecedentes y formularios 033, para las alertas clínicas ────
    # paciente-4: alertas de los tres campos, con negaciones y tildes.
    MedicalBackground.objects.create(
        patient=pacientes[4], allergies="Alergia a la Penicilina (urticaria). Niega alergia al látex",
        medications="Sintrom 4 mg; Ácido alendrónico semanal", conditions="Hipertensión controlada, diabetes tipo 2",
    )
    # paciente-9: solo negaciones, y embarazo declarado como «no».
    MedicalBackground.objects.create(
        patient=pacientes[9], allergies="Niega alergias", medications="No toma anticoagulantes",
        conditions="Sin antecedentes", is_pregnant=False,
    )
    # paciente-6: tres formularios; manda el de fecha más reciente y, con la
    # misma fecha, el creado después.
    Form033Record.objects.create(id=fijo("form033-1"), tenant=demo, patient=pacientes[6], date=hoy - timedelta(days=30),
                                 antecedentes_personales={"Diabetes": {"checked": True, "detail": ""}})
    Form033Record.objects.create(id=fijo("form033-2"), tenant=demo, patient=pacientes[6], date=hoy - timedelta(days=10),
                                 antecedentes_personales={"Alergia antibiótico": {"checked": True, "detail": "no recuerda cuál"}})
    Form033Record.objects.create(id=fijo("form033-3"), tenant=demo, patient=pacientes[6], date=hoy - timedelta(days=10),
                                 embarazada=True, temperatura=Decimal("36.5"), pulso=72, presion_arterial="120/80",
                                 indicadores_salud_bucal={"ihos": 1.5, "placa": [0, 1, 2.0]},
                                 examen_estomatognatico={"labios": "normal", "x": 1e-5, "zz": None},
                                 professional_snapshot={"full_name": "Dra. Elena Mora", "license_number": "MSP-123"},
                                 antecedentes_personales={
                                     "Alergia antibiótico": {"checked": True, "detail": "Amoxicilina, urticaria"},
                                     "Otro": {"checked": "sí", "detail": "Toma Xarelto. Zoledronato por osteoporosis"},
                                     "Hemorragias": {"checked": 0, "detail": "hemofilia"},
                                     "Enf. cardíaca": {"checked": [1]},
                                 })
    # paciente-9 también tiene un formulario, con la sección D vacía.
    Form033Record.objects.create(id=fijo("form033-4"), tenant=demo, patient=pacientes[9], date=hoy, antecedentes_personales={},
                                 professional_snapshot={"full_name": None, "license_number": 0})

    # ── Diagnósticos: 22 de paciente-6, para que la lista tenga dos páginas ──
    codigos = ["K02.1", "K04.0", "K05", "K02.0", "K08.1", ""]
    for i in range(22):
        Diagnosis.objects.create(tenant=demo, patient=pacientes[6], doctor=doctora if i % 3 else None,
                                 tooth_fdi_code=["", "16", "26", "36"][i % 4], code=codigos[i % len(codigos)],
                                 description=f"Diagnóstico {i}", diagnosis_kind="def" if i % 2 else "pre",
                                 date=hoy - timedelta(days=i * 3))

    # ── Odontograma de paciente-6 ────────────────────────────────────
    # Los 22 estados de la clínica demo vienen de la base de referencia
    # (los siembra el `bootstrap` de Django). Uno inactivo, para ver que no
    # sale en el catálogo pero se puede registrar.
    estado = {e.code: e for e in OdontogramState.objects.filter(tenant=demo)}
    OdontogramState.objects.create(id=fijo("estado-obsoleto"), tenant=demo, code="OBSOLETO", label="Obsoleto",
                                   color="#999999", order=99, is_active=False)
    n_diente = iter(range(100))

    def diente(pieza, codigo, dias, superficie="whole", **extra):
        return ToothRecord.objects.create(id=fijo(f"diente-{next(n_diente)}"), tenant=demo, patient=pacientes[6],
                                          tooth_fdi_code=pieza, surface=superficie, state=estado[codigo],
                                          date=hoy - timedelta(days=dias), **extra)

    diente("16", "CARIES", 20)
    diente("16", "OBTURADO", 10, "occlusal", doctor=doctora, notes="Resina")
    diente("26", "INDICADA_EXTRACCION", 15)
    diente("36", "CORONA", 12, mobility=2, recession=1)
    diente("46", "SANO", 30)
    diente("55", "CARIES", 5)
    diente("64", "PERDIDA_CARIES", 5)
    diente("75", "OBTURADO", 5)
    diente("11", "FRACTURA", 3, "vestibular")
    # El último CREADO es SANO, pero el de fecha más reciente es CARIES: el
    # odontograma actual pinta SANO y el CPO cuenta una caries.
    diente("21", "CARIES", 30)
    diente("21", "SANO", 40)
    for _ in range(11):
        diente("48", "SANO", 50)

    # ── Citas: hoy en varios estados, y los bordes del día ───────────
    numero = iter(range(1000))

    def cita(p, doctor, inicio, estado="confirmed", llegada=None, atencion=None, notas=""):
        return Appointment.objects.create(
            id=fijo(f"cita-{next(numero)}"), tenant=demo, patient=p, doctor=doctor, scheduled_start=inicio,
            scheduled_end=inicio + timedelta(minutes=30), status=estado, notes=notas,
            checkin_at=llegada, attention_started_at=atencion, created_by=admin)
    cita(pacientes[0], doctora, a_las(hoy, 8), llegada=ahora - timedelta(minutes=25, seconds=30), notas="Control")
    cita(pacientes[1], doctora, a_las(hoy, 9, 30), estado="cancelled", llegada=ahora - timedelta(minutes=50))
    cita(pacientes[2], doctora, a_las(hoy, 11), estado="pending")
    cita(pacientes[3], doctora, a_las(hoy, 13), llegada=ahora - timedelta(minutes=70),
         atencion=ahora - timedelta(minutes=10))
    cita(pacientes[4], doctor2, a_las(hoy, 15), llegada=ahora - timedelta(minutes=5, microseconds=123456))
    # 23:30 en Guayaquil es el día SIGUIENTE en UTC: tiene que salir hoy.
    cita(pacientes[5], doctor2, a_las(hoy, 23, 30), estado="pending", llegada=ahora - timedelta(minutes=1))
    cita(pacientes[6], doctora, a_las(hoy - timedelta(days=1), 23, 59), estado="completed")
    cita(pacientes[7], doctora, a_las(hoy + timedelta(days=1), 0, 1), estado="pending")
    for semanas in (1, 2):
        cita(pacientes[0], doctora, a_las(hoy + timedelta(weeks=semanas), 10))
    for dias in (10, 40, 400):
        cita(pacientes[1], doctor2, a_las(hoy - timedelta(days=dias), 9), estado="completed")

    # ── Cobros del mes y del anterior ────────────────────────────────
    primero = hoy.replace(day=1)
    for p, monto, metodo, fecha in [
        (pacientes[0], "150.50", "cash", hoy), (pacientes[1], "40.00", "transfer", primero),
        (pacientes[2], "25.25", "card", hoy), (pacientes[3], "10.10", "cash", primero),
        (pacientes[4], "999.99", "card", primero - timedelta(days=1)),
    ]:
        Payment.objects.create(tenant=demo, patient=p, amount=Decimal(monto), method=metodo, date=fecha,
                               registered_by=admin)

    # ── Cuotas vencidas, con pagos parciales y uno de más ────────────
    def plan(p, cuotas):
        presupuesto = Budget.objects.create(tenant=demo, patient=p, created_by=admin,
                                            total_amount=sum(Decimal(c[1]) for c in cuotas))
        pp = PaymentPlan.objects.create(tenant=demo, budget=presupuesto, patient=p,
                                        total_amount=presupuesto.total_amount, installment_count=len(cuotas))
        hechas = []
        for n, (dias, monto, estado, pagado) in enumerate(cuotas, start=1):
            c = Installment.objects.create(tenant=demo, payment_plan=pp, patient=p, number=n,
                                           due_date=hoy + timedelta(days=dias), amount=Decimal(monto), status=estado)
            if pagado:
                Payment.objects.create(tenant=demo, patient=p, installment=c, amount=Decimal(pagado),
                                       method="cash", date=primero - timedelta(days=40), registered_by=admin)
            hechas.append(c)
        return hechas
    plan(pacientes[8], [(-40, "100.00", "pending", "30.00"), (-10, "100.00", "overdue", None), (20, "100.00", "pending", None)])
    plan(pacientes[9], [(-5, "50.00", "paid", "50.00"), (-2, "80.00", "pending", None)])
    plan(pacientes[10], [(-15, "100.00", "pending", "120.00")])

    # ── Seguimientos clínicos ────────────────────────────────────────
    larga = ("Control de la endodoncia en la pieza 36 🦷 con radiografía periapical; revisar la "
             "sensibilidad a la percusión y el estado de la restauración provisional antes de cementar.")
    for i, (p, dias, notas) in enumerate([(pacientes[0], -3, "Control en 7 días"), (pacientes[11], 0, larga),
                                          (pacientes[12], 30, "Futuro: no debe salir"), (pacientes[13], -60, "Muy vencido")]):
        Evolution.objects.create(id=fijo(f"evolucion-seguimiento-{i}"), tenant=demo, patient=p, created_by=admin,
                                 type="clinical_note", date=hoy - timedelta(days=10), notes=notas,
                                 follow_up_date=hoy + timedelta(days=dias))
    Evolution.objects.create(tenant=demo, patient=pacientes[14], created_by=admin, type="clinical_note",
                             date=hoy, notes="Sin seguimiento")

    # ── Evoluciones de paciente-6: de la doctora, de recepción sin doctor, una receta visible ──
    Evolution.objects.create(id=fijo("evolucion-1"), tenant=demo, patient=pacientes[6], doctor=doctora,
                             created_by=doctora.user, type="clinical_note", date=hoy - timedelta(days=2),
                             notes="Dolor en la 36 al masticar.")
    Evolution.objects.create(id=fijo("evolucion-2"), tenant=demo, patient=pacientes[6], created_by=admin,
                             type="prescription", date=hoy - timedelta(days=2), notes="Ibuprofeno 400 mg c/8h",
                             visible_to_patient=True)
    Evolution.objects.create(id=fijo("evolucion-3"), tenant=demo, patient=pacientes[6], type="care_instruction",
                             date=hoy - timedelta(days=5), notes="Enjuague con clorhexidina")

    # ── Especialidades, tratamientos y planes ────────────────────────
    # Las cinco de base vienen de la referencia (las siembra el `bootstrap`); una
    # más con id fijo, para poder filtrar por ella desde peticiones.json.
    general = Specialty.objects.get(tenant=demo, name="Clínica general")
    endo = Specialty.objects.get(tenant=demo, name="Endodoncia")
    estetica = Specialty.objects.create(id=fijo("especialidad-estetica"), tenant=demo, name="Estética dental")
    Treatment.objects.create(id=fijo("tratamiento-blanqueamiento"), tenant=demo, specialty=estetica,
                             name="Blanqueamiento", base_price=Decimal("150"))
    resina = Treatment.objects.create(id=fijo("tratamiento-resina"), tenant=demo, specialty=general,
                                      name="Resina compuesta", base_price=Decimal("45.00"), consumes_inventory=True)
    Treatment.objects.create(id=fijo("tratamiento-endodoncia"), tenant=demo, specialty=endo,
                             name="Endodoncia unirradicular", base_price=Decimal("180.50"))
    Treatment.objects.create(id=fijo("tratamiento-antiguo"), tenant=demo, specialty=general,
                             name="Amalgama", base_price=Decimal("20"), is_active=False)
    # Los insumos de la resina: la anestesia sale primero del lote que caduca antes.
    anestesia = Product.objects.create(id=fijo("producto-anestesia"), tenant=demo, name="Anestesia", min_stock=Decimal("1"))
    for numero, cantidad, caduca in [("L1", "1.50", hoy + timedelta(days=200)), ("L2", "5", hoy + timedelta(days=30)),
                                     ("L3", "9", None)]:
        Batch.objects.create(id=fijo(f"lote-{numero}"), tenant=demo, product=anestesia, batch_number=numero,
                             quantity=Decimal(cantidad), expiration_date=caduca)
    TreatmentInventoryItem.objects.create(treatment=resina, product=anestesia, quantity_used=Decimal("6.25"))

    plan_activo = TreatmentPlan.objects.create(id=fijo("plan-1"), tenant=demo, patient=pacientes[6], created_by=doctora,
                                               status="active", notes="Rehabilitación del cuadrante 3")
    for orden, (tratamiento, pieza, estado, precio) in enumerate([
        (resina, "36", "done", "45.00"), (resina, "37", "planned", "40.00"),
        (Treatment.objects.get(id=fijo("tratamiento-endodoncia")), "36", "in_progress", "180.50"),
    ], start=1):
        TreatmentPlanItem.objects.create(id=fijo(f"linea-{orden}"), treatment_plan=plan_activo, treatment=tratamiento,
                                         tooth_fdi_code=pieza, order=orden, status=estado, estimated_price=Decimal(precio))
    TreatmentPlan.objects.create(id=fijo("plan-2"), tenant=demo, patient=pacientes[6], notes="")
    TreatmentPlan.objects.create(id=fijo("plan-vacio"), tenant=demo, patient=pacientes[6], notes="Sin líneas")

    # ── Tarifas, plantillas y un plan con precios a mano ─────────────
    endodoncia = Treatment.objects.get(id=fijo("tratamiento-endodoncia"))
    Tariff.objects.create(tenant=demo, treatment=resina, price=Decimal("50.00"))                       # general
    Tariff.objects.create(tenant=demo, treatment=endodoncia, agreement=seguro, price=Decimal("150.00"))  # del convenio
    Tariff.objects.create(tenant=demo, treatment=endodoncia, agreement=Agreement.objects.get(id=fijo("convenio-empresa")),
                          price=Decimal("-5"))   # mal cargada: nunca un precio negativo
    rehabilitacion = TreatmentPlanTemplate.objects.create(id=fijo("plantilla-1"), tenant=demo, name="Rehabilitación",
                                                          description="Endodoncia y resina")
    TreatmentPlanTemplateItem.objects.create(id=fijo("linea-plantilla-1"), template=rehabilitacion, treatment=resina,
                                             order=2, notes="Después de la endodoncia")
    TreatmentPlanTemplateItem.objects.create(id=fijo("linea-plantilla-2"), template=rehabilitacion, treatment=endodoncia,
                                             order=1)
    TreatmentPlanTemplate.objects.create(id=fijo("plantilla-vacia"), tenant=demo, name="Control anual")
    antigua = TreatmentPlanTemplate.objects.create(id=fijo("plantilla-antigua"), tenant=demo, name="Antigua",
                                                   is_active=False)
    TreatmentPlanTemplateItem.objects.create(template=antigua, treatment=Treatment.objects.get(id=fijo("tratamiento-antiguo")))
    plan_seguro = TreatmentPlan.objects.create(id=fijo("plan-seguro"), tenant=demo, patient=pacientes[5], status="active")
    TreatmentPlanItem.objects.create(treatment_plan=plan_seguro, treatment=resina, tooth_fdi_code="11", order=1,
                                     estimated_price=Decimal("99.99"), price_is_manual=True)
    TreatmentPlanItem.objects.create(treatment_plan=plan_seguro, treatment=endodoncia, tooth_fdi_code="21", order=2,
                                     estimated_price=Decimal("1"))
    TreatmentPlanItem.objects.create(treatment_plan=plan_seguro, treatment=resina, order=3,
                                     estimated_price=Decimal("0"), price_is_manual=True)

    # ── Inventario ───────────────────────────────────────────────────
    agujas = Product.objects.create(tenant=demo, name="Agujas", min_stock=Decimal("50"))
    Batch.objects.create(tenant=demo, product=agujas, batch_number="X", quantity=Decimal("10"))
    gasa = Product.objects.create(tenant=demo, name="Gasa", min_stock=Decimal("5"))
    Batch.objects.create(tenant=demo, product=gasa, batch_number="A", quantity=Decimal("30"))
    Batch.objects.create(tenant=demo, product=gasa, batch_number="B", quantity=Decimal("20"))
    Product.objects.create(tenant=demo, name="Guantes", min_stock=Decimal("10"))       # sin lotes: «0»
    Product.objects.create(tenant=demo, name="Composite", min_stock=Decimal("0"))      # 0 <= 0: bajo
    Product.objects.create(tenant=demo, name="Retirado", min_stock=Decimal("99"), is_active=False)

    # ── Bandeja de la app ────────────────────────────────────────────
    otro_estado = [v for v in SolicitudCita.Estado.values if v != SolicitudCita.Estado.PENDIENTE][0]
    for p, estado in [(pacientes[0], SolicitudCita.Estado.PENDIENTE), (pacientes[1], SolicitudCita.Estado.PENDIENTE),
                      (pacientes[2], otro_estado)]:
        SolicitudCita.objects.create(tenant=demo, patient=p, fecha_preferida=hoy + timedelta(days=2), estado=estado)
    MensajeConsultorio.objects.create(tenant=demo, patient=pacientes[0], texto="¿Puedo comer después?")
    MensajeConsultorio.objects.create(tenant=demo, patient=pacientes[1], texto="Gracias")
    MensajeConsultorio.objects.create(tenant=demo, patient=pacientes[2], texto="Hola", respuesta="Hola",
                                      respondido_por=admin, respondido_en=ahora)

    # ── Otra clínica, para comprobar que no se mezcla nada ───────────
    vecina = Tenant.objects.create(id=fijo("clinica-vecina"), name="Clínica Vecina")
    pv = Patient.objects.create(id=fijo("paciente-vecina"), tenant=vecina, first_name="Ana", last_name="Vecina", national_id="1700000000",
                                birth_date=datetime(1970, hoy.month, min(hoy.day, 28)).date())
    Payment.objects.create(tenant=vecina, patient=pv, amount=Decimal("5000.00"), method="cash", date=hoy)
    Product.objects.create(tenant=vecina, name="Ajeno", min_stock=Decimal("1000"))
    uv = User(id=fijo("doctor-vecina@clinube.local"), email="doctor-vecina@clinube.local", role="doctor",
              tenant=vecina, full_name="Dr. Vecino")
    uv.set_password(CLAVE)
    uv.save()
    Doctor.objects.create(id=fijo("doctor-vecina"), calendar_token=fijo("calendario-vecina"), tenant=vecina, user=uv)
    Agreement.objects.create(id=fijo("convenio-vecina"), tenant=vecina, name="Convenio ajeno",
                             discount_percentage=Decimal("50"))
    Form033Record.objects.create(id=fijo("form033-vecina"), tenant=vecina, patient=pv, date=hoy, motivo_consulta="Ajeno")
    ajena = Specialty.objects.create(id=fijo("especialidad-vecina"), tenant=vecina, name="Ajena")
    tratamiento_ajeno = Treatment.objects.create(id=fijo("tratamiento-vecina"), tenant=vecina, specialty=ajena,
                                                 name="Tratamiento ajeno", base_price=Decimal("1"))
    plan_ajeno = TreatmentPlan.objects.create(id=fijo("plan-vecina"), tenant=vecina, patient=pv)
    TreatmentPlanItem.objects.create(id=fijo("linea-vecina"), treatment_plan=plan_ajeno, treatment=tratamiento_ajeno)
    plantilla_ajena = TreatmentPlanTemplate.objects.create(id=fijo("plantilla-vecina"), tenant=vecina, name="Ajena")
    TreatmentPlanTemplateItem.objects.create(id=fijo("linea-plantilla-vecina"), template=plantilla_ajena,
                                             treatment=tratamiento_ajeno)
    Evolution.objects.create(id=fijo("evolucion-vecina"), tenant=vecina, patient=pv, type="clinical_note", date=hoy,
                             notes="Ajena")
    Diagnosis.objects.create(tenant=vecina, patient=pv, description="Ajeno", date=hoy)
    ajeno = OdontogramState.objects.create(id=fijo("estado-vecina"), tenant=vecina, code="CARIES", label="Caries ajena",
                                           color="#123456", order=1)
    ToothRecord.objects.create(id=fijo("diente-vecina"), tenant=vecina, patient=pv, tooth_fdi_code="16", state=ajeno, date=hoy)

    print(f"Sembrado en {BASE}: {Patient.objects.filter(tenant=demo).count()} pacientes, "
          f"{Appointment.objects.filter(tenant=demo).count()} citas, hoy {hoy} en {zona}.")


sembrar()
