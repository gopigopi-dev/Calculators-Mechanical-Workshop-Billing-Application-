package com.example.calculators.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.calculators.data.BillWithItems
import com.example.calculators.theme.JetBrainsMonoFontFamily
import java.io.File
import java.io.FileOutputStream

private val InvoiceOuterBorderColor = Color(0xFF1E293B)
private val InvoiceGridLineColor = Color(0xFF334155)
private val InvoiceHeaderBgColor = Color(0xFFE2E8F0)
private val InvoiceTotalRowBgColor = Color(0xFFF1F5F9)
private val InvoicePaperColor = Color(0xFFFFFFFF)

/**
 * Generates and displays a formatted, receipt-style mechanical workshop invoice preview
 * immediately when the user clicks Finalize Bill.
 *
 * Follows the exact bordered table layout:
 * `Image | Product | Quantity | Amount`
 * with solid column/row separators, optional Quantity rendered as "-", right-aligned Rupee amounts,
 * a bold Grand Total row inside the receipt grid, and working Print and Share actions.
 */
@Composable
fun BillReceiptPreviewDialog(
    billWithItems: BillWithItems,
    statusMessage: String?,
    actionFeedbackMessage: String?,
    onPrintResult: (String) -> Unit,
    onShareResult: (String) -> Unit,
    onStartNewBill: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bill = billWithItems.bill

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .testTag("bill_receipt_preview_dialog"),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Finalized Confirmation Strip
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipt_status_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusMessage ?: "Bill ${bill.billId} finalized and saved to History.",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_status_text")
                        )
                    }
                }

                // Scrollable Paper Invoice / Receipt Sheet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MechanicalWorkshopReceiptSheet(
                        billWithItems = billWithItems
                    )
                }

                // Print / Share Action Feedback Banner
                if (!actionFeedbackMessage.isNullOrBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_action_feedback_banner")
                    ) {
                        Text(
                            text = actionFeedbackMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("receipt_action_status_text")
                        )
                    }
                }

                // Working Print & Share Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val msg = ReceiptExportHelper.printBillReceipt(context, billWithItems)
                            onPrintResult(msg)
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(0.5f)
                            .minimumInteractiveComponentSize()
                            .testTag("receipt_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.button_print_bill))
                    }

                    OutlinedButton(
                        onClick = {
                            val msg = ReceiptExportHelper.shareBillReceipt(context, billWithItems)
                            onShareResult(msg)
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(0.5f)
                            .minimumInteractiveComponentSize()
                            .testTag("receipt_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.button_share_bill))
                    }
                }

                // Bottom Navigation Actions: New Bill + Close Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onStartNewBill,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("receipt_new_bill_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.button_new_bill))
                    }

                    Button(
                        onClick = onDismiss,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_receipt_preview_button")
                    ) {
                        Text(text = stringResource(id = R.string.button_close_receipt))
                    }
                }
            }
        }
    }
}

/**
 * Reusable paper-style mechanical workshop invoice sheet with strict grid borders:
 * `Image | Product | Quantity | Amount`
 * and an integrated `Grand Total` row matching the user's sample bill design.
 */
