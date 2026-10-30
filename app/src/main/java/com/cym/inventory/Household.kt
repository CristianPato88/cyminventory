package com.cym.inventory

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/** Thrown when a picked photo/receipt (or the combination of both) won't fit Firestore's per-document size limit. */
internal class AttachmentTooLargeException(message: String) : Exception(message)

internal data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
    val createdBy: String = "",
    val createdById: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val recurrenceDays: Int? = null,
    val doneAt: Long? = null,
    val assignedTo: String = "",
    val assignedToId: String = "",
)

internal data class NoteItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val author: String = "",
    val authorId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

internal data class Member(
    val id: String,
    val name: String,
    val photoMime: String? = null,
)

private val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ" // sin 0/O ni 1/I/L, para que se lea y teclee sin líos
private fun generateHouseholdCode(): String = (1..8).map { CODE_ALPHABET.random() }.joinToString("")

/** Talks to Firebase: anonymous identity, household join code, and real-time sync for every collection. */
internal class HouseholdRepository(private val context: Context, private val store: InventoryStore) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("cym_household", Context.MODE_PRIVATE)

    val householdId: String? get() = prefs.getString("householdId", null)
    val memberName: String get() = prefs.getString("memberName", "") ?: ""
    val memberId: String get() = auth.currentUser?.uid ?: ""

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    suspend fun createHousehold(name: String, photo: Uri? = null): String = withContext(Dispatchers.IO) {
        ensureSignedIn()
        var code = generateHouseholdCode()
        var attempts = 0
        while (attempts < 5 && db.collection("households").document(code).get().await().exists()) {
            code = generateHouseholdCode()
            attempts++
        }
        db.collection("households").document(code).set(mapOf(
            "code" to code,
            "createdAt" to System.currentTimeMillis(),
        )).await()
        saveLocalHousehold(code, name)
        saveMemberProfile(code, name, photo)
        migrateLocalData(code)
        code
    }

    suspend fun joinHousehold(code: String, name: String, photo: Uri? = null): Boolean = withContext(Dispatchers.IO) {
        ensureSignedIn()
        val normalized = code.trim().uppercase()
        val doc = db.collection("households").document(normalized).get().await()
        if (!doc.exists()) return@withContext false
        saveLocalHousehold(normalized, name)
        saveMemberProfile(normalized, name, photo)
        true
    }

    // ---------- Members ----------

    fun membersFlow(householdId: String): Flow<List<Member>> =
        db.collection("households").document(householdId).collection("members")
            .snapshots().map { snapshot -> snapshot.documents.mapNotNull { it.toMember() } }

    /** Saves this device's member profile (name + optional photo); safe to call again later to edit it. */
    suspend fun saveMemberProfile(householdId: String, name: String, pickedPhoto: Uri?) = withContext(Dispatchers.IO) {
        ensureSignedIn()
        val id = memberId
        if (id.isBlank()) return@withContext
        var photoMime: String? = null
        var photoBase64: Any? = null
        if (pickedPhoto != null) {
            photoMime = store.cachePickedFile(pickedPhoto, "member-photos", id)
            photoBase64 = store.prepareUpload("member-photos", id, photoMime)
                ?: throw AttachmentTooLargeException("La foto es demasiado grande. Prueba con otra imagen.")
        }
        val data = mutableMapOf<String, Any?>("name" to name)
        if (photoMime != null) data["photoMime"] = photoMime
        if (photoBase64 != null) data["photoData"] = photoBase64
        db.collection("households").document(householdId).collection("members").document(id)
            .set(data, SetOptions.merge()).await()
        prefs.edit().putString("memberName", name).apply()
    }

    fun leaveHousehold() {
        prefs.edit().clear().apply()
    }

    private fun saveLocalHousehold(code: String, name: String) {
        prefs.edit().putString("householdId", code).putString("memberName", name)
            .putLong("lastSyncCheck", System.currentTimeMillis()).apply()
    }

    private suspend fun migrateLocalData(householdId: String) {
        store.loadLegacyItems().forEach { runCatching { saveItem(householdId, it, null, null) } }
        store.loadLegacyWishlist().forEach { runCatching { saveWishlistItem(householdId, it, null) } }
    }

    // ---------- Inventory ----------

    fun itemsFlow(householdId: String): Flow<List<InventoryItem>> =
        db.collection("households").document(householdId).collection("items")
            .snapshots().map { snapshot -> snapshot.documents.mapNotNull { it.toInventoryItem() } }

    private fun itemsCol(householdId: String) =
        db.collection("households").document(householdId).collection("items")

    private fun filesCol(householdId: String) =
        db.collection("households").document(householdId).collection("files")

    private fun receiptChunkId(itemId: String, index: Int) = "$itemId-receipt-$index"

    suspend fun saveItem(householdId: String, item: InventoryItem, pickedPhoto: Uri?, pickedReceipt: Uri?) =
        withContext(Dispatchers.IO) {
            var photoMime = item.photoMime
            var photoBase64: String? = null
            if (pickedPhoto != null) {
                photoMime = store.cachePickedFile(pickedPhoto, "photos", item.id)
                photoBase64 = store.prepareUpload("photos", item.id, photoMime, InventoryStore.PHOTO_MAX_BYTES)
                    ?: throw AttachmentTooLargeException("No se ha podido procesar la foto. Prueba con otra imagen.")
            }
            var receiptMime = item.receiptMime
            var receiptChunks = item.receiptChunks
            var receiptVersion = item.receiptVersion
            if (pickedReceipt != null) {
                // El ticket va troceado en su propia colección: así un PDF de varios MB no choca con el límite de 1 MB por documento.
                receiptVersion = System.currentTimeMillis()
                store.deleteLocalMedia("receipts", item.id)
                receiptMime = store.cachePickedFile(pickedReceipt, "receipts", item.id)
                val bytes = store.prepareReceiptBytes(item.id, receiptMime)
                    ?: throw AttachmentTooLargeException(
                        "El ticket es demasiado grande. Los PDF pueden ocupar hasta " +
                            "${InventoryStore.RECEIPT_PDF_MAX_BYTES / 1_000_000} MB; si es más grande, hazle una foto.")
                receiptChunks = uploadReceiptChunks(householdId, item.id, bytes, previousChunks = item.receiptChunks)
            }
            val data: MutableMap<String, Any?> = item.copy(photoMime = photoMime, receiptMime = receiptMime,
                receiptChunks = receiptChunks, receiptVersion = receiptVersion,
                updatedAt = System.currentTimeMillis()).toMap().toMutableMap()
            if (photoBase64 != null) data["photoData"] = photoBase64
            // Con el ticket ya troceado, se quita la copia antigua incrustada en el documento, si la había.
            if (pickedReceipt != null) data["receiptData"] = FieldValue.delete()
            itemsCol(householdId).document(item.id).set(data, SetOptions.merge()).await()
        }

    private suspend fun uploadReceiptChunks(householdId: String, itemId: String, bytes: ByteArray, previousChunks: Int): Int {
        val chunks = (bytes.indices step InventoryStore.RECEIPT_CHUNK_BYTES).map { start ->
            bytes.copyOfRange(start, minOf(start + InventoryStore.RECEIPT_CHUNK_BYTES, bytes.size))
        }
        chunks.forEachIndexed { index, chunk ->
            filesCol(householdId).document(receiptChunkId(itemId, index)).set(mapOf(
                "itemId" to itemId,
                "index" to index,
                "data" to Base64.encodeToString(chunk, Base64.NO_WRAP),
            )).await()
        }
        // Si el ticket anterior tenía más trozos, sobran.
        for (index in chunks.size until previousChunks) {
            runCatching { filesCol(householdId).document(receiptChunkId(itemId, index)).delete().await() }
        }
        return chunks.size
    }

    /** Descarga el ticket troceado a la caché local la primera vez que se abre en este móvil. */
    suspend fun downloadReceipt(householdId: String, item: InventoryItem): Uri? = withContext(Dispatchers.IO) {
        store.receiptUri(item)?.let { return@withContext it }
        val mime = item.receiptMime ?: return@withContext null
        if (item.receiptChunks <= 0) return@withContext null
        val chunks = (0 until item.receiptChunks).map { index ->
            filesCol(householdId).document(receiptChunkId(item.id, index)).get().await().getString("data")
                ?: return@withContext null
        }
        store.writeReceiptFromChunks(item.id, mime, chunks)
        store.receiptUri(item)
    }

    suspend fun deleteItem(householdId: String, item: InventoryItem) = withContext(Dispatchers.IO) {
        itemsCol(householdId).document(item.id).delete().await()
        for (index in 0 until item.receiptChunks) {
            runCatching { filesCol(householdId).document(receiptChunkId(item.id, index)).delete().await() }
        }
        store.deleteLocalMedia("photos", item.id)
        store.deleteLocalMedia("receipts", item.id)
    }

    // ---------- Rooms ----------

    fun roomsFlow(householdId: String): Flow<List<Room>> =
        db.collection("households").document(householdId).collection("rooms")
            .snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { it.toRoom() }.sortedWith(compareBy({ it.order }, { normalizeKey(it.name) }))
            }

    /**
     * La primera vez crea las habitaciones por defecto y las que ya se escribían a mano en los artículos,
     * unificando las que solo cambian en tildes o mayúsculas ("salon" = "Salón"). Los ids derivan del nombre,
     * así que si los dos móviles lo ejecutan a la vez escriben los mismos documentos y no se duplica nada.
     */
    suspend fun ensureRooms(householdId: String) = withContext(Dispatchers.IO) {
        val home = db.collection("households").document(householdId)
        if (home.get().await().getBoolean("roomsInitialized") == true) return@withContext
        val roomsCol = home.collection("rooms")
        val names = LinkedHashMap<String, Pair<String, String>>()
        DEFAULT_ROOMS.forEach { (name, icon) -> names[roomSlug(name)] = name to icon }
        itemsCol(householdId).get().await().documents
            .mapNotNull { it.getString("room")?.trim() }
            .filter(String::isNotBlank)
            .forEach { name -> names.putIfAbsent(roomSlug(name), name.replaceFirstChar { it.uppercase() } to DEFAULT_ROOM_ICON) }
        val existing = roomsCol.get().await().documents.map { it.id }.toSet()
        val batch = db.batch()
        names.entries.forEachIndexed { index, (slug, value) ->
            if (slug.isNotBlank() && slug !in existing) {
                batch.set(roomsCol.document(slug), mapOf("name" to value.first, "icon" to value.second, "order" to index))
            }
        }
        batch.commit().await()
        runCatching { home.set(mapOf("roomsInitialized" to true), SetOptions.merge()).await() }
    }

    /** Asigna roomId a los artículos antiguos que solo tenían el nombre de la habitación escrito. */
    suspend fun assignRoomIds(householdId: String, items: List<InventoryItem>, rooms: List<Room>) = withContext(Dispatchers.IO) {
        val bySlug = rooms.associateBy { roomSlug(it.name) } + rooms.associateBy { it.id }
        val updates = items.filter { it.roomId.isBlank() && it.room.isNotBlank() }
            .mapNotNull { item -> bySlug[roomSlug(item.room)]?.let { item to it } }
        if (updates.isEmpty()) return@withContext
        updates.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (item, room) ->
                batch.update(itemsCol(householdId).document(item.id), mapOf("roomId" to room.id, "room" to room.name))
            }
            batch.commit().await()
        }
    }

    /** Crea o actualiza una habitación. Si cambia el nombre, lo actualiza también en sus artículos. */
    suspend fun saveRoom(householdId: String, room: Room, previousName: String?) = withContext(Dispatchers.IO) {
        db.collection("households").document(householdId).collection("rooms").document(room.id)
            .set(mapOf("name" to room.name, "icon" to room.icon, "order" to room.order)).await()
        if (previousName != null && previousName != room.name) {
            itemsCol(householdId).whereEqualTo("roomId", room.id).get().await().documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.update(it.reference, "room", room.name) }
                batch.commit().await()
            }
        }
    }

    /** Borra una habitación pasando antes sus artículos a [moveTo] (o a "sin habitación"). */
    suspend fun deleteRoom(householdId: String, room: Room, moveTo: Room?) = withContext(Dispatchers.IO) {
        itemsCol(householdId).whereEqualTo("roomId", room.id).get().await().documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(it.reference, mapOf("roomId" to (moveTo?.id ?: ""), "room" to (moveTo?.name ?: ""))) }
            batch.commit().await()
        }
        db.collection("households").document(householdId).collection("rooms").document(room.id).delete().await()
    }

    private fun DocumentSnapshot.toRoom(): Room? {
        val name = getString("name") ?: return null
        return Room(id = id, name = name, icon = getString("icon") ?: DEFAULT_ROOM_ICON, order = getLong("order")?.toInt() ?: 0)
    }

    // ---------- Wishlist ----------

    fun wishlistFlow(householdId: String): Flow<List<WishlistItem>> =
        db.collection("households").document(householdId).collection("wishlist")
            .snapshots().map { snapshot -> snapshot.documents.mapNotNull { it.toWishlistItem() } }

    suspend fun saveWishlistItem(householdId: String, item: WishlistItem, pickedPhoto: Uri?) =
        withContext(Dispatchers.IO) {
            var photoMime = item.photoMime
            var photoBase64: Any? = null
            if (pickedPhoto != null) {
                photoMime = store.cachePickedFile(pickedPhoto, "wishlist-photos", item.id)
                photoBase64 = store.prepareUpload("wishlist-photos", item.id, photoMime)
                    ?: throw AttachmentTooLargeException("La foto es demasiado grande. Prueba con otra imagen.")
            }
            val data = item.copy(photoMime = photoMime, updatedAt = System.currentTimeMillis()).toMap().toMutableMap()
            if (photoBase64 != null) data["photoData"] = photoBase64
            db.collection("households").document(householdId).collection("wishlist").document(item.id)
                .set(data, SetOptions.merge()).await()
        }

    suspend fun deleteWishlistItem(householdId: String, item: WishlistItem) = withContext(Dispatchers.IO) {
        db.collection("households").document(householdId).collection("wishlist").document(item.id).delete().await()
        store.deleteLocalMedia("wishlist-photos", item.id)
    }

    // ---------- Tasks ----------

    fun tasksFlow(householdId: String): Flow<List<TaskItem>> =
        db.collection("households").document(householdId).collection("tasks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots().map { snapshot -> snapshot.documents.mapNotNull { it.toTaskItem() } }

    suspend fun addTask(householdId: String, text: String, author: String, recurrenceDays: Int? = null,
        assignedTo: String = "", assignedToId: String = "") = withContext(Dispatchers.IO) {
        val task = TaskItem(text = text, createdBy = author, createdById = memberId,
            recurrenceDays = recurrenceDays, assignedTo = assignedTo, assignedToId = assignedToId)
        db.collection("households").document(householdId).collection("tasks").document(task.id)
            .set(task.toMap()).await()
    }

    suspend fun setTaskDone(householdId: String, taskId: String, done: Boolean) = withContext(Dispatchers.IO) {
        val doneAt: Any = if (done) System.currentTimeMillis() else com.google.firebase.firestore.FieldValue.delete()
        db.collection("households").document(householdId).collection("tasks").document(taskId)
            .update(mapOf("done" to done, "doneAt" to doneAt)).await()
    }

    suspend fun deleteTask(householdId: String, taskId: String) = withContext(Dispatchers.IO) {
        db.collection("households").document(householdId).collection("tasks").document(taskId).delete().await()
    }

    // ---------- Notes ----------

    fun notesFlow(householdId: String): Flow<List<NoteItem>> =
        db.collection("households").document(householdId).collection("notes")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots().map { snapshot -> snapshot.documents.mapNotNull { it.toNoteItem() } }

    suspend fun addNote(householdId: String, text: String, author: String) = withContext(Dispatchers.IO) {
        val note = NoteItem(text = text, author = author, authorId = memberId)
        db.collection("households").document(householdId).collection("notes").document(note.id)
            .set(note.toMap()).await()
    }

    suspend fun deleteNote(householdId: String, noteId: String) = withContext(Dispatchers.IO) {
        db.collection("households").document(householdId).collection("notes").document(noteId).delete().await()
    }

    // ---------- Mapping ----------

    private fun InventoryItem.toMap(): Map<String, Any?> = mapOf(
        "name" to name, "room" to room, "roomId" to roomId, "category" to category, "status" to status.name,
        "priceCents" to priceCents, "purchaseDate" to purchaseDate,
        "receiptMime" to receiptMime, "receiptChunks" to receiptChunks, "receiptVersion" to receiptVersion,
        "photoMime" to photoMime,
        "shop" to shop, "description" to description, "location" to location, "purpose" to purpose,
        "paidBy" to paidBy, "paidById" to paidById,
        "updatedAt" to updatedAt,
    )

    private fun DocumentSnapshot.toInventoryItem(): InventoryItem? {
        val name = getString("name") ?: return null
        val photoMime = getString("photoMime")
        val receiptMime = getString("receiptMime")
        if (photoMime != null) getString("photoData")?.let { store.materializeFromBase64("photos", id, photoMime, it) }
        if (receiptMime != null) getString("receiptData")?.let { store.materializeFromBase64("receipts", id, receiptMime, it) }
        return InventoryItem(
            id = id,
            name = name,
            room = getString("room") ?: "",
            roomId = getString("roomId") ?: "",
            category = getString("category") ?: "",
            status = runCatching { ItemStatus.valueOf(getString("status") ?: "PLANNED") }.getOrDefault(ItemStatus.PLANNED),
            priceCents = getLong("priceCents"),
            purchaseDate = getString("purchaseDate"),
            receiptMime = receiptMime,
            receiptChunks = getLong("receiptChunks")?.toInt() ?: 0,
            receiptVersion = getLong("receiptVersion") ?: 0L,
            photoMime = photoMime,
            shop = getString("shop") ?: "",
            description = getString("description") ?: "",
            location = getString("location") ?: "",
            purpose = getString("purpose") ?: "",
            paidBy = getString("paidBy") ?: "",
            paidById = getString("paidById") ?: "",
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }

    private fun WishlistItem.toMap(): Map<String, Any?> = mapOf(
        "name" to name, "url" to url, "shop" to shop, "location" to location,
        "priceCents" to priceCents, "notes" to notes, "addedBy" to addedBy,
        "photoMime" to photoMime, "addedDate" to addedDate, "updatedAt" to updatedAt,
    )

    private fun DocumentSnapshot.toWishlistItem(): WishlistItem? {
        val name = getString("name") ?: return null
        val photoMime = getString("photoMime")
        if (photoMime != null) getString("photoData")?.let { store.materializeFromBase64("wishlist-photos", id, photoMime, it) }
        return WishlistItem(
            id = id,
            name = name,
            url = getString("url") ?: "",
            shop = getString("shop") ?: "",
            location = getString("location") ?: "",
            priceCents = getLong("priceCents"),
            notes = getString("notes") ?: "",
            addedBy = getString("addedBy") ?: "",
            photoMime = photoMime,
            addedDate = getString("addedDate") ?: "",
            updatedAt = getLong("updatedAt") ?: 0L,
        )
    }

    private fun TaskItem.toMap(): Map<String, Any?> = mapOf(
        "text" to text, "done" to done, "createdBy" to createdBy, "createdById" to createdById, "createdAt" to createdAt,
        "recurrenceDays" to recurrenceDays, "doneAt" to doneAt, "assignedTo" to assignedTo, "assignedToId" to assignedToId,
    )

    private fun DocumentSnapshot.toTaskItem(): TaskItem? {
        val text = getString("text") ?: return null
        return TaskItem(id = id, text = text, done = getBoolean("done") ?: false,
            createdBy = getString("createdBy") ?: "", createdById = getString("createdById") ?: "",
            createdAt = getLong("createdAt") ?: 0L, recurrenceDays = getLong("recurrenceDays")?.toInt(),
            doneAt = getLong("doneAt"), assignedTo = getString("assignedTo") ?: "", assignedToId = getString("assignedToId") ?: "")
    }

    private fun NoteItem.toMap(): Map<String, Any?> = mapOf(
        "text" to text, "author" to author, "authorId" to authorId, "createdAt" to createdAt,
    )

    private fun DocumentSnapshot.toNoteItem(): NoteItem? {
        val text = getString("text") ?: return null
        return NoteItem(id = id, text = text, author = getString("author") ?: "", authorId = getString("authorId") ?: "",
            createdAt = getLong("createdAt") ?: 0L)
    }

    private fun DocumentSnapshot.toMember(): Member? {
        val name = getString("name") ?: return null
        val photoMime = getString("photoMime")
        if (photoMime != null) getString("photoData")?.let { store.materializeFromBase64("member-photos", id, photoMime, it) }
        return Member(id = id, name = name, photoMime = photoMime)
    }
}

private fun com.google.firebase.firestore.Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) { close(error); return@addSnapshotListener }
        if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}
