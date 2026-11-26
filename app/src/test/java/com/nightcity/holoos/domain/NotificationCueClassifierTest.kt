package com.nightcity.holoos.domain

import com.nightcity.holoos.domain.logic.NotificationCueClassifier
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.HoloPreferences
import com.nightcity.holoos.domain.model.HoloToggle
import com.nightcity.holoos.domain.model.IncomingNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCueClassifierTest {
    private val classifier = NotificationCueClassifier()
    private val prefs = HoloPreferences()

    private fun notif(
        pkg: String,
        category: String? = null,
        importance: Int = 3,
        ongoing: Boolean = false,
        summary: Boolean = false,
        title: String? = null,
        text: String? = null,
    ) = IncomingNotification("k", pkg, category, ongoing, summary, false, importance, title, text)

    @Test fun knownBankFiresEddies() =
        assertEquals(AudioCue.EDDIE_TRANSFER, classifier.classify(notif("com.chase.sig.android"), prefs))

    @Test fun heuristicMatchesBankingTokens() {
        assertTrue(classifier.matchesFinanceHeuristic("com.examplebank.mobile"))
        assertTrue(classifier.matchesFinanceHeuristic("io.acme.pay"))
        assertFalse(classifier.matchesFinanceHeuristic("com.spotify.music"))
        assertFalse(classifier.matchesFinanceHeuristic("com.android.chrome"))
    }

    @Test fun blockedHeuristicAppIsIgnored() {
        val p = prefs.copy(blockedFinancePackages = setOf("io.acme.pay"))
        assertNull(classifier.classify(notif("io.acme.pay"), p))
    }

    @Test fun explicitPackageFires() {
        val p = prefs.copy(financePackages = setOf("com.example.coffee"))
        assertEquals(AudioCue.EDDIE_TRANSFER, classifier.classify(notif("com.example.coffee"), p))
    }

    @Test fun silentOngoingAndSummariesAreIgnored() {
        assertNull(classifier.classify(notif("com.venmo", importance = 2), prefs))
        assertNull(classifier.classify(notif("com.venmo", ongoing = true), prefs))
        assertNull(classifier.classify(notif("com.venmo", summary = true), prefs))
    }

    @Test fun emailFiresGigUnlessKeywordsMiss() {
        assertEquals(AudioCue.NEW_GIG, classifier.classify(notif("com.google.android.gm"), prefs))
        assertEquals(AudioCue.NEW_GIG, classifier.classify(notif("org.mail", category = "email"), prefs))
        val p = prefs.copy(gigKeywords = setOf("offer"))
        assertNull(classifier.classify(notif("com.google.android.gm", title = "Newsletter"), p))
        assertEquals(AudioCue.NEW_GIG, classifier.classify(notif("com.google.android.gm", text = "Your OFFER letter"), p))
    }

    @Test fun togglesDisableCues() {
        val p = prefs.copy(toggles = mapOf(HoloToggle.FINANCE_CUE to false, HoloToggle.GIG_CUE to false))
        assertNull(classifier.classify(notif("com.venmo"), p))
        assertNull(classifier.classify(notif("com.google.android.gm"), p))
    }
}
