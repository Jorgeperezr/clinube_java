package com.clinube.legal

// GENERADO por scripts/generar-textos-legales.py desde apps/common/legal.py
// de clinube. NO SE EDITA A MANO: si cambia un texto en Django, se vuelve a
// generar, y se sube la versión en TextosLegales.kt igual que allí.

@Suppress("LongMethod", "MaxLineLength")
internal object TextosLegalesGenerados {
    fun seccionesDelAviso(d: DatosDelTitular, encargados: List<String>): List<Seccion> = listOf(
        Seccion(
            "Quién trata tus datos",
            listOf(
                "Tus datos los trata la clínica o el consultorio que te atiende. Es la responsable del tratamiento: decide para qué se usan y es a quien primero debes acudir con cualquier pregunta sobre tu historia clínica, tus citas o tus pagos.",
                "La clínica lleva esa información en ${d.plataforma}, un sistema que opera ${d.titular} (RUC ${d.ruc}). ${d.plataforma} es encargado del tratamiento: guarda y procesa los datos por cuenta de la clínica y siguiendo sus instrucciones, nunca para fines propios. Contacto para temas de privacidad: ${d.correo}.",
            ),
        ),
        Seccion(
            "Qué datos se guardan",
            listOf(
                "Identificación y contacto: nombre, cédula o pasaporte, fecha de nacimiento, sexo, teléfono, correo y dirección.",
                "Datos de salud: historia clínica, odontograma, diagnósticos, tratamientos, radiografías, documentos y consentimientos firmados. La Ley Orgánica de Protección de Datos Personales (LOPDP) los considera datos sensibles y se protegen como tales.",
                "Citas, presupuestos, pagos y saldos.",
                "Si usas la app: el número de teléfono con el que entras y lo que envías desde ella, como solicitudes de cita y mensajes.",
            ),
        ),
        Seccion(
            "Para qué se usan",
            listOf(
                "Para atenderte y llevar tu historia clínica, como exige la normativa sanitaria.",
                "Para organizar tus citas, recordártelas y gestionar cobros y facturación.",
                "Para mandarte recordatorios por WhatsApp, solo si lo autorizaste. Puedes retirar esa autorización cuando quieras pidiéndolo en la clínica.",
                "No se venden, no se ceden para publicidad y no se usan para tomar decisiones automáticas sobre ti.",
            ),
        ),
        Seccion(
            "Con qué base legal",
            listOf(
                "La prestación de asistencia sanitaria, las obligaciones legales de la clínica (historia clínica, facturación) y tu consentimiento para lo que no es imprescindible, como los recordatorios por WhatsApp.",
            ),
        ),
        Seccion(
            "Dónde están y quién más interviene",
            listOf(
                "Los datos se guardan en un servidor de Oracle Cloud en Santiago de Chile. Como es fuera de Ecuador, se trata de una transferencia internacional de datos.",
                "Además de la clínica y de quien opera la plataforma, intervienen estos proveedores, cada uno solo en lo necesario para su función:",
                *encargados.toTypedArray(),
            ),
        ),
        Seccion(
            "Cuánto tiempo se guardan",
            listOf(
                "La historia clínica se conserva el tiempo que exige la normativa sanitaria del Ecuador, aunque dejes de ir a la clínica. El resto, mientras dure tu relación con la clínica y lo que exijan otras leyes, como las tributarias.",
                "Las copias de seguridad se borran solas a los 30 días.",
            ),
        ),
        Seccion(
            "Cómo se protegen",
            listOf(
                "Conexiones cifradas, una cuenta personal para cada persona de la clínica con permisos según su función, registro de quién hace qué, y copias de seguridad diarias cifradas y guardadas fuera del servidor.",
            ),
        ),
        Seccion(
            "Tus derechos",
            listOf(
                "La LOPDP te reconoce los derechos de acceso, rectificación y actualización, eliminación, oposición, portabilidad, suspensión del tratamiento y a no ser objeto de decisiones basadas únicamente en tratamientos automatizados.",
                "Para ejercerlos, pídelo en la clínica que te atiende o escribe a ${d.correo}. Tienes respuesta en un plazo máximo de 15 días.",
                "La eliminación tiene un límite: la historia clínica no se puede borrar mientras la ley obligue a conservarla.",
                "Si crees que no se respetaron tus derechos, puedes presentar un reclamo ante la Superintendencia de Protección de Datos Personales.",
            ),
        ),
        Seccion(
            "Cambios en este aviso",
            listOf(
                "Si cambia, la versión nueva se publica en esta misma dirección con su fecha.",
            ),
        ),
    )

