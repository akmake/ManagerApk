package com.sterni.tether.admin

/** A blocked channel remains blocked through search/keyboard transitions and app switches. */
internal class WhatsAppAccessGuard {
    private val pendingPackages = mutableSetOf<String>()
    fun block(packageName: String): Boolean = pendingPackages.add(packageName)
    fun isPending(packageName: String): Boolean = packageName in pendingPackages
    fun reachedChats(packageName: String) { pendingPackages.remove(packageName) }
    fun clear(packageName: String) { pendingPackages.remove(packageName) }
}
