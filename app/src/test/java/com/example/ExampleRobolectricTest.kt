package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.calculators.MainActivity
import com.example.calculators.data.BillRepository
import com.example.calculators.data.BillingDatabase
import com.example.calculators.data.FinalizeBillResult
import com.example.calculators.image.ImageCollectionManager
import com.example.calculators.image.ImageFilenameMatcher
import com.example.calculators.image.ImageMatchStatus
import com.example.calculators.image.MechanicalPartImageMatcher
import com.example.calculators.model.BillingCalculator
import com.example.calculators.model.BillingSession
import com.example.calculators.model.Product
import com.example.calculators.textprocessing.DetectedLanguage
import com.example.calculators.textprocessing.ProductDictionary
import com.example.calculators.textprocessing.ProductParser
import com.example.calculators.textprocessing.TextNormalizer
import com.example.calculators.viewmodel.BillHistoryViewModel
import com.example.calculators.viewmodel.BillingViewModel
import com.example.calculators.voice.RecognitionLanguage
import com.example.calculators.voice.SpeechErrorType
import com.example.calculators.voice.SpeechRecognitionListener
import com.example.calculators.voice.SpeechRecognitionManager
import com.example.calculators.voice.SpeechRecognizerGateway
import com.example.calculators.voice.VoiceListeningStatus
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `verify mechanical calibration domain labels, 5-column table header with Image and Items, and initial screen state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Calculators", appName)

        // Ensure no hardcoded grocery product list exists
        assertTrue(ProductDictionary.DEFAULT_PRODUCTS.isEmpty())

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Calculators").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mechanical Parts Calibration").assertIsDisplayed()
        composeTestRule.onNodeWithText("Calibration Records").assertIsDisplayed()

        // Verify strict 5-column order: 1. Image | 2. Items | 3. Quantity | 4. Amount | 5. Action
        val imageHeaderBounds = composeTestRule.onNodeWithTag("column_header_image")
            .assertIsDisplayed()
            .assertTextEquals("Image")
            .fetchSemanticsNode().boundsInRoot
        val productHeaderBounds = composeTestRule.onNodeWithTag("column_header_product")
            .assertIsDisplayed()
            .assertTextEquals("Items")
            .fetchSemanticsNode().boundsInRoot
        val quantityHeaderBounds = composeTestRule.onNodeWithTag("column_header_quantity")
            .assertIsDisplayed()
            .assertTextEquals("Quantity")
            .fetchSemanticsNode().boundsInRoot
        val amountHeaderBounds = composeTestRule.onNodeWithTag("column_header_amount")
            .assertIsDisplayed()
            .assertTextEquals("Amount")
            .fetchSemanticsNode().boundsInRoot
        val actionHeaderBounds = composeTestRule.onNodeWithTag("column_header_action")
            .assertIsDisplayed()
            .assertTextEquals("Action")
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Image column must be before Items column", imageHeaderBounds.left < productHeaderBounds.left)
        assertTrue("Items column must be before Quantity column", productHeaderBounds.left < quantityHeaderBounds.left)
        assertTrue("Quantity column must be before Amount column", quantityHeaderBounds.left < amountHeaderBounds.left)
        assertTrue("Amount column must be before Action column", amountHeaderBounds.left < actionHeaderBounds.left)

        composeTestRule.onNodeWithText("No calibration entries recorded yet.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grand Total").assertIsDisplayed()
        composeTestRule.onNodeWithTag("grand_total_value").assertTextEquals("₹0.00")
        composeTestRule.onNodeWithTag("finalize_bill_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_nav_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("voice_recognition_card").assertIsDisplayed()
    }

    @Test
    fun `verify Phase 9 filename keyword extraction, exact matching, similar filename ambiguity, and unclear IMG fallback`() {
        assertEquals("piston", ImageFilenameMatcher.extractPrimaryKeyword("Piston_272f9602.jpg"))
        assertEquals("666 paste", ImageFilenameMatcher.extractPrimaryKeyword("666_paste_3935fc0a.jpg"))
        assertEquals("bearing", ImageFilenameMatcher.extractPrimaryKeyword("Bearing__c92d9f7b.jpg"))
        assertEquals("head", ImageFilenameMatcher.extractPrimaryKeyword("Head .jpg"))
        assertEquals("rings", ImageFilenameMatcher.extractPrimaryKeyword("Rings.png"))
        assertEquals(
            "motor clutch centrifugal plates",
            ImageFilenameMatcher.extractPrimaryKeyword("motor-clutch-centrifugal-plates.jpg")
        )
        assertEquals("steel plate", ImageFilenameMatcher.extractPrimaryKeyword("steel plate.jpg"))
        assertTrue(ImageFilenameMatcher.isUnclearFilename("IMG_a80d976b-8427-433c-83b0-8a08e5af66fc.jpg"))
        assertEquals("", ImageFilenameMatcher.extractPrimaryKeyword("IMG_a80d976b-8427-433c-83b0-8a08e5af66fc.jpg"))

        val matcher = MechanicalPartImageMatcher()
        assertEquals(24, matcher.imageCollectionManager.getAllImages().size)

        val headMatch = matcher.identifyImageForPart("Head")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, headMatch.status)
        assertEquals("Head .jpg", headMatch.matchedFilename)
        assertFalse(headMatch.requiresUserConfirmation)

        val pasteMatch = matcher.identifyImageForPart("Paste")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, pasteMatch.status)
        assertEquals("666 paste.jpg", pasteMatch.matchedFilename)

        val bearingMatch = matcher.identifyImageForPart("Bearing")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, bearingMatch.status)
        assertEquals("Bearing .jpg", bearingMatch.matchedFilename)

        val pistonMatch = matcher.identifyImageForPart("Piston")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, pistonMatch.status)
        assertEquals("Piston.jpg", pistonMatch.matchedFilename)

        val connectingRodMatch = matcher.identifyImageForPart("Connecting rod")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, connectingRodMatch.status)
        assertEquals("Connectingrod.jpg", connectingRodMatch.matchedFilename)

        // Similar filenames ("bush" vs "steel bush", and ambiguous "steel" query)
        val bushMatch = matcher.identifyImageForPart("bush")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, bushMatch.status)
        assertEquals("bush.jpg", bushMatch.matchedFilename)

        val steelBushMatch = matcher.identifyImageForPart("steel bush")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, steelBushMatch.status)
        assertEquals("steelbush.png", steelBushMatch.matchedFilename)

        val ambiguousSteel = matcher.identifyImageForPart("steel")
        assertEquals(ImageMatchStatus.AMBIGUOUS_SIMILAR_FILENAMES, ambiguousSteel.status)
        assertTrue(ambiguousSteel.requiresUserConfirmation)
        assertTrue(ambiguousSteel.candidateImages.size >= 2)

        // Tamil spoken mechanical part name matching (preserving Tamil part name)
        val tamilHeadMatch = matcher.identifyImageForPart("ஹெட்")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, tamilHeadMatch.status)
        assertEquals("Head .jpg", tamilHeadMatch.matchedFilename)

        val tamilBearingMatch = matcher.identifyImageForPart("பேரிங்")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, tamilBearingMatch.status)
        assertEquals("Bearing .jpg", tamilBearingMatch.matchedFilename)

        val tamilSheetMatch = matcher.identifyImageForPart("தகுது")
        assertEquals(ImageMatchStatus.EXACT_FILENAME_MATCH, tamilSheetMatch.status)
        assertEquals("sheet.jpg", tamilSheetMatch.matchedFilename)

        // Unclear filename (IMG_...) visual fallback requires user confirmation
        val boltFallback = matcher.identifyImageForPart("Hex Bolt")
        assertEquals(ImageMatchStatus.CONTENT_FALLBACK_UNCERTAIN, boltFallback.status)
        assertTrue(boltFallback.requiresUserConfirmation)
        assertTrue(boltFallback.matchedFilename.startsWith("IMG_"))

        // Unknown mechanical part never randomly assigns an image
        val unknownPart = matcher.identifyImageForPart("Hydraulic Servo Valve X900")
        assertEquals(ImageMatchStatus.NO_MATCH, unknownPart.status)
        assertNull(unknownPart.imageUri)
    }

    @Test
    fun `verify English, Tamil, and mixed mechanical transcript processing and number normalization`() {
        val englishInput = "Head two two hundred Paste one hundred"
        val englishProcessed = TextNormalizer.processTranscript(englishInput)
        assertEquals(englishInput, englishProcessed.originalTranscript)
        assertEquals(DetectedLanguage.ENGLISH, englishProcessed.detectedLanguage)
        assertEquals(listOf("Head", "Paste"), englishProcessed.preservedProductWords)
        assertEquals(
            listOf("2", "200", "1", "100"),
            englishProcessed.normalizedNumbers.map { it.normalizedValue }
        )
        assertEquals("Head 2 200 Paste 1 100", englishProcessed.normalizedTranscript)

        val mixedInput = "Head இரண்டு இருநூறு Paste ஒன்று நூறு"
        val mixedProcessed = TextNormalizer.processTranscript(mixedInput)
        assertEquals(mixedInput, mixedProcessed.originalTranscript)
        assertEquals(DetectedLanguage.MIXED, mixedProcessed.detectedLanguage)
        assertEquals(listOf("Head", "Paste"), mixedProcessed.preservedProductWords)
        assertEquals(
            listOf("2", "200", "1", "100"),
            mixedProcessed.normalizedNumbers.map { it.normalizedValue }
        )
        assertEquals("Head 2 200 Paste 1 100", mixedProcessed.normalizedTranscript)
    }

    @Test
    fun `verify mechanical part extraction with optional quantity, independent amount, and automatic image matching`() {
        val parser = ProductParser()

        val ex1 = parser.parse("Head two two hundred, Paste one one hundred")
        assertEquals(2, ex1.confidentProducts.size)
        assertEquals("Head", ex1.confidentProducts[0].productName)
        assertEquals(2.0, ex1.confidentProducts[0].extractedQuantity.value ?: 0.0, 0.001)
        assertEquals(BigDecimal("200.00"), ex1.confidentProducts[0].extractedAmount.value)
        assertEquals("Head .jpg", ex1.confidentProducts[0].imageIdentification.matchedFilename)
        assertEquals("Paste", ex1.confidentProducts[1].productName)
        assertEquals(1.0, ex1.confidentProducts[1].extractedQuantity.value ?: 0.0, 0.001)
        assertEquals(BigDecimal("100.00"), ex1.confidentProducts[1].extractedAmount.value)
        assertEquals("666 paste.jpg", ex1.confidentProducts[1].imageIdentification.matchedFilename)

        val noQtySingle = parser.parse("Spindle Bearing ₹450.50")
        assertEquals(1, noQtySingle.confidentProducts.size)
        assertEquals("Spindle Bearing", noQtySingle.confidentProducts[0].productName)
        assertNull(noQtySingle.confidentProducts[0].extractedQuantity.value)
        assertEquals(BigDecimal("450.50"), noQtySingle.confidentProducts[0].extractedAmount.value)
        assertEquals("Bearing .jpg", noQtySingle.confidentProducts[0].imageIdentification.matchedFilename)
        val convertedProduct = noQtySingle.confidentProducts[0].toProductOrNull()
        assertNull(convertedProduct?.quantity)
        assertEquals("-", convertedProduct?.formattedQuantity)
        assertEquals("₹450.50", convertedProduct?.formattedAmount)
        assertNotNull(convertedProduct?.imageUri)
    }

    @Test
    fun `verify manual editing of item names, optional quantities, independent amounts, and associated images`() {
        val viewModel = BillingViewModel()

        // 1. Add entry WITHOUT Quantity ("Piston", Amount = 350) -> auto-matches Piston.jpg
        viewModel.openAddProductDialog()
        viewModel.onProductNameChanged("Piston")
        viewModel.onQuantityChanged("") // Quantity intentionally empty
        viewModel.onAmountChanged("350")
        assertTrue(viewModel.saveProduct())

        val initialProduct = viewModel.uiState.value.products.single()
        assertEquals("Piston", initialProduct.productName)
        assertNull(initialProduct.quantity)
        assertEquals("-", initialProduct.formattedQuantity)
        assertEquals(350.0, initialProduct.amount, 0.001)
        assertEquals("Piston.jpg", initialProduct.imageSourceName)
        assertEquals("₹350.00", viewModel.uiState.value.grandTotalDisplay)

        // 2. Edit item name ("Piston" -> "Gasket"), add Quantity = 4, edit Amount = 420 -> auto-updates image to Gasket.jpg
        viewModel.openEditProductDialog(initialProduct)
        viewModel.onProductNameChanged("Gasket")
        viewModel.onQuantityChanged("4")
        viewModel.onAmountChanged("420")
        assertTrue(viewModel.saveProduct())

        val editedProduct = viewModel.uiState.value.products.single()
        assertEquals("Gasket", editedProduct.productName)
        assertEquals(4.0, editedProduct.quantity ?: 0.0, 0.001)
        assertEquals(420.0, editedProduct.amount, 0.001)
        assertEquals("Gasket.jpg", editedProduct.imageSourceName)
        assertEquals("₹420.00", viewModel.uiState.value.grandTotalDisplay)

        // 3. Edit entry to clear Quantity back to empty and remove associated image inside dialog
        viewModel.openEditProductDialog(editedProduct)
        viewModel.onQuantityChanged("")
        viewModel.onAmountChanged("500")
        viewModel.onRemoveFormImage()
        assertTrue(viewModel.saveProduct())

        val afterImageRemoved = viewModel.uiState.value.products.single()
        assertNull(afterImageRemoved.quantity)
        assertEquals("-", afterImageRemoved.formattedQuantity)
        assertEquals(500.0, afterImageRemoved.amount, 0.001)
        assertNull(afterImageRemoved.imageUri)
        assertEquals("₹500.00", viewModel.uiState.value.grandTotalDisplay)

        // 4. Manually replace image with "Filter.jpg"
        val filterAssoc = viewModel.uiState.value.availableImages.first { it.originalFilename == "Filter.jpg" }
        viewModel.selectImageForProduct(afterImageRemoved.id, filterAssoc)
        assertEquals("Filter.jpg", viewModel.uiState.value.products.single().imageSourceName)
        assertNotNull(viewModel.uiState.value.products.single().imageUri)
    }

    @Test
    fun `verify Phase 10 Room database persistence across restarts, duplicate prevention, search, date filter, and deletion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Open persistent Room database (Session 1 before app restart)
        val db1 = BillingDatabase.openPersistentInstance(context)
        val repo1 = BillRepository(db1)

        // Attempt to finalize an empty bill -> rejected with InvalidBill
        val emptySession = BillingSession(sessionId = "CAL-0001", products = emptyList())
        val emptyResult = repo1.finalizeAndSaveBill(emptySession, "")
        assertTrue(emptyResult is FinalizeBillResult.InvalidBill)

        // Create Bill 1 with English and Tamil mechanical parts, including omitted quantity and matched images
        val parser = ProductParser()
        val parsedBill1 = parser.parse("Head 200, Paste 1 100, தகுது 10 100")
        val bill1Products = parsedBill1.confidentProducts.mapNotNull { it.toProductOrNull() }
        val session1 = BillingSession(
            sessionId = "CAL-0001",
            products = bill1Products,
            grandTotal = BillingCalculator.calculateGrandTotal(bill1Products),
            grandTotalDisplay = BillingCalculator.calculateFormattedGrandTotal(bill1Products)
        )

        // Timestamp for 2026-11-01 10:15 UTC
        val timestampDay1 = 1793528100000L
        val saveResult1 = repo1.finalizeAndSaveBill(
            session = session1,
            originalTranscript = "Head 200, Paste 1 100, தகுது 10 100",
            timestampMillis = timestampDay1
        )
        assertTrue(saveResult1 is FinalizeBillResult.Saved)
        val savedBill1 = (saveResult1 as FinalizeBillResult.Saved).billWithItems
        assertEquals("BILL-0001", savedBill1.bill.billId)
        assertEquals("₹400.00", savedBill1.bill.grandTotalFormatted)
        assertEquals(3, savedBill1.items.size)
        assertNull(savedBill1.sortedItems[0].quantity) // "Head 200" has omitted quantity preserved as null
        assertNotNull(savedBill1.sortedItems[0].imageUri) // Head .jpg image reference preserved

        // 2. Duplicate prevention: tapping Finalize again on the same bill is blocked from creating a duplicate
        val duplicateAttempt = repo1.finalizeAndSaveBill(
            session = session1,
            originalTranscript = "Head 200, Paste 1 100, தகுது 10 100",
            timestampMillis = timestampDay1 + 5000L
        )
        assertTrue(duplicateAttempt is FinalizeBillResult.DuplicatePrevented)
        assertEquals(1, repo1.getFinalizedBillsSnapshot().size)

        // 3. Create Bill 2 on a newer date (most recent first)
        val parsedBill2 = parser.parse("Piston 550, Bearing 2 350")
        val bill2Products = parsedBill2.confidentProducts.mapNotNull { it.toProductOrNull() }
        val session2 = BillingSession(
            sessionId = "CAL-0002",
            products = bill2Products,
            grandTotal = BillingCalculator.calculateGrandTotal(bill2Products),
            grandTotalDisplay = BillingCalculator.calculateFormattedGrandTotal(bill2Products)
        )
        val timestampDay2 = timestampDay1 + (2 * 24 * 60 * 60 * 1000L) // 2 days later
        val saveResult2 = repo1.finalizeAndSaveBill(
            session = session2,
            originalTranscript = "Piston 550, Bearing 2 350",
            timestampMillis = timestampDay2
        )
        assertTrue(saveResult2 is FinalizeBillResult.Saved)
        val savedBill2 = (saveResult2 as FinalizeBillResult.Saved).billWithItems
        assertEquals("BILL-0002", savedBill2.bill.billId)
        assertEquals("₹900.00", savedBill2.bill.grandTotalFormatted)

        // Close db1 to simulate application closing
        db1.close()

        // 4. Open a brand-new persistent Room database instance to simulate application restart
        val dbAfterRestart = BillingDatabase.openPersistentInstance(context)
        val repoAfterRestart = BillRepository(dbAfterRestart)
        val persistedBills = repoAfterRestart.getFinalizedBillsSnapshot()

        // Verify both bills survived restart and are ordered most recent first (BILL-0002 then BILL-0001)
        assertEquals(2, persistedBills.size)
        assertEquals("BILL-0002", persistedBills[0].bill.billId)
        assertEquals("BILL-0001", persistedBills[1].bill.billId)
        assertEquals("Head 200, Paste 1 100, தகுது 10 100", persistedBills[1].bill.originalTranscript)
        assertTrue(persistedBills[0].sortedItems[0].imageUri?.endsWith("Piston_272f9602.jpg") == true)
        assertNull(persistedBills[0].sortedItems[0].quantity)

        // 5. Verify Search by Bill ID and by Item Name (English & Tamil) and Filter by Date
        val searchByBillId = BillRepository.filterBills(persistedBills, searchQuery = "BILL-0001", dateFilter = "")
        assertEquals(1, searchByBillId.size)
        assertEquals("BILL-0001", searchByBillId.single().bill.billId)

        val searchByTamilItem = BillRepository.filterBills(persistedBills, searchQuery = "தகுது", dateFilter = "")
        assertEquals(1, searchByTamilItem.size)
        assertEquals("BILL-0001", searchByTamilItem.single().bill.billId)

        val searchByPiston = BillRepository.filterBills(persistedBills, searchQuery = "Piston", dateFilter = "")
        assertEquals(1, searchByPiston.size)
        assertEquals("BILL-0002", searchByPiston.single().bill.billId)

        val day2DateKey = persistedBills[0].bill.dateKey
        val filterByDate = BillRepository.filterBills(persistedBills, searchQuery = "", dateFilter = day2DateKey)
        assertEquals(1, filterByDate.size)
        assertEquals("BILL-0002", filterByDate.single().bill.billId)

        // 6. Delete BILL-0001 and verify it is removed while BILL-0002 remains
        repoAfterRestart.deleteBill("BILL-0001")
        val remainingBills = repoAfterRestart.getFinalizedBillsSnapshot()
        assertEquals(1, remainingBills.size)
        assertEquals("BILL-0002", remainingBills.single().bill.billId)

        dbAfterRestart.close()
    }

    @Test
    fun `verify UI Phase 10 finalize bill, duplicate save prevention, history navigation, detail view with images, and confirmed deletion`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

        composeTestRule.waitForIdle()

        // 1. Tapping Finalize Bill on an empty bill shows validation error and does not save
        composeTestRule.onNodeWithTag("finalize_bill_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("finalize_status_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finalize_status_text")
            .assertTextContains("Cannot finalize an empty bill", substring = true)

        // 2. Enter voice/text transcript with omitted quantity and explicit quantities
        composeTestRule.onNodeWithTag("voice_transcript_input")
            .performTextInput("Head 200, Paste 1 100, தகுது 10 100")
        composeTestRule.onNodeWithTag("reprocess_transcript_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("grand_total_value").assertTextEquals("₹400.00")

        // Verify Image thumbnail for "Head" is in Column 1 (left of "Head" text)
        val headImageBounds = composeTestRule.onNode(
            hasContentDescription("Mechanical part image for Head")
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val headNameBounds = composeTestRule.onNodeWithText("Head")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(headImageBounds.left < headNameBounds.left)

        // 3. Finalize the bill -> immediately opens BillReceiptPreviewDialog & saves BILL-0001 to Room History
        composeTestRule.onNodeWithTag("finalize_bill_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("bill_receipt_preview_dialog")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("bill_receipt_preview_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_invoice_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_status_text")
            .assertTextContains("BILL-0001 finalized and saved to History", substring = true)
        composeTestRule.onNodeWithTag("receipt_item_name_0").assertTextEquals("Head")
        composeTestRule.onNodeWithTag("receipt_item_qty_0").assertTextEquals("-")
        composeTestRule.onNodeWithTag("receipt_item_amount_0").assertTextEquals("₹200.00")
        composeTestRule.onNodeWithTag("receipt_item_name_2").assertTextEquals("தகுது")
        composeTestRule.onNodeWithTag("receipt_item_qty_2").assertTextEquals("10")
        composeTestRule.onNodeWithTag("receipt_item_amount_2").assertTextEquals("₹100.00")
        composeTestRule.onNodeWithTag("receipt_preview_grand_total").assertTextEquals("₹400.00")

        // Verify Print button works inside Receipt Preview
        composeTestRule.onNodeWithTag("receipt_print_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("receipt_action_status_text")
            .assertTextContains("BILL-0001", substring = true)

        // Close receipt preview dialog
        composeTestRule.onNodeWithTag("close_receipt_preview_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("finalize_status_text")
            .assertTextContains("BILL-0001 finalized and saved to History", substring = true)

        // Tapping Finalize Bill a second time prevents duplicate save and re-opens the receipt preview
        composeTestRule.onNodeWithTag("finalize_bill_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bill_receipt_preview_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("receipt_status_text")
            .assertTextContains("already finalized and saved in History", substring = true)
        composeTestRule.onNodeWithTag("close_receipt_preview_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("finalize_status_text")
            .assertTextContains("already finalized and saved in History", substring = true)

        // 4. Open Bill History screen
        composeTestRule.onNodeWithTag("history_nav_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("history_bill_id_BILL-0001")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("bill_history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_bill_id_BILL-0001").assertTextEquals("BILL-0001")
        composeTestRule.onNodeWithTag("history_bill_total_BILL-0001").assertTextEquals("₹400.00")

        // 5. Verify UI search input in Bill History (search by Tamil item "தகுது")
        composeTestRule.onNodeWithTag("history_search_input").performTextInput("தகுது")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_bill_id_BILL-0001").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clear_history_search_button").performClick()
        composeTestRule.waitForIdle()

        // 6. Open BILL-0001 detail dialog and verify items, optional quantity "-", amounts, image, and transcript
        composeTestRule.onNodeWithTag("open_history_bill_BILL-0001").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("bill_history_detail_dialog")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("bill_history_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("detail_bill_grand_total").assertTextEquals("₹400.00")
        composeTestRule.onNodeWithTag("history_detail_transcript")
            .assertTextEquals("Head 200, Paste 1 100, தகுது 10 100")
        composeTestRule.onNodeWithTag("history_detail_item_name_0").assertTextEquals("Head")
        composeTestRule.onNodeWithTag("history_detail_item_qty_0").assertTextEquals("-")
        composeTestRule.onNodeWithTag("history_detail_item_amount_0").assertTextEquals("₹200.00")

        // 7. Reopen BILL-0001 in editor and verify it returns to Billing screen with items intact
        composeTestRule.onNodeWithTag("reopen_bill_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("finalize_status_text")
            .assertTextContains("Reopened Bill BILL-0001", substring = true)
        composeTestRule.onNodeWithTag("grand_total_value").assertTextEquals("₹400.00")

        // 8. Navigate back to Bill History and delete BILL-0001 with confirmation dialog
        composeTestRule.onNodeWithTag("history_nav_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("delete_history_bill_button_BILL-0001")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("delete_history_bill_button_BILL-0001").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_delete_bill_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_delete_bill_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("No finalized bills saved yet.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("No finalized bills saved yet.").assertIsDisplayed()
    }

    @Test
    fun `verify Phase 11 complete voice-to-billing workflow, bill reopening, and image association persistence across restarts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "calculators_phase11_integration_${System.nanoTime()}.db"
        val prefsName = "calculators_phase11_prefs_${System.nanoTime()}"

        val dbBeforeRestart = BillingDatabase.openPersistentInstance(context, dbName)
        val repoBeforeRestart = BillRepository(dbBeforeRestart)

        val viewModel = BillingViewModel(billRepository = repoBeforeRestart)
        viewModel.bindImageStorage(context, prefsName)

        // 1. Voice-to-Billing workflow: simulate spoken result with Tamil & English parts, optional & explicit quantities
        viewModel.onFinalSpeechResult("Piston 500, Bearing 2 300, வாஷர் 10 50")

        val activeProducts = viewModel.uiState.value.billingSession.products
        assertEquals(3, activeProducts.size)

        // Piston: quantity omitted (null -> "-"), amount 500, matched to Piston.jpg
        assertEquals("Piston", activeProducts[0].productName)
        assertNull(activeProducts[0].quantity)
        assertEquals("-", activeProducts[0].formattedQuantity)
        assertEquals(500.0, activeProducts[0].amount, 0.001)
        assertEquals("Piston.jpg", activeProducts[0].imageSourceName)

        // Bearing: quantity 2, amount 300, matched to Bearing .jpg
        assertEquals("Bearing", activeProducts[1].productName)
        assertEquals(2.0, activeProducts[1].quantity!!, 0.001)
        assertEquals(300.0, activeProducts[1].amount, 0.001)
        assertEquals("Bearing .jpg", activeProducts[1].imageSourceName)

        // வாஷர்: preserved Tamil script, quantity 10, amount 50
        assertEquals("வாஷர்", activeProducts[2].productName)
        assertEquals(10.0, activeProducts[2].quantity!!, 0.001)
        assertEquals(50.0, activeProducts[2].amount, 0.001)
        assertTrue(!activeProducts[2].imageUri.isNullOrBlank())

        // Grand Total = 500 + 300 + 50 = ₹850.00
        assertEquals("₹850.00", viewModel.uiState.value.grandTotalDisplay)

        // 2. Save bill directly via repository and test reopening in ViewModel
        val saveResult = repoBeforeRestart.finalizeAndSaveBill(
            session = viewModel.uiState.value.billingSession,
            originalTranscript = viewModel.uiState.value.speechRecognitionState.originalTranscript,
            timestampMillis = 1_731_200_000_000L
        )
        assertTrue(saveResult is FinalizeBillResult.Saved)
        val savedBillWithItems = (saveResult as FinalizeBillResult.Saved).billWithItems
        assertEquals("BILL-0001", savedBillWithItems.bill.billId)

        // Start a blank session, then reopen BILL-0001
        viewModel.startNewBillSession()
        assertTrue(viewModel.uiState.value.billingSession.products.isEmpty())
        assertEquals("₹0.00", viewModel.uiState.value.grandTotalDisplay)

        viewModel.reopenSavedBill(savedBillWithItems)
        assertEquals(3, viewModel.uiState.value.billingSession.products.size)
        assertEquals("₹850.00", viewModel.uiState.value.grandTotalDisplay)
        assertEquals("Piston 500, Bearing 2 300, வாஷர் 10 50", viewModel.uiState.value.speechRecognitionState.originalTranscript)
        assertTrue(viewModel.uiState.value.isCurrentBillFinalized)

        // Tapping finalizeBill() on an unedited reopened bill prevents duplicate creation
        viewModel.finalizeBill()
        assertTrue(
            viewModel.uiState.value.finalizeStatusMessage?.contains("already finalized and saved in History") == true
        )

        // 3. Customize image associations and add a device image before simulated restart
        val reopenedFirstId = viewModel.uiState.value.billingSession.products[0].id
        val gasketImage = viewModel.uiState.value.availableImages.first { it.originalFilename == "Gasket.jpg" }
        viewModel.selectImageForProduct(reopenedFirstId, gasketImage)
        viewModel.addDeviceImageToCollection(
            imageUri = "content://device/storage/custom_calibrator.jpg",
            originalFilename = "custom_calibrator.jpg",
            assignToProductId = null
        )

        dbBeforeRestart.close()

        // 4. Simulate application close and reopen with fresh instances bound to the same disk files
        val managerAfterRestart = ImageCollectionManager()
        managerAfterRestart.bindPersistentStorage(context, prefsName)
        val matcherAfterRestart = MechanicalPartImageMatcher(managerAfterRestart)

        // Verify custom assignment ("Piston" -> Gasket.jpg) and device image ("custom_calibrator.jpg") survived restart
        val pistonMatchAfterRestart = matcherAfterRestart.identifyImageForPart("Piston")
        assertEquals(ImageMatchStatus.USER_ASSOCIATION_MATCH, pistonMatchAfterRestart.status)
        assertEquals("Gasket.jpg", pistonMatchAfterRestart.matchedFilename)

        val customDeviceMatchAfterRestart = matcherAfterRestart.identifyImageForPart("custom calibrator")
        assertTrue(customDeviceMatchAfterRestart.hasImage)
        assertEquals("custom_calibrator.jpg", customDeviceMatchAfterRestart.matchedFilename)
    }

    @Test
    fun `verify SpeechRecognitionManager English and Tamil recognition, session guard, error mapping, and cleanup`() {
        var currentListener: RecognitionListener? = null
        var lastStartedIntent: Intent? = null
        var isDestroyed = false
        var isCanceled = false
        var available = true

        val fakeGateway = object : SpeechRecognizerGateway {
            override fun isRecognitionAvailable(): Boolean = available
            override fun setRecognitionListener(listener: RecognitionListener) {
                currentListener = listener
            }
            override fun startListening(recognizerIntent: Intent) {
                lastStartedIntent = recognizerIntent
            }
            override fun stopListening() {}
            override fun cancel() {
                isCanceled = true
            }
            override fun destroy() {
                isDestroyed = true
            }
        }

        val observedStatuses = mutableListOf<VoiceListeningStatus>()
        var observedTranscript = ""
        var observedError: SpeechErrorType? = null

        val manager = SpeechRecognitionManager(
            gateway = fakeGateway,
            onStatusChanged = { observedStatuses.add(it) },
            onPartialTranscript = {},
            onFinalTranscript = { observedTranscript = it },
            onError = { observedError = it }
        )

        assertTrue(manager.startListening(RecognitionLanguage.ENGLISH_IN))
        assertEquals(VoiceListeningStatus.LISTENING, observedStatuses.last())
        assertEquals("en-IN", lastStartedIntent?.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertFalse(manager.startListening(RecognitionLanguage.ENGLISH_IN))

        currentListener?.onEndOfSpeech()
        assertEquals(VoiceListeningStatus.PROCESSING, observedStatuses.last())

        val englishBundle = Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("Head two hundred")
            )
        }
        currentListener?.onResults(englishBundle)
        assertEquals("Head two hundred", observedTranscript)

        assertEquals(
            SpeechErrorType.PERMISSION_DENIED,
            SpeechRecognitionListener.mapSpeechRecognizerError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        )
        manager.release()
        assertTrue(isCanceled)
        assertTrue(isDestroyed)
    }

    @Test
    fun `verify BillingCalculator BigDecimal precision with null and non-null quantities`() {
        val entries = listOf(
            Product(id = "1", productName = "Part A", quantity = null, amount = 0.10),
            Product(id = "2", productName = "Part B", quantity = null, amount = 0.20),
            Product(id = "3", productName = "Part C", quantity = 2.0, amount = 1250.20)
        )
        assertEquals(BigDecimal("1250.50"), BillingCalculator.calculateGrandTotal(entries))
        assertEquals("₹1,250.50", BillingCalculator.calculateFormattedGrandTotal(entries))
    }
}
