package com.example.calculators.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculators.data.BillRepository
import com.example.calculators.data.BillWithItems
import com.example.calculators.data.FinalizeBillResult
import com.example.calculators.image.ImageAssociation
import com.example.calculators.image.ImageCollectionManager
import com.example.calculators.image.ImageMatchStatus
import com.example.calculators.model.BillingCalculator
import com.example.calculators.model.BillingSession
import com.example.calculators.model.Product
import com.example.calculators.presentation.AddProductFormState
import com.example.calculators.presentation.BillingUiState
import com.example.calculators.presentation.ValidationError
import com.example.calculators.textprocessing.ParsedProduct
import com.example.calculators.textprocessing.ParsingResult
import com.example.calculators.textprocessing.ProcessedTranscript
import com.example.calculators.textprocessing.ProductParser
import com.example.calculators.voice.RecognitionLanguage
import com.example.calculators.voice.SpeechErrorType
import com.example.calculators.voice.VoiceListeningStatus
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BillingViewModel(
    private val productParser: ProductParser = ProductParser(),
    private var billRepository: BillRepository? = null
) : ViewModel() {

    private val imageCollectionManager: ImageCollectionManager
        get() = productParser.imageMatcher.imageCollectionManager

    private val _uiState = MutableStateFlow(
        BillingUiState(
            availableImages = imageCollectionManager.getAllImages()
        )
    )
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    /**
     * Tracks product IDs added from the active transcript session so reprocessing or editing
     * the current transcript updates those entries in-place instead of creating duplicates.
     */
    private var currentTranscriptProductIds: List<String> = emptyList()

    /**
     * Tracks the last finalized content signature in memory in addition to Room database
     * checks so duplicate finalization taps are prevented even if no repository is attached.
     */
    private var lastFinalizedContentSignature: String? = null
    private var sessionCounter: Int = 1

    fun attachBillRepository(repository: BillRepository) {
        if (this.billRepository == null) {
            this.billRepository = repository
        }
    }

    /**
     * Binds the mechanical part image collection manager to persistent device storage
     * (`SharedPreferences`) so custom part-to-image associations and device images survive app restarts.
     */
    fun bindImageStorage(
        context: Context,
        prefsName: String = ImageCollectionManager.DEFAULT_PREFS_NAME
    ) {
        imageCollectionManager.bindPersistentStorage(context, prefsName)
        _uiState.update { current ->
            current.copy(availableImages = imageCollectionManager.getAllImages())
        }
    }

    fun registerCustomProduct(productName: String) {
        productParser.registerUserProduct(productName)
    }

    fun openAddProductDialog() {
        _uiState.update { current ->
            current.copy(
                isAddProductDialogOpen = true,
                addProductForm = AddProductFormState()
            )
        }
    }

    fun openEditProductDialog(product: Product) {
        _uiState.update { current ->
            current.copy(
                isAddProductDialogOpen = true,
                addProductForm = AddProductFormState(
                    editingProductId = product.id,
                    productNameInput = product.productName,
                    quantityInput = product.rawQuantityInput,
                    amountInput = product.rawAmountInput,
                    calibrationNotesInput = product.calibrationNotes,
                    imageUri = product.imageUri,
                    imageSourceName = product.imageSourceName,
                    isImageRemovedByUser = false
                )
            )
        }
    }

    /**
     * Opens the Add Calibration Entry dialog pre-filled with an incomplete or ambiguous extracted entry
     * so the user can manually complete or confirm it.
     */
    fun openReviewUncertainProductDialog(uncertainIndex: Int = 0) {
        val currentSpeech = _uiState.value.speechRecognitionState
        val uncertainProduct = currentSpeech.parsingResult.uncertainProducts.getOrNull(uncertainIndex)
            ?: return

        val quantityPrefill = uncertainProduct.quantityInputPrefill
        val amountPrefill = uncertainProduct.extractedAmount.value
            ?.stripTrailingZeros()
            ?.toPlainString()
            ?: ""

        _uiState.update { current ->
            current.copy(
                isAddProductDialogOpen = true,
                addProductForm = AddProductFormState(
                    reviewingUncertainProductIndex = uncertainIndex,
                    productNameInput = uncertainProduct.productName,
                    quantityInput = quantityPrefill,
                    amountInput = amountPrefill,
                    imageUri = uncertainProduct.imageIdentification.imageUri,
                    imageSourceName = uncertainProduct.imageIdentification.matchedFilename
                )
            )
        }
    }

    fun closeAddProductDialog() {
        _uiState.update { current ->
            current.copy(
                isAddProductDialogOpen = false,
                isSelectingImageForForm = false,
                addProductForm = AddProductFormState()
            )
        }
    }

    fun onProductNameChanged(value: String) {
        val autoMatchedImage = if (value.trim().isNotEmpty()) {
            productParser.imageMatcher.identifyImageForPart(value.trim())
        } else {
            null
        }
        _uiState.update { current ->
            val currentForm = current.addProductForm
            val shouldUseAutoImage = !currentForm.isImageRemovedByUser
            current.copy(
                addProductForm = currentForm.copy(
                    productNameInput = value,
                    imageUri = if (shouldUseAutoImage) {
                        autoMatchedImage?.imageUri ?: currentForm.imageUri
                    } else {
                        null
                    },
                    imageSourceName = if (shouldUseAutoImage) {
                        autoMatchedImage?.matchedFilename?.ifEmpty { currentForm.imageSourceName } ?: currentForm.imageSourceName
                    } else {
                        ""
                    },
                    productNameError = null
                )
            )
        }
    }

    fun onQuantityChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                addProductForm = current.addProductForm.copy(
                    quantityInput = value,
                    quantityError = null
                )
            )
        }
    }

    fun onAmountChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                addProductForm = current.addProductForm.copy(
                    amountInput = value,
                    amountError = null
                )
            )
        }
    }

    fun onCalibrationNotesChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                addProductForm = current.addProductForm.copy(
                    calibrationNotesInput = value
                )
            )
        }
    }

    fun onRemoveFormImage() {
        _uiState.update { current ->
            current.copy(
                addProductForm = current.addProductForm.copy(
                    imageUri = null,
                    imageSourceName = "",
                    isImageRemovedByUser = true
                )
            )
        }
    }

    fun openFormImageSelector() {
        _uiState.update { current ->
            current.copy(
                isImageDialogOpen = true,
                isSelectingImageForForm = true,
                imageDialogTargetProductId = current.addProductForm.editingProductId,
                availableImages = imageCollectionManager.getAllImages()
            )
        }
    }

    /**
     * Validates and saves the calibration/part entry.
     *
     * Rules:
     * - Quantity is optional (`null` when blank; never defaulted to 1).
     * - If Quantity is entered, it must be a valid positive number.
     * - Amount is entered and validated independently.
     * - Automatically identifies a matching mechanical part image unless manually chosen or removed.
     */
    fun saveProduct(): Boolean {
        val currentForm = _uiState.value.addProductForm
        val trimmedName = currentForm.productNameInput.trim()
        val trimmedQuantityInput = currentForm.quantityInput.trim()
        val parsedOptionalQuantity: Double? = if (trimmedQuantityInput.isEmpty()) {
            null
        } else {
            trimmedQuantityInput.toDoubleOrNull()
        }
        val parsedAmount = currentForm.amountInput.trim().toDoubleOrNull()
        val trimmedNotes = currentForm.calibrationNotesInput.trim()

        val nameError = if (!Product.isValidProductName(trimmedName)) {
            ValidationError.EMPTY_PRODUCT_NAME
        } else {
            null
        }

        val quantityError = if (!Product.isValidOptionalQuantityInput(trimmedQuantityInput)) {
            ValidationError.INVALID_QUANTITY
        } else {
            null
        }

        val amountError = if (!Product.isValidAmount(parsedAmount)) {
            ValidationError.INVALID_AMOUNT
        } else {
            null
        }

        if (nameError != null || quantityError != null || amountError != null) {
            _uiState.update { current ->
                current.copy(
                    addProductForm = current.addProductForm.copy(
                        productNameError = nameError,
                        quantityError = quantityError,
                        amountError = amountError
                    )
                )
            }
            return false
        }

        val validAmount = parsedAmount!!
        val editingId = currentForm.editingProductId
        val reviewingUncertainIndex = currentForm.reviewingUncertainProductIndex

        // Register user-defined part/machine name in dictionary for future transcript identification
        productParser.registerUserProduct(trimmedName)

        if (currentForm.isImageRemovedByUser) {
            imageCollectionManager.removePartImageAssociation(trimmedName)
        }

        // Phase 9: Identify mechanical part image by filename / user association
        val imageResult = productParser.imageMatcher.identifyImageForPart(trimmedName)

        _uiState.update { current ->
            val updatedProducts = if (editingId == null) {
                val resolvedUri = if (currentForm.isImageRemovedByUser) {
                    null
                } else {
                    currentForm.imageUri ?: imageResult.imageUri
                }
                val resolvedSourceName = if (currentForm.isImageRemovedByUser) {
                    ""
                } else {
                    currentForm.imageSourceName.ifEmpty { imageResult.matchedFilename }
                }
                val newProduct = Product(
                    id = UUID.randomUUID().toString(),
                    productName = trimmedName,
                    quantity = parsedOptionalQuantity,
                    amount = validAmount,
                    calibrationNotes = trimmedNotes,
                    imageUri = resolvedUri,
                    imageSourceName = resolvedSourceName,
                    imageMatchStatus = when {
                        resolvedUri == null -> ImageMatchStatus.NO_MATCH
                        currentForm.imageUri != null && imageResult.imageUri == null -> ImageMatchStatus.USER_ASSOCIATION_MATCH
                        else -> imageResult.status
                    },
                    imageNeedsConfirmation = !currentForm.isImageRemovedByUser && imageResult.requiresUserConfirmation
                )
                current.billingSession.products + newProduct
            } else {
                current.billingSession.products.map { existing ->
                    if (existing.id == editingId) {
                        val nameChanged = !existing.productName.equals(trimmedName, ignoreCase = true)
                        val resolvedUri = when {
                            currentForm.isImageRemovedByUser -> null
                            nameChanged -> currentForm.imageUri ?: imageResult.imageUri
                            else -> currentForm.imageUri ?: existing.imageUri ?: imageResult.imageUri
                        }
                        val resolvedSourceName = when {
                            currentForm.isImageRemovedByUser -> ""
                            nameChanged -> currentForm.imageSourceName.ifEmpty { imageResult.matchedFilename }
                            else -> currentForm.imageSourceName.ifEmpty {
                                existing.imageSourceName.ifEmpty { imageResult.matchedFilename }
                            }
                        }
                        existing.copy(
                            productName = trimmedName,
                            quantity = parsedOptionalQuantity,
                            amount = validAmount,
                            calibrationNotes = trimmedNotes,
                            imageUri = resolvedUri,
                            imageSourceName = resolvedSourceName,
                            imageMatchStatus = when {
                                resolvedUri == null -> ImageMatchStatus.NO_MATCH
                                nameChanged -> imageResult.status
                                else -> existing.imageMatchStatus
                            },
                            imageNeedsConfirmation = if (currentForm.isImageRemovedByUser) {
                                false
                            } else if (nameChanged) {
                                imageResult.requiresUserConfirmation
                            } else {
                                existing.imageNeedsConfirmation
                            }
                        )
                    } else {
                        existing
                    }
                }
            }

            val updatedSpeechState = if (reviewingUncertainIndex != null) {
                val uncertainTarget = current.speechRecognitionState.parsingResult
                    .uncertainProducts
                    .getOrNull(reviewingUncertainIndex)
                if (uncertainTarget != null) {
                    val remainingEntries = current.speechRecognitionState.parsingResult.parsedProducts
                        .filterNot { it === uncertainTarget }
                    current.speechRecognitionState.copy(
                        parsingResult = current.speechRecognitionState.parsingResult.copy(
                            parsedProducts = remainingEntries
                        )
                    )
                } else {
                    current.speechRecognitionState
                }
            } else {
                current.speechRecognitionState
            }

            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                speechRecognitionState = updatedSpeechState,
                isAddProductDialogOpen = false,
                isSelectingImageForForm = false,
                addProductForm = AddProductFormState(),
                isCurrentBillFinalized = false,
                finalizeStatusMessage = null,
                isFinalizeError = false
            )
        }
        return true
    }

    fun deleteProduct(productId: String) {
        currentTranscriptProductIds = currentTranscriptProductIds.filterNot { it == productId }
        _uiState.update { current ->
            val updatedProducts = current.billingSession.products.filterNot { it.id == productId }
            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                isCurrentBillFinalized = false,
                finalizeStatusMessage = null,
                isFinalizeError = false
            )
        }
    }

    fun clearAllProducts() {
        currentTranscriptProductIds = emptyList()
        _uiState.update { current ->
            current.copy(
                billingSession = recalculateSession(current.billingSession, emptyList()),
                isCurrentBillFinalized = false,
                finalizeStatusMessage = null,
                isFinalizeError = false
            )
        }
    }

    // --- Phase 10: Bill Finalization, Automatic Saving & History Navigation ---

    fun openHistoryScreen() {
        _uiState.update { current ->
            current.copy(isHistoryScreenOpen = true)
        }
    }

    fun closeHistoryScreen() {
        _uiState.update { current ->
            current.copy(isHistoryScreenOpen = false)
        }
    }

    fun openReceiptPreview() {
        val current = _uiState.value
        val preview = current.finalizedBillPreview ?: if (current.billingSession.products.isNotEmpty()) {
            val billId = current.lastFinalizedBillId ?: String.format(Locale.US, "BILL-%04d", sessionCounter)
            val transcript = current.speechRecognitionState.originalTranscript
                .ifBlank { current.speechRecognitionState.transcript }
            BillRepository.buildBillWithItemsSnapshot(
                billId = billId,
                session = current.billingSession,
                originalTranscript = transcript
            )
        } else {
            null
        }
        if (preview != null) {
            _uiState.update { state ->
                state.copy(
                    isReceiptPreviewOpen = true,
                    finalizedBillPreview = preview,
                    receiptActionMessage = null
                )
            }
        }
    }

    fun closeReceiptPreview() {
        _uiState.update { current ->
            current.copy(
                isReceiptPreviewOpen = false,
                receiptActionMessage = null
            )
        }
    }

    fun onReceiptActionMessage(message: String?) {
        _uiState.update { current ->
            current.copy(receiptActionMessage = message)
        }
    }

    /**
     * Finalizes the current bill, immediately generates and opens the formatted workshop receipt
     * preview, and automatically persists the bill to the local Room database.
     *
     * Rules:
     * - Auto-processes any unmerged confident transcript text before checking items.
     * - Does not silently save empty bills.
     * - Prevents accidental duplicate saving when the user taps Finalize multiple times, while
     *   re-displaying the receipt preview for the already-finalized bill.
     */
    fun finalizeBill(timestampMillis: Long = System.currentTimeMillis()) {
        // Auto-merge typed transcript entries if the user tapped Finalize Bill directly after typing
        val initialSpeech = _uiState.value.speechRecognitionState
        if (initialSpeech.transcript.isNotBlank() &&
            (_uiState.value.billingSession.products.isEmpty() || currentTranscriptProductIds.isEmpty())
        ) {
            val preservedOriginal = initialSpeech.originalTranscript.ifBlank { initialSpeech.transcript }
            val parsed = productParser.parse(
                rawTranscript = initialSpeech.transcript,
                preservedOriginal = preservedOriginal
            )
            if (parsed.confidentProducts.isNotEmpty()) {
                reprocessTranscript()
            }
        }

        val currentState = _uiState.value
        val session = currentState.billingSession
        val products = session.products

        if (products.isEmpty()) {
            val uncertainCount = currentState.speechRecognitionState.parsingResult.uncertainProducts.size
            val emptyMessage = if (uncertainCount > 0) {
                "Please review or clear $uncertainCount incomplete voice entry before finalizing."
            } else {
                "Cannot finalize an empty bill. Add at least one mechanical part or service entry."
            }
            _uiState.update { current ->
                current.copy(
                    isCurrentBillFinalized = false,
                    isReceiptPreviewOpen = false,
                    finalizeStatusMessage = emptyMessage,
                    isFinalizeError = true
                )
            }
            return
        }

        val transcript = currentState.speechRecognitionState.originalTranscript
            .ifBlank { currentState.speechRecognitionState.transcript }

        val signature = BillRepository.buildContentSignature(
            sessionId = session.sessionId,
            products = products,
            originalTranscript = transcript
        )

        if (currentState.isCurrentBillFinalized && lastFinalizedContentSignature == signature) {
            val existingId = currentState.lastFinalizedBillId
                ?: String.format(Locale.US, "BILL-%04d", sessionCounter)
            val existingPreview = currentState.finalizedBillPreview
                ?: BillRepository.buildBillWithItemsSnapshot(
                    billId = existingId,
                    session = session,
                    originalTranscript = transcript,
                    timestampMillis = timestampMillis,
                    signature = signature
                )
            _uiState.update { current ->
                current.copy(
                    isCurrentBillFinalized = true,
                    isReceiptPreviewOpen = true,
                    finalizedBillPreview = existingPreview,
                    finalizeStatusMessage = "Bill $existingId is already finalized and saved in History.",
                    isFinalizeError = false,
                    receiptActionMessage = null
                )
            }
            return
        }

        // Generate the receipt preview immediately so the UI responds on the exact frame of the click
        val optimisticBillId = currentState.lastFinalizedBillId
            ?: String.format(Locale.US, "BILL-%04d", sessionCounter)
        val immediatePreview = BillRepository.buildBillWithItemsSnapshot(
            billId = optimisticBillId,
            session = session,
            originalTranscript = transcript,
            timestampMillis = timestampMillis,
            signature = signature
        )
        lastFinalizedContentSignature = signature

        _uiState.update { current ->
            current.copy(
                isCurrentBillFinalized = true,
                lastFinalizedBillId = optimisticBillId,
                isReceiptPreviewOpen = true,
                finalizedBillPreview = immediatePreview,
                finalizeStatusMessage = "Bill $optimisticBillId finalized and saved to History.",
                isFinalizeError = false,
                receiptActionMessage = null
            )
        }

        val repo = billRepository
        if (repo != null) {
            viewModelScope.launch {
                try {
                    when (val result = repo.finalizeAndSaveBill(session, transcript, timestampMillis)) {
                        is FinalizeBillResult.Saved -> {
                            val savedBillId = result.billWithItems.bill.billId
                            _uiState.update { current ->
                                current.copy(
                                    isCurrentBillFinalized = true,
                                    lastFinalizedBillId = savedBillId,
                                    finalizedBillPreview = result.billWithItems,
                                    finalizeStatusMessage = "Bill $savedBillId finalized and saved to History.",
                                    isFinalizeError = false
                                )
                            }
                        }
                        is FinalizeBillResult.DuplicatePrevented -> {
                            val existingBillId = result.existingBill.billId
                            val syncedPreview = BillRepository.buildBillWithItemsSnapshot(
                                billId = existingBillId,
                                session = session,
                                originalTranscript = transcript,
                                timestampMillis = result.existingBill.createdAtMillis,
                                signature = signature
                            )
                            _uiState.update { current ->
                                current.copy(
                                    isCurrentBillFinalized = true,
                                    lastFinalizedBillId = existingBillId,
                                    finalizedBillPreview = syncedPreview,
                                    finalizeStatusMessage = "Bill $existingBillId is already finalized and saved in History.",
                                    isFinalizeError = false
                                )
                            }
                        }
                        is FinalizeBillResult.InvalidBill -> {
                            lastFinalizedContentSignature = null
                            _uiState.update { current ->
                                current.copy(
                                    isCurrentBillFinalized = false,
                                    isReceiptPreviewOpen = false,
                                    finalizeStatusMessage = result.reason,
                                    isFinalizeError = true
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            finalizeStatusMessage = "Bill $optimisticBillId generated (local storage warning: ${e.message ?: "Error"}).",
                            isFinalizeError = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Reopens a previously finalized bill from History into the active calibration workspace
     * without creating inconsistent or duplicate records.
     */
    fun reopenSavedBill(billWithItems: BillWithItems) {
        val bill = billWithItems.bill
        val reopenedProducts = billWithItems.sortedItems.map { item ->
            Product(
                id = item.itemId,
                productName = item.productName,
                quantity = item.quantity,
                amount = item.amount,
                isUnitPrice = item.isUnitPrice,
                calibrationNotes = item.calibrationNotes,
                imageUri = item.imageUri,
                imageSourceName = item.imageSourceName,
                imageMatchStatus = if (!item.imageUri.isNullOrBlank()) {
                    ImageMatchStatus.EXACT_FILENAME_MATCH
                } else {
                    ImageMatchStatus.NO_MATCH
                },
                imageNeedsConfirmation = false
            )
        }

        val restoredTranscript = bill.originalTranscript
        val parsingResult = if (restoredTranscript.isNotBlank()) {
            productParser.parse(
                rawTranscript = restoredTranscript,
                preservedOriginal = restoredTranscript
            )
        } else {
            ParsingResult()
        }

        currentTranscriptProductIds = reopenedProducts.map { it.id }
        lastFinalizedContentSignature = BillRepository.buildContentSignature(
            sessionId = bill.sessionId,
            products = reopenedProducts,
            originalTranscript = restoredTranscript
        )

        _uiState.update { current ->
            current.copy(
                billingSession = recalculateSession(
                    BillingSession(sessionId = bill.sessionId),
                    reopenedProducts
                ),
                speechRecognitionState = current.speechRecognitionState.copy(
                    status = VoiceListeningStatus.IDLE,
                    originalTranscript = restoredTranscript,
                    transcript = restoredTranscript,
                    partialTranscript = "",
                    processedTranscript = parsingResult.processedTranscript,
                    parsingResult = parsingResult,
                    errorType = null
                ),
                isHistoryScreenOpen = false,
                isCurrentBillFinalized = true,
                lastFinalizedBillId = bill.billId,
                finalizedBillPreview = billWithItems,
                isReceiptPreviewOpen = false,
                finalizeStatusMessage = "Reopened Bill ${bill.billId} (${bill.sessionId}).",
                isFinalizeError = false,
                receiptActionMessage = null
            )
        }
    }

    /**
     * Starts a fresh calibration billing session after finalizing a bill.
     */
    fun startNewBillSession() {
        sessionCounter += 1
        currentTranscriptProductIds = emptyList()
        val nextSessionId = String.format(Locale.US, "CAL-%04d", sessionCounter)
        _uiState.update { current ->
            current.copy(
                billingSession = BillingSession(sessionId = nextSessionId),
                speechRecognitionState = current.speechRecognitionState.copy(
                    status = VoiceListeningStatus.IDLE,
                    originalTranscript = "",
                    transcript = "",
                    partialTranscript = "",
                    processedTranscript = ProcessedTranscript(),
                    parsingResult = ParsingResult(),
                    errorType = null
                ),
                isCurrentBillFinalized = false,
                lastFinalizedBillId = null,
                isReceiptPreviewOpen = false,
                finalizedBillPreview = null,
                finalizeStatusMessage = null,
                isFinalizeError = false,
                receiptActionMessage = null
            )
        }
    }

    // --- Phase 9: Manual Image Correction, Confirmation & Image Collection Management ---

    fun openImageDialogForProduct(productId: String) {
        _uiState.update { current ->
            current.copy(
                isImageDialogOpen = true,
                isSelectingImageForForm = false,
                imageDialogTargetProductId = productId,
                availableImages = imageCollectionManager.getAllImages()
            )
        }
    }

    fun openManageImagesDialog() {
        _uiState.update { current ->
            current.copy(
                isImageDialogOpen = true,
                isSelectingImageForForm = false,
                imageDialogTargetProductId = null,
                availableImages = imageCollectionManager.getAllImages()
            )
        }
    }

    fun closeImageDialog() {
        _uiState.update { current ->
            current.copy(
                isImageDialogOpen = false,
                isSelectingImageForForm = false,
                imageDialogTargetProductId = null
            )
        }
    }

    /**
     * Manually selects or reassigns an image to a calibration entry (or the active Add/Edit form)
     * and updates the local part-to-image association so future mentions of the same part use this image.
     */
    fun selectImageForProduct(productId: String, imageAssociation: ImageAssociation) {
        val currentState = _uiState.value
        if (currentState.isSelectingImageForForm) {
            val formName = currentState.addProductForm.productNameInput.trim()
            if (formName.isNotEmpty()) {
                imageCollectionManager.assignImageToPart(formName, imageAssociation.id)
            }
            _uiState.update { current ->
                current.copy(
                    addProductForm = current.addProductForm.copy(
                        imageUri = imageAssociation.imageUri,
                        imageSourceName = imageAssociation.originalFilename,
                        isImageRemovedByUser = false
                    ),
                    availableImages = imageCollectionManager.getAllImages(),
                    isImageDialogOpen = false,
                    isSelectingImageForForm = false,
                    imageDialogTargetProductId = null
                )
            }
            return
        }

        val targetProduct = currentState.billingSession.products.firstOrNull { it.id == productId }
            ?: return

        imageCollectionManager.assignImageToPart(targetProduct.productName, imageAssociation.id)

        _uiState.update { current ->
            val updatedProducts = current.billingSession.products.map { prod ->
                if (prod.id == productId || prod.productName.equals(targetProduct.productName, ignoreCase = true)) {
                    prod.copy(
                        imageUri = imageAssociation.imageUri,
                        imageSourceName = imageAssociation.originalFilename,
                        imageMatchStatus = ImageMatchStatus.USER_ASSOCIATION_MATCH,
                        imageNeedsConfirmation = false
                    )
                } else {
                    prod
                }
            }
            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                availableImages = imageCollectionManager.getAllImages(),
                isImageDialogOpen = false,
                isSelectingImageForForm = false,
                imageDialogTargetProductId = null,
                isCurrentBillFinalized = false,
                finalizeStatusMessage = null
            )
        }
    }

    /**
     * Confirms an uncertain image match (e.g. from an unclear IMG_... filename or similar filenames).
     */
    fun confirmProductImageMatch(productId: String) {
        val targetProduct = _uiState.value.billingSession.products.firstOrNull { it.id == productId }
            ?: return
        val matchedAssoc = imageCollectionManager.getImageByUri(targetProduct.imageUri)
        if (matchedAssoc != null) {
            imageCollectionManager.assignImageToPart(targetProduct.productName, matchedAssoc.id)
        }

        _uiState.update { current ->
            val updatedProducts = current.billingSession.products.map { prod ->
                if (prod.id == productId) {
                    prod.copy(
                        imageMatchStatus = ImageMatchStatus.USER_ASSOCIATION_MATCH,
                        imageNeedsConfirmation = false
                    )
                } else {
                    prod
                }
            }
            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                availableImages = imageCollectionManager.getAllImages(),
                isImageDialogOpen = false,
                imageDialogTargetProductId = null
            )
        }
    }

    /**
     * Removes an incorrect image association from a product and suppresses auto-matching
     * for that part name until the user manually reassigns an image.
     */
    fun removeImageFromProduct(productId: String) {
        val targetProduct = _uiState.value.billingSession.products.firstOrNull { it.id == productId }
            ?: return

        imageCollectionManager.removePartImageAssociation(targetProduct.productName)

        _uiState.update { current ->
            val updatedProducts = current.billingSession.products.map { prod ->
                if (prod.id == productId) {
                    prod.copy(
                        imageUri = null,
                        imageSourceName = "",
                        imageMatchStatus = ImageMatchStatus.NO_MATCH,
                        imageNeedsConfirmation = false
                    )
                } else {
                    prod
                }
            }
            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                availableImages = imageCollectionManager.getAllImages(),
                isImageDialogOpen = false,
                imageDialogTargetProductId = null,
                isCurrentBillFinalized = false,
                finalizeStatusMessage = null
            )
        }
    }

    /**
     * Adds a user-selected image from Android device storage to the local collection,
     * extracting its filename keyword and optionally assigning it to the active product.
     */
    fun addDeviceImageToCollection(
        imageUri: String,
        originalFilename: String,
        assignToProductId: String? = _uiState.value.imageDialogTargetProductId
    ) {
        val targetProduct = assignToProductId?.let { id ->
            _uiState.value.billingSession.products.firstOrNull { it.id == id }
        }
        val addedAssociation = imageCollectionManager.addOrUpdateImageFromStorage(
            imageUri = imageUri,
            originalFilename = originalFilename,
            assignedPartName = targetProduct?.productName
        )

        _uiState.update { current ->
            val updatedProducts = current.billingSession.products.map { prod ->
                if (targetProduct != null && prod.id == targetProduct.id) {
                    prod.copy(
                        imageUri = addedAssociation.imageUri,
                        imageSourceName = addedAssociation.originalFilename,
                        imageMatchStatus = ImageMatchStatus.USER_ASSOCIATION_MATCH,
                        imageNeedsConfirmation = false
                    )
                } else if (prod.imageUri == null) {
                    val matchResult = productParser.imageMatcher.identifyImageForPart(prod.productName)
                    if (matchResult.hasImage) {
                        prod.copy(
                            imageUri = matchResult.imageUri,
                            imageSourceName = matchResult.matchedFilename,
                            imageMatchStatus = matchResult.status,
                            imageNeedsConfirmation = matchResult.requiresUserConfirmation
                        )
                    } else {
                        prod
                    }
                } else {
                    prod
                }
            }
            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                availableImages = imageCollectionManager.getAllImages(),
                isImageDialogOpen = if (targetProduct != null) false else current.isImageDialogOpen,
                imageDialogTargetProductId = if (targetProduct != null) null else current.imageDialogTargetProductId
            )
        }
    }

    fun removeImageFromCollection(imageId: String) {
        val removedAssoc = imageCollectionManager.getImageById(imageId)
        imageCollectionManager.removeImage(imageId)

        _uiState.update { current ->
            val updatedProducts = if (removedAssoc != null) {
                current.billingSession.products.map { prod ->
                    if (prod.imageUri == removedAssoc.imageUri) {
                        prod.copy(
                            imageUri = null,
                            imageSourceName = "",
                            imageMatchStatus = ImageMatchStatus.NO_MATCH,
                            imageNeedsConfirmation = false
                        )
                    } else {
                        prod
                    }
                }
            } else {
                current.billingSession.products
            }
            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                availableImages = imageCollectionManager.getAllImages()
            )
        }
    }

    private fun recalculateSession(
        session: BillingSession,
        products: List<Product>
    ): BillingSession {
        val total = BillingCalculator.calculateGrandTotal(products)
        val formattedTotal = BillingCalculator.formatCurrency(total)
        return session.copy(
            products = products,
            grandTotal = total,
            grandTotalDisplay = formattedTotal
        )
    }

    // --- Speech Recognition, Text Processing & Part/Calibration Extraction ---

    fun onRecognitionLanguageSelected(language: RecognitionLanguage) {
        _uiState.update { current ->
            current.copy(
                speechRecognitionState = current.speechRecognitionState.copy(
                    selectedLanguage = language,
                    errorType = null,
                    status = if (current.speechRecognitionState.status == VoiceListeningStatus.ERROR) {
                        VoiceListeningStatus.IDLE
                    } else {
                        current.speechRecognitionState.status
                    }
                )
            )
        }
    }

    fun onVoiceStatusChanged(status: VoiceListeningStatus) {
        if (status == VoiceListeningStatus.LISTENING) {
            currentTranscriptProductIds = emptyList()
        }
        _uiState.update { current ->
            current.copy(
                speechRecognitionState = current.speechRecognitionState.copy(
                    status = status,
                    errorType = if (status == VoiceListeningStatus.LISTENING || status == VoiceListeningStatus.PROCESSING) {
                        null
                    } else {
                        current.speechRecognitionState.errorType
                    },
                    partialTranscript = if (status == VoiceListeningStatus.IDLE) {
                        ""
                    } else {
                        current.speechRecognitionState.partialTranscript
                    },
                    isVoicePanelVisible = true
                )
            )
        }
    }

    fun onPartialSpeechResult(partialText: String) {
        _uiState.update { current ->
            current.copy(
                speechRecognitionState = current.speechRecognitionState.copy(
                    partialTranscript = partialText,
                    isVoicePanelVisible = true
                )
            )
        }
    }

    fun onFinalSpeechResult(recognizedText: String) {
        val parsingResult = productParser.parse(
            rawTranscript = recognizedText,
            preservedOriginal = recognizedText
        )
        _uiState.update { current ->
            val updatedProducts = mergeConfidentProductsIntoBill(
                existingProducts = current.billingSession.products,
                confidentParsedProducts = parsingResult.confidentProducts
            )
            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                speechRecognitionState = current.speechRecognitionState.copy(
                    status = VoiceListeningStatus.SUCCESS,
                    originalTranscript = recognizedText,
                    transcript = recognizedText,
                    partialTranscript = "",
                    processedTranscript = parsingResult.processedTranscript,
                    parsingResult = parsingResult,
                    errorType = null,
                    isVoicePanelVisible = true
                ),
                isCurrentBillFinalized = false,
                finalizeStatusMessage = null,
                isFinalizeError = false
            )
        }
    }

    fun onSpeechRecognitionError(errorType: SpeechErrorType) {
        _uiState.update { current ->
            current.copy(
                speechRecognitionState = current.speechRecognitionState.copy(
                    status = VoiceListeningStatus.ERROR,
                    errorType = errorType,
                    partialTranscript = "",
                    isVoicePanelVisible = true
                )
            )
        }
    }

    /**
     * Updates the editable transcript and runs live text normalization & part/amount/image parsing preview
     * without overwriting the original voice transcript (unless no voice transcript existed yet).
     */
    fun onTranscriptEdited(updatedTranscript: String) {
        _uiState.update { current ->
            val currentSpeech = current.speechRecognitionState
            val preservedOriginal = if (currentSpeech.originalTranscript.isNotBlank()) {
                currentSpeech.originalTranscript
            } else {
                updatedTranscript
            }
            val parsingResult = productParser.parse(
                rawTranscript = updatedTranscript,
                preservedOriginal = preservedOriginal
            )
            current.copy(
                speechRecognitionState = currentSpeech.copy(
                    originalTranscript = if (updatedTranscript.isBlank() && currentSpeech.originalTranscript.isBlank()) {
                        ""
                    } else {
                        preservedOriginal
                    },
                    transcript = updatedTranscript,
                    partialTranscript = "",
                    processedTranscript = parsingResult.processedTranscript,
                    parsingResult = parsingResult
                )
            )
        }
    }

    /**
     * Explicitly runs the text-processing, extraction, and mechanical part image matching engine
     * on the current editable transcript and synchronizes confident entries into the calibration
     * table without creating duplicates when re-run on the same transcript.
     */
    fun reprocessTranscript() {
        _uiState.update { current ->
            val currentSpeech = current.speechRecognitionState
            val preservedOriginal = if (currentSpeech.originalTranscript.isNotBlank()) {
                currentSpeech.originalTranscript
            } else {
                currentSpeech.transcript
            }
            val parsingResult = productParser.parse(
                rawTranscript = currentSpeech.transcript,
                preservedOriginal = preservedOriginal
            )
            val updatedProducts = mergeConfidentProductsIntoBill(
                existingProducts = current.billingSession.products,
                confidentParsedProducts = parsingResult.confidentProducts
            )
            current.copy(
                billingSession = recalculateSession(current.billingSession, updatedProducts),
                speechRecognitionState = currentSpeech.copy(
                    processedTranscript = parsingResult.processedTranscript,
                    parsingResult = parsingResult
                ),
                isCurrentBillFinalized = false,
                finalizeStatusMessage = null,
                isFinalizeError = false
            )
        }
    }

    fun clearTranscript() {
        currentTranscriptProductIds = emptyList()
        _uiState.update { current ->
            current.copy(
                speechRecognitionState = current.speechRecognitionState.copy(
                    status = VoiceListeningStatus.IDLE,
                    originalTranscript = "",
                    transcript = "",
                    partialTranscript = "",
                    processedTranscript = ProcessedTranscript(),
                    parsingResult = ParsingResult(),
                    errorType = null
                )
            )
        }
    }

    private fun mergeConfidentProductsIntoBill(
        existingProducts: List<Product>,
        confidentParsedProducts: List<ParsedProduct>
    ): List<Product> {
        val previousTranscriptIds = currentTranscriptProductIds.toSet()

        val newTranscriptProducts = confidentParsedProducts.mapIndexedNotNull { index, parsed ->
            val existingId = currentTranscriptProductIds.getOrNull(index) ?: UUID.randomUUID().toString()
            parsed.toProductOrNull(customId = existingId)
        }

        val newIds = newTranscriptProducts.map { it.id }

        if (previousTranscriptIds.isEmpty()) {
            currentTranscriptProductIds = newIds
            return existingProducts + newTranscriptProducts
        }

        val firstPreviousIndex = existingProducts.indexOfFirst { it.id in previousTranscriptIds }
        val retainedProducts = existingProducts.filterNot { it.id in previousTranscriptIds }
        currentTranscriptProductIds = newIds

        return if (firstPreviousIndex in 0..retainedProducts.size) {
            val mutable = retainedProducts.toMutableList()
            mutable.addAll(firstPreviousIndex, newTranscriptProducts)
            mutable
        } else {
            retainedProducts + newTranscriptProducts
        }
    }
}
