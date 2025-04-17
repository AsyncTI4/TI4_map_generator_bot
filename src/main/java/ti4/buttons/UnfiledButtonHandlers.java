package ti4.buttons;

import static org.apache.commons.lang3.StringUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.ExploreHelper;
import ti4.helpers.Helper;
import ti4.helpers.ObjectiveHelper;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StatusHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.omegaPhase.PriorityTrackHelper;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.PlanetService;
import ti4.service.StatusCleanupService;
import ti4.service.button.ReactionService;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.game.StartPhaseService;
import ti4.service.game.SwapFactionService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.objectives.ScorePublicObjectiveService;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.player.RefreshCardsService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.turn.EndTurnService;
import ti4.service.turn.PassService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;

/*
 * Buttons methods which were factored out of {@link ButtonListener} which need to be filed away somewhere more appropriate
 */
public class UnfiledButtonHandlers { // TODO: move all of these methods to a better location, closer to the original button call and/or other related code

    @ButtonHandler("declareUse_")
    public static void declareUse(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String msg = player.getFactionEmojiOrColor() + " is using " + buttonID.split("_")[1];
        if (msg.contains("Vaylerian")) {
            msg = player.getFactionEmojiOrColor() + " is using Pyndil Gonsuul, the Vaylerian commander, to add +2 capacity to a ship with capacity.";
        }
        if (msg.contains("Tnelis")) {
            msg = player.getFactionEmojiOrColor() + " is using Fillipo Rois, the Tnelis commander,"
                + " producing a hit against 1 of their __non-fighter__ ships in the system to give __one__ of their ships a +1 move boost."
                + "\n-# This ability may only be used once per activation.";
            String pos = buttonID.split("_")[2];
            List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game,
                game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", use buttons to assign 1 hit.", buttons);
            game.setStoredValue("tnelisCommanderTracker", player.getFaction());
        }
        if (msg.contains("Ghemina")) {
            msg = player.getFactionEmojiOrColor() + " is using Jarl Vel & Jarl Jotrun, the Ghemina commanders, to gain 1 trade good after winning the space combat.";
            player.setTg(player.getTg() + 1);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
            ButtonHelperAbilities.pillageCheck(player, game);
        }
        if (msg.contains("Lightning")) {
            msg = player.getFactionEmojiOrColor()
                + " is using _Lightning Drives_ to give each ship not transporting fighters or infantry a +1 move boost."
                + "\n-# A ship transporting just mechs gets this boost.";
        }
        if (msg.contains("Impactor")) {
            msg = player.getFactionEmojiOrColor()
                + " is using _Reality Field Impactor_ to nullify the effects of one anomaly for this tactical action.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("unlockCommander_")
    public static void unlockCommander(ButtonInteractionEvent event, Player player, String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        CommanderUnlockCheckService.checkPlayer(player, buttonID.split("_")[1]);
    }

    @ButtonHandler("fogAllianceAgentStep3_")
    public static void fogAllianceAgentStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperHeroes.argentHeroStep3(game, player, event, buttonID);
    }

    @ButtonHandler(value = "requestAllFollow_", save = false)
    public static void requestAllFollow(ButtonInteractionEvent event, Game game) {
        if (game.getName().equalsIgnoreCase("fow273")) {
            event.getMessage().reply(event.getUser().getAsMention() + " has requested that everyone resolve this strategy card before play continues." +
                " Please do so as soon as you can. The active player should not take an action until this is done.")
                .queue();
        } else {
            event.getMessage().reply(game.getPing() + ", someone has requested that everyone resolve this strategy card before play continues." +
                " Please do so as soon as you can. The active player should not take an action until this is done.")
                .queue();
        }
    }

    @ButtonHandler("starChartsStep1_")
    public static void starChartsStep1(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelper.starChartStep1(game, player, buttonID.split("_")[1]);
    }

