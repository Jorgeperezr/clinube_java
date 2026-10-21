package com.clinube.plataforma

import com.clinube.clinicas.Clinica

/**
 * Qué módulos tiene contratada cada clínica. Portado de
 * `apps/common/funcionalidades.py`, con el mismo catálogo y el mismo orden.
 *
 * **Lo básico no se apaga.** Pacientes, agenda, historia clínica y pagos no
 * están en esta lista: una clínica odontológica sin ficha de paciente no es
 * una versión reducida del producto, es un producto que no sirve.
 *
 * **Apagar algo NO borra sus datos.** Apagar es dejar de ver y de poder
 * tocar, no destruir.
 *
 * Dos llaves, cada una de quien le toca: la plataforma decide qué se PUEDE
 * usar (`funcionalidades` de la clínica) y la clínica, qué QUIERE usar
 * (`modulos_clinica`). Una funcionalidad está activa solo si las dos lo
 * dicen.
 */
object Funcionalidades {

    data class Entrada(
        val nombre: String,
        val descripcion: String,
        /** «incluida»: viene con el producto. «adicional»: se agrega a pedido. */
        val tipo: String,
        val porDefecto: Boolean,
        /** «prueba» mientras solo existe en las clínicas de prueba. */
        val fase: String? = null,
        val origen: String = "",
    )

    val CATALOGO: Map<String, Entrada> = linkedMapOf(
        "inventario" to Entrada(
            "Inventario", "Control de stock e insumos por tratamiento.", "incluida", true,
        ),
        "whatsapp" to Entrada(
            "Recordatorios por WhatsApp",
            "Recordatorios, recetas y confirmaciones por WhatsApp. Se contrata aparte: la " +
                "clínica paga su cuenta de Meta y entrega su número a la plataforma.",
            "adicional", false,
        ),
        "convenios" to Entrada(
            "Convenios y tarifarios", "Precios pactados con aseguradoras y empresas.", "incluida", true,
        ),
        "app_paciente" to Entrada(
            "App del paciente", "Sus citas, su saldo y sus indicaciones en el teléfono.", "incluida", true,
        ),
        "formulario_033" to Entrada(
            "Formulario MSP 033",
            "Historia clínica única del Ministerio de Salud (HCU-033).", "incluida", true,
        ),
        "reportes" to Entrada(
            "Reportes", "Informes financieros y de producción.", "incluida", true,
        ),
        "temas_personales" to Entrada(
            "Tema de cada persona",
            "Cada profesional elige el tema de su panel entre los de su clínica y " +
                "los de Clinube. Los documentos y la app del paciente siguen con el " +
                "tema de la clínica.",
            "incluida", true, fase = "prueba",
        ),
        "finanzas" to Entrada(
            "Finanzas",
            "Lo que entra y lo que sale: egresos, caja diaria, utilidad del mes, " +
                "liquidaciones de honorarios, rol de pagos, comprobantes de cobro y el " +
                "Excel para el contador.",
            "incluida", true, fase = "prueba",
        ),
        "odontograma_3d" to Entrada(
            "Odontograma 3D",
            "Modelo tridimensional de las piezas en la ficha, junto al odontograma " +
                "clásico, que sigue siendo el del formulario oficial.",
            "adicional", false,
            origen = "Pedida por una clínica odontológica cliente (2026)",
        ),
        "logros" to Entrada(
            "Rachas y logros",
            "Programa de fidelización: el paciente gana logros y rachas por acudir a " +
                "sus controles y estar al día, y los ve en su app. No envía mensajes: " +
                "los recordatorios son otra función.",
            "adicional", false,
            origen = "Pedida por una clínica odontológica cliente (2026)",
        ),
    )

    val POR_DEFECTO: Map<String, Boolean> = CATALOGO.mapValues { it.value.porDefecto }

    /** Las que nacieron en prueba: se pueden publicar y volver a retirar. */
    val NACIDAS_EN_PRUEBA: Set<String> =
        CATALOGO.filterValues { it.fase == "prueba" }.keys

    /**
     * Deja el diccionario con exactamente las claves del catálogo: las
     * desconocidas se tiran —serán de una versión anterior— y las que
     * faltan toman su valor por defecto. Así, añadir una funcionalidad
     * nueva no obliga a migrar los datos de nadie.
     */
    fun normalizar(valor: Map<String, Any?>?): Map<String, Boolean> =
        CATALOGO.keys.associateWith { clave ->
            (valor?.get(clave) as? Boolean) ?: POR_DEFECTO.getValue(clave)
        }

    /**
     * Lo que la plataforma le ha dado de alta a la clínica. Una función en
     * prueba solo cuenta en una clínica de prueba: en las demás no existe
     * todavía, la tengan marcada o no.
     */
    fun contratadas(clinica: Clinica, enPrueba: Set<String>): Map<String, Boolean> {
        val datos = normalizar(clinica.funcionalidades).toMutableMap()
        if (!clinica.esDePruebas) {
            enPrueba.forEach { datos[it] = false }
        }
        return datos
    }

    /** Lo contratado por la plataforma Y no apagado por la propia clínica. */
    fun efectivas(clinica: Clinica?, enPrueba: Set<String>): Map<String, Boolean> {
        if (clinica == null) return CATALOGO.keys.associateWith { false }
        val propias = clinica.modulosDeLaClinica
        return contratadas(clinica, enPrueba).mapValues { (clave, activa) ->
            activa && propias[clave] != false
        }
    }
}
