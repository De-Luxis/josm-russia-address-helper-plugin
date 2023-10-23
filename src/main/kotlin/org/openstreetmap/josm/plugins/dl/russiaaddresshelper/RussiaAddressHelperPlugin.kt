package org.openstreetmap.josm.plugins.dl.russiaaddresshelper

import org.openstreetmap.josm.actions.UploadAction
import org.openstreetmap.josm.data.Version
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.OsmValidator
import org.openstreetmap.josm.data.validation.ValidationTask
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.MapFrame
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.preferences.PreferenceSetting
import org.openstreetmap.josm.plugins.Plugin
import org.openstreetmap.josm.plugins.PluginInformation
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions.ClickAction
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions.SelectAction
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EgrnApi
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsedAddressInfo
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.PluginSetting
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks.EGRNCleanPluginCache
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks.EGRNUploadTagFilter
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.*
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import org.openstreetmap.josm.tools.Logging
import javax.swing.JMenu
import javax.swing.JOptionPane

class RussiaAddressHelperPlugin(info: PluginInformation) : Plugin(info) {
    init {
        menuInit(MainApplication.getMenu().dataMenu)

        versionInfo = info.version
    }

    companion object {
        val ACTION_NAME = I18n.tr("Russia address helper")!!
        val ICON_NAME = "icon.svg"

        lateinit var versionInfo: String

        var egrnResponses: MutableMap<OsmPrimitive, Triple<EastNorth?, EGRNResponse, ParsedAddressInfo>> = mutableMapOf()
        var ignoredValidators: MutableMap<OsmPrimitive, MutableSet<EGRNTestCode>> = mutableMapOf()

        val egrnUploadTagFilterHook : EGRNUploadTagFilter = EGRNUploadTagFilter()
        val cleanPluginCacheHook : EGRNCleanPluginCache = EGRNCleanPluginCache()
        //debug tool, to get any buildings which were processed by plugin but not validated
        var processedByValidators: MutableMap<OsmPrimitive, MutableSet<EGRNTestCode>> = mutableMapOf()

        var totalRequestsPerSession = 0L
        var totalSuccessRequestsPerSession = 0L

        val selectAction: SelectAction = SelectAction()
        val clickAction: ClickAction = ClickAction()

        fun getEgrnClient(): EgrnApi {
            val userAgent = String.format(
                EgrnSettingsReader.EGRN_REQUEST_USER_AGENT.get(),
                Version.getInstance().versionString,
                versionInfo
            )

            return EgrnApi(EgrnSettingsReader.EGRN_URL_REQUEST.get(), userAgent)
        }

        fun ignoreValidator(primitive: OsmPrimitive, code: EGRNTestCode) {
            val ignoredValidators = ignoredValidators[primitive]
            if (ignoredValidators == null || ignoredValidators.isEmpty()) {
                RussiaAddressHelperPlugin.ignoredValidators[primitive] = mutableSetOf(code)
            } else {
                ignoredValidators.add(code)
                RussiaAddressHelperPlugin.ignoredValidators[primitive] = ignoredValidators
            }
        }

        fun ignoreAllValidators(primitive: OsmPrimitive) {
            ignoredValidators[primitive] = EGRNTestCode.values().toMutableSet()
        }


        fun isIgnored(primitive: OsmPrimitive, code: EGRNTestCode): Boolean {
            val ignoredValidators = ignoredValidators[primitive]
            return ignoredValidators != null && ignoredValidators.contains(code)
        }

        fun markAsProcessed(primitive: OsmPrimitive, code: EGRNTestCode) {
            val processedByValidators = RussiaAddressHelperPlugin.processedByValidators[primitive]
            if (processedByValidators == null || processedByValidators.isEmpty()) {
                    RussiaAddressHelperPlugin.processedByValidators[primitive] = mutableSetOf(code)
            } else {
                processedByValidators.add(code)
                RussiaAddressHelperPlugin.processedByValidators[primitive] = processedByValidators
            }
        }

        fun removeFromAllCaches (primitive: OsmPrimitive) {
            if (egrnResponses[primitive]!=null) {
                egrnResponses.remove(primitive)
            }
            if (ignoredValidators[primitive] != null) {
                ignoredValidators.remove(primitive)
            }
            if (processedByValidators[primitive]!=null) {
                processedByValidators.remove(primitive)
            }
        }

        fun emptyAllCaches() {
                egrnResponses.clear()
                ignoredValidators.clear()
                processedByValidators.clear()
        }

        fun getUnprocessedEntities() {
            val unprocessed = egrnResponses.keys.minus(processedByValidators.keys).minus(ignoredValidators.keys)
            if (unprocessed.isNotEmpty()) {
                val egrnResponses = egrnResponses.filter { unprocessed.contains(it.key) }
                Notification("Не обработано ни одним валидатором ${unprocessed.size} обьектов").setIcon(JOptionPane.WARNING_MESSAGE)
                    .show()

            }
        }

        fun runEgrnValidation(selection : Collection<OsmPrimitive?>) {
            val map = MainApplication.getMap()
            if (map == null || !map.isVisible) return

            OsmValidator.initializeTests()

            //лучше бы фильтровать более надежным методом, но я его не придумал
            val egrnTests = OsmValidator.getEnabledTests(false)
                .filter { test -> test.name.contains("ЕГРН") || test.name.contains("EGRN")}
            if (egrnTests.isEmpty()) return

            MainApplication.worker.submit(ValidationTask(egrnTests, selection, null))
        }

        fun cleanFromDoubles(primitives: MutableList<OsmPrimitive>): Set<OsmPrimitive> {
            //получает на вход мутабельный список примитивов, которым хотим присвоить адрес.
            //удаляем из него все примитивы, для которых есть дубликат адреса в ОСМ или среди них самих
            //возвращаем список дублей
            val needToAssignAddressPrimitives = primitives.filter { egrnResponses[it]!=null
                    && egrnResponses[it]?.third?.getPreferredAddress() != null
                    && !it.hasKey("addr:housenumber") }
            val needToAssignAddressPrimitivesMap = needToAssignAddressPrimitives.groupBy { getParsedInlineAddress(it) }

            val doubleAddressPrimitives : MutableSet<OsmPrimitive> = mutableSetOf()
            val osmBuildingsWithAddress = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives()
                .filter { p ->
                    p !is Node && p.hasKey("building") && p.hasKey("addr:housenumber") && (p.hasKey("addr:street") || p.hasKey(
                        "addr:place"
                    ))
                }
            val osmBuildingsAddressMap = osmBuildingsWithAddress.groupBy { getOsmInlineAddress(it) }

            needToAssignAddressPrimitivesMap.forEach{(address, listToProcess) ->
                if (listToProcess.isEmpty()) return@forEach
                if (osmBuildingsAddressMap.containsKey(address)) {
                    //уже есть дубль в данных ОСМ
                    primitives.removeAll(listToProcess)
                    doubleAddressPrimitives.addAll(listToProcess)
                    return@forEach}

                val assignToPrimitive = listToProcess.filterIsInstance<Way>().maxByOrNull { Geometry.computeArea(it) }
                if (assignToPrimitive == null) {
                    Logging.error("EGRN PLUGIN Something went wrong when finding doubles, building has no area")
                    primitives.removeAll(listToProcess)
                    return@forEach
                }
                val doubles = listToProcess.minus(assignToPrimitive)
                primitives.removeAll(doubles)
                doubleAddressPrimitives.addAll(doubles)
            }
            doubleAddressPrimitives.forEach { markAsProcessed(it, EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND) }
            return doubleAddressPrimitives
        }

        private fun getParsedInlineAddress(primitive: OsmPrimitive) :String {
            val prefAddress = egrnResponses[primitive]?.third?.getPreferredAddress() ?: return ""
            return prefAddress.getOsmAddress().getInlineAddress(",") ?: ""
        }


        private fun getOsmInlineAddress(p: OsmPrimitive): String {
        return if (p.hasKey("addr:street")) {
            "${p["addr:street"]}, ${p["addr:housenumber"]}"
        } else {
            "${p["addr:place"]}, ${p["addr:housenumber"]}"
        }
    }

    }
    override fun getPreferenceSetting(): PreferenceSetting {
        return PluginSetting()
    }

