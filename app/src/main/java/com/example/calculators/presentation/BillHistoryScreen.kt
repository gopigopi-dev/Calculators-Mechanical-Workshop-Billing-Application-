package com.example.calculators.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.calculators.data.BillEntity
import com.example.calculators.data.BillWithItems
import com.example.calculators.theme.JetBrainsMonoFontFamily
import com.example.calculators.viewmodel.BillHistoryUiState

/**
 * Phase 10: Dedicated Bill History screen displaying saved mechanical calibration bills
 * (most recent first), with search by Bill ID or item name, date filter, bill detail dialog,
 * and confirmed bill deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillHistoryScreen(
    historyState: BillHistoryUiState,
    onSearchQueryChange: (String) -> Unit,
    onDateFilterChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onOpenBillDetail: (String) -> Unit,
    onCloseBillDetail: () -> Unit,
    onRequestDeleteBill: (BillEntity) -> Unit,
    onConfirmDeleteBill: () -> Unit,
    onCancelDeleteBill: () -> Unit,
    onBackToBilling: () -> Unit,
    onReopenBill: (BillWithItems) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToBilling)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("bill_history_screen"),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onBackToBilling,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("history_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.content_desc_back_to_billing),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = stringResource(id = R.string.history_screen_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.history_screen_subtitle,
                                    historyState.allBills.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search by Bill ID or Mechanical Part / Item Name + Filter by Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = historyState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        label = { Text(text = stringResource(id = R.string.label_history_search)) },
                        placeholder = { Text(text = stringResource(id = R.string.placeholder_history_search)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (historyState.searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.testTag("clear_history_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = stringResource(id = R.string.button_clear)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .weight(0.58f)
                            .testTag("history_search_input")
                    )

                    OutlinedTextField(
                        value = historyState.dateFilter,
                        onValueChange = onDateFilterChange,
                        singleLine = true,
                        label = { Text(text = stringResource(id = R.string.label_history_date_filter)) },
                        placeholder = { Text(text = stringResource(id = R.string.placeholder_history_date_filter)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = if (historyState.dateFilter.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { onDateFilterChange("") },
                                    modifier = Modifier.testTag("clear_history_date_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = stringResource(id = R.string.button_clear)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .weight(0.42f)
                            .testTag("history_date_filter_input")
                    )
                }

                // Quick Date Filter Chips if saved bills exist
                if (historyState.availableDateKeys.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = historyState.dateFilter.isEmpty(),
                            onClick = { onDateFilterChange("") },
                            label = { Text(text = stringResource(id = R.string.filter_all_dates)) },
                            modifier = Modifier.testTag("date_chip_all")
                        )
                        historyState.availableDateKeys.forEach { dateKey ->
                            FilterChip(
                                selected = historyState.dateFilter == dateKey,
                                onClick = {
                                    onDateFilterChange(
                                        if (historyState.dateFilter == dateKey) "" else dateKey
                                    )
                                },
                                label = { Text(text = dateKey) },
                                modifier = Modifier.testTag("date_chip_$dateKey")
                            )
                        }
                    }
                }

                // Bill History List (Most recent first)
                val displayedBills = historyState.filteredBills
                if (displayedBills.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("empty_history_state"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (historyState.allBills.isEmpty()) {
                                        stringResource(id = R.string.empty_history_message)
                                    } else {
                                        stringResource(id = R.string.no_matching_history_message)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                if (historyState.searchQuery.isNotEmpty() || historyState.dateFilter.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = onClearFilters,
                                        modifier = Modifier.testTag("reset_history_filters_button")
                                    ) {
                                        Text(text = stringResource(id = R.string.button_reset_filters))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("history_bills_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = displayedBills,
                            key = { it.bill.billId }
                        ) { billWithItems ->
                            HistoryBillCard(
                                billWithItems = billWithItems,
                                onOpenClick = { onOpenBillDetail(billWithItems.bill.billId) },
                                onDeleteClick = { onRequestDeleteBill(billWithItems.bill) }
                            )
                        }
                    }
                }
            }
        }

        // Detailed Bill Inspection Dialog
        val detailBill = historyState.selectedBillDetail
        if (detailBill != null) {
            BillHistoryDetailDialog(
                billWithItems = detailBill,
                onDismiss = onCloseBillDetail,
                onReopenClick = {
                    onCloseBillDetail()
                    onReopenBill(detailBill)
                },
                onDeleteClick = {
                    onCloseBillDetail()
                    onRequestDeleteBill(detailBill.bill)
                }
            )
        }

        // Delete Bill Confirmation Dialog
        val pendingDelete = historyState.pendingDeleteBill
        if (pendingDelete != null) {
            DeleteBillConfirmationDialog(
                bill = pendingDelete,
                onConfirmDelete = onConfirmDeleteBill,
                onCancelDelete = onCancelDeleteBill
            )
        }
    }
}

@Composable
private fun HistoryBillCard(
    billWithItems: BillWithItems,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val bill = billWithItems.bill
    val items = billWithItems.sortedItems

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_bill_card_${bill.billId}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bill.billId,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("history_bill_id_${bill.billId}")
                    )

                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = bill.sessionId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = bill.grandTotalFormatted,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("history_bill_total_${bill.billId}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bill.formattedDateTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("history_bill_date_${bill.billId}")
                )

                Text(
                    text = stringResource(id = R.string.items_count_badge, items.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Item preview summary
            if (items.isNotEmpty()) {
                Text(
                    text = items.joinToString(", ") { it.productName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("history_bill_items_summary_${bill.billId}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_history_bill_${bill.billId}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(id = R.string.button_view_bill))
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(44.dp)
                        .testTag("delete_history_bill_button_${bill.billId}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(
                            id = R.string.content_desc_delete_bill,
                            bill.billId
                        ),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BillHistoryDetailDialog(
    billWithItems: BillWithItems,
    onDismiss: () -> Unit,
    onReopenClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val bill = billWithItems.bill
    val items = billWithItems.sortedItems

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .testTag("bill_history_detail_dialog"),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${bill.billId} (${bill.sessionId})",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("detail_bill_id")
                        )
                        Text(
                            text = bill.formattedDateTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("detail_bill_datetime")
                        )
                    }

                    Text(
                        text = bill.grandTotalFormatted,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("detail_bill_grand_total")
                    )
                }

                if (bill.originalTranscript.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = stringResource(id = R.string.voice_transcript_label),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bill.originalTranscript,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("history_detail_transcript")
                            )
                        }
                    }
                }

                // Table Header: 1. Image | 2. Items | 3. Quantity | 4. Amount
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.column_image),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.width(52.dp),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(id = R.string.column_product),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .weight(0.44f)
                                    .padding(start = 6.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.column_quantity),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(0.22f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(id = R.string.column_amount),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(0.34f),
                                textAlign = TextAlign.End
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .testTag("history_detail_items_list")
                        ) {
                            itemsIndexed(
                                items = items,
                                key = { _, item -> item.itemId }
                            ) { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("history_detail_item_row_${index}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.width(52.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MechanicalPartThumbnail(
                                            imageUri = item.imageUri,
                                            partName = item.productName,
                                            size = 42.dp,
                                            modifier = Modifier.testTag("history_detail_item_image_${index}")
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(0.44f)
                                            .padding(start = 6.dp, end = 4.dp)
                                    ) {
                                        Text(
                                            text = item.productName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.testTag("history_detail_item_name_${index}")
                                        )
                                        if (item.calibrationNotes.isNotBlank()) {
                                            Text(
                                                text = item.calibrationNotes,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = item.formattedQuantity,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .weight(0.22f)
                                            .testTag("history_detail_item_qty_${index}")
                                    )

                                    Text(
                                        text = item.lineAmountFormatted,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        textAlign = TextAlign.End,
                                        modifier = Modifier
                                            .weight(0.34f)
                                            .testTag("history_detail_item_amount_${index}")
                                    )
                                }

                                if (index < items.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_delete_bill_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.button_delete_bill))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onReopenClick,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reopen_bill_button")
                        ) {
                            Text(text = stringResource(id = R.string.button_reopen_bill))
                        }

                        Button(
                            onClick = onDismiss,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("close_bill_detail_button")
                        ) {
                            Text(text = stringResource(id = R.string.button_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteBillConfirmationDialog(
    bill: BillEntity,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit
) {
    BackHandler(onBack = onCancelDelete)

    Dialog(onDismissRequest = onCancelDelete) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("confirm_delete_bill_dialog"),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_delete_bill_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(
                        id = R.string.dialog_delete_bill_message,
                        bill.billId,
                        bill.grandTotalFormatted
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancelDelete,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_delete_bill_button")
                    ) {
                        Text(text = stringResource(id = R.string.button_cancel))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onConfirmDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("confirm_delete_bill_button")
                    ) {
                        Text(text = stringResource(id = R.string.button_delete_bill))
                    }
                }
            }
        }
    }
}
