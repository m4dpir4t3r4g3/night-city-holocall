package com.nightcity.holoos.domain.logic

import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.HoloPreferences
import com.nightcity.holoos.domain.model.HoloToggle
import com.nightcity.holoos.domain.model.IncomingNotification
import javax.inject.Inject

/**
 * Decides which (if any) contextual cue an incoming notification should fire.
 * Pure Kotlin so it is covered by JVM unit tests.
 */
class NotificationCueClassifier @Inject constructor() {

    fun classify(n: IncomingNotification, prefs: HoloPreferences): AudioCue? {
        if (n.isOngoing || n.isGroupSummary) return null
        // Only react to notifications the system itself would alert for (IMPORTANCE_DEFAULT+).
        if (n.importance < IMPORTANCE_DEFAULT) return null
        if (prefs.isEnabled(HoloToggle.FINANCE_CUE) && isFinanceApp(n.packageName, prefs)) {
            return AudioCue.EDDIE_TRANSFER
        }
        if (prefs.isEnabled(HoloToggle.GIG_CUE) && isPriorityMail(n, prefs.gigKeywords)) {
            return AudioCue.NEW_GIG
        }
        return null
    }

    fun isFinanceApp(packageName: String, prefs: HoloPreferences): Boolean =
        packageName in prefs.financePackages ||
            (matchesFinanceHeuristic(packageName) && packageName !in prefs.blockedFinancePackages)

    fun matchesFinanceHeuristic(packageName: String): Boolean {
        if (packageName in KNOWN_FINANCE) return true
        val segments = packageName.lowercase().split('.', '_', '-')
        return segments.any { segment -> FINANCE_TOKENS.any { token -> segment.contains(token) } }
    }

    fun isPriorityMail(n: IncomingNotification, keywords: Set<String>): Boolean {
        val isMail = n.category == CATEGORY_EMAIL || n.packageName in KNOWN_MAIL
        if (!isMail) return false
        if (keywords.isEmpty()) return true
        val haystack = listOfNotNull(n.title, n.text).joinToString(" ").lowercase()
        return keywords.any { it.isNotBlank() && haystack.contains(it.trim().lowercase()) }
    }

    companion object {
        /** Mirrors NotificationManager.IMPORTANCE_DEFAULT without the Android dependency. */
        const val IMPORTANCE_DEFAULT = 3

        /** Mirrors Notification.CATEGORY_EMAIL. */
        const val CATEGORY_EMAIL = "email"

        val FINANCE_TOKENS = listOf(
            "bank", "pay", "wallet", "finance", "fintech", "cash", "money", "venmo",
            "revolut", "monzo", "zelle", "coinbase", "crypto", "credit", "transferwise",
        )

        val KNOWN_FINANCE = setOf(
            "com.google.android.apps.walletnfcrel",
            "com.paypal.android.p2pmobile",
            "com.venmo",
            "com.squareup.cash",
            "com.revolut.revolut",
            "com.chase.sig.android",
            "com.wf.wellsfargomobile",
            "com.infonow.bofa",
            "com.konylabs.capitalone",
            "com.citi.citimobile",
            "com.usaa.mobile.android.usaa",
            "com.transferwise.android",
            "co.uk.getmondo",
            "com.starlingbank.android",
            "com.coinbase.android",
            "com.americanexpress.android.acctsvcs.us",
            "com.samsung.android.spay",
            "com.robinhood.android",
            "de.number26.android",
            "com.discoverfinancial.mobile",
            "com.ally.MobileBanking",
        )

        val KNOWN_MAIL = setOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "ch.protonmail.android",
            "com.fsck.k9",
            "com.samsung.android.email.provider",
            "me.bluemail.mail",
            "com.readdle.spark",
        )
    }
}