    override fun mapFrameInitialized(oldFrame: MapFrame?, newFrame: MapFrame?) {

        OsmValidator.addTest(EGRNEmptyResponseTest::class.java)
        OsmValidator.addTest(EGRNFuzzyStreetMatchingTest::class.java)
        OsmValidator.addTest(EGRNInitialsStreetMatchingTest::class.java)
        OsmValidator.addTest(EGRNMultipleValidAddressTest::class.java)
        OsmValidator.addTest(EGRNStreetNotFoundTest::class.java)
        OsmValidator.addTest(EGRNAddressAddedTest::class.java)
        OsmValidator.addTest(EGRNCantParseAddressTest::class.java)
        OsmValidator.addTest(EGRNFlatsInAddressTest::class.java)
        OsmValidator.addTest(EGRNPlaceNotFoundTest::class.java)
        OsmValidator.addTest(EGRNFuzzyOrInitialsPlaceMatchTest::class.java)
        OsmValidator.addTest(EGRNDuplicateAddressesTest::class.java)
        OsmValidator.addTest(EGRNStreetOrPlaceTooFarTest::class.java)

        UploadAction.registerUploadHook(cleanPluginCacheHook, true)
        UploadAction.registerUploadHook(egrnUploadTagFilterHook, true)

    }

    private fun menuInit(menu: JMenu) {
        menu.isVisible = true

        if (menu.itemCount > 0) {
            menu.addSeparator()
        }

        val subMenu = JMenu(ACTION_NAME)
        subMenu.icon =
            ImageProvider(ICON_NAME).resource.getPaddedIcon(ImageProvider.ImageSizes.SMALLICON.imageDimension)

        subMenu.add(selectAction)
        subMenu.add(clickAction)

        menu.add(subMenu)
    }

}