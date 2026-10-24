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
    # Los ids que genera cada servidor no pueden coincidir: los de las filas
    # nuevas de finanzas (y los que van en las rutas de finanzas que las
    # usan) se cambian por un marcador.
    "auditoria": """select action,
                    case when entity_type like '/api/v1/finanzas/%' or entity_type like '/api/v1/products/%'
                         then regexp_replace(entity_type, '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', '{id}', 'g')
                         else entity_type end as entity_type,
                    case when action in ('accept_terms', 'apply_plan_template', 'generate_budget_from_plan', 'create_expense',
                                         'update_expense', 'close_cash', 'update_work_profile', 'create_settlement',
                                         'pay_settlement', 'void_settlement', 'save_payroll', 'pay_payroll', 'pay_obligation')
                         then 'id generado' else entity_id end as entity_id,
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
    "evoluciones": """select patient_id, doctor_id, appointment_id, type, date, notes, visible_to_patient, follow_up_date,
                      created_by_id, tenant_id from clinical_evolution
                      order by patient_id, date, type, notes""",
    "planes": """select patient_id, created_by_id, status, notes, tenant_id from clinical_treatmentplan
                 order by patient_id, status, notes""",
    "líneas de plan": """select p.patient_id, i.treatment_id, i.tooth_fdi_code, i."order", i.status, i.estimated_price::text,
                         i.price_is_manual from clinical_treatmentplanitem i join clinical_treatmentplan p on p.id = i.treatment_plan_id
                         order by p.patient_id, i."order", i.tooth_fdi_code, i.status, i.estimated_price""",
    "productos": """select tenant_id, name, unit, min_stock::text, is_active from inventory_product
                    order by tenant_id, name, unit, min_stock, is_active""",
    "tarifas de doctores": """select tenant_id, doctor_id, treatment_id, fee_type, value::text from billing_doctorfee
                              order by doctor_id, fee_type, value, treatment_id""",
    "lotes": "select product_id, batch_number, quantity::text, expiration_date from inventory_batch order by product_id, batch_number",
    # El lote, por su número: los lotes nuevos tienen la id de cada servidor.
    "movimientos": """select m.product_id, b.batch_number, m.movement_type, m.quantity::text, m.reason,
                      m.related_treatment_plan_item_id is not null as de_tratamiento, m.tenant_id, count(*) as filas
                      from inventory_inventorymovement m left join inventory_batch b on b.id = m.batch_id
                      group by 1, 2, 3, 4, 5, 6, 7 order by 1, 2, 3, 4, 5, 6, 7""",
    "plantillas": """select t.name, t.description, t.is_active, t.tenant_id, i.treatment_id, i."order", i.notes
                     from clinical_treatmentplantemplate t left join clinical_treatmentplantemplateitem i on i.template_id = t.id
                     order by t.tenant_id, t.name, i."order", i.notes, i.treatment_id""",
    "presupuestos": """select b.patient_id, b.status, b.total_amount::text, b.notes, b.created_by_id, b.tenant_id,
                       i.treatment_id, i.tooth_fdi_code, i.quantity, i.unit_price::text
                       from billing_budget b left join billing_budgetitem i on i.budget_id = b.id
                       order by b.patient_id, b.total_amount, b.notes, i.treatment_id, i.tooth_fdi_code, i.unit_price""",
    "planes de pago": """select pp.patient_id, pp.total_amount::text, pp.installment_count, pp.tenant_id, i.number, i.due_date,
                         i.amount::text, i.status from billing_paymentplan pp left join billing_installment i on i.payment_plan_id = pp.id
                         order by pp.patient_id, pp.total_amount, pp.installment_count, i.number""",
    "pagos": """select patient_id, installment_id, amount::text, method, date, registered_by_id, profesional_id,
                numero_comprobante, tenant_id from billing_payment
                order by patient_id, date, amount, method, installment_id""",
    "configuración de finanzas": """select tenant_id, salario_basico::text, aporte_personal_iess::text, aporte_patronal_iess::text,
                                    aporte_iece_secap::text, fondos_reserva::text, retencion_honorarios::text, siguiente_comprobante,
                                    region, recargo_suplementarias::text, recargo_extraordinarias::text
                                    from finanzas_configuracionfinanzas order by tenant_id""",
    "categorías de egreso": """select tenant_id, nombre, clave, activa, orden from finanzas_categoriaegreso
                               order by tenant_id, orden, nombre, clave""",
    "egresos": """select e.tenant_id, e.fecha, c.nombre as categoria, e.concepto, e.monto::text, e.forma_pago, e.estado, e.vence,
                  e.proveedor, e.proveedor_ruc, e.numero_factura, e.comprobante, e.descontar_al_profesional, e.profesional_id,
                  e.origen, e.periodo, e.registrado_por_id, e.anulado, e.motivo_anulacion
                  from finanzas_egreso e join finanzas_categoriaegreso c on c.id = e.categoria_id
                  order by e.tenant_id, e.fecha, e.concepto, e.monto, e.estado, e.anulado, e.origen""",
    "cierres de caja": """select tenant_id, fecha, saldo_inicial::text, cobros_efectivo::text, egresos_efectivo::text, esperado::text,
                          contado::text, diferencia::text, retirado::text, observaciones, cerrado_por_id
                          from finanzas_cierrecaja order by tenant_id, fecha""",
    "perfiles laborales": """select tenant_id, usuario_id, modalidad, cedula, cargo, base_honorarios, porcentaje::text, monto_fijo::text,
                             emite_factura, sueldo::text, fecha_ingreso, decimo_tercero_mensual, decimo_cuarto_mensual,
                             fondos_reserva_mensual from finanzas_perfillaboral order by usuario_id""",
    # Los cobros y los descuentos pueden haberlos creado las peticiones: sin su id.
    "liquidaciones": """select tenant_id, numero, profesional_id, desde, hasta, base::text, porcentaje::text, honorario::text,
                        descuentos::text, ajustes::text, retencion_porcentaje::text, retencion::text, a_pagar::text,
                        detalle->'modalidad' as modalidad,
                        (select coalesce(jsonb_agg(c - 'id'), '[]') from jsonb_array_elements(detalle->'cobros') c) as cobros_sin_id,
                        jsonb_array_length(detalle->'cobros_ids') as liquidados,
                        (select coalesce(jsonb_agg(d - 'id'), '[]') from jsonb_array_elements(detalle->'descuentos') d) as descuentos_sin_id,
                        jsonb_array_length(detalle->'descuentos_ids') as descontados, nota, estado, egreso_id is null as sin_egreso,
                        creada_por_id from finanzas_liquidacion order by tenant_id, numero""",
    "roles de pagos": """select tenant_id, empleado_id, mes, dias_trabajados, horas_extra::text, horas_suplementarias::text,
                         horas_extraordinarias::text, otros_ingresos::text, anticipos::text, prestamos_iess::text,
                         otros_descuentos::text, calculo, neto::text, costo_empleador::text, estado, egreso_id is null as sin_egreso,
                         creado_por_id from finanzas_rolpagos order by tenant_id, empleado_id, mes, estado""",
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
