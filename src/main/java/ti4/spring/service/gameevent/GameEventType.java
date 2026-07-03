package ti4.spring.service.gameevent;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GameEventType {
    public static final String TACTICAL_ACTION = "TACTICAL_ACTION";
    public static final String TURN = "TURN";
    public static final String CARD_PLAY_ACTION_CARD = "CARD_PLAY_ACTION_CARD";
    public static final String CARD_PLAY_PROMISSORY_NOTE = "CARD_PLAY_PROMISSORY_NOTE";
    public static final String CARD_PLAY_AGENT = "CARD_PLAY_AGENT";
    public static final String CARD_PLAY_HERO = "CARD_PLAY_HERO";
    public static final String CARD_PLAY_RELIC = "CARD_PLAY_RELIC";
    public static final String CARD_PLAY_ABILITY = "CARD_PLAY_ABILITY";
    public static final String CARD_PLAY_BREAKTHROUGH = "CARD_PLAY_BREAKTHROUGH";
    public static final String CARD_PLAY_TECH_EXHAUST = "CARD_PLAY_TECH_EXHAUST";
    public static final String TECH_RESEARCHED = "TECH_RESEARCHED";
    public static final String SC_PLAYED = "SC_PLAYED";
    public static final String SC_PICKED = "SC_PICKED";
    public static final String OBJECTIVE_SCORED = "OBJECTIVE_SCORED";
    public static final String AGENDA_RESOLVED = "AGENDA_RESOLVED";
    public static final String TRANSACTION = "TRANSACTION";
    public static final String GAME_ENDED = "GAME_ENDED";
    // Raw record of a state-modifying slash command; carries the unparsed command string. Not deduped against
    // the typed events above, and prunes on undo like any other event.
    public static final String COMMAND = "COMMAND";
}
