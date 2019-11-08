package fiofoundation.io.fiosdk

import fiofoundation.io.fiosdk.enums.FioDomainVisiblity
import fiofoundation.io.fiosdk.errors.FIOError
import fiofoundation.io.fiosdk.errors.fionetworkprovider.*
import fiofoundation.io.fiosdk.errors.formatters.FIOFormatterError
import fiofoundation.io.fiosdk.errors.serializationprovider.DeserializeTransactionError
import fiofoundation.io.fiosdk.errors.serializationprovider.SerializeTransactionError
import fiofoundation.io.fiosdk.errors.session.TransactionBroadCastError
import fiofoundation.io.fiosdk.errors.session.TransactionPrepareError
import fiofoundation.io.fiosdk.errors.session.TransactionSignError
import fiofoundation.io.fiosdk.errors.signatureprovider.ImportKeyError
import fiofoundation.io.fiosdk.formatters.FIOFormatter
import fiofoundation.io.fiosdk.implementations.ABIProvider
import fiofoundation.io.fiosdk.implementations.FIONetworkProvider
import fiofoundation.io.fiosdk.implementations.SoftKeySignatureProvider
import fiofoundation.io.fiosdk.interfaces.ISerializationProvider
import fiofoundation.io.fiosdk.interfaces.ISignatureProvider
import fiofoundation.io.fiosdk.models.Constants
import fiofoundation.io.fiosdk.models.Validator
import fiofoundation.io.fiosdk.models.fionetworkprovider.FIOApiEndPoints
import fiofoundation.io.fiosdk.models.fionetworkprovider.FIORequestContent
import fiofoundation.io.fiosdk.models.fionetworkprovider.FundsRequestContent
import fiofoundation.io.fiosdk.models.fionetworkprovider.RecordSendContent
import fiofoundation.io.fiosdk.models.fionetworkprovider.actions.*
import fiofoundation.io.fiosdk.models.fionetworkprovider.request.*
import fiofoundation.io.fiosdk.models.fionetworkprovider.response.*
import fiofoundation.io.fiosdk.session.processors.*
import fiofoundation.io.fiosdk.utilities.CryptoUtils
import fiofoundation.io.fiosdk.utilities.PrivateKeyUtils

import java.math.BigInteger

/**
 * Kotlin SDK for FIO Foundation API
 *
 * @param privateKey the fio private key of the client sending requests to FIO API.
 * @param publicKey the fio public key of the client sending requests to FIO API.
 * @param serializationProvider the serialization provider used for abi serialization and deserialization.
 * @param signatureProvider the signature provider used to sign block chain transactions.
 * @param networkBaseUrl the url to the FIO API.
 */
