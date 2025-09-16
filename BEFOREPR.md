Make sure there are good answers to the following before PR:

- What if a buttony message gets deleted?
- What if a player presses an undo button? or uses a big undo command?
- EVERY interaction should be slashable.
- ALL info should be dumpable via slashes.
- The /milty/ diff should be trivial.
- Invalid draft state: report a FIXABLE issue, pref using / commands to fix.
- A "/draft retry" command to re-play the most recent draft action, in the case that something goes wrong and needs to be manually fixed.
- NO unhandled exceptions. Everything is cleanly reported to players, everything is fixable with commands.
- Slash commands to see what's blocking a draft from ending, and to see what's blocking player setup.
- Slash command to report the button ids of all buttons in reverse posting order, for the last X messages. Specific to the thread it's used in.
- Setup an entire draft via slash commands (e.g. reset draft manager, add orchestrator, add draftables, initialize each thing)