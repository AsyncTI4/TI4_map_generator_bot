package ti4.service.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.map.Game;

@UtilityClass
public class GameModeService {

    public static List<String> getModes(Game game) {
        List<String> enabledModes = Stream.of(
                        Map.entry("Alliance", (Supplier<Boolean>) game::isAllianceMode),
                        Map.entry("Community", (Supplier<Boolean>) game::isCommunityMode),
                        Map.entry("TIGL", (Supplier<Boolean>) game::isCompetitiveTIGLGame),
                        Map.entry("Fog of War", (Supplier<Boolean>) game::isFowMode),
                        Map.entry("Light Fog", (Supplier<Boolean>) game::isLightFogMode),
                        Map.entry("Absol", (Supplier<Boolean>) game::isAbsolMode),
                        Map.entry("Discordant Stars", (Supplier<Boolean>) game::isDiscordantStarsMode),
                        Map.entry("Uncharted Space", (Supplier<Boolean>) game::isUnchartedSpaceStuff),
                        Map.entry("Milty Mod", (Supplier<Boolean>) game::isMiltyModMode),
                        Map.entry("Promises, Promises", (Supplier<Boolean>) game::isPromisesPromisesMode),
                        Map.entry("Flagshipping", (Supplier<Boolean>) game::isFlagshippingMode),
                        Map.entry("Red Tape", (Supplier<Boolean>) game::isRedTapeMode),
                        Map.entry("Omega Phase", (Supplier<Boolean>) game::isOmegaPhaseMode),
                        Map.entry("Homebrew", (Supplier<Boolean>) game::hasHomebrew),
                        Map.entry("Homebrew Strategy Cards", (Supplier<Boolean>) game::isHomebrewSCMode),
                        Map.entry("Extra Secret", (Supplier<Boolean>) game::isExtraSecretMode),
                        Map.entry("Voice of the Council", (Supplier<Boolean>) game::isVotcMode),
                        Map.entry("Base Game", (Supplier<Boolean>) game::isBaseGameMode),
                        Map.entry("Prophecy of Kings", (Supplier<Boolean>) game::isProphecyOfKings),
                        Map.entry("Thunder's Edge", (Supplier<Boolean>)
                                () -> game.isThundersEdge() && !game.isThundersEdgeDemo()),
                        Map.entry("Thunder's Edge Demo", (Supplier<Boolean>) game::isThundersEdgeDemo),
                        Map.entry("Twilight's Fall", (Supplier<Boolean>) game::isTwilightsFallMode),
                        Map.entry("Age of Exploration", (Supplier<Boolean>) game::isAgeOfExplorationMode),
                        Map.entry("Facilities", (Supplier<Boolean>) game::isFacilitiesMode),
                        Map.entry("Minor Factions", (Supplier<Boolean>) game::isMinorFactionsMode),
                        Map.entry("Total War", (Supplier<Boolean>) game::isTotalWarMode),
                        Map.entry("Dangerous Wilds", (Supplier<Boolean>) game::isDangerousWildsMode),
                        Map.entry("Civilized Society", (Supplier<Boolean>) game::isCivilizedSocietyMode),
                        Map.entry("Age of Fighters", (Supplier<Boolean>) game::isAgeOfFightersMode),
                        Map.entry("Mercenaries for Hire", (Supplier<Boolean>) game::isMercenariesForHireMode),
                        Map.entry("Advent of the Warsun", (Supplier<Boolean>) game::isAdventOfTheWarsunMode),
                        Map.entry("Cultural Exchange Program", (Supplier<Boolean>) game::isCulturalExchangeProgramMode),
                        Map.entry("Conventions of War Abandoned", (Supplier<Boolean>)
                                game::isConventionsOfWarAbandonedMode),
                        Map.entry("Rapid Mobilization", (Supplier<Boolean>) game::isRapidMobilizationMode),
                        Map.entry("Weird Wormholes", (Supplier<Boolean>) game::isWeirdWormholesMode),
                        Map.entry("Cosmic Phenomenae", (Supplier<Boolean>) game::isCosmicPhenomenaeMode),
                        Map.entry("Monument to the Ages", (Supplier<Boolean>) game::isMonumentToTheAgesMode),
                        Map.entry("Wild, Wild Galaxy", (Supplier<Boolean>) game::isWildWildGalaxyMode),
                        Map.entry("Zealous Orthodoxy", (Supplier<Boolean>) game::isZealousOrthodoxyMode),
                        Map.entry("Stellar Atomics", (Supplier<Boolean>) game::isStellarAtomicsMode),
                        Map.entry("No Support Swap", (Supplier<Boolean>) game::isNoSwapMode),
                        Map.entry("Age of Commerce", (Supplier<Boolean>) game::isAgeOfCommerceMode),
                        Map.entry("Hidden Agenda", (Supplier<Boolean>) game::isHiddenAgendaMode),
                        Map.entry("Ordinian", (Supplier<Boolean>) game::isOrdinianC1Mode),
                        Map.entry("Liberation", (Supplier<Boolean>) game::isLiberationC4Mode))
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));

        if (game.getSpinMode() != null && !"OFF".equalsIgnoreCase(game.getSpinMode())) {
            enabledModes.add("Spin Mode");
        }

        PriorityTrackHelper.PriorityTrackMode priorityTrackMode = game.getPriorityTrackMode();
        if (priorityTrackMode != null && priorityTrackMode != PriorityTrackHelper.PriorityTrackMode.NONE) {
            enabledModes.add(priorityTrackMode.name() + " Priority Track");
        }

        return enabledModes;
    }
}
