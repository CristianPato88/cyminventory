package com.cym.inventory

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
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
    val receiptFile: String? = null,
    val photoFile: String? = null,
    val shop: String = "",
    val description: String = "",
    val location: String = "",
    val purpose: String = "",
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
    val photoFile: String? = null,
    val addedDate: String = LocalDate.now().toString(),
)

/** Local data for the first Android milestone. Cloud synchronization will replace this store. */
internal class InventoryStore(private val context: Context) {
    private val dataFile = File(context.filesDir, "inventory-v1.json")
    private val wishlistFile = File(context.filesDir, "wishlist-v1.json")

    fun load(): List<InventoryItem> = runCatching {
        if (!dataFile.exists()) return emptyList()
        val data = JSONArray(dataFile.readText())
        (0 until data.length()).map { index ->
            val value = data.getJSONObject(index)
            InventoryItem(
                id = value.getString("id"),
                name = value.getString("name"),
                room = value.optString("room"),
                category = value.optString("category"),
                status = ItemStatus.valueOf(value.optString("status", "PLANNED")),
                priceCents = if (value.isNull("priceCents")) null else value.getLong("priceCents"),
                purchaseDate = value.optString("purchaseDate").takeIf(String::isNotBlank),
                receiptFile = value.optString("receiptFile").takeIf(String::isNotBlank),
                photoFile = value.optString("photoFile").takeIf(String::isNotBlank),
                shop = value.optString("shop"),
                description = value.optString("description"),
                location = value.optString("location"),
                purpose = value.optString("purpose"),
            )
        }
    }.getOrElse { emptyList() }

    fun save(items: List<InventoryItem>) {
        val data = JSONArray()
        items.forEach { item ->
            data.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("room", item.room)
                put("category", item.category)
                put("status", item.status.name)
                put("priceCents", item.priceCents)
                put("purchaseDate", item.purchaseDate)
                put("receiptFile", item.receiptFile)
                put("photoFile", item.photoFile)
                put("shop", item.shop)
                put("description", item.description)
                put("location", item.location)
                put("purpose", item.purpose)
            })
        }
        writeAtomically(dataFile, data.toString())
    }

    fun loadWishlist(): List<WishlistItem> = runCatching {
        if (!wishlistFile.exists()) return emptyList()
        val data = JSONArray(wishlistFile.readText())
        (0 until data.length()).map { index ->
            val value = data.getJSONObject(index)
            WishlistItem(
                id = value.getString("id"),
                name = value.getString("name"),
                url = value.optString("url"),
                shop = value.optString("shop"),
                location = value.optString("location"),
                priceCents = if (value.isNull("priceCents")) null else value.getLong("priceCents"),
                notes = value.optString("notes"),
                addedBy = value.optString("addedBy"),
                photoFile = value.optString("photoFile").takeIf(String::isNotBlank),
                addedDate = value.optString("addedDate").takeIf(String::isNotBlank) ?: LocalDate.now().toString(),
            )
        }
    }.getOrElse { emptyList() }

    fun saveWishlist(items: List<WishlistItem>) {
        val data = JSONArray()
        items.forEach { item ->
            data.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("url", item.url)
                put("shop", item.shop)
                put("location", item.location)
                put("priceCents", item.priceCents)
                put("notes", item.notes)
                put("addedBy", item.addedBy)
                put("photoFile", item.photoFile)
                put("addedDate", item.addedDate)
            })
        }
        writeAtomically(wishlistFile, data.toString())
    }

    private fun writeAtomically(target: File, content: String) {
        val pending = File(target.parentFile, "${target.name}.tmp")
        pending.writeText(content)
        check(pending.renameTo(target)) { "Could not save ${target.name}" }
    }

    private fun copyFile(uri: Uri, subdir: String): String {
        val directory = File(context.filesDir, subdir).apply { mkdirs() }
        val type = context.contentResolver.getType(uri)
        val extension = when (type) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use(input::copyTo)
        } ?: error("Archivo no disponible")
        return file.name
    }

    fun copyReceipt(uri: Uri): String = copyFile(uri, "receipts")
    fun copyPhoto(uri: Uri): String = copyFile(uri, "photos")
    fun copyWishlistPhoto(uri: Uri): String = copyFile(uri, "wishlist-photos")

    private fun fileUri(subdir: String, fileName: String?): Uri? = fileName?.let { name ->
        if (name != File(name).name) return@let null
        File(context.filesDir, "$subdir/$name").takeIf(File::exists)?.let {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it)
        }
    }

    fun receiptUri(item: InventoryItem): Uri? = fileUri("receipts", item.receiptFile)
    fun photoUri(item: InventoryItem): Uri? = fileUri("photos", item.photoFile)
    fun wishlistPhotoUri(item: WishlistItem): Uri? = fileUri("wishlist-photos", item.photoFile)

    fun receiptMimeType(item: InventoryItem): String = mimeTypeFor(item.receiptFile)

    private fun mimeTypeFor(fileName: String?): String = when (fileName?.substringAfterLast('.')) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
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
                if (item.receiptFile != null) "Sí" else "No",
                if (item.photoFile != null) "Sí" else "No",
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
