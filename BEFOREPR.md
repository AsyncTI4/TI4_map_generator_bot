Make sure there are good answers to the following before PR:

- [X] What if a buttony message gets deleted?
- [X] What if a player presses an undo button? or uses a big undo command?
- [X] EVERY interaction should be slashable.
- [X] ALL info should be dumpable via slashes.
- The /milty/ diff should be trivial.
- [X] Invalid draft state: report a FIXABLE issue, pref using / commands to fix.
- [X] NO unhandled exceptions. Everything is cleanly reported to players, everything is fixable with commands.
- [X] Slash commands to see what's blocking a draft from ending, and to see what's blocking player setup.
- Slash command to report the button ids of all buttons in reverse posting order, for the last X messages. Specific to the thread it's used in.
- [X] Setup an entire draft via slash commands (e.g. reset draft manager, add orchestrator, add draftables, initialize each thing)
- Draft setup button with a premade map. Instead of Slice+Faction+SpeakerOrder, you draft Seat+Faction+SpeakerOrder on the premade map.
- Review fix and add suggested slash commands to error messages.