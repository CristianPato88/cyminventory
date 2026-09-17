package com.cym.inventory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal enum class ItemStatus { PLANNED, PURCHASED }

internal data class InventoryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val room: String = "",
    val category: String = "",
    val status: ItemStatus = ItemStatus.PLANNED,
    val priceCents: Long? = null,
    val purchaseDate: String? = null,
    val receiptMime: String? = null,
    val photoMime: String? = null,
    val shop: String = "",
    val description: String = "",
    val location: String = "",
    val purpose: String = "",
    val paidBy: String = "",
    val paidById: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

internal data class WishlistItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String = "",
    val shop: String = "",
    val location: String = "",
    val priceCents: Long? = null,
    val notes: String = "",
    val addedBy: String = "",
    val photoMime: String? = null,
    val addedDate: String = LocalDate.now().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Local file cache for photos/receipts (keyed by item id) plus the legacy on-device JSON, kept only to migrate old data into a household. */
internal class InventoryStore(private val context: Context) {
    private val legacyDataFile = File(context.filesDir, "inventory-v1.json")
    private val legacyWishlistFile = File(context.filesDir, "wishlist-v1.json")

    // ---------- Legacy local JSON (pre-Firestore), used once to migrate into a household ----------

    fun loadLegacyItems(): List<InventoryItem> = runCatching {
        if (!legacyDataFile.exists()) return emptyList()
        val data = JSONArray(legacyDataFile.readText())
        (0 until data.length()).map { index ->
            val value = data.getJSONObject(index)
            val id = value.getString("id")
            val oldReceipt = value.optString("receiptFile").takeIf(String::isNotBlank)
            val oldPhoto = value.optString("photoFile").takeIf(String::isNotBlank)
            InventoryItem(
                id = id,
                name = value.getString("name"),
                room = value.optString("room"),
                category = value.optString("category"),
                status = ItemStatus.valueOf(value.optString("status", "PLANNED")),
                priceCents = if (value.isNull("priceCents")) null else value.getLong("priceCents"),
                purchaseDate = value.optString("purchaseDate").takeIf(String::isNotBlank),
                receiptMime = oldReceipt?.let { migrateLegacyFile(it, "receipts", id) },
                photoMime = oldPhoto?.let { migrateLegacyFile(it, "photos", id) },
                shop = value.optString("shop"),
                description = value.optString("description"),
                location = value.optString("location"),
                purpose = value.optString("purpose"),
            )
        }
    }.getOrElse { emptyList() }

    fun loadLegacyWishlist(): List<WishlistItem> = runCatching {
        if (!legacyWishlistFile.exists()) return emptyList()
        val data = JSONArray(legacyWishlistFile.readText())
        (0 until data.length()).map { index ->
            val value = data.getJSONObject(index)
            val id = value.getString("id")
            val oldPhoto = value.optString("photoFile").takeIf(String::isNotBlank)
            WishlistItem(
                id = id,
                name = value.getString("name"),
                url = value.optString("url"),
                shop = value.optString("shop"),
                location = value.optString("location"),
                priceCents = if (value.isNull("priceCents")) null else value.getLong("priceCents"),
                notes = value.optString("notes"),
                addedBy = value.optString("addedBy"),
                photoMime = oldPhoto?.let { migrateLegacyFile(it, "wishlist-photos", id) },
                addedDate = value.optString("addedDate").takeIf(String::isNotBlank) ?: LocalDate.now().toString(),
            )
        }
    }.getOrElse { emptyList() }

    private fun migrateLegacyFile(oldFileName: String, subdir: String, newId: String): String? {
        val oldFile = File(context.filesDir, "$subdir/$oldFileName")
        if (!oldFile.exists()) return null
        val mime = mimeTypeFor(oldFileName)
        val newFile = File(context.filesDir, "$subdir/$newId.${extensionFor(mime)}")
        return runCatching { oldFile.copyTo(newFile, overwrite = true); mime }.getOrNull()
    }

    // ---------- Local media cache (deterministic path: {subdir}/{id}.{ext}) ----------

