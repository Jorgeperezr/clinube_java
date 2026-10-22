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
from apps.clinical.models import Evolution  # noqa: E402
from apps.common.models import Tenant  # noqa: E402
from apps.inventory.models import Batch, Product  # noqa: E402
from apps.patients.models import Patient  # noqa: E402

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
                                    user=usuario("doctor2@clinube.local", "doctor", ""))
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
    Patient.objects.create(tenant=demo, first_name="Dado", last_name="De Baja", national_id="1799999999",
                           is_active=False, birth_date=datetime(1990, hoy.month, min(hoy.day, 28)).date())

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
    for p, dias, notas in [(pacientes[0], -3, "Control en 7 días"), (pacientes[11], 0, larga),
                           (pacientes[12], 30, "Futuro: no debe salir"), (pacientes[13], -60, "Muy vencido")]:
        Evolution.objects.create(tenant=demo, patient=p, created_by=admin, type="clinical_note",
                                 date=hoy - timedelta(days=10), notes=notas, follow_up_date=hoy + timedelta(days=dias))
    Evolution.objects.create(tenant=demo, patient=pacientes[14], created_by=admin, type="clinical_note",
                             date=hoy, notes="Sin seguimiento")

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

    print(f"Sembrado en {BASE}: {Patient.objects.filter(tenant=demo).count()} pacientes, "
          f"{Appointment.objects.filter(tenant=demo).count()} citas, hoy {hoy} en {zona}.")


sembrar()
