package com.example.challenge

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a neon-styled trophy Challenge Card and launches the Android Share Sheet
 * using FileProvider without internet permissions.
 */
object ChallengeShareHelper {
    private const val LOG_TAG = "ChallengeShareHelper"

    /**
     * Renders a 1080x1350 high-resolution bitmap with a neon card design.
     */
    fun createChallengeBitmap(
        code: String,
        gridSizeTag: String,
        score: Int,
        attempts: Int = 1,
        tries: Int = 3,
        paceName: String = "Classic",
        difficulty: GameDifficulty = GameDifficulty.EASY,
        isReply: Boolean = false,
        rivalName: String? = null,
        won: Boolean = false,
        tied: Boolean = false
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Base dark background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#0B0F19"),
                Color.parseColor("#04060A"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Ambient neon glow spots
        val glowPaint = Paint().apply {
            shader = RadialGradient(
                width * 0.3f, height * 0.25f, 500f,
                Color.parseColor("#2600E5FF"), // translucent cyan
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.3f, height * 0.25f, 500f, glowPaint)

        val glowPaint2 = Paint().apply {
            shader = RadialGradient(
                width * 0.7f, height * 0.65f, 600f,
                Color.parseColor("#22FF0055"), // translucent magenta
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.7f, height * 0.65f, 600f, glowPaint2)

        // 3. Outer border frame
        val borderPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }
        val innerRect = RectF(48f, 48f, (width - 48).toFloat(), (height - 48).toFloat())
        canvas.drawRoundRect(innerRect, 40f, 40f, borderPaint)

        // 4. Header Dots & Title
        val dotRadius = 14f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        dotPaint.color = Color.parseColor("#00E676") // Green
        canvas.drawCircle(120f, 150f, dotRadius, dotPaint)
        dotPaint.color = Color.parseColor("#00E5FF") // Cyan
        canvas.drawCircle(160f, 150f, dotRadius, dotPaint)
        dotPaint.color = Color.parseColor("#FF1744") // Red
        canvas.drawCircle(200f, 150f, dotRadius, dotPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("GLOWBACK", 240f, 168f, titlePaint)

        // Badge: Async Challenge
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#152238")
            style = Paint.Style.FILL
        }
        val badgeRect = RectF(100f, 230f, (width - 100).toFloat(), 310f)
        canvas.drawRoundRect(badgeRect, 20f, 20f, badgeBgPaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isReply && tied -> Color.parseColor("#FFD600")
                isReply && won -> Color.parseColor("#00E676")
                else -> Color.parseColor("#00E5FF")
            }
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        val badgeLabel = if (isReply) {
            when {
                tied -> "CHALLENGE TIED! 🤝"
                won -> "CHALLENGE CONQUERED!"
                else -> "CHALLENGE COMPLETED"
            }
        } else {
            "OFFICIAL ASYNC CHALLENGE"
        }
        canvas.drawText(badgeLabel, width / 2f, 282f, badgeTextPaint)

        // 5. Hero Stats Center Card
        val centerCardRect = RectF(100f, 350f, (width - 100).toFloat(), 840f)
        val centerCardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(centerCardRect, 32f, 32f, centerCardBg)

        val centerCardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                tied -> Color.parseColor("#FFD600")
                won -> Color.parseColor("#00E676")
                else -> Color.parseColor("#38BDF8")
            }
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(centerCardRect, 32f, 32f, centerCardBorder)

        // Grid tag pill
        val gridPillRect = RectF(width / 2f - 140f, 390f, width / 2f + 140f, 450f)
        val gridPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
        }
        canvas.drawRoundRect(gridPillRect, 30f, 30f, gridPillPaint)

        val gridPillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.1f
        }
        canvas.drawText("GRID: ${gridSizeTag.uppercase()}", width / 2f, 432f, gridPillText)

        // Score Label & Number
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText(if (isReply) "MY SCORE" else "TARGET SCORE TO BEAT", width / 2f, 530f, statLabelPaint)

        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (won) Color.parseColor("#00E676") else Color.parseColor("#FFD600")
            textSize = 140f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(score.toString(), width / 2f, 680f, scorePaint)

        val subStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        val triesLabel = if (tries == 0) "Unlimited" else "$tries"
        val subStatText = if (isReply && rivalName != null) {
            "Rival: $rivalName"
        } else {
            "Target: $score  •  Tries: $triesLabel  •  ${difficulty.label}  •  $paceName"
        }
        canvas.drawText(subStatText, width / 2f, 760f, subStatPaint)

        // 6. Challenge Code Box
        val codeBoxRect = RectF(100f, 880f, (width - 100).toFloat(), 1080f)
        val codeBoxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#09101E")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(codeBoxRect, 28f, 28f, codeBoxBg)

        val codeBoxBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(codeBoxRect, 28f, 28f, codeBoxBorder)

        val codeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.drawText("CHALLENGE CODE", width / 2f, 935f, codeLabelPaint)

