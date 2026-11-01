package com.example.notification

import android.content.Context
import android.content.SharedPreferences
import kotlin.random.Random

/**
 * Safe phrase template that maps arguments according to individual template requirements,
 * avoiding any String.format argument mismatch crashes.
 */
data class NotificationPhrase(
    val template: String,
    val format: (score: Int, gridLabel: String) -> String
)

/**
 * Dynamic Content Provider supplying 30 high-retention phrases categorized into 3 time tiers.
 * - Tier 1: 0-24 Hours (Focus: High Score & Grid)
 * - Tier 2: 24-48 Hours (Focus: The "Rust" Phase)
 * - Tier 3: 48-72+ Hours (Focus: Urgency & "Ghost" Phase)
 */
object NotificationContentProvider {

    private const val PREFS_NOTIFICATION = "glowback_notification_prefs"
    private const val KEY_LAST_PHRASE_INDEX = "last_notification_phrase_index"

    // Tier 1: 0-24 Hours (1 to 10)
    val TIER_1 = listOf(
        NotificationPhrase("Your best is %d on %s. Can you beat it today?") { score, grid ->
            if (score > 0) "Your best is $score on $grid. Can you beat it today?"
            else "Ready to set your first high score on $grid today?"
        },
        NotificationPhrase("%s grid. %d steps. Let's see if you can push it further.") { score, grid ->
            if (score > 0) "$grid grid. $score steps. Let's see if you can push it further."
            else "$grid grid is waiting. Let's see how far you can push your sequence."
        },
        NotificationPhrase("Brain warm-up time. Can you hit %d on the %s board?") { score, grid ->
            if (score > 0) "Brain warm-up time. Can you hit $score on the $grid board?"
            else "Brain warm-up time. Jump onto the $grid board and test your memory!"
        },
        NotificationPhrase("Your record of %d is looking vulnerable. Defend it!") { score, _ ->
            if (score > 0) "Your record of $score is looking vulnerable. Defend it!"
            else "A blank slate is waiting. Time to set your first record!"
        },
        NotificationPhrase("Ready to glow? Your %s high score is waiting to be broken.") { _, grid ->
            "Ready to glow? Your $grid high score is waiting to be broken."
        },
        NotificationPhrase("Just one more round. Can you beat your best of %d?") { score, _ ->
            if (score > 0) "Just one more round. Can you beat your best of $score?"
            else "Just one quick round. How far can you go?"
        },
        NotificationPhrase("The %s board is lit. Can you reach %d steps again?") { score, grid ->
            if (score > 0) "The $grid board is lit. Can you reach $score steps again?"
            else "The $grid board is lit. Ready to light up the sequence?"
        },
        NotificationPhrase("Memory check! Your best is %d. Let's see what you've got.") { score, _ ->
            if (score > 0) "Memory check! Your best is $score. Let's see what you've got."
            else "Memory check! Jump in and see what you've got."
        },
        NotificationPhrase("Glowback time. Can you top your %d step record?") { score, _ ->
            if (score > 0) "Glowback time. Can you top your $score step record?"
            else "Glowback time. Ready to set your first memory record?"
        },
        NotificationPhrase("Fresh day, fresh sequence. Can you beat %d on %s?") { score, grid ->
            if (score > 0) "Fresh day, fresh sequence. Can you beat $score on $grid?"
            else "Fresh day, fresh sequence. Can you conquer the $grid grid today?"
        }
    )

