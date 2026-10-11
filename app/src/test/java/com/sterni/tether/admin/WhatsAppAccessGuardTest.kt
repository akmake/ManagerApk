package com.sterni.tether.admin

import org.junit.Assert.*
import org.junit.Test

class WhatsAppAccessGuardTest {
    @Test fun searchAndAppSwitchDoNotForgetBlockedChannel() {
        val guard = WhatsAppAccessGuard()
        guard.block("com.whatsapp")
        assertTrue(guard.isPending("com.whatsapp"))
        assertFalse(guard.isPending("com.other.app"))
        assertTrue(guard.isPending("com.whatsapp"))
        assertFalse(guard.block("com.whatsapp"))
    }
    @Test fun onlyReturningToChatsReleasesThatPackage() {
        val guard = WhatsAppAccessGuard()
        guard.block("com.whatsapp")
        guard.block("com.whatsapp.w4b")
        guard.reachedChats("com.whatsapp")
        assertFalse(guard.isPending("com.whatsapp"))
        assertTrue(guard.isPending("com.whatsapp.w4b"))
    }
    @Test fun disablingShieldReleasesPendingEnforcement() {
        val guard = WhatsAppAccessGuard()
        guard.block("com.whatsapp")
        guard.clear("com.whatsapp")
        assertFalse(guard.isPending("com.whatsapp"))
    }
}