    private fun extensionFor(mime: String?): String = when (mime) {
        "application/pdf" -> "pdf"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    private fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.').lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun mediaFile(subdir: String, id: String, mime: String): File = File(context.filesDir, "$subdir/$id.${extensionFor(mime)}")

    /** Appends the file's last-modified time so Compose sees a new Uri (and reloads the thumbnail) whenever a photo at this same path is replaced. */
    private fun toFileProviderUri(file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        .buildUpon().appendQueryParameter("t", file.lastModified().toString()).build()

    fun photoUri(item: InventoryItem): Uri? = item.photoMime?.let { mediaFile("photos", item.id, it) }?.takeIf(File::exists)?.let(::toFileProviderUri)
    fun receiptUri(item: InventoryItem): Uri? = item.receiptMime?.let { mediaFile("receipts", item.id, it) }?.takeIf(File::exists)?.let(::toFileProviderUri)
    fun wishlistPhotoUri(item: WishlistItem): Uri? = item.photoMime?.let { mediaFile("wishlist-photos", item.id, it) }?.takeIf(File::exists)?.let(::toFileProviderUri)
    fun memberPhotoUri(member: Member): Uri? = member.photoMime?.let { mediaFile("member-photos", member.id, it) }?.takeIf(File::exists)?.let(::toFileProviderUri)
    fun receiptMimeType(item: InventoryItem): String = item.receiptMime ?: "image/jpeg"

    /** Copies a picked content Uri into the local cache at a deterministic path and returns its MIME type. */
    fun cachePickedFile(uri: Uri, subdir: String, id: String): String {
        val type = context.contentResolver.getType(uri) ?: "image/jpeg"
        val directory = File(context.filesDir, subdir).apply { mkdirs() }
        val file = File(directory, "$id.${extensionFor(type)}")
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
            ?: error("Archivo no disponible")
        return type
    }

    fun deleteLocalMedia(subdir: String, id: String) {
        File(context.filesDir, subdir).listFiles { f -> f.nameWithoutExtension == id }?.forEach { it.delete() }
    }

    /** Decodes a base64 payload from Firestore into the local cache, if not already cached on this device. */
    fun materializeFromBase64(subdir: String, id: String, mime: String, base64: String) {
        val file = mediaFile(subdir, id, mime)
        if (file.exists()) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(Base64.decode(base64, Base64.NO_WRAP))
        }
    }

    /** Compresses (images) or reads (PDFs) the cached file for upload; null if it can't fit the free Firestore document budget. */
    fun prepareUpload(subdir: String, id: String, mime: String, maxBytes: Int = 650_000): String? {
        val file = mediaFile(subdir, id, mime)
        if (!file.exists()) return null
        val bytes = if (mime == "application/pdf") file.readBytes().takeIf { it.size <= maxBytes }
            else compressImage(file, maxBytes)
        return bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun compressImage(file: File, maxBytes: Int): ByteArray? {
        var sample = 1
        repeat(4) {
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
            var quality = 85
            while (quality >= 30) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val bytes = out.toByteArray()
                if (bytes.size <= maxBytes) return bytes
                quality -= 20
            }
            sample *= 2
        }
        return null
    }

    /** Exports the full inventory as a semicolon-separated CSV that Excel opens directly. */
    fun exportCsv(items: List<InventoryItem>): Uri {
        val directory = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(directory, "cyminventory-${System.currentTimeMillis()}.csv")
        val header = listOf(
            "Nombre", "Estado", "Habitación", "Categoría", "Precio (€)", "Fecha de compra",
            "Tienda", "Ubicación", "Para qué sirve", "Descripción", "Ticket", "Foto",
        )
        val rows = items.map { item ->
            listOf(
                item.name,
                if (item.status == ItemStatus.PURCHASED) "Comprado" else "Por comprar",
                item.room,
                item.category,
                item.priceCents?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: "",
                item.purchaseDate ?: "",
                item.shop,
                item.location,
                item.purpose,
                item.description,
                if (item.receiptMime != null) "Sí" else "No",
                if (item.photoMime != null) "Sí" else "No",
            )
        }
        val csv = buildString {
            append(header.joinToString(";", postfix = "\n") { csvEscape(it) })
            rows.forEach { row -> append(row.joinToString(";", postfix = "\n") { csvEscape(it) }) }
        }
        file.writeText("﻿$csv")
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ';' || it == '"' || it == '\n' }) "\"$escaped\"" else escaped
    }
}