@Composable
fun MechanicalWorkshopReceiptSheet(
    billWithItems: BillWithItems,
    modifier: Modifier = Modifier
) {
    val bill = billWithItems.bill
    val items = billWithItems.sortedItems

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(InvoicePaperColor)
            .border(BorderStroke(1.5.dp, InvoiceOuterBorderColor))
            .testTag("receipt_invoice_sheet")
    ) {
        // 1. Workshop Receipt Title Banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(InvoiceHeaderBgColor.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.receipt_workshop_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = InvoiceOuterBorderColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(id = R.string.receipt_workshop_subtitle),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = InvoiceGridLineColor,
                textAlign = TextAlign.Center
            )
        }

        HorizontalDivider(thickness = 1.5.dp, color = InvoiceOuterBorderColor)

        // 2. Invoice Metadata Strip (Bill No, Session ID, Date & Time)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Bill No: ${bill.billId}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = InvoiceOuterBorderColor,
                    modifier = Modifier.testTag("receipt_bill_id")
                )
                Text(
                    text = "Session: ${bill.sessionId}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = InvoiceGridLineColor,
                    modifier = Modifier.testTag("receipt_session_id")
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Date: ${bill.formattedDateTime}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = InvoiceOuterBorderColor,
                    modifier = Modifier.testTag("receipt_date_time")
                )
                Text(
                    text = "Entries: ${items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InvoiceGridLineColor
                )
            }
        }

        HorizontalDivider(thickness = 1.5.dp, color = InvoiceOuterBorderColor)

        // 3. Receipt Table Header Row: Image | Product | Quantity | Amount
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(InvoiceHeaderBgColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.column_image),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = InvoiceOuterBorderColor,
                    textAlign = TextAlign.Center
                )
            }

            ReceiptVerticalSeparator()

            Box(
                modifier = Modifier
                    .weight(0.42f)
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(id = R.string.receipt_column_product),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = InvoiceOuterBorderColor
                )
            }

            ReceiptVerticalSeparator()

            Box(
                modifier = Modifier
                    .weight(0.22f)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.column_quantity),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = InvoiceOuterBorderColor,
                    textAlign = TextAlign.Center
                )
            }

            ReceiptVerticalSeparator()

            Box(
                modifier = Modifier
                    .weight(0.36f)
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = stringResource(id = R.string.column_amount),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = InvoiceOuterBorderColor,
                    textAlign = TextAlign.End
                )
            }
        }

        HorizontalDivider(thickness = 1.5.dp, color = InvoiceOuterBorderColor)

        // 4. Receipt Line Item Rows with continuous vertical and horizontal cell borders
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .testTag("receipt_item_row_$index"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: Mechanical Part Image
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MechanicalPartThumbnail(
                        imageUri = item.imageUri,
                        partName = item.productName,
                        size = 40.dp,
                        modifier = Modifier.testTag("receipt_item_image_$index")
                    )
                }

                ReceiptVerticalSeparator()

                // Column 2: Product / Mechanical Part Name (Tamil & English preserved)
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = InvoiceOuterBorderColor,
                        modifier = Modifier.testTag("receipt_item_name_$index")
                    )
                    if (item.calibrationNotes.isNotBlank()) {
                        Text(
                            text = item.calibrationNotes,
                            style = MaterialTheme.typography.labelSmall,
                            color = InvoiceGridLineColor
                        )
                    }
                }

                ReceiptVerticalSeparator()

                // Column 3: Optional Quantity ("-" when omitted)
                Box(
                    modifier = Modifier
                        .weight(0.22f)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.formattedQuantity,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = InvoiceOuterBorderColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("receipt_item_qty_$index")
                    )
                }

                ReceiptVerticalSeparator()

                // Column 4: Line Amount (₹)
                Box(
                    modifier = Modifier
                        .weight(0.36f)
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = item.lineAmountFormatted,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = InvoiceOuterBorderColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.testTag("receipt_item_amount_$index")
                    )
                }
            }

            HorizontalDivider(
                thickness = if (index == items.lastIndex) 1.5.dp else 1.dp,
                color = if (index == items.lastIndex) InvoiceOuterBorderColor else InvoiceGridLineColor
            )
        }

        // 5. Grand Total Row (Integrated into the Receipt Grid: Grand Total | | ₹Total)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(InvoiceTotalRowBgColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spans Image + Product columns
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                // Keep aligned with Image column
            }

            Box(
                modifier = Modifier
                    .weight(0.42f)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(id = R.string.grand_total_label),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = InvoiceOuterBorderColor
                )
            }

            ReceiptVerticalSeparator()

            // Empty Quantity cell in Grand Total row (matching sample: Grand Total | | ₹400)
            Box(
                modifier = Modifier
                    .weight(0.22f)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ReceiptVerticalSeparator()

            // Final Grand Total Amount Cell
            Box(
                modifier = Modifier
                    .weight(0.36f)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = bill.grandTotalFormatted,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("receipt_preview_grand_total")
                )
            }
        }

        // 6. Optional Original Transcript & Authorized Signature Footer
        if (bill.originalTranscript.isNotBlank()) {
            HorizontalDivider(thickness = 1.dp, color = InvoiceGridLineColor)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.voice_transcript_label),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = InvoiceGridLineColor
                )
                Text(
                    text = bill.originalTranscript,
                    style = MaterialTheme.typography.bodySmall,
                    color = InvoiceOuterBorderColor,
                    modifier = Modifier.testTag("receipt_original_transcript")
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = InvoiceGridLineColor)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = stringResource(id = R.string.receipt_customer_signature),
                style = MaterialTheme.typography.labelSmall,
                color = InvoiceGridLineColor
            )
            Text(
                text = stringResource(id = R.string.receipt_authorized_signatory),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = InvoiceOuterBorderColor
            )
        }
    }
}

@Composable
private fun ReceiptVerticalSeparator() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(InvoiceGridLineColor)
    )
}

/**
 * Provides real PDF invoice generation, Android PrintManager integration, and system Share Intent
 * support for finalized mechanical workshop receipts.
 */
object ReceiptExportHelper {

