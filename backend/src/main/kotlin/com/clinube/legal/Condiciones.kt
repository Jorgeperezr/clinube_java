package com.clinube.legal

import com.clinube.cuentas.dominio.Rol
import com.clinube.cuentas.dominio.Usuario
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Las condiciones de uso que la clínica tiene que aceptar.
 *
 * CAMBIAR UN TEXTO ES CAMBIAR SU VERSIÓN: al subir la versión, todas las
 * clínicas vuelven a tener que aceptarlas. Los textos completos son de la
 * fase 11; aquí está lo único que necesita `/auth/me/`.
 */
@Component
class Condiciones(private val em: EntityManager) {

    companion object {
        /** Igual que `CONDICIONES_VERSION` en `apps/common/legal.py`. */
        const val VERSION = "2026.10"
    }

    /**
     * ¿Tiene esta persona que aceptar las condiciones antes de seguir?
     *
     * Solo la administración de una clínica puede aceptarlas, y basta con
     * que lo haga una de sus administradoras: la que acepta lo hace en
     * nombre de la clínica.
     */
    @Transactional(readOnly = true)
    fun pendientesPara(usuario: Usuario): Boolean {
        if (usuario.rol != Rol.ADMIN || usuario.clinicaId == null) return false
        val cuantas = em.createNativeQuery(
            "select count(*) from common_aceptacioncondiciones where tenant_id = ?1 and version = ?2",
        )
            .setParameter(1, usuario.clinicaId)
            .setParameter(2, VERSION)
            .singleResult as Number
        return cuantas.toLong() == 0L
    }
}
