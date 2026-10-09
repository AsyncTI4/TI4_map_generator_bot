package ti4.ai.seat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.game.Game;

@UtilityClass
public class AiGameSupport {

    private static final String DRAFT_PHASE = "miltydraft";
    private static final Set<String> PRE_GAME_PHASES = Set.of("", DRAFT_PHASE, "playerSetup", "strategy");

    @Nullable
    public static String refusalReason(Game game, String faction) {
        if (!AiSettings.isEnabled()) {
            return "AI players are disabled on this bot. A developer can enable them with "
                    + "`/developer setting setting_name:ai_players_enabled setting_value:true setting_type:bool`.";
        }
        if (!AiSettings.isSupportedFaction(faction)) {
            return "AI seats can play " + String.join(", ", AiSettings.SUPPORTED_FACTIONS) + ", not `" + faction + "`.";
        }
        if (isTaken(game, faction)) return "`" + faction + "` is already taken in this game.";
        if (game.getRealPlayers().size() >= AiSettings.MAX_SEATS) {
            return "This game already has " + AiSettings.MAX_SEATS + " seats.";
        }
        if (DRAFT_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) {
            return "AI seats can't be added while a draft is running. Add them once the draft has finished.";
        }
        if (hasStarted(game)) return "AI seats can only be added before the secret objectives are dealt.";
        List<String> unsupported = unsupportedModes(game);
        if (!unsupported.isEmpty()) {
            return "AI seats only support Base, Prophecy of Kings and Thunder's Edge games. Unsupported here: "
                    + String.join(", ", unsupported) + ".";
        }
        return null;
    }

    public static boolean isTaken(Game game, String faction) {
        return game.getPlayers().values().stream().anyMatch(player -> faction.equals(player.getFaction()));
    }

    @Nullable
    public static String firstFreeFaction(Game game) {
        return AiSettings.SUPPORTED_FACTIONS.stream()
                .filter(faction -> !isTaken(game, faction))
                .findFirst()
                .orElse(null);
    }

    public static boolean hasStarted(Game game) {
        return game.getRound() > 1
                || !PRE_GAME_PHASES.contains(StringUtils.defaultString(game.getPhaseOfGame()))
                || !game.getPlayedSCs().isEmpty()
                || !game.getStoredValue("revealedFlop" + game.getRound()).isEmpty()
                || game.getRealPlayers().stream()
                        .anyMatch(
                                player -> !player.getSCs().isEmpty() || player.getSo() > 0 || player.getSoScored() > 0);
    }

    public static List<String> unsupportedModes(Game game) {
        Map<String, Boolean> modes = new LinkedHashMap<>();
        modes.put("TIGL", game.isCompetitiveTIGLGame());
        modes.put("Fog of War", game.isFowMode() || game.isLightFogMode());
        modes.put("Community", game.isCommunityMode());
        modes.put("Alliance", game.isAllianceMode());
        modes.put("Twilight's Fall", game.isTwilightsFallMode());
        modes.put("Franken", game.isFrankenGame());
        modes.put("Homebrew", game.isHomebrew() && !AiSeats.hasAiSeat(game));
        modes.put("Discordant Stars", game.isDiscordantStarsMode());
        modes.put("Absol", game.isAbsolMode());
        modes.put("MiltyMod", game.isMiltyModMode());
        modes.put("Homebrew strategy cards", game.isHomebrewSCMode());
        modes.put("Omega Phase", game.isOmegaPhaseMode());
        modes.put("Extra secret", game.isExtraSecretMode());
        modes.put("Galactic events", hasGalacticEvent(game));
        modes.put("Scenario", game.isLiberationC4Mode() || game.isOrdinianC1Mode() || game.isErwansGambitMode());
        return modes.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static boolean hasGalacticEvent(Game game) {
        return game.isMinorFactionsMode()
                || game.isAgeOfExplorationMode()
                || game.isHiddenAgendaMode()
                || game.isTotalWarMode()
                || game.isDangerousWildsMode()
                || game.isStellarAtomicsMode()
                || game.isCivilizedSocietyMode()
                || game.isAgeOfFightersMode()
                || game.isAdventOfTheWarsunMode()
                || game.isCulturalExchangeProgramMode()
                || game.isConventionsOfWarAbandonedMode()
                || game.isRapidMobilizationMode()
                || game.isMonumentToTheAgesMode()
                || game.isMonumentsMode()
                || game.isWeirdWormholesMode()
                || game.isCosmicPhenomenaeMode()
                || game.isCosmicConvergenceMode()
                || game.isMuaatManiaMode()
                || game.isWildWildGalaxyMode()
                || game.isFeastOrFamineMode()
                || game.isZealousOrthodoxyMode()
                || game.isMercenariesForHireMode()
                || game.isAgeOfCommerceMode()
                || game.isFacilitiesMode()
                || game.isFlagshippingMode()
                || game.isPromisesPromisesMode()
                || game.isRedTapeMode()
                || game.isVotcMode()
                || game.isBlueReverieMode();
    }
}
