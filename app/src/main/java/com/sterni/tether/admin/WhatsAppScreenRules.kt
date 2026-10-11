package com.sterni.tether.admin

/** Fallback for channel screens that reuse the normal conversation layout after search. */
internal object WhatsAppScreenRules {
    private val followActions = setOf("follow", "unfollow", "עקוב", "מעקב", "הפסק לעקוב", "הפסקת מעקב")
    private val followerCount = Regex("(?:.*\\d.*(?:followers|עוקבים)|(?:followers|עוקבים).*\\d.*)", RegexOption.IGNORE_CASE)

    fun isChannelSurface(labels: List<String>, actionLabels: List<String>): Boolean =
        actionLabels.any { it.trim().lowercase() in followActions } &&
            labels.any { followerCount.matches(it.trim()) }

    fun resourceMarker(resourceName: String, selected: Boolean): String? {
        val id = resourceName.substringAfter("id/").lowercase()
        // The navigation tab exists in ordinary chats too. Only its selected state is evidence.
        if (id.contains("tab") || id.contains("bottom_nav")) {
            return if (selected && (id.contains("updates") || id.contains("status") ||
                        id.contains("channel") || id.contains("newsletter"))) id else null
        }
        return id.takeIf {
            listOf("newsletter", "channel", "status_playback", "my_status", "status_list",
                "status_recycler", "status_row", "status_grid").any(it::contains)
        }
    }
}