        val codeValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (code.length > 22) 38f else 54f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = if (code.length > 22) 0.06f else 0.14f
        }
        canvas.drawText(code, width / 2f, 1020f, codeValuePaint)

        // 7. Footer Instructions
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Open Glowback  ➔  Rivalry Tab  ➔  Paste code to play", width / 2f, 1160f, footerPaint)

        val brandFootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        canvas.drawText("100% Offline Peer-to-Peer Sequence Challenge", width / 2f, 1220f, brandFootPaint)

        return bitmap
    }

    /**
     * Shares/copies plain code text only (no bitmap card generation) when consecutive shares
     * target the same rival.
     */
    suspend fun shareTextOnly(
        context: Context,
        code: String,
        chooserTitle: String = "Share Glowback Challenge"
    ) = withContext(Dispatchers.Main) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Glowback Challenge Code", code))
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, code)
            putExtra(Intent.EXTRA_SUBJECT, "Glowback Challenge")
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (t: ActivityNotFoundException) {
            if (BuildConfig.DEBUG) {
                Log.w(LOG_TAG, "No activity found to share challenge text", t)
            }
            Toast.makeText(context, "No app available to share challenge.", Toast.LENGTH_SHORT).show()
        } catch (t: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(LOG_TAG, "Failed to launch text share chooser", t)
            }
            Toast.makeText(context, "Unable to open share menu.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Writes the bitmap to app cache and launches the system share sheet with both the PNG trophy card
     * and the 7-segment code text caption on ALL shares (prevents text-editing forgery).
     */
    suspend fun shareChallenge(
        context: Context,
        code: String,
        gridSizeTag: String,
        score: Int,
        attempts: Int = 1,
        tries: Int = 3,
        paceName: String = "Classic",
        difficulty: GameDifficulty = GameDifficulty.EASY,
        rivalName: String? = null
    ) = withContext(Dispatchers.IO) {
        if (!rivalName.isNullOrBlank()) {
            ChallengeStorage.setLastSharedRivalName(context, rivalName)
        }

        val bitmap = createChallengeBitmap(
            code = code,
            gridSizeTag = gridSizeTag,
            score = score,
            attempts = attempts,
            tries = tries,
            paceName = paceName,
            difficulty = difficulty,
            isReply = false,
            rivalName = rivalName
        )

        val contentUri = try {
            val cacheFolder = File(context.cacheDir, "shared_challenges").apply { mkdirs() }
            cleanOldSharedFiles(cacheFolder, 24L)
            val imageFile = File(cacheFolder, "glowback_challenge_${code}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (t: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(LOG_TAG, "Failed to prepare FileProvider URI for challenge card", t)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Unable to prepare challenge card for sharing.", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val shareCaption = code

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, shareCaption)
            putExtra(Intent.EXTRA_SUBJECT, "Glowback Challenge")
            clipData = android.content.ClipData.newUri(context.contentResolver, "Glowback Challenge Card", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        withContext(Dispatchers.Main) {
            val chooser = Intent.createChooser(sendIntent, "Share Glowback Challenge").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(chooser)
            } catch (t: ActivityNotFoundException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "No activity found to share challenge card", t)
                }
                Toast.makeText(context, "No app available to share challenge.", Toast.LENGTH_SHORT).show()
            } catch (t: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "Failed to launch challenge card chooser", t)
                }
                Toast.makeText(context, "Unable to open share menu.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Always shares the PNG response card + 7-segment code caption back to a rival (prevents text-editing forgery).
     */
    suspend fun shareReplyResult(
        context: Context,
        code: String,
        gridSizeTag: String,
        myScore: Int,
        targetScore: Int,
        rivalName: String,
        won: Boolean,
        tied: Boolean = myScore == targetScore
    ) = withContext(Dispatchers.IO) {
        if (rivalName.isNotBlank()) {
            ChallengeStorage.setLastSharedRivalName(context, rivalName)
        }

        val bitmap = createChallengeBitmap(
            code = code,
            gridSizeTag = gridSizeTag,
            score = myScore,
            attempts = 1,
            isReply = true,
            rivalName = rivalName,
            won = won && !tied,
            tied = tied
        )

        val contentUri = try {
            val cacheFolder = File(context.cacheDir, "shared_challenges").apply { mkdirs() }
            cleanOldSharedFiles(cacheFolder, 24L)
            val imageFile = File(cacheFolder, "glowback_result_${code}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (t: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(LOG_TAG, "Failed to prepare FileProvider URI for reply card", t)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Unable to prepare result card for sharing.", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val shareCaption = code

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, shareCaption)
            putExtra(Intent.EXTRA_SUBJECT, "Glowback Result")
            clipData = android.content.ClipData.newUri(context.contentResolver, "Glowback Result Card", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        withContext(Dispatchers.Main) {
            val chooser = Intent.createChooser(sendIntent, "Send Result to Rival").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(chooser)
            } catch (t: ActivityNotFoundException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "No activity found to share reply card", t)
                }
                Toast.makeText(context, "No app available to share result.", Toast.LENGTH_SHORT).show()
            } catch (t: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "Failed to launch reply card chooser", t)
                }
                Toast.makeText(context, "Unable to open share menu.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Deletes cached share images older than [maxAgeHours] to prevent unbounded disk growth.
     */
    fun cleanOldSharedFiles(cacheFolder: File, maxAgeHours: Long = 24L) {
        try {
            val now = System.currentTimeMillis()
            val maxAgeMillis = maxAgeHours * 60 * 60 * 1000L
            val files = cacheFolder.listFiles() ?: return
            for (file in files) {
                if (file.isFile && (now - file.lastModified()) > maxAgeMillis) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Best effort cache cleanup
        }
    }
}
