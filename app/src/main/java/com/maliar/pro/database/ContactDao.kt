package com.maliar.pro.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    
    @Query("SELECT * FROM contacts ORDER BY rowNumber ASC")
    fun getAllContacts(): Flow<List<Contact>>
    
    @Query("SELECT * FROM contacts ORDER BY rowNumber ASC")
    suspend fun getAllContactsList(): List<Contact>
    
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): Contact?
    
    @Query("SELECT * FROM contacts WHERE rowNumber = :rowNumber")
    suspend fun getContactByRowNumber(rowNumber: Int): Contact?
    
    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :name || '%'")
    suspend fun searchContactsByName(name: String): List<Contact>
    
    @Query("SELECT * FROM contacts WHERE phoneNumber LIKE '%' || :phone || '%'")
    suspend fun searchContactsByPhone(phone: String): List<Contact>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<Contact>)
    
    @Update
    suspend fun update(contact: Contact)
    
    @Delete
    suspend fun delete(contact: Contact)
    
    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
    
    @Query("SELECT MAX(rowNumber) FROM contacts")
    suspend fun getMaxRowNumber(): Int?
}
