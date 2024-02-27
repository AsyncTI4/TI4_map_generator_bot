package ti4.commands.tech;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
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

    public static void exhaustTechAndResolve(GenericInteractionCreateEvent event, Game activeGame, Player player, String tech) {
        TechnologyModel techModel = Mapper.getTech(tech);
        String exhaustMessage = player.getRepresentation() + " exhausted tech " + techModel.getRepresentation(false);
        if (activeGame.isShowFullComponentTextEmbeds()) {
            MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), exhaustMessage, techModel.getRepresentationEmbed());
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), exhaustMessage);
        }
        player.exhaustTech(tech);
        switch (tech) {
            case "bs" -> { // Bio-stims
                ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeGame, event, player, false);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_bs" -> { // Bio-stims
                ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeGame, event, player, true);
            }
            case "absol_x89" -> { // Absol X-89
                ButtonHelper.sendAbsolX89NukeOptions(activeGame, event, player);
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_dxa" -> { // Dacxive
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Use buttons to drop 2 infantry on a planet", Helper.getPlanetPlaceUnitButtons(player, activeGame, "2gf", "placeOneNDone_skipbuild"));
                deleteTheOneButtonIfButtonEvent(event);
            }
            case "absol_nm" -> { // Absol's Neural Motivator
                deleteIfButtonEvent(event);
                Button draw2ACButton = Button.secondary(player.getFinsFactionCheckerPrefix() + "sc_ac_drawdeleteThisButton", "Draw 2 Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", draw2ACButton);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "td" -> { // Transit Diodes
                ButtonHelper.resolveTransitDiodesStep1(activeGame, player);
            }
            case "miltymod_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                Button gainCC = Button.success(player.getFinsFactionCheckerPrefix() + "gain_CC", "Gain CC");
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getFactionEmojiOrColor() + " use button to gain a CC:", List.of(gainCC));
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
                Button deleteButton = Button.danger("FFCC_" + player.getFaction() + "_deleteButtons", "Delete These Buttons");
                String message = player.getRepresentation(false, true) + " use buttons to redistribute";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, List.of(Buttons.REDISTRIBUTE_CCs, deleteButton));
            }
            case "mi" -> { // Mageon
                deleteIfButtonEvent(event);
                List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "getACFrom", null);
                String message = player.getRepresentation(true, true) + " Select who you would like to Mageon.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
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
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            case "absol_wg" -> { // Absol's Wormhole Generator
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                String message = player.getRepresentation(true, true) + " select type of wormhole you wish to drop";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
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
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet("mr"), "inf mr", activeGame);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getFactionEmoji() + " added 1 infantry to Mecatol Rex using Laxax Gate Folding");
                    sendNextActionButtonsIfButtonEvent(event, activeGame, player);
                } else {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + " You do not control Mecatol Rex");
                    player.refreshTech("lgf");
                }
            }
            case "sr" -> { // Sling Relay
                deleteIfButtonEvent(event);
                List<Button> buttons = new ArrayList<>();
                List<Tile> tiles = new ArrayList<>(ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Spacedock, UnitType.CabalSpacedock, UnitType.PlenaryOrbital));
                if (player.hasUnit("ghoti_flagship")) {
                    tiles.addAll(ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship));
                }
                List<String> pos2 = new ArrayList<>();
                for (Tile tile : tiles) {
                    if (!pos2.contains(tile.getPosition())) {
                        String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                        Button tileButton = Button.success(buttonID, tile.getRepresentationForButtons(activeGame, player));
                        buttons.add(tileButton);
                        pos2.add(tile.getPosition());
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select which tile you would like to Sling a ship into.", buttons);
                sendNextActionButtonsIfButtonEvent(event, activeGame, player);
            }
            default -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "> This tech is not automated. Please resolve manually.");
            }
        }
    }

    private static void checkAndApplyCombatMods(GenericInteractionCreateEvent event, Player player, String techID) {
        TemporaryCombatModifierModel possibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.TECH, techID, player.getNumberTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Combat modifier will be applied next time you push the combat roll button.");
        }
    }

    private static void deleteIfButtonEvent(GenericInteractionCreateEvent event) {
        if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).getMessage().delete().queue();
        }
    }

    private static void sendNextActionButtonsIfButtonEvent(GenericInteractionCreateEvent event, Game activeGame, Player player) {
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