    @ButtonHandler("genericRemove_")
    public static void genericRemove(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericRemove_", "");
        game.resetCurrentMovedUnitsFrom1System();
        game.resetCurrentMovedUnitsFrom1TacticalAction();
        List<Button> systemButtons = ButtonHelperTacticalAction.getButtonsForAllUnitsInSystem(player, game, game.getTileByPosition(pos), "Remove");
        game.resetCurrentMovedUnitsFrom1System();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Chose to remove units from " + game.getTileByPosition(pos).getRepresentationForButtons(game, player));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to select the units you wish to remove.", systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doActivation_")
    public static void doActivation(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("doActivation_", "");
        ButtonHelper.resolveOnActivationEnemyAbilities(game, game.getTileByPosition(pos), player, false, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getACFrom_")
    public static void getACFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("getACFrom_", "");
        Player victim = game.getPlayerFromColorOrFaction(faction);
        List<Button> buttons = ButtonHelperFactionSpecific.getButtonsToTakeSomeonesAC(player, victim);
        ActionCardHelper.showAll(victim, player, game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged() + ", select which action card you wish to steal.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ring_")
    public static void ring(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> ringButtons = ButtonHelper.getTileInARing(player, game, buttonID);
        String num = buttonID.replace("ring_", "");
        String message;
        if (!"corners".equalsIgnoreCase(num)) {
            int ring = Integer.parseInt(num.charAt(0) + "");
            if (ring > 4 && !num.contains("left") && !num.contains("right")) {
                message = "That ring is very large. Specify if your tile is on the left or right side of the map (center will be counted in both).";
            } else {
                message = "Click the tile that you wish to activate.";
            }
        } else {
            message = "Click the tile that you wish to activate.";
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("garboziaAbilityExhaust_")
    public static void garboziaAbilityExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String planet = "garbozia";
        player.exhaustPlanetAbility(planet);
        ExploreService.explorePlanet(event, game.getTileFromPlanet(planet), planet, "INDUSTRIAL", player, true, game, 1, false);
    }

    @ButtonHandler("planetAbilityExhaust_")
    public static void planetAbilityExhaust(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("planetAbilityExhaust_", "");
        PlanetExhaustAbility.doAction(event, player, planet, game, true);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("genericBuild_")
    public static void genericBuild(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericBuild_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "genericBuild", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericModifyAllTiles_")
    public static void genericModifyAllTiles(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModifyAllTiles_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "genericModifyAllTiles", "place");
        String message = player.getRepresentation() + " Use the buttons to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericModify_")
    public static void genericModify(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModify_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.offerBuildOrRemove(player, game, event, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericReact")
    public static void genericReact(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "Turned down window" : null;
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("integratedBuild_")
    public static void integratedBuild(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int resources = 0;
        if (uH instanceof Planet plan) {
            resources = plan.getResources();
        }
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "integrated" + planet, "place");
        String message = player.getRepresentation()
            + " Using _Integrated Economy_ on " + Helper.getPlanetRepresentation(planet, game)
            + ". Use the buttons to produce units with a combined cost up to the planet (" + resources + ") resources.\n"
            + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Produce Units", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("produceOneUnitInTile_")
    public static void produceOneUnitInTile(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        buttonID = buttonID.replace("produceOneUnitInTile_", "");
        String type = buttonID.split("_")[1];
        String pos = buttonID.split("_")[0];
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to produce 1 unit.\n> " + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("winnuStructure_")
    public static void winnuStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("winnuStructure_", "").split("_")[0];
        String planet = buttonID.replace("winnuStructure_", "").split("_")[1];
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit + " " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " Placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, game));
    }

    @ButtonHandler("removeAllStructures_")
    public static void removeAllStructures(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        plan.removeAllUnits(player.getColor());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Removed all units on " + planet + " for " + player.getRepresentation());
        AddPlanetToPlayAreaService.addPlanetToPlayArea(event, game.getTileFromPlanet(planet), planet, game);
    }

    @ButtonHandler("jrStructure_")
    public static void jrStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("jrStructure_", "");
        if (!"tg".equalsIgnoreCase(unit)) {
            String message = player.getRepresentationUnfogged() + ", please choose the planet you wish to put your structure on.";
            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "placeOneNDone_dontskip");
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            }
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " trade goods increased by 1 " + player.gainTG(1) + ".");
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dacxive_")
    public static void daxcive(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("dacxive_", "");
        AddUnitService.addUnits(event, game.getTile(AliasHandler.resolveTile(planet)), game, player.getColor(), "infantry " + planet);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor() + " placed 1 infantry on "
            + Helper.getPlanetRepresentation(planet, game) + " via _Dacxive Animators_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arboAgentOn_")
    public static void arboAgentOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(player, game, event, game.getTileByPosition(pos), unit);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), player.getRepresentationUnfogged() + " select which unit you'd like to place down", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("setAutoPassMedian_")
    public static void setAutoPassMedian(ButtonInteractionEvent event, Player player, String buttonID) {
        String hours = buttonID.split("_")[1];
        int median = Integer.parseInt(hours);
        player.setAutoSaboPassMedian(median);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set median time to " + median + " hours");
        if (median > 0) {
            if (!player.hasAbility("quash") && !player.ownsPromissoryNote("rider")
                && !player.getPromissoryNotes().containsKey("riderm")
                && !player.hasAbility("radiance") && !player.hasAbility("galactic_threat")
                && !player.hasAbility("conspirators")
                && !player.ownsPromissoryNote("riderx")
                && !player.ownsPromissoryNote("riderm") && !player.ownsPromissoryNote("ridera")
                && !player.hasTechReady("gr")) {
                List<Button> buttons = new ArrayList<>();
                String msg = player.getRepresentation()
                    + " The bot may also auto react for you when you have no whens/afters. Default for this is off. This will only apply to this game. If you have any whens or afters or related when/after abilities, it will not do anything. ";
                buttons.add(Buttons.green("playerPrefDecision_true_agenda", "Turn on"));
                buttons.add(Buttons.green("playerPrefDecision_false_agenda", "Turn off"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arboAgentIn_")
    public static void arboAgentIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), player.getRepresentationUnfogged() + " select which unit you'd like to replace", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getReleaseButtons")
    public static void getReleaseButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentationUnfogged() + " you may release units one at a time with the buttons. Reminder that captured units may only be released as part of an ability or a transaction.",
            ButtonHelperFactionSpecific.getReleaseButtons(player, game));
    }

    @ButtonHandler("shroudOfLithStart")
    public static void shroudOfLithStart(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Select up to 2 ships and 2 ground forces to place in the space area",
            ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game));
    }

    @ButtonHandler("assimilate_")
    public static void assimilate(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(buttonID.split("_")[1], game);
        ButtonHelperModifyUnits.infiltratePlanet(player, game, uH, event);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("absolX89Nuke_")
    public static void absolX89Nuke(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFaction() + " used _X-89 Bacterial Weapon_ to remove all ground forces on " + planet + ".");
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        Map<UnitKey, Integer> units = new HashMap<>(uH.getUnits());
        for (UnitKey unit : units.keySet()) {
            if (unit.getUnitType() == UnitType.Mech || unit.getUnitType() == UnitType.Infantry) {
                uH.removeUnit(unit, units.get(unit));
            }
        }
    }

    @ButtonHandler("useTech_")
    public static void useTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tech = buttonID.replace("useTech_", "");
        TechnologyModel techModel = Mapper.getTech(tech);
        if (!tech.equalsIgnoreCase("st")) {
            String useMessage = player.getRepresentation() + " used technology: _" + techModel.getRepresentation(false) + "_.";
            if (game.isShowFullComponentTextEmbeds()) {
                MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), useMessage,
                    techModel.getRepresentationEmbed());
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), useMessage);
            }
        }
        switch (tech) {
            case "st" -> { // Sarween Tools
                player.addSpentThing("sarween");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                ButtonHelper.deleteTheOneButton(event, event.getButton().getId(), false);
                player.setSarweenCounter(player.getSarweenCounter() + 1);
                String msg = player.getFactionEmoji() + " has used Sarween Tools to save " + player.getSarweenCounter() + " resource(s) in this game so far. ";
                int result = ThreadLocalRandom.current().nextInt(0, 5);
                if (player.getSarweenCounter() < 6) {

                    List<String> lameMessages = Arrays.asList(
                        "Not too impressive.",
                        "The technology has not yet proven its worth.",
                        "There better be more savings to come.",
                        "Your faction's stockholders are so far unimpressed.",
                        "Perhaps AIDEV or Scanlink might have been more useful.");
                    msg += lameMessages.get(result);
                } else {
                    if (player.getSarweenCounter() < 11) {
                        List<String> lameMessages = Arrays.asList(
                            "Not too shabby.",
                            "The tech is finally starting to justify its existence.",
                            "Hopefully there are still even more savings to come.",
                            "Your faction's stockholders are satisfied with the results of this technology.",
                            "Some folks still think Scanlink might have been more useful.");
                        msg += lameMessages.get(result);
                    } else {
                        if (player.getSarweenCounter() < 16) {
                            List<String> lameMessages = Arrays.asList(
                                "Very impressive.",
                                "If only all technology was this productive.",
                                "Surely there can't be even more savings to come?",
                                "Your faction's stockholders are ectatic.",
                                "The Scanlink stans have been thoroughly shamed.");
                            msg += lameMessages.get(result);
                        } else {
                            List<String> lameMessages = Arrays.asList(
                                "Words cannot adequately express how impressive this is.",
                                "Is Sarween the best tech?!",
                                "Is this much saving even legal? The international IRS will be doing an audit on your paperwork sometime soon.",
                                "Your faction's stockholders have erected a statue of you in the city center.",
                                "Keep this up and we'll have to make a new channel, called Sarween Streaks, just for your numbers.");
                            msg += lameMessages.get(result);
                        }
                    }
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), msg);
                event.getMessage().editMessage(exhaustedMessage).queue();
            }
            case "absol_st" -> { // Absol's Sarween Tools
                player.addSpentThing("absol_sarween");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                ButtonHelper.deleteTheOneButton(event, event.getButton().getId(), false);
                event.getMessage().editMessage(exhaustedMessage).queue();
            }
            case "absol_pa" -> { // Absol's Psychoarcheology
                List<Button> absolPAButtons = new ArrayList<>();
                absolPAButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard", CardEmojis.ActionCard));
                for (String planetID : player.getReadiedPlanets()) {
                    Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetID, game);
                    if (planet != null && isNotBlank(planet.getOriginalPlanetType())) {
                        List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
                        absolPAButtons.addAll(planetButtons);
                    }
                }
                ButtonHelper.deleteTheOneButton(event);
                MessageHelper
                    .sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + ", use buttons to discard 2 action cards to explore a readied planet.",
                        absolPAButtons);
            }
        }
    }

    @ButtonHandler("useRelic_")
    public static void useRelic(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String relic = buttonID.replace("useRelic_", "");
        ButtonHelper.deleteTheOneButton(event);
        if ("boon".equals(relic)) {// Sarween Tools
            player.addSpentThing("boon");
            String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
            event.getMessage().editMessage(exhaustedMessage).queue();
        }
    }

    @ButtonHandler("bombardConfirm_")
    public static void bombardConfirm(ButtonInteractionEvent event, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(buttonID.replace("bombardConfirm_", ""), "Roll Bombardment"));
        String message = player.getRepresentationUnfogged()
            + " please declare what units are bombarding what planet before hitting this button"
            + " (e.g. if you have two dreadnoughts and are splitting their BOMBADMENT across two planets, specify which planet the first one is hitting)."
            + " The bot does not track this.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler("deflectSC_")
    public static void deflectSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.split("_")[1];
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("deflectedSC", sc);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Put _Deflection_ on **" + Helper.getSCName(Integer.parseInt(sc), game) + "**.");
    }

    @ButtonHandler("finishComponentAction_")
    public static void finishComponentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resFrontier_")
    public static void resFrontier(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        buttonID = buttonID.replace("resFrontier_", "");
        String[] stuff = buttonID.split("_");
        String cardChosen = stuff[0];
        String pos = stuff[1];
        String cardRefused = stuff[2];
        game.addExplore(cardRefused);
        ExploreService.expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player, cardChosen);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reduceComm_")
    public static void reduceComm(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        String message = player.getFactionEmojiOrColor() + " reduced commodities by " + tgLoss + " (" + player.getCommodities() + "->"
            + (player.getCommodities() - tgLoss) + ")";

        if (tgLoss > player.getCommodities()) {
            message = "You don't have " + tgLoss + " commodit" + (tgLoss == 1 ? "y" : "ies") + ". No change made.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            player.setCommodities(player.getCommodities() - tgLoss);
            player.addSpentThing(message);
        }
        String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        Leader playerLeader = player.getLeader("keleresagent").orElse(null);
        if (playerLeader != null && !playerLeader.isExhausted()) {
            playerLeader.setExhausted(true);
            String messageText = player.getRepresentation() +
                " exhausted " + Helper.getLeaderFullRepresentation(playerLeader);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), messageText);

        }
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("reduceTG_")
    public static void reduceTG(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);

        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        if (tgLoss > player.getTg()) {
            String message = "You don't have " + tgLoss + " trade good" + (tgLoss == 1 ? "" : "s") + ". No change made.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            player.setTg(player.getTg() - tgLoss);
            player.increaseTgsSpentThisWindow(tgLoss);
        }
        if (tgLoss > player.getTg()) {
            ButtonHelper.deleteTheOneButton(event);
        }
        String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("sabotage_")
    public static void sabotage(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String typeNName = buttonID.replace("sabotage_", "");
        String type = typeNName.substring(0, typeNName.indexOf("_"));
        String acName = typeNName.replace(type + "_", "");
        String message = "Cancelling the action card _" + acName + "_ using ";
        Integer count = game.getAllActionCardsSabod().get(acName);
        if (count == null) {
            game.setSpecificActionCardSaboCount(acName, 1);
        } else {
            game.setSpecificActionCardSaboCount(acName, 1 + count);
        }
        GameMessageManager.remove(game.getName(), event.getMessageId());
        boolean sendReact = true;
        if ("empy".equalsIgnoreCase(type)) {
            message += "a Watcher (Empyrean mech)! The relevant Watcher should now be removed by the owner.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                "Remove the Watcher",
                ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "mech"));
            ButtonHelper.deleteMessage(event);
        } else if ("xxcha".equalsIgnoreCase(type)) {
            message += "_Instinct Training_! The technology has been exhausted and a command token removed from strategy pool.";
            if (player.hasTech(AliasHandler.resolveTech("Instinct Training"))) {
                player.exhaustTech(AliasHandler.resolveTech("Instinct Training"));
                if (player.getStrategicCC() > 0) {
                    player.setStrategicCC(player.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
                }
                ButtonHelper.deleteMessage(event);
            } else {
                sendReact = false;
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Someone clicked the _Instinct Training_ button but did not have the technology.");
            }
        } else if ("ac".equalsIgnoreCase(type)) {
            message += "A _Sabotage_!";
            boolean hasSabo = false;
            String saboID = "3";
            for (String AC : player.getActionCards().keySet()) {
                if (AC.contains("sabo")) {
                    hasSabo = true;
                    saboID = "" + player.getActionCards().get(AC);
                    break;
                }
            }
            if (hasSabo) {
                ActionCardHelper.playAC(event, game, player, saboID, game.getActionsChannel());
            } else {
                message = "Tried to play a _Sabotage_ but found none in hand.";
                sendReact = false;
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation()
                    + " You clicked the _Sabotage_ action card button but did not have a _Sabotage_ in hand.");
            }
        }

        if (acName.contains("Rider") || acName.contains("Sanction")) {
            AgendaHelper.reverseRider("reverse_" + acName, event, game, player);
        }
        if (sendReact) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), message + "\n" + game.getPing());
        }
    }

    @ButtonHandler("spend_")
    public static void spend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        PlanetExhaust.doAction(player, planetName, game);
        player.addSpentThing(planetName);

        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        if (uH != null) {
            if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                Tile tile = game.getTileFromPlanet(planetName);
                String msg = player.getRepresentation() + " added 1 infantry to " + planetName
                    + " due to the _Arcane Citadel_.";
                AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        if (whatIsItFor.contains("tech") && player.hasAbility("ancient_knowledge")) {
            if ((Mapper.getPlanet(planetName).getTechSpecialties() != null
                && !Mapper.getPlanet(planetName).getTechSpecialties().isEmpty())
                || ButtonHelper.checkForTechSkips(game, planetName)) {
                String msg = player.getRepresentation()
                    + " due to your **Ancient Knowledge** ability, you may be eligible to receive a commodity here if you exhausted this planet ("
                    + planetName
                    + ") for its technology speciality.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm));
                buttons.add(Buttons.red("deleteButtons", "N/A"));
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji()
                        + " may have the opportunity to gain a commodity from their **Ancient Knowledge** ability due to exhausting a technology speciality planet.");
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            }
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (!buttonRow.isEmpty()) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
    }

    @ButtonHandler("autoneticMemoryStep3")
    public static void autoneticMemoryStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (buttonID.contains("autoneticMemoryStep3a")) {
            ButtonHelperAbilities.autoneticMemoryStep3a(game, player, event);
        } else {
            ButtonHelperAbilities.autoneticMemoryStep3b(game, player, event);
        }
    }

    @ButtonHandler("cymiaeHeroAutonetic")
    public static void cymiaeHeroAutonetic(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String msg2 = player.getRepresentationNoPing() + " is choosing to resolve their **Autonetic Memory** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        buttons.add(Buttons.green("autoneticMemoryStep3a", "Pick Action Card From the Discard"));
        buttons.add(Buttons.blue("autoneticMemoryStep3b", "Drop 1 infantry"));
        String msg = player.getRepresentationUnfogged()
            + " you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        buttons = new ArrayList<>();
        buttons.add(
            Buttons.green("cymiaeHeroStep1_" + (game.getRealPlayers().size()), "Resolve Cymiae Hero"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation() + ", please resuming resolving your hero after doing **Autonetic Memory** steps.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getRepairButtons_")
    public static void getRepairButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("getRepairButtons_", "");
        List<Button> buttons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentationUnfogged() + " Use buttons to resolve", buttons);
    }

    @ButtonHandler("getDamageButtons_")
    public static void getDamageButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (buttonID.contains("deleteThis")) {
            buttonID = buttonID.replace("deleteThis", "");
            ButtonHelper.deleteMessage(event);
        }
        String pos = buttonID.split("_")[1];
        String assignType = "combat";
        if (buttonID.split("_").length > 2) {
            assignType = buttonID.split("_")[2];
        }
        List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game,
            game.getTileByPosition(pos), assignType);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " Use buttons to resolve", buttons);
    }

    @ButtonHandler("refreshViewOfSystem_")
    public static void refreshViewOfSystem(ButtonInteractionEvent event, String buttonID, Game game) {
        String rest = buttonID.replace("refreshViewOfSystem_", "");
        String pos = rest.split("_")[0];
        Player p1 = game.getPlayerFromColorOrFaction(rest.split("_")[1]);
        Player p2 = game.getPlayerFromColorOrFaction(rest.split("_")[2]);
        String groundOrSpace = rest.split("_")[3];
        try (FileUpload systemWithContext = new TileGenerator(game, event, null, 0, pos).createFileUpload()) {
            MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
            List<Button> buttons = StartCombatService.getGeneralCombatButtons(game, pos, p1, p2, groundOrSpace, event);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
        } catch (IOException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "Failed to close FileUpload", e);
        }
    }

    @ButtonHandler("refresh_")
    public static void refresh(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        Player p2 = player;
        if (StringUtils.countMatches(buttonID, "_") > 1) {
            String faction = buttonID.split("_")[2];
            p2 = game.getPlayerFromColorOrFaction(faction);
        }

        PlanetService.refreshPlanet(p2, planetName);
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (!buttonRow.isEmpty()) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        String totalVotesSoFar = event.getMessage().getContentRaw();
        if (totalVotesSoFar.contains("Readied")) {
            totalVotesSoFar += ", " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        } else {
            totalVotesSoFar = player.getFactionEmojiOrColor() + " Readied " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        }
        if (!actionRow2.isEmpty()) {
            event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
        }
    }

    // @ButtonHandler("strategicAction_")
    public static void strategicAction(ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel mainGameChannel) {
        int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
        PlayStrategyCardService.playSC(event, scNum, game, mainGameChannel, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("increaseTGonSC_")
    public static void increaseTGonSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.replace("increaseTGonSC_", "");
        int scNum = Integer.parseInt(sc);
        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        int tgCount = scTradeGoods.get(scNum);
        game.setScTradeGood(scNum, (tgCount + 1));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Added 1 trade good to " + Helper.getSCName(scNum, game) + ". There " + (tgCount == 0 ? "is" : "are") + " now "
                + (tgCount + 1) + " trade good" + (tgCount == 0 ? "" : "s") + " on it.");
    }

    @ButtonHandler("autoAssignAFBHits_")
    public static void autoAssignAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperModifyUnits.autoAssignAntiFighterBarrageHits(player, game, buttonID.split("_")[1],
            Integer.parseInt(buttonID.split("_")[2]), event);
    }

    @ButtonHandler("cancelAFBHits_")
    public static void cancelAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " cancelled 1 hit with an ability.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(finChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + h,
            "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "_afb",
            "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelAFBHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = "You may automatically assign " + h + " ANTI-FIGHTER BARRAGE hit" + (h == 1 ? "" : "s") + ".";
        event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
            .queue();
    }

    @ButtonHandler("cancelPdsOffenseHits_")
    public static void cancelPDSOffenseHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " cancelled 1 hit with an ability.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(finChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h,
            "Auto-Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "_pds",
            "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelPdsOffenseHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentationNoPing() + ", you may automatically assign " + (h == 1 ? "the hit" : "hits") + ". "
            + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true, true);
        event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
            .queue();
    }

    @ButtonHandler("cancelGroundHits_")
    public static void cancelGroundHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " cancelled 1 hit with an ability";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(finChecker + "autoAssignGroundHits_" + tile.getPosition() + "_" + h,
            "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(
            Buttons.red("getDamageButtons_" + tile.getPosition() + "_groundcombat",
                "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelGroundHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentation() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
        event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
            .queue();
    }

    @ButtonHandler("cancelSpaceHits_")
    public static void cancelSpaceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " cancelled 1 hit with an ability";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(finChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
            "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(
            Buttons.red("getDamageButtons_" + tile.getPosition() + "_spacecombat",
                "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelSpaceHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentationNoPing() + ", you may automatically assign " + (h == 1 ? "the hit" : "hits") + ". "
            + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true);
        event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
            .queue();
    }

    @ButtonHandler("autoAssignSpaceCannonOffenceHits_")
    public static void autoAssignSpaceCannonOffenceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game,
                game.getTileByPosition(buttonID.split("_")[1]),
                Integer.parseInt(buttonID.split("_")[2]), event, false, true));
    }

    @ButtonHandler("autoAssignSpaceHits_")
    public static void autoAssignSpaceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game,
                game.getTileByPosition(buttonID.split("_")[1]),
                Integer.parseInt(buttonID.split("_")[2]), event, false));
    }

    @ButtonHandler("explore_look_All")
    public static void exploreLookAll(ButtonInteractionEvent event, Player player, Game game) {
        List<String> order = List.of("cultural", "industrial", "hazardous");
        for (String type : order) {
            List<String> deck = game.getExploreDeck(type);
            List<String> discard = game.getExploreDiscard(type);

            String traitNameWithEmoji = ExploreEmojis.getTraitEmoji(type) + type;
            if (deck.isEmpty() && discard.isEmpty()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    traitNameWithEmoji + " exploration deck & discard is empty - nothing to look at.");
                continue;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            ExploreModel exp = Mapper.getExplore(deck.getFirst());
            sb.append(exp.textRepresentation());
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb.toString());
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            "The top card of each of the cultural, industrial, and hazardous exploration decks has been set to " + player.getFactionEmoji() + " `#cards-info` thread.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("discardExploreTop_")
    public static void discardExploreTop(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String deckType = buttonID.replace("discardExploreTop_", "");
        ButtonHelperFactionSpecific.resolveExpDiscard(player, game, event, deckType);
    }

    @ButtonHandler("resolveExp_Look_")
    public static void resolveExpLook(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String deckType = buttonID.replace("resolveExp_Look_", "");
        ButtonHelperFactionSpecific.resolveExpLook(player, game, event, deckType);
        ButtonHelper.deleteMessage(event);
    }

    public static void movedNExplored(ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel mainGameChannel) {
        String bID = buttonID.replace("movedNExplored_", "");
        boolean dsdihmy = false;
        if (bID.startsWith("dsdihmy_")) {
            dsdihmy = true;
        }
        String[] info = bID.split("_");
        Tile tile = game.getTileFromPlanet(info[1]);
        ExploreService.explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2], player, false, game, 1, false);
        if (dsdihmy) {
            player.exhaustPlanet(info[1]);
            MessageHelper.sendMessageToChannel(mainGameChannel,
                info[1] + " was exhausted by _Impressment Programs_.");
        }
        if (tile != null && player.getTechs().contains("dsdihmy")) {
            List<Button> produce = new ArrayList<>();
            String pos = tile.getPosition();
            produce.add(Buttons.blue("dsdihmy_" + pos, "Produce (1) Units"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation()
                    + ", you explored a planet, and due to _Impressment Programs_ you may now produce 1 ship in the system.",
                produce);
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("spendAStratCC")
    public static void spendAStratCC(ButtonInteractionEvent event, Player player, Game game) {
        if (player.getStrategicCC() > 0) {
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
        }
        String message = ButtonHelperSCs.deductCC(game, player, -1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("exhaustRelic_")
    public static void exhaustRelic(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String relic = buttonID.replace("exhaustRelic_", "");
        if (player.hasRelicReady(relic)) {
            player.addExhaustedRelic(relic);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji() + " exhausted " + Mapper.getRelic(relic).getName());
            ButtonHelper.deleteTheOneButton(event);
            if ("absol_luxarchtreatise".equalsIgnoreCase(relic)) {
                game.setStoredValue("absolLux", "true");
            }
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji() + " doesn't have an unexhausted " + relic);
        }
    }

    @ButtonHandler("reveal_stage_")
    public static void revealPOStage(ButtonInteractionEvent event, String buttonID, Game game) {
        String stage = buttonID.replace("reveal_stage_", "");
        if ("true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            if (game.getPhaseOfGame().equalsIgnoreCase("statusScoring")) {
                StringBuilder missingPeople = new StringBuilder();
                for (Player player : game.getRealPlayers()) {
                    String so = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "SO");
                    if (so.isEmpty()) {
                        missingPeople.append(player.getRepresentation(false, true));
                    }
                }
                if (missingPeople.length() > 0) {
                    MessageHelper.sendMessageToChannel(game.getActionsChannel(), missingPeople + " need to indicate if they are scoring a secret objective before the next PO can be flipped");
                    return;
                }
            }
        }
        if (!game.isRedTapeMode()) {
            if ("2".equalsIgnoreCase(stage)) {
                RevealPublicObjectiveService.revealS2(game, event, event.getChannel());
            } else if ("2x2".equalsIgnoreCase(stage)) {
                RevealPublicObjectiveService.revealTwoStage2(game, event, event.getChannel());
            } else if ("none".equalsIgnoreCase(stage)) {
                //continue without revealing anything
            } else {
                RevealPublicObjectiveService.revealS1(game, event, event.getChannel());
            }
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "In Red Tape, no objective is revealed at this stage.");
            int playersWithSCs = 0;
            for (Player player2 : game.getRealPlayers()) {
                if (player2.getSCs() != null && !player2.getSCs().isEmpty() && !player2.getSCs().contains(0)) {
                    playersWithSCs++;
                }
            }
            if (playersWithSCs > 0) {
                StatusCleanupService.runStatusCleanup(game);
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    "### " + game.getPing() + " **Status Cleanup Run!**");
            }
        }

        if (!game.isOmegaPhaseMode()) {
            StartPhaseService.startStatusHomework(event, game);
        } else {
            var speakerPlayer = game.getSpeaker();
            ObjectiveHelper.secondHalfOfPeakStage1(game, speakerPlayer, 1);
            String message = "The next Objective has been revealed to " + speakerPlayer.getRepresentation() + ". When ready, proceed to the Strategy Phase.";
            Button proceedToStrategyPhase = Buttons.green("proceed_to_strategy",
                "Proceed to Strategy Phase (will refresh all cards and ping the priority player)");
            MessageHelper.sendMessageToChannelWithButton(event.getChannel(), message, proceedToStrategyPhase);
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("assignSpeaker_")
    public static void assignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = StringUtils.substringAfter(buttonID, "assignSpeaker_");
        game.setStoredValue("hasntSetSpeaker", "");
        if (!game.isFowMode()) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.getFaction().equals(faction)) {
                    game.setSpeakerUserID(player_.getUserID());
                    String message = MiscEmojis.SpeakerToken + " Speaker assigned to: " + player_.getRepresentation(false, true);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    if (!game.isFowMode()) {
                        ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                    }
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("assignSpeaker_")
    @ButtonHandler(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)
    public static void sc3AssignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
        faction = faction.replace("assignSpeaker_", "");
        game.setStoredValue("hasntSetSpeaker", "");
        for (Player player_ : game.getPlayers().values()) {
            if (player_.getFaction().equals(faction)) {
                game.setSpeakerUserID(player_.getUserID());
                String message = MiscEmojis.SpeakerToken + " Speaker assigned to: " + player_.getRepresentation(false, true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                if (game.isFowMode() && player != player_) {
                    MessageHelper.sendMessageToChannel(player_.getPrivateChannel(), message);
                }
                if (!game.isFowMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void poScoring(ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel privateChannel) {
        if (!"true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, privateChannel, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
                    String msg = "Please score objectives.";
                    msg += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
                    event.getMessage().editMessage(msg).queue();
                }
            } catch (Exception e) {
                BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Could not parse PO ID: " + poID, e);
                event.getChannel().sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                    .queue();
            }
            return;
        }
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key3b = "potentialScoreSOBlockers";
        String message;
        for (Player player2 : game.getActionPhaseTurnOrder()) {
            if (player2 == player) {
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2, game.getStoredValue(key2)
                        .replace(player.getFaction() + "*", ""));
                }

                String poID = buttonID.replace(Constants.PO_SCORING, "");
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, privateChannel, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key3, game.getStoredValue(key3)
                        .replace(player.getFaction() + "*", ""));
                    if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                        Helper.resolvePOScoringQueue(game, event);
                    }
                }
                break;
            }
            if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                message = " has been queued to score a public objective. ";
                if (!game.isFowMode()) {
                    message += player2.getRepresentationUnfogged()
                        + " is the one the game is currently waiting on";
                }
                String poID = buttonID.replace(Constants.PO_SCORING, "");
                try {
                    int poIndex = Integer.parseInt(poID);
                    if (!game.getPhaseOfGame().equalsIgnoreCase("action")) {
                        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "Queued");
                    }
                    game.setStoredValue(player.getFaction() + "queuedPOScore", "" + poIndex);
                } catch (Exception e) {
                    BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Could not parse PO ID: " + poID, e);
                    event.getChannel().sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                        .queue();
                }
                game.setStoredValue(key2,
                    game.getStoredValue(key2) + player.getFaction() + "*");
                ReactionService.addReaction(event, game, player, message);
                break;
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty() && !game.getPhaseOfGame().equalsIgnoreCase("action")) {
            String msg = "Please score objectives.";
            msg += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue();
        }
        if (game.getPhaseOfGame().equalsIgnoreCase("action")) {
            event.getMessage().delete().queue();
        }
    }

    @ButtonHandler(value = "get_so_discard_buttons", save = false)
    public static void getSODiscardButtons(ButtonInteractionEvent event, Player player, Game game) {
        String secretScoreMsg = "_ _\nClick a button below to discard your Secret Objective";
        List<Button> soButtons = SecretObjectiveHelper.getUnscoredSecretObjectiveDiscardButtons(player);
        if (!soButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Developers");
        }
    }

    @ButtonHandler("retreat_")
    public static void retreat(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        boolean skilled = false;
        if (buttonID.contains("skilled")) {
            skilled = true;
            ButtonHelper.deleteMessage(event);
        }
        if (buttonID.contains("foresight")) {
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor()
                + ", you placed 1 command token from your strategy pool to resolve your " + FactionEmojis.Naalu + "**Foresight** ability.");
            player.setStrategicCC(player.getStrategicCC() - 1);
            skilled = true;
        }
        String message = player.getRepresentationUnfogged() + ", use buttons to select a system to move to.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, pos, skilled));
    }

    @ButtonHandler("retreatUnitsFrom_")
    public static void retreatUnitsFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperModifyUnits.retreatSpaceUnits(buttonID, event, game, player);
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentationNoPing() + " retreated all units in space to "
            + game.getTileByPosition(pos2).getRepresentationForButtons(game, player) + ".");
        String message = player.getRepresentationUnfogged() + " Use below buttons to move any ground forces or conclude retreat.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, game, pos1, pos2));
        ButtonHelper.deleteMessage(event);
        if (game.getTileByPosition(pos1).isGravityRift()) {
            Button rift = Buttons.green(player.getFinsFactionCheckerPrefix() + "getRiftButtons_" + pos2,
                "Rift Units", MiscEmojis.GravityRift);
            List<Button> buttons = new ArrayList<>();
            buttons.add(rift);
            String message2 = player.getRepresentationUnfogged() + " if applicable, use this button to rift retreating units.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
        }
    }

    @ButtonHandler("getPsychoButtons")
    public static void offerPsychoButtons(Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " use buttons to gain 1 trade good per planet exhausted.",
            ButtonHelper.getPsychoTechPlanets(game, player));
    }

    @ButtonHandler("getAgentSelection_")
    public static void getAgentSelection(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteTheOneButton(event);
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentationUnfogged() + " choose the target of your agent", buttons);
    }

    @ButtonHandler(value = "get_so_score_buttons", save = false)
    public static void getSoScoreButtons(ButtonInteractionEvent event, Player player) {
        String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
        List<Button> soButtons = SecretObjectiveHelper.getUnscoredSecretObjectiveButtons(player);
        if (!soButtons.isEmpty()) {
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, secretScoreMsg, soButtons);
        } else {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have no secret objectives you can score");
        }
    }

    public static void soScoreFromHand(
        ButtonInteractionEvent event,
        String buttonID,
        Game game, Player player,
        MessageChannel privateChannel,
        MessageChannel mainGameChannel,
        MessageChannel actionsChannel
    ) {
        String soID = buttonID.replace(Constants.SO_SCORE_FROM_HAND, "");
        MessageChannel channel;
        if (game.isFowMode()) {
            channel = privateChannel;
        } else if (game.isCommunityMode() && game.getMainGameChannel() != null) {
            channel = mainGameChannel;
        } else {
            channel = actionsChannel;
        }
        if (channel != null) {
            int soIndex2 = Integer.parseInt(soID);
            // String phase = "action";
            if (player.getSecret(soIndex2) != null && "status".equalsIgnoreCase(player.getSecret(soIndex2).getPhase())
                && "true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
                String key2 = "queueToScoreSOs";
                String key3 = "potentialScoreSOBlockers";
                String key3b = "potentialScorePOBlockers";
                String message;
                for (Player player2 : game.getActionPhaseTurnOrder()) {
                    if (player2 == player) {
                        int soIndex = Integer.parseInt(soID);
                        SecretObjectiveHelper.scoreSO(event, game, player, soIndex, channel);
                        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key2, game.getStoredValue(key2)
                                .replace(player.getFaction() + "*", ""));
                        }
                        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key3, game.getStoredValue(key3)
                                .replace(player.getFaction() + "*", ""));
                            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                                Helper.resolvePOScoringQueue(game, event);
                            }
                        }

                        break;
                    }
                    if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                        message = player.getRepresentation()
                            + "  has been queued to score a secret objective. ";
                        if (!game.isFowMode()) {
                            message += player2.getRepresentationUnfogged()
                                + " is the one the game is currently waiting on";
                        }
                        if (!game.getPhaseOfGame().equalsIgnoreCase("action")) {
                            game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "Queued");
                        }
                        MessageHelper.sendMessageToChannel(channel, message);
                        int soIndex = Integer.parseInt(soID);
                        game.setStoredValue(player.getFaction() + "queuedSOScore", "" + soIndex);
                        game.setStoredValue(key2,
                            game.getStoredValue(key2) + player.getFaction() + "*");
                        break;
                    }
                }
            } else {
                try {
                    int soIndex = Integer.parseInt(soID);
                    SecretObjectiveHelper.scoreSO(event, game, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse secret objective ID: " + soID + ". Please score manually.")
                        .queue();
                    return;
                }
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void acDiscardFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player, MessageChannel actionsChannel) { //TODO: bake this into /ac discard
        String acIndex = buttonID.replace("ac_discard_from_hand_", "");
        boolean stalling = false;
        boolean drawReplacement = false;
        boolean retainButtons = false;
        if (acIndex.contains("stall")) {
            acIndex = acIndex.replace("stall", "");
            stalling = true;
        }
        if (acIndex.endsWith("redraw")) {
            acIndex = acIndex.replace("redraw", "");
            drawReplacement = true;
        }
        if (acIndex.endsWith("retain")) {
            acIndex = acIndex.replace("retain", "");
            retainButtons = true;
        }

        MessageChannel channel;
        if (game.getMainGameChannel() != null) {
            channel = game.getMainGameChannel();
        } else {
            channel = actionsChannel;
        }

        if (channel != null) {
            try {
                String acID = null;
                for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                    if (so.getValue().equals(Integer.parseInt(acIndex))) {
                        acID = so.getKey();
                    }
                }

                boolean removed = game.discardActionCard(player.getUserID(), Integer.parseInt(acIndex));
                if (!removed) {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        "No such Action Card ID found, please retry");
                    return;
                }
                String sb = player.getRepresentation() + " discarded the action card _" + Mapper.getActionCard(acID).getName() + "_.\n" +
                    Mapper.getActionCard(acID).getRepresentation();
                MessageChannel channel2 = game.getMainGameChannel();
                if (game.isFowMode()) {
                    channel2 = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannel(channel2, sb);
                ActionCardHelper.sendActionCardInfo(game, player);
                String message = "Use buttons to end turn or do another action.";
                if (stalling) {
                    if (player.hasUnit("yssaril_mech") && !ButtonHelper.isLawInPlay(game, "regulations") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4) {
                        String message3 = "Use buttons to drop 1 mech on a planet or decline";
                        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game,
                            "mech", "placeOneNDone_skipbuild"));
                        buttons.add(Buttons.red("deleteButtons", "Decline to drop Mech"));
                        MessageHelper.sendMessageToChannelWithButtons(channel2, message3, buttons);
                    }
                    List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
                }
                if (drawReplacement) {
                    ActionCardHelper.drawActionCards(game, player, 1, true);
                }
                ButtonHelper.checkACLimit(game, player);
                if (!retainButtons) {
                    ButtonHelper.deleteMessage(event);
                } else {
                    ButtonHelper.deleteTheOneButton(event, buttonID, false);
                }
                if (player.hasUnexhaustedLeader("cymiaeagent")) {
                    List<Button> buttons2 = new ArrayList<>();
                    Button hacanButton = Buttons.gray("exhaustAgent_cymiaeagent_" + player.getFaction(), "Use Cymiae Agent", FactionEmojis.cymiae);
                    buttons2.add(hacanButton);
                    MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Skhot Unit X-12, the Cymiae"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent, to make yourself draw 1 action card.",
                        buttons2);
                }
                ActionCardHelper.serveReverseEngineerButtons(game, player, List.of(acID));
            } catch (Exception e) {
                BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Something went wrong discarding", e);
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
    }

    @ButtonHandler(Constants.AC_PLAY_FROM_HAND)
    public static void acPlayFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player) { //TODO: bake this into /ac play
        String acID = buttonID.replace(Constants.AC_PLAY_FROM_HAND, "");
        MessageChannel channel = game.getMainGameChannel();
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + ", please play _Sabotage_ by clicking the Sabo button on the action card you wish to Sabo.");
            return;
        }
        if (acID.contains("reverse_")) {
            String actionCardTitle = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolveReverse_" + actionCardTitle,
                "Pick Up " + actionCardTitle + " From The Discard"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + ", after checking for Sabos, use buttons to resolve _Reverse Engineer_.", scButtons);
        }
        if (acID.contains("counterstroke_")) {
            String tilePos = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolveCounterStroke_" + tilePos,
                "Counterstroke in " + tilePos));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + ", after checking for Sabos, use buttons to resolve _Counterstroke_.", scButtons);
        }
        if (channel != null) {
            try {
                String error = ActionCardHelper.playAC(event, game, player, acID, channel);
                if (error != null) {
                    event.getChannel().sendMessage(error).queue();
                }
            } catch (Exception e) {
                BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Could not parse AC ID: " + acID, e);
                event.getChannel().asThreadChannel().sendMessage("Could not parse action card ID: " + acID + ". Please play manually.").queue();
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
    }

    @ButtonHandler("deleteButtons")
    public static void deleteButtons(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        buttonID = buttonID.replace("deleteButtons_", "");
        String editedMessage = event.getMessage().getContentRaw();
        if (("Done Gaining Command Tokens".equalsIgnoreCase(buttonLabel)
            || "Done Redistributing Command Tokens".equalsIgnoreCase(buttonLabel)
            || "Done Losing Command Tokens".equalsIgnoreCase(buttonLabel)) && editedMessage.contains("command tokens have gone from")) {

            String playerRep = player.getRepresentation();
            String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
            String shortCCs = editedMessage.substring(editedMessage.indexOf("command tokens have gone from "));
            shortCCs = shortCCs.replace("command tokens have gone from ", "");
            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
            if (event.getMessage().getContentRaw().contains("Net gain")) {
                boolean cyber = false;
                int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                finalCCs += ". You gained a net total of " + netGain + " command token" + (netGain == 1 ? "" : "s");
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                        cyber = true;
                    }
                }
                if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
                    if (player.hasAbility("versatile") || player.hasTech("hm") || cyber) {
                        int properGain = 2;
                        String reasons = "";
                        if (player.hasAbility("versatile")) {
                            properGain += 1;
                            reasons = "**Versatile** ";
                        }
                        if (player.hasTech("hm")) {
                            properGain += 1;
                            reasons += "_Hyper Metabolism_ ";
                        }
                        if (cyber) {
                            properGain += 1;
                            reasons += "_Cybernetic Enhancements_ ";
                        }
                        if (netGain < properGain && netGain != 1) {
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                                "# " + player.getRepresentationUnfogged()
                                    + " heads up, bot thinks you should have gained " + properGain
                                    + " command token" + (properGain == 1 ? "" : "s") + " due to: " + reasons);
                        }
                    }
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(player.getPrivateChannel(),
                            "## Remember to click \"Ready for " + (game.isCustodiansScored() ? "Agenda" : "Strategy Phase")
                                + "\" when done with homework!\n" + game.getMainGameChannel().getJumpUrl());
                    }
                }
                player.setTotalExpenses(player.getTotalExpenses() + netGain * 3);
            }

            if ("Done Redistributing Command Tokens".equalsIgnoreCase(buttonLabel)) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    playerRep + ", your initial command token allocation was " + shortCCs + ". Your final command token allocation is " + finalCCs + ".");
            } else {
                if ("leadership".equalsIgnoreCase(buttonID)) {
                    game.setStoredValue("ledSpend" + player.getFaction(), "");
                    String message = playerRep + ", your initial command token allocation was " + shortCCs + ". Your final command tokens allocation is "
                        + finalCCs + ".";
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "leadership");
                } else {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        playerRep + ", your final command tokens allocation is " + finalCCs + ".");
                }

            }
            ButtonHelper.checkFleetInEveryTile(player, game, event);

        }
        if (("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
            || "Done Producing Units".equalsIgnoreCase(buttonLabel))
            && !event.getMessage().getContentRaw().contains("Click the names of the planets you wish")) {
            Tile tile = null;
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel) && buttonID.contains("_")) {
                String pos = buttonID.split("_")[1];
                buttonID = buttonID.split("_")[0];
                tile = game.getTileByPosition(pos);
            }
            ButtonHelper.sendMessageToRightStratThread(player, game, editedMessage, buttonID);
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel)) {
                event.getChannel().getHistory().retrievePast(2).queue(messageHistory -> {
                    Message previousMessage = messageHistory.get(1);
                    if (previousMessage.getContentRaw().contains("You have available to you")) {
                        previousMessage.delete().queue();
                    }
                });
                player.setTotalExpenses(player.getTotalExpenses() + Helper.calculateCostOfProducedUnits(player, game, true));
                String message2 = player.getRepresentationUnfogged() + " Click the names of the planets you wish to exhaust.";
                boolean warM = player.getSpentThingsThisWindow().contains("warmachine");

                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
                if (player.hasTechReady("sar") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("exhaustTech_sar", "Exhaust Self-Assembly Routines", TechEmojis.WarfareTech));
                }
                if (player.hasTechReady("htp") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    buttons.add(Buttons.red("exhaustTech_htp", "Exhaust Hegemonic Trade Policy", FactionEmojis.Winnu));
                }
                if (game.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated") && !buttonID.contains("generic")) {
                    ButtonHelperCommanders.titansCommanderUsage(event, game, player);
                }
                if (player.hasTechReady("dsbenty")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.green("exhaustTech_dsbenty", "Use Merged Replicators", FactionEmojis.bentor));
                }
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 0 && player.hasTechReady("aida")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("exhaustTech_aida", "Exhaust AI Development Algorithm (" + ButtonHelper.getNumberOfUnitUpgrades(player) + "r)", TechEmojis.WarfareTech));
                }
                if (player.hasTechReady("st") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("useTech_st", "Use Sarween Tools", TechEmojis.CyberneticTech));
                }
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    buttons.add(Buttons.red("useRelic_boon", "Use Boon Of The Cerulean God Relic"));
                }
                if (player.hasTechReady("absol_st")) {
                    buttons.add(Buttons.red("useTech_absol_st", "Use Sarween Tools"));
                }
                if (player.hasUnexhaustedLeader("winnuagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("exhaustAgent_winnuagent", "Use Winnu Agent", FactionEmojis.Winnu));
                }
                if (player.hasUnexhaustedLeader("gledgeagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("exhaustAgent_gledgeagent_" + player.getFaction(), "Use Gledge Agent", FactionEmojis.gledge));
                }
                if (player.hasUnexhaustedLeader("uydaiagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("exhaustAgent_uydaiagent_" + player.getFaction(), "Use Uydai Agent", FactionEmojis.gledge));
                }
                if (player.hasUnexhaustedLeader("ghotiagent")) {
                    buttons.add(Buttons.red("exhaustAgent_ghotiagent_" + player.getFaction(), "Use Ghoti Agent", FactionEmojis.ghoti));
                }
                if (player.hasUnexhaustedLeader("mortheusagent")) {
                    buttons.add(Buttons.red("exhaustAgent_mortheusagent_" + player.getFaction(), "Use Mortheus Agent", FactionEmojis.mortheus));
                }
                if (player.hasUnexhaustedLeader("rohdhnaagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    buttons.add(Buttons.red("exhaustAgent_rohdhnaagent_" + player.getFaction(), "Use Roh'Dhna Agent", FactionEmojis.rohdhna));
                }
                if (player.hasLeaderUnlocked("hacanhero") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("purgeHacanHero", "Purge Hacan Hero", FactionEmojis.Hacan));
                }
                Button doneExhausting;
                if (!buttonID.contains("deleteButtons")) {
                    doneExhausting = Buttons.red("deleteButtons_" + buttonID, "Done Exhausting Planets");
                } else {
                    doneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
                }
                if (warM) {
                    player.addSpentThing("warmachine");
                }
                // ButtonHelper.updateMap(game, event,
                // "Result of build on turn " + player.getInRoundTurnCount() + " for " + player.getFactionEmoji());
                buttons.add(doneExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                if (tile != null && player.hasAbility("rally_to_the_cause")
                    && player.getHomeSystemTile() == tile
                    && !ButtonHelperAbilities.getTilesToRallyToTheCause(game, player).isEmpty()) {
                    String msg = player.getRepresentation()
                        + " due to your **Rally to the Cause** ability, if you just produced a ship in your home system,"
                        + " you may produce up to 2 ships in a system that contains a planet with a trait,"
                        + " but does not contain a legendary planet or another player's units. Press button to resolve";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.green("startRallyToTheCause", "Rally To The Cause"));
                    buttons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons2);

                }
            }
        }
        if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            if (player.hasTech("asn") && (buttonID.contains("tacticalAction") || buttonID.contains("warfare") || buttonID.contains("anarchy7Build"))) {
                ButtonHelperFactionSpecific.offerASNButtonsStep1(game, player, buttonID);
            }
            player.resetSpentThings();
            if (buttonID.contains("tacticalAction")) {
                ButtonHelper.exploreDET(player, game, event);
                ButtonHelperFactionSpecific.cleanCavUp(game, event);
                if (player.hasAbility("cunning")) {
                    List<Button> trapButtons = new ArrayList<>();
                    for (Planet uH : game.getTileByPosition(game.getActiveSystem()).getPlanetUnitHolders()) {
                        String planet = uH.getName();
                        trapButtons.add(Buttons.gray("setTrapStep3_" + planet, Helper.getPlanetRepresentation(planet, game)));
                    }
                    trapButtons.add(Buttons.red("deleteButtons", "Decline"));
                    String msg = player.getRepresentationUnfogged() + " you may use the buttons to place a trap on a planet.";
                    if (trapButtons.size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, trapButtons);
                    }
                }
                if (player.hasUnexhaustedLeader("celdauriagent")) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.gray("exhaustAgent_celdauriagent_" + player.getFaction(), "Use Celdauri Agent", FactionEmojis.celdauri));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "George Nobin, the Celdauri"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent, to place 1 space dock for 2 trade goods or 2 commodities.",
                        buttons);
                }
                List<Button> systemButtons2 = new ArrayList<>();
                if (!game.isAbsolMode() && player.getRelics().contains("emphidia")
                    && !player.getExhaustedRelics().contains("emphidia")) {
                    String message = player.getRepresentationUnfogged() + " You may use the button to explore a planet using _The Crown of Emphidia_.";
                    systemButtons2.add(Buttons.green("crownofemphidiaexplore", "Use Crown of Emphidia To Explore"));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("sardakkagent")) {
                    String message = player.getRepresentationUnfogged() + " You may use the button to use "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "T'ro, the N'orr" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                        + " agent.";
                    systemButtons2.addAll(ButtonHelperAgents.getSardakkAgentButtons(game));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("nomadagentmercer")) {
                    String message = player.getRepresentationUnfogged() + " You may use the button to to use "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Field Marshal Mercer, a Nomad"
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
                    systemButtons2.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(game, player));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }

                if (game.isNaaluAgent()) {
                    player = game.getPlayer(game.getActivePlayerID());
                    game.setNaaluAgent(false);
                }
                game.setStoredValue("tnelisCommanderTracker", "");

                String message = player.getRepresentationUnfogged()
                    + " Use buttons to end turn or do another action.";
                List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    message, systemButtons);
                player.resetOlradinPolicyFlags();
            }
        }
        if ("diplomacy".equalsIgnoreCase(buttonID)) {
            ButtonHelper.sendMessageToRightStratThread(player, game, editedMessage, "diplomacy", null);
        }
        if ("spitItOut".equalsIgnoreCase(buttonID) && !"Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), editedMessage);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void clearAllReactions(@NotNull ButtonInteractionEvent event) {
        Message mainMessage = event.getInteraction().getMessage();
        mainMessage.clearReactions().queue();
    }

    public static void checkForAllReactions(@NotNull ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getId();

        String messageId = event.getInteraction().getMessage().getId();
        var gameMessage = GameMessageManager.getOne(game.getName(), messageId);
        int matchingFactionReactions = 0;
        for (Player player : game.getRealPlayers()) {
            boolean factionReacted = false;
            String faction = player.getFaction();
            if (gameMessage.isPresent() && gameMessage.get().factionsThatReacted().contains(faction)) {
                factionReacted = true;
            } else if (buttonID.contains("no_after")) {
                if (game.getPlayersWhoHitPersistentNoAfter().contains(faction)) {
                    factionReacted = true;
                } else {
                    Message mainMessage = event.getMessage();
                    Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, event.getMessageId());
                    MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                    if (reaction != null) {
                        factionReacted = true;
                    }
                }
            } else if (buttonID.contains("no_when")) {
                if (game.getPlayersWhoHitPersistentNoWhen().contains(faction)) {
                    factionReacted = true;
                } else {
                    Message mainMessage = event.getMessage();
                    Emoji reactionEmoji = Helper.getPlayerReactionEmoji(game, player, event.getMessageId());
                    MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                    if (reaction != null) {
                        factionReacted = true;
                    }
                }
            }
            if (factionReacted) {
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = game.getRealPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            respondAllPlayersReacted(event, game);
            GameMessageManager.remove(game.getName(), messageId);
        } else if (buttonID != null && (buttonID.contains("po_scoring") || buttonID.contains("po_no_scoring"))) {
            boolean allReacted = true;
            for (Player player : game.getRealPlayers()) {
                String po = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "PO");
                if (po.isEmpty()) {
                    allReacted = false;
                }
            }
            if (allReacted) {
                respondAllPlayersReacted(event, game);
            }
        }
    }

    private static void respondAllPlayersReacted(ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getId();
        if (game == null || buttonID == null) {
            return;
        }
        if (buttonID.startsWith(Constants.PO_SCORING)) {
            buttonID = Constants.PO_SCORING;
        } else if ((buttonID.startsWith(Constants.SC_FOLLOW) || buttonID.startsWith("sc_no_follow"))) {
            buttonID = Constants.SC_FOLLOW;
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            String buttonText = event.getButton().getLabel();
            event.getInteraction().getMessage().reply("All players have reacted to '" + buttonText + "'").queue();
        }
        switch (buttonID) {
            case Constants.SC_FOLLOW, "sc_refresh", "sc_refresh_and_wash", "trade_primary", "sc_ac_draw", "sc_draw_so", "sc_trade_follow" -> {
                String message = "All players have reacted to this Strategy Card";
                if (game.isFowMode()) {
                    event.getInteraction().getMessage().reply(message).queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage(message).queueAfter(10, TimeUnit.SECONDS);
                }
            }
            case "no_when", "no_when_persistent" -> ReactionService.handleAllPlayersReactingNoWhens(event.getInteraction().getMessage(), game);
            case "no_after", "no_after_persistent" -> ReactionService.handleAllPlayersReactingNoAfters(event.getInteraction().getMessage(), game);
            case "no_sabotage" -> ReactionService.handleAllPlayersReactingNoSabotage(event.getInteraction().getMessage(), game);
            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> {
                String message2 = "All players have indicated public scoring. Flip the relevant public objective using the buttons. This will automatically run status clean-up if it has not been run already.";
                Button draw2Stage2 = Buttons.green("reveal_stage_2x2", "Reveal 2 Stage 2");
                Button drawStage2 = Buttons.green("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Buttons.green("reveal_stage_1", "Reveal Stage 1");
                List<Button> buttons = new ArrayList<>();
                if (game.isRedTapeMode()) {
                    message2 = "All players have indicated scoring. This game is Red Tape mode, which means no objective is revealed at this stage."
                        + " Please press one of the buttons below anyways though -- don't worry, it won't reveal anything, it will just run cleanup.";
                }
                if (game.getRound() < 4 || !game.getPublicObjectives1Peakable().isEmpty()) {
                    buttons.add(drawStage1);
                }
                if ((game.getRound() > 3 || game.getPublicObjectives1Peakable().isEmpty()) && !game.isOmegaPhaseMode()) {
                    if ("456".equalsIgnoreCase(game.getStoredValue("homebrewMode"))) {
                        buttons.add(draw2Stage2);
                    } else {
                        buttons.add(drawStage2);
                    }
                }
                var endGameDeck = game.isOmegaPhaseMode() ? game.getPublicObjectives1Peakable() : game.getPublicObjectives2Peakable();
                var endGameRound = game.isOmegaPhaseMode() ? 9 : 7;
                if (game.getRound() > endGameRound || endGameDeck.isEmpty()) {
                    if (game.isFowMode()) {
                        message2 += "\n> - If there are no more objectives to reveal, use the button to continue as is.";
                        message2 += " Or end the game manually.";
                        buttons.add(Buttons.green("reveal_stage_none", "Continue without revealing"));
                    } else {
                        message2 += "\n> - If there are no more objectives to reveal, use the button to end the game.";
                        message2 += " Whoever has the most points is crowned the winner, or whoever has the earliest initiative in the case of ties.";

                        buttons.add(Buttons.red("gameEnd", "End Game"));
                        buttons.add(Buttons.blue("rematch", "Rematch (make new game with same players/channels)"));
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }
            case "pass_on_abilities" -> {
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now,",
                        buttons);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), game.getPing()
                        + ", all players have indicated completion of status phase. Proceeding to Strategy Phase.");
                    StartPhaseService.startPhase(event, game, "strategy");
                }
            }
            case "redistributeCCButtons" -> {
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                            + " Please press the \"Flip Agenda\" button after they have finished redistributing tokens and you have fully resolved all other status phase effects.",
                        buttons);
                } else {
                    Button flipAgenda = Buttons.blue("startStrategyPhase", "Start Strategy Phase");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                            + " As the Custodians token is still on Mecatol Rex, there will be no agenda phase this round."
                            + " Please press the \"Start Strategy Phase\" button after they have finished redistributing tokens and you have fully resolved all other status phase effects.",
                        buttons);
                }
            }
        }
    }

    @ButtonHandler("relic_look_top")
    public static void relicLookTop(ButtonInteractionEvent event, Game game, Player player) {
        List<String> deck = game.getAllRelics();
        if (deck.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "The " + ExploreEmojis.Relic + " relic deck & discard is empty - nothing to look at.");
            return;
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "The top card of the " + ExploreEmojis.Relic + " relic deck has been sent to " + player.getFactionEmojiOrColor() + " `#cards-info` thread.");
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, false) + " looked at top card of the "
                + ExploreEmojis.Relic + " relic deck. The card has been sent to their `#cards-info` thread.");
        }

        // Cards Info Message
        String topCard = deck.getFirst();
        RelicModel relic = Mapper.getRelic(topCard);
        String message = "You looked at the top of the " + ExploreEmojis.Relic + " relic deck and saw _" + relic.getName() + "_.";
        MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, relic.getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reinforcements_cc_placement_")
    public static void reinforcementsCCPlacement(GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace("reinforcements_cc_placement_", "");
        String tileID = AliasHandler.resolveTile(planet.toLowerCase());
        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }
        CommandCounterHelper.addCC(event, player, tile);
        String message = player.getFactionEmojiOrColor() + " Placed 1 command token from reinforcements in the " + Helper.getPlanetRepresentation(planet, game) + " system.";
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event);
        }
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeHolderOfConInSystem_")
    public static void placeHolderOfConInSystem(GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace("placeHolderOfConInSystem_", "");
        String tileID = AliasHandler.resolveTile(planet.toLowerCase());
        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }
        Player constructionPlayer = player;
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(4)) {
                constructionPlayer = p2;
            }
        }
        CommandCounterHelper.addCC(event, constructionPlayer, tile);

        String colorName = Mapper.getColor(constructionPlayer.getColor()).getDisplayName();
        String message = player.getRepresentation() + " Placed 1 " + colorName + " command token in the "
            + Helper.getPlanetRepresentation(planet, game)
            + " system due to use of " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent on **Construction**.";
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startYinSpinner")
    public static void startYinSpinner(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentationNoPing() + " is using _Yin Spinner_.");
        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
        String message = "Use buttons to drop 2 infantry on a planet. Technically you may also drop 2 infantry with your ships, but this ain't supported yet via button."; //TODO: support yin spinner into space
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("componentAction")
    public static void componentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Please choose what kind of component action you wish to do.";
        List<Button> systemButtons = ComponentActionHelper.getAllPossibleCompButtons(game, player, event);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, systemButtons);
        // if (!game.isFowMode()) {
        //     ButtonHelper.deleteMessage(event);
        // }
    }

    @ButtonHandler("drawRelicFromFrag")
    public static void drawRelicFromFrag(ButtonInteractionEvent event, Player player, Game game) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        doAnotherAction(event, player, game);
    }

    @ButtonHandler("drawRelic")
    public static void drawRelic(ButtonInteractionEvent event, Player player, Game game) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thronePoint")
    public static void thronePoint(ButtonInteractionEvent event, Player player, Game game) {
        Integer poIndex = game.addCustomPO("Throne of the False Emperor", 1);
        game.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " scored a secret objective (they'll specify which one)");
        Helper.checkEndGame(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pay1tgToAnnounceARetreat")
    public static void pay1tgToAnnounceARetreat(ButtonInteractionEvent event, Player player) {
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor()
            + " paid 1 trade good to announce a retreat " + player.gainTG(-1) + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("announceReadyForDice_")
    public static void announceReadyForDice(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String p1Color = buttonID.split("_")[1];
        Player p1 = game.getPlayerFromColorOrFaction(p1Color);
        String p2Color = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(p2Color);
        String msg = " your opponent has declared they are ready to roll combat dice if you are.";
        if (player == p1 || player.getAllianceMembers().contains(p1.getFaction())) {
            msg = p2.getRepresentation(false, true) + msg;
        } else {
            msg = p1.getRepresentation(false, true) + msg;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

    }

    @ButtonHandler("announceARetreat")
    public static void announceARetreat(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "## " + player.getRepresentationNoPing() + " has announced a retreat.";
        if (game.playerHasLeaderUnlockedOrAlliance(player, "nokarcommander")) {
            msg += " Since they have Jack Hallard, the Nokar commander, this means they may cancel 2 hits in this coming combat round.";
        }
        String combatName = "combatRoundTracker" + game.getActivePlayer().getFaction() + game.getActiveSystem() + "space";
        if (game.getActivePlayer() != null && game.getActivePlayer() != player && game.getActivePlayer().hasAbility("cargo_raiders")
            && game.getStoredValue(combatName).isEmpty()) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("pay1tgToAnnounceARetreat", "Pay 1 Trade Good"));
            buttons.add(Buttons.red("deleteButtons", "I Don't Have to Pay"));
            String raiders = "\n" + player.getRepresentation() + ", a reminder that your opponent has the **Cargo Raiders** ability,"
                + " which means you might have to pay 1 trade good to announce a retreat if they choose.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg + raiders, buttons);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        }
    }

    @ButtonHandler("crownofemphidiaexplore")
    public static void crownOfEmphidiaExplore(ButtonInteractionEvent event, Player player, Game game) {
        player.addExhaustedRelic("emphidia");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmojiOrColor() + " Exhausted _The Crown of Emphidia_.");
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doAnotherAction")
    @ButtonHandler("finishComponentAction")
    public static void doAnotherAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doneRemoving")
    public static void doneRemoving(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        ButtonHelper.deleteMessage(event);
        ButtonHelper.updateMap(game, event);
    }

    @ButtonHandler("scoreAnObjective")
    public static void scoreAnObjective(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> poButtons = StatusHelper.getScoreObjectiveButtons(game, player.getFinsFactionCheckerPrefix());
        poButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        MessageChannel channel = event.getMessageChannel();
        if (game.isFowMode()) {
            channel = player.getPrivateChannel();
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to score an objective", poButtons);
    }

    @ButtonHandler("useLawsOrder")
    public static void useLawsOrder(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor() + " is paying " + MiscEmojis.Influence_1 + " influence to ignore laws for the turn.");
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Click the names of the planets you wish to exhaust to pay the 1 influence", buttons);
        ButtonHelper.deleteTheOneButton(event);
        game.setStoredValue("lawsDisabled", "yes");
    }

    @ButtonHandler("dominusOrb")
    public static void dominusOrb(ButtonInteractionEvent event, Player player, Game game) {
        game.setDominusOrb(true);
        String purgeOrExhaust = "Purged ";
        String relicId = "dominusorb";
        player.removeRelic(relicId);
        player.removeExhaustedRelic(relicId);
        String relicName = Mapper.getRelic(relicId).getName();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), purgeOrExhaust + ExploreEmojis.Relic + " relic: " + relicName);
        ButtonHelper.deleteMessage(event);
        String message = "Choose a system to move from.";
        List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, game, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
    }

    @ButtonHandler("getDiscardButtonsACs")
    public static void getDiscardButtonsACs(Player player, Game game) {
        String msg = player.getRepresentationUnfogged() + " use buttons to discard an action card.";
        List<Button> buttons = ActionCardHelper.getDiscardActionCardButtons(player, false);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("chooseMapView")
    public static void chooseMapView(ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("checkWHView", "Find Wormholes"));
        buttons.add(Buttons.red("checkAnomView", "Find Anomalies"));
        buttons.add(Buttons.green("checkLegendView", "Find Legendaries"));
        buttons.add(Buttons.gray("checkEmptyView", "Find Empties"));
        buttons.add(Buttons.blue("checkAetherView", "Determine Aetherstreamable Systems"));
        buttons.add(Buttons.red("checkCannonView", "Calculate Space Cannon Offense Shots"));
        buttons.add(Buttons.green("checkTraitView", "Find Traits"));
        buttons.add(Buttons.green("checkTechSkipView", "Find Technology Specialties"));
        buttons.add(Buttons.blue("checkAttachmView", "Find Attachments"));
        buttons.add(Buttons.gray("checkShiplessView", "Show Map Without Ships"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "", buttons);
    }

    @ButtonHandler("resetSpend_")
    public static void resetSpend_(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Helper.refreshPlanetsOnTheRespend(player, game);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 1) {
            whatIsItFor = buttonID.split("_")[1];
        }

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor);
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            for (ItemComponent but : buttonRow) {
                if (but instanceof Button butt) {
                    if (!Helper.doesListContainButtonID(buttons, butt.getId())) {
                        buttons.add(butt);
                    }
                }
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(exhaustedMessage).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons)).queue();
    }

    @ButtonHandler("resetSpend")
    public static void resetSpend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Helper.refreshPlanetsOnTheRevote(player, game);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        player.resetSpentThings();
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor);
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            for (ItemComponent but : buttonRow) {
                if (but instanceof Button butt) {
                    if (!buttons.contains(butt)) {
                        buttons.add(butt);
                    }
                }
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(exhaustedMessage).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons)).queue();
    }

    @ButtonHandler("setOrder")
    public static void setOrder(ButtonInteractionEvent event, Game game) {
        Helper.setOrder(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gain_CC")
    public static void gainCC(ButtonInteractionEvent event, Player player, Game game) {
        String message = "";

        String message2 = player.getRepresentationUnfogged() + ", your current command tokens are " + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("run_status_cleanup")
    public static void runStatusCleanup(ButtonInteractionEvent event, Game game, Player player) {
        StatusCleanupService.runStatusCleanup(game);
        ButtonHelper.deleteTheOneButton(event);
        ReactionService.addReaction(event, game, player, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
    }

    @ButtonHandler("ChooseDifferentDestination")
    public static void chooseDifferentDestination(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Choosing a different system to activate. Please select the ring of the map that the system you wish to activate is located in.";
        if (!game.isFowMode()) {
            message += " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
        }
        List<Button> ringButtons = ButtonHelper.getPossibleRings(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("willRevolution")
    public static void willRevolution(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("willRevolution", "active");
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Reversed strategy card picking order.");
    }

    public static void lastMinuteDeliberation(ButtonInteractionEvent event, Player player, Game game, MessageChannel actionsChannel) {
        ButtonHelper.deleteMessage(event);
        String message = player.getRepresentation() + " Click the names of up to 2 planets you wish to ready ";
        List<Button> buttons = Helper.getPlanetRefreshButtons(player, game);
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying Planets")); // spitItOut
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        AgendaHelper.revealAgenda(event, false, game, actionsChannel);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to ready 2 planets to the player who pressed the button.");
    }

    @ButtonHandler("draw_2_ACDelete")
    public static void draw2ACDelete(ButtonInteractionEvent event, Player player, Game game) {
        String message;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 2);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = "Drew 2 action cards with **Scheming**. Please discard 1 action card.";
        }
        ReactionService.addReaction(event, game, player, true, false, message);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + ", use buttons to discard an action card.",
            ActionCardHelper.getDiscardActionCardButtons(player, false));

        ButtonHelper.deleteMessage(event);
        ButtonHelper.checkACLimit(game, player);
    }

    @ButtonHandler("draw_1_ACDelete")
    public static void draw1ACDelete(ButtonInteractionEvent event, Player player, Game game) {
        String message;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = " drew 1 action card.";
        }
        ReactionService.addReaction(event, game, player, true, false, message);
        ButtonHelper.deleteMessage(event);
        ButtonHelper.checkACLimit(game, player);
    }

    @ButtonHandler("draw_1_AC")
    public static void draw1AC(ButtonInteractionEvent event, Player player, Game game) {
        String message;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = " drew 1 action card.";
        }
        ReactionService.addReaction(event, game, player, true, false, message);
        ButtonHelper.checkACLimit(game, player);
    }

    @ButtonHandler("confirm_cc")
    public static void confirmCC(ButtonInteractionEvent event, Game game, Player player) {
        String message = "Confirmed command tokens: " + player.getTacticalCC() + "/" + player.getFleetCC();
        if (!player.getMahactCC().isEmpty()) {
            message += "(+" + player.getMahactCC().size() + ")";
        }
        message += "/" + player.getStrategicCC();
        ReactionService.addReaction(event, game, player, true, false, message);
    }

    @ButtonHandler("shuffleExplores")
    public static void shuffleExplores(ButtonInteractionEvent event, Game game) {
        game.shuffleExplores();
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("temporaryPingDisable")
    public static void temporaryPingDisable(ButtonInteractionEvent event, Game game) {
        game.setTemporaryPingDisable(true);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Disabled autopings for this turn.");
        ButtonHelper.deleteMessage(event);
    }

    public static void declineExplore(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        ReactionService.addReaction(event, game, player, "declined exploration card.");
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(mainGameChannel, pF + " declined exploration card.");
        }
    }

    @ButtonHandler("mallice_convert_comm")
    public static void malliceConvertComm(ButtonInteractionEvent event, Player player, Game game) {
        String playerRep = player.getFactionEmoji();
        int commod = player.getCommodities();
        String message = playerRep + " exhausted Mallice ability to convert their " + commod
            + " commodit" + (commod == 1 ? "y" : "ies") + " to "
            + (commod == 1 ? "a trade good" : commod + " trade goods") + " (trade goods: "
            + player.getTg() + "->" + (player.getTg() + commod) + ").";
        player.setTg(player.getTg() + commod);
        player.setCommodities(0);
        if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mallice_2_tg")
    public static void mallice2tg(ButtonInteractionEvent event, Player player, Game game) {
        String playerRep = player.getFactionEmoji();
        String message = playerRep + " exhausted Mallice ability and gained trade goods " + player.gainTG(2) + ".";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 2);
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @Deprecated
    public static void gain1tgFromCommander(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " Gained 1 trade good " + player.gainTG(1) + " from their commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromMuaatCommander(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " Gained 1 trade good " + player.gainTG(1) + " from Magmus, the Muaat commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromLetnevCommander(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " Gained 1 trade good " + player.gainTG(1) + " from Rear Admiral Farran, the Letnev commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1TG(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = "";
        String labelP = event.getButton().getLabel();
        String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1);
        boolean failed = false;
        if (labelP.contains("inf") && labelP.contains("mech")) {
            message += ExploreHelper.checkForMechOrRemoveInf(planetName, game, player);
            failed = message.contains("Please try again.");
        }
        if (!failed) {
            message += "Gained 1 trade good " + player.gainTG(1, true) + ".";
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        ReactionService.addReaction(event, game, player, message);
        if (!failed) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(mainGameChannel, pF + " " + message);
            }
        }
    }

    @ButtonHandler("decrease_fleet_cc")
    public static void decreaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() - 1);
        String originalCCs = game
            .getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
            + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("decrease_tactic_cc")
    public static void decreaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() - 1);
        String originalCCs = game
            .getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
            + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("decrease_strategy_cc")
    public static void decreaseStrategyCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setStrategicCC(player.getStrategicCC() - 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs
            + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_fleet_cc")
    public static void increaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from "
            + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
        if (ButtonHelper.isLawInPlay(game, "regulations") && player.getFleetCC() > 4) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                + " reminder that under the _Fleet Regulations_ law, fleet pools are limited to 4 command tokens.");
        }
    }

    @ButtonHandler("resetCCs")
    public static void resetCCs(ButtonInteractionEvent event, Player player, Game game) {
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        ButtonHelper.resetCCs(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from "
            + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: 0.";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_tactic_cc")
    public static void increaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from "
            + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("exhauste6g0network")
    public static void exhaustE6G0Network(ButtonInteractionEvent event, Player player, Game game) {
        player.addExhaustedRelic("e6-g0_network");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " chose to exhaust _E6-G0 Network_.");
        String message;
        if (player.hasAbility("scheming")) {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            message = player.getFactionEmoji() + " drew 2 action cards with **Scheming**. Please discard 1 action card.";
            ActionCardHelper.sendActionCardInfo(game, player, event);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ActionCardHelper.getDiscardActionCardButtons(player, false));
        } else if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = player.getFactionEmoji() + " drew 1 action card.";
        }
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.checkACLimit(game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("resetProducedThings")
    public static void resetProducedThings(ButtonInteractionEvent event, Player player, Game game) {
        Helper.resetProducedUnits(player, game, event);
        event.getMessage().editMessage(Helper.buildProducedUnitsMessage(player, game)).queue();
    }

    @ButtonHandler("yssarilMinisterOfPolicy")
    public static void yssarilMinisterOfPolicy(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " is drawing their _Minister of Policy_ action card.");
        String message;
        if (player.hasAbility("scheming")) {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            message = player.getFactionEmoji() + " drew 2 action cards with **Scheming**. Please discard 1 action card.";
            ActionCardHelper.sendActionCardInfo(game, player, event);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", please use buttons to discard.",
                ActionCardHelper.getDiscardActionCardButtons(player, false));

        } else if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = player.getFactionEmoji() + " drew 1 action card.";
        }
        player.checkCommanderUnlock("yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.checkACLimit(game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("increase_strategy_cc")
    public static void increaseStrategyCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setStrategicCC(player.getStrategicCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from "
            + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("proceed_to_strategy")
    public static void proceedToStrategy(ButtonInteractionEvent event, Game game) {
        if (game.isOmegaPhaseMode() && PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please fill the priority track before starting the Strategy Phase.");
            PriorityTrackHelper.PrintPriorityTrack(game);
            return;
        }
        Map<String, Player> players = game.getPlayers();
        if (game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
            for (Player player_ : players.values()) {
                RefreshCardsService.refreshPlayerCards(game, player_, false);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), "All planets have been readied at the end of the agenda phase.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Did not automatically ready planets due to _Checks and Balances_ resolving \"against\"."
                    + " Players have been sent buttons to ready up to 3 planets.");
        }
        StartPhaseService.startStrategyPhase(event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("non_sc_draw_so")
    public static void nonSCDrawSO(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Drew A Secret Objective";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message += ". Drew a second secret objective due to **Plausible Deniability**.";
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("draw2 AC")
    public static void draw2AC(ButtonInteractionEvent event, Player player, Game game) {
        boolean hasSchemingAbility = player.hasAbility("scheming");
        String message = hasSchemingAbility
            ? "Drew 3 Action Cards (Scheming) - please discard 1 action card from your hand"
            : "Drew 2 Action cards";
        int count = hasSchemingAbility ? 3 : 2;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";

        } else {
            for (int i = 0; i < count; i++) {
                game.drawActionCard(player.getUserID());
            }
            ActionCardHelper.sendActionCardInfo(game, player, event);
            ButtonHelper.checkACLimit(game, player);
        }

        ReactionService.addReaction(event, game, player, message);
        if (hasSchemingAbility) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ActionCardHelper.getDiscardActionCardButtons(player, false));
        }
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("diploSystem")
    public static void diploSystem(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + " Choose the system you wish to diplo";
        List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
    }

    @ButtonHandler("redistributeCCButtons") // Buttons.REDISTRIBUTE_CCs
    public static void redistributeCCButtons(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + "! Your current command tokens are " + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());

        String finChecker = player.finChecker();
        Button getTactic = Buttons.green(finChecker + "increase_tactic_cc", "Gain 1 Tactic Token");
        Button getFleet = Buttons.green(finChecker + "increase_fleet_cc", "Gain 1 Fleet Token");
        Button getStrat = Buttons.green(finChecker + "increase_strategy_cc", "Gain 1 Strategy Token");
        Button loseTactic = Buttons.red(finChecker + "decrease_tactic_cc", "Lose 1 Tactic Token");
        Button loseFleet = Buttons.red(finChecker + "decrease_fleet_cc", "Lose 1 Fleet Token");
        Button loseStrat = Buttons.red(finChecker + "decrease_strategy_cc", "Lose 1 Strategy Token");
        Button doneGainingCC = Buttons.red(finChecker + "deleteButtons", "Done Redistributing Command Tokens");
        Button resetCC = Buttons.gray(finChecker + "resetCCs", "Reset Command Tokens");

        List<Button> buttons = Arrays.asList(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat, doneGainingCC, resetCC);
        if (player.hasAbility("deliberate_action") && game.getPhaseOfGame().contains("status")) {
            buttons = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
        }
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }

        if (!game.isFowMode() && "statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
            ReactionService.addReaction(event, game, player);
        }

        if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
            boolean cyber = false;
            for (String pn : player.getPromissoryNotes().keySet()) {
                if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                    cyber = true;
                }
            }
            if (player.hasAbility("versatile") || player.hasTech("hm") || cyber) {
                int properGain = 2;
                String reasons = "";
                if (player.hasAbility("versatile")) {
                    properGain += 1;
                    reasons = "**Versatile** ";
                }
                if (player.hasTech("hm")) {
                    properGain += 1;
                    reasons += "_Hypermetabolism_ ";
                }
                if (cyber) {
                    properGain += 1;
                    reasons += "_Cybernetic Enhancements_ ";
                }
                if (properGain > 2) {
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Heads up " + player.getRepresentationUnfogged()
                        + ", the bot thinks you should gain " + properGain + " command token" + (properGain == 1 ? "" : "s") + " now due to: " + reasons + ".");
                }
            }
            if (game.isCcNPlasticLimit()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "Your highest fleet count in a system is currently "
                        + ButtonHelper.checkFleetInEveryTile(player, game, event)
                        + ". That's how many command tokens you'll need to retain in your fleet pool to avoid removing ships.");
            }
        }
    }

    @ButtonHandler("endOfTurnAbilities")
    public static void endOfTurnAbilities(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "Use buttons to do an end of turn ability";
        List<Button> buttons = ButtonHelper.getEndOfTurnAbilities(player, game);
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("startStrategyPhase")
    public static void startStrategyPhase(ButtonInteractionEvent event, Game game) {
        if (game.isOmegaPhaseMode() && PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please fill the priority track before starting the Strategy Phase.");
            PriorityTrackHelper.PrintPriorityTrack(game);
            return;
        }
        StartPhaseService.startPhase(event, game, "strategy");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("deployTyrant")
    public static void deployTyrant(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to place the _Tyrant's Lament_ with your ships.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
            Helper.getTileWithShipsPlaceUnitButtons(player, game, "tyrantslament", "placeOneNDone_skipbuild"));
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " is deploying the _Tyrant's Lament_.");
        player.addOwnedUnitByID("tyrantslament");
    }

    @ButtonHandler("nekroTechExhaust")
    public static void nekroTechExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button doneExhausting = Buttons.red("deleteButtons_technology", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("leadershipExhaust")
    public static void leadershipExhaust(ButtonInteractionEvent event, Player player, Game game) {
        ReactionService.addReaction(event, game, player);
        String message = player.getRepresentationUnfogged() + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons_leadership", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
    }

    @ButtonHandler("turnOffForcedScoring")
    public static void turnOffForcedScoring(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), game.getPing() + " Forced scoring order has been turned off. Any queues will not be resolved.");
        game.setStoredValue("forcedScoringOrder", "");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("forceACertainScoringOrder")
    public static void forceACertainScoringOrder(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), game.getPing()
            + ", players will be forced to score in order. Players will not be prevented from declaring they don't score, and are in fact encouraged to do so without delay if that is the case."
            + " This forced scoring order also does not yet affect secret objectives, it only restrains public objectives.");
        game.setStoredValue("forcedScoringOrder", "true");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("passForRound")
    public static void passForRound(ButtonInteractionEvent event, Player player, Game game) {
        PassService.passPlayerForRound(event, game, player, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("no_sabotage")
    public static void noSabotage(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "No Sabotage" : null;
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler(Constants.SO_NO_SCORING)
    public static void soNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation() + " has opted not to score a secret objective at this point in time.";
        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "None");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        String key2 = "queueToScoreSOs";
        String key3 = "potentialScoreSOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2,
                game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3,
                game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            String key3b = "potentialScorePOBlockers";
            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                Helper.resolvePOScoringQueue(game, event);
                // Helper.resolveSOScoringQueue(game, event);
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
            String msg = "Please score objectives.";
            msg += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue();
        }
    }

    @ButtonHandler(value = "refreshStatusSummary", save = false)
    public static void refreshStatusSummary(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "Please score objectives.";
        msg += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
        event.getMessage().editMessage(msg).queue();
    }

    @ButtonHandler("acquireAFreeTech") // Buttons.GET_A_FREE_TECH
    public static void acquireAFreeTech(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String finsFactionCheckerPrefix = player.getFinsFactionCheckerPrefix();
        game.setComponentAction(true);
        buttons.add(Buttons.blue(finsFactionCheckerPrefix + "getAllTechOfType_propulsion_noPay", "Get a Propulsion Technology", TechEmojis.PropulsionTech));
        buttons.add(Buttons.green(finsFactionCheckerPrefix + "getAllTechOfType_biotic_noPay", "Get a Biotic Technology", TechEmojis.BioticTech));
        buttons.add(Buttons.gray(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic_noPay", "Get a Cybernetic Technology", TechEmojis.CyberneticTech));
        buttons.add(Buttons.red(finsFactionCheckerPrefix + "getAllTechOfType_warfare_noPay", "Get a Warfare Technology", TechEmojis.WarfareTech));
        buttons.add(Buttons.gray(finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade_noPay", "Get A Unit Upgrade Technology", TechEmojis.UnitUpgradeTech));
        String message = player.getRepresentation() + ", please choose what type of technology you wish to get?";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(Constants.PO_NO_SCORING)
    public static void poNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        // AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
        String message = player.getRepresentation() + " has opted not to score a public objective at this point in time.";
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "None");
        String reply = game.isFowMode() ? "No public objective scored" : null;
        ReactionService.addReaction(event, game, player, reply);
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2,
                game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            String key3b = "potentialScoreSOBlockers";
            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                Helper.resolvePOScoringQueue(game, event);
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
            String msg = "Please score objectives.";
            msg += "\n\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue();
        }
    }

    @ButtonHandler("drawActionCards_")
    public static void drawActionCards(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        try {
            int count = Integer.parseInt(buttonID.replace("drawActionCards_", ""));
            ActionCardHelper.drawActionCards(game, player, count, true);
            ButtonHelper.deleteTheOneButton(event);
        } catch (Exception ignored) {}
    }

    @ButtonHandler("applytempcombatmod__" + Constants.AC + "__")
    public static void applytempcombatmodAC(ButtonInteractionEvent event, Player player, String buttonID) {
        String acAlias = buttonID.substring(buttonID.lastIndexOf("__") + 2);
        TemporaryCombatModifierModel combatModAC = CombatTempModHelper.getPossibleTempModifier(Constants.AC,
            acAlias,
            player.getNumberOfTurns());
        if (combatModAC != null) {
            player.addNewTempCombatMod(combatModAC);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Combat modifier will be applied next time you push the combat roll button.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("applytempcombatmod__" + "tech" + "__")
    public static void applytempcombatmodtech(ButtonInteractionEvent event, Player player) {
        String acAlias = "sc";
        TemporaryCombatModifierModel combatModAC = CombatTempModHelper.getPossibleTempModifier("tech", acAlias,
            player.getNumberOfTurns());
        if (combatModAC != null) {
            player.addNewTempCombatMod(combatModAC);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji()
                    + " +1 modifier will be applied the next time you push the combat roll button due to _Supercharge_.");
        }
        player.exhaustTech("sc");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("rollIxthian")
    public static void rollIxthian(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (game.getSpeakerUserID().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
            AgendaHelper.rollIxthian(game, true);
        } else {
            Button ixthianButton = Buttons.green("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact", PlanetEmojis.Mecatol);
            String msg = "The speaker should roll for Ixthain Artifact. Click this button to roll anyway!";
            MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg, ixthianButton);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("removeCCFromBoard_")
    public static void removeCCFromBoard(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.resolveRemovingYourCC(player, game, event, buttonID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("refreshLandingButtons")
    public static void refreshLandingButtons(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    @ButtonHandler("useTA_")
    public static void useTA(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String ta = buttonID.replace("useTA_", "") + "_ta";
        PromissoryNoteHelper.resolvePNPlay(ta, player, game, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mahactCommander")
    public static void mahactCommander(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "mahactCommander");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("primaryOfWarfare")
    public static void primaryOfWarfare(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "warfare");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
    }

    @ButtonHandler("comm_for_AC")
    public static void commForAC(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        boolean hasSchemingAbility = player.hasAbility("scheming");
        int count2 = hasSchemingAbility ? 2 : 1;
        String commOrTg = "";
        if (player.getCommodities() > 0) {
            commOrTg = "commodity";
            player.setCommodities(player.getCommodities() - 1);

        } else if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
            commOrTg = "trade good";
        } else {
            ReactionService.addReaction(event, game, player, "Didn't have any commodities or trade goods to spend, so no action card was drawn.");
        }
        String message = hasSchemingAbility
            ? "Spent 1 " + commOrTg + " to draw " + count2 + " action card (**Scheming** added 1 action card). Please discard 1 action card from your hand."
            : "Spent 1 " + commOrTg + " to draw " + count2 + " action card.";
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count2);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            for (int i = 0; i < count2; i++) {
                game.drawActionCard(player.getUserID());
            }
            ButtonHelper.checkACLimit(game, player);
            ActionCardHelper.sendActionCardInfo(game, player, event);
        }

        CommanderUnlockCheckService.checkPlayer(player, "yssaril");

        if (hasSchemingAbility) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard.",
                ActionCardHelper.getDiscardActionCardButtons(player, false));
        }

        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), pF + " " + message);
        }
    }

    @ButtonHandler("drawAgenda_2")
    public static void drawAgenda2(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.getStoredValue("hasntSetSpeaker").isEmpty() && !game.isHomebrewSCMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged() + " you need to assign speaker first before drawing agendas. You can override this restriction with `/agenda draw`");
            return;
        }
        AgendaHelper.drawAgenda(2, game, player);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, false) + " drew 2 agendas");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startOfGameObjReveal")
    public static void startOfGameObjReveal(ButtonInteractionEvent event, Game game, Player player) {
        for (Player p : game.getRealPlayers()) {
            if (p.getSecrets().size() > 1 && !game.isExtraSecretMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please ensure everyone has discarded secret objectives before hitting this button. ");
                return;
            }
        }

        Player speaker = null;
        if (game.getPlayer(game.getSpeakerUserID()) != null) {
            speaker = game.getPlayers().get(game.getSpeakerUserID());
        }
        if (speaker == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please assign speaker before hitting this button.");
            ButtonHelper.offerSpeakerButtons(game, player);
            return;
        }
        if (game.isOmegaPhaseMode() && PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please fill the priority track before revealing public objectives.");
            PriorityTrackHelper.PrintPriorityTrack(game);
            return;
        }
        RevealPublicObjectiveService.revealTwoStage1(game);
        StartPhaseService.startStrategyPhase(event, game);
        PlayerPreferenceHelper.offerSetAutoPassOnSaboButtons(game, null);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dsdihmy_")
    public static void dsDihmhonYellowTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperFactionSpecific.resolveImpressmentPrograms(buttonID, event, game, player);
    }

    @ButtonHandler("swapToFaction_")
    public static void swapToFaction(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("swapToFaction_", "");
        SwapFactionService.secondHalfOfSwap(game, player, game.getPlayerFromColorOrFaction(faction), event.getUser(), event);
    }
}
