package edu.uade.enviosg1.data

import edu.uade.enviosg1.model.CategoriaHistorial
import edu.uade.enviosg1.model.EstadoOrden
import edu.uade.enviosg1.model.EventoHistorial
import edu.uade.enviosg1.model.FaseEntrega
import edu.uade.enviosg1.model.OrdenRobot
import java.util.UUID

internal fun EstadoSimulador.eventosDesde(anterior: EstadoSimulador, horaMs: Long): List<EventoHistorial> =
    buildList {
        val actual = entrega?.datos
        val previa = anterior.entrega?.datos?.takeIf { it.numero == actual?.numero }
        val codigo = actual?.codigo ?: anterior.entrega?.datos?.codigo

        fun registrar(categoria: CategoriaHistorial, titulo: String, detalle: String, instanteMs: Long = horaMs) {
            add(EventoHistorial(UUID.randomUUID().toString(), instanteMs, categoria, codigo, titulo, detalle))
        }

        val respuesta = ultimaRespuesta
        if (respuesta != null && respuesta != anterior.ultimaRespuesta) {
            registrar(
                CategoriaHistorial.Orden,
                "${respuesta.estado.etiqueta} · ${respuesta.orden.titulo}",
                respuesta.detalle,
                respuesta.horaMs
            )
            if (respuesta.orden == OrdenRobot.Iniciar && respuesta.estado == EstadoOrden.Confirmada) {
                registrar(
                    CategoriaHistorial.Mision,
                    "Salida de Recepción",
                    "La entrega comenzó hacia ${actual?.destino?.nombre.orEmpty()}.",
                    respuesta.horaMs
                )
            }
        }

        actual?.retiroConfirmadoMs?.takeIf { it != previa?.retiroConfirmadoMs }?.let { retiro ->
            registrar(CategoriaHistorial.Mision, "Retiro confirmado", "Paquete retirado en ${actual.destino?.nombre.orEmpty()}.", retiro)
        }
        actual?.retiroVencidoMs?.takeIf { it != previa?.retiroVencidoMs }?.let { vencimiento ->
            registrar(CategoriaHistorial.Mision, "No retirado", "Venció el plazo con el paquete dentro del G1.", vencimiento)
        }

        val fase = actual?.fase
        if (fase in fasesEnRecepcion && previa?.fase !in fasesEnRecepcion) {
            registrar(
                CategoriaHistorial.Mision,
                "Regreso a Recepción",
                if (entrega?.conPaquete == true) "El G1 volvió con el paquete." else "El G1 volvió sin carga.",
                entrega?.finRegresoEpochMs ?: horaMs
            )
        }
        if (previa?.fase == FaseEntrega.DescargaEnRecepcion && fase == FaseEntrega.Finalizada) {
            registrar(CategoriaHistorial.Mision, "Descarga en Recepción", "El paquete se descargó en la base.")
        }
        if (fase == FaseEntrega.Cancelada && previa?.fase != FaseEntrega.Cancelada) {
            registrar(CategoriaHistorial.Mision, "Misión cancelada", "El G1 quedó inmóvil en su ubicación.")
        }

        val estabaConectado = anterior.lecturaAntesDelCorte == null
        val conectado = lecturaAntesDelCorte == null
        if (estabaConectado && !conectado) {
            registrar(CategoriaHistorial.Contingencia, "Pérdida de enlace", "La última lectura quedó desactualizada.")
        } else if (!estabaConectado && conectado) {
            registrar(CategoriaHistorial.Contingencia, "Conexión recuperada", "Se recibió una lectura actualizada del G1.")
        }
        if (bateriaForzada && !anterior.bateriaForzada) {
            registrar(CategoriaHistorial.Contingencia, "Batería baja", "La batería llegó al 20 %.")
        } else if (!bateriaForzada && anterior.bateriaForzada) {
            registrar(CategoriaHistorial.Contingencia, "Batería restablecida", "La batería volvió a su nivel simulado normal.")
        }
        if (trayectoBloqueado && !anterior.trayectoBloqueado) {
            registrar(CategoriaHistorial.Contingencia, "Obstáculo en el trayecto", "El G1 pausó la marcha.")
        } else if (!trayectoBloqueado && anterior.trayectoBloqueado) {
            registrar(CategoriaHistorial.Contingencia, "Obstáculo retirado", "El trayecto quedó libre.")
        }
    }

private val fasesEnRecepcion = setOf(FaseEntrega.DescargaEnRecepcion, FaseEntrega.Finalizada)
