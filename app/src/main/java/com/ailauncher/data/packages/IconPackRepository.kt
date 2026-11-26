package com.ailauncher.data.packages

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** An installed icon pack (an app that answers one of the common icon pack intents). */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * Icon packs in the format most launchers read (ADW, Nova, Lawnchair): the pack's `appfilter.xml`
 * maps `ComponentInfo{package/activity}` to a drawable name in the pack. Apps the pack doesn't
 * cover can be drawn on the pack's own backs (`iconback`, `iconmask`, `iconupon`, `scale`), so the
 * grid still looks like one set.
 *
 * Everything is read from the pack's resources on the phone; nothing is downloaded.
 */
@Singleton
class IconPackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager
    private val mutex = Mutex()
    private var loaded: LoadedPack? = null

    /** Installed icon packs, sorted by name. */
    suspend fun installedPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        PACK_INTENTS.flatMap { action ->
            runCatching { packageManager.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        }
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    IconPackInfo(pkg, packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString())
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * The pack's icon for [component], or a [PackIcon.Composed] recipe to draw the app's own icon
     * on the pack's back, or null when the pack has neither (or is no longer installed).
     */
    suspend fun iconFor(packPackage: String, component: ComponentName, density: Int): PackIcon? {
        val pack = pack(packPackage) ?: return null
        val name = pack.icons[component.flattenToString()] ?: pack.icons[component.packageName]
        if (name != null) {
            pack.drawable(name, density)?.let { return PackIcon.Mapped(it) }
        }
        if (pack.backs.isEmpty()) return null
        // Pick a back per app, so it stays the same between launches.
        val back = pack.drawable(pack.backs[Math.floorMod(component.packageName.hashCode(), pack.backs.size)], density) ?: return null
        return PackIcon.Composed(
            back = back,
            mask = pack.mask?.let { pack.drawable(it, density) },
            upon = pack.upon?.let { pack.drawable(it, density) },
            scale = pack.scale,
        )
    }

    private suspend fun pack(packPackage: String): LoadedPack? = mutex.withLock {
        loaded?.takeIf { it.packageName == packPackage }?.let { return it }
        val pack = withContext(Dispatchers.IO) { load(packPackage) }
        loaded = pack
        pack
    }

    private fun load(packPackage: String): LoadedPack? {
        val resources = try {
            packageManager.getResourcesForApplication(packPackage)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val icons = HashMap<String, String>()
        val backs = ArrayList<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f
        val parsed = runCatching {
            withAppFilter(resources, packPackage) { parser ->
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "item" -> {
                                val drawable = parser.getAttributeValue(null, "drawable")
                                val key = parser.getAttributeValue(null, "component")?.let(::componentKey)
                                if (drawable != null && key != null) icons.putIfAbsent(key, drawable)
                            }
                            "iconback" -> for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).startsWith("img")) backs += parser.getAttributeValue(i)
                            }
                            "iconmask" -> mask = parser.getAttributeValue(null, "img1")
                            "iconupon" -> upon = parser.getAttributeValue(null, "img1")
                            "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.coerceIn(0.3f, 1.2f) ?: 1f
                        }
                    }
                    event = parser.next()
                }
            }
        }
        if (parsed.isFailure) Log.w(TAG, "Could not read appfilter of $packPackage", parsed.exceptionOrNull())
        return LoadedPack(packPackage, resources, icons, backs, mask, upon, scale)
    }

    /** Opens `res/xml/appfilter.xml`, or `assets/appfilter.xml` for packs that ship it there. */
    @SuppressLint("DiscouragedApi") // Icon packs are only addressable by resource name.
    private fun withAppFilter(resources: Resources, packPackage: String, read: (XmlPullParser) -> Unit) {
        val id = resources.getIdentifier("appfilter", "xml", packPackage)
        if (id != 0) {
            val parser: XmlResourceParser = resources.getXml(id)
            try {
                read(parser)
            } finally {
                parser.close()
            }
            return
        }
        val stream: InputStream = resources.assets.open("appfilter.xml")
        stream.use {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(it, null)
            read(parser)
        }
    }

    private class LoadedPack(
        val packageName: String,
        val resources: Resources,
        /** "package/activity" (or just "package") → drawable name. */
        val icons: Map<String, String>,
        val backs: List<String>,
        val mask: String?,
        val upon: String?,
        val scale: Float,
    ) {
        private val ids = HashMap<String, Int>()

        @SuppressLint("DiscouragedApi") // Icon packs are only addressable by resource name.
        fun drawable(name: String, density: Int): Drawable? {
            val id = synchronized(ids) { ids.getOrPut(name) { resources.getIdentifier(name, "drawable", packageName) } }
            if (id == 0) return null
            return runCatching { resources.getDrawableForDensity(id, density, null) }.getOrNull()
        }
    }

    companion object {
        private const val TAG = "IconPackRepository"

        /** Intents icon packs declare so launchers can find them. */
        val PACK_INTENTS = listOf(
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "app.lawnchair.icons.THEMED_ICON",
        )

        /**
         * "ComponentInfo{com.app/com.app.Main}" → "com.app/com.app.Main" (the form of
         * [ComponentName.flattenToString] with a full class name). Short class names (".Main")
         * are expanded. Null for malformed entries.
         */
        fun componentKey(raw: String): String? {
            val inner = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}").trim()
            val slash = inner.indexOf('/')
            if (slash == 0) return null
            if (slash < 0) return inner.takeIf { it.isNotEmpty() && ' ' !in it }
            val pkg = inner.substring(0, slash)
            val cls = inner.substring(slash + 1).takeIf { it.isNotEmpty() } ?: return pkg
            return "$pkg/${if (cls.startsWith(".")) pkg + cls else cls}"
        }
    }
}

/** What an icon pack has for one app. */
sealed interface PackIcon {
    /** The pack draws this app's icon itself. */
    data class Mapped(val drawable: Drawable) : PackIcon

    /** Draw the app's own icon, scaled by [scale], on [back], cut by [mask] and covered by [upon]. */
    data class Composed(val back: Drawable, val mask: Drawable?, val upon: Drawable?, val scale: Float) : PackIcon
}
