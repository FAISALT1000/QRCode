package com.clipqr.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Patterns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.File
import java.io.FileOutputStream

/** Sorts any text into a category automatically. */
object Categorizer {
    private val digitCode = Regex("^[0-9]{4,8}$")
    private val mixedCode = Regex("^(?=.*[0-9])(?=.*[A-Z])[A-Z0-9]{4,10}$")
    private val phone = Regex("^\\+?[0-9][0-9\\s\\-()]{6,19}$")
    private val date = Regex("^\\d{4}-\\d{1,2}-\\d{1,2}$")
    private val urlStart = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)

    fun categorize(raw: String): Category {
        val s = raw.trim()
        return when {
            s.startsWith("WIFI:", true) -> Category.WIFI
            s.startsWith("BEGIN:VCARD", true) || s.startsWith("MECARD:", true) -> Category.CONTACT
            s.startsWith("mailto:", true) || Patterns.EMAIL_ADDRESS.matcher(s).matches() -> Category.EMAIL
            s.startsWith("tel:", true) -> Category.PHONE
            digitCode.matches(s) || mixedCode.matches(s) -> Category.CODE
            phone.matches(s) && !date.matches(s) && s.count { it.isDigit() } >= 7 -> Category.PHONE
            s.none { it.isWhitespace() } && (urlStart.matches(s) ||
                (Patterns.WEB_URL.matcher(s).matches() && s.contains('.') && s.any { it.isLetter() })) -> Category.LINK
            else -> Category.TEXT
        }
    }
}

/** Simple offline checks for suspicious links (common QR scam signs). */
object LinkSafety {
    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "ow.ly",
        "rb.gy", "shorturl.at", "tiny.cc", "buff.ly", "rebrand.ly"
    )
    private val ip = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    fun normalize(raw: String): String {
        val s = raw.trim()
        return if (hasScheme.containsMatchIn(s)) s else "https://$s"
    }

    fun warnings(raw: String): List<String> {
        val uri = Uri.parse(normalize(raw))
        val host = uri.host?.lowercase() ?: return listOf("This link looks broken or unusual")
        val w = mutableListOf<String>()
        if (uri.scheme.equals("http", ignoreCase = true)) w += "Not encrypted (http instead of https)"
        if (ip.matches(host)) w += "Uses a raw IP address instead of a website name"
        if (host.split('.').any { it.startsWith("xn--") }) w += "Uses look-alike letters that can imitate a real site"
        if (host in shorteners) w += "Short link: the real destination is hidden"
        if (uri.encodedUserInfo != null) w += "Contains an '@' trick that can disguise the real site"
        if (uri.path?.lowercase()?.endsWith(".apk") == true) w += "Downloads an app file (.apk)"
        if (host.count { it == '.' } >= 4) w += "Unusually long website address"
        return w
    }
}

data class WifiInfo(val ssid: String, val password: String, val security: String, val hidden: Boolean)

object QrFormats {
    private fun esc(s: String) = buildString {
        s.forEach { c -> if (c in "\\;,\":") append('\\'); append(c) }
    }

    fun wifi(ssid: String, password: String, security: String, hidden: Boolean): String = buildString {
        append("WIFI:T:").append(security).append(';')
        append("S:").append(esc(ssid)).append(';')
        if (security != "nopass") append("P:").append(esc(password)).append(';')
        if (hidden) append("H:true;")
        append(';')
    }

    fun vcard(name: String, phone: String, email: String, org: String): String = buildString {
        append("BEGIN:VCARD\nVERSION:3.0\n")
        if (name.isNotBlank()) append("N:;").append(name.trim()).append(";;;\nFN:").append(name.trim()).append('\n')
        if (org.isNotBlank()) append("ORG:").append(org.trim()).append('\n')
        if (phone.isNotBlank()) append("TEL:").append(phone.trim()).append('\n')
        if (email.isNotBlank()) append("EMAIL:").append(email.trim()).append('\n')
        append("END:VCARD")
    }

    fun parseWifi(raw: String): WifiInfo? {
        if (!raw.startsWith("WIFI:", true)) return null
        val fields = mutableMapOf<String, String>()
        val cur = StringBuilder()
        var escaped = false
        for (c in raw.substring(5)) {
            when {
                escaped -> { cur.append(c); escaped = false }
                c == '\\' -> escaped = true
                c == ';' -> {
                    val part = cur.toString()
                    val i = part.indexOf(':')
                    if (i > 0) fields[part.substring(0, i).uppercase()] = part.substring(i + 1)
                    cur.clear()
                }
                else -> cur.append(c)
            }
        }
        // Note: escaped ':' inside values was kept as plain text above, so split on the first ':' is safe for keys.
        val ssid = fields["S"] ?: return null
        return WifiInfo(ssid, fields["P"].orEmpty(), fields["T"] ?: "nopass", fields["H"] == "true")
    }

    fun contactName(raw: String): String? {
        raw.lines().forEach { line ->
            if (line.startsWith("FN:", true)) return line.substring(3).trim()
        }
        if (raw.startsWith("MECARD:", true)) {
            raw.substring(7).split(';').forEach { p ->
                if (p.startsWith("N:", true)) return p.substring(2).replace(",", " ").trim()
            }
        }
        return null
    }
}

object Qr {
    fun encode(text: String, size: Int = 700): Bitmap? = try {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1)
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        BarcodeEncoder().createBitmap(matrix)
    } catch (e: Exception) {
        null
    }

    fun decode(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        return try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: Exception) {
            try {
                MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val max = maxOf(w, h)
                if (max > 2000) {
                    val scale = 2000f / max
                    decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }
}

/** Phone actions: copy, share, open, call, etc. */
object Actions {
    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    private fun start(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(ctx, "No app found to open this")
        }
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ClipQR", text))
        if (Build.VERSION.SDK_INT < 33) toast(ctx, "Copied")
    }

    fun shareText(ctx: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(ctx, Intent.createChooser(i, "Share"))
    }

    private fun sharedFile(ctx: Context, name: String): File {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        return File(dir, name)
    }

    private fun uriFor(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)

    fun shareBitmap(ctx: Context, bitmap: Bitmap) {
        val file = sharedFile(ctx, "qr-code.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val i = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uriFor(ctx, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(ctx, Intent.createChooser(i, "Share QR code"))
    }

    fun openUrl(ctx: Context, url: String) = start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun dial(ctx: Context, number: String) =
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.removePrefix("tel:").trim())))

    fun email(ctx: Context, address: String) =
        start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address.removePrefix("mailto:").trim())))

    fun wifiSettings(ctx: Context) = start(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))

    fun addContact(ctx: Context, vcard: String) {
        val file = sharedFile(ctx, "contact.vcf")
        file.writeText(vcard)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(ctx, file), "text/x-vcard")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(ctx, i)
    }
}