    fun seccionesDeLasCondiciones(d: DatosDelTitular, encargados: List<String>): List<Seccion> = listOf(
        Seccion(
            "Partes y objeto",
            listOf(
                "${d.titular} (RUC ${d.ruc}), que opera ${d.plataforma} («la plataforma»), y la clínica que la usa («la clínica»), representada por quien acepta estas condiciones como administrador o administradora.",
                "La plataforma presta un sistema de gestión clínica en la nube: agenda, fichas de pacientes, historia clínica, cobros, app del paciente y copias de seguridad.",
            ),
        ),
        Seccion(
            "Quién responde de los datos",
            listOf(
                "La clínica es la responsable del tratamiento de los datos de sus pacientes y de su personal. La plataforma es encargada del tratamiento: los trata solo para prestar el servicio y según las instrucciones de la clínica, que son estas condiciones y el uso que la clínica hace del sistema.",
            ),
        ),
        Seccion(
            "Lo que se compromete a hacer la plataforma",
            listOf(
                "Usar los datos únicamente para prestar el servicio. No venderlos, no cederlos y no usarlos para fines propios.",
                "Exigir confidencialidad a toda persona que tenga acceso a ellos.",
                "Mantener medidas de seguridad: conexiones cifradas, copias diarias cifradas guardadas fuera del servidor, parches de seguridad automáticos y acceso administrativo restringido.",
                "Trabajar solo con estos proveedores (subencargados), y avisar a la clínica antes de añadir o cambiar alguno:",
                *encargados.toTypedArray(),
                "Avisar a la clínica sin demora de cualquier violación de seguridad que afecte a sus datos, con lo que se sepa en ese momento, para que pueda cumplir su propio deber de notificarla a la Superintendencia de Protección de Datos Personales y a los afectados.",
                "Ayudar a la clínica a atender las solicitudes de sus pacientes sobre sus derechos.",
                "Al terminar el servicio, dar a la clínica 30 días para descargar su copia completa (datos y archivos). Después, los datos se borran del servidor, y de las copias de seguridad cuando estas caducan, a los 30 días.",
            ),
        ),
        Seccion(
            "Lo que se compromete a hacer la clínica",
            listOf(
                "Tener base legal para los datos que registra y entregar a cada paciente el aviso de privacidad. El sistema permite dejar constancia de la entrega en la ficha.",
                "Pedir la autorización del paciente antes de activarle los recordatorios por WhatsApp.",
                "Crear una cuenta personal para cada persona que use el sistema, con el rol que le corresponde, y desactivarla cuando deje la clínica. Las contraseñas no se comparten.",
                "Usar el sistema solo para la gestión de la propia clínica. No está permitido subir contenido ilícito o programas maliciosos, intentar acceder a datos de otras clínicas, sobrecargar el servicio, escanear o probar la seguridad del sistema o de la infraestructura donde se aloja sin autorización por escrito, ni hacer nada que vaya contra las políticas de uso aceptable de los proveedores de alojamiento.",
                "Descargar sus propias copias de seguridad cada cierto tiempo, desde Configuración → Copia de seguridad.",
            ),
        ),
        Seccion(
            "Disponibilidad y suspensión",
            listOf(
                "La plataforma procura que el servicio esté siempre disponible, pero no puede garantizar que funcione sin interrupciones: hay mantenimientos y fallos de proveedores que no controla.",
                "Puede suspender el acceso de una clínica que incumpla el uso permitido. Avisará antes, salvo que el riesgo para otros sea inmediato.",
            ),
        ),
        Seccion(
            "Cambios",
            listOf(
                "Si estas condiciones cambian, la versión nueva se mostrará al entrar en el panel y habrá que aceptarla para seguir usándolo.",
            ),
        ),
        Seccion(
            "Ley aplicable",
            listOf(
                "Estas condiciones se rigen por las leyes de la República del Ecuador, en particular por la Ley Orgánica de Protección de Datos Personales y su reglamento.",
            ),
        ),
    )
}
