#!/usr/bin/env python3
"""
Compara el ESTADO de dos bases después de que el comparador de contrato
lance las mismas peticiones contra Django y contra Kotlin.

Las respuestas iguales no bastan: una vista puede responder lo mismo y
dejar la base distinta (una fila de auditoría de menos, un `updated_at`
que no debía cambiar). Aquí se resume cada tabla que tocan las rutas
portadas, sin identificadores generados ni marcas de tiempo, y se comparan.

    python3 scripts/comparar-estado.py clinube_cmp_django clinube_cmp_kotlin
"""
import json
import subprocess
import sys

RESUMENES = {
    "pacientes": """select first_name, last_name, national_id, birth_date, phone, email, address,
                    agreement_id, is_active, deleted_at is null as sin_baja, sex, photo, user_id
                    from patients_patient order by national_id, first_name, is_active""",
    "citas": """select patient_id, doctor_id, treatment_id, scheduled_start, scheduled_end, status, notes,
                checkin_at is null as sin_llegada, attention_started_at is null as sin_atencion,
                checkout_at is null as sin_salida, created_by_id, google_calendar_event_id
                from agenda_appointment order by scheduled_start, patient_id, status, notes""",
    "antecedentes": """select patient_id, allergies, medications, conditions, is_pregnant, updated_by_id
                       from patients_medicalbackground order by patient_id""",
    "avisos": """select patient_id, version, medio, registrado_por_id, tenant_id
                 from patients_avisoprivacidadentregado order by patient_id, version""",
    "auditoria": """select action, entity_type,
                    case when action in ('accept_terms') then 'id generado' else entity_id end as entity_id,
                    ip_address is null as sin_ip, metadata, user_id, tenant_id, count(*) as filas
                    from accounts_auditlog group by 1, 2, 3, 4, 5, 6, 7
                    -- `5::text` ordenaría por la constante «5»: hay que nombrar la columna.
                    order by 1, 2, 3, 4, metadata::text, 6, 7""",
    "formularios 033": """select patient_id, date, motivo_consulta, embarazada, enfermedad_actual,
                          antecedentes_personales, antecedentes_familiares, temperatura::text, pulso,
                          frecuencia_respiratoria, presion_arterial, examen_estomatognatico, indicadores_salud_bucal,
                          professional_snapshot - 'recorded_at' as constancia, created_by_id, doctor_id, tenant_id
                          from clinical_form033record
                          order by patient_id, date, motivo_consulta, antecedentes_personales::text, 
                                   indicadores_salud_bucal::text, examen_estomatognatico::text, temperatura""",
    "diagnósticos": """select patient_id, doctor_id, tooth_fdi_code, code, description, diagnosis_kind, date, tenant_id
                       from clinical_diagnosis order by patient_id, date, description, code, tooth_fdi_code""",
    # Sin los pacientes: Django apunta un refresco al rechazar su login por
    # el panel (lo crea `super().validate()` antes de mirar el rol) y Kotlin
    # no, a propósito (docs/DIFERENCIAS.md). Cuando se porte su entrada por
    # código, sus tokens tendrán que entrar aquí.
    "tokens": """select v.user_id, count(*) as apuntados, count(n.id) as invalidados
                 from token_blacklist_outstandingtoken v
                 join accounts_user u on u.id = v.user_id and u.role <> 'patient'
                 left join token_blacklist_blacklistedtoken n on n.token_id = v.id
                 group by v.user_id order by v.user_id""",
    "dientes": """select patient_id, tooth_fdi_code, surface, state_id, doctor_id, treatment_plan_item_id, date, notes,
                  mobility, recession, tenant_id
                  from clinical_toothrecord
                  order by patient_id, tooth_fdi_code, surface, date, state_id, notes, mobility, recession""",
    "marca": "select tenant_id, theme, paleta, temas, display_name from configuration_clinicbranding order by tenant_id",
    "configuración": """select platform_name, canal_codigo_paciente, funciones_publicadas, google_client_id
                        from common_platformconfiguration""",
    "condiciones": """select tenant_id, version, nombre, correo, aceptada_por_id
                      from common_aceptacioncondiciones order by tenant_id""",
}


def resumen(base, sql):
    salida = subprocess.run(
        ["psql", "-h", "localhost", "-U", "clinica", "-d", base, "-tA", "-c",
         f"select coalesce(json_agg(t), '[]') from ({sql}) t"],
        capture_output=True, text=True, check=True,
    )
    return json.loads(salida.stdout)


def main():
    base_a, base_b = sys.argv[1], sys.argv[2]
    distintas = 0
    for nombre, sql in RESUMENES.items():
        a, b = resumen(base_a, sql), resumen(base_b, sql)
        if a == b:
            print(f"\033[32m✔ {nombre}\033[0m  ({len(a)} filas)")
            continue
        distintas += 1
        print(f"\033[31m✘ {nombre}\033[0m  Django {len(a)} filas, Kotlin {len(b)}")
        solo_a = [x for x in a if x not in b]
        solo_b = [x for x in b if x not in a]
        for fila in solo_a[:5]:
            print(f"    solo en Django: {json.dumps(fila, ensure_ascii=False)}")
        for fila in solo_b[:5]:
            print(f"    solo en Kotlin: {json.dumps(fila, ensure_ascii=False)}")
    print(f"\n{len(RESUMENES) - distintas} tablas iguales, {distintas} distintas.")
    return 1 if distintas else 0


if __name__ == "__main__":
    sys.exit(main())
