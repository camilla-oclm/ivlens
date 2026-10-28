# Discord: Plum Comet Labs

The studio's server (ID 1563856279385149452), set up by hand on 2026-10-25. One server for all the
studio's apps: a studio part on top and one category per app.

| Category | Channel | Type | Who can see and post |
|---|---|---|---|
| PLUM COMET LABS | `#welcome` | Text | Everyone reads, only you post |
| | `#rules` | Text (rules channel) | Everyone reads, only you post |
| | `#general` | Text | Everyone |
| | `#mod-updates` | Text, private | Only you |
| IV LENS | `#announcements` | Announcement | Everyone reads, only you post |
| | `#bug-reports` | Forum | Everyone |
| | `#ideas` | Text | Everyone |
| | `#testers` | Text, private | `Tester` role |

## Keep in mind

- `#mod-updates` is Discord's Community Updates channel, where it posts notices for moderators. Keep
  it private and apart from `#announcements`.
- `Tester` has no permissions of its own: giving someone the role only opens `#testers`.
- When the closed test starts, edit the pinned message in `#testers` to add the opt-in link.
- A new app gets its own category. Then move `#announcements` up to the studio part, or give the
  new app its own.
- The posted texts live in Discord. The last full copies, and the setup steps, are in this file's
  git history (commit 05d71b3).

## Later

- A bot could post release notes to `#announcements` and pass new bug posts on to the developer.
  It needs a bot token kept out of git.
- The in-app "Report a bug" (1.24) is a form: COPY & OPEN DISCORD copies the report and opens
  `https://discord.gg/mUcCaJqkeX`, a never-expiring invite made from #bug-reports (2026-10-27), so
  people land there, members or not. Don't let it expire or delete it.
- The Server Guide (a Community feature) is still empty.
- The server icon is still the letters "PCL". Give it one when the studio has a logo.
