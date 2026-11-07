package com.example.calculators.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.calculators.theme.JetBrainsMonoFontFamily
import com.example.calculators.voice.RecognitionLanguage
import com.example.calculators.voice.SpeechRecognitionState
import com.example.calculators.voice.VoiceListeningStatus

@Composable
fun VoiceRecognitionCard(
    speechState: SpeechRecognitionState,
    onLanguageSelected: (RecognitionLanguage) -> Unit,
    onTranscriptChanged: (String) -> Unit,
    onReprocessTranscript: () -> Unit,
    onClearTranscript: () -> Unit,
    onRetryVoiceRecognition: () -> Unit,
    onReviewUncertainProduct: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isListeningOrProcessing =
        speechState.status == VoiceListeningStatus.LISTENING ||
            speechState.status == VoiceListeningStatus.PROCESSING

    val borderColor = when (speechState.status) {
        VoiceListeningStatus.LISTENING,
        VoiceListeningStatus.PROCESSING -> MaterialTheme.colorScheme.primary
        VoiceListeningStatus.ERROR -> MaterialTheme.colorScheme.error
        VoiceListeningStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
        VoiceListeningStatus.IDLE -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_recognition_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Header Row: Title + Language Selector Chips (English / Tamil)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isListeningOrProcessing) {
                            Icons.Outlined.GraphicEq
                        } else {
                            Icons.Outlined.Mic
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.voice_section_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RecognitionLanguage.entries.forEach { language ->
                        val isSelected = speechState.selectedLanguage == language
                        val tag = if (language == RecognitionLanguage.ENGLISH_IN) {
                            "voice_lang_en_button"
                        } else {
                            "voice_lang_ta_button"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { onLanguageSelected(language) },
                            label = {
                                Text(
                                    text = stringResource(id = language.labelResId),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag(tag)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Listening Status Banner
            val statusText = when (speechState.status) {
                VoiceListeningStatus.IDLE -> stringResource(id = R.string.voice_status_idle)
                VoiceListeningStatus.LISTENING -> stringResource(id = R.string.voice_status_listening)
                VoiceListeningStatus.PROCESSING -> stringResource(id = R.string.voice_status_processing)
                VoiceListeningStatus.SUCCESS -> stringResource(id = R.string.voice_status_success)
                VoiceListeningStatus.ERROR -> {
                    speechState.errorType?.let { stringResource(id = it.messageResId) }
                        ?: stringResource(id = R.string.voice_error_generic)
                }
            }

            val statusContainerColor = when (speechState.status) {
                VoiceListeningStatus.LISTENING,
                VoiceListeningStatus.PROCESSING -> MaterialTheme.colorScheme.primaryContainer
                VoiceListeningStatus.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
                VoiceListeningStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                VoiceListeningStatus.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            }

            val statusContentColor = when (speechState.status) {
                VoiceListeningStatus.LISTENING,
                VoiceListeningStatus.PROCESSING -> MaterialTheme.colorScheme.onPrimaryContainer
                VoiceListeningStatus.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
                VoiceListeningStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                VoiceListeningStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = statusContainerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_status_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (speechState.status == VoiceListeningStatus.ERROR) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = statusContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color = statusContentColor, shape = CircleShape)
                                .testTag("voice_status_indicator_dot")
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusContentColor,
                        modifier = Modifier.testTag("voice_status_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Editable Recognized Transcript Field
            val displayedText = if (
                speechState.status == VoiceListeningStatus.LISTENING &&
                speechState.partialTranscript.isNotBlank() &&
                speechState.transcript.isBlank()
            ) {
                speechState.partialTranscript
            } else {
                speechState.transcript
            }

            OutlinedTextField(
                value = displayedText,
                onValueChange = onTranscriptChanged,
                label = {
                    Text(text = stringResource(id = R.string.voice_transcript_label))
                },
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.voice_transcript_placeholder),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_transcript_input")
            )

            // Phase 5–8: Processed Transcript & Product Extraction Summary
            val processed = speechState.processedTranscript
            val parsingResult = speechState.parsingResult
            if (processed.hasProcessedData) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("processed_transcript_summary")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.processed_language_label,
                                    processed.detectedLanguage.displayName
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("processed_language_text")
                            )

                            if (processed.originalTranscript.isNotBlank()) {
                                Text(
                                    text = stringResource(
                                        id = R.string.processed_original_label,
                                        processed.originalTranscript
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(start = 8.dp)
                                        .testTag("processed_original_text")
                                )
                            }
                        }

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.processed_product_words_label,
                                    processed.formattedProductWords.ifEmpty { "-" }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(0.55f)
                                    .testTag("processed_product_words_text")
                            )

                            Text(
                                text = stringResource(
                                    id = R.string.processed_numbers_label,
                                    processed.formattedNormalizedNumbers.ifEmpty { "-" }
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(0.45f)
                                    .testTag("processed_numbers_text")
                            )
                        }

                        if (parsingResult.parsedProducts.isNotEmpty()) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            val readyCount = parsingResult.confidentProducts.size
                            val uncertainList = parsingResult.uncertainProducts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val summaryLabel = if (uncertainList.isEmpty()) {
                                    stringResource(id = R.string.extracted_items_summary, readyCount)
                                } else {
                                    stringResource(
                                        id = R.string.extracted_items_with_review_summary,
                                        readyCount,
                                        uncertainList.size
                                    )
                                }
                                Text(
                                    text = summaryLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (uncertainList.isEmpty()) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("extracted_products_status_text")
                                )

                                if (uncertainList.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { onReviewUncertainProduct(0) },
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("review_uncertain_product_button")
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.button_review_entry),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            val firstUncertainWarning = uncertainList.firstOrNull()?.statusMessage
                            if (!firstUncertainWarning.isNullOrBlank()) {
                                Text(
                                    text = firstUncertainWarning,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("uncertain_product_warning_text")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Controls Row: Privacy Note + Reprocess / Clear / Retry Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.voice_privacy_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                        .testTag("voice_privacy_notice_text")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onReprocessTranscript,
                        enabled = speechState.transcript.isNotBlank(),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reprocess_transcript_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.button_reprocess_transcript),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    OutlinedButton(
                        onClick = onClearTranscript,
                        enabled = speechState.transcript.isNotEmpty() || speechState.partialTranscript.isNotEmpty(),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_transcript_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.button_clear_transcript),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    OutlinedButton(
                        onClick = onRetryVoiceRecognition,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("retry_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.button_retry_voice),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