class FIOSDK(private var privateKey: String, var publicKey: String,
             var serializationProvider: ISerializationProvider,
             var signatureProvider: ISignatureProvider, private val networkBaseUrl:String)
{
    private var networkProvider:FIONetworkProvider = FIONetworkProvider(networkBaseUrl,"")

    private val abiProvider:ABIProvider = ABIProvider(networkProvider,this.serializationProvider)

    companion object Static {
        private var fioSdk: FIOSDK? = null

        private const val ISLEGACY_KEY_FORMAT = true

        /**
         * Create a FIO private key.
         *
         * @param mnemonic mnemonic used to generate a random unique private key.
         */
        @Throws(FIOFormatterError::class)
        fun createPrivateKey(mnemonic: String): String {
            return FIOFormatter.convertPEMFormattedPrivateKeyToFIOFormat(
                PrivateKeyUtils.createPEMFormattedPrivateKey(mnemonic)
            )
        }

        /**
         * Create a FIO public key.
         *
         * @param fioPrivateKey FIO private key.
         */
        @Throws(FIOFormatterError::class)
        fun derivedPublicKey(fioPrivateKey: String): String {
            return FIOFormatter.convertPEMFormattedPublicKeyToFIOFormat(
                PrivateKeyUtils.extractPEMFormattedPublicKey(
                    FIOFormatter.convertFIOPrivateKeyToPEMFormat(fioPrivateKey)
                ), ISLEGACY_KEY_FORMAT
            )
        }

        /**
         * Initialize a static instance of the FIO SDK.  If an instance already exists,
         * it will be returned.
         *
         * @param privateKey the fio private key of the client sending requests to FIO API.
         * @param publicKey the fio public key of the client sending requests to FIO API.
         * @param serializationProvider the serialization provider used for abi serialization and deserialization.
         * @param signatureProvider the signature provider used to sign block chain transactions.
         * @param networkBaseUrl the url to the FIO API.
         */
        fun getInstance(privateKey: String,publicKey: String,
                        serializationProvider: ISerializationProvider,
                        signatureProvider: ISignatureProvider,networkBaseUrl:String): FIOSDK
        {
            if(fioSdk == null)
             fioSdk = FIOSDK(privateKey,publicKey,serializationProvider,
                 signatureProvider,networkBaseUrl)

            return fioSdk!!
        }

        /**
         * Initialize a static instance of the FIO SDK using the default signature provider
         * and default serialization provider.  If an instance already exists,
         * it will be returned.
         *
         * @param privateKey the fio private key of the client sending requests to FIO API.
         * @param publicKey the fio public key of the client sending requests to FIO API.
         * @param networkBaseUrl the url to the FIO API.
         */
        fun getInstance(privateKey: String,publicKey: String,
                        serializationProvider: ISerializationProvider,
                        networkBaseUrl:String): FIOSDK
        {
            if(fioSdk == null)
            {
                val signatureProvider = SoftKeySignatureProvider()
                signatureProvider.importKey(privateKey)

                fioSdk = FIOSDK(
                    privateKey,
                    publicKey,
                    serializationProvider,
                    signatureProvider,
                    networkBaseUrl
                )
            }

            return fioSdk!!
        }

        /**
         * Return a previously initialized instance of the FIO SDK.
         */
        fun getInstance(): FIOSDK
        {
            if(this.fioSdk == null)
                throw FIOError("The instance has not been previously initialized.")

            return fioSdk!!
        }

        /**
         * Set the FIO SDK instance to null
         */
        fun destroyInstance()
        {
            fioSdk = null
        }
    }

    /**
     * @suppress
     */
    var mockServerBaseUrl:String=""
        set(value){
            if(value.isNotEmpty())
            {
                this.networkProvider = FIONetworkProvider(this.networkBaseUrl,value)
            }
            field = value
        }

    /**
     * @param privateKey set private key.  If using the default signature provider, a new instance
     * of the provider will be created automatically.
     * @throws [FIOError]
     * */
    @Throws(FIOError::class)
    open fun setPrivateKey(privateKey:String)
    {
        this.privateKey = privateKey

        //If the signature provider is the default provider, then import the private key
        try {
            if(this.signatureProvider is SoftKeySignatureProvider)
                (this.signatureProvider as SoftKeySignatureProvider).importKey(privateKey)
        }
        catch(e:ImportKeyError)
        {
            throw FIOError(e.message!!,e)
        }
        catch(ex:Exception)
        {
            throw FIOError(ex.message!!,ex)
        }

    }

    fun getPrivateKey():String
    {
        return this.privateKey
    }

    /**
     * @suppress
     */
    @Throws(FIOError::class)
    fun registerFioNameOnBehalfOfUser(fioName:String): RegisterFIONameForUserResponse
    {
        val request = RegisterFIONameForUserRequest(fioName, this.publicKey)

        return this.networkProvider.registerFioNameOnBehalfOfUser(request)
    }

    /**
     * @suppress
     */
    @Throws(FIOError::class)
    fun registerFioNameOnBehalfOfUser(fioName:String, ownerPublicKey:String): RegisterFIONameForUserResponse
    {
        try
        {
            val request = RegisterFIONameForUserRequest(fioName, ownerPublicKey)

            return this.networkProvider.registerFioNameOnBehalfOfUser(request)
        }
        catch(registerFIONameForUserError: RegisterFIONameForUserError)
        {
            throw FIOError(registerFIONameForUserError.message!!,registerFIONameForUserError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Registers a FIO Address on the FIO blockchain.
     *
     * @param fioAddress FIO Address to register.
     * @param ownerPublicKey Public key which will own the FIO Address after registration. Set to empty if same as sender.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun registerFioAddress(fioAddress:String ,ownerPublicKey:String, maxFee:BigInteger,
                           walletFioAddress:String): PushTransactionResponse
    {
        var transactionProcessor = RegisterFIOAddressTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateRegisterFioAddress(fioAddress,ownerPublicKey,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                var registerFioAddressAction =
                    RegisterFIOAddressAction(
                        fioAddress,
                        ownerPublicKey,
                        walletFioAddress,
                        maxFee,
                        this.publicKey
                    )

                var actionList = ArrayList<RegisterFIOAddressAction>()
                actionList.add(registerFioAddressAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Registers a FIO Address on the FIO blockchain.
     *
     * @param fioAddress FIO Address to register.
     * @param ownerPublicKey Public key which will own the FIO Address after registration. Set to empty if same as sender.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @return
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun registerFioAddress(fioAddress:String, ownerPublicKey:String, maxFee:BigInteger): PushTransactionResponse
    {
        return registerFioAddress(fioAddress,ownerPublicKey,maxFee,"")
    }

    /**
     * Registers a FIO Address on the FIO blockchain.  The owner will be the public key associated with the FIO SDK instance.
     *
     * @param fioAddress FIO Address to register.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun registerFioAddress(fioAddress:String, maxFee:BigInteger, walletFioAddress:String): PushTransactionResponse
    {
        return registerFioAddress(fioAddress,"", maxFee, walletFioAddress)
    }

    /**
     * Registers a FIO Address on the FIO blockchain.  The owner will be the public key associated with the FIO SDK instance.
     *
     * @param fioAddress FIO Address to register.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun registerFioAddress(fioAddress:String, maxFee:BigInteger): PushTransactionResponse
    {
        return registerFioAddress(fioAddress,"",maxFee)
    }

    /**
     * Registers a FIO Domain on the FIO blockchain.
     *
     * @param fioDomain FIO Domain to register.
     * @param ownerPublicKey Public key which will own the FIO Domain after registration. Set to empty if same as sender.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun registerFioDomain(fioDomain:String, ownerPublicKey:String, maxFee:BigInteger,
                          walletFioAddress:String): PushTransactionResponse
    {
        var transactionProcessor = RegisterFIODomainTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateRegisterFioDomain(fioDomain,ownerPublicKey,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                var registerFioDomainAction = RegisterFIODomainAction(
                    fioDomain,
                    ownerPublicKey,
                    walletFioAddress,
                    maxFee,
                    this.publicKey
                )

                var actionList = ArrayList<RegisterFIODomainAction>()
                actionList.add(registerFioDomainAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Registers a FIO Domain on the FIO blockchain.
     *
     * @param fioDomain FIO Domain to register.
     * @param ownerPublicKey Public key which will own the FIO Domain after registration. Set to empty if same as sender.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun registerFioDomain(fioDomain:String, ownerPublicKey:String, maxFee:BigInteger): PushTransactionResponse
    {
        return registerFioDomain(fioDomain, ownerPublicKey, maxFee,"")
    }

    /**
     * Registers a FIO Domain on the FIO blockchain.
     *
     * @param fioDomain FIO Domain to register. The owner will be the public key associated with the FIO SDK instance.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun registerFioDomain(fioDomain:String, maxFee:BigInteger,
                          walletFioAddress:String): PushTransactionResponse
    {
        return registerFioDomain(fioDomain,"",maxFee,walletFioAddress)
    }

    /**
     * Registers a FIO Domain on the FIO blockchain.
     *
     * @param fioDomain FIO Domain to register. The owner will be the public key associated with the FIO SDK instance.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun registerFioDomain(fioDomain:String, maxFee:BigInteger): PushTransactionResponse
    {
        return registerFioDomain(fioDomain,maxFee,"")
    }

    /**
     * Renew a FIO Domain on the FIO blockchain.
     *
     * @param fioDomain FIO Domain to renew.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun renewFioDomain(fioDomain:String, maxFee:BigInteger,
                          walletFioAddress:String): PushTransactionResponse
    {
        var transactionProcessor = RegisterFIODomainTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateRenewFioDomain(fioDomain,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                var renewFioDomainAction = RenewFIODomainAction(
                    fioDomain,
                    maxFee,
                    walletFioAddress,
                    this.publicKey
                )

                var actionList = ArrayList<RenewFIODomainAction>()
                actionList.add(renewFioDomainAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Renew a FIO Domain on the FIO blockchain.
     *
     * @param fioDomain FIO Domain to renew.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun renewFioDomain(fioDomain:String, maxFee:BigInteger): PushTransactionResponse
    {
        return renewFioDomain(fioDomain, maxFee,"")
    }

    /**
     * Renew a FIO Address on the FIO blockchain.
     *
     * @param fioAddress FIO Address to renew.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun renewFioAddress(fioAddress:String, maxFee:BigInteger,
                           walletFioAddress:String): PushTransactionResponse
    {
        var transactionProcessor = RenewFIOAddressTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateRenewFioAddress(fioAddress,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                var renewFioAddressAction =
                    RenewFIOAddressAction(
                        fioAddress,
                        maxFee,
                        walletFioAddress,
                        this.publicKey
                    )

                var actionList = ArrayList<RenewFIOAddressAction>()
                actionList.add(renewFioAddressAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Renew a FIO Address on the FIO blockchain.
     *
     * @param fioAddress FIO Address to renew.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by @ [getFee] for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun renewFioAddress(fioAddress:String, maxFee:BigInteger): PushTransactionResponse
    {
        return renewFioAddress(fioAddress,maxFee,"")
    }

    /**
     *
     * Transfers FIO tokens from public key associated with the FIO SDK instance to
     * the payeePublicKey.
     *
     * @param payeePublicKey FIO public Address of the one receiving the tokens.
     * @param amount Amount sent in SUFs.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by /get_fee for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun transferTokens(payeeFioPublicKey:String, amount:BigInteger, maxFee:BigInteger,
                                  walletFioAddress:String): PushTransactionResponse
    {
        var transactionProcessor = TransTokensPubKeyTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateTransferPublicTokens(payeeFioPublicKey,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                var transferTokensToPublickey = TransferTokensPubKeyAction(
                    payeeFioPublicKey,
                    amount,
                    maxFee,
                    walletFioAddress,
                    this.publicKey
                )

                var actionList = ArrayList<TransferTokensPubKeyAction>()
                actionList.add(transferTokensToPublickey)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     *
     * Transfers FIO tokens from public key associated with the FIO SDK instance to
     * the payeePublicKey.
     *
     * @param payeePublicKey FIO public Address of the one receiving the tokens.
     * @param amount Amount sent in SUFs.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by /get_fee for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun transferTokens(payeeFioPublicKey:String, amount:BigInteger, maxFee:BigInteger): PushTransactionResponse
    {
        return transferTokens(payeeFioPublicKey, amount, maxFee,"")
    }

    /**
     * Retrieves balance of FIO tokens using the public key of the client
     * sending the request.
     * @return [GetFIOBalanceResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getFioBalance(): GetFIOBalanceResponse
    {
        return this.getFioBalance(this.publicKey)
    }

    /**
     * Retrieves balance of FIO tokens
     *
     * @param fioPublicKey FIO public key.
     * @return [GetFIOBalanceResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getFioBalance(fioPublicKey:String): GetFIOBalanceResponse
    {
        try
        {
            val request = GetFIOBalanceRequest(fioPublicKey)

            return this.networkProvider.getFIOBalance(request)
        }
        catch(fioBalanceError: GetFIOBalanceError)
        {
            throw FIOError(fioBalanceError.message!!,fioBalanceError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Create a new funds request on the FIO chain.
     *
     * @param payerFioAddress FIO Address of the payer. This address will receive the request and will initiate payment.
     * @param payeeFioAddress FIO Address of the payee. This address is sending the request and will receive payment.
     * @param payeeTokenPublicAddress Payee's public address where they want funds sent.
     * @param amount Amount requested.
     * @param tokenCode Code of the token represented in amount requested.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by [getFee] for correct value.
     * @param walletFioAddress (optional) FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    fun requestNewFunds(payerFioAddress:String, payeeFioAddress:String,
                        payeeTokenPublicAddress:String, amount:String, tokenCode:String,
                        maxFee:BigInteger, walletFioAddress:String=""): PushTransactionResponse
    {
        val fundsRequestContent = FundsRequestContent(payeeTokenPublicAddress,amount,tokenCode)

        return this.requestNewFunds(payerFioAddress,payeeFioAddress,fundsRequestContent,maxFee,walletFioAddress)
    }

    /**
     * Create a new funds request on the FIO chain.
     *
     * @param payerFioAddress FIO Address of the payer. This address will receive the request and will initiate payment.
     * @param payeeFioAddress FIO Address of the payee. This address is sending the request and will receive payment.
     * @param payeeTokenPublicAddress Payee's public address where they want funds sent.
     * @param amount Amount requested.
     * @param tokenCode Code of the token represented in amount requested.
     * @param memo (optional)
     * @param hash (optional)
     * @param offlineUrl (optional)
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by [getFee] for correct value.
     * @param walletFioAddress (optional) FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    fun requestNewFunds(payerFioAddress:String, payeeFioAddress:String,
                        payeeTokenPublicAddress:String, amount:String, tokenCode:String,
                        memo: String?=null, hash: String?=null, offlineUrl:String?=null,
                        maxFee:BigInteger, walletFioAddress:String=""): PushTransactionResponse
    {
        val fundsRequestContent = FundsRequestContent(payeeTokenPublicAddress,amount,tokenCode,memo,hash,offlineUrl)

        return this.requestNewFunds(payerFioAddress,payeeFioAddress,fundsRequestContent,maxFee,walletFioAddress)
    }

    /**
     * Reject funds request.
     *
     * @param fioRequestId Existing funds request Id
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by [getFee] for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun rejectFundsRequest(fioRequestId:BigInteger, maxFee: BigInteger, walletFioAddress:String): PushTransactionResponse
    {
        var transactionProcessor = RejectFundsRequestTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateRejectFundsRequest(fioRequestId,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                var rejectFundsRequestAction = RejectFundsRequestAction(
                    fioRequestId,
                    maxFee,
                    walletFioAddress,
                    this.publicKey
                )

                var actionList = ArrayList<RejectFundsRequestAction>()
                actionList.add(rejectFundsRequestAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Reject funds request.
     *
     * @param fioRequestId Existing funds request Id
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by [getFee] for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun rejectFundsRequest(fioRequestId: BigInteger, maxFee: BigInteger): PushTransactionResponse
    {
        return rejectFundsRequest(fioRequestId,maxFee,"")
    }

    /**
     *
     * Records information on the FIO blockchain about a transaction that occurred on other blockchain, i.e. 1 BTC was sent on Bitcoin Blockchain, and both
     * sender and receiver have FIO Addresses. OBT stands for Other Blockchain Transaction
     *
     * @param fioRequestId ID of funds request, if this Record Send transaction is in response to a previously received funds request.  Send empty if no FIO Request ID
     * @param payerFioAddress FIO Address of the payer. This address initiated payment.
     * @param payeeFioAddress FIO Address of the payee. This address is receiving payment.
     * @param payerTokenPublicAddress Public address on other blockchain of user sending funds.
     * @param payeeTokenPublicAddress Public address on other blockchain of user receiving funds.
     * @param amount Amount sent.
     * @param tokenCode Code of the token represented in Amount requested, i.e. BTC.
     * @param obtId Other Blockchain Transaction ID (OBT ID), i.e Bitcoin transaction ID.
     * @param status Status of this OBT. Allowed statuses are: sent_to_blockchain.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by /get_fee for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    fun recordSend(fioRequestId: BigInteger, payerFioAddress:String, payeeFioAddress:String,
                   payerTokenPublicAddress: String, payeeTokenPublicAddress:String, amount:Double,
                   tokenCode:String, status:String="sent_to_blockchain", obtId:String, maxFee:BigInteger,walletFioAddress:String=""): PushTransactionResponse
    {
        val recordSendContent = RecordSendContent(payerTokenPublicAddress,payeeTokenPublicAddress,amount.toString(),
            tokenCode,obtId,status)

        return this.recordSend(fioRequestId,payerFioAddress,payeeFioAddress,recordSendContent,maxFee,walletFioAddress)
    }

    /**
     *
     * Records information on the FIO blockchain about a transaction that occurred on other blockchain, i.e. 1 BTC was sent on Bitcoin Blockchain, and both
     * sender and receiver have FIO Addresses. OBT stands for Other Blockchain Transaction
     *
     * @param fioRequestId ID of funds request, if this Record Send transaction is in response to a previously received funds request.  Send empty if no FIO Request ID
     * @param payerFioAddress FIO Address of the payer. This address initiated payment.
     * @param payeeFioAddress FIO Address of the payee. This address is receiving payment.
     * @param payerTokenPublicAddress Public address on other blockchain of user sending funds.
     * @param payeeTokenPublicAddress Public address on other blockchain of user receiving funds.
     * @param amount Amount sent.
     * @param tokenCode Code of the token represented in Amount requested, i.e. BTC.
     * @param obtId Other Blockchain Transaction ID (OBT ID), i.e Bitcoin transaction ID.
     * @param status Status of this OBT. Allowed statuses are: sent_to_blockchain.
     * @param memo
     * @param hash
     * @param offlineUrl
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by /get_fee for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    fun recordSend(fioRequestId: BigInteger, payerFioAddress:String, payeeFioAddress:String,
                   payerTokenPublicAddress: String, payeeTokenPublicAddress:String, amount:Double,
                   tokenCode:String, status:String="sent_to_blockchain", obtId:String, maxFee:BigInteger,walletFioAddress:String="",
                   memo:String?=null, hash:String?=null, offlineUrl:String?=null): PushTransactionResponse
    {
        val recordSendContent = RecordSendContent(payerTokenPublicAddress,payeeTokenPublicAddress,amount.toString(),
            tokenCode,obtId,status,memo,hash,offlineUrl)

        return this.recordSend(fioRequestId,payerFioAddress,payeeFioAddress,recordSendContent,maxFee,walletFioAddress)
    }

    fun recordSend(fioRequestId: BigInteger, payerFioAddress:String, payeeFioAddress:String,
                   payerTokenPublicAddress: String, payeeTokenPublicAddress:String, amount:Double,
                   tokenCode:String, obtId:String, status:String="sent_to_blockchain",
                   maxFee:BigInteger): PushTransactionResponse
    {
        val recordSendContent = RecordSendContent(payerTokenPublicAddress,
            payeeTokenPublicAddress, amount.toString(),tokenCode,obtId,status)

        return this.recordSend(fioRequestId, payerFioAddress, payeeFioAddress, recordSendContent, maxFee,"")
    }

    /**
     * Polls for any pending requests sent to public key associated with the FIO SDK instance.
     *
     * @param limit Number of request to return. If omitted, all requests will be returned.
     * @param offset First request from list to return. If omitted, 0 is assumed.
     *
     * @return [List<FIORequestContent>]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getPendingFioRequests(limit:Int?=null,offset:Int?=null): List<FIORequestContent>
    {
        return this.getPendingFioRequests(this.publicKey,limit,offset)
    }

    /**
     * Polls for any sent requests sent by public key associated with the FIO SDK instance.
     *
     * @param limit Number of request to return. If omitted, all requests will be returned.
     * @param offset First request from list to return. If omitted, 0 is assumed.
     *
     * @return [List<FIORequestContent>]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getSentFioRequests(limit:Int?=null,offset:Int?=null): List<FIORequestContent>
    {
        return this.getSentFioRequests(this.publicKey,limit,offset)
    }

    /**
     * Returns FIO Addresses and FIO Domains owned by this public key.
     *
     * @param fioPublicKey FIO public key of owner.
     * @return [GetFIONamesResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getFioNames(fioPublicKey:String): GetFIONamesResponse
    {
        try
        {
            val request = GetFIONamesRequest(fioPublicKey)

            return this.networkProvider.getFIONames(request)
        }
        catch(getFioNamesError: GetFIONamesError)
        {
            throw FIOError(getFioNamesError.message!!,getFioNamesError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Returns the FIO token public address for specified FIO Address.
     *
     * @param fioAddress FIO Address for which fio token public address is to be returned.
     * @return [GetPublicAddressResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getFioPublicAddress(fioAddress:String): GetPublicAddressResponse
    {
        return getPublicAddress(fioAddress,"FIO")
    }

    /**
     * Returns a token public address for specified token code and FIO Address.
     *
     * @param fioAddress FIO Address for which the token public address is to be returned.
     * @param tokenCode Token code for which public address is to be returned.
     * @return [GetPublicAddressResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getPublicAddress(fioAddress:String, tokenCode:String): GetPublicAddressResponse
    {
        try
        {
            val request = GetPublicAddressRequest(fioAddress,tokenCode)

            return this.networkProvider.getPublicAddress(request)
        }
        catch(getPublicAddressError: GetPublicAddressError)
        {
            throw FIOError(getPublicAddressError.message!!,getPublicAddressError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Checks if a FIO Address or FIO Domain is available for registration.
     *
     * @param fioName FIO Address or FIO Domain to check.
     * @return [FIONameAvailabilityCheckResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun isAvailable(fioName:String): FIONameAvailabilityCheckResponse
    {
        try
        {
            val request = FIONameAvailabilityCheckRequest(fioName)

            return this.networkProvider.isFIONameAvailable(request)
        }
        catch(fioNameAvailabilityCheckError: FIONameAvailabilityCheckError)
        {
            throw FIOError(fioNameAvailabilityCheckError.message!!,fioNameAvailabilityCheckError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Compute and return fee amount for specific call and specific user
     *
     * @param fioName
     *        if endPointName is RenewFioAddress, FIO Address incurring the fee and owned by signer.
     *        if endPointName is RenewFioDomain, FIO Domain incurring the fee and owned by signer.
     *        if endPointName is RecordSend, Payee FIO Address incurring the fee and owned by signer.
     * @param endPointName Name of API call end point, e.g. add_pub_address.
     * @return [GetFeeResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getFee(endPointName:FIOApiEndPoints.EndPointsWithFees): GetFeeResponse
    {
        try
        {
            val request = GetFeeRequest(endPointName.endpoint,"")

            return this.networkProvider.getFee(request)
        }
        catch(getFeeError: GetFeeError)
        {
            throw FIOError(getFeeError.message!!,getFeeError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Compute and return fee amount for New Funds Request
     *
     * @return [GetFeeResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getFeeForNewFundsRequest(): GetFeeResponse
    {
        try
        {
            if(this.publicKey.isFioPublicKey()) {
                val request = GetFeeRequest(FIOApiEndPoints.new_funds_request, this.publicKey)

                return this.networkProvider.getFee(request)
            }
            else
                throw Exception("Invalid FIO Public Key")
        }
        catch(getFeeError: GetFeeError)
        {
            throw FIOError(getFeeError.message!!,getFeeError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Compute and return fee amount for Reject Funds Request
     *
     * @return [GetFeeResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getFeeForRejectFundsRequest(): GetFeeResponse
    {
        try
        {
            if(this.publicKey.isFioPublicKey()) {
                val request = GetFeeRequest(FIOApiEndPoints.reject_funds_request, this.publicKey)

                return this.networkProvider.getFee(request)
            }
            else
                throw Exception("Invalid FIO Public Key")
        }
        catch(getFeeError: GetFeeError)
        {
            throw FIOError(getFeeError.message!!,getFeeError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Compute and return fee amount for RecordSend
     *
     * @param payerFioAddress The FIO Address of the payer whose transaction is being recorded by the RecordSend call
     * @return [GetFeeResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    fun getFeeForRecordSend(payerFioAddress:String): GetFeeResponse
    {
        try
        {
            if(payerFioAddress.isFioAddress()) {
                val request = GetFeeRequest(FIOApiEndPoints.record_send, payerFioAddress)

                return this.networkProvider.getFee(request)
            }
            else
                throw Exception("Invalid FIO Address")
        }
        catch(getFeeError: GetFeeError)
        {
            throw FIOError(getFeeError.message!!,getFeeError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Adds a public address of the specific blockchain type to the FIO Address.
     *
     * @param fioAddress FIO Address to add the public address to.
     * @param tokenCode Token code to be used with that public address.
     * @param tokenPublicAddress The public address to be added to the FIO Address for the specified token.
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by [getFee] for correct value.
     * @param walletFioAddress (optional) FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    fun addPublicAddress(fioAddress:String, tokenCode:String, tokenPublicAddress:String,
                         maxFee:BigInteger, walletFioAddress:String=""): PushTransactionResponse
    {
        var transactionProcessor = AddPublicAddressTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateAddPublicAddress(fioAddress,tokenCode,tokenPublicAddress,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                var addPublicAddressAction = AddPublicAddressAction(
                    fioAddress,
                    tokenCode,
                    tokenPublicAddress,
                    maxFee,
                    walletFioAddress,
                    this.publicKey
                )

                var actionList = ArrayList<AddPublicAddressAction>()
                actionList.add(addPublicAddressAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * By default all FIO Domains are non-public, meaning only the owner can register FIO Addresses on that domain.
     * Setting them to public allows anyone to register a FIO Address on that domain.
     *
     * @param fioDomain FIO Domain to make public or private.  Default is private.
     * @param visibility [FioDomainVisiblity]
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by [getFee] for correct value.
     * @param walletFioAddress (optional) FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    fun setFioDomainVisibility(fioDomain:String, visibility:FioDomainVisiblity,
                         maxFee:BigInteger, walletFioAddress:String=""): PushTransactionResponse
    {
        var transactionProcessor = SetFioDomainVisibilityTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateSetFioDomainVisibility(fioDomain,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                var setFioDomainVisibilityAction = SetFioDomainVisibilityAction(
                    fioDomain,
                    visibility,
                    maxFee,
                    walletFioAddress,
                    this.publicKey
                )


                var actionList = ArrayList<SetFioDomainVisibilityAction>()
                actionList.add(setFioDomainVisibilityAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    fun getMultiplier(): Int
    {
        return Constants.multiplier
    }

    //Private Methods

    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    private fun serializeAndEncryptNewFundsContent(fundsRequestContent: FundsRequestContent, payerPublickey: String): String
    {
        try
        {
            val serializedNewFundsContent = this.serializationProvider.serializeNewFundsContent(fundsRequestContent.toJson())

            val secretKey = CryptoUtils.generateSharedSecret(this.privateKey,payerPublickey)


            return CryptoUtils.encryptSharedMessage(serializedNewFundsContent,secretKey)
        }
        catch(serializeError: SerializeTransactionError)
        {
            throw FIOError(serializeError.message!!,serializeError)
        }

    }

    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    private fun serializeAndEncryptRecordSendContent(recordSendContent: RecordSendContent, payerPublickey: String): String
    {
        try
        {
            val serializedNewFundsContent = this.serializationProvider.serializeRecordSendContent(recordSendContent.toJson())

            val secretKey = CryptoUtils.generateSharedSecret(this.privateKey,payerPublickey)

            return CryptoUtils.encryptSharedMessage(serializedNewFundsContent,secretKey)
        }
        catch(serializeError: SerializeTransactionError)
        {
            throw FIOError(serializeError.message!!,serializeError)
        }

    }

    /**
     * Create a new funds request on the FIO chain.
     *
     * @param payerFioAddress FIO Address of the payer. This address will receive the request and will initiate payment.
     * @param payeeFioAddress FIO Address of the payee. This address is sending the request and will receive payment.
     * @param fundsRequestContent [FundsRequestContent]
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by [getFee] for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    private fun requestNewFunds(payerFioAddress:String, payeeFioAddress:String,
                        fundsRequestContent: FundsRequestContent, maxFee:BigInteger,
                        walletFioAddress:String): PushTransactionResponse
    {
        var transactionProcessor = NewFundsRequestTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateNewFundsRequest(payerFioAddress,payeeFioAddress,fundsRequestContent,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                val payerPublicKey = this.getFioPublicAddress(payerFioAddress).publicAddress

                val encryptedContent = serializeAndEncryptNewFundsContent(fundsRequestContent,payerPublicKey)

                var newFundsRequestAction = NewFundsRequestAction(
                    payerFioAddress,
                    payeeFioAddress,
                    encryptedContent,
                    maxFee,
                    walletFioAddress,
                    this.publicKey
                )


                var actionList = ArrayList<NewFundsRequestAction>()
                actionList.add(newFundsRequestAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    /**
     * Create a new funds request on the FIO chain.
     *
     * @param payerFioAddress FIO Address of the payer. This address will receive the request and will initiate payment.
     * @param payeeFioAddress FIO Address of the payee. This address is sending the request and will receive payment.
     * @param fundsRequestContent [FundsRequestContent]
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by [getFee] for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    private fun requestNewFunds(payerFioAddress:String, payeeFioAddress:String,
                        fundsRequestContent: FundsRequestContent, maxFee:BigInteger): PushTransactionResponse
    {
        return requestNewFunds(payerFioAddress, payeeFioAddress, fundsRequestContent, maxFee,"")
    }

    /**
     *
     * Records information on the FIO blockchain about a transaction that occurred on other blockchain, i.e. 1 BTC was sent on Bitcoin Blockchain, and both
     * sender and receiver have FIO Addresses. OBT stands for Other Blockchain Transaction
     *
     * @param fioRequestId ID of funds request, if this Record Send transaction is in response to a previously received funds request.  Send empty if no FIO Request ID
     * @param payerFioAddress FIO Address of the payer. This address initiated payment.
     * @param payeeFioAddress FIO Address of the payee. This address is receiving payment.
     * @param recordSendContent [RecordSendContent]
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by /get_fee for correct value.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    private fun recordSend(fioRequestId: BigInteger, payerFioAddress:String, payeeFioAddress:String,
                   recordSendContent: RecordSendContent,
                   maxFee:BigInteger): PushTransactionResponse
    {
        return recordSend(fioRequestId,payerFioAddress,payeeFioAddress,recordSendContent,maxFee,"")
    }

    /**
     *
     * Records information on the FIO blockchain about a transaction that occurred on other blockchain, i.e. 1 BTC was sent on Bitcoin Blockchain, and both
     * sender and receiver have FIO Addresses. OBT stands for Other Blockchain Transaction
     *
     * @param fioRequestId ID of funds request, if this Record Send transaction is in response to a previously received funds request.  Send empty if no FIO Request ID
     * @param payerFioAddress FIO Address of the payer. This address initiated payment.
     * @param payeeFioAddress FIO Address of the payee. This address is receiving payment.
     * @param recordSendContent [RecordSendContent]
     * @param maxFee Maximum amount of SUFs the user is willing to pay for fee. Should be preceded by /get_fee for correct value.
     * @param walletFioAddress FIO Address of the wallet which generates this transaction.
     * @return [PushTransactionResponse]
     *
     * @throws [FIOError]
     */
    @Throws(FIOError::class)
    @ExperimentalUnsignedTypes
    private fun recordSend(fioRequestId: BigInteger, payerFioAddress:String,
                           payeeFioAddress:String, recordSendContent: RecordSendContent,
                   maxFee:BigInteger, walletFioAddress:String): PushTransactionResponse
    {
        var transactionProcessor = RecordSendTrxProcessor(
            this.serializationProvider,
            this.networkProvider,
            this.abiProvider,
            this.signatureProvider
        )

        try
        {
            val validator = validateRecordSendRequest(fioRequestId,payerFioAddress,
                payeeFioAddress,recordSendContent,walletFioAddress)

            if(!validator.isValid)
                throw FIOError(validator.errorMessage!!)
            else
            {
                if(recordSendContent.status == "")
                    recordSendContent.status = "sent_to_blockchain"

                val encryptedContent = serializeAndEncryptRecordSendContent(recordSendContent,this.publicKey)

                var recordSendAction = RecordSendAction(
                    payerFioAddress,
                    payeeFioAddress,
                    encryptedContent,
                    fioRequestId,
                    maxFee,
                    walletFioAddress,
                    this.publicKey
                )


                var actionList = ArrayList<RecordSendAction>()
                actionList.add(recordSendAction)

                @Suppress("UNCHECKED_CAST")
                transactionProcessor.prepare(actionList as ArrayList<IAction>)

                transactionProcessor.sign()

                return transactionProcessor.broadcast()
            }
        }
        catch(fioError:FIOError)
        {
            throw fioError
        }
        catch(prepError: TransactionPrepareError)
        {
            throw FIOError(prepError.message!!,prepError)
        }
        catch(signError: TransactionSignError)
        {
            throw FIOError(signError.message!!,signError)
        }
        catch(broadcastError: TransactionBroadCastError)
        {
            throw FIOError(broadcastError.message!!,broadcastError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    @Throws(FIOError::class)
    private fun getPendingFioRequests(requesteeFioPublicKey:String,limit:Int?=null,offset:Int?=null): List<FIORequestContent>
    {
        try
        {
            val request = GetPendingFIORequestsRequest(requesteeFioPublicKey,limit,offset)
            val response = this.networkProvider.getPendingFIORequests(request)

            for (item in response.requests)
            {
                try
                {
                    val sharedSecretKey = CryptoUtils.generateSharedSecret(this.privateKey, item.payeeFioPublicKey)
                    item.deserializeRequestContent(sharedSecretKey,this.serializationProvider)
                }
                catch(deserializationError: DeserializeTransactionError)
                {
                    //eat this error.  We do not want this error to stop the process.
                }
            }

            return response.requests
        }
        catch(getPendingFIORequestsError: GetPendingFIORequestsError)
        {
            throw FIOError(getPendingFIORequestsError.message!!,getPendingFIORequestsError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    @Throws(FIOError::class)
    private fun getSentFioRequests(senderFioPublicKey:String,limit:Int?=null,offset:Int?=null): List<FIORequestContent>
    {
        try
        {
            val request = GetSentFIORequestsRequest(senderFioPublicKey,limit,offset)
            val response = this.networkProvider.getSentFIORequests(request)

            for (item in response.requests)
            {
                try
                {
                    val sharedSecretKey = CryptoUtils.generateSharedSecret(this.privateKey, item.payerFioPublicKey)
                    item.deserializeRequestContent(sharedSecretKey,this.serializationProvider)
                }
                catch(deserializationError: DeserializeTransactionError)
                {
                    //eat this error.  We do not want this error to stop the process.
                }
            }

            return response.requests
        }
        catch(getSentFIORequestsError: GetSentFIORequestsError)
        {
            throw FIOError(getSentFIORequestsError.message!!,getSentFIORequestsError)
        }
        catch(e:Exception)
        {
            throw FIOError(e.message!!,e)
        }
    }

    private fun validateNewFundsRequest(payerFioAddress:String, payeeFioAddress:String,
                                        fundsRequestContent: FundsRequestContent,walletFioAddress:String): Validator
    {
        var isValid = (payerFioAddress.isFioAddress() && payeeFioAddress.isFioAddress()
                && fundsRequestContent.tokenCode.isTokenCode())

        if(walletFioAddress.isNotEmpty())
            isValid = isValid && walletFioAddress.isFioAddress()

        return Validator(isValid,if(!isValid) "Invalid New Funds Request" else "")
    }

    private fun validateAddPublicAddress(fioAddress:String, tokenCode:String,
                                         tokenPublicAddress:String,
                                        walletFioAddress:String=""): Validator
    {
        var isValid = (fioAddress.isFioAddress()
                && tokenPublicAddress.isNativeBlockChainPublicAddress()
                && tokenCode.isTokenCode())

        if(walletFioAddress.isNotEmpty())
            isValid = isValid && walletFioAddress.isFioAddress()

        return Validator(isValid,if(!isValid) "Invalid AddPublicAddress Request" else "")
    }

    private fun validateRegisterFioAddress(fioAddress:String ,ownerPublicKey:String,
                                           walletFioAddress:String): Validator
    {
        var isValid = fioAddress.isFioAddress()

        if(walletFioAddress.isNotEmpty())
            isValid = isValid && walletFioAddress.isFioAddress()

        if(ownerPublicKey.isNotEmpty())
            isValid = isValid && ownerPublicKey.isFioPublicKey()

        return Validator(isValid,if(!isValid) "Invalid Register FIO Address Request" else "")
    }

    private fun validateRegisterFioDomain(fioDomain:String ,ownerPublicKey:String,
                                           walletFioAddress:String): Validator
    {
        var isValid = fioDomain.isFioDomain()

        if(walletFioAddress.isNotEmpty())
            isValid = isValid && walletFioAddress.isFioAddress()

        if(ownerPublicKey.isNotEmpty())
            isValid = isValid && ownerPublicKey.isFioPublicKey()

        return Validator(isValid,if(!isValid) "Invalid Register FIO Domain Request" else "")
    }

    private fun validateRenewFioAddress(fioAddress:String, walletFioAddress:String): Validator
    {
        try {
            return this.validateRegisterFioAddress(fioAddress,"",walletFioAddress)
        }
        catch(e:Exception)
        {
            throw FIOError("Invalid Renew FIO Address Request")
        }
    }

    private fun validateRenewFioDomain(fioDomain:String, walletFioAddress:String): Validator
    {
        try {
            return this.validateRegisterFioDomain(fioDomain,"",walletFioAddress)
        }
        catch(e:Exception)
        {
            throw FIOError("Invalid Renew FIO Domain Request")
        }
    }

    private fun validateSetFioDomainVisibility(fioDomain:String, walletFioAddress:String): Validator
    {
        var isValid = fioDomain.isFioDomain()

        if(walletFioAddress.isNotEmpty())
            isValid = isValid && walletFioAddress.isFioAddress()

        return Validator(isValid,if(!isValid) "Invalid Set FIO Domain Visibility Request" else "")
    }

    private fun validateRejectFundsRequest(fioRequestId:BigInteger,walletFioAddress:String): Validator
    {
        var isValid = fioRequestId > BigInteger.ZERO

        if(walletFioAddress.isNotEmpty())
            isValid = isValid && walletFioAddress.isFioAddress()

        return Validator(isValid,if(!isValid) "Invalid Reject Funds Request" else "")
    }

    private fun validateRecordSendRequest(fioRequestId: BigInteger, payerFioAddress:String,
                                          payeeFioAddress:String, recordSendContent: RecordSendContent,
                                          walletFioAddress:String): Validator
    {
        var isValid = fioRequestId > BigInteger.ZERO

        isValid = isValid && (payerFioAddress.isFioAddress() && payeeFioAddress.isFioAddress()
                && recordSendContent.tokenCode.isTokenCode())

        if(walletFioAddress.isNotEmpty())
            isValid = isValid && walletFioAddress.isFioAddress()

        return Validator(isValid,if(!isValid) "Invalid Send Record Request" else "")
    }

    private fun validateTransferPublicTokens(payeeFioPublicKey:String, walletFioAddress:String=""): Validator
    {
        var isValid = payeeFioPublicKey.isFioPublicKey()

        if(walletFioAddress.isNotEmpty())
            isValid = isValid && walletFioAddress.isFioAddress()

        return Validator(isValid,if(!isValid) "Invalid Transfer Public Tokens Request" else "")
    }
}