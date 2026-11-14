package ti4.service.tournament;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;

@UtilityClass
public class TournamentSetupAccess {

    public static final String SETUP_GAME_KEY = "tournamentSetupGame";

    static final List<String> SETUP_COMPONENT_PREFIXES = List.of(
            "jmfA_",
            "jmfN_",
            "miltySetup",
            "startDraftSystem",
            "chooseExp_",
            "getHomebrewButtons",
            "setupHomebrew",
            "offerGameOptionButtons",
            "offerTEOptionButtons",
            "enableDaneMode_",
            "addMapString",
            "startTFGame",
            "setupBaseGameMode",
            "frankenSetup");

    public static boolean isSetupGame(Game game) {
        return game != null && StringUtils.isNotBlank(game.getStoredValue(SETUP_GAME_KEY));
    }

    public static boolean allowsSeatlessAdmin(Game game, Member member, String componentId) {
        return isSetupGame(game)
                && !game.isFowMode()
                && componentId != null
                && SETUP_COMPONENT_PREFIXES.stream().anyMatch(componentId::startsWith)
                && TournamentAccess.isTournamentAdmin(member);
    }
}
