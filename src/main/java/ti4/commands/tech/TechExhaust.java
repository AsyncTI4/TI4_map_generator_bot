package ti4.commands.tech;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.collections4.CollectionUtils;
import ti4.buttons.Buttons;
import ti4.commands.ds.DrawBlueBackTile;
import ti4.commands.ds.DrawRedBackTile;
import ti4.commands.tokens.RemoveCC;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.UnitModifier;
import ti4.helpers.Units.UnitType;
import ti4.helpers.ignis_aurora.IgnisAuroraHelperTechs;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;

public class TechExhaust extends TechAddRemove {
    public TechExhaust() {
        super(Constants.TECH_EXHAUST, "Exhaust Tech");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        exhaustTechAndResolve(event, getActiveGame(), player, techID);
        checkAndApplyCombatMods(event, player, techID);
    }

    @ButtonHandler("exhaustTech_")
    public static void exhaustTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tech = buttonID.replace("exhaustTech_", "");
        exhaustTechAndResolve(event, game, player, tech);
    }

    public static void exhaustTechAndResolve(GenericInteractionCreateEvent event, Game game, Player player, String tech) {
        String pos = "";
        if (tech.contains("dskortg")) {
            pos = tech.split("_")[1];
            tech = "dskortg";
        }
        TechnologyModel techModel = Mapper.getTech(tech);
        String exhaustMessage = player.getRepresentation() + " exhausted tech " + techModel.getRepresentation(false);
        if (game.isShowFullComponentTextEmbeds()) {
            MessageHelper.sendMessageToChannelWithEmbed(player.getCorrectChannel(), exhaustMessage, techModel.getRepresentationEmbed());
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), exhaustMessage);
        }

        player.exhaustTech(tech);

        // Handle Ignis Aurora Techs
        if (tech.startsWith("baldrick_")) {
            IgnisAuroraHelperTechs.handleExhaustIgnisAuroraTech(event, game, player, tech);
            return;
        }

        switch (tech) {
            case "bs" -> { // Bio-stims
                ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(game, event, player, false);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "gls" -> { // Graviton
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " exhausted Graviton Laser System. The auto assign hit buttons for PDS fire will now kill fighters last");
                game.setStoredValue(player.getFaction() + "graviton", "true");
                deleteTheOneButtonIfButtonEvent(event);
            }

            case "dsbenty" -> {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " exhausted Merged Replicators to increase the production value of one of their units by 2, or to match the largest value on the board");
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "dsceldr" -> {
                ButtonHelper.celdauriRedTech(player, game, event);
                deleteTheOneButtonIfButtonEvent(event);
            }

            case "dscymiy" -> {
                List<Tile> tiles = new ArrayList<>();
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && !tile.isHomeSystem() && !tile.isMecatol()) {
                        tiles.add(tile);
                    }
                }
                ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(player, game, tiles);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_bs" -> { // Bio-stims
                ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(game, event, player, true);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_x89" -> { // Absol X-89
                ButtonHelper.sendAbsolX89NukeOptions(game, event, player);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_dxa" -> { // Dacxive
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Use buttons to drop 2 infantry on a planet",
                    Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_nm" -> { // Absol's Neural Motivator
                deleteIfButtonEvent(event);
                Button draw2ACButton = Buttons.gray(player.getFinsFactionCheckerPrefix() + "draw2 AC", "Draw 2 Action Cards", Emojis.ActionCard);
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", draw2ACButton);
                //sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dskortg" -> {
                Tile tile = null;
                if (!pos.isEmpty()) {
                    tile = game.getTileByPosition(pos);
                } else if (!game.getActiveSystem().isEmpty()) {
                    tile = game.getTileByPosition(game.getActiveSystem());
                }
                if (tile != null) {
                    String tileRep = tile.getRepresentationForButtons(game, player);
                    String ident = player.getFactionEmoji();
                    String msg = ident + " removed CC from " + tileRep;
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    RemoveCC.removeCC(event, player.getColor(), tile, game);
                }
            }
            case "td", "absol_td" -> { // Transit Diodes
                ButtonHelper.resolveTransitDiodesStep1(game, player);
            }
            case "miltymod_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                Button gainCC = Buttons.green(player.getFinsFactionCheckerPrefix() + "gain_CC", "Gain CC");
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getFactionEmojiOrColor() + " use button to gain 1 CC:", List.of(gainCC));
            }
            case "absol_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "gain_CC", "Gain CC"));
                if (player.getStrategicCC() > 0) {
                    for (Leader leader : player.getLeaders()) {
                        if (leader.isExhausted() && leader.getId().contains("agent")) {
                            buttons.add(Buttons.blue(
                                player.getFinsFactionCheckerPrefix() + "spendStratNReadyAgent_" + leader.getId(),
                                "Ready " + leader.getId()));
                        }
                    }
                    for (String relic : player.getExhaustedRelics()) {
                        if ("titanprototype".equalsIgnoreCase("relic") || "absol_jr".equalsIgnoreCase(relic)) {
                            buttons.add(Buttons.blue(
                                player.getFinsFactionCheckerPrefix() + "spendStratNReadyAgent_" + relic,
                                "Ready JR"));
                        }
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getFactionEmojiOrColor()
                    + " use button to gain 1 CC or spend 1 strat CC to ready your agent", buttons);
            }
            case "aida", "sar", "htp", "absol_aida" -> {
                if (event instanceof ButtonInteractionEvent buttonEvent) {
                    tech = tech.replace("absol_", "");
                    ButtonHelper.deleteTheOneButton(buttonEvent);
                    if (buttonEvent.getButton().getLabel().contains("(")) {
                        player.addSpentThing(tech + "_");
                    } else {
                        player.addSpentThing(tech);
                    }
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                    buttonEvent.getMessage().editMessage(exhaustedMessage).queue();
                }
            }
            case "pi", "absol_pi" -> { // Predictive Intelligence
                deleteTheOneButtonIfButtonEvent(event);
                Button deleteButton = Buttons.red("FFCC_" + player.getFaction() + "_deleteButtons",
                    "Delete These Buttons");
                String message = player.getRepresentation(false, true) + " use buttons to redistribute";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                    List.of(Buttons.REDISTRIBUTE_CCs, deleteButton));
            }
            case "dsvadeb" -> {
                ButtonHelperFactionSpecific.resolveVadenTgForSpeed(player, game, event);
            }
            case "mi" -> { // Mageon
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player || p2.getAc() == 0) {
                        continue;
                    }
                    if (game.isFowMode()) {
                        buttons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "getACFrom_" + p2.getFaction(), p2.getColor()));
                    } else {
                        Button button = Buttons.gray(player.getFinsFactionCheckerPrefix() + "getACFrom_" + p2.getFaction(), " ");
                        String factionEmojiString = p2.getFactionEmoji();
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                        buttons.add(button);
                    }
                }
                String message = player.getRepresentationUnfogged() + " Select who you would like to Mageon.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dsaxisy" -> {
                deleteIfButtonEvent(event);
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, "sd",
                    "placeOneNDone_skipbuild");
                String message = player.getRepresentationUnfogged()
                    + " select the planet you would like to place or move a space dock to.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dskolly" -> {
                deleteIfButtonEvent(event);
                if (event instanceof ButtonInteractionEvent bevent) {
                    ButtonHelperActionCards.resolveSeizeArtifactStep1(player, game, bevent, "yes");
                }
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dskolug" -> {
                deleteIfButtonEvent(event);
                String message = player.getRepresentationUnfogged() + " stalled using the Applied Biothermics tech.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "vtx", "absol_vtx" -> { // Vortex
                deleteIfButtonEvent(event);
                List<Button> buttons = ButtonHelperFactionSpecific.getUnitButtonsForVortex(player, game, event);
                String message = player.getRepresentationUnfogged() + " Select what unit you would like to capture";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "wg" -> { // Wormhole Generator
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                String message = player.getRepresentationUnfogged() + " select the type of wormhole you wish to drop.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "absol_wg" -> { // Absol's Wormhole Generator
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                String message = player.getRepresentationUnfogged() + " select the type of wormhole you wish to drop.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message, buttons);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message, buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "pm" -> { // Production Biomes
                deleteIfButtonEvent(event);
                ButtonHelperFactionSpecific.resolveProductionBiomesStep1(player, game, event);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "lgf" -> { // Lazax Gate Folding
                if (CollectionUtils.containsAny(player.getPlanetsAllianceMode(), Constants.MECATOLS)) {
                    deleteIfButtonEvent(event);
                    UnitModifier.parseAndUpdateGame(event, player.getColor(), game.getMecatolTile(), "inf mr", game);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getFactionEmoji() + " added 1 infantry to Mecatol Rex using Laxax Gate Folding");
                    sendNextActionButtonsIfButtonEvent(event, game, player);
                } else {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentation() + " You do not control Mecatol Rex");
                    player.refreshTech("lgf");
                }
            }
            case "det", "absol_det" -> {
                deleteIfButtonEvent(event);
                Die d1 = new Die(5);

                String message = player.getRepresentation() + " Rolled a " + d1.getResult() + " and will thus place a ";
                if (d1.getResult() > 4) {
                    message = message + "blue backed tile";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    DrawBlueBackTile.drawBlueBackTiles(event, game, player, 1);
                } else {
                    message = message + "red backed tile";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    DrawRedBackTile.drawRedBackTiles(event, game, player, 1);
                }
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "sr", "absol_sar" -> { // Sling Relay or Absol Self Assembley Routines
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>();
                List<Tile> tiles = new ArrayList<>(ButtonHelper.getTilesOfPlayersSpecificUnits(game, player,
                    UnitType.Spacedock, UnitType.CabalSpacedock, UnitType.PlenaryOrbital));
                if (player.hasUnit("ghoti_flagship")) {
                    tiles.addAll(ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship));
                }
                List<String> pos2 = new ArrayList<>();
                for (Tile tile : tiles) {
                    if (!pos2.contains(tile.getPosition())) {
                        String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                        Button tileButton = Buttons.green(buttonID,
                            tile.getRepresentationForButtons(game, player));
                        buttons.add(tileButton);
                        pos2.add(tile.getPosition());
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Select which tile you would like to produce a ship in ", buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            case "dsdihmy" -> { // Impressment Programs
                List<Button> buttons = ButtonHelper.getButtonsToExploreReadiedPlanets(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select a planet to explore",
                    buttons);
                sendNextActionButtonsIfButtonEvent(event, game, player);
            }
            default -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "> This tech is not automated. Please resolve manually.");
            }
        }
    }

    private static void checkAndApplyCombatMods(GenericInteractionCreateEvent event, Player player, String techID) {
        TemporaryCombatModifierModel possibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.TECH,
            techID, player.getNumberTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Combat modifier will be applied next time you push the combat roll button.");
        }
    }

    private static void deleteIfButtonEvent(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).getMessage().delete().queue();
        }
    }

    private static void sendNextActionButtonsIfButtonEvent(GenericInteractionCreateEvent event, Game game,
        Player player) {
        if (event instanceof ButtonInteractionEvent) {
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        }
    }

    public static void deleteTheOneButtonIfButtonEvent(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }
}
