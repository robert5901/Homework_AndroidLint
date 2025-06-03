package ru.otus.homework.lintchecks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Document

private const val ID = "RawColorUsage"

private const val BRIEF_DESCRIPTION = "Используйте цвета описанные в colors.xml"
private const val EXPLANATION = "Используйте цвета описанные в colors.xml"

private const val PRIORITY = 6

private val USED_COLOR_ATTRIBUTES = listOf("color", "fillColor", "tint", "background", "")

data class RawColorUsage(val context: XmlContext, val location: Location, val color: String)

class RawColorUsageDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE = Issue.create(
            ID,
            BRIEF_DESCRIPTION,
            EXPLANATION,
            Category.CORRECTNESS,
            PRIORITY,
            Severity.WARNING,
            Implementation(RawColorUsageDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    private val colorPalette = mutableMapOf<String, String>()
    private val rawColorUsages = mutableSetOf<RawColorUsage>()

    override fun afterCheckRootProject(context: Context) {
        rawColorUsages.forEach {
            val colorName = if (it.color.startsWith("#"))
                findColorName(it.color)
            else null

            if (colorName == null) {
                it.context.report(
                    ISSUE,
                    it.location,
                    BRIEF_DESCRIPTION
                )
            } else {
                it.context.report(
                    ISSUE,
                    it.location,
                    BRIEF_DESCRIPTION,
                    createHardcodedColorFix(it.color, colorName)
                )
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType) =
        folderType in setOf(
            ResourceFolderType.DRAWABLE,
            ResourceFolderType.LAYOUT,
            ResourceFolderType.COLOR,
            ResourceFolderType.VALUES
        )

    override fun getApplicableAttributes(): List<String> = XmlScannerConstants.ALL

    override fun visitDocument(context: XmlContext, document: Document) {
        if (context.file.path.endsWith("res/values/colors.xml")
            && document.documentElement.nodeName == "resources"
        ) {
            val colorNodes = document.documentElement.childNodes
            for (i in 0 until colorNodes.length) {
                val node = colorNodes.item(i)
                if (node.nodeName == "color") {
                    val colorName = node.attributes.getNamedItem("name")?.nodeValue ?: continue
                    val colorValue = node.childNodes.item(0).nodeValue ?: continue
                    colorPalette[colorName] = colorValue
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attr: Attr) {
        val value = attr.value.orEmpty()
        if (attr.localName in USED_COLOR_ATTRIBUTES
            && (value.startsWith("#") || value.startsWith("@android:color/"))
        ) {
            rawColorUsages.add(RawColorUsage(context, context.getLocation(attr), value))
        }
    }

    private fun createHardcodedColorFix(value: String, colorName: String): LintFix {
        return LintFix.create()
            .replace()
            .text(value)
            .with("@color/$colorName")
            .build()
    }

    private fun findColorName(colorValue: String): String? {
        return colorPalette.filterValues { it == colorValue }.map { it.key }.firstOrNull()
    }

}
