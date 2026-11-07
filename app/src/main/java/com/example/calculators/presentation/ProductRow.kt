package com.example.calculators.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.R
import com.example.calculators.model.Product
import com.example.calculators.theme.JetBrainsMonoFontFamily

/**
 * Phase 9: Calibration Row with strict 5-column order:
 * 1. Image | 2. Part | 3. Quantity | 4. Amount | 5. Action
 */
@Composable
fun ProductRow(
    product: Product,
    onEditClick: (Product) -> Unit,
    onDeleteClick: (String) -> Unit,
    onImageClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("product_row_${product.id}")
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Column 1: Image (First Column — thumbnail maintaining original aspect ratio)
        Box(
            modifier = Modifier
                .width(56.dp)
                .minimumInteractiveComponentSize()
                .testTag("product_image_cell_${product.id}"),
            contentAlignment = Alignment.Center
        ) {
            MechanicalPartThumbnail(
                imageUri = product.imageUri,
                partName = product.productName,
                needsConfirmation = product.imageNeedsConfirmation,
                size = 48.dp,
                onClick = onImageClick,
                modifier = Modifier.testTag("product_thumbnail_${product.id}")
            )
        }

        // Column 2: Product / Mechanical Part Name (supports Tamil & English names and optional notes)
        Column(
            modifier = Modifier
                .weight(0.36f)
                .padding(start = 6.dp, end = 6.dp)
        ) {
            Text(
                text = product.productName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("product_name_${product.id}")
            )
            if (product.calibrationNotes.isNotBlank()) {
                Text(
                    text = product.calibrationNotes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("product_notes_${product.id}")
                )
            }
            if (product.imageNeedsConfirmation) {
                Text(
                    text = stringResource(id = R.string.label_image_needs_confirmation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("product_image_confirm_badge_${product.id}")
                )
            }
        }

        // Column 3: Quantity (Optional — displays "-" when null)
        Text(
            text = product.formattedQuantity,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(0.18f)
                .testTag("product_quantity_${product.id}")
        )

        // Column 4: Amount
        Text(
            text = product.formattedAmount,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(0.24f)
                .padding(end = 4.dp)
                .testTag("product_amount_${product.id}")
        )

        // Column 5: Action (Edit + Delete buttons)
        Row(
            modifier = Modifier.width(88.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onEditClick(product) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .testTag("edit_product_button_${product.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(
                        id = R.string.content_desc_edit_product,
                        product.productName
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = { onDeleteClick(product.id) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .testTag("delete_product_button_${product.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(
                        id = R.string.content_desc_delete_product,
                        product.productName
                    ),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
