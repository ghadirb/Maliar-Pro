package com.maliar.pro.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ContactManager(context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val contactDao = database.contactDao()
    
    fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts()
    }
    
    suspend fun getAllContactsList(): List<Contact> {
        return contactDao.getAllContactsList()
    }
    
    suspend fun getContactById(id: Long): Contact? {
        return contactDao.getContactById(id)
    }
    
    suspend fun getContactByRowNumber(rowNumber: Int): Contact? {
        return contactDao.getContactByRowNumber(rowNumber)
    }
    
    suspend fun searchContactsByName(name: String): List<Contact> {
        return contactDao.searchContactsByName(name)
    }
    
    suspend fun searchContactsByPhone(phone: String): List<Contact> {
        return contactDao.searchContactsByPhone(phone)
    }
    
    suspend fun addContact(contact: Contact): Long {
        return contactDao.insert(contact)
    }
    
    suspend fun updateContact(contact: Contact) {
        contactDao.update(contact.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun deleteContact(contact: Contact) {
        contactDao.delete(contact)
    }
    
    suspend fun deleteContactById(id: Long) {
        contactDao.deleteById(id)
    }
    
    suspend fun deleteAllContacts() {
        contactDao.deleteAll()
    }
    
    suspend fun getNextRowNumber(): Int {
        val maxRow = contactDao.getMaxRowNumber()
        return (maxRow ?: 0) + 1
    }
}
