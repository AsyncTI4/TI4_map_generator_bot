package ti4.discord.interactions.buttons.handlers.relics.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.explore.BlueReverieArtifactService;
import ti4.service.planet.AddPlanetService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class BlueReverieArtifactRelicHandler {
    private static final String CULTURAL_ARTIFACT = "culturalartifact";
    private static final String HAZARDOUS_ARTIFACT = "hazardousartifact";
    private static final String INDUSTRIAL_ARTIFACT = "industrialartifact";
    private static final String UNKNOWN_ARTIFACT = "unknownartifact";
    private static final String CULTURAL = "resolveCulturalArtifact";
    private static final String HAZARDOUS = "resolveHazardousArtifact_";
    private static final String INDUSTRIAL_COMMODITIES = "resolveIndustrialArtifactCommodities";
    private static final String INDUSTRIAL_RESEARCH_TG = "resolveIndustrialArtifactResearchTg_";
    private static final String INDUSTRIAL_RESEARCH_COMMODITIES = "resolveIndustrialArtifactResearchCommodities_";
    private static final String UNKNOWN = "resolveUnknownArtifact_";
    private static final String HAZARDOUS_PLANET = "resolveHazardousArtifactPlanet_";
    private static final String UNKNOWN_MOVE_VALUE_KEY = "unknownArtifactMoveValueOne";

    public static Button getCulturalArtifactButton(Player player) {
        return player.hasRelic(CULTURAL_ARTIFACT)
                ? Buttons.red("queueWhen_relic_" + CULTURAL_ARTIFACT, "Cultural Artifact")
                : null;
    }

    @ButtonHandler(CULTURAL)
    public static void resolveCulturalArtifact(ButtonInteractionEvent event, Game game, Player player) {
        if (!BlueReverieArtifactService.discardArtifact(game, player, CULTURAL_ARTIFACT)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                player.getRepresentationNoPing()
                        + " discarded _Cultural Artifact_. The revealed agenda is discarded and a new agenda is being revealed.");
        AgendaHelper.resolveVeto(event, game);
    }

    public static void addHazardousArtifactCombatButton(List<Button> buttons, Player player, String tilePosition) {
        if (!player.hasRelic(HAZARDOUS_ARTIFACT)) {
            return;
        }
        buttons.add(
                Buttons.red(player.factionButtonChecker() + HAZARDOUS + tilePosition, "Discard Hazardous Artifact"));
    }

    @ButtonHandler(HAZARDOUS)
    public static void resolveHazardousArtifact(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        var tile = game.getTileByPosition(buttonID.substring(HAZARDOUS.length()));
        if (tile == null || !player.hasRelic(HAZARDOUS_ARTIFACT)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Planet> planets = new ArrayList<>(tile.getPlanetUnitHolders());
        if (planets.size() == 1) {
            resolveHazardousArtifactOnPlanet(
                    event, game, player, planets.getFirst().getName());
            return;
        }
        List<Button> buttons = planets.stream()
                .map(planet -> Buttons.red(
                        player.factionButtonChecker() + HAZARDOUS_PLANET + planet.getName(),
                        "Use Hazardous Artifact on " + Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose a planet for _Hazardous Artifact_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(HAZARDOUS_PLANET)
    public static void resolveHazardousArtifactPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveHazardousArtifactOnPlanet(event, game, player, buttonID.substring(HAZARDOUS_PLANET.length()));
    }

    private static void resolveHazardousArtifactOnPlanet(
            ButtonInteractionEvent event, Game game, Player player, String planetName) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null || !BlueReverieArtifactService.discardArtifact(game, player, HAZARDOUS_ARTIFACT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String previousCoexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet).stream()
                    .filter(otherPlayer -> otherPlayer != player)
                    .findFirst()
                    .ifPresent(otherPlayer -> {
                        if (player.getPlanets().contains(planetName)) {
                            AddPlanetService.addPlanet(otherPlayer, planetName, game);
                        } else if (otherPlayer.getPlanets().contains(planetName)) {
                            AddPlanetService.addPlanet(player, planetName, game);
                        }
                    });
        } finally {
            if (previousCoexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", previousCoexistenceFlag);
            }
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " discarded _Hazardous Artifact_; units are now coexisting on "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerIndustrialArtifact(Game game, Player player, UnitModel unit) {
        if (!player.hasRelic(INDUSTRIAL_ARTIFACT)) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + INDUSTRIAL_COMMODITIES, "Discard Artifact To Gain 3 Commodities"));
        UnitModel unitUpgrade = unit.getUpgradesToUnitId().map(Mapper::getUnit).orElse(unit);
        String techId = unitUpgrade.getRequiredTechId().orElse(null);
        if (techId != null && Mapper.getTech(techId) != null && !player.hasTech(techId)) {
            if (player.getTg() >= 2) {
                buttons.add(Buttons.blue(
                        player.factionButtonChecker() + INDUSTRIAL_RESEARCH_TG + techId,
                        "Spend 2 Trade Goods To Research "
                                + Mapper.getTech(techId).getName()));
            }
            if (player.getCommodities() >= 2) {
                buttons.add(Buttons.blue(
                        player.factionButtonChecker() + INDUSTRIAL_RESEARCH_COMMODITIES + techId,
                        "Spend 2 Commodities To Research "
                                + Mapper.getTech(techId).getName()));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", you may resolve _Industrial Artifact_.",
                buttons);
    }

    @ButtonHandler(INDUSTRIAL_COMMODITIES)
    public static void resolveIndustrialArtifactCommodities(ButtonInteractionEvent event, Game game, Player player) {
        if (!BlueReverieArtifactService.discardArtifact(game, player, INDUSTRIAL_ARTIFACT)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        int before = player.getCommodities();
        player.gainCommodities(3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " discarded _Industrial Artifact_ and gained "
                        + (player.getCommodities() - before) + " commodities.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(INDUSTRIAL_RESEARCH_TG)
    public static void resolveIndustrialArtifactResearchWithTradeGoods(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveIndustrialArtifactResearch(
                event, game, player, buttonID.substring(INDUSTRIAL_RESEARCH_TG.length()), true);
    }

    @ButtonHandler(INDUSTRIAL_RESEARCH_COMMODITIES)
    public static void resolveIndustrialArtifactResearchWithCommodities(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveIndustrialArtifactResearch(
                event, game, player, buttonID.substring(INDUSTRIAL_RESEARCH_COMMODITIES.length()), false);
    }

    private static void resolveIndustrialArtifactResearch(
            ButtonInteractionEvent event, Game game, Player player, String techId, boolean useTradeGoods) {
        boolean canPay = useTradeGoods ? player.getTg() >= 2 : player.getCommodities() >= 2;
        if (!canPay
                || player.hasTech(techId)
                || Mapper.getTech(techId) == null
                || !BlueReverieArtifactService.discardArtifact(game, player, INDUSTRIAL_ARTIFACT)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        if (useTradeGoods) {
            player.setTg(player.getTg() - 2);
        } else {
            player.setCommodities(player.getCommodities() - 2);
        }
        PlayerTechService.addTech(event, game, player, techId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " discarded _Industrial Artifact_, spent 2 "
                        + (useTradeGoods ? "trade goods" : "commodities") + ", and researched _"
                        + Mapper.getTech(techId).getName() + "_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerUnknownArtifact(Game game, Player activePlayer) {
        for (Player player : game.getRealPlayers()) {
            if (player == activePlayer || !player.hasRelic(UNKNOWN_ARTIFACT)) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCardsInfoThread(),
                    player.getRepresentation()
                            + ", you may discard _Unknown Artifact_ to make each ship's move value 1 until this tactical action ends.",
                    Buttons.red(
                            player.factionButtonChecker() + UNKNOWN + activePlayer.getFaction(),
                            "Discard Unknown Artifact"));
        }
    }

    @ButtonHandler(UNKNOWN)
    public static void resolveUnknownArtifact(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player activePlayer = game.getPlayerFromColorOrFaction(buttonID.substring(UNKNOWN.length()));
        if (activePlayer == null
                || activePlayer != game.getActivePlayer()
                || !BlueReverieArtifactService.discardArtifact(game, player, UNKNOWN_ARTIFACT)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        game.setStoredValue(UNKNOWN_MOVE_VALUE_KEY, activePlayer.getFaction());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentationNoPing() + " discarded _Unknown Artifact_.");
        if (player != activePlayer) {
            MessageHelper.sendMessageToChannel(
                    activePlayer.getCorrectChannel(),
                    "Each of your ships has move value 1 until this tactical action ends.");
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    public static boolean hasUnknownArtifactMoveRestriction(Game game, Player player) {
        return player == game.getActivePlayer()
                && player.getFaction().equals(game.getStoredValue(UNKNOWN_MOVE_VALUE_KEY));
    }

    public static void clearUnknownArtifactMoveRestriction(Game game) {
        game.removeStoredValue(UNKNOWN_MOVE_VALUE_KEY);
    }
}
