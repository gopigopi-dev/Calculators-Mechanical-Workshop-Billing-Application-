package com.example.calculators.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.calculators.data.BillRepository
import com.example.calculators.data.BillingDatabase
import com.example.calculators.image.ImageAssociation
import com.example.calculators.model.Product
import com.example.calculators.viewmodel.BillHistoryViewModel
import com.example.calculators.viewmodel.BillingViewModel
import com.example.calculators.voice.RecognitionLanguage
import com.example.calculators.voice.SpeechErrorType
import com.example.calculators.voice.SpeechRecognitionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    billingViewModel: BillingViewModel = viewModel(),
    billRepositoryOverride: BillRepository? = null,
    speechRecognitionManagerOverride: SpeechRecognitionManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val billRepository = remember(billRepositoryOverride, context) {
        (billRepositoryOverride ?: BillRepository(BillingDatabase.getInstance(context))).also {
            billingViewModel.attachBillRepository(it)
            billingViewModel.bindImageStorage(context)
        }
    }

    LaunchedEffect(billingViewModel, billRepository) {
        billingViewModel.attachBillRepository(billRepository)
        billingViewModel.bindImageStorage(context)
    }

    val historyViewModel: BillHistoryViewModel = viewModel(
        factory = BillHistoryViewModel.provideFactory(billRepository)
    )

    val uiState by billingViewModel.uiState.collectAsStateWithLifecycle()
    val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()

    val speechManager = remember(speechRecognitionManagerOverride) {
        speechRecognitionManagerOverride ?: SpeechRecognitionManager(
            context = context,
            onStatusChanged = billingViewModel::onVoiceStatusChanged,
            onPartialTranscript = billingViewModel::onPartialSpeechResult,
            onFinalTranscript = billingViewModel::onFinalSpeechResult,
            onError = billingViewModel::onSpeechRecognitionError
        )
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechManager.startListening(uiState.speechRecognitionState.selectedLanguage)
        } else {
            billingViewModel.onSpeechRecognitionError(SpeechErrorType.PERMISSION_DENIED)
        }
    }

    val imageDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Persistable permission is optional depending on document provider
            }
            val displayFilename = resolveDisplayNameFromUri(context, uri)
            billingViewModel.addDeviceImageToCollection(
                imageUri = uri.toString(),
                originalFilename = displayFilename
            )
        }
    }

    val startOrRequestVoiceRecognition: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            speechManager.startListening(uiState.speechRecognitionState.selectedLanguage)
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val handleVoiceButtonToggle: () -> Unit = {
        if (uiState.speechRecognitionState.isListening) {
            speechManager.stopListening()
        } else {
            startOrRequestVoiceRecognition()
        }
    }

    DisposableEffect(lifecycleOwner, speechManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                speechManager.stopListening()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speechManager.release()
        }
    }

    if (uiState.isHistoryScreenOpen) {
        BillHistoryScreen(
            historyState = historyState,
            onSearchQueryChange = historyViewModel::onSearchQueryChanged,
            onDateFilterChange = historyViewModel::onDateFilterChanged,
            onClearFilters = historyViewModel::clearFilters,
            onOpenBillDetail = historyViewModel::openBillDetail,
            onCloseBillDetail = historyViewModel::closeBillDetail,
            onRequestDeleteBill = historyViewModel::requestDeleteBill,
            onConfirmDeleteBill = historyViewModel::confirmDeleteBill,
            onCancelDeleteBill = historyViewModel::cancelDeleteBill,
            onBackToBilling = billingViewModel::closeHistoryScreen,
            onReopenBill = billingViewModel::reopenSavedBill,
            modifier = modifier
        )
    } else {
        BillingScreenContent(
            uiState = uiState,
            savedBillsCount = historyState.allBills.size,
            onOpenAddProductDialog = billingViewModel::openAddProductDialog,
            onOpenEditProductDialog = billingViewModel::openEditProductDialog,
            onCloseAddProductDialog = billingViewModel::closeAddProductDialog,
            onProductNameChange = billingViewModel::onProductNameChanged,
            onQuantityChange = billingViewModel::onQuantityChanged,
            onAmountChange = billingViewModel::onAmountChanged,
            onCalibrationNotesChange = billingViewModel::onCalibrationNotesChanged,
            onSelectFormImageClick = billingViewModel::openFormImageSelector,
            onRemoveFormImageClick = billingViewModel::onRemoveFormImage,
            onSaveProduct = { billingViewModel.saveProduct() },
            onDeleteProduct = billingViewModel::deleteProduct,
            onClearAllProducts = billingViewModel::clearAllProducts,
            onVoiceButtonClick = handleVoiceButtonToggle,
            onLanguageSelected = { language ->
                if (uiState.speechRecognitionState.isListening) {
                    speechManager.stopListening()
                }
                billingViewModel.onRecognitionLanguageSelected(language)
            },
            onTranscriptChanged = billingViewModel::onTranscriptEdited,
            onReprocessTranscript = billingViewModel::reprocessTranscript,
            onClearTranscript = {
                if (uiState.speechRecognitionState.isListening) {
                    speechManager.stopListening()
                }
                billingViewModel.clearTranscript()
            },
            onRetryVoiceRecognition = {
                if (uiState.speechRecognitionState.isListening) {
                    speechManager.stopListening()
                }
                startOrRequestVoiceRecognition()
            },
            onReviewUncertainProduct = billingViewModel::openReviewUncertainProductDialog,
            onProductImageClick = billingViewModel::openImageDialogForProduct,
            onOpenManageImagesDialog = billingViewModel::openManageImagesDialog,
            onCloseImageDialog = billingViewModel::closeImageDialog,
            onSelectImageForProduct = billingViewModel::selectImageForProduct,
            onConfirmUncertainImage = billingViewModel::confirmProductImageMatch,
            onRemoveImageFromProduct = billingViewModel::removeImageFromProduct,
            onAddImageFromDeviceStorage = {
                imageDocumentLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            },
            onRemoveImageFromCollection = billingViewModel::removeImageFromCollection,
            onFinalizeBill = {
                billingViewModel.finalizeBill()
                historyViewModel.refreshHistory()
            },
            onStartNewBill = billingViewModel::startNewBillSession,
            onOpenReceiptPreview = billingViewModel::openReceiptPreview,
            onCloseReceiptPreview = billingViewModel::closeReceiptPreview,
            onReceiptActionMessage = billingViewModel::onReceiptActionMessage,
            onOpenHistoryScreen = {
                historyViewModel.refreshHistory()
                billingViewModel.openHistoryScreen()
            },
            modifier = modifier
        )
    }
}