    // Tier 2: 24-48 Hours (11 to 20)
    val TIER_2 = listOf(
        NotificationPhrase("It's been a day. Your memory muscles are getting rusty. Play now!") { _, _ ->
            "It's been a day. Your memory muscles are getting rusty. Play now!"
        },
        NotificationPhrase("24 hours since your last round. Don't let your %d record gather dust.") { score, _ ->
            if (score > 0) "24 hours since your last round. Don't let your $score record gather dust."
            else "24 hours since your last round. Don't let your memory skills gather dust."
        },
        NotificationPhrase("Your brain needs a reset. Jump back into the %s grid.") { _, grid ->
            "Your brain needs a reset. Jump back into the $grid grid."
        },
        NotificationPhrase("Feeling sharp? Prove it. Beat your best of %d today.") { score, _ ->
            if (score > 0) "Feeling sharp? Prove it. Beat your best of $score today."
            else "Feeling sharp? Prove it. Set a new high score today."
        },
        NotificationPhrase("A day away from Glowback makes the sequences feel twice as long. Come back!") { _, _ ->
            "A day away from Glowback makes the sequences feel twice as long. Come back!"
        },
        NotificationPhrase("Your %d step record is safe... for now. Come defend it.") { score, _ ->
            if (score > 0) "Your $score step record is safe... for now. Come defend it."
            else "The leaderboard is waiting. Come claim your spot."
        },
        NotificationPhrase("Memory fades fast. Keep it sharp with a quick %s round.") { _, grid ->
            "Memory fades fast. Keep it sharp with a quick $grid round."
        },
        NotificationPhrase("Did you forget the sequence already? Come back and prove you didn't.") { _, _ ->
            "Did you forget the sequence already? Come back and prove you didn't."
        },
        NotificationPhrase("24 hours is the exact amount of time it takes to forget a 10-step sequence. Test yourself.") { _, _ ->
            "24 hours is the exact amount of time it takes to forget a 10-step sequence. Test yourself."
        },
        NotificationPhrase("The pads are glowing, but you're not. Fix that.") { _, _ ->
            "The pads are glowing, but you're not. Fix that."
        }
    )

    // Tier 3: 48-72+ Hours (21 to 30)
    val TIER_3 = listOf(
        NotificationPhrase("48 hours?! Your brain is officially rusting. Get back in the game.") { _, _ ->
            "48 hours?! Your brain is officially rusting. Get back in the game."
        },
        NotificationPhrase("We miss you. And your %d step record misses you.") { score, _ ->
            if (score > 0) "We miss you. And your $score step record misses you."
            else "We miss you. The glowing pads are waiting."
        },
        NotificationPhrase("It's been days. The %s grid is getting lonely. Come play.") { _, grid ->
            "It's been days. The $grid grid is getting lonely. Come play."
        },
        NotificationPhrase("Your high score of %d is in serious danger of being forgotten. Save it!") { score, _ ->
            if (score > 0) "Your high score of $score is in serious danger of being forgotten. Save it!"
            else "Your memory skills are in serious danger of being forgotten. Save them!"
        },
        NotificationPhrase("3 days without Glowback? That's 3 days of lost brain training. Fix it now.") { _, _ ->
            "3 days without Glowback? That's 3 days of lost brain training. Fix it now."
        },
        NotificationPhrase("Are you scared of the %s grid? Come back and prove you aren't.") { _, grid ->
            "Are you scared of the $grid grid? Come back and prove you aren't."
        },
        NotificationPhrase("Your friends are sending challenges. Don't leave them hanging.") { _, _ ->
            "Your friends are sending challenges. Don't leave them hanging."
        },
        NotificationPhrase("72 hours. That's 4,320 minutes of not training your memory. Let's fix that.") { _, _ ->
            "72 hours. That's 4,320 minutes of not training your memory. Let's fix that."
        },
        NotificationPhrase("The neon lights are dimming without you. Come turn them back on.") { _, _ ->
            "The neon lights are dimming without you. Come turn them back on."
        },
        NotificationPhrase("Last chance to beat your %d record today before the day is gone!") { score, _ ->
            if (score > 0) "Last chance to beat your $score record today before the day is gone!"
            else "Last chance to play a quick round before the day is gone!"
        }
    )

    /**
     * Resolves the phrase based on hours elapsed since last opened:
     * - Tier 1: 0 to 24 hours
     * - Tier 2: 24 to 48 hours
     * - Tier 3: 48+ hours
     *
     * Anti-repetition: Ensures consecutive notifications in the same tier pick different phrases.
     */
    fun getPhrase(
        context: Context,
        deltaMillis: Long,
        bestScore: Int,
        gridLabel: String
    ): String {
        val hours = deltaMillis / (1000L * 60 * 60)
        val tierList = when {
            hours < 24 -> TIER_1
            hours < 48 -> TIER_2
            else -> TIER_3
        }

        val prefs = context.getSharedPreferences(PREFS_NOTIFICATION, Context.MODE_PRIVATE)
        val lastIdx = prefs.getInt(KEY_LAST_PHRASE_INDEX, -1)

        val candidateIndices = tierList.indices.filter { it != lastIdx }
        val selectedIdx = if (candidateIndices.isNotEmpty()) {
            candidateIndices[Random.nextInt(candidateIndices.size)]
        } else {
            Random.nextInt(tierList.size)
        }

        prefs.edit().putInt(KEY_LAST_PHRASE_INDEX, selectedIdx).apply()
        return tierList[selectedIdx].format(bestScore, gridLabel)
    }
}
