package fiofoundation.io.fiosdk.interfaces

import fiofoundation.io.fiosdk.errors.serializationprovider.DeserializeError
import fiofoundation.io.fiosdk.models.serializationprovider.AbiFIOSerializationObject
import fiofoundation.io.fiosdk.errors.serializationprovider.SerializeError
import fiofoundation.io.fiosdk.errors.serializationprovider.DeserializeTransactionError
import fiofoundation.io.fiosdk.errors.serializationprovider.SerializeTransactionError
import fiofoundation.io.fiosdk.errors.serializationprovider.DeserializeAbiError
import fiofoundation.io.fiosdk.errors.serializationprovider.SerializeAbiError

interface ISerializationProvider {
    @Throws(DeserializeError::class)
    fun deserialize(deserilizationObject: AbiFIOSerializationObject)

    @Throws(SerializeError::class)
    fun serialize(serializationObject: AbiFIOSerializationObject)

    @Throws(DeserializeTransactionError::class)
    fun deserializeTransaction(hex: String): String

    @Throws(SerializeTransactionError::class)
    fun serializeTransaction(json: String): String

    @Throws(DeserializeAbiError::class)
    fun deserializeAbi(hex: String): String

    @Throws(SerializeAbiError::class)
    fun serializeAbi(json: String): String

    @Throws(SerializeTransactionError::class)
    fun serializeNewFundsContent(json: String): String

    @Throws(DeserializeTransactionError::class)
    fun deserializeNewFundsContent(hex: String): String

    @Throws(SerializeTransactionError::class)
    fun serializeRecordSendContent(json: String): String

    @Throws(DeserializeTransactionError::class)
    fun deserializeRecordSendContent(hex: String): String
}