package ti4.service.game;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.helpers.omega_phase.PriorityTrackHelper;

@UtilityClass
public class GameModeService {

    public static Set<String> getModes(Game game) {
        Set<String> enabledModes = Stream.<Map.Entry<String, Supplier<Boolean>>>of(
                        Map.entry("Normal", game::isNormalGame),
                        Map.entry("Veiled Heart", game::isVeiledHeartMode),
                        Map.entry("Action Card Deck 2", game::isAcd2),
                        Map.entry("Franken", game::isFrankenGame),
                        Map.entry("Alliance", game::isAllianceMode),
                        Map.entry("Community", (Supplier<Boolean>) game::isCommunityMode),
                        Map.entry("TIGL", game::isCompetitiveTIGLGame),
                        Map.entry("Fog of War", game::isFowMode),
                        Map.entry("Light Fog", game::isLightFogMode),
                        Map.entry("Absol", game::isAbsolMode),
                        Map.entry("Discordant Stars", game::isDiscordantStarsMode),
                        Map.entry("Blue Reverie", game::isBlueReverieMode),
                        Map.entry("Uncharted Space", game::isUnchartedSpaceStuff),
                        Map.entry("Milty Mod", game::isMiltyModMode),
                        Map.entry("Promises, Promises", game::isPromisesPromisesMode),
                        Map.entry("Flagshipping", game::isFlagshippingMode),
                        Map.entry("Red Tape", game::isRedTapeMode),
                        Map.entry("Omega Phase", game::isOmegaPhaseMode),
                        Map.entry("Homebrew", game::hasHomebrew),
                        Map.entry("Homebrew Strategy Cards", game::isHomebrewSCMode),
                        Map.entry("Extra Secret", game::isExtraSecretMode),
                        Map.entry("Voice of the Council", game::isVotcMode),
                        Map.entry("Base Game", game::isBaseGameMode),
                        Map.entry("Prophecy of Kings", game::isProphecyOfKings),
                        Map.entry("Thunder's Edge", () -> game.isThundersEdge() && !game.isThundersEdgeDemo()),
                        Map.entry("Thunder's Edge Demo", game::isThundersEdgeDemo),
                        Map.entry("Twilight's Fall", game::isTwilightsFallMode),
                        // isTwilightKart is deprecated. Once removed, just check isTkDestroyerCup
                        Map.entry(
                                "Twilight Kart: Destroyer Cup", () -> game.isTwilightKart() || game.isTkDestroyerCup()),
                        Map.entry("Twilight Kart: Nova Cup", game::isTkNovaCup),
                        Map.entry("Twilight Discordant Stars", game::isTwilightDS),
                        Map.entry("WhiteTF", game::isTfBr),
                        Map.entry("Age of Exploration", game::isAgeOfExplorationMode),
                        Map.entry("Facilities", game::isFacilitiesMode),
                        Map.entry("Minor Factions", game::isMinorFactionsMode),
                        Map.entry("Total War", game::isTotalWarMode),
                        Map.entry("Dangerous Wilds", game::isDangerousWildsMode),
                        Map.entry("Civilized Society", game::isCivilizedSocietyMode),
                        Map.entry("Age of Fighters", game::isAgeOfFightersMode),
                        Map.entry("Mercenaries for Hire", game::isMercenariesForHireMode),
                        Map.entry("Advent of the Warsun", game::isAdventOfTheWarsunMode),
                        Map.entry("Cultural Exchange Program", game::isCulturalExchangeProgramMode),
                        Map.entry("Conventions of War Abandoned", game::isConventionsOfWarAbandonedMode),
                        Map.entry("Rapid Mobilization", game::isRapidMobilizationMode),
                        Map.entry("Weird Wormholes", game::isWeirdWormholesMode),
                        Map.entry("Cosmic Phenomenae", game::isCosmicPhenomenaeMode),
                        Map.entry("Cosmic Convergence", game::isCosmicConvergenceMode),
                        Map.entry("Muaat Mania", game::isMuaatManiaMode),
                        Map.entry("Call of the Void", game::isCallOfTheVoidMode),
                        Map.entry("Monument to the Ages", game::isMonumentToTheAgesMode),
                        Map.entry("Monuments+", game::isMonumentsMode),
                        Map.entry("Wild, Wild Galaxy", game::isWildWildGalaxyMode),
                        Map.entry("Feast or Famine", game::isFeastOrFamineMode),
                        Map.entry("Zealous Orthodoxy", game::isZealousOrthodoxyMode),
                        Map.entry("Stellar Atomics", game::isStellarAtomicsMode),
                        Map.entry("No Support Swap", game::isNoSwapMode),
                        Map.entry("Age of Commerce", game::isAgeOfCommerceMode),
                        Map.entry("Hidden Agenda", game::isHiddenAgendaMode),
                        Map.entry("Ordinian", game::isOrdinianC1Mode),
                        Map.entry("Liberation", game::isLiberationC4Mode),
                        Map.entry("Erwan's Gambit", game::isErwansGambitMode),
                        Map.entry("No Fracture", game::isNoFractureMode))
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(HashSet::new));

        if (game.getSpinMode() != null && !"OFF".equalsIgnoreCase(game.getSpinMode())) {
            enabledModes.add("Spin Mode");
        }

        PriorityTrackHelper.PriorityTrackMode priorityTrackMode = game.getPriorityTrackMode();
        if (priorityTrackMode != null && priorityTrackMode != PriorityTrackHelper.PriorityTrackMode.NONE) {
            enabledModes.add("Priority Track (" + priorityTrackMode.name() + ")");
        }

        enabledModes.addAll(game.getTags());

        return enabledModes;
    }
}
