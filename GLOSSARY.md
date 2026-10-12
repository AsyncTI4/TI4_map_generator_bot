# Async TI4

Asynchronous Twilight Imperium 4 games run through Discord: the games, the seats users hold in them, and the results credited from them.

## Language

### Game lifecycle

**Game**:
One asynchronous Twilight Imperium match run by the bot, known by its name.
_Avoid_: map, match

**Ongoing**:
A game that hasn't ended and in which no one has reached the victory point goal.
_Avoid_: active, in progress, live

**Pending end**:
A game that hasn't ended although someone has reached the victory point goal; it waits for someone to end it.
_Avoid_: won, finished

**Ended**:
A game brought to a close by a player, a bothelper, or automatically after inactivity. An ended game can be reopened, which makes it Ongoing or Pending end again.
_Avoid_: finished, over

**Completed**:
An ended game with a winner.
_Avoid_: finished, won game

**Aborted**:
An ended game without a winner.
_Avoid_: abandoned, dead, cancelled

**Inactive game**:
A game that hasn't ended and has had no changes for 60 days; the bot ends it automatically.
_Avoid_: stale, dead, old

### Seats

**Seat**:
A user's place in one game, whether or not they play a faction. A user holds at most one seat per game.
_Avoid_: slot, player record

**Seat holder**:
The user currently sitting in a seat.
_Avoid_: owner

**Stats-tracked user**:
The user a seat's results are credited to: the seat holder, unless a replacement left the credit with the replaced user.
_Avoid_: original player, credited player

**Replacement**:
Handing an occupied seat to a different user partway through a game.
_Avoid_: substitution, swap

**Playing seat**:
A seat that holds a faction and colour and hasn't been eliminated, including one in NPC mode.
_Avoid_: real player

**NPC mode**:
A choice a seat holder makes so the bot stops pinging them and plays routine decisions for them; the seat and its game remain theirs for every other purpose.
_Avoid_: bot seat, AI player, departed player

**Eliminated seat**:
A seat whose faction has been eliminated from the game.

**Neutral seat**:
A seat holding a faction that no user plays, such as the neutral faction.
_Avoid_: dummy

**Unassigned seat**:
A seat with no faction: someone who hasn't picked one yet, a spectator, or the fog-of-war game master.
_Avoid_: spectator, unless it is known to be one

**Participant**:
A user holding a Playing or Eliminated seat in a game.
_Avoid_: real player, member

### Limits

**Drop**:
A game a user left before it ended, by being replaced or by leaving the server.
_Avoid_: quit, abandonment

**Join limit**:
How many games that haven't ended a user may take part in at once; it grows with completed games and shrinks with drops.
_Avoid_: game cap

**Personal game limit**:
A stricter cap a bothelper sets for one user, applied on top of the join limit.

**Bothelper**:
A community staff member with access to the bot's maintenance commands.
_Avoid_: mod, admin
