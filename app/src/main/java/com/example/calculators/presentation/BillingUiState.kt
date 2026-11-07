package com.example.calculators.presentation

import com.example.calculators.data.BillWithItems
import com.example.calculators.image.ImageAssociation
import com.example.calculators.image.ImageCollectionManager
import com.example.calculators.model.BillingSession
import com.example.calculators.model.Product
import com.example.calculators.voice.SpeechRecognitionState

enum class ValidationError {
    EMPTY_PRODUCT_NAME,
    INVALID_QUANTITY,
    INVALID_AMOUNT
}

data class AddProductFormState(
    val editingProductId: String? = null,
    val reviewingUncertainProductIndex: Int? = null,
    val productNameInput: String = "",
    val quantityInput: String = "",
    val amountInput: String = "",
    val calibrationNotesInput: String = "",
    val imageUri: String? = null,
    val imageSourceName: String = "",
    val isImageRemovedByUser: Boolean = false,
    val productNameError: ValidationError? = null,
    val quantityError: ValidationError? = null,
    val amountError: ValidationError? = null
) {
    val isEditMode: Boolean
        get() = editingProductId != null
}

data class BillingUiState(
    val billingSession: BillingSession = BillingSession(),
    val isAddProductDialogOpen: Boolean = false,
    val addProductForm: AddProductFormState = AddProductFormState(),
    val speechRecognitionState: SpeechRecognitionState = SpeechRecognitionState(),
    val availableImages: List<ImageAssociation> = ImageCollectionManager.defaultBundledMechanicalImages(),
    val isImageDialogOpen: Boolean = false,
    val imageDialogTargetProductId: String? = null,
    val isSelectingImageForForm: Boolean = false,
    val isHistoryScreenOpen: Boolean = false,
    val isCurrentBillFinalized: Boolean = false,
    val lastFinalizedBillId: String? = null,
    val finalizeStatusMessage: String? = null,
    val isFinalizeError: Boolean = false,
    val isReceiptPreviewOpen: Boolean = false,
    val finalizedBillPreview: BillWithItems? = null,
    val receiptActionMessage: String? = null
) {
    val products: List<Product>
        get() = billingSession.products

    val grandTotalDisplay: String
        get() = billingSession.grandTotalDisplay

    val imageDialogTargetProduct: Product?
        get() = imageDialogTargetProductId?.let { id ->
            billingSession.products.firstOrNull { it.id == id }
        }
}
