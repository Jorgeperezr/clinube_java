package com.clinube.plataforma

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Lo que hace falta de `common_platformconfiguration` para las funciones
 * publicadas. La tabla entera es de la fase 11 (Plataforma); aquí se lee
 * solo esta columna, con una consulta directa, para no mapear todavía una
 * entidad que no se va a usar.
 */
@Component
class ConfiguracionDePlataforma(
    private val em: EntityManager,
    private val json: ObjectMapper,
) {

    /**
     * Las funciones nacidas en prueba que el Super Administrador ya dio a
     * todas las clínicas. Si no hay fila de configuración, ninguna.
     */
    @Transactional(readOnly = true)
    fun funcionesPublicadas(): Set<String> {
        if (Funcionalidades.NACIDAS_EN_PRUEBA.isEmpty()) return emptySet()
        val crudo = em.createNativeQuery(
            "select cast(funciones_publicadas as text) from common_platformconfiguration limit 1",
        ).resultList.firstOrNull() as? String ?: return emptySet()
        val lista = try {
            json.readValue(crudo, List::class.java)
        } catch (e: Exception) {
            return emptySet()
        }
        return lista.filterIsInstance<String>().filter { it in Funcionalidades.NACIDAS_EN_PRUEBA }.toSet()
    }
}
