package com.amvarpvtltd.swiftNote.room

import com.amvarpvtltd.swiftNote.dataclass

object NoteEntityMapper {
    fun toEntity(domain: dataclass, synced: Boolean): NoteEntity {
        // Encrypt fields for storage
        return NoteEntity(
            id = domain.id,
            title = domain.getEncryptedTitle(),
            description = domain.getEncryptedDescription(),
            mymobiledeviceid = domain.mymobiledeviceid,
            timestamp = domain.timestamp,
            synced = synced,
            updatedAt = domain.updatedAt,
            isPinned = domain.isPinned,
            isArchived = domain.isArchived,
            category = domain.category,
            colorKey = domain.colorKey
        )
    }

    fun toDomain(entity: NoteEntity): dataclass {
        // Create encrypted dataclass to use decryption logic
        val encrypted = dataclass(
            title = entity.title,
            description = entity.description,
            id = entity.id,
            mymobiledeviceid = entity.mymobiledeviceid,
            timestamp = entity.timestamp,
            updatedAt = if (entity.updatedAt > 0) entity.updatedAt else entity.timestamp,
            isPinned = entity.isPinned,
            isArchived = entity.isArchived,
            category = entity.category,
            colorKey = entity.colorKey
        )
        // Decrypt and return fresh dataclass
        return dataclass.fromEncryptedData(encrypted)
    }
}
