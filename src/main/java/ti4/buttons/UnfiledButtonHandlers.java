package ti4.buttons;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;
import ti4.buttons.handlers.phases.TurnEndButtonHandler;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.ExploreHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.ObjectiveHelper;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StatusHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.listeners.annotations.ButtonHandler;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.StatusCleanupService;
import ti4.service.abilities.MahactTokenService;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.breakthrough.AutoFactoriesService;
import ti4.service.breakthrough.EidolonMaximumService;
import ti4.service.breakthrough.TheIconService;
import ti4.service.button.ReactionService;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.fow.LoreService;
import ti4.service.game.EndGameService;
import ti4.service.game.StartPhaseService;
import ti4.service.game.SwapFactionService;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.objectives.ScorePublicObjectiveService;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.planet.PlanetService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.tactical.TacticalActionService;
import ti4.service.turn.PassService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class UnfiledButtonHandlers {

    @ButtonHandler("declareUse_")
    public static void declareUse(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String msg = player.getFactionEmojiOrColor() + " is using " + buttonID.split("_")[1];
        if (msg.contains("Vaylerian")) {
            msg = player.getFactionEmojiOrColor()
                    + " is using Pyndil Gonsuul, the Vaylerian commander, to add +2 capacity to a ship with capacity.";
        }
        if (msg.contains("Tnelis")) {
            msg = player.getFactionEmojiOrColor() + " is using Fillipo Rois, the Tnelis commander,"
                    + " producing a hit against 1 of their __non-fighter__ ships in the system to give __one__ of their ships a +1 move boost."
                    + "\n-# This ability may only be used once per activation.";
            String pos = buttonID.split("_")[2];
            List<Button> buttons =
                    ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + ", use buttons to assign 1 hit.",
                    buttons);
            game.setStoredValue("tnelisCommanderTracker", player.getFaction());
        }
        if (msg.contains("Ghemina")) {
            msg = player.getFactionEmojiOrColor()
                    + " is using Jarl Vel & Jarl Jotrun, the Ghemina commanders, to gain 1 trade good after winning the space combat.";
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
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("unlockCommander_")
    public static void unlockCommander(ButtonInteractionEvent event, Player player, String buttonID) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        CommanderUnlockCheckService.checkPlayer(player, buttonID.split("_")[1]);
    }

    @ButtonHandler("fogAllianceAgentStep3_")
    public static void fogAllianceAgentStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperHeroes.argentHeroStep3(game, player, buttonID);
    }

    @ButtonHandler("genericRemove_")
    public static void genericRemove(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericRemove_", "");

        game.getTacticalActionDisplacement().clear();
        List<Button> systemButtons = ButtonHelperTacticalAction.getButtonsForAllUnitsInSystem(
                player, game, game.getTileByPosition(pos), "Remove");

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Removing units from " + game.getTileByPosition(pos).getRepresentationForButtons(game, player) + ".");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please choose the units you wish to remove.", systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doActivation_")
    public static void doActivation(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("doActivation_", "");
        ButtonHelper.resolveOnActivationEnemyAbilities(game, game.getTileByPosition(pos), player, false, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("planetAbilityExhaust_")
    public static void planetAbilityExhaust(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("planetAbilityExhaust_", "");
        PlanetExhaustAbility.doAction(event, player, planet, game, true);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("genericBuild_")
    public static void genericBuild(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericBuild_", "");
        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "genericBuild", "place");
        String message = player.getRepresentation() + ", use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericModifyAllTiles_")
    public static void genericModifyAllTiles(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModifyAllTiles_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), "genericModifyAllTiles", "place");
        String message = player.getRepresentation() + ", use the buttons to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericModify_")
    public static void genericModify(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModify_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.offerBuildOrRemove(player, game, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("genericReact")
    public static void genericReact(ButtonInteractionEvent event, Game game, Player player) {
        String message = game.isFowMode() ? "Turned down window" : null;
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("produceOneUnitInTile_")
    public static void produceOneUnitInTile(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        buttonID = buttonID.replace("produceOneUnitInTile_", "");
        String type = buttonID.split("_")[1];
        String pos = buttonID.split("_")[0];
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation() + ", use the buttons to produce 1 unit.\n> "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("removeAllStructures_")
    public static void removeAllStructures(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        plan.removeAllUnits(player.getColor());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Removed all units on " + planet + " for " + player.getRepresentation() + ".");
        AddPlanetToPlayAreaService.addPlanetToPlayArea(event, game.getTileFromPlanet(planet), planet, game);
    }

    @ButtonHandler("sandbagPref_")
    public static void sandbagPref(ButtonInteractionEvent event, Player player, String buttonID) {
        var userSettings = UserSettingsManager.get(player.getUserID());
        userSettings.setSandbagPref(buttonID.split("_")[1]);
        UserSettingsManager.save(userSettings);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Decision saved.");
    }

    @ButtonHandler("setAutoPassMedian_")
    public static void setAutoPassMedian(ButtonInteractionEvent event, Player player, String buttonID) {
        String hours = buttonID.split("_")[1];
        int median = Integer.parseInt(hours);
        player.setAutoSaboPassMedian(median);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set median time to " + median + " hours.");
        var userSettings = UserSettingsManager.get(player.getUserID());
        userSettings.setAutoNoSaboInterval(median);
        UserSettingsManager.save(userSettings);
        if (median > 0) {
            if (!player.hasAbility("quash")
                    && !player.ownsPromissoryNote("rider")
                    && !player.getPromissoryNotes().containsKey("riderm")
                    && !player.hasAbility("radiance")
                    && !player.hasAbility("galactic_threat")
                    && !player.hasAbility("conspirators")
                    && !player.ownsPromissoryNote("riderx")
                    && !player.ownsPromissoryNote("riderm")
                    && !player.ownsPromissoryNote("ridera")
                    && !player.hasTechReady("gr")) {
                if (!userSettings.isPrefersPassOnWhensAfters()) {
                    List<Button> buttons = new ArrayList<>();
                    String msg = player.getRepresentation()
                            + ", the bot may also auto react for you when you have no \"when\"s or \"after\"s."
                            + " Default for this is off. This will only apply to this game."
                            + " If you have any \"when\"s or \"after\"s or related \"when\"/\"after\" abilities, it will not do anything. ";
                    buttons.add(Buttons.green("playerPrefDecision_true_agenda", "Turn on"));
                    buttons.add(Buttons.green("playerPrefDecision_false_agenda", "Turn off"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
                } else {
                    player.setAutoPassOnWhensAfters(true);
                }
            }
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getReleaseButtons")
    public static void getReleaseButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged()
                        + ", you may release units one at a time with the buttons. Reminder that captured units may only be released as part of an ability or a transaction.",
                ButtonHelperFactionSpecific.getReleaseButtons(player, game));
    }

    @ButtonHandler("shroudOfLithStart")
    public static void shroudOfLithStart(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Select up to 2 ships and 2 ground forces to place in the space area.",
                ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game));
    }

    @ButtonHandler("useTech_")
    public static void useTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tech = buttonID.replace("useTech_", "");
        TechnologyModel techModel = Mapper.getTech(tech);
        if (!"st".equalsIgnoreCase(tech)) {
            String useMessage =
                    player.getRepresentation() + " used the _" + techModel.getRepresentation(false) + "_ technology.";
            if (game.isShowFullComponentTextEmbeds()) {
                MessageHelper.sendMessageToChannelWithEmbed(
                        event.getMessageChannel(), useMessage, techModel.getRepresentationEmbed());
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), useMessage);
            }
        }
        switch (tech) {
            case "st" -> { // Sarween Tools
                player.addSpentThing("sarween");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
                player.setSarweenCounter(player.getSarweenCounter() + 1);
                String msg =
                        player.getFactionEmoji() + " has used _Sarween Tools_ to save " + player.getSarweenCounter()
                                + " resource" + (player.getSarweenCounter() == 1 ? "" : "s") + " in this game so far. ";
                int result = ThreadLocalRandom.current().nextInt(0, 5);
                var userSettings = UserSettingsManager.get(player.getUserID());

                if (userSettings.isPrefersSarweenMsg()) {
                    if (player.getSarweenCounter() < 6) {

                        List<String> lameMessages = Arrays.asList(
                                "Not too impressive.",
                                "The technology has not yet proven its worth.",
                                "There better be more savings to come.",
                                "Your faction's stockholders are so far unimpressed.",
                                "Perhaps _AI Development Algorithm_ or _Scanlink Drone Network_ might have been more useful?");
                        msg += lameMessages.get(result);
                    } else if (player.getSarweenCounter() < 11) {
                        List<String> lameMessages = Arrays.asList(
                                "Not too shabby.",
                                "The technology is finally starting to justify its existence.",
                                "Hopefully there are still even more savings to come.",
                                "Your faction's stockholders are satisfied with the results of this technology.",
                                "Some folks still think _Scanlink Drone Network_ might have been more useful.");
                        msg += lameMessages.get(result);
                    } else if (player.getSarweenCounter() < 16) {
                        List<String> lameMessages = Arrays.asList(
                                "Very impressive.",
                                "If only all technology was this productive.",
                                "Surely there can't be even more savings to come?",
                                "Your faction's stockholders are ecstatic.",
                                "The _Scanlink Drone Network_ stans have been thoroughly shamed.");
                        msg += lameMessages.get(result);
                    } else {
                        List<String> lameMessages = Arrays.asList(
                                "Words cannot adequately express how impressive this is.",
                                "Is _Sarween Tools_ the best technology‽",
                                "Is this much saving even legal? The international IRS will be doing an audit on your paperwork sometime soon.",
                                "Your faction's stockholders have erected a statue of you in the city center.",
                                "Keep this up and we'll have to make a new channel, called \"Sarween Streaks\", just for your numbers.");
                        msg += lameMessages.get(result);
                    }
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), msg);
                event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
            }
            case "tf-sledfactories" -> { // Sarween Tools
                player.addSpentThing("sledfactories");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);

                event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
            }
            case "absol_st" -> { // Absol's Sarween Tools
                player.addSpentThing("absol_sarween");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
                event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
            }
            case "absol_pa" -> { // Absol's Psychoarcheology
                List<Button> absolPAButtons = new ArrayList<>();
                absolPAButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard", CardEmojis.getACEmoji(game)));
                for (String planetID : player.getReadiedPlanets()) {
                    Planet planet = ButtonHelper.getUnitHolderFromPlanetName(planetID, game);
                    if (planet != null && isNotBlank(planet.getOriginalPlanetType())) {
                        List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
                        absolPAButtons.addAll(planetButtons);
                    }
                }
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", use buttons to discard 2 action cards to explore a readied planet.",
                        absolPAButtons);
            }
        }
    }

    private static String getBestBombardablePlanet(Player player, Game game, Tile tile) {
        String best = "";
        for (String planet : getBombardablePlanets(player, game, tile)) {
            best = planet;
            for (Player p2 : game.getRealPlayers()) {
                if (ButtonHelper.getNumberOfGroundForces(p2, game.getUnitHolderFromPlanet(planet)) > 0) {
                    return best;
                }
            }
        }
        return best;
    }

    public static void autoAssignAllBombardmentToAPlanet(Player player, Game game) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        game.removeStoredValue("assignedBombardment" + player.getFaction());
        if (tile == null) {
            return;
        }
        Map<UnitModel, Integer> bombardUnits = CombatRollService.getUnitsInBombardment(tile, player, null);
        String planet = getBestBombardablePlanet(player, game, tile);
        for (Map.Entry<UnitModel, Integer> entry : bombardUnits.entrySet()) {
            for (int x = 0; x < entry.getValue(); x++) {
                String name = entry.getKey().getAsyncId() + "_" + x;

                String assignedUnit = name + "_" + planet;
                game.setStoredValue(
                        "assignedBombardment" + player.getFaction(),
                        game.getStoredValue("assignedBombardment" + player.getFaction()) + assignedUnit + ";");
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            game.setStoredValue(
                    "assignedBombardment" + player.getFaction(),
                    game.getStoredValue("assignedBombardment" + player.getFaction()) + "plasma_99_" + planet + ";");
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            game.setStoredValue(
                    "assignedBombardment" + player.getFaction(),
                    game.getStoredValue("assignedBombardment" + player.getFaction()) + "argentcommander_99_" + planet
                            + ";");
        }
    }

    private static List<Button> getBombardmentAssignmentButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            return buttons;
        }
        Map<UnitModel, Integer> bombardUnits = CombatRollService.getUnitsInBombardment(tile, player, null);
        String assignedUnits = game.getStoredValue("assignedBombardment" + player.getFaction());
        List<String> usedLabels = new ArrayList<>();
        for (Map.Entry<UnitModel, Integer> entry : bombardUnits.entrySet()) {
            UnitModel mod = entry.getKey();
            for (int x = 0; x < entry.getValue(); x++) {
                String name = mod.getAsyncId() + "_" + x;
                if (assignedUnits.contains(name)) {
                    for (String assignedUnit : assignedUnits.split(";")) {

                        if (assignedUnit.contains(name)) {
                            String planet = assignedUnit.split("_")[2];
                            String label = "Unassign " + capitalize(mod.getBaseType()) + " From "
                                    + Helper.getPlanetRepresentationNoResInf(planet, game);
                            if (!usedLabels.contains(label)) {
                                buttons.add(
                                        Buttons.red("unassignBombardUnit_" + assignedUnit, label, mod.getUnitEmoji()));
                                usedLabels.add(label);
                            }
                        }
                    }
                } else {
                    for (String planet : getBombardablePlanets(player, game, tile)) {
                        String label = "Assign " + capitalize(mod.getBaseType()) + " To "
                                + Helper.getPlanetRepresentationNoResInf(planet, game);
                        if (!usedLabels.contains(label)) {
                            buttons.add(Buttons.green(
                                    "assignBombardUnit_" + name + "_" + planet, label, mod.getUnitEmoji()));
                            usedLabels.add(label);
                        }
                    }
                }
            }
        }
        if (player.hasTech("ps") || player.hasTech("absol_ps")) {
            if (assignedUnits.contains("plasma")) {
                for (String assignedUnit : assignedUnits.split(";")) {
                    if (assignedUnit.contains("plasma")) {
                        String planet = assignedUnit.split("_")[2];
                        buttons.add(Buttons.red(
                                "unassignBombardUnit_" + assignedUnit,
                                "Unassign Plasma Scoring Die From "
                                        + Helper.getPlanetRepresentationNoResInf(planet, game),
                                TechEmojis.WarfareTech));
                    }
                }
            } else {
                for (String planet : getBombardablePlanets(player, game, tile)) {
                    buttons.add(Buttons.green(
                            "assignBombardUnit_plasma_99_" + planet,
                            "Assign Plasma Scoring Die To " + Helper.getPlanetRepresentationNoResInf(planet, game),
                            TechEmojis.WarfareTech));
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander") || player.hasTech("tf-zealous")) {
            if (assignedUnits.contains("argent")) {
                for (String assignedUnit : assignedUnits.split(";")) {
                    if (assignedUnit.contains("argent")) {
                        String planet = assignedUnit.split("_")[2];
                        buttons.add(Buttons.red(
                                "unassignBombardUnit_" + assignedUnit,
                                "Unassign Argent Commander Die from "
                                        + Helper.getPlanetRepresentationNoResInf(planet, game),
                                FactionEmojis.Argent));
                    }
                }
            } else {
                for (String planet : getBombardablePlanets(player, game, tile)) {
                    buttons.add(Buttons.green(
                            "assignBombardUnit_argentcommander_99_" + planet,
                            "Assign Argent Commander Die to " + Helper.getPlanetRepresentationNoResInf(planet, game),
                            FactionEmojis.Argent));
                }
            }
        }
        buttons.add(Buttons.blue(
                "combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment + "_deleteTheseButtons",
                "Done Assigning"));
        // buttons.add(Buttons.blue("doneAssigningBombard", "Done Assigning"));
        return buttons;
    }

    @ButtonHandler("unassignBombardUnit_")
    public static void unassignBombardUnit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String assignedUnit = buttonID.replace("unassignBombardUnit_", "");
        game.setStoredValue(
                "assignedBombardment" + player.getFaction(),
                game.getStoredValue("assignedBombardment" + player.getFaction())
                        .replace(assignedUnit, "")
                        .replace(";;", ";"));
        List<Button> buttons = getBombardmentAssignmentButtons(player, game);
        event.getMessage()
                .editMessage(getBombardmentSummary(player, game))
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("assignBombardUnit_")
    public static void assignBombardUnit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String assignedUnit = buttonID.replace("assignBombardUnit_", "");
        game.setStoredValue(
                "assignedBombardment" + player.getFaction(),
                game.getStoredValue("assignedBombardment" + player.getFaction()) + assignedUnit + ";");
        List<Button> buttons = getBombardmentAssignmentButtons(player, game);
        event.getMessage()
                .editMessage(getBombardmentSummary(player, game))
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static List<String> getBombardablePlanets(Player player, Game game, Tile tile) {
        List<String> planets = new ArrayList<>();
        for (Planet planetUH : tile.getPlanetUnitHolders()) {
            if (!player.getPlanetsAllianceMode().contains(planetUH.getName())
                    || FoWHelper.otherPlayersHaveUnitsOnPlanet(player, planetUH)) {
                if (!planetUH.getPlanetTypes().contains("cultural") || !ButtonHelper.isLawInPlay(game, "conventions")) {
                    planets.add(planetUH.getName());
                }
            }
        }

        return planets;
    }

    private static String getBombardmentSummary(Player player, Game game) {
        StringBuilder summary = new StringBuilder();
        String assignedUnits = game.getStoredValue("assignedBombardment" + player.getFaction());
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            return summary.toString();
        }
        for (String planet : getBombardablePlanets(player, game, tile)) {
            summary.append("### ")
                    .append(player.fogSafeEmoji())
                    .append(" BOMBARDMENT of ")
                    .append(Helper.getPlanetRepresentationNoResInf(planet, game))
                    .append(":\n");
            for (Player p2 : game.getRealAndEliminatedPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (FoWHelper.playerHasUnitsOnPlanet(p2, game.getUnitHolderFromPlanet(planet))) {
                    summary.append("-# ")
                            .append(p2.fogSafeEmoji())
                            .append(" currently has ")
                            .append(ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(
                                    game, p2, planet))
                            .append('\n');
                    break;
                }
            }

            for (String assignedUnit : assignedUnits.split(";")) {
                if (assignedUnit.endsWith(planet)) {
                    if (assignedUnit.contains("99")) {
                        if (assignedUnit.contains("argent")) {
                            summary.append("- Trrakan Aun Zulok die\n");
                        } else {
                            summary.append("- _Plasma Scoring_ die\n");
                        }
                    } else {
                        String asyncID = assignedUnit.split("_")[0];
                        UnitModel mod = player.getUnitFromAsyncID(asyncID);
                        summary.append("- ").append(mod.getUnitEmoji()).append('\n');
                    }
                }
            }
        }
        return summary.toString();
    }

    @ButtonHandler("bombardConfirm_")
    public static void bombardConfirm(ButtonInteractionEvent event, Player player, Game game) {
        if (game.getActiveSystem() == null) {
            return;
        }
        if (game.getTileByPosition(game.getActiveSystem()).isScar()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation()
                            + ", you cannot use BOMBARDMENT (or any other unit abilities) in an entropic scar.");
            return;
        }
        if (getBombardablePlanets(player, game, game.getTileByPosition(game.getActiveSystem()))
                .isEmpty()) {
            String message = player.getRepresentation()
                    + ", there are no planets in this system that you can legally use BOMBARDMENT against. "
                    + "You cannot use BOMBARDMENT against planets you own";
            message += ButtonHelper.isLawInPlay(game, "conventions")
                    ? ", and you cannot bombard cultural planets while the _Conventions of War_ law is in play."
                    : ".";
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            return;
        }

        autoAssignAllBombardmentToAPlanet(player, game);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentation() + " is assigning units to bombard as follows:\n"
                        + getBombardmentSummary(player, game),
                getBombardmentAssignmentButtons(player, game));
    }

    @ButtonHandler("finishComponentAction_")
    public static void finishComponentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), event.getMessage().getContentRaw());
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reduceComm_")
    public static void reduceComm(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        player.getFactionEmojiOrColor();
        String message;

        if (tgLoss > player.getCommodities()) {
            message = "You don't have " + tgLoss + " commodit" + (tgLoss == 1 ? "y" : "ies") + ". No change made.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            player.setCommodities(player.getCommodities() - tgLoss);
            player.addSpentThing("comm_" + tgLoss);
        }
        String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        Leader playerLeader = player.getLeader("keleresagent").orElse(null);
        if (playerLeader != null && !playerLeader.isExhausted()) {
            playerLeader.setExhausted(true);
            String messageText =
                    player.getRepresentation() + " exhausted " + Helper.getLeaderFullRepresentation(playerLeader) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), messageText);
        }
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("reduceTG_")
    public static void reduceTG(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);

        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        if (tgLoss > player.getTg()) {
            String message =
                    "You don't have " + tgLoss + " trade good" + (tgLoss == 1 ? "" : "s") + ". No change made.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            player.setTg(player.getTg() - tgLoss);
            player.increaseTgsSpentThisWindow(tgLoss);
        }
        if (tgLoss > player.getTg()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
        String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
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
                String msg = player.getRepresentation() + " added 1 infantry to "
                        + Helper.getPlanetRepresentation(planetName, game) + " due to the _Arcane Citadel_.";
                AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            if (uH.getTokenList().contains("attachment_facilitylogisticshub.png")) {
                String msg = player.getRepresentation() + " gained 1 commodity due to exhausting "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " while it had a _Logistics Hub Facility_.";
                player.setCommodities(player.getCommodities() + 1);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            if (uH.getTokenList().contains("attachment_facilityresearchlab.png")) {
                int amountThereNow = game.changeCommsOnPlanet(1, planetName);

                String msg =
                        player.getRepresentation() + " gained 1 trade good on the _Research Lab_ due to exhausting "
                                + Helper.getPlanetRepresentation(planetName, game)
                                + ". It now has " + amountThereNow
                                + " trade good" + (amountThereNow == 1 ? "" : "s") + " on it.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        if (whatIsItFor.contains("tech") && player.hasAbility("ancient_knowledge")) {
            if ((Mapper.getPlanet(planetName).getTechSpecialties() != null
                            && !Mapper.getPlanet(planetName)
                                    .getTechSpecialties()
                                    .isEmpty())
                    || ButtonHelper.checkForTechSkips(game, planetName)) {
                String msg = player.getRepresentation()
                        + " due to your **Ancient Knowledge** ability, you may be eligible to receive a commodity here if you exhausted this planet ("
                        + planetName
                        + ") for its technology speciality.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm));
                buttons.add(Buttons.red("deleteButtons", "N/A"));
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji()
                                + " may have the opportunity to gain a commodity from their **Ancient Knowledge** ability due to exhausting a technology speciality planet.");
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("getRepairButtons_")
    public static void getRepairButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("getRepairButtons_", "");
        List<Button> buttons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentationUnfogged() + ", use buttons to resolve.", buttons);
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
            List<Button> buttons = StartCombatService.getGeneralCombatButtons(game, pos, p1, p2, groundOrSpace);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
        } catch (IOException e) {
            BotLogger.error(new LogOrigin(event), "Failed to close FileUpload", e);
        }
    }

    @ButtonHandler("refresh_")
    public static void refresh(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        Player p2 = player;
        if (countMatches(buttonID, "_") > 1) {
            String faction = buttonID.split("_")[2];
            p2 = game.getPlayerFromColorOrFaction(faction);
        }

        PlanetService.refreshPlanet(p2, planetName);
        String totalVotesSoFar = event.getMessage().getContentRaw();
        if (totalVotesSoFar.contains("Readied")) {
            totalVotesSoFar += ", " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        } else {
            totalVotesSoFar = player.getFactionEmojiOrColor() + " Readied "
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        }
        event.getMessage().editMessage(totalVotesSoFar).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // @ButtonHandler("strategicAction_")
    public static void strategicAction(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel mainGameChannel) {
        int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
        PlayStrategyCardService.playSC(event, scNum, game, mainGameChannel, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("toldarPN")
    public static void toldarPN(ButtonInteractionEvent event, Player player) {
        player.setCommodities(player.getCommodities() + 3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " used _Concordat Allegiant_ (the Toldar promissory note)"
                        + " to gain 3 commodities after winning a combat against someone with more victory points than them. They can do this once per action. Their currently hold "
                        + player.getCommodities() + " commodit" + (player.getCommodities() == 1 ? "y" : "ies") + ".");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("reveal_stage_")
    public static void revealPOStage(ButtonInteractionEvent event, String buttonID, Game game) {
        String stage = buttonID.replace("reveal_stage_", "");
        if ("true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            if ("statusScoring".equalsIgnoreCase(game.getPhaseOfGame())) {
                StringBuilder missingPeople = new StringBuilder();
                for (Player player : game.getRealPlayers()) {
                    String so = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "SO");
                    if (so.isEmpty()) {
                        missingPeople.append(player.getRepresentation(false, true));
                    }
                }
                if (!missingPeople.isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            game.getActionsChannel(),
                            missingPeople
                                    + " need to indicate if they are scoring a secret objective before the next public objective can be flipped.");
                    return;
                }
            }
        }
        if (!game.getStoredValue("revealedPOInRound" + game.getRound()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    "The bot thinks that a public objective was already revealed this round. Try doing `/status reveal` if this was a mistake.");
            return;
        } else {
            game.setStoredValue("revealedPOInRound" + game.getRound(), "Yes");
        }
        String revealedObjective = null;
        if (!game.isRedTapeMode() && !game.isCivilizedSocietyMode()) {
            if ("2".equalsIgnoreCase(stage)) {
                RevealPublicObjectiveService.revealS2(game, event);
            } else if ("2x2".equalsIgnoreCase(stage)) {
                RevealPublicObjectiveService.revealTwoStage2(game, event.getChannel());
            } else if ("none".equalsIgnoreCase(stage)) {
                // continue without revealing anything
            } else {
                revealedObjective = RevealPublicObjectiveService.revealS1(game, event);
            }
        } else {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "No objective is revealed at this stage in this mode.");
            int playersWithSCs = 0;
            for (Player player2 : game.getRealPlayers()) {
                if (player2.getSCs() != null
                        && !player2.getSCs().isEmpty()
                        && !player2.getSCs().contains(0)) {
                    playersWithSCs++;
                }
            }
            if (playersWithSCs > 0) {
                StatusCleanupService.runStatusCleanup(game);
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), "### " + game.getPing() + " **Status Cleanup Run!**");
            }
        }

        if (!game.isOmegaPhaseMode()) {
            StartPhaseService.startStatusHomework(event, game);
        } else {
            if (Constants.IMPERIUM_REX_ID.equalsIgnoreCase(revealedObjective)) {
                EndGameService.secondHalfOfGameEnd(event, game, true, true, false);
            } else {
                var speakerPlayer = game.getSpeaker();
                ObjectiveHelper.secondHalfOfPeakStage1(game, speakerPlayer, 1, true);
                TextChannel tableTalkChannel = game.getTableTalkChannel();
                if (!game.isFowMode() && tableTalkChannel != null) {
                    MessageHelper.sendMessageToChannel(
                            tableTalkChannel, "## End of Round #" + game.getRound() + " Scoring Info");
                    ListPlayerInfoService.displayerScoringProgression(game, true, tableTalkChannel, "both");
                }
                String message = "When ready, proceed to the Strategy Phase.";
                Button proceedToStrategyPhase = Buttons.green(
                        "proceed_to_strategy",
                        "Proceed to Strategy Phase (will refresh all cards and ping the priority player)");
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "The next objective has been revealed to " + MiscEmojis.SpeakerToken
                                + speakerPlayer.getRepresentationNoPing() + ".");
                MessageHelper.sendMessageToChannelWithButton(event.getChannel(), message, proceedToStrategyPhase);
            }
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("assignSpeaker_")
    @ButtonHandler(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)
    public static void sc3AssignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
        faction = faction.replace("assignSpeaker_", "");
        Player newSpeaker = game.getPlayerFromColorOrFaction(faction);
        if (newSpeaker.isSpeaker()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "That player is already speaker.");
            return;
        }
        game.setStoredValue("hasntSetSpeaker", "");
        for (Player player_ : game.getPlayers().values()) {
            if (player_.getFaction().equals(faction)) {
                game.setSpeakerUserID(player_.getUserID());
                String message = MiscEmojis.SpeakerToken + " Speaker has been assigned to "
                        + player_.getRepresentation(false, true) + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (game.isFowMode() && player != player_) {
                    MessageHelper.sendMessageToChannel(player_.getPrivateChannel(), message);
                }
                if (!game.isFowMode() && !game.isTwilightsFallMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                }
            }
        }
        ButtonHelper.deleteMessage(event);

        if (game.isTwilightsFallMode()) {
            String assignSpeakerMessage =
                    player.getRepresentation() + ", please choose a faction below to receive the Tyrant token.";
            List<Button> assignSpeakerActionRow = getTyrannusAssignTyrantButtons(game, player);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), assignSpeakerMessage, assignSpeakerActionRow);
        }
    }

    @ButtonHandler("assignTyrant_")
    public static void assignTyrant(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("assignTyrant_", "");
        for (Player player_ : game.getPlayers().values()) {
            if (player_.getFaction().equals(faction)) {
                game.setTyrantUserID(player_.getUserID());
                String message = MiscEmojis.BenedictionToken + " Tyrant has been assigned to "
                        + player_.getRepresentation(false, true) + ".";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
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

    private static List<Button> getTyrannusAssignTyrantButtons(Game game, Player politicsHolder) {
        List<Button> assignSpeakerButtons = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if ((!player.isSpeaker() || !politicsHolder.getSCs().contains(3)) && !player.isTyrant()) {
                String faction = player.getFaction();
                if (Mapper.isValidFaction(faction)) {
                    Button button;
                    if (!game.isFowMode()) {
                        button = Buttons.gray(
                                politicsHolder.getFinsFactionCheckerPrefix() + "assignTyrant_" + faction,
                                " ",
                                player.getFactionEmoji());
                    } else {
                        button = Buttons.gray(
                                politicsHolder.getFinsFactionCheckerPrefix() + "assignTyrant_" + faction,
                                player.getColor(),
                                ColorEmojis.getColorEmoji(player.getColor()));
                    }
                    assignSpeakerButtons.add(button);
                }
            }
        }
        return assignSpeakerButtons;
    }

    public static void poScoring(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel privateChannel) {
        if (!"true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (!game.getStoredValue("newStatusScoringMode").isEmpty() && event != null) {
                    String msg = "Please score objectives.";
                    msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
                    event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
                }
            } catch (Exception e) {
                if (event != null) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse PO ID: " + poID, e);
                    event.getChannel()
                            .sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                } else {
                    BotLogger.error("Hm", e);
                }
            }
            return;
        }
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key3b = "potentialScoreSOBlockers";
        String message;
        for (Player player2 : StatusHelper.getPlayersInScoringOrder(game)) {
            if (player2 == player) {
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                }

                String poID = buttonID.replace(Constants.PO_SCORING, "");
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                    if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                        Helper.resolvePOScoringQueue(game, event);
                    }
                }
                break;
            }
            if (game.getStoredValue(key3).contains(player2.getFaction() + "*")
                    || game.getStoredValue(key3b).contains(player2.getFaction() + "*")) {
                message = " has been queued to score a public objective. ";
                if (!game.isFowMode()) {
                    message += player2.getRepresentationUnfogged() + " is the one the game is currently waiting on.";
                }
                String poID = buttonID.replace(Constants.PO_SCORING, "");
                try {
                    int poIndex = Integer.parseInt(poID);
                    if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
                        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "Queued");
                    }
                    game.setStoredValue(player.getFaction() + "queuedPOScore", "" + poIndex);
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse PO ID: " + poID, e);
                    event.getChannel()
                            .sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                }
                game.setStoredValue(key2, game.getStoredValue(key2) + player.getFaction() + "*");
                ReactionService.addReaction(event, game, player, message);
                break;
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()
                && !"action".equalsIgnoreCase(game.getPhaseOfGame())
                && event != null
                && !game.isFowMode()) {
            String msg = "Please score objectives.";
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        if ("action".equalsIgnoreCase(game.getPhaseOfGame()) && event != null) {
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    @ButtonHandler(value = "get_so_discard_buttons", save = false)
    public static void getSODiscardButtons(ButtonInteractionEvent event, Player player) {
        String secretScoreMsg = "Click a button below to discard your secret objective.";
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
        boolean feint = false;
        if (buttonID.contains("skilled")) {
            if (game.isTwilightsFallMode()) {
                feint = true;
            }
            skilled = true;
            ButtonHelper.deleteMessage(event);
        }
        if (buttonID.contains("foresight")) {
            if (!game.isTwilightsFallMode()) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        player.getFactionEmojiOrColor()
                                + ", you placed 1 command token from your strategy pool to resolve your "
                                + FactionEmojis.Naalu
                                + "**Foresight** ability.");
                player.setStrategicCC(player.getStrategicCC() - 1);
            }
            skilled = true;
        }

        if (buttonID.contains("gheminabt")) {
            String btID = "gheminabt";
            Player p1 = player;
            BreakthroughModel btModel = Mapper.getBreakthrough(btID);
            p1.getBreakthroughExhausted().put(btID, true);
            String message = p1.getRepresentation() + " exhausted _" + btModel.getName() + "_ to immediately retreat.";
            MessageHelper.sendMessageToChannelWithEmbed(
                    p1.getCorrectChannel(), message, btModel.getRepresentationEmbed());
        }

        String message = player.getRepresentationUnfogged() + ", please choose a system to move to.";
        List<Button> retreatButtons =
                ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, pos, skilled, feint);
        if (retreatButtons.isEmpty()) {
            message = player.getRepresentationUnfogged() + ", there are no valid systems to retreat to.";
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, retreatButtons);

        if (game.getTileByPosition(pos).isGravityRift()
                && !player.hasRelic("circletofthevoid")
                && !player.hasTech("tf-crucible")) {
            Button rift = Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "getRiftButtons_" + pos,
                    "Rift Units",
                    MiscEmojis.GravityRift);
            List<Button> buttons = new ArrayList<>();
            buttons.add(rift);
            String message2 = "## " + player.getRepresentationUnfogged()
                    + ", if applicable, use this button to rift retreating units __before__ choosing where to retreat. It needs to be before you actually select where to retreat.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
        }
    }

    @ButtonHandler("retreatUnitsFrom_")
    public static void retreatUnitsFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperModifyUnits.retreatSpaceUnits(buttonID, event, game, player);
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " retreated all units in space to "
                        + game.getTileByPosition(pos2).getRepresentationForButtons(game, player) + ".");
        LoreService.showSystemLore(player, game, pos2, LoreService.TRIGGER.CONTROLLED);
        FOWCombatThreadMirroring.mirrorMessage(
                event, game, player.getRepresentationNoPing() + " retreated all units in space.");
        String message =
                player.getRepresentationUnfogged() + ", please choose which ground forces you wish to retreat.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, game, pos1, pos2));
        Tile oldTile = game.getTileFromPlanet("avernus");
        if (player.hasUnlockedBreakthrough("muaatbt")
                && CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Warsun)
                        .contains(game.getTileByPosition(pos2))
                && !game.getTileByPosition(pos2).isHomeSystem(game)
                && game.getTileByPosition(pos1) == oldTile) {

            List<Button> breakthroughButtons = new ArrayList<>();
            breakthroughButtons.add(
                    Buttons.blue(player.finChecker() + "moveAvernus_" + pos2, "Retreat Avernus", FactionEmojis.Muaat));
            breakthroughButtons.add(Buttons.red("deleteButtons", "Decline"));
            String breakthroughMessage = player.getRepresentationUnfogged() + ", you may move Avernus into "
                    + game.getTileByPosition(pos2).getRepresentationForButtons(game, player) + ".";
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(), breakthroughMessage, breakthroughButtons);
        }

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getPsychoButtons")
    public static void offerPsychoButtons(Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use buttons to gain 1 trade good per planet exhausted.",
                ButtonHelper.getPsychoTechPlanets(game, player));
    }

    @ButtonHandler("getAgentSelection_")
    public static void getAgentSelection(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please choose the target of your agent.",
                buttons);
    }

    @ButtonHandler("preScoreObbie_")
    public static void preScoreObbie(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        if (game.getPhaseOfGame().contains("action")) {
            String poOrSO = buttonID.split("_")[1];
            String num = buttonID.split("_")[2];
            game.setStoredValue(player.getFaction() + "Round" + game.getRound() + "PreScored" + poOrSO, num);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Successfully queued an objective to score (it won't be scored if you later stop meeting the requirements).");
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray("reverse" + buttonID, "Unqueue it"));
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You can use this to unqueue it and queue something else.", buttons);
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "The game is not currently in the action phase, and so no scoring was queued. Go score normally.");
        }
    }

    @ButtonHandler("reversepreScoreObbie_")
    public static void reversepreScoreObbie(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        if (game.getPhaseOfGame().contains("action")) {
            String poOrSO = buttonID.split("_")[1];
            game.setStoredValue(player.getFaction() + "Round" + game.getRound() + "PreScored" + poOrSO, "");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Successfully unqueued an objective.");
            StatusHelper.offerPreScoringButtons(game, player);
        }
    }

    @ButtonHandler(value = "get_so_score_buttons", save = false)
    public static void getSoScoreButtons(ButtonInteractionEvent event, Player player) {
        String secretScoreMsg = "Please choose the secret objective you wish to score.";
        List<Button> soButtons = SecretObjectiveHelper.getUnscoredSecretObjectiveButtons(player);
        if (!soButtons.isEmpty()) {
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, secretScoreMsg, soButtons);
        } else {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have no secret objectives you can score.");
        }
    }

    public static void soScoreFromHand(
            ButtonInteractionEvent event,
            String buttonID,
            Game game,
            Player player,
            MessageChannel privateChannel,
            MessageChannel mainGameChannel,
            MessageChannel actionsChannel) {
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
            if (player.getSecret(soIndex2) != null
                    && "status".equalsIgnoreCase(player.getSecret(soIndex2).getPhase())
                    && "true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
                String key2 = "queueToScoreSOs";
                String key3 = "potentialScoreSOBlockers";
                String key3b = "potentialScorePOBlockers";
                String message;
                for (Player player2 : StatusHelper.getPlayersInScoringOrder(game)) {
                    if (player2 == player) {
                        int soIndex = Integer.parseInt(soID);
                        SecretObjectiveHelper.scoreSO(event, game, player, soIndex, channel);
                        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                        }
                        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                                Helper.resolvePOScoringQueue(game, event);
                            }
                        }

                        break;
                    }
                    if (game.getStoredValue(key3).contains(player2.getFaction() + "*")
                            || game.getStoredValue(key3b).contains(player2.getFaction() + "*")) {
                        message = player.getRepresentation() + " has been queued to score a secret objective. ";
                        if (!game.isFowMode()) {
                            message += player2.getRepresentationUnfogged()
                                    + " is the one the game is currently waiting on.";
                        }
                        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
                            game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "Queued");
                        }
                        MessageHelper.sendMessageToChannel(channel, message);
                        int soIndex = Integer.parseInt(soID);
                        game.setStoredValue(player.getFaction() + "queuedSOScore", "" + soIndex);
                        game.setStoredValue(key2, game.getStoredValue(key2) + player.getFaction() + "*");
                        break;
                    }
                }
            } else {
                try {
                    int soIndex = Integer.parseInt(soID);
                    SecretObjectiveHelper.scoreSO(event, game, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.error(new LogOrigin(event, player), "Could not parse SO ID: " + soID, e);
                    event.getChannel()
                            .sendMessage("Could not parse secret objective ID: " + soID + ". Please score manually.")
                            .queue(Consumers.nop(), BotLogger::catchRestError);
                    return;
                }
            }
        } else {
            if (event != null) {
                event.getChannel()
                        .sendMessage("Could not find channel to play card. Please ping Bothelper.")
                        .queue(Consumers.nop(), BotLogger::catchRestError);
            }
        }
        ButtonHelper.deleteMessage(event);
        checkForAllReactions(event, game);
    }

    @ButtonHandler("deleteButtons")
    public static void deleteButtons(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        buttonID = buttonID.replace("deleteButtons_", "");
        String editedMessage = event.getMessage().getContentRaw();
        if (("Done Gaining Command Tokens".equalsIgnoreCase(buttonLabel)
                        || "Done Redistributing Command Tokens".equalsIgnoreCase(buttonLabel)
                        || "Done Losing Command Tokens".equalsIgnoreCase(buttonLabel)
                        || "Done Losing Fleet Tokens".equalsIgnoreCase(buttonLabel))
                && editedMessage.contains("command tokens have gone from")) {

            String playerRep = player.getRepresentation();
            String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
            String shortCCs = editedMessage.substring(editedMessage.indexOf("command tokens have gone from "));
            shortCCs = shortCCs.replace("command tokens have gone from ", "");
            shortCCs = shortCCs.substring(0, shortCCs.indexOf(' '));
            if (event.getMessage().getContentRaw().contains("Net gain")) {
                boolean cyber = false;
                boolean malevolency = false;
                int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                finalCCs += ". You gained a net total of " + netGain + " command token" + (netGain == 1 ? "" : "s");
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                        cyber = true;
                    }
                    if (!player.ownsPromissoryNote("malevolency") && "malevolency".equalsIgnoreCase(pn)) {
                        malevolency = true;
                    }
                }
                if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
                    if (malevolency && !player.getMahactCC().isEmpty()) {
                        malevolency = false;
                        MahactTokenService.removeFleetCC(game, player, "due to _Malevolency_");
                    }
                    if (player.hasAbility("versatile")
                            || player.hasTech("hm")
                            || cyber
                            || malevolency
                            || player.hasTech("tf-inheritancesystems")) {
                        int properGain = 2;
                        String reasons = "";
                        if (player.hasAbility("versatile")) {
                            properGain += 1;
                            reasons = "**Versatile**";
                        }
                        if (player.hasTech("hm")) {
                            properGain += 1;
                            reasons += (properGain == 3 ? "" : ", ") + "_Hyper Metabolism_";
                        }
                        if (player.hasTech("tf-inheritancesystems")) {
                            properGain += 1;
                            reasons += (properGain == 3 ? "" : ", ") + "_Inheritance Systems_";
                        }
                        if (malevolency) {
                            properGain -= 1;
                            reasons += (properGain == 1 ? "" : ", ") + "_Malevolency_";
                        }
                        if (cyber) {
                            properGain += 1;
                            reasons += (properGain == 3 ? "" : ", ") + "_Cybernetic Enhancements_";
                        }
                        if (netGain != properGain) {
                            MessageHelper.sendMessageToChannel(
                                    player.getCorrectChannel(),
                                    player.getRepresentationUnfogged()
                                            + ", heads up, bot thinks you should have gained "
                                            + (properGain == 1 ? "only " : "") + properGain
                                            + " command token" + (properGain == 1 ? "" : "s") + " due to " + reasons
                                            + ".");
                        } else {
                            if (netGain > 2 && cyber) {
                                PromissoryNoteHelper.resolvePNPlay("ce", player, game, event);
                            }
                        }
                    }
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(
                                player.getPrivateChannel(),
                                "## Remember to click \"Ready for "
                                        + (game.isCustodiansScored() ? "Agenda" : "Strategy Phase")
                                        + "\" when done with homework!\n"
                                        + game.getMainGameChannel().getJumpUrl());
                    }
                }
                player.setTotalExpenses(player.getTotalExpenses() + netGain * 3);
            }

            if ("Done Redistributing Command Tokens".equalsIgnoreCase(buttonLabel)) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        playerRep + ", your initial command token allocation was " + shortCCs
                                + ". Your final command token allocation is " + finalCCs + ".");
            } else {
                if ("leadership".equalsIgnoreCase(buttonID)) {
                    game.setStoredValue("ledSpend" + player.getFaction(), "");
                    String message = playerRep + ", your initial command token allocation was " + shortCCs
                            + ". Your final command tokens allocation is "
                            + finalCCs + ".";
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "leadership");
                } else {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            playerRep + ", your final command tokens allocation is " + finalCCs + ".");
                }
            }
            ButtonHelper.checkFleetInEveryTile(player, game);
        }
        if (("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
                || "Done Producing Units".equalsIgnoreCase(buttonLabel))) {
            Tile tile = null;
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel) && buttonID.contains("_")) {
                String pos = buttonID.split("_")[1];
                buttonID = buttonID.split("_")[0];
                tile = game.getTileByPosition(pos);
                game.setStoredValue(
                        "currentActionSummary" + player.getFaction(),
                        game.getStoredValue("currentActionSummary" + player.getFaction()) + " Produced units in "
                                + tile.getRepresentationForButtons() + ".");
            }
            if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
                    && player.hasAbility("amalgamation")
                    && !game.getStoredValue("amalgAmount").isEmpty()) {
                editedMessage = Helper.buildSpentThingsMessage(player, game, "res");
            }
            ButtonHelper.sendMessageToRightStratThread(player, game, editedMessage, buttonID);
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel)) {
                event.getChannel().getHistory().retrievePast(2).queue(messageHistory -> {
                    Message previousMessage = messageHistory.get(1);
                    if (previousMessage.getContentRaw().contains("You have available to you")) {
                        previousMessage.delete().queue(Consumers.nop(), BotLogger::catchRestError);
                    }
                });
                AutoFactoriesService.resolveAutoFactories(game, player, buttonID);
                TheIconService.checkAndSendIconButton(event, game, player, buttonID);
                EidolonMaximumService.sendEidolonMaximumFlipButtons(game, player);
                int cost = Helper.calculateCostOfProducedUnits(player, game, true);
                game.setStoredValue("producedUnitCostFor" + player.getFaction(), "" + cost);
                player.setTotalExpenses(
                        player.getTotalExpenses() + Helper.calculateCostOfProducedUnits(player, game, true));
                String message2 = player.getRepresentationUnfogged()
                        + ", please choose the planets you wish to exhaust to pay a cost of " + cost + ".";
                boolean warM = player.getSpentThingsThisWindow().contains("warmachine");

                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");

                if (player.hasTechReady("htp")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)) {
                    buttons.add(Buttons.red("exhaustTech_htp", "Exhaust Hegemonic Trade Policy", FactionEmojis.Winnu));
                }
                if ((game.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
                                || player.hasTech("tf-abundance"))
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("generic")) {
                    ButtonHelperCommanders.titansCommanderUsage(event, game, player);
                }
                if (player.hasTechReady("dsbenty")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.green("exhaustTech_dsbenty", "Use Merged Replicators", FactionEmojis.bentor));
                }
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 0
                        && player.hasTechReady("aida")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red(
                            "exhaustTech_aida",
                            "Exhaust AI Development Algorithm (" + ButtonHelper.getNumberOfUnitUpgrades(player) + "r)",
                            TechEmojis.WarfareTech));
                }
                if (player.hasTech("st")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("useTech_st", "Use Sarween Tools", TechEmojis.CyberneticTech));
                }
                if (player.hasTechReady("tf-sledfactories")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(
                            Buttons.red("useTech_tf-sledfactories", "Use Sled Factories", TechEmojis.CyberneticTech));
                }
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    buttons.add(Buttons.red("useRelic_boon", "Use Boon Of The Cerulean God Relic"));
                }
                if (player.hasTechReady("absol_st")) {
                    buttons.add(Buttons.red("useTech_absol_st", "Use Sarween Tools"));
                }
                if (player.hasUnexhaustedLeader("winnuagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("exhaustAgent_winnuagent", "Use Winnu Agent", FactionEmojis.Winnu));
                }
                if (player.hasUnexhaustedLeader("gledgeagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_gledgeagent_" + player.getFaction(),
                            "Use Gledge Agent",
                            FactionEmojis.gledge));
                }

                if (player.hasUnexhaustedLeader("ghotiagent")) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_ghotiagent_" + player.getFaction(), "Use Ghoti Agent", FactionEmojis.ghoti));
                }

                if (player.hasUnexhaustedLeader("mortheusagent")) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_mortheusagent_" + player.getFaction(),
                            "Use Mortheus Agent",
                            FactionEmojis.mortheus));
                }
                if (player.hasUnexhaustedLeader("rohdhnaagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_rohdhnaagent_" + player.getFaction(),
                            "Use Roh'Dhna Agent",
                            FactionEmojis.rohdhna));
                }
                if (player.hasLeaderUnlocked("hacanhero")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !"solBtBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
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
                if (!game.getStoredValue("manifestDiscount").isEmpty()) {
                    player.addSpentThing("manifest");
                    game.removeStoredValue("manifestDiscount");
                }
                if (player.hasUnlockedBreakthrough("ghostbt")
                        && tile != null
                        && !tile.getWormholes(game).isEmpty()) {
                    Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
                    int adjust = 0;
                    for (Map.Entry<String, Integer> entry : producedUnits.entrySet()) {
                        String unit2 = entry.getKey().split("_")[0];
                        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit2), player.getColor());
                        UnitModel producedUnit =
                                player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();

                        if (producedUnit.getUnitType() == UnitType.Flagship && player.ownsUnit("creuss_flagship")) {
                            adjust = 1;
                        }
                    }
                    if (tile.getWormholes(game).size() - adjust > 0) {
                        player.addSpentThing(
                                "ghostbt" + (tile.getWormholes(game).size() - adjust));
                    }
                }
                // ButtonHelper.updateMap(game, event,
                // "Result of build on turn " + player.getInRoundTurnCount() + " for " +
                // player.getFactionEmoji());
                buttons.add(doneExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                if (tile != null
                        && player.hasAbility("rally_to_the_cause")
                        && player.getHomeSystemTile() == tile
                        && !ButtonHelperAbilities.getTilesToRallyToTheCause(game, player)
                                .isEmpty()) {
                    String msg = player.getRepresentation()
                            + " due to your **Rally to the Cause** ability, if you just produced a ship in your home system,"
                            + " you may produce up to 2 ships in a system that contains a planet with a trait,"
                            + " but does not contain a legendary planet or another player's units. Please use the button to resolve.";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.green("startRallyToTheCause", "Rally To The Cause"));
                    buttons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons2);
                }
            }
        }
        if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            if (player.hasTech("asn")
                    && game.getStoredValue("ASN" + player.getFaction()).isEmpty()
                    && (buttonID.contains("tacticalAction")
                            || buttonID.contains("warfare")
                            || buttonID.contains("construction")
                            || buttonID.contains("anarchy7Build")
                            || buttonID.contains("lumi7Build")
                            || buttonID.contains("ministerBuild"))) {
                ButtonHelperFactionSpecific.offerASNButtonsStep1(game, player, buttonID);
            }
            player.resetSpentThings();
            game.removeStoredValue("producedUnitCostFor" + player.getFaction());
            if (player.hasAbility("amalgamation")) {
                game.removeStoredValue("amalgAmount");
            }
            if (buttonID.contains("lumi7Build")) {
                if (!game.getStoredValue("lumi7System").isEmpty()) {
                    Tile tile = game.getTileByPosition(game.getStoredValue("lumi7System"));
                    CommandCounterHelper.addCC(event, player, tile);
                    String message =
                            player.getFactionEmojiOrColor() + " placed 1 command token from reinforcements in the "
                                    + tile.getRepresentation() + " system.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                }
            }
            if (buttonID.contains("tacticalAction")
                    && game.getStoredValue("ASN" + player.getFaction()).isEmpty()) {
                ButtonHelperTacticalAction.endOfTacticalActionThings(player, game, event);
                List<Button> systemButtons2;
                if (player.hasUnexhaustedLeader("sardakkagent")) {
                    String message = player.getRepresentationUnfogged() + ", you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "T'ro, the N'orr" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent.";
                    systemButtons2 = new ArrayList<>(ButtonHelperAgents.getSardakkAgentButtons(game));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("nomadagentmercer")) {
                    String message = player.getRepresentationUnfogged() + ", you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Field Marshal Mercer, a Nomad"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
                    systemButtons2.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(game, player));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }

                if (game.isNaaluAgent()) {
                    player = game.getPlayer(game.getActivePlayerID());
                }

                if (game.isFowMode()) {
                    LoreService.showSystemLore(player, game, game.getActiveSystem(), LoreService.TRIGGER.CONTROLLED);
                }

                game.removeStoredValue("producedUnitCostFor" + player.getFaction());

                String message = player.getRepresentationUnfogged()
                        + ", please use the buttons to end turn or do another action.";
                List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, systemButtons);
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
        mainMessage.clearReactions().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static void checkForAllReactions(@NotNull ButtonInteractionEvent event, Game game) {
        if (event == null) {
            return;
        }
        String buttonID = event.getButton().getCustomId();

        String messageId = event.getInteraction().getMessage().getId();
        var gameMessage = GameMessageManager.getOne(game.getName(), messageId);
        int matchingFactionReactions = 0;
        if (buttonID != null
                && (buttonID.contains("po_scoring")
                        || buttonID.contains("po_no_scoring")
                        || buttonID.contains("so_no_scoring")
                        || buttonID.contains("so_score_hand"))) {
            boolean allReacted = true;
            for (Player player : game.getRealPlayers()) {
                String po = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "PO");
                String so = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "SO");
                if (po.isEmpty()
                        || so.isEmpty()
                        || game.getPhaseOfGame().contains("action")
                        || game.getPhaseOfGame().contains("agenda")) {
                    allReacted = false;
                }
            }
            if (allReacted) {
                respondAllPlayersReacted(event, game);
                GameMessageManager.remove(game.getName(), messageId);
            }
        } else {

            for (Player player : game.getRealPlayers()) {
                boolean factionReacted = false;
                String faction = player.getFaction();
                if (gameMessage.isPresent()
                        && gameMessage.get().factionsThatReacted().contains(faction)) {
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
            }
        }
    }

    public static void respondAllHaveScored(Game game) {
        String message2 =
                "All players have indicated scoring. Flip the relevant public objective using the buttons. This will automatically run status clean-up if it has not been run already.";
        Button draw2Stage2 = Buttons.green("reveal_stage_2x2", "Reveal 2 Stage 2");
        Button drawStage2 = Buttons.green("reveal_stage_2", "Reveal Stage 2");
        Button drawStage1 = Buttons.green("reveal_stage_1", "Reveal Stage 1");
        List<Button> buttons = new ArrayList<>();
        if (game.isRedTapeMode() || game.isCivilizedSocietyMode()) {
            message2 = "All players have indicated scoring. In this game mode, no objective is revealed at this stage."
                    + " Please press one of the buttons below anyways though - don't worry, it won't reveal anything, it will just run cleanup.";
        }
        if (game.getRound() < 4 || !game.getPublicObjectives1Peekable().isEmpty()) {
            buttons.add(drawStage1);
        }
        if ((game.getRound() > 3 || game.getPublicObjectives1Peekable().isEmpty()) && !game.isOmegaPhaseMode()) {
            if ("456".equalsIgnoreCase(game.getStoredValue("homebrewMode"))) {
                buttons.add(draw2Stage2);
            } else {
                buttons.add(drawStage2);
            }
        }
        var endGameDeck =
                game.isOmegaPhaseMode() ? game.getPublicObjectives1Peekable() : game.getPublicObjectives2Peekable();
        var endGameRound = game.isOmegaPhaseMode() ? 9 : 7;
        if ((game.getRound() > endGameRound || endGameDeck.isEmpty())
                && !game.isRedTapeMode()
                && !game.isCivilizedSocietyMode()) {
            if (game.isFowMode()) {
                message2 += "\n> - If there are no more objectives to reveal, use the button to continue as is.";
                message2 += " Or end the game manually.";
                buttons.add(Buttons.green("reveal_stage_none", "Continue without revealing"));
            } else {
                message2 += "\n> - If there are no more objectives to reveal, use the button to end the game.";
                message2 +=
                        " Whoever has the most points is crowned the winner, or whoever has the earliest initiative in the case of ties.";

                buttons.add(Buttons.red("gameEnd", "End Game"));
                buttons.add(Buttons.blue("rematch", "Rematch (make new game with same players/channels)"));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), message2, buttons);
    }

    private static void respondAllPlayersReacted(ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getCustomId();
        if (game == null || buttonID == null) {
            return;
        }
        if (buttonID.startsWith(Constants.PO_SCORING)
                || buttonID.contains("po_no_scoring")
                || buttonID.contains("so_no_scoring")
                || buttonID.contains("so_score_hand")) {
            buttonID = Constants.PO_SCORING;
        } else if ((buttonID.startsWith(Constants.SC_FOLLOW) || buttonID.startsWith("sc_no_follow"))) {
            buttonID = Constants.SC_FOLLOW;
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            String buttonText = event.getButton().getLabel();
            event.getInteraction()
                    .getMessage()
                    .reply("All players have reacted to \"" + buttonText + "\".")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
        switch (buttonID) {
            case Constants.SC_FOLLOW,
                    "sc_refresh",
                    "sc_refresh_and_wash",
                    "trade_primary",
                    "sc_ac_draw",
                    "sc_draw_so",
                    "sc_trade_follow" -> {
                String message = "All players have reacted to this strategy card.";
                if (game.isFowMode()) {
                    event.getInteraction().getMessage().reply(message).queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage(message).queueAfter(10, TimeUnit.SECONDS);
                }
            }
            case "no_when", "no_when_persistent" ->
                ReactionService.handleAllPlayersReactingNoWhens(
                        event.getInteraction().getMessage(), game);
            case "no_after", "no_after_persistent" ->
                ReactionService.handleAllPlayersReactingNoAfters(
                        event.getInteraction().getMessage(), game);
            case "no_sabotage" ->
                ReactionService.handleAllPlayersReactingNoSabotage(
                        event.getInteraction().getMessage(), game);
            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> respondAllHaveScored(game);
            case "pass_on_abilities" -> {
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    if (game.isTwilightsFallMode()) {
                        Button flipAgenda = Buttons.blue("edictPhase", "Do Edict Phase");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(), "Please proceed to the Edict Phase now.", buttons);
                    } else {
                        Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(), "Please flip agenda now.", buttons);
                    }
                } else {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            game.getPing()
                                    + ", all players have indicated completion of Status Phase. Proceeding to Strategy Phase.");
                    StartPhaseService.startPhase(event, game, "strategy");
                }
                if (game.isFowMode()) {
                    game.setStoredValue("fowStatusDone", "");
                    StatusCleanupService.returnEndStatusPNs(game);
                }
            }
            case "redistributeCCButtons" -> {
                StatusCleanupService.returnEndStatusPNs(
                        game); // return any PNs with "end of status phase" return timing
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    if (game.isTwilightsFallMode()) {
                        Button flipAgenda = Buttons.blue("edictPhase", "Do Edict Phase");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(),
                                "Please proceed to Edict Phase after the last person finishing doing gaining and redistributing command tokens.",
                                buttons);
                    } else {
                        Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                        List<Button> buttons = List.of(flipAgenda);
                        MessageHelper.sendMessageToChannelWithButtons(
                                event.getChannel(),
                                "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                                        + " Please press the \"Flip Agenda\" button after they have finished redistributing tokens and you have fully resolved all other Status Phase effects.",
                                buttons);
                    }

                } else {
                    Button flipAgenda = Buttons.blue("startStrategyPhase", "Start Strategy Phase");
                    List<Button> buttons = List.of(flipAgenda);
                    String condition;
                    if (game.isOrdinianC1Mode()) {
                        condition = "the _Coatl_ is still damaged";
                    } else if (game.isTwilightsFallMode()) {
                        condition = "there is no Tyrant";
                    } else {
                        condition = "the Custodians token is still on Mecatol Rex";
                    }
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getChannel(),
                            "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                                    + " As " + condition + ", there will be no Agenda Phase this round."
                                    + " Please press the \"Start Strategy Phase\" button after they have finished redistributing tokens and you have fully resolved all other Status Phase effects.",
                            buttons);
                }
            }
        }
    }

    @ButtonHandler("reinforcements_cc_placement_")
    public static void reinforcementsCCPlacement(
            GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace("reinforcements_cc_placement_", "");
        String tileID = AliasHandler.resolveTile(planet.toLowerCase());
        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }
        CommandCounterHelper.addCC(event, player, tile);
        String message = player.getFactionEmojiOrColor() + " placed 1 command token from reinforcements in the "
                + Helper.getPlanetRepresentation(planet, game) + " system.";
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event);
        }
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("componentAction")
    public static void componentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Please choose what kind of component action you wish to do.";
        if (IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure")) {
            message += "\n-# You have been _Political Censure_'d, and thus cannot play action cards.";
        }
        List<Button> systemButtons = ComponentActionHelper.getAllPossibleCompButtons(game, player, event);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, systemButtons);
    }

    @ButtonHandler("thronePoint")
    public static void thronePoint(ButtonInteractionEvent event, Player player, Game game) {
        Integer poIndex = game.addCustomPO("Throne of the False Emperor", 1);
        game.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " scored a secret objective (they'll specify which one). The bot has already given you a victory point for this.");
        Helper.checkEndGame(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doneRemoving")
    public static void doneRemoving(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), event.getMessage().getContentRaw());
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

    @ButtonHandler("chooseMapView")
    public static void chooseMapView(ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("checkWHView", "Find Wormholes"));
        buttons.add(Buttons.red("checkAnomView", "Find Anomalies"));
        buttons.add(Buttons.green("checkLegendView", "Find Legendaries"));
        buttons.add(Buttons.gray("checkEmptyView", "Find Empties"));
        buttons.add(Buttons.red("checkExileView", "Determine Exile Breachable Systems"));
        buttons.add(Buttons.blue("checkAetherView", "Determine Aetherstreamable Systems"));
        buttons.add(Buttons.red("checkCannonView", "Calculate Space Cannon Offense Shots"));
        buttons.add(Buttons.green("checkTraitView", "Find Traits"));
        buttons.add(Buttons.green("checkTechSkipView", "Find Technology Specialties"));
        buttons.add(Buttons.blue("checkAttachmView", "Find Attachments"));
        buttons.add(Buttons.gray("checkShiplessView", "Show Map Without Ships"));
        buttons.add(Buttons.gray("checkUnlocked", "Show Only Unlocked Units"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "", buttons);
    }

    @ButtonHandler("checkExileView")
    public static void calculateExileView(ButtonInteractionEvent event, Game game) {
        ButtonHelper.showFeatureType(event, game, DisplayType.exile);
    }

    @ButtonHandler("resetSpend_")
    public static void resetSpend_(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Helper.refreshPlanetsOnTheRespend(player, game);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 1) {
            whatIsItFor = buttonID.split("_")[1];
        }

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor);
        for (ActionRow row : event.getMessage().getComponentTree().findAll(ActionRow.class)) {
            List<ActionRowChildComponentUnion> buttonRow = row.getComponents();
            for (ActionRowChildComponentUnion but : buttonRow) {
                if (but instanceof Button butt) {
                    if (!Helper.doesListContainButtonID(buttons, butt.getCustomId())) {
                        buttons.add(butt);
                    }
                }
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage()
                .editMessage(exhaustedMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
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
        for (ActionRow row : event.getMessage().getComponentTree().findAll(ActionRow.class)) {
            List<ActionRowChildComponentUnion> buttonRow = row.getComponents();
            for (ActionRowChildComponentUnion but : buttonRow) {
                if (but instanceof Button butt) {
                    if (!buttons.contains(butt)) {
                        buttons.add(butt);
                    }
                }
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage()
                .editMessage(exhaustedMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("setOrder")
    public static void setOrder(ButtonInteractionEvent event, Game game) {
        Helper.setOrder(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gain_CC")
    public static void gainCC(ButtonInteractionEvent event, Player player, Game game) {
        String message = "";

        String message2 = player.getRepresentationUnfogged() + ", your current command tokens are "
                + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("run_status_cleanup")
    public static void runStatusCleanup(ButtonInteractionEvent event, Game game, Player player) {
        StatusCleanupService.runStatusCleanup(game);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        ReactionService.addReaction(
                event, game, player, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
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

    @ButtonHandler("temporaryPingDisable")
    public static void temporaryPingDisable(ButtonInteractionEvent event, Game game) {
        game.setTemporaryPingDisable(true);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Disabled autopings for this turn.");
        ButtonHelper.deleteMessage(event);
    }

    @Deprecated
    public static void gain1tgFromCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message =
                player.getRepresentation() + " gained 1 trade good " + player.gainTG(1) + " from their commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromMuaatCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Magmus, the Muaat commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromLetnevCommander(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " gained 1 trade good " + player.gainTG(1)
                + " from Rear Admiral Farran, the Letnev commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1TG(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = "";
        String labelP = event.getButton().getLabel();
        boolean failed = false;
        if (labelP.contains("inf") && labelP.contains("mech")) {
            message += "Please resolve removing infantry manually, if applicable.";
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

    @ButtonHandler("resetProducedThings")
    public static void resetProducedThings(ButtonInteractionEvent event, Player player, Game game) {
        Helper.resetProducedUnits(player, game, event);
        event.getMessage()
                .editMessage(Helper.buildProducedUnitsMessage(player, game))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("yssarilMinisterOfPolicy")
    public static void yssarilMinisterOfPolicy(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " is drawing their _Minister of Policy_ action card.");
        ActionCardHelper.drawActionCards(player, 1);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("non_sc_draw_so")
    public static void nonSCDrawSO(ButtonInteractionEvent event, Player player, Game game) {
        String message = "drew a secret objective.";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message += ". Drew a second secret objective due to **Plausible Deniability**.";
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("diploSystem")
    public static void diploSystem(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", please choose the system you wish to Diplo.";
        List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
    }

    @ButtonHandler("placeCCBack_")
    public static void placeCCBack(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String position = buttonID.split("_")[1];
        String message = player.getRepresentationUnfogged()
                + " has chosen to not pay the 1 command token required to remove a command token from the Errant (Toldar flagship) system,"
                + " and so their command token has been placed back in tile " + position + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
        CommandCounterHelper.addCC(event, player, game.getTileByPosition(position));
    }

    @ButtonHandler("placeWingTransferCC_")
    public static void placeCC(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String position = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(position);
        String message =
                player.getRepresentationUnfogged() + " is using _Wing Transfer_ to place their command token in the "
                        + tile.getRepresentationForButtons() + " system.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        CommandCounterHelper.addCC(event, player, tile);
    }

    @ButtonHandler("passingAbilities")
    private static void passingAbilities(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "Use these buttons to do an ability when you pass.";
        List<Button> buttons = ButtonHelper.getPassingAbilities(player, game);
        buttons.addFirst(Buttons.red(player.finChecker() + "passForRound", "Pass"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("endOfTurnAbilities")
    public static void endOfTurnAbilities(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "Use buttons to do an end of turn ability";
        List<Button> buttons = ButtonHelper.getEndOfTurnAbilities(player, game);
        ButtonHelper.deleteMessage(event);
        if (!buttons.isEmpty()) {
            buttons.addFirst(Buttons.red(player.finChecker() + "turnEnd", "End Turn"));
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), msg, buttons);
        } else {
            TurnEndButtonHandler.turnEnd(event, game, player);
        }
    }

    @ButtonHandler("deployTyrant")
    public static void deployTyrant(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to place the _Tyrant's Lament_ with your ships.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "tyrantslament", "placeOneNDone_skipbuild"));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " is deploying the _Tyrant's Lament_.");
        player.addOwnedUnitByID("tyrantslament");
    }

    @ButtonHandler("turnOffForcedScoring")
    public static void turnOffForcedScoring(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                game.getPing() + ", forced scoring order has been turned off. Any queues will not be resolved.");
        game.setStoredValue("forcedScoringOrder", "");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("forceACertainScoringOrder")
    public static void forceACertainScoringOrder(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                game.getPing()
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
        String message =
                player.getRepresentation() + " has opted not to score a secret objective at this point in time.";
        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "SO", "None");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        String key2 = "queueToScoreSOs";
        String key3 = "potentialScoreSOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3, game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            String key3b = "potentialScorePOBlockers";
            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                Helper.resolvePOScoringQueue(game, event);
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
            String msg = "Please score objectives.";
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        ReactionService.addReaction(event, game, player);
    }

    @ButtonHandler(value = "refreshStatusSummary", save = false)
    public static void refreshStatusSummary(ButtonInteractionEvent event, Game game) {
        String msg = "Please score objectives.";
        msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
        event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("acquireAFreeTech") // Buttons.GET_A_FREE_TECH
    public static void acquireAFreeTech(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String finsFactionCheckerPrefix = player.getFinsFactionCheckerPrefix();
        game.setComponentAction(true);
        buttons.add(Buttons.blue(
                finsFactionCheckerPrefix + "getAllTechOfType_propulsion_noPay",
                "Get a Propulsion Technology",
                TechEmojis.PropulsionTech));
        buttons.add(Buttons.green(
                finsFactionCheckerPrefix + "getAllTechOfType_biotic_noPay",
                "Get a Biotic Technology",
                TechEmojis.BioticTech));
        buttons.add(Buttons.gray(
                finsFactionCheckerPrefix + "getAllTechOfType_cybernetic_noPay",
                "Get a Cybernetic Technology",
                TechEmojis.CyberneticTech));
        buttons.add(Buttons.red(
                finsFactionCheckerPrefix + "getAllTechOfType_warfare_noPay",
                "Get a Warfare Technology",
                TechEmojis.WarfareTech));
        buttons.add(Buttons.gray(
                finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade_noPay",
                "Get A Unit Upgrade Technology",
                TechEmojis.UnitUpgradeTech));
        String message = player.getRepresentation() + ", please choose what type of technology you wish to get?";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(Constants.PO_NO_SCORING)
    public static void poNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        // AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
        String message =
                player.getRepresentation() + " has opted not to score a public objective at this point in time.";
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        game.setStoredValue(player.getFaction() + "round" + game.getRound() + "PO", "None");
        String reply = game.isFowMode() ? "No public objective scored" : null;
        ReactionService.addReaction(event, game, player, reply);
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
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
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    @ButtonHandler("removeCCFromBoard_")
    public static void removeCCFromBoard(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.resolveRemovingYourCC(player, game, event, buttonID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("refreshLandingButtons")
    public static void refreshLandingButtons(ButtonInteractionEvent event, Player player, Game game) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> systemButtons = TacticalActionService.getLandingTroopsButtons(game, player, tile);
        event.getMessage()
                .editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
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
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose which system you wish to remove your command token from.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startOfGameObjReveal")
    public static void startOfGameObjReveal(ButtonInteractionEvent event, Game game) {
        for (Player p : game.getRealPlayers()) {
            if (p.getSecrets().size() > 1 && !game.isExtraSecretMode()) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Please ensure everyone has discarded secret objectives before hitting this button. ");
                return;
            }
        }

        Player speaker = null;
        if (game.getPlayer(game.getSpeakerUserID()) != null) {
            speaker = game.getPlayers().get(game.getSpeakerUserID());
        }
        if (speaker == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Please assign speaker before hitting this button.");
            ButtonHelper.offerSpeakerButtons(game);
            return;
        }
        if (game.hasAnyPriorityTrackMode()
                && PriorityTrackHelper.getPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            PriorityTrackHelper.CreateDefaultPriorityTrack(game);
            if (PriorityTrackHelper.getPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Failed to fill the Priority Track with the default seating order. Use `/omegaphase assign_player_priority` to fill the track before proceeding.");
                PriorityTrackHelper.PrintPriorityTrack(game);
                return;
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Set up the Priority Track in the default seating order.");
            PriorityTrackHelper.PrintPriorityTrack(game);
        }
        if (!game.getStoredValue("revealedFlop" + game.getRound()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    "The bot thinks that public objectives were already revealed. Try doing `/status reveal` if this was a mistake.");
            return;
        } else {
            game.setStoredValue("revealedFlop" + game.getRound(), "Yes");
        }

        if (game.isCivilizedSocietyMode()) {
            RevealPublicObjectiveService.revealAllObjectives(game);
        } else {
            RevealPublicObjectiveService.revealTwoStage1(game);
        }

        if (game.isTwilightsFallMode()
                && !game.getStoredValue("needsInauguralSplice").isEmpty()) {
            game.removeStoredValue("needsInauguralSplice");
            ButtonHelperTwilightsFall.startInauguralSplice(game);
        } else {
            startOfGameStrategyPhase(event, game);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startOfGameStrategyPhase")
    public static void startOfGameStrategyPhase(ButtonInteractionEvent event, Game game) {
        StartPhaseService.startStrategyPhase(event, game);
        PlayerPreferenceHelper.offerSetAutoPassOnSaboButtons(game, null);
        ButtonHelper.deleteMessage(event);
        // Reduce file size by clearing draft info
        game.clearAllDraftInfo();
    }

    @ButtonHandler("swapToFaction_")
    public static void swapToFaction(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("swapToFaction_", "");
        SwapFactionService.secondHalfOfSwap(
                game, player, game.getPlayerFromColorOrFaction(faction), event.getUser(), event);
    }
}
