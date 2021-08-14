package org.openstreetmap.josm.plugins.dl.russiaaddresshelper

import org.apache.commons.text.StringEscapeUtils
import org.openstreetmap.josm.actions.mapmode.MapMode
import org.openstreetmap.josm.command.AddCommand
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.util.KeyPressReleaseListener
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EgrnQuery
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import org.openstreetmap.josm.tools.Logging
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.SwingUtilities

class RussiaAddressHelperPluginClickAction : MapMode(ACTION_NAME, ICON_NAME, null, ImageProvider.getCursor("crosshair", "create_note")), KeyPressReleaseListener {

    companion object {
        val ACTION_NAME = I18n.tr("By click")
        val ICON_NAME = "click.svg"
    }

    override fun enterMode() {
        super.enterMode()
        val map = MainApplication.getMap()
        map.mapView.addMouseListener(this)
        map.keyDetector.addKeyListener(this)
    }

    override fun exitMode() {
        super.exitMode()
        val map = MainApplication.getMap()
        map.mapView.removeMouseListener(this)
        map.keyDetector.removeKeyListener(this)
    }

    override fun mouseClicked(e: MouseEvent) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return
        }

        val map = MainApplication.getMap()
        map.selectMapMode(map.mapModeSelect)

        val mapView = map.mapView

        if (!mapView.isActiveLayerDrawable) {
            return
        }

        val ds = layerManager.editDataSet
        val cmds: MutableList<Command> = mutableListOf()
        val mouseEN = mapView.getEastNorth(e.x, e.y)
        val n = Node(mouseEN)

        val httpResponse = EgrnQuery(mouseEN).httpClient.connect()

        if (httpResponse.responseCode == 200) {
            val match = Regex("""address":\s"(.+?)"""").find(StringEscapeUtils.unescapeJson(httpResponse.contentReader.readText()))
            if (match == null) {
                Logging.error("Parse EGRN response error.")
            } else {
                val address = match.groupValues[1]

                n.put("addr:RU:egrn", address)
                n.put("fixme", "yes")

                cmds.add(AddCommand(ds, n))

                val c: Command = SequenceCommand(I18n.tr("Added node from RussiaAddressHelper"), cmds)
                UndoRedoHandler.getInstance().add(c)

                ds.setSelected(n)
            }

        }
    }

    override fun doKeyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ESCAPE) {
            val map = MainApplication.getMap()
            map.selectMapMode(map.mapModeSelect)
        }
    }

    override fun doKeyReleased(e: KeyEvent?) {
        // Do nothing
    }
}