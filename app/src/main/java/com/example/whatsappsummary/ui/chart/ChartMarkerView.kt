package com.example.whatsappsummary.ui.chart

import android.content.Context
import android.view.View
import android.widget.TextView
import com.example.whatsappsummary.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

/**
 * Tooltip personalizado para PieChart y LineChart.
 * Se ancla sobre el punto seleccionado con diseño plano y paleta pastel.
 *
 * @param provider recibe el Entry seleccionado y devuelve (título, valor, pista opcional).
 */
class ChartMarkerView(
    context: Context,
    private val provider: (Entry?, Highlight?) -> MarkerContent
) : MarkerView(context, R.layout.marker_chart) {

    data class MarkerContent(
        val title: String,
        val value: String,
        val hint: String? = null
    )

    private val titleView: TextView = findViewById(R.id.markerTitle)
    private val valueView: TextView = findViewById(R.id.markerValue)
    private val hintView: TextView = findViewById(R.id.markerHint)
    private val offset = MPPointF()

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val content = provider(e, highlight)
        titleView.text = content.title
        valueView.text = content.value
        if (content.hint.isNullOrBlank()) {
            hintView.visibility = View.GONE
        } else {
            hintView.visibility = View.VISIBLE
            hintView.text = content.hint
        }
        super.refreshContent(e, highlight)
    }

    /**
     * Ubica el tooltip centrado sobre el punto, y lo clampea a los bordes del
     * chart para que nunca se corte. Si el punto está muy arriba (no cabe por
     * encima), lo mueve por debajo del punto.
     */
    override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
        // Base: centrado horizontal, encima del punto.
        offset.x = -(width / 2f)
        offset.y = -height.toFloat()

        val chart = chartView ?: return offset
        val chartW = chart.width.toFloat()
        val chartH = chart.height.toFloat()
        val margin = EDGE_MARGIN_PX

        // Clamp horizontal: si se sale por la izquierda o derecha, pegar al margen.
        val left = posX + offset.x
        val right = left + width
        if (left < margin) {
            offset.x += margin - left
        } else if (right > chartW - margin) {
            offset.x -= right - (chartW - margin)
        }

        // Clamp vertical: si no cabe por encima, mostrar por debajo del punto.
        val top = posY + offset.y
        if (top < margin) {
            offset.y = VERTICAL_GAP_PX
        } else {
            val bottom = top + height
            if (bottom > chartH - margin) {
                offset.y -= bottom - (chartH - margin)
            }
        }
        return offset
    }

    companion object {
        private const val EDGE_MARGIN_PX = 12f
        private const val VERTICAL_GAP_PX = 12f
    }
}
