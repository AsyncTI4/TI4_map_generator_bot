# Misprints on the printed components

The bot copies the printed components word for word, misprints included. This file lists the known misprints so nobody "fixes" them in the bot by mistake, and so they can be corrected in one go if the bot ever switches to the intended text.

The source of truth is the official component images on ti4ultimate.com (faction sheet backs: `resources/images/shared/factionimages/<Faction>_FactionSheetBack.webp`). The site's typed text is not a reliable source.

Bot data: `src/main/resources/data/factions/` (`base.json`, `pok.json`, `keleres.json`, `te_factions.json`).

## Faction sheet backs

### Clear misprints

| Faction | Bot alias | File | Field | Printed text | Intended |
| --- | --- | --- | --- | --- | --- |
| Ghosts of Creuss | `ghost` | base.json | lore | the Mentak frigate Entropic undertook its fifth journey **though** Shaleri space | through |
| L1Z1X Mindnet | `l1z1x` | base.json | lore | destruction of any record that could **possible** identify his secret destination | possibly |
| Embers of Muaat | `muaat` | base.json | lore | the spread of the virus to the **Jol Nar** home worlds | Jol-Nar |
| Winnu | `winnu` | base.json | lore | the supreme Winnu leader **Muab** Di Faruuq traveled to Mecatol | Muad (the spelling used everywhere else) |
| Winnu | `winnu` | base.json | quotes | Traditionalists to the core, **The** Winnu decree that | the (lower case, mid-sentence) |
| Naalu Collective | `naalu` | base.json | lore | With her **Nefishh**, a rare crystal radium device | Neffish (the spelling in the sheet's own quote) |
| Nekro Virus | `nekro` | base.json | lore | While the narrative of Ibna’s early struggles **are** common lore | is |
| Titans of Ul | `titans` | pok.json | lore | an elder can be the size of a **habitant** tower | habitat |
| Empyrean | `empyrean` | pok.json | lore | the Twilight Wars of **a millennia** ago | a millennium |
| Ral Nel Consortium | `ralnel` | te_factions.json | lore | This attitude only goes so far in **reigning** in internecine conflict | reining |
| Ral Nel Consortium | `ralnel` | te_factions.json | lore | the Deepwrought Scholarate appeared to be **motived** not by | motivated |
| Ral Nel Consortium | `ralnel` | te_factions.json | lore | the goal of the Consortium’s zio is **simple;** to guarantee | simple: (colon) |

### Possible misprints

| Faction | Bot alias | File | Field | Printed text | Note |
| --- | --- | --- | --- | --- | --- |
| Barony of Letnev | `letnev` | base.json | lore | the sharp **Saimoc—strong** crystals used in mining | The em dash reads oddly; a hyphen ("Saimoc-strong") or no dash ("Saimoc strong") may be meant. |

### Not misprints

These look wrong but match the print and are fine:

- **L1Z1X:** the print sets the name in small capitals, which some transcriptions read as "L1z1x". The bot's "L1Z1X" is right.
- **Obsidian:** the sheet back is printed in an alien glyph script, so there is no English lore to copy.
- **Other wording:** "phosphorous chemistry" (Nekro), "near 700 years" (Nekro), "Mecatol, which would become the Rex" (Winnu), "Maaluk" (Arborec and Naalu), "the various Creuss vessels" (Deepwrought) and "the Keleres’s founding" (Keleres).

## Action cards

Bot data: `src/main/resources/data/action_cards/action_cards.json`.

| Card | Bot alias | Field | Printed text | Intended |
| --- | --- | --- | --- | --- |
| Blitz | `blitz` | text | Each of your non-fighter ships in the active system that **do** not have BOMBARDMENT **gain** BOMBARDMENT 6 | does … gains ("each" is singular) |

## Corrected in the bot

These are missing from the print. Unlike the misprints above, the bot carries the corrected text.

| Card | Bot alias | File | Field | Printed text | Bot text |
| --- | --- | --- | --- | --- | --- |
| Arise (Twilight's Fall edict) | `tf-arise` | agendas/twilights_fall.json | text1 | … or place 1 fighter in each system that contains your ships. | … or place 1 fighter **from your reinforcements** in each system that contains your ships. |

## Not yet checked against images

- Each faction's first, attributed quote, for example "… —Dirzuga Rophal". These quotes aren't printed on the faction sheet backs.
- The Letnev quote signed "Baron Unlenn".
- Leader flavour quotes, which aren't on the leader card images.
- Agendas, relics, explores, technologies and other cards, and the action cards that haven't been compared with an image yet. So far only Blitz, Fighter Conscription and Rally have been checked.