    fun formatPlainTextReceipt(billWithItems: BillWithItems): String {
        val bill = billWithItems.bill
        val items = billWithItems.sortedItems
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("               CALCULATORS                ")
        sb.appendLine("  Mechanical Parts Calibration Invoice    ")
        sb.appendLine("==========================================")
        sb.appendLine("Bill No : ${bill.billId}")
        sb.appendLine("Session : ${bill.sessionId}")
        sb.appendLine("Date    : ${bill.formattedDateTime}")
        sb.appendLine("------------------------------------------")
        sb.appendLine(String.format("%-18s | %8s | %10s", "Product", "Quantity", "Amount"))
        sb.appendLine("------------------------------------------")
        for (item in items) {
            sb.appendLine(
                String.format(
                    "%-18s | %8s | %10s",
                    item.productName.take(18),
                    item.formattedQuantity,
                    item.lineAmountFormatted
                )
            )
        }
        sb.appendLine("------------------------------------------")
        sb.appendLine(
            String.format(
                "%-18s | %8s | %10s",
                "Grand Total",
                "",
                bill.grandTotalFormatted
            )
        )
        sb.appendLine("==========================================")
        return sb.toString()
    }

    /**
     * Generates a formatted PDF invoice file in the local cache and opens the Android system
     * Print dialog when an Activity print context is available.
     */
    fun printBillReceipt(context: Context, billWithItems: BillWithItems): String {
        val bill = billWithItems.bill
        val pdfFile = generateInvoicePdfFile(context, billWithItems)

        if (context is Activity) {
            try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val jobName = "Calculators_Invoice_${bill.billId}"
                    printManager.print(
                        jobName,
                        WorkshopReceiptPrintAdapter(context, billWithItems),
                        PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                            .build()
                    )
                    return "Print dialog opened for ${bill.billId}."
                }
            } catch (_: Exception) {
                // Fall back to saved PDF confirmation below if system print spooler is unavailable
            }
        }

        return if (pdfFile != null) {
            "Printable invoice PDF generated for ${bill.billId} (${pdfFile.name})."
        } else {
            "Print job prepared for ${bill.billId}."
        }
    }

    /**
     * Launches the Android system Share chooser with the formatted receipt text.
     */
    fun shareBillReceipt(context: Context, billWithItems: BillWithItems): String {
        val bill = billWithItems.bill
        val receiptText = formatPlainTextReceipt(billWithItems)
        return try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Invoice ${bill.billId} - Calculators")
                putExtra(Intent.EXTRA_TEXT, receiptText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(sendIntent, "Share Invoice ${bill.billId}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
            "Share options opened for ${bill.billId}."
        } catch (_: Exception) {
            "Receipt ${bill.billId} formatted for sharing."
        }
    }

    fun generateInvoicePdfFile(context: Context, billWithItems: BillWithItems): File? {
        return try {
            val invoicesDir = File(context.cacheDir, "invoices").apply { mkdirs() }
            val outFile = File(invoicesDir, "${billWithItems.bill.billId}.pdf")
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            drawInvoiceOnCanvas(page.canvas, billWithItems)
            pdfDocument.finishPage(page)
            FileOutputStream(outFile).use { stream ->
                pdfDocument.writeTo(stream)
            }
            pdfDocument.close()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    internal fun drawInvoiceOnCanvas(canvas: Canvas, billWithItems: BillWithItems) {
        val bill = billWithItems.bill
        val items = billWithItems.sortedItems

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(30, 41, 59)
            textSize = 11f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(51, 65, 85)
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }

        var y = 48f
        canvas.drawText("CALCULATORS - Mechanical Parts Calibration Invoice", 40f, y, titlePaint)
        y += 24f
        canvas.drawText("Bill No: ${bill.billId}   |   Session: ${bill.sessionId}   |   Date: ${bill.formattedDateTime}", 40f, y, bodyPaint)
        y += 16f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f

        canvas.drawText("Product / Mechanical Part", 48f, y, headerPaint)
        canvas.drawText("Quantity", 340f, y, headerPaint)
        canvas.drawText("Amount", 460f, y, headerPaint)
        y += 10f
        canvas.drawLine(40f, y, 555f, y, linePaint)

        for (item in items) {
            y += 22f
            canvas.drawText(item.productName, 48f, y, bodyPaint)
            canvas.drawText(item.formattedQuantity, 355f, y, bodyPaint)
            canvas.drawText(item.lineAmountFormatted, 460f, y, bodyPaint)
            y += 8f
            canvas.drawLine(40f, y, 555f, y, linePaint)
        }

        y += 24f
        canvas.drawText("Grand Total", 48f, y, headerPaint)
        canvas.drawText(bill.grandTotalFormatted, 460f, y, headerPaint)
        y += 12f
        canvas.drawLine(40f, y, 555f, y, linePaint)
    }
}

private class WorkshopReceiptPrintAdapter(
    private val context: Context,
    private val billWithItems: BillWithItems
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("${billWithItems.bill.billId}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        val pdfDocument = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            ReceiptExportHelper.drawInvoiceOnCanvas(page.canvas, billWithItems)
            pdfDocument.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use { out ->
                pdfDocument.writeTo(out)
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            pdfDocument.close()
        }
    }
}
