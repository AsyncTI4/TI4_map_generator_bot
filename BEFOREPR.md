Make sure there are good answers to the following before PR:

- [X] What if a buttony message gets deleted?
- [X] What if a player presses an undo button? or uses a big undo command?
- [X] EVERY interaction should be slashable.
- [X] ALL info should be dumpable via slashes.
- [X] The /milty/ diff should be trivial.
- [X] Invalid draft state: report a FIXABLE issue, pref using / commands to fix.
- [X] NO unhandled exceptions. Everything is cleanly reported to players, everything is fixable with commands.
- [X] Slash commands to see what's blocking a draft from ending, and to see what's blocking player setup.
- [X] Setup an entire draft via slash commands (e.g. reset draft manager, add orchestrator, add draftables, initialize each thing)
- Review fix and add suggested slash commands to error messages.

- [X] Required tiles are distributed breadth-first to player slices
- [X] Allowed tiles are correct before swapping
- [X] Error states are checked on a successful draft
  - One tile occurring multiple times
  - A position placed multiple times
  - A position NOT placed at all

- [ ] Try an 8p draft with ALL tile sources
- [ ] Try an 3p draft with ALL tile sources