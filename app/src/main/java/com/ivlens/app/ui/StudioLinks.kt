package com.ivlens.app.ui

/**
 * Plum Comet Labs' links, shown at the bottom of Settings (1.21).
 * - The Discord invite never expires (checked 2026-10-25). A new one mustn't either (Discord ›
 *   Invite People › Edit invite link › Expire after: Never), or the button stops working for
 *   everyone with this version.
 * - The website's static page isn't up yet (2026-10-25); it will be before the Play release.
 */
object StudioLinks {
    const val DISCORD = "https://discord.gg/VvvbhuxmfF"
    const val WEBSITE = "https://plumcometlabs.com"

    /**
     * Where "Report a bug" sends people (1.24): an invite made from the #bug-reports forum, so it
     * lands there, for members and newcomers alike (a channel link only opens for members). It
     * never expires (checked 2026-10-27); a new one mustn't either.
     */
    const val BUG_REPORTS = "https://discord.gg/mUcCaJqkeX"
}