private fun resolveDisplayNameFromUri(context: Context, uri: Uri): String {
    var displayName: String? = null
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        displayName = cursor.getString(idx)
                    }
                }
            }
    } catch (_: Exception) {
        // Fallback to last path segment below
    }
    return displayName?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "mechanical_part.jpg"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreenContent(
    uiState: BillingUiState,
    savedBillsCount: Int = 0,
    onOpenAddProductDialog: () -> Unit,
    onOpenEditProductDialog: (Product) -> Unit,
    onCloseAddProductDialog: () -> Unit,
    onProductNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCalibrationNotesChange: (String) -> Unit = {},
    onSelectFormImageClick: () -> Unit = {},
    onRemoveFormImageClick: () -> Unit = {},
    onSaveProduct: () -> Unit,
    onDeleteProduct: (String) -> Unit,
    onClearAllProducts: () -> Unit = {},
    onVoiceButtonClick: () -> Unit,
    onLanguageSelected: (RecognitionLanguage) -> Unit = {},
    onTranscriptChanged: (String) -> Unit = {},
    onReprocessTranscript: () -> Unit = {},
    onClearTranscript: () -> Unit = {},
    onRetryVoiceRecognition: () -> Unit = {},
    onReviewUncertainProduct: (Int) -> Unit = {},
    onProductImageClick: (String) -> Unit = {},
    onOpenManageImagesDialog: () -> Unit = {},
    onCloseImageDialog: () -> Unit = {},
    onSelectImageForProduct: (String, ImageAssociation) -> Unit = { _, _ -> },
    onConfirmUncertainImage: (String) -> Unit = {},
    onRemoveImageFromProduct: (String) -> Unit = {},
    onAddImageFromDeviceStorage: () -> Unit = {},
    onRemoveImageFromCollection: (String) -> Unit = {},
    onFinalizeBill: () -> Unit = {},
    onStartNewBill: () -> Unit = {},
    onOpenReceiptPreview: () -> Unit = {},
    onCloseReceiptPreview: () -> Unit = {},
    onReceiptActionMessage: (String?) -> Unit = {},
    onOpenHistoryScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    modifier = Modifier.testTag("top_app_bar"),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(id = R.string.app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = onOpenManageImagesDialog,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("manage_images_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(
                                    id = R.string.button_manage_images,
                                    uiState.availableImages.size
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenHistoryScreen,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("history_nav_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = stringResource(id = R.string.content_desc_open_history),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (savedBillsCount > 0) {
                                    stringResource(id = R.string.button_history_with_count, savedBillsCount)
                                } else {
                                    stringResource(id = R.string.button_history)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.bill_session_label,
                                    uiState.billingSession.sessionId
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("session_id_badge")
                            )
                        }
                    }
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Upper and Middle Calibration Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                ) {
                    // Section B: Calibration Section Heading
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(id = R.string.section_current_bill),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("current_bill_heading")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.products.isNotEmpty()) {
                                TextButton(
                                    onClick = onClearAllProducts,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_all_products_button")
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.button_clear_all),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = stringResource(
                                        id = R.string.items_count_badge,
                                        uiState.products.size
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("items_count_badge")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Section C: Dynamic Calibration Table (1. Image | 2. Items | 3. Quantity | 4. Amount | 5. Action)
                    ProductTable(
                        products = uiState.products,
                        onEditProduct = onOpenEditProductDialog,
                        onDeleteProduct = onDeleteProduct,
                        onImageClick = onProductImageClick,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Section: Voice Recognition Card + Total Amount Card + Finalize / Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Phase 4–9: Voice Recognition, Text Processing, Extraction & Image Matching Section
                    VoiceRecognitionCard(
                        speechState = uiState.speechRecognitionState,
                        onLanguageSelected = onLanguageSelected,
                        onTranscriptChanged = onTranscriptChanged,
                        onReprocessTranscript = onReprocessTranscript,
                        onClearTranscript = onClearTranscript,
                        onRetryVoiceRecognition = onRetryVoiceRecognition,
                        onReviewUncertainProduct = onReviewUncertainProduct
                    )

                    // Phase 10: Finalization Status Banner
                    if (!uiState.finalizeStatusMessage.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (uiState.isFinalizeError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (uiState.isFinalizeError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("finalize_status_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = uiState.finalizeStatusMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (uiState.isFinalizeError) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("finalize_status_text")
                                )

                                if (uiState.isCurrentBillFinalized) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = onOpenReceiptPreview,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("view_receipt_button")
                                        ) {
                                            Text(text = stringResource(id = R.string.button_view_receipt))
                                        }

                                        TextButton(
                                            onClick = onStartNewBill,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("new_bill_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.NoteAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = stringResource(id = R.string.button_new_bill))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section E: Total Amount Card
                    TotalAmountCard(
                        grandTotalDisplay = uiState.grandTotalDisplay
                    )

                    // Section D, F & Phase 10 Finalize Bill: Voice Input + Add Entry + Finalize Bill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MicrophoneButton(
                            isListening = uiState.speechRecognitionState.isListening,
                            onClick = onVoiceButtonClick,
                            modifier = Modifier.weight(0.34f)
                        )

                        OutlinedButton(
                            onClick = onOpenAddProductDialog,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .weight(0.32f)
                                .minimumInteractiveComponentSize()
                                .testTag("add_product_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(id = R.string.button_add_product),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Button(
                            onClick = onFinalizeBill,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .weight(0.34f)
                                .minimumInteractiveComponentSize()
                                .testTag("finalize_bill_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(id = R.string.button_finalize_bill),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isAddProductDialogOpen) {
            AddProductDialog(
                formState = uiState.addProductForm,
                onProductNameChange = onProductNameChange,
                onQuantityChange = onQuantityChange,
                onAmountChange = onAmountChange,
                onCalibrationNotesChange = onCalibrationNotesChange,
                onSelectFormImageClick = onSelectFormImageClick,
                onRemoveFormImageClick = onRemoveFormImageClick,
                onSaveClick = onSaveProduct,
                onCancelClick = onCloseAddProductDialog
            )
        }

        if (uiState.isImageDialogOpen) {
            ImageSelectionDialog(
                targetProduct = uiState.imageDialogTargetProduct,
                availableImages = uiState.availableImages,
                onSelectImageForProduct = onSelectImageForProduct,
                onConfirmUncertainImage = onConfirmUncertainImage,
                onRemoveImageFromProduct = onRemoveImageFromProduct,
                onAddImageFromDeviceStorage = onAddImageFromDeviceStorage,
                onRemoveImageFromCollection = onRemoveImageFromCollection,
                onDismiss = onCloseImageDialog
            )
        }

        val receiptPreview = uiState.finalizedBillPreview
        if (uiState.isReceiptPreviewOpen && receiptPreview != null) {
            BillReceiptPreviewDialog(
                billWithItems = receiptPreview,
                statusMessage = uiState.finalizeStatusMessage,
                actionFeedbackMessage = uiState.receiptActionMessage,
                onPrintResult = onReceiptActionMessage,
                onShareResult = onReceiptActionMessage,
                onStartNewBill = onStartNewBill,
                onDismiss = onCloseReceiptPreview
            )
        }
    }
}
