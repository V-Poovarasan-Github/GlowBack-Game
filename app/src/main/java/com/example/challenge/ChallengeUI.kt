package com.example.challenge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Helper to display human-readable time ago.
 */
fun formatTimeAgo(timestamp: Long): String {
    val deltaMs = System.currentTimeMillis() - timestamp
    val seconds = deltaMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}

/**
 * Top in-game banner showing active rival, target score, and remaining tries.
 */
@Composable
fun InGameChallengeBanner(
    rivalName: String,
    targetScore: Int,
    triesLeft: Int,
    maxTries: Int = 3,
    isRevenge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isRevenge) Color(0xFFFF0055) else Color(0xFF00E5FF)
    val dotColor = if (isRevenge) Color(0xFFFF0055) else Color(0xFF00E5FF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("ingame_challenge_banner"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRevenge) "REVENGE vs $rivalName" else "RIVAL: $rivalName",
                color = if (isRevenge) Color(0xFFFF5252) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "  •  TARGET: $targetScore",
                color = Color(0xFFFFD600),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            val triesDisplay = if (maxTries == 0) "Unlimited" else "$triesLeft"
            Text(
                text = "  •  TRIES: $triesDisplay",
                color = if (maxTries == 0 || triesLeft > 1) Color(0xFF00E676) else Color(0xFFFF5252),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Horizontal rail on the Idle screen displaying active challenges ONLY when non-empty.
 */
@Composable
fun ActiveChallengesRail(
    challenges: List<ChallengeItem>,
    onPlayChallenge: (ChallengeItem) -> Unit,
    onDeleteChallenge: (String) -> Unit,
    onOpenEnterModal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (challenges.isEmpty()) return

    val listState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(challenges.size) {
        listState.scrollToItem(0)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .testTag("active_challenges_rail"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
    ) {
        items(challenges, key = { it.code }) { item ->
            ChallengeCard(
                item = item,
                onPlay = { onPlayChallenge(item) },
                onDelete = { onDeleteChallenge(item.code) }
            )
        }
    }
}

@Composable
fun ChallengeCard(
    item: ChallengeItem,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        item.isWon -> Color(0xFF00E676)
        item.attemptsLeft <= 0 -> Color(0xFF64748B)
        item.isRevenge -> Color(0xFFFF0055)
        else -> Color(0xFF00E5FF)
    }

    Card(
        modifier = modifier
            .width(220.dp)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .testTag("challenge_card_${item.code}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.rivalName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("delete_challenge_${item.code}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove challenge",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.gridSizeTag.uppercase(),
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Target: ${item.targetScore}",
                    color = Color(0xFFFFD600),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Best: ${item.bestScore}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val triesText = when {
                    item.isWon -> "WON!"
                    item.attemptsLeft <= 0 -> "OUT OF TRIES"
                    else -> "${item.attemptsLeft}/${item.maxTries} tries"
                }
                val triesColor = when {
                    item.isWon -> Color(0xFF00E676)
                    item.attemptsLeft <= 0 -> Color(0xFFFF5252)
                    else -> Color(0xFF94A3B8)
                }

                Text(
                    text = triesText,
                    color = triesColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onPlay,
                    enabled = item.attemptsLeft > 0 || item.isWon,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.isWon) Color(0xFF00E676) else Color(0xFF00E5FF),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF1E293B),
                        disabledContentColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("play_challenge_${item.code}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (item.isWon) "REPLAY" else "PLAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Modal to enter a challenge code and optional rival name.
 */
@Composable
fun EnterChallengeDialog(
    onDismiss: () -> Unit,
    onSubmit: (ChallengeItem) -> Unit
) {
    val context = LocalContext.current
    var rawCode by remember { mutableStateOf("") }
    var rivalName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showHelpBanner by remember { mutableStateOf(false) }

    // Dialog state for legacy 5-segment codes
    var pendingLegacyChallenge by remember { mutableStateOf<ParsedChallenge?>(null) }

    Dialog(
        onDismissRequest = {
            if (pendingLegacyChallenge != null) {
                pendingLegacyChallenge = null
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (pendingLegacyChallenge != null) {
            LegacyCodeNameCardContent(
                initialName = rivalName,
                onDismiss = { pendingLegacyChallenge = null },
                onSubmitName = { enteredName ->
                    val parsed = pendingLegacyChallenge!!
                    pendingLegacyChallenge = null
                    val finalName = ChallengeSeed.sanitizePlayerName(enteredName)
                    val item = ChallengeItem(
                        code = parsed.fullCode,
                        rivalName = finalName,
                        gridSizeTag = parsed.gridTag,
                        targetScore = parsed.targetScore,
                        maxTries = parsed.tries,
                        attemptsLeft = parsed.tries,
                        bestScore = 0,
                        seed = parsed.seedInt,
                        difficulty = parsed.difficulty,
                        chainId = parsed.chainId
                    )
                    onSubmit(item)
                }
            )
        } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
                .testTag("enter_challenge_dialog_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enter challenge code",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("enter_challenge_dialog_title")
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showHelpBanner = !showHelpBanner },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("challenge_help_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "How challenge codes work",
                                tint = if (showHelpBanner) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("close_challenge_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showHelpBanner) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("challenge_help_banner")
                    ) {
                        Text(
                            text = "Ask a friend to share their challenge code from their Game Over screen, then paste it here!",
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = rawCode,
                    onValueChange = { input ->
                        rawCode = input.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                        errorMessage = null
                    },
                    label = { Text("Challenge code") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("challenge_code_input")
                )

                Text(
                    text = "Format: 4x4-12-T3H-NEON_KING-K7Q-8X2AB-C",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp)
                        .testTag("challenge_code_example_text")
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("challenge_code_error_text")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rivalName,
                    onValueChange = { rivalName = it },
                    label = { Text("Custom Rival Name (optional override)") },
                    placeholder = { Text("Auto-read from code if left empty", color = Color(0xFF475569)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("challenge_rival_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val parsed = ChallengeSeed.parseChallengeCode(rawCode)
                        val myName = ChallengeStorage.getMyName(context)
                        if (parsed == null) {
                            errorMessage = "Invalid challenge code format."
                        } else if (parsed.senderName.isNotEmpty() && parsed.senderName.equals(myName, ignoreCase = true)) {
                            errorMessage = "You cannot play your own challenge code!"
                            Toast.makeText(context, "You cannot play your own challenge code!", Toast.LENGTH_SHORT).show()
                        } else if (ChallengeStorage.isSelfChallenge(context, rawCode) || ChallengeStorage.isSelfChallenge(context, parsed.fullCode)) {
                            errorMessage = "You cannot play your own challenge code!"
                            Toast.makeText(context, "You cannot play your own challenge code!", Toast.LENGTH_SHORT).show()
                        } else if (parsed.isLegacy) {
                            // Confirmation 4: Pasting a legacy code must ALWAYS show "Old format code detected. Who sent this?" dialog. Never silently default to RIVAL.
                            pendingLegacyChallenge = parsed
                        } else {
                            // Check if an outbound awaiting card matches this chain
                            if (parsed.chainId.isNotEmpty()) {
                                ChallengeStorage.deleteAwaitingChallenge(context, parsed.chainId)
                            }
                            val finalRival = if (rivalName.isNotBlank()) {
                                ChallengeSeed.sanitizePlayerName(rivalName)
                            } else {
                                parsed.senderName.ifEmpty { "RIVAL" }
                            }
                            val item = ChallengeItem(
                                code = parsed.fullCode,
                                rivalName = finalRival,
                                gridSizeTag = parsed.gridTag,
                                targetScore = parsed.targetScore,
                                maxTries = parsed.tries,
                                attemptsLeft = parsed.tries,
                                bestScore = 0,
                                seed = parsed.seedInt,
                                difficulty = parsed.difficulty,
                                chainId = parsed.chainId
                            )
                            onSubmit(item)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_challenge_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Accept Challenge",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        }
    }
}

/**
 * Prompt for legacy 5-segment codes lacking a name segment.
 */
@Composable
fun LegacyCodeNameDialog(
    initialName: String = "",
    onDismiss: () -> Unit,
    onSubmitName: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        LegacyCodeNameCardContent(
            initialName = initialName,
            onDismiss = onDismiss,
            onSubmitName = onSubmitName
        )
    }
}

@Composable
fun LegacyCodeNameCardContent(
    initialName: String = "",
    onDismiss: () -> Unit,
    onSubmitName: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(initialName.replace(' ', '_').uppercase().filter { it.isLetterOrDigit() || it == '_' }.take(10)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(20.dp))
            .testTag("legacy_code_dialog"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Old format code detected",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("legacy_dialog_title")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Who sent this challenge?",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { input ->
                    val cleaned = input.replace(' ', '_').uppercase().filter { it.isLetterOrDigit() || it == '_' }
                    nameInput = if (cleaned.length > 10) cleaned.substring(0, 10) else cleaned
                },
                label = { Text("Rival's Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("legacy_name_input")
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("legacy_dialog_cancel"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
                Button(
                    onClick = {
                        val finalName = ChallengeSeed.sanitizePlayerName(nameInput)
                        onSubmitName(finalName)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("legacy_dialog_submit"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Accept", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Dialog shown after sharing or copying a challenge: "Did you share it?"
 */
@Composable
fun DidYouShareDialog(
    awaiting: AwaitingChallenge,
    onDismiss: () -> Unit,
    onConfirmShared: (AwaitingChallenge) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        DidYouShareCardContent(
            awaiting = awaiting,
            onDismiss = onDismiss,
            onConfirmShared = onConfirmShared
        )
    }
}

@Composable
fun DidYouShareCardContent(
    awaiting: AwaitingChallenge,
    onDismiss: () -> Unit,
    onConfirmShared: (AwaitingChallenge) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(22.dp))
            .testTag("did_you_share_dialog"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Did you share it?",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("did_you_share_title")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "If you sent this challenge to a friend, we'll keep an awaiting card in your Rivalry tab so you can track when they beat it.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("did_you_share_no_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("No, Cancel", color = Color(0xFF94A3B8))
                }
                Button(
                    onClick = { onConfirmShared(awaiting) },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(46.dp)
                        .testTag("did_you_share_yes_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Yes, Track it", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * Dialog for pasting a new code from a specific Rival card (strictly name-locked).
 */
@Composable
fun PasteRivalCodeDialog(
    rivalName: String,
    onDismiss: () -> Unit,
    onSubmit: (ChallengeItem) -> Unit
) {
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
                .testTag("paste_rival_code_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Paste Code from $rivalName",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("paste_rival_dialog_title")
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' }
                        errorMessage = null
                    },
                    label = { Text("Challenge code") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_rival_code_input")
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("paste_rival_error_text")
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val parsed = ChallengeSeed.parseChallengeCode(codeInput)
                        if (parsed == null) {
                            errorMessage = "Invalid challenge code format."
                        } else if (!parsed.isLegacy && !parsed.senderName.equals(rivalName, ignoreCase = true)) {
                            // Strict Name-Lock rule
                            val toastMsg = "This code is from ${parsed.senderName}. Paste it on the Home screen."
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            errorMessage = toastMsg
                        } else if (ChallengeStorage.isSelfChallenge(context, parsed.fullCode)) {
                            val toastMsg = "You cannot play your own challenge code!"
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                            errorMessage = toastMsg
                        } else {
                            if (parsed.chainId.isNotEmpty()) {
                                ChallengeStorage.deleteAwaitingChallenge(context, parsed.chainId)
                            }
                            val item = ChallengeItem(
                                code = parsed.fullCode,
                                rivalName = rivalName,
                                gridSizeTag = parsed.gridTag,
                                targetScore = parsed.targetScore,
                                maxTries = parsed.tries,
                                attemptsLeft = parsed.tries,
                                bestScore = 0,
                                seed = parsed.seedInt,
                                difficulty = parsed.difficulty,
                                chainId = parsed.chainId
                            )
                            onSubmit(item)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("paste_rival_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Accept Rival Code", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Share Tries Selector Dialog with sender name auto-fill, Free option, and board selector.
 */
@Composable
fun ShareTriesSelectorDialog(
    initialTries: Int = 3,
    boardOptions: List<BoardScoreOption> = emptyList(),
    currentScoreToShare: Int = 1,
    requireNameSetupFirst: Boolean = false,
    awaitingChallengeForDialog: AwaitingChallenge? = null,
    onDismiss: () -> Unit,
    onDismissDidYouShare: () -> Unit = onDismiss,
    onConfirmDidYouShare: (AwaitingChallenge) -> Unit = {},
    onGenerateCard: (tries: Int, senderName: String, chosenBoard: BoardScoreOption?) -> Unit
) {
    val context = LocalContext.current
    var isConfiguringName by remember { mutableStateOf(requireNameSetupFirst) }
    var senderNameInput by remember { mutableStateOf(ChallengeStorage.getMyName(context)) }
    val options = listOf(1, 3, 5, 0) // 0 = Free/Unlimited
    var selectedTries by remember { mutableIntStateOf(if (initialTries in options) initialTries else 3) }
    var selectedBoardIndex by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = {
            if (awaitingChallengeForDialog != null) {
                onDismissDidYouShare()
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (awaitingChallengeForDialog != null) {
            DidYouShareCardContent(
                awaiting = awaitingChallengeForDialog,
                onDismiss = onDismissDidYouShare,
                onConfirmShared = onConfirmDidYouShare
            )
        } else if (isConfiguringName) {
            ChallengerNameSetupCardContent(
                onSaveName = { savedName ->
                    ChallengeStorage.setMyName(context, savedName)
                    senderNameInput = savedName
                    isConfiguringName = false
                }
            )
        } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(22.dp))
                    .testTag("tries_selector_sheet"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Share Challenge",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f).testTag("share_challenge_header_title")
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("close_tries_selector_button")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = senderNameInput,
                        onValueChange = { input ->
                            val cleaned = input.replace(' ', '_').uppercase().filter { it.isLetterOrDigit() || it == '_' }
                            senderNameInput = if (cleaned.length > 10) cleaned.substring(0, 10) else cleaned
                        },
                        label = { Text("Your Display Name (max 10 chars)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_sender_name_input")
                    )

                    if (boardOptions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Target Score to Beat:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("board_scores_row")
                        ) {
                            itemsIndexed(boardOptions) { idx, opt ->
                                val isChosen = selectedBoardIndex == idx
                                Surface(
                                    onClick = { selectedBoardIndex = idx },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isChosen) Color(0xFF0284C7).copy(alpha = 0.45f) else Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, if (isChosen) Color(0xFF00E5FF) else Color(0xFF334155)),
                                    modifier = Modifier.testTag("board_option_$idx")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${opt.gridTag} ${opt.paceName}",
                                            color = if (isChosen) Color.White else Color(0xFF94A3B8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${opt.score}",
                                            color = Color(0xFFFFD600),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "How many tries does your friend get?",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().testTag("tries_selector_title")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(23.dp))
                            .padding(3.dp)
                            .testTag("tries_segmented_row"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        options.forEach { triesVal ->
                            val isSelected = selectedTries == triesVal
                            val label = if (triesVal == 0) "Free" else "$triesVal"
                            val tag = if (triesVal == 0) "tries_option_free" else "tries_option_$triesVal"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) Color(0xFF0284C7).copy(alpha = 0.42f) else Color.Transparent
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp))
                                        else Modifier
                                    )
                                    .clickable { selectedTries = triesVal }
                                    .testTag(tag),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                                    fontSize = if (triesVal == 0) 13.sp else 15.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontFamily = if (triesVal == 0) FontFamily.Default else FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val finalName = ChallengeSeed.sanitizePlayerName(senderNameInput)
                            ChallengeStorage.setMyName(context, finalName)
                            val chosenBoard = boardOptions.getOrNull(selectedBoardIndex)
                            onGenerateCard(selectedTries, finalName, chosenBoard)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_card_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF090D16)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Generate Card", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        }
    }
}

/**
 * Gate dialog shown when user has not yet completed a game with a score > 0.
 */
@Composable
fun FirstTimeShareGateDialog(
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(22.dp))
                .testTag("first_time_share_gate_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎮", fontSize = 30.sp)
                }

                Text(
                    text = "Set a score first!",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("first_time_share_title")
                )

                Text(
                    text = "Play a quick game so your friend has a real score to beat.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Button(
                    onClick = onPlayNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("first_time_share_play_now_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF090D16)
                    )
                ) {
                    Text("Play Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("first_time_share_dismiss_button")
                ) {
                    Text("Not Now", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * One-time dialog prompting the player for their Challenger Tag if not yet configured.
 */
@Composable
fun ChallengerNameSetupDialog(
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ChallengerNameSetupCardContent(onSaveName = onSaveName)
    }
}

@Composable
fun ChallengerNameSetupCardContent(
    onSaveName: (String) -> Unit
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(22.dp))
            .testTag("challenger_name_setup_dialog"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Enter Your Challenger Tag",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("challenger_tag_title")
            )

            Text(
                text = "Your rivals will see this name on their cards and challenge codes.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value = nameInput,
                onValueChange = { input ->
                    val cleaned = input.replace(' ', '_').uppercase().filter { it.isLetterOrDigit() || it == '_' }
                    nameInput = if (cleaned.length > 10) cleaned.substring(0, 10) else cleaned
                    errorText = null
                },
                label = { Text("Your Name (max 10 chars)") },
                placeholder = { Text("e.g. ALEX", color = Color(0xFF475569)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF00E5FF),
                    unfocusedLabelColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("challenger_tag_input")
            )

            if (errorText != null) {
                Text(text = errorText!!, color = Color(0xFFFF5252), fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val finalName = ChallengeSeed.sanitizePlayerName(nameInput)
                    if (nameInput.isBlank()) {
                        errorText = "Please enter a display name."
                    } else {
                        ChallengeStorage.setMyName(context, finalName)
                        onSaveName(finalName)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("challenger_tag_save_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF090D16)
                )
            ) {
                Text("Save & Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Full LEADERBOARD Screen containing:
 * - Tab 1: [ ⚔️ RIVALRY ]
 * - Tab 2: [ 🏆 RECORDS ]
 */
@Composable
fun HallOfFameScreen(
    onNavigateBack: () -> Unit,
    onStartRevenge: (RivalRecord) -> Unit,
    onPlayAwaiting: (AwaitingChallenge) -> Unit,
    onShareCodeClicked: () -> Unit,
    onPasteCodeClicked: () -> Unit,
    onAcceptChallenge: (ChallengeItem) -> Unit,
    sharedPrefs: android.content.SharedPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = RIVALRY, 1 = RECORDS

    var rivals by remember { mutableStateOf(ChallengeStorage.loadRivals(context)) }
    var awaitingChallenges by remember { mutableStateOf(ChallengeStorage.loadAwaitingChallenges(context)) }
    var rivalForPasteCode by remember { mutableStateOf<RivalRecord?>(null) }

    fun refreshData() {
        rivals = ChallengeStorage.loadRivals(context)
        awaitingChallenges = ChallengeStorage.loadAwaitingChallenges(context)
    }

    if (rivalForPasteCode != null) {
        PasteRivalCodeDialog(
            rivalName = rivalForPasteCode!!.name,
            onDismiss = { rivalForPasteCode = null },
            onSubmit = { item ->
                rivalForPasteCode = null
                onAcceptChallenge(item)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("hall_of_fame_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("leaderboard_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Game",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "HALL OF FAME",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f).testTag("leaderboard_screen_title")
            )
        }

        // 2-Tab Selector
        val tabs = listOf("⚔️ RIVALRY", "🏆 RECORDS")
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF0F172A),
            contentColor = Color(0xFF00E5FF),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF00E5FF),
                    height = 3.dp
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("leaderboard_tab_row")
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag(if (index == 0) "tab_rivalry" else "tab_records"),
                    text = {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == index) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                RivalryTabContent(
                    rivals = rivals,
                    awaitingChallenges = awaitingChallenges,
                    onGetRevenge = onStartRevenge,
                    onPasteNewCode = { rival -> rivalForPasteCode = rival },
                    onDeleteRival = { rival ->
                        ChallengeStorage.deleteRival(context, rival.name)
                        refreshData()
                    },
                    onDeleteAwaiting = { awaiting ->
                        ChallengeStorage.deleteAwaitingChallenge(context, awaiting.chainId)
                        refreshData()
                    },
                    onShareCode = onShareCodeClicked,
                    onPasteCode = onPasteCodeClicked,
                    modifier = Modifier.fillMaxSize().testTag("rivalry_tab_content")
                )
            } else {
                RecordsTabContent(
                    sharedPrefs = sharedPrefs,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Tab 1: Rivalry Content
 */
@Composable
fun RivalryTabContent(
    rivals: List<RivalRecord>,
    awaitingChallenges: List<AwaitingChallenge>,
    onGetRevenge: (RivalRecord) -> Unit,
    onPasteNewCode: (RivalRecord) -> Unit,
    onDeleteRival: (RivalRecord) -> Unit,
    onDeleteAwaiting: (AwaitingChallenge) -> Unit,
    onShareCode: () -> Unit,
    onPasteCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (rivals.isEmpty() && awaitingChallenges.isEmpty()) {
        EmptyRivalryState(
            onShareCode = onShareCode,
            onPasteCode = onPasteCode,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("rivalry_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Awaiting Response Section
        if (awaitingChallenges.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AWAITING RESPONSE (${awaitingChallenges.size})",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.testTag("awaiting_section_header")
                    )
                }
            }

            items(awaitingChallenges, key = { it.chainId }) { awaiting ->
                AwaitingCard(
                    awaiting = awaiting,
                    onCopyAgain = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Glowback Challenge Code", awaiting.code))
                        Toast.makeText(context, "Code copied: ${awaiting.code}", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = { onDeleteAwaiting(awaiting) }
                )
            }

            if (rivals.isEmpty()) {
                item {
                    Text(
                        text = "Battles will appear here once someone responds.",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Active Rivals Section
        if (rivals.isNotEmpty()) {
            item {
                Text(
                    text = "RIVAL BATTLES (${rivals.size})",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).testTag("rivals_section_header")
                )
            }

            items(rivals, key = { it.name }) { rival ->
                RivalCard(
                    rival = rival,
                    onDynamicAction = { onGetRevenge(rival) },
                    onPasteNewCode = { onPasteNewCode(rival) },
                    onDeleteRival = { onDeleteRival(rival) }
                )
            }
        }
    }
}

/**
 * Empty State for Rivalry tab.
 */
@Composable
fun EmptyRivalryState(
    onShareCode: () -> Unit,
    onPasteCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("empty_rivalry_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
                .border(2.dp, Color(0xFF00E5FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "⚔️", fontSize = 34.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No rivalries yet",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("empty_rivalry_title")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Share a challenge code with a friend, or paste theirs to start a battle.",
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onShareCode,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("empty_share_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Code", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Button(
                onClick = onPasteCode,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("empty_paste_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Paste Code", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Outbound Awaiting Card.
 */
@Composable
fun AwaitingCard(
    awaiting: AwaitingChallenge,
    onCopyAgain: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .testTag("awaiting_card_${awaiting.chainId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "❓", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Waiting for a challenger...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp).testTag("delete_awaiting_${awaiting.chainId}")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${awaiting.gridSizeTag.uppercase()} ${awaiting.difficulty.label} · Target: ${awaiting.targetScore} · ${formatTimeAgo(awaiting.createdAt)}",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCopyAgain,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("copy_awaiting_${awaiting.chainId}"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Code Again", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Head-to-Head Rival Card.
 */
@Composable
fun RivalCard(
    rival: RivalRecord,
    onDynamicAction: () -> Unit,
    onPasteNewCode: () -> Unit,
    onDeleteRival: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val statusColor = when (rival.lastStatus) {
        "WON" -> Color(0xFF00E676)
        "LOST" -> Color(0xFFFF5252)
        else -> Color(0xFFFFD600)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .testTag("rival_card_${rival.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: ⚔️ [NAME] and Three-dot menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚔️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rival.name,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.testTag("rival_card_name_${rival.name}")
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp).testTag("rival_menu_button_${rival.name}")
                    ) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu", tint = Color(0xFF94A3B8))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Paste New Code", color = Color.White) },
                            onClick = {
                                menuExpanded = false
                                onPasteNewCode()
                            },
                            modifier = Modifier.testTag("rival_menu_paste_${rival.name}")
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Rivalry", color = Color(0xFFFF5252)) },
                            onClick = {
                                menuExpanded = false
                                onDeleteRival()
                            },
                            modifier = Modifier.testTag("rival_menu_delete_${rival.name}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scoreline: Wins • Losses • Ties (Matches)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${rival.wins} Wins  •  ${rival.losses} Losses  •  ${rival.ties} Ties",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "  (${rival.matches} matches)",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Last Match Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF090E17))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last: ${rival.lastGrid.uppercase()} ${rival.lastDifficulty.label}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Target: ${rival.lastTarget} vs You: ${rival.lastUserScore} [${rival.lastStatus}]",
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Action Button & Secondary Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dynamic Button
                val (buttonLabel, buttonColor, buttonTextColor) = when (rival.lastStatus) {
                    "LOST" -> Triple("⚡ Get Revenge", Color(0xFFFF0055), Color.White)
                    "TIED" -> Triple("🤝 Tie-Breaker", Color(0xFFFFD600), Color.Black)
                    else -> Triple("👑 Send Challenge", Color(0xFF00E5FF), Color.Black)
                }

                Button(
                    onClick = onDynamicAction,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(38.dp)
                        .testTag("rival_dynamic_button_${rival.name}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(text = buttonLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPasteNewCode,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(38.dp)
                        .testTag("rival_paste_button_${rival.name}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(text = "Paste Code", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Tab 2: Records Content
 */
@Composable
fun RecordsTabContent(
    sharedPrefs: android.content.SharedPreferences,
    modifier: Modifier = Modifier
) {
    var selectedModeIndex by remember { mutableIntStateOf(0) } // 0 = SOLO, 1 = 2P, 2 = 3P, 3 = 4P
    var selectedGridIndex by remember { mutableIntStateOf(0) } // 0 = 2x2, 1 = 3x3, 2 = 4x4

    val modes = listOf("SOLO", "2P", "3P", "4P")
    val grids = listOf("2×2", "3×3", "4×4")
    val gridKeys = listOf("2x2", "3x3", "4x4")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("records_tab_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Selector: [SOLO] [2P] [3P] [4P]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(22.dp))
                .padding(3.dp)
                .testTag("records_mode_selector"),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            modes.forEachIndexed { index, modeLabel ->
                val isSelected = selectedModeIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (isSelected) Color(0xFF0284C7).copy(alpha = 0.42f) else Color.Transparent)
                        .then(if (isSelected) Modifier.border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(19.dp)) else Modifier)
                        .clickable { selectedModeIndex = index }
                        .testTag("records_mode_$modeLabel"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = modeLabel,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedModeIndex == 0) {
            // SOLO MODE: Grid Size Chips + 4 Score Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("records_grid_chips"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grids.forEachIndexed { index, gridLabel ->
                    val isSelected = selectedGridIndex == index
                    OutlinedButton(
                        onClick = { selectedGridIndex = index },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("records_grid_$gridLabel"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF00E5FF) else Color(0xFF334155)
                        )
                    ) {
                        Text(text = gridLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val currentGridKey = gridKeys[selectedGridIndex]

            // 4 Score Showcase Cards:
            // Easy Classic, Easy Progressive, Hard Classic, Hard Progressive
            val easyClassicKey = "best_${currentGridKey}_classic_EASY"
            val easyProgKey = "best_${currentGridKey}_progressive_EASY"
            val hardClassicKey = "best_${currentGridKey}_classic_HARD"
            val hardProgKey = "best_${currentGridKey}_progressive_HARD"

            val easyClassicScore = sharedPrefs.getInt(easyClassicKey, 0)
            val easyProgScore = sharedPrefs.getInt(easyProgKey, 0)
            val hardClassicScore = sharedPrefs.getInt(hardClassicKey, 0)
            val hardProgScore = sharedPrefs.getInt(hardProgKey, 0)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecordScoreCard(
                    title = "Easy · Classic",
                    score = easyClassicScore,
                    tag = "record_score_easy_classic",
                    accentColor = Color(0xFF00E676)
                )
                RecordScoreCard(
                    title = "Easy · Progressive",
                    score = easyProgScore,
                    tag = "record_score_easy_prog",
                    accentColor = Color(0xFF38BDF8)
                )
                RecordScoreCard(
                    title = "Hard · Classic",
                    score = hardClassicScore,
                    tag = "record_score_hard_classic",
                    accentColor = Color(0xFFFFB300)
                )
                RecordScoreCard(
                    title = "Hard · Progressive",
                    score = hardProgScore,
                    tag = "record_score_hard_prog",
                    accentColor = Color(0xFFFF5252)
                )
            }
        } else {
            // MULTIPLAYER MODE (2P, 3P, 4P)
            val playerCount = selectedModeIndex + 1
            val longestSurvivalKey = "longest_survival_${playerCount}p"
            val longestSurvival = sharedPrefs.getInt(longestSurvivalKey, 0)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Longest Match Survival",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (longestSurvival > 0) "Round $longestSurvival Survived" else "No matches yet",
                            color = Color(0xFFFFD600),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.testTag("multiplayer_longest_survival")
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Total Multiplayer Wins",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        for (i in 0 until playerCount) {
                            val winsKey = "wins_P${i + 1}"
                            val wins = sharedPrefs.getInt(winsKey, 0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("multiplayer_p${i + 1}_wins_row"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Player ${i + 1}",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$wins wins",
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordScoreCard(
    title: String,
    score: Int,
    tag: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Best: $score",
                color = if (score > 0) Color(0xFFFFD600) else Color(0xFF64748B),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
