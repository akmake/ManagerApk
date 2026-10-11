package com.sterni.tether.admin

import org.junit.Assert.*
import org.junit.Test

class WhatsAppScreenRulesTest {
    @Test fun searchOpenedChannelWithGenericChatLayoutIsRecognized() {
        assertTrue(WhatsAppScreenRules.isChannelSurface(listOf("News", "12,345 followers"), listOf("Follow")))
        assertTrue(WhatsAppScreenRules.isChannelSurface(listOf("חדשות", "123 עוקבים"), listOf("הפסק לעקוב")))
    }

    @Test fun ordinaryChatMentioningChannelOrFollowIsNotBlocked() {
        assertFalse(WhatsAppScreenRules.isChannelSurface(listOf("Follow", "123 followers"), emptyList()))
        assertFalse(WhatsAppScreenRules.isChannelSurface(listOf("Follow", "Messages"), listOf("Follow")))
    }

    @Test fun updatesNavigationDoesNotBlockChatsUnlessSelected() {
        assertNull(WhatsAppScreenRules.resourceMarker("com.whatsapp:id/channel_tab", false))
        assertNotNull(WhatsAppScreenRules.resourceMarker("com.whatsapp:id/channel_tab", true))
        assertNotNull(WhatsAppScreenRules.resourceMarker("com.whatsapp:id/newsletter_message", false))
        assertNotNull(WhatsAppScreenRules.resourceMarker("com.whatsapp:id/status_playback", false))
    }
}
