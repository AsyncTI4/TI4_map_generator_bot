Notes on how the bot is orchestrated

## Status / End of Action

EndTurnService - handles end of turns in which a player passed, including the last player of the round. Therefore handles kickoff to status phase ("score public" stuff)

PassService - Handles stuff that happens during Passing even if the Action Phase continues

ReactionService:

- addReaction: Put a player's emoji on a message, and if all players have their emojis there then use checkForAllReactions below.

UnfiledButtonHandlers:

- checkForAllReactions: Looks at a message to see if all players have done something, and if so progresses to the "next step" handler
- respondAllPlayersReacted: Looks at the triggering button to determine what kind of thing people were reacting to. Then progresses the game for whatever that thing was. For example, if it was a call to score Public Objectives at the start of the Status Phase, then it creates the "Reveal Public Objective" button(s) using button prefix `reveal_stage_`.
- `reveal_stage_`: Typically uses RevealPublicObjectiveService after checking for blocking. This will internally invoke status phase cleanup. Then after RevealPublicObjectiveService finishes, start status homework.

RevealPublicObjectiveService:

- Reveals and pins the next objective
- Checks if anyone has a strategy card to determine if Status Cleanup should happen. If so, invokes StatusCleanupService.runStatusCleanup.

StatusCleanupService.runStatusCleanup:

- Basically clears a ton of temp game/player values
- Refreshes all cards and things
- Handles "End Status Phase" effects, except some stuff in EndTurnService for some reason.

StartPhaseService:

- Used by /game start_phase
- Can kick off a specific async phase, such as status scoring, status cleanup ("homework"), strategy, action, agenda
- startStatusHomework: Create buttons for players to do non-objective status activity, such as draw ACs and redistribute/gain CCs.

## AGENDA

"flip_agenda" button handler:

- Generally used to start the agenda phase or progress it
- Note on agenda phase: Maw of Worlds and Ancient Burial Sites are treated as Status Phase events
- Invokes AgendaHelper.revealAgenda

AgendaHelper.revealAgenda:

- Reveals the next agenda
- Sets up targets
- Counts agendas for the round, to recognize the first as the start of the agenda phase

UnfiledButtonHandler:

- "proceed_to_strategy" button: Refresh planets only, and proceed to strategy phase

StatusCleanupService.runStatusCleanup

- Clears the "agendaCount" game value to effectively allow the next agenda phase to start

## STRATEGY

Helper.java has getRemainingSCButtons(game, player) which creates the picker buttons for that player based on remaining unpicked SCs

PickStrategyCardButtonHandler:

- "scPick\_"
- Also "queueScPick\_" to look at...

PlayerStatsService:

- secondHalfOfPickSC: Validates and then adds SC to Player. Handles on-pick things, like gaining TGs.

PickStrategyCardService:

- secondHalfOfSCPick: Handles whatever would happen after a successful SC card pick (ping next player, handle queue of picks, or kick off action phase)
- checkForQueuedSCPick: Basically checks to see if it should click the button for the player
