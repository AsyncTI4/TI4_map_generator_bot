package ti4.service.fow.setup;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Persisted (via {@link ti4.game.helper.StoredValueHelper}) progress of the FoW GM setup wizard for a single game. */
@Data
@NoArgsConstructor
public class FowSetupWizardState {

    private FowSetupStep step = FowSetupStep.GAME_TYPE;
    private Long panelMessageId;
    private Set<FowSetupStep> infoThreadsPosted = EnumSet.noneOf(FowSetupStep.class);
    private boolean introShown = false;

    private String gameType;
    private String scenarioNote;

    // FACTIONS step: factions chosen by the GM but not yet finalized with a home position
    private Map<String, String> pendingFactionByUserId = new LinkedHashMap<>();

    // FACTIONS step: mini faction-deal - userID -> the factions offered to them, awaiting their pick
    private Map<String, List<String>> dealtFactionChoices = new LinkedHashMap<>();

    // FACTIONS step: position typed for a player, awaiting a color pick before setupPlayer is called
    private Map<String, String> pendingPositionByUserId = new LinkedHashMap<>();

    // TABLE_ORDER step: manual seat-order picks, in chosen order
    private List<String> manualOrderPicks = new ArrayList<>();

    // Step 3b: dice-based table order
    private Integer diceCount;
    private Integer diceSides;
    private Long diceRollMessageId;
    /** userID -> roll total. Insertion order doubles as click order for tiebreaks. */
    private Map<String, Integer> diceRolls = new LinkedHashMap<>();

    /**
     * Step "Game Options": which toggles the wizard's own submenu currently shows as ON, keyed by
     * {@code HomebrewService.Homebrew.name()} (or "TIGL"). Only options with no single unambiguous
     * backing boolean need this (e.g. the two Absol toggles share one {@code Game.absolMode} flag) -
     * most options just read/write the real game flag directly instead of duplicating it here.
     */
    private Set<String> enabledGameOptions = new LinkedHashSet<>();
}
