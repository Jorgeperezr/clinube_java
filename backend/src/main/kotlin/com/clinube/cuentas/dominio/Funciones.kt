package com.clinube.cuentas.dominio

/**
 * Qué módulos de gestión puede usar cada profesional. Portado de
 * `apps/accounts/funciones.py`, incluido el orden del catálogo: el panel
 * recibe este diccionario y lo pinta en el orden en que llega.
 *
 * El rol sigue mandando en lo clínico (quién escribe en la historia, quién
 * firma una receta). Lo que se decide por persona son los MÓDULOS, porque
 * ahí cada clínica se organiza distinto.
 *
 * Dos juegos de valores por defecto, y la diferencia importa:
 *
 *   · HEREDADAS: lo que cada rol podía hacer antes de que existieran las
 *     funciones. Se aplican a quien se creó entonces y no tiene nada
 *     guardado, para que actualizar no le quite ni le dé nada a nadie.
 *   · AL_CREAR: lo que se propone marcado al dar de alta a alguien nuevo.
 */
object Funciones {

    data class Entrada(
        val etiqueta: String,
        val descripcion: String,
        /** La funcionalidad de la clínica de la que depende, si depende de alguna. */
        val funcionalidad: String? = null,
        /** Los roles para los que la función tiene sentido. */
        val roles: List<String> = ROLES_CON_FUNCIONES,
    )

    val ROLES_CON_FUNCIONES = listOf(Rol.RECEPCION, Rol.DOCTOR, Rol.AUXILIAR)

    /** El orden es el de Django y se respeta: es el orden en que lo pinta el panel. */
    val CATALOGO: Map<String, Entrada> = linkedMapOf(
        "agenda" to Entrada(
            "Gestionar la agenda",
            "Crear, mover, confirmar y cancelar citas, y agendar las solicitudes que llegan desde la app.",
        ),
        "cobros" to Entrada(
            "Cobros y presupuestos",
            "Presupuestos, planes de pago, cobro de cuotas y convenio del paciente.",
        ),
        "mensajes_app" to Entrada(
            "Responder mensajes de la app",
            "Contestar las preguntas que los pacientes envían desde la aplicación.",
            funcionalidad = "app_paciente",
        ),
        "logros" to Entrada(
            "Rachas y logros",
            "Conceder logros y descuentos a los pacientes.",
            funcionalidad = "logros",
        ),
        "inventario" to Entrada(
            "Inventario",
            "Productos, lotes, entradas y salidas de material.",
            funcionalidad = "inventario",
        ),
        "reportes" to Entrada(
            "Reportes de gestión",
            "Ingresos, pacientes nuevos y resumen de citas de toda la clínica.",
            funcionalidad = "reportes",
        ),
        "finanzas" to Entrada(
            "Finanzas",
            "Egresos, caja, utilidad del mes, honorarios y rol de pagos del equipo. " +
                "Ve lo que cobra y lo que gana cada persona: dalo solo a quien lleva las cuentas.",
            funcionalidad = "finanzas",
        ),
        "whatsapp" to Entrada(
            "Configurar WhatsApp",
            "Conectar la cuenta de WhatsApp de la clínica y encender los recordatorios.",
            funcionalidad = "whatsapp",
        ),
        // Solo para quien atiende pacientes: la copia lleva SUS pacientes y
        // su historia clínica, así que no tiene sentido para recepción.
        "respaldo" to Entrada(
            "Respaldo cifrado de sus pacientes",
            "Descargar una copia cifrada de la información de los pacientes que atiende: " +
                "historias, evoluciones, odontogramas y su agenda.",
            roles = listOf(Rol.DOCTOR, Rol.AUXILIAR),
        ),
    )

    /**
     * Lo que cada rol podía hacer antes. NO cambiar sin una migración: es
     * lo que tiene hoy cada persona creada antes de que existieran las
     * funciones.
     */
    val HEREDADAS: Map<String, Set<String>> = mapOf(
        Rol.RECEPCION to setOf("agenda", "cobros", "mensajes_app"),
        Rol.DOCTOR to setOf("mensajes_app", "respaldo"),
        Rol.AUXILIAR to setOf("inventario", "respaldo"),
    )

    /** Lo que se propone marcado al dar de alta a alguien. */
    val AL_CREAR: Map<String, Set<String>> = mapOf(
        Rol.RECEPCION to setOf("agenda", "cobros", "mensajes_app"),
        Rol.DOCTOR to setOf("agenda", "mensajes_app", "logros", "respaldo"),
        Rol.AUXILIAR to setOf("inventario", "respaldo"),
    )

    private fun esAdministracion(usuario: Usuario) =
        usuario.rol == Rol.ADMIN || usuario.esSuperusuario

    /** Si la función tiene sentido para ese rol (p. ej. «respaldo» no, en recepción). */
    fun aplica(clave: String, rol: String): Boolean =
        rol in (CATALOGO[clave]?.roles ?: ROLES_CON_FUNCIONES)

    fun alCrear(rol: String): Map<String, Boolean> =
        CATALOGO.keys.associateWith { clave ->
            clave in (AL_CREAR[rol] ?: emptySet()) && aplica(clave, rol)
        }

    /**
     * Qué funciones tiene de verdad esta persona, clave por clave.
     *
     * Una clave que falta toma el valor heredado del rol: así, añadir al
     * catálogo una función nueva no le cambia el acceso a nadie.
     */
    fun de(usuario: Usuario): Map<String, Boolean> {
        if (esAdministracion(usuario)) return CATALOGO.keys.associateWith { true }
        if (usuario.rol !in ROLES_CON_FUNCIONES) return CATALOGO.keys.associateWith { false }
        val guardadas = usuario.funciones
        val heredadas = HEREDADAS[usuario.rol] ?: emptySet()
        return CATALOGO.keys.associateWith { clave ->
            aplica(clave, usuario.rol) &&
                (guardadas[clave] as? Boolean ?: (clave in heredadas))
        }
    }

    fun tiene(usuario: Usuario, clave: String): Boolean = de(usuario)[clave] ?: false
}
