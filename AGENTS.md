# AGENTS.md

Reference notes for agents working on this Discord bot. Keep Discord's API limits in
mind whenever generating message content, embeds, buttons, select menus, or modals —
exceeding them causes the message send to fail (or, for button labels, silent truncation).

## Comments

**Do not write comments in production code.** Code under `src/main/java` should be
self-documenting: express intent through method and variable names, small focused
methods, and early returns rather than through prose explaining what the code does.

- If a block of code needs a comment to be understood, extract it into a
  well-named method instead.
- Do not add Javadoc, inline `//` notes, section banners, or "explain the change"
  comments to production code.
- Do not add comments to code you are only touching incidentally, and leave
  existing comments alone unless the code they describe is being removed.

The exception is test code (`src/test/java`), where comments explaining scenario
setup, non-obvious assertions, or the reason a case exists are welcome.

## Discord limits

### Slash commands
| Thing | Limit |
| --- | --- |
| Slash commands per app (per guild, and globally) | 100 |
| Subcommands per command, or per subcommand group | 25 |
| Subcommand groups per command | 25 |
| Options per command or subcommand | 25 |
| Choices per option | 25 |
| Autocomplete suggestions per response | 25 |
| Command name | 32 |
| Command description | 100 |
| Option name | 32 |
| Option description | 100 |

Subcommands, subcommand groups and top-level options all share the **same 25 slots** on a
command — in Discord's model they are one list, not three separate budgets.

### Messages
| Thing | Limit |
| --- | --- |
| Message content | 2000 characters (4000 with Nitro) |
| Embeds per message | 10 |
| Total embed characters (all embeds combined) | 6000 |
| Files / attachments per message | 10 |

### Embeds
| Thing | Limit |
| --- | --- |
| Title | 256 |
| Description | 4096 |
| Fields | 25 |
| Field name | 256 |
| Field value | 1024 |
| Footer text | 2048 |
| Author name | 256 |

### Buttons & action rows
| Thing | Limit |
| --- | --- |
| Buttons per action row | 5 |
| Action rows per message | 5 |
| Total buttons per message | 25 (5 rows × 5) |
| Button label | 80 characters |
| Button `custom_id` | 100 characters |

### Select menus
- A select menu takes up an **entire action row** (1 per row, so max 5 per message).

| Thing | Limit |
| --- | --- |
| Options per select menu | 25 |
| Select menu `custom_id` | 100 |
| Placeholder | 150 |
| Option label | 100 |
| Option value | 100 |
| Option description | 100 |

### Modals
| Thing | Limit |
| --- | --- |
| Title | 45 |
| Components (action rows) per modal | 5 |
| Text input label | 45 |
| Text input value (max length setting) | 4000 |
| Text input placeholder | 100 |
| Modal `custom_id` | 100 |

### Mixing note
A single action row can hold **either** up to 5 buttons **or** one select menu — not both.

## Codebase notes

- **Button labels are auto-truncated.** `Buttons.of(...)` in
  [Buttons.java](src/main/java/ti4/discord/interactions/buttons/Buttons.java) logs a
  warning and truncates any label longer than **80** chars to `77 chars + "..."`. Don't
  rely on this for intentional shortening — prefer concise labels, and keep the
  `custom_id` (the `id` argument) within the **100**-char limit yourself, since that is
  not auto-truncated and an over-length id will fail the send.
- **Parent commands are capped at 25 subcommands, and several sit exactly at the cap.**
  Adding a 26th makes JDA throw `Cannot have more than 25 subcommands for a command!` from
  [ParentCommand.register](src/main/java/ti4/discord/interactions/commands/ParentCommand.java)
  — client-side, while building the command, before any request reaches Discord. That throw
  lands inside the `try` in `JdaService.startBot`, which aborts registration of *every*
  command for that guild and skips `guilds.add(guild)`, so the guild never enters the
  whitelist. On 2026-09-20 that emptied the whitelist and the bot left every server it was
  in. `SlashCommandLimitsTest` now fails the build first. Before adding a subcommand, check
  the parent's current count — if it is full, put the command under a different parent
  rather than freeing a slot.
