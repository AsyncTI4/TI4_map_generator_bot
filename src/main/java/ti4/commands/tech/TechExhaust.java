package ti4.commands.tech;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
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

    public static void exhaustTechAndResolve(GenericInteractionCreateEvent event, Game activeGame, Player player,
        String tech) {
        String pos = "";
        if (tech.contains("dskortg")) {
            pos = tech.split("_")[1];
            tech = "dskortg";
        }
        TechnologyModel techModel = Mapper.getTech(tech);
        String exhaustMessage = player.getRepresentation() + " exhausted tech " + techModel.getRepresentation(false);
        if (activeGame.isShowFullComponentTextEmbeds()) {
            MessageHelper.sendMessageToChannelWithEmbed(ButtonHelper.getCorrectChannel(player, activeGame), exhaustMessage,
                techModel.getRepresentationEmbed());
        } else {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), exhaustMessage);
        }
        player.exhaustTech(tech);
        switch (tech) {
            case "bs" -> { // Bio-stims
                ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeGame, event, player, false);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "gls" -> { // Graviton
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " exhausted graviton. The auto assign hit buttons for PDS fire will now kill fighters last");
                activeGame.setStoredValue(player.getFaction() + "graviton", "true");
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "dsgledb" -> {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " exhausted lightning drives because they are applying +1 move value to ships transporting fighters or infantry");
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "dsbenty" -> {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " exhausted merged replicators to increase the production value of one of their units by 2, or to match the largest value on the board");
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "dsceldr" -> {
                ButtonHelper.celdauriRedTech(player, activeGame, event);
                deleteTheOneButtonIfButtonEvent(event);
            }

            case "dscymiy" -> {
                List<Tile> tiles = new ArrayList<>();
                for (Tile tile : activeGame.getTileMap().values()) {
                    if (FoWHelper.playerHasUnitsInSystem(player, tile) && !tile.isHomeSystem() && !tile.getTileID().equalsIgnoreCase("18")) {
                        tiles.add(tile);
                    }
                }
                ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep1(player, activeGame, tiles);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_bs" -> { // Bio-stims
                ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeGame, event, player, true);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_x89" -> { // Absol X-89
                ButtonHelper.sendAbsolX89NukeOptions(activeGame, event, player);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_dxa" -> { // Dacxive
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Use buttons to drop 2 infantry on a planet",
                    Helper.getPlanetPlaceUnitButtons(player, activeGame, "2gf", "placeOneNDone_skipbuild"));
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_nm" -> { // Absol's Neural Motivator
                deleteIfButtonEvent(event);
                Button draw2ACButton = Button
                    .secondary(player.getFinsFactionCheckerPrefix() + "draw2ac", "Draw 2 Action Cards")
                    .withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", draw2ACButton);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "dskortg" -> {
                Tile tile = null;
                if (!pos.isEmpty()) {
                    tile = activeGame.getTileByPosition(pos);
                } else if (!activeGame.getActiveSystem().isEmpty()) {
                    tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
                }
                if (tile != null) {
                    String tileRep = tile.getRepresentationForButtons(activeGame, player);
                    String ident = player.getFactionEmoji();
                    String msg = ident + " removed CC from " + tileRep;
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                    RemoveCC.removeCC(event, player.getColor(), tile, activeGame);
                }
            }
            case "td" -> { // Transit Diodes
                ButtonHelper.resolveTransitDiodesStep1(activeGame, player);
            }
            case "miltymod_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                Button gainCC = Button.success(player.getFinsFactionCheckerPrefix() + "gain_CC", "Gain CC");
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    player.getFactionEmojiOrColor() + " use button to gain a CC:", List.of(gainCC));
            }
            case "absol_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                List<Button> buttons = new ArrayList<>();
                buttons.add(Button.success(player.getFinsFactionCheckerPrefix() + "gain_CC", "Gain CC"));
                if (player.getStrategicCC() > 0) {
                    for (Leader leader : player.getLeaders()) {
                        if (leader.isExhausted() && leader.getId().contains("agent")) {
                            buttons.add(Button.primary(
                                player.getFinsFactionCheckerPrefix() + "spendStratNReadyAgent_" + leader.getId(),
                                "Ready " + leader.getId()));
                        }
                    }
                    for (String relic : player.getExhaustedRelics()) {
                        if ("titanprototype".equalsIgnoreCase("relic") || "absol_jr".equalsIgnoreCase(relic)) {
                            buttons.add(Button.primary(
                                player.getFinsFactionCheckerPrefix() + "spendStratNReadyAgent_" + relic,
                                "Ready JR"));
                        }
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getFactionEmojiOrColor()
                    + " use button to gain a CC or spend a strat CC to ready your agent", buttons);
            }
            case "aida", "sar", "htp" -> {
                if (event instanceof ButtonInteractionEvent) {
                    ButtonInteractionEvent buttonEvent = (ButtonInteractionEvent) event;
                    ButtonHelper.deleteTheOneButton(buttonEvent);
                    if (buttonEvent.getButton().getLabel().contains("(")) {
                        player.addSpentThing(tech + "_");
                    } else {
                        player.addSpentThing(tech);
                    }
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, activeGame, "res");
                    buttonEvent.getMessage().editMessage(exhaustedMessage).queue();
                }
            }
            case "pi" -> { // Predictive Intelligence
                deleteTheOneButtonIfButtonEvent(event);
                Button deleteButton = Button.danger("FFCC_" + player.getFaction() + "_deleteButtons",
                    "Delete These Buttons");
                String message = player.getRepresentation(false, true) + " use buttons to redistribute";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                    List.of(Buttons.REDISTRIBUTE_CCs, deleteButton));
            }
            case "mi" -> { // Mageon
                deleteIfButtonEvent(event);
                //List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "getACFrom", null); old way
                List<Button> buttons = new ArrayList<>();
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player || p2.getAc() == 0) {
                        continue;
                    }
                    if (activeGame.isFoWMode()) {
                        buttons.add(Button.secondary(player.getFinsFactionCheckerPrefix() + "getACFrom_" + p2.getFaction(), p2.getColor()));
                    } else {
                        Button button = Button.secondary(player.getFinsFactionCheckerPrefix() + "getACFrom_" + p2.getFaction(), " ");
                        String factionEmojiString = p2.getFactionEmoji();
                        button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                        buttons.add(button);
                    }
                }
                String message = player.getRepresentation(true, true) + " Select who you would like to Mageon.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "dsaxisy" -> {
                deleteIfButtonEvent(event);
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, "sd",
                    "placeOneNDone_skipbuild");
                String message = player.getRepresentation(true, true)
                    + " select the planet you would like to place or move a spacedock to.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "dskolly" -> {
                deleteIfButtonEvent(event);
                if (event instanceof ButtonInteractionEvent bevent) {
                    ButtonHelperActionCards.resolveSeizeArtifactStep1(player, activeGame, bevent, "yes");
                }
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "dskolug" -> {
                deleteIfButtonEvent(event);
                String message = player.getRepresentation(true, true) + " stalled using the Applied Biothermics tech.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "vtx", "absol_vtx" -> { // Vortex
                deleteIfButtonEvent(event);
                List<Button> buttons = ButtonHelperFactionSpecific.getUnitButtonsForVortex(player, activeGame, event);
                String message = player.getRepresentation(true, true) + " Select what unit you would like to capture";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "wg" -> { // Wormhole Generator
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                String message = player.getRepresentation(true, true) + " select type of wormhole you wish to drop";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    message, buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "absol_wg" -> { // Absol's Wormhole Generator
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                String message = player.getRepresentation(true, true) + " select type of wormhole you wish to drop";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    message, buttons);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    message, buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "pm" -> { // Production Biomes
                deleteIfButtonEvent(event);
                ButtonHelperFactionSpecific.resolveProductionBiomesStep1(player, activeGame, event);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "lgf" -> { // Lazax Gate Folding
                if (player.getPlanets().contains("mr")) {
                    deleteIfButtonEvent(event);
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet("mr"), "inf mr",
                        activeGame);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getFactionEmoji() + " added 1 infantry to Mecatol Rex using Laxax Gate Folding");
                    sendNextActionButtonsIfButtonEvent(event, activeGame, player);
                } else {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation() + " You do not control Mecatol Rex");
                    player.refreshTech("lgf");
                }
            }
            case "sr", "absol_sar" -> { // Sling Relay or Absol Self Assembley Routines
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>();
                List<Tile> tiles = new ArrayList<>(ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player,
                    UnitType.Spacedock, UnitType.CabalSpacedock, UnitType.PlenaryOrbital));
                if (player.hasUnit("ghoti_flagship")) {
                    tiles.addAll(ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship));
                }
                List<String> pos2 = new ArrayList<>();
                for (Tile tile : tiles) {
                    if (!pos2.contains(tile.getPosition())) {
                        String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                        Button tileButton = Button.success(buttonID,
                            tile.getRepresentationForButtons(activeGame, player));
                        buttons.add(tileButton);
                        pos2.add(tile.getPosition());
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Select which tile you would like to produce a ship in ", buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "dsdihmy" -> { // Impressment Programs
                List<Button> buttons = ButtonHelper.getButtonsToExploreReadiedPlanets(player, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select a planet to explore",
                    buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
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

    private static void sendNextActionButtonsIfButtonEvent(GenericInteractionCreateEvent event, Game activeGame,
        Player player) {
        if (event instanceof ButtonInteractionEvent) {
            ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
        }
    }

    private static void deleteTheOneButtonIfButtonEvent(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent) {
            ButtonHelper.deleteTheOneButton((ButtonInteractionEvent) event);
        }
    }
}
