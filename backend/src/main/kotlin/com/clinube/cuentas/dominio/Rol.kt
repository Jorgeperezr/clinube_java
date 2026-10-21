package com.clinube.cuentas.dominio

/**
 * Los seis roles del sistema. Se guardan como texto en `accounts_user.role`
 * con EXACTAMENTE estas claves: son las que viajan en el token y las que
 * el panel compara.
 */
object Rol {
    const val SUPERADMIN = "superadmin"
    const val ADMIN = "admin"
    const val RECEPCION = "reception"
    const val DOCTOR = "doctor"
    const val AUXILIAR = "auxiliary"
    const val PACIENTE = "patient"

    val TODOS = listOf(SUPERADMIN, ADMIN, RECEPCION, DOCTOR, AUXILIAR, PACIENTE)
}
