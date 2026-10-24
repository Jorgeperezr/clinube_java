package com.clinube.finanzas

import com.clinube.comun.python.Python
import com.clinube.comun.seguridad.Permisos
import com.clinube.finanzas.ControladorDeHonorarios.Companion.datosSueltos
import com.clinube.finanzas.Finanzas.Companion.MESES
import com.clinube.finanzas.Finanzas.Companion.PERMISO
import com.clinube.finanzas.Finanzas.Companion.anioMes
import com.clinube.finanzas.Finanzas.Companion.consulta
import com.clinube.finanzas.Finanzas.Companion.detalle
import com.clinube.finanzas.Finanzas.Companion.error
import com.clinube.finanzas.Finanzas.Companion.errorInterno
import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate

/**
 * Lo que se paga a terceros (`ObligacionesView`: la planilla del IESS y las
 * retenciones al SRI) y los décimos acumulados (`DecimosView`). Registrar el
 * pago crea su egreso, que es lo que las marca como pagadas.
 */
@RestController
class ControladorDeObligaciones(
    private val permisos: Permisos,
    private val finanzas: Finanzas,
    private val calculos: CalculosDeFinanzas,
    private val egresos: ControladorDeEgresos,
) {

    companion object {
        val CATEGORIA_DE = mapOf("iess" to "sueldos", "sri" to "impuestos", "decimo_tercero" to "sueldos", "decimo_cuarto" to "sueldos")
    }

    /** `_forma_y_fecha`: la forma (por defecto, transferencia) y la fecha del pago (por defecto, hoy). */
    private fun formaYFecha(datos: JsonNode): Pair<String, LocalDate> {
        val forma = ControladorDeHonorarios.forma(datos) ?: error("Forma de pago desconocida.")
        val fecha = Finanzas.fecha(Finanzas.valor(datos, "fecha"), finanzas.hoy()) ?: errorInterno("fecha nula")
        return forma to fecha
    }

    /** GET /api/v1/finanzas/obligaciones/?mes=2026-10 — la planilla del IESS y las retenciones del mes. */
    @GetMapping("/api/v1/finanzas/obligaciones/")
    @Transactional
    fun ver(peticion: HttpServletRequest): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val (inicio, _) = Finanzas.mesDe(consulta(peticion).get("mes"), finanzas.hoy()) ?: return detalle("El mes va como 2026-10.")
        return ResponseEntity.ok(linkedMapOf("mes" to anioMes(inicio), "obligaciones" to calculos.obligaciones(inicio)))
    }

    /** POST /api/v1/finanzas/obligaciones/ {"tipo": "iess"|"sri", "mes", "fecha", "forma_pago"} — registra su pago. */
    @PostMapping("/api/v1/finanzas/obligaciones/")
    @Transactional
    fun pagar(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val datos = datosSueltos(cuerpo)
        val (inicio, _) = Finanzas.mesDe(Finanzas.valor(datos, "mes"), finanzas.hoy()) ?: return detalle("El mes va como 2026-10.")
        val tipo = Finanzas.valor(datos, "tipo")?.takeIf { it.isTextual }?.textValue()
        val o = calculos.obligaciones(inicio).firstOrNull { it["tipo"] == tipo } ?: return detalle("No hay nada que pagar de eso en ese mes.")
        if (o["pagado"] == true) return detalle("Ya está registrado. Si hay que corregirlo, anula ese egreso.")
        val (forma, fecha) = formaYFecha(datos)
        val id = finanzas.crearEgreso(
            fecha, finanzas.categoria(CATEGORIA_DE.getValue(tipo!!)), "${o["nombre"]} — ${MESES[inicio.monthValue - 1]} ${inicio.year}",
            BigDecimal(o["monto"] as String), forma, origen = tipo, periodo = inicio, proveedor = if (tipo == "iess") "IESS" else "SRI",
        )
        finanzas.auditar("pay_obligation", "Egreso", id, mapOf("tipo" to tipo, "mes" to anioMes(inicio)))
        return ResponseEntity.status(HttpStatus.CREATED).body(egresos.egreso(id))
    }

    /**
     * `DecimosView._datos`: el décimo y el año (por defecto, el actual).
     * `int()` de Python: un texto con decimales no vale, pero un número
     * decimal se trunca; una lista o un objeto revientan (500).
     */
    private fun tipoYAnio(fuente: JsonNode): Pair<String, Int> {
        val tipo = Finanzas.valor(fuente, "tipo")?.takeIf { it.isTextual }?.textValue()
        val crudo = Finanzas.valor(fuente, "anio")
        val anio: BigInteger? = when {
            !Python.verdad(crudo) -> BigInteger.valueOf(finanzas.hoy().year.toLong())
            crudo!!.isTextual -> Python.entero(crudo.textValue())
            crudo.isBoolean -> if (crudo.booleanValue()) BigInteger.ONE else BigInteger.ZERO
            crudo.isIntegralNumber -> crudo.bigIntegerValue()
            crudo.isFloatingPointNumber -> crudo.decimalValue().toBigInteger()
            else -> errorInterno("int() de un ${crudo.nodeType}")
        }
        if (tipo !in listOf("tercero", "cuarto") || anio == null || anio.signum() == 0 ||
            anio !in BigInteger.valueOf(2000)..BigInteger.valueOf(2100)
        ) error("Indica el décimo (tercero o cuarto) y el año.")
        return tipo!! to anio.toInt()
    }

    /** GET /api/v1/finanzas/decimos/?tipo=tercero|cuarto&anio=2026 — lo acumulado por persona. */
    @GetMapping("/api/v1/finanzas/decimos/")
    @Transactional
    fun decimos(peticion: HttpServletRequest): Map<String, Any?> {
        permisos.exigir(PERMISO)
        val (tipo, anio) = tipoYAnio(consulta(peticion))
        return calculos.decimos(tipo, anio)
    }

    /** POST /api/v1/finanzas/decimos/ {"tipo", "anio", "empleado"?, "fecha", "forma_pago"} — paga a uno o a todos. */
    @PostMapping("/api/v1/finanzas/decimos/")
    @Transactional
    fun pagarDecimos(@RequestBody(required = false) cuerpo: JsonNode?): ResponseEntity<Any> {
        permisos.exigir(PERMISO)
        val datos = datosSueltos(cuerpo)
        val (tipo, anio) = tipoYAnio(datos)
        val (forma, fecha) = formaYFecha(datos)
        val d = calculos.decimos(tipo, anio)
        val elegido = Finanzas.valor(datos, "empleado")
        @Suppress("UNCHECKED_CAST")
        val filas = (d["filas"] as List<Map<String, Any?>>).filter {
            it["pagado"] == false && (!Python.verdad(elegido) || it["empleado"] == Python.str(elegido!!))
        }
        if (filas.isEmpty()) return detalle("No queda nada pendiente de ese décimo.")
        val equipo = finanzas.equipo().map { it.id.toString() }.toSet()
        for (f in filas) {
            finanzas.crearEgreso(
                fecha, finanzas.categoria("sueldos"), "${d["nombre"]} $anio — ${f["nombre"]}", BigDecimal(f["monto"] as String), forma,
                profesional = (f["empleado"] as String).takeIf { it in equipo }?.let(java.util.UUID::fromString),
                origen = "decimo_$tipo", periodo = LocalDate.of(anio, 1, 1),
            )
        }
        val config = finanzas.configuracion()
        finanzas.auditar("pay_bonus", "ConfiguracionFinanzas", config.id, mapOf("tipo" to tipo, "anio" to anio, "personas" to filas.size))
        return ResponseEntity.status(HttpStatus.CREATED).body(calculos.decimos(tipo, anio))
    }
}
