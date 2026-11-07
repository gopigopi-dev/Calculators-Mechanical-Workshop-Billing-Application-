package com.example.calculators.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.calculators.image.ImageAssociation
import com.example.calculators.model.Product

/**
 * Phase 9: Dialog for viewing, confirming, changing, removing, or adding mechanical part images
 * from the local collection or Android device storage.
 */
@Composable
fun ImageSelectionDialog(
    targetProduct: Product?,
    availableImages: List<ImageAssociation>,
    onSelectImageForProduct: (String, ImageAssociation) -> Unit,
    onConfirmUncertainImage: (String) -> Unit,
    onRemoveImageFromProduct: (String) -> Unit,
    onAddImageFromDeviceStorage: () -> Unit,
    onRemoveImageFromCollection: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .testTag("image_selection_dialog"),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (targetProduct != null) {
                        stringResource(
                            id = R.string.dialog_part_image_title,
                            targetProduct.productName
                        )
                    } else {
                        stringResource(id = R.string.dialog_manage_images_title)
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("image_dialog_title")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (targetProduct != null) {
                        stringResource(id = R.string.dialog_part_image_subtitle)
                    } else {
                        stringResource(
                            id = R.string.dialog_manage_images_subtitle,
                            availableImages.size
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (targetProduct != null) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MechanicalPartThumbnail(
                                    imageUri = targetProduct.imageUri,
                                    partName = targetProduct.productName,
                                    needsConfirmation = targetProduct.imageNeedsConfirmation,
                                    size = 56.dp
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (targetProduct.imageSourceName.isNotBlank()) {
                                            stringResource(
                                                id = R.string.matched_image_filename_label,
                                                targetProduct.imageSourceName
                                            )
                                        } else {
                                            stringResource(id = R.string.no_image_associated_label)
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (targetProduct.imageNeedsConfirmation) {
                                        Text(
                                            text = stringResource(id = R.string.uncertain_image_match_notice),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.testTag("uncertain_image_notice")
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (targetProduct.imageNeedsConfirmation && !targetProduct.imageUri.isNullOrBlank()) {
                                    Button(
                                        onClick = { onConfirmUncertainImage(targetProduct.id) },
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("confirm_image_match_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(id = R.string.button_confirm_match))
                                    }
                                }

                                if (!targetProduct.imageUri.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { onRemoveImageFromProduct(targetProduct.id) },
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("remove_image_association_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LinkOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = stringResource(id = R.string.button_remove_image))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.image_collection_count_heading,
                            availableImages.size
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onAddImageFromDeviceStorage,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_image_from_storage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.button_upload_device_image))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .testTag("image_collection_list")
                ) {
                    items(
                        items = availableImages,
                        key = { it.id }
                    ) { imageAssoc ->
                        val isCurrentlySelected = targetProduct?.imageUri == imageAssoc.imageUri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectImageForProduct(targetProduct?.id.orEmpty(), imageAssoc)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("select_collection_image_${imageAssoc.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                MechanicalPartThumbnail(
                                    imageUri = imageAssoc.imageUri,
                                    partName = imageAssoc.primaryLabel,
                                    needsConfirmation = imageAssoc.isFilenameUnclear && !imageAssoc.isConfirmedByUser,
                                    size = 44.dp
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = imageAssoc.primaryLabel,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrentlySelected) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Medium
                                            }
                                        ),
                                        color = if (isCurrentlySelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = imageAssoc.originalFilename,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (targetProduct != null) {
                                TextButton(
                                    onClick = { onSelectImageForProduct(targetProduct.id, imageAssoc) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("assign_image_button_${imageAssoc.id}")
                                ) {
                                    Text(
                                        text = if (isCurrentlySelected) {
                                            stringResource(id = R.string.label_selected_image)
                                        } else {
                                            stringResource(id = R.string.button_select_image)
                                        }
                                    )
                                }
                            } else if (!imageAssoc.isBundledAsset) {
                                IconButton(
                                    onClick = { onRemoveImageFromCollection(imageAssoc.id) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(48.dp)
                                        .testTag("delete_collection_image_${imageAssoc.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(
                                            id = R.string.content_desc_delete_product,
                                            imageAssoc.originalFilename
                                        ),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_image_dialog_button")
                    ) {
                        Text(text = stringResource(id = R.string.button_close))
                    }
                }
            }
        }
    }
}
