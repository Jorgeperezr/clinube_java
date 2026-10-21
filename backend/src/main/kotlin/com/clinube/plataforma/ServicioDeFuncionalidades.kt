package com.clinube.plataforma

import com.clinube.clinicas.Clinica
import org.springframework.stereotype.Service

/** Las funcionalidades de una clínica, resolviendo antes qué está en prueba. */
@Service
class ServicioDeFuncionalidades(private val configuracion: ConfiguracionDePlataforma) {

    /** Las que todavía solo existen en las clínicas de prueba. */
    fun enPrueba(): Set<String> {
        if (Funcionalidades.NACIDAS_EN_PRUEBA.isEmpty()) return emptySet()
        return Funcionalidades.NACIDAS_EN_PRUEBA - configuracion.funcionesPublicadas()
    }

    fun efectivas(clinica: Clinica?): Map<String, Boolean> =
        Funcionalidades.efectivas(clinica, enPrueba())

    fun activa(clinica: Clinica?, clave: String): Boolean = efectivas(clinica)[clave] ?: false
}
