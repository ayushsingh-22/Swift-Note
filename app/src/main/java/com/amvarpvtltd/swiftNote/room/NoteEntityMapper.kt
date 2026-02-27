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
            synced = synced
        )
    }

    fun toDomain(entity: NoteEntity): dataclass {
        // Create encrypted dataclass to use decryption logic
        val encrypted = dataclass(
            title = entity.title,
            description = entity.description,
            id = entity.id,
            mymobiledeviceid = entity.mymobiledeviceid,
            timestamp = entity.timestamp
        )
        // Decrypt and return fresh dataclass
        return dataclass.fromEncryptedData(encrypted)
    }
}
