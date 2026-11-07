package com.example.calculators.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R

@Composable
fun AddProductDialog(
    formState: AddProductFormState,
    onProductNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCalibrationNotesChange: (String) -> Unit = {},
    onSelectFormImageClick: () -> Unit = {},
    onRemoveFormImageClick: () -> Unit = {},
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    BackHandler(onBack = onCancelClick)

    Dialog(
        onDismissRequest = onCancelClick,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("add_product_dialog"),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(
                        id = if (formState.isEditMode) {
                            R.string.dialog_edit_product_title
                        } else {
                            R.string.dialog_add_product_title
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("product_dialog_title")
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(
                        id = if (formState.isEditMode) {
                            R.string.dialog_edit_product_subtitle
                        } else {
                            R.string.dialog_add_product_subtitle
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phase 9 & 10: Associated Image preview + manual Replace / Remove controls
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("dialog_matched_image_preview")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            MechanicalPartThumbnail(
                                imageUri = formState.imageUri,
                                partName = formState.productNameInput.ifBlank { "Item" },
                                size = 44.dp
                            )
                            Text(
                                text = if (!formState.imageUri.isNullOrBlank()) {
                                    stringResource(
                                        id = R.string.matched_image_filename_label,
                                        formState.imageSourceName.ifBlank { formState.productNameInput }
                                    )
                                } else {
                                    stringResource(id = R.string.no_image_associated_label)
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = onSelectFormImageClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dialog_select_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = stringResource(id = R.string.button_select_image))
                            }

                            if (!formState.imageUri.isNullOrBlank()) {
                                TextButton(
                                    onClick = onRemoveFormImageClick,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("dialog_remove_image_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LinkOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Part / Machine Name Field (supports Tamil and English scripts without auto-translation)
                OutlinedTextField(
                    value = formState.productNameInput,
                    onValueChange = onProductNameChange,
                    label = { Text(text = stringResource(id = R.string.label_product_name)) },
                    placeholder = { Text(text = stringResource(id = R.string.placeholder_product_name)) },
                    singleLine = true,
                    isError = formState.productNameError != null,
                    supportingText = if (formState.productNameError != null) {
                        {
                            Text(
                                text = stringResource(id = R.string.error_empty_product_name),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("product_name_error_text")
                            )
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Optional Quantity Field
                OutlinedTextField(
                    value = formState.quantityInput,
                    onValueChange = onQuantityChange,
                    label = { Text(text = stringResource(id = R.string.label_quantity)) },
                    placeholder = { Text(text = stringResource(id = R.string.placeholder_quantity)) },
                    singleLine = true,
                    isError = formState.quantityError != null,
                    supportingText = if (formState.quantityError != null) {
                        {
                            Text(
                                text = stringResource(id = R.string.error_invalid_quantity),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("quantity_error_text")
                            )
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quantity_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Amount Field (entered and validated independently)
                OutlinedTextField(
                    value = formState.amountInput,
                    onValueChange = onAmountChange,
                    label = { Text(text = stringResource(id = R.string.label_amount)) },
                    placeholder = { Text(text = stringResource(id = R.string.placeholder_amount)) },
                    singleLine = true,
                    isError = formState.amountError != null,
                    supportingText = if (formState.amountError != null) {
                        {
                            Text(
                                text = stringResource(id = R.string.error_invalid_amount),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("amount_error_text")
                            )
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("amount_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Optional Calibration Reference / Notes Field (user-entered, never assumed)
                OutlinedTextField(
                    value = formState.calibrationNotesInput,
                    onValueChange = onCalibrationNotesChange,
                    label = { Text(text = stringResource(id = R.string.label_calibration_notes)) },
                    placeholder = { Text(text = stringResource(id = R.string.placeholder_calibration_notes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calibration_notes_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_product_button")
                    ) {
                        Text(text = stringResource(id = R.string.button_cancel))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onSaveClick,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_product_button")
                    ) {
                        Text(text = stringResource(id = R.string.button_save))
                    }
                }
            }
        }
    }
}
