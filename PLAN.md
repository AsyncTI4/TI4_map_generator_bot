For this branch, we're going to integrate the new draft system into the existing settings framework.

That should let us do the following:
- For a Nucleus draft, create the DraftSystemSettings object instead of the MiltySettings
- Therein, a Draftable and Orchestrator driven collection of menus is created
  - For our purposes, maybe fixed elements for now
- General settings
  - Number of players / which players are playing
  - Component sources. e.g. DiscoStars, UnchartedSpace, Eronous, etc.
  - In-game homebrew. Relic decks, AC decks, Priority Track, etc etc.
  - Alliance?
  - TIGL?
  - Game VP-related rules: VPs to win, Stage 1 count, Stage 2 count, Secrets allowed.
- Special settings
  - SliceDraftable: will be a flag for nucleus vs non-nucleus templates
  - Premade map WON'T use nucleus anything, or any template ref
  - MantisTileDraftable: The tile placement order is well-defined, and I'm not sure how Nucleus tiles fit into that. So leave it out.
- Draftables and Orchestrators will be extended so that they can:
  - Provide a settings object with default settings for the player count
  - Process a settings object to set themselves up (e.g. generate factions, slices, so on)
- As part of this, figure out default values for most nucleus controls based on player count
