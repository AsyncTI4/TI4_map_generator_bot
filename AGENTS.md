# AGENTS.md

Reference notes for agents working on this Discord bot. Keep Discord's API limits in
mind whenever generating message content, embeds, buttons, select menus, or modals —
exceeding them causes the message send to fail (or, for button labels, silent truncation).

## Discord limits

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
