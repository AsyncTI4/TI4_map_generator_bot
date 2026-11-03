package ti4.helpers.thirtyk;

import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKCulturalExploreHandler;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKFrontierExploreHandler;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKHazardousExploreHandler;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKIndustrialExploreHandler;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKPlayAreaExploreHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class ThirtyKExplores {

    public static final Set<String> PLAY_AREA_CARDS = Set.of(
            "richhostages30k",
            "subjectivityequasion30k",
            "destructiveelements30k",
            "ancientbiologicalspecimens30k",
            "hyperalgorithms30k",
            "ancientexcavators30k",
            "dauntingweaponsschematics30k",
            "enhancedmobilitycore30k",
            "macguffianoutpostjmsc");

    public static void resolveExplore(
            GenericInteractionCreateEvent event,
            String drawnID,
            String cardID,
            Tile tile,
            String planetID,
            Player player,
            Game game) {
        if (!ThirtyKCards.inUse(game) || !ThirtyKCards.isExplore(cardID)) return;
        String baseId = ThirtyKCards.baseId(cardID);
        if (PLAY_AREA_CARDS.contains(baseId)) {
            ThirtyKPlayAreaExploreHandler.moveToPlayArea(event, game, player, drawnID);
            return;
        }
        switch (baseId) {
            case "sharedwealth30k" -> ThirtyKCulturalExploreHandler.resolveSharedWealth(event, game, player, tile);
            case "tourismbureau30k" ->
                ThirtyKCulturalExploreHandler.resolveTourismBureau(event, game, player, tile, planetID);
            case "directimport30k" -> ThirtyKCulturalExploreHandler.resolveDirectImport(event, game, player, tile);
            case "initialinvestmentsurplus30k" ->
                ThirtyKCulturalExploreHandler.resolveInitialInvestmentSurplus(event, game, player, planetID);
            case "crossspeciesmingling30k" ->
                ThirtyKCulturalExploreHandler.resolveCrossSpeciesMingling(event, game, player, tile, planetID);
            case "fauxaltruisticfestivals30k" ->
                ThirtyKCulturalExploreHandler.resolveFauxAltruisticFestivals(event, game, player);
            case "distractionefforts30k" ->
                ThirtyKFrontierExploreHandler.resolveDistractionEfforts(event, game, player);
            case "wastefulpirates30k" ->
                ThirtyKFrontierExploreHandler.resolveWastefulPirates(event, game, player, tile);
            case "faultyemergencysignal30k" ->
                ThirtyKFrontierExploreHandler.resolveFaultyEmergencySignal(event, game, player, tile);
            case "insatiablefuelsource30k" ->
                ThirtyKFrontierExploreHandler.resolveInsatiableFuelSource(event, game, player, tile);
            case "replicationarrays30k" -> ThirtyKFrontierExploreHandler.resolveReplicationArrays(event, game, player);
            case "interestingcontagion30k" ->
                ThirtyKFrontierExploreHandler.resolveInterestingContagion(event, game, player, tile);
            case "itsatrapjmsc" -> ThirtyKFrontierExploreHandler.resolveItsATrap(event, game, player, tile);
            case "systematicsympathiesjmsc" ->
                ThirtyKFrontierExploreHandler.resolveSystematicSympathies(event, game, player, tile);
            case "ponderingthenebulaejmsc" ->
                ThirtyKFrontierExploreHandler.resolvePonderingTheNebulae(event, game, player);
            case "werelostcaptainjmsc" ->
                ThirtyKFrontierExploreHandler.resolveWereLostCaptain(event, game, player, tile);
            case "unseendangerjmsc" -> ThirtyKFrontierExploreHandler.resolveUnseenDanger(event, game, player, tile);
            case "foregonediscoveriesjmsc" ->
                ThirtyKFrontierExploreHandler.resolveForegoneDiscoveries(event, game, player);
            case "emergentdefenses30k" ->
                ThirtyKHazardousExploreHandler.resolveEmergentDefenses(event, game, player, planetID);
            case "pilotrecoveryprogram30k" ->
                ThirtyKHazardousExploreHandler.resolvePilotRecovery(event, game, player, planetID);
            case "lostandfound30k" -> ThirtyKHazardousExploreHandler.resolveLostAndFound(event, game, player, planetID);
            case "imitationfactories30k" ->
                ThirtyKHazardousExploreHandler.resolveImitationFactories(event, game, player, drawnID, planetID);
            case "corrosivediscovery30k" ->
                ThirtyKHazardousExploreHandler.resolveCorrosiveDiscovery(event, game, player, tile, planetID);
            case "repurposedfacilities30k" ->
                ThirtyKIndustrialExploreHandler.resolveRepurposedFacilities(event, game, player, planetID);
            case "militaryproceduraldocuments30k" ->
                ThirtyKIndustrialExploreHandler.resolveMilitaryProceduralDocuments(event, game, player, planetID);
            case "foresightofindustry30k" ->
                ThirtyKIndustrialExploreHandler.resolveForesightOfIndustry(event, game, player, planetID);
            case "foresightofindustryb30k" ->
                ThirtyKIndustrialExploreHandler.resolveEstablishedSurveying(event, game, player, tile, planetID);
            default -> {}
        }
    }
}
