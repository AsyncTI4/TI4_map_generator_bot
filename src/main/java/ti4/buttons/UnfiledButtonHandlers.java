package ti4.buttons;

import static org.apache.commons.lang3.StringUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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
import ti4.listeners.ButtonListener;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.PlanetService;
import ti4.service.StatusCleanupService;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.button.ReactionService;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.game.EndGameService;
import ti4.service.game.StartPhaseService;
import ti4.service.game.SwapFactionService;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.objectives.ScorePublicObjectiveService;
import ti4.service.option.GameOptionService;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.player.RefreshCardsService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.tactical.TacticalActionService;
import ti4.service.turn.PassService;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.settings.users.UserSettingsManager;

/**
 * TODO: move all of these methods to a better location, closer to the original button call and/or other related code
 * Buttons methods which were factored out of {@link ButtonListener} which need to be filed away somewhere more appropriate
 */
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
        ButtonHelperHeroes.argentHeroStep3(game, player, buttonID);
    }

    @ButtonHandler("enableDaneMode_")
    public static void enableDaneMode(ButtonInteractionEvent event, String buttonID, Game game) {
        String mode = buttonID.split("_")[1];
        boolean enable = "enable".equalsIgnoreCase(buttonID.split("_")[2]);
        String message = "Successfully " + buttonID.split("_")[2] + "d the ";
        if ("hiddenagenda".equalsIgnoreCase(mode)) {
            game.setHiddenAgendaMode(enable);
            message += "Hidden Agenda Mode. Nothing more needs to be done.";
        }
        if ("minorFactions".equalsIgnoreCase(mode)) {
            game.setMinorFactionsMode(enable);
            message += "Minor Factions Mode. ";
            if (enable) {
                message +=
                        "You will need to decide how you wish to draft the minor factions. This site has a decent setup for it, "
                                + "and you can important the map using buttons above: https://tidraft.com/draft/prechoice. Note that you need to set up a neutral player "
                                + "after the draft finishes with `/special2 setup_neutral_player`, and you can add 3 infantry to the minor faction planets pretty easily with `/add_units`.";
            }
        }
        if ("ageOfExploration".equalsIgnoreCase(mode)) {
            game.setAgeOfExplorationMode(enable);
            message += "Age of Exploration Mode. Nothing more needs to be done.";
        }
        if ("ageOfCommerce".equalsIgnoreCase(mode)) {
            game.setAgeOfCommerceMode(enable);
            message += "Age of Commerce Mode. Nothing more needs to be done.";
        }
        if ("totalWar".equalsIgnoreCase(mode)) {
            game.setTotalWarMode(enable);
            message += "Total War Mode. Nothing more needs to be done.";
        }
        if ("DangerousWilds".equalsIgnoreCase(mode)) {
            game.setDangerousWildsMode(enable);
            message += "Dangerous Wilds Mode. Nothing more needs to be done.";
            if (enable) {
                message += " The game will automatically put down infantry upon the start of every strategy phase.";
            }
        }
        if ("CivilizedSociety".equalsIgnoreCase(mode)) {
            game.setCivilizedSocietyMode(enable);
            message += "Civilized Society Mode. Nothing more needs to be done.";
        }
        if ("AgeOfFighters".equalsIgnoreCase(mode)) {
            game.setAgeOfFightersMode(enable);
            message += "Age Of Fighters Mode. Nothing more needs to be done.";
            if (enable) {
                for (Player player : game.getRealPlayers()) {
                    String tech = "ff2";
                    for (String factionTech : player.getNotResearchedFactionTechs()) {
                        TechnologyModel fTech = Mapper.getTech(factionTech);
                        if (fTech != null
                                && !fTech.getAlias()
                                        .equalsIgnoreCase(Mapper.getTech(tech).getAlias())
                                && fTech.isUnitUpgrade()
                                && fTech.getBaseUpgrade()
                                        .orElse("bleh")
                                        .equalsIgnoreCase(Mapper.getTech(tech).getAlias())) {
                            tech = fTech.getAlias();
                            break;
                        }
                    }
                    player.addTech(tech);
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation() + " gained the "
                                    + Mapper.getTech(tech).getNameRepresentation()
                                    + " technology due to the _Age of Fighters_ galactic event.");
                }
            }
        }
        if ("StellarAtomics".equalsIgnoreCase(mode)) {
            game.setStellarAtomicsMode(enable);
            if (enable) {
                int poIndex = game.addCustomPO("Stellar Atomics", 0);
                for (Player playerWL : game.getRealPlayers()) {
                    game.scorePublicObjective(playerWL.getUserID(), poIndex);
                }
            }
            message += "Stellar Atomics Mode. Nothing more needs to be done.";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        List<Button> buttons = GameOptionService.getDaneLeakModeButtons(game);
        event.getMessage()
                .editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
    }

    @ButtonHandler(value = "requestAllFollow_", save = false)
    public static void requestAllFollow(ButtonInteractionEvent event, Game game) {
        if ("fow273".equalsIgnoreCase(game.getName())) {
            event.getMessage()
                    .reply(
                            event.getUser().getAsMention()
                                    + " has requested that everyone resolve this strategy card before play continues."
                                    + " Please do so as soon as you can. The active player should not take an action until this is done.")
                    .queue();
        } else {
            event.getMessage()
                    .reply(
                            game.getPing()
                                    + ", someone has requested that everyone resolve this strategy card before play continues."
                                    + " Please do so as soon as you can. The active player should not take an action until this is done.")
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

    @ButtonHandler("ring_")
    public static void ring(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> ringButtons = ButtonHelper.getTileInARing(player, game, buttonID);
        String num = buttonID.replace("ring_", "");
        String message;
        if (!"corners".equalsIgnoreCase(num)) {
            int ring = Integer.parseInt(num.charAt(0) + "");
            if (ring > 4 && !num.contains("left") && !num.contains("right")) {
                message =
                        "That ring is very large. Specify if your tile is on the left or right side of the map (center will be counted in both).";
            } else {
                message = "Please choose the system that you wish to activate.";
            }
        } else {
            message = "Please choose the system that you wish to activate.";
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        ButtonHelper.deleteMessage(event);
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
                + " is using _Integrated Economy_ on " + Helper.getPlanetRepresentation(planet, game)
                + ". Use the buttons to produce units with a combined cost up to the planet (" + resources
                + ") resources.\n"
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
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation() + ", use the buttons to produce 1 unit.\n> "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("winnuStructure_")
    public static void winnuStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("winnuStructure_", "").split("_")[0];
        String planet = buttonID.replace("winnuStructure_", "").split("_")[1];
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit + " " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, game)
                        + ".");
        CommanderUnlockCheckService.checkPlayer(player, "titans", "saar", "rohdhna", "cheiran", "celdauri");
    }

    @ButtonHandler("qhetMechProduce_")
    public static void qhetMechProduce(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 inf " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " produced 2 infantry on " + Helper.getPlanetRepresentation(planet, game));
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(DoneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Please pay one resource.", buttons);
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

    @ButtonHandler("jrStructure_")
    public static void jrStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("jrStructure_", "");
        if (!"tg".equalsIgnoreCase(unit)) {
            String message = player.getRepresentationUnfogged()
                    + ", please choose the planet you wish to put your structure on.";
            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "placeOneNDone_dontskip");
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);

        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getFactionEmojiOrColor() + " trade goods increased by 1 " + player.gainTG(1) + ".");
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dacxive_")
    public static void daxcive(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("dacxive_", "");
        AddUnitService.addUnits(
                event, game.getTile(AliasHandler.resolveTile(planet)), game, player.getColor(), "infantry " + planet);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " placed 1 infantry on "
                        + Helper.getPlanetRepresentation(planet, game) + " via _Dacxive Animators_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arboAgentOn_")
    public static void arboAgentOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(
                player, game, event, game.getTileByPosition(pos), unit);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you wish to place down.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("sandbagPref_")
    public static void sandbagPref(ButtonInteractionEvent event, Player player, String buttonID) {
        var userSettings = UserSettingsManager.get(player.getUserID());
        userSettings.setSandbagPref(buttonID.split("_")[1]);
        UserSettingsManager.save(userSettings);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Thank you for answering.");
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

    @ButtonHandler("arboAgentIn_")
    public static void arboAgentIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf('_') + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you'd like to replace.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void startGlimmersRedTech(Player player, Game game) {
        Set<UnitType> allowedUnits = Set.of(
                UnitType.Fighter,
                UnitType.Destroyer,
                UnitType.Cruiser,
                UnitType.Carrier,
                UnitType.Dreadnought,
                UnitType.Flagship,
                UnitType.Warsun);

        List<Button> buttons = new ArrayList<>();
        for (UnitType unit : allowedUnits) {
            buttons.add(
                    Buttons.green("endGlimmersRedTech_" + unit.plainName(), unit.plainName(), unit.getUnitTypeEmoji()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the unit that was destroyed, and that you will be placing via _Fractal Plating_.",
                buttons);
    }

    @ButtonHandler("endGlimmersRedTech_")
    public static void endGlimmersRedTech(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event != null) {
            ButtonHelper.deleteMessage(event);
        }
        String unit = buttonID.split("_")[1];

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the system adjacent to your destroyed unit that you wish to place the unit."
                        + "\n-# Note that not all options displayed are legal options. The bot did not check where the unit was destroyed.",
                Helper.getTileWithShipsPlaceUnitButtons(player, game, unit, "placeOneNDone_skipbuild"));
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
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Select up to 2 ships and 2 ground forces to place in the space area.",
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
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFaction() + " used _X-89 Bacterial Weapon_ to remove all ground forces on " + planet + ".");

        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        Set<UnitKey> units = new HashSet<>(uH.getUnitsByState().keySet());
        for (UnitKey unit : units) {
            if (game.getPlayerByUnitKey(unit)
                    .map(p -> p.getUnitFromUnitKey(unit))
                    .map(UnitModel::getIsGroundForce)
                    .orElse(false)) {
                int amt = uH.getUnitCount(unit);
                DestroyUnitService.destroyUnit(event, tile, game, unit, amt, uH, true);
            }
        }
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
                ButtonHelper.deleteTheOneButton(event, event.getButton().getId(), false);
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
                    Planet planet = ButtonHelper.getUnitHolderFromPlanetName(planetID, game);
                    if (planet != null && isNotBlank(planet.getOriginalPlanetType())) {
                        List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
                        absolPAButtons.addAll(planetButtons);
                    }
                }
                ButtonHelper.deleteTheOneButton(event);
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
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander")) {
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
                            String label = "Unassign " + StringUtils.capitalize(mod.getBaseType()) + " from "
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
                        String label = "Assign " + StringUtils.capitalize(mod.getBaseType()) + " to "
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
        if (game.playerHasLeaderUnlockedOrAlliance(player, "argentcommander")) {
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
                .queue();
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
                .queue();
    }

    public static List<String> getBombardablePlanets(Player player, Game game, Tile tile) {
        List<String> planets = new ArrayList<>();
        for (UnitHolder planetUH : tile.getPlanetUnitHolders()) {
            if (!player.getPlanetsAllianceMode().contains(planetUH.getName())) {
                if (!((Planet) planetUH).getPlanetTypes().contains("cultural")
                        || !ButtonHelper.isLawInPlay(game, "conventions")) {
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
                    .append(player.getFactionEmoji())
                    .append(" BOMBARDMENT of ")
                    .append(Helper.getPlanetRepresentationNoResInf(planet, game))
                    .append(":\n");
            for (Player p2 : game.getRealAndEliminatedPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (FoWHelper.playerHasUnitsOnPlanet(p2, game.getUnitHolderFromPlanet(planet))) {
                    summary.append("-# ")
                            .append(p2.getFactionEmoji())
                            .append(" currently has ")
                            .append(ExploreHelper.getUnitListEmojisOnPlanetForHazardousExplorePurposes(
                                    game, p2, planet))
                            .append("\n");
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
                        summary.append("- ").append(mod.getUnitEmoji()).append("\n");
                    }
                }
            }
        }
        return summary.toString();
    }

    @ButtonHandler("bombardConfirm_")
    public static void bombardConfirm(ButtonInteractionEvent event, Player player, Game game) {
        if (getBombardablePlanets(player, game, game.getTileByPosition(game.getActiveSystem()))
                .isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation() + " there are no planets in this system that you can legally bombard. "
                            + "You cannot bombard planets you own, and you cannot bombard cultural planets if conventions of war law is in play");
            return;
        }
        autoAssignAllBombardmentToAPlanet(player, game);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentation() + " is assigning units to bombard as follows:\n"
                        + getBombardmentSummary(player, game),
                getBombardmentAssignmentButtons(player, game));
    }

    @ButtonHandler("deflectSC_")
    public static void deflectSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.split("_")[1];
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("deflectedSC", sc);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Put _Deflection_ on **" + Helper.getSCName(Integer.parseInt(sc), game) + "**.");
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
            String message =
                    "You don't have " + tgLoss + " trade good" + (tgLoss == 1 ? "" : "s") + ". No change made.";
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
        String typeNNameNTarget = buttonID.replace("sabotage_", "");
        String type = typeNNameNTarget.split("_")[0];
        String acName = typeNNameNTarget.split("_")[1];
        String target = "somebody";
        if (typeNNameNTarget.split("_").length > 2) {
            String faction = typeNNameNTarget.split("_")[2];
            Player p2 = game.getPlayerFromColorOrFaction(faction);
            target = p2.getRepresentationUnfogged();
        }
        String message = game.getPing() + ", the action card _" + acName + "_ played by " + target
                + " has been cancelled by " + player.getRepresentationUnfogged() + " with ";
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
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    "Remove the Watcher",
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "mech", true));
            ButtonHelper.deleteMessage(event);
        } else if ("xxcha".equalsIgnoreCase(type)) {
            message +=
                    "_Instinct Training_! The technology has been exhausted and a command token removed from strategy pool.";
            if (player.hasTech(AliasHandler.resolveTech("Instinct Training"))) {
                player.exhaustTech(AliasHandler.resolveTech("Instinct Training"));
                if (player.getStrategicCC() > 0) {
                    player.setStrategicCC(player.getStrategicCC() - 1);
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
                }
                ButtonHelper.deleteMessage(event);
            } else {
                sendReact = false;
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        "You clicked the _Instinct Training_ button but did not have the technology.");
            }
        } else if ("ac".equalsIgnoreCase(type)) {
            message += "a _Sabotage_!";
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
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        player.getRepresentation()
                                + ", you clicked the _Sabotage_ action card button but did not have a _Sabotage_ in hand.");
            }
        }

        if (acName.contains("Rider") || acName.contains("Sanction")) {
            AgendaHelper.reverseRider("reverse_" + acName, event, game, player);
        }
        if (sendReact) {
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(), game.getPing() + ", an action card has been cancelled.");
            } else {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
            }
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
        event.getMessage()
                .editMessage(exhaustedMessage)
                .setComponents(actionRow2)
                .queue();
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
                + ", you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        buttons = new ArrayList<>();
        buttons.add(Buttons.green("cymiaeHeroStep1_" + (game.getRealPlayers().size()), "Resolve Cymiae Hero"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please resuming resolving your hero after doing **Autonetic Memory** steps.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getRepairButtons_")
    public static void getRepairButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("getRepairButtons_", "");
        List<Button> buttons =
                ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game, game.getTileByPosition(pos));
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
            totalVotesSoFar = player.getFactionEmojiOrColor() + " Readied "
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        }
        if (!actionRow2.isEmpty()) {
            event.getMessage()
                    .editMessage(totalVotesSoFar)
                    .setComponents(actionRow2)
                    .queue();
        }
    }

    // @ButtonHandler("strategicAction_")
    public static void strategicAction(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel mainGameChannel) {
        int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
        PlayStrategyCardService.playSC(event, scNum, game, mainGameChannel, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("increaseTGonSC_")
    public static void increaseTGonSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.replace("increaseTGonSC_", "");
        int scNum = Integer.parseInt(sc);
        int newTradeGoodCount = game.addTradeGoodsToStrategyCard(scNum, 1);
        boolean useSingular = newTradeGoodCount == 1;
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Added 1 trade good to " + Helper.getSCName(scNum, game) + ". There " + (useSingular ? "is" : "are")
                        + " now " + newTradeGoodCount + " trade good" + (useSingular ? "" : "s") + " on it.");
    }

    @ButtonHandler("autoAssignAFBHits_")
    public static void autoAssignAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperModifyUnits.autoAssignAntiFighterBarrageHits(
                player, game, buttonID.split("_")[1], Integer.parseInt(buttonID.split("_")[2]), event);
    }

    @ButtonHandler("cancelAFBHits_")
    public static void cancelAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        String msg = "\n" + player.getRepresentationUnfogged() + " cancelled 1 hit with an ability.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + player.getFaction() + "_";
        buttons.add(Buttons.green(
                finChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_afb", "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelAFBHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = "You may automatically assign " + h + " ANTI-FIGHTER BARRAGE hit" + (h == 1 ? "" : "s") + ".";
        event.getMessage()
                .editMessage(msg2)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
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
        buttons.add(Buttons.green(
                finChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h,
                "Auto-Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_pds", "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelPdsOffenseHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentationNoPing() + ", you may automatically assign "
                + (h == 1 ? "the hit" : "hits") + ". "
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true, true);
        event.getMessage()
                .editMessage(msg2)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
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
        buttons.add(Buttons.green(
                finChecker + "autoAssignGroundHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_groundcombat",
                "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelGroundHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentation() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
        event.getMessage()
                .editMessage(msg2)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
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
        buttons.add(Buttons.green(
                finChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red(
                "getDamageButtons_" + tile.getPosition() + "_spacecombat",
                "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelSpaceHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = player.getRepresentationNoPing() + ", you may automatically assign "
                + (h == 1 ? "the hit" : "hits") + ". "
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true);
        event.getMessage()
                .editMessage(msg2)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
    }

    @ButtonHandler("autoAssignSpaceCannonOffenceHits_")
    public static void autoAssignSpaceCannonOffenceHits(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                        player,
                        game,
                        game.getTileByPosition(buttonID.split("_")[1]),
                        Integer.parseInt(buttonID.split("_")[2]),
                        event,
                        false,
                        true));
    }

    @ButtonHandler("autoAssignSpaceHits_")
    public static void autoAssignSpaceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                ButtonHelperModifyUnits.autoAssignSpaceCombatHits(
                        player,
                        game,
                        game.getTileByPosition(buttonID.split("_")[1]),
                        Integer.parseInt(buttonID.split("_")[2]),
                        event,
                        false));
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

    @ButtonHandler("toldarPN")
    public static void toldarPN(ButtonInteractionEvent event, Player player) {
        player.setCommodities(player.getCommodities() + 3);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " used _Concordat Allegiant_ (the Toldar promissory note)"
                        + " to gain 3 commodities after winning a combat against someone with more victory points than them. They can do this once per action. Their currently hold "
                        + player.getCommodities() + " commodit" + (player.getCommodities() == 1 ? "y" : "ies") + ".");
        ButtonHelper.deleteTheOneButton(event);
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
                if (!game.isFowMode() && game.getTableTalkChannel() != null) {
                    MessageHelper.sendMessageToChannel(
                            game.getTableTalkChannel(), "## End of Round #" + game.getRound() + " Scoring Info");
                    ListPlayerInfoService.displayerScoringProgression(game, true, game.getTableTalkChannel(), "both");
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
    public static void assignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = StringUtils.substringAfter(buttonID, "assignSpeaker_");
        game.setStoredValue("hasntSetSpeaker", "");
        if (!game.isFowMode()) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.getFaction().equals(faction)) {
                    game.setSpeakerUserID(player_.getUserID());
                    String message =
                            MiscEmojis.SpeakerToken + " Speaker assigned to: " + player_.getRepresentation(false, true);
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
                String message =
                        MiscEmojis.SpeakerToken + " Speaker assigned to: " + player_.getRepresentation(false, true);
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

    public static void poScoring(
            ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel privateChannel) {
        if (!"true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, privateChannel, game, player, poIndex);
                ReactionService.addReaction(event, game, player);
                if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
                    String msg = "Please score objectives.";
                    msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
                    event.getMessage().editMessage(msg).queue();
                }
            } catch (Exception e) {
                BotLogger.error(new LogOrigin(event, player), "Could not parse PO ID: " + poID, e);
                event.getChannel()
                        .sendMessage("Could not parse public objective ID: " + poID + ". Please score manually.")
                        .queue();
            }
            return;
        }
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key3b = "potentialScoreSOBlockers";
        String message;
        for (Player player2 : StatusHelper.GetPlayersInScoringOrder(game)) {
            if (player2 == player) {
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    game.setStoredValue(key2, game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                }

                String poID = buttonID.replace(Constants.PO_SCORING, "");
                int poIndex = Integer.parseInt(poID);
                ScorePublicObjectiveService.scorePO(event, privateChannel, game, player, poIndex);
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
                            .queue();
                }
                game.setStoredValue(key2, game.getStoredValue(key2) + player.getFaction() + "*");
                ReactionService.addReaction(event, game, player, message);
                break;
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()
                && !"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            String msg = "Please score objectives.";
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue();
        }
        if ("action".equalsIgnoreCase(game.getPhaseOfGame())) {
            event.getMessage().delete().queue();
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
        if (buttonID.contains("skilled")) {
            skilled = true;
            ButtonHelper.deleteMessage(event);
        }
        if (buttonID.contains("foresight")) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getFactionEmojiOrColor()
                            + ", you placed 1 command token from your strategy pool to resolve your "
                            + FactionEmojis.Naalu
                            + "**Foresight** ability.");
            player.setStrategicCC(player.getStrategicCC() - 1);
            skilled = true;
        }
        String message = player.getRepresentationUnfogged() + ", please choose a system to move to.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, pos, skilled));

        if (game.getTileByPosition(pos).isGravityRift() && !player.hasRelic("circletofthevoid")) {
            Button rift = Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "getRiftButtons_" + pos,
                    "Rift Units",
                    MiscEmojis.GravityRift);
            List<Button> buttons = new ArrayList<>();
            buttons.add(rift);
            String message2 =
                    player.getRepresentationUnfogged() + ", if applicable, use this button to rift retreating units.";
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
        FOWCombatThreadMirroring.mirrorMessage(
                event, game, player.getRepresentationNoPing() + " retreated all units in space.");
        String message =
                player.getRepresentationUnfogged() + ", please choose which ground forces you wish to retreat.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, game, pos1, pos2));
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
        ButtonHelper.deleteTheOneButton(event);
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please choose the target of your agent.",
                buttons);
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
                for (Player player2 : StatusHelper.GetPlayersInScoringOrder(game)) {
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
                            .queue();
                    return;
                }
            }
        } else {
            event.getChannel()
                    .sendMessage("Could not find channel to play card. Please ping Bothelper.")
                    .queue();
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
                        || "Done Losing Command Tokens".equalsIgnoreCase(buttonLabel))
                && editedMessage.contains("command tokens have gone from")) {

            String playerRep = player.getRepresentation();
            String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
            String shortCCs = editedMessage.substring(editedMessage.indexOf("command tokens have gone from "));
            shortCCs = shortCCs.replace("command tokens have gone from ", "");
            shortCCs = shortCCs.substring(0, shortCCs.indexOf(' '));
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
                            reasons = "**Versatile**";
                        }
                        if (player.hasTech("hm")) {
                            properGain += 1;
                            reasons += (properGain == 1 ? "" : ", ") + "_Hyper Metabolism_";
                        }
                        if (cyber) {
                            properGain += 1;
                            reasons += (properGain == 1 ? "" : ", ") + "_Cybernetic Enhancements_";
                        }
                        if (netGain < properGain && netGain != 1) {
                            MessageHelper.sendMessageToChannel(
                                    player.getCorrectChannel(),
                                    player.getRepresentationUnfogged()
                                            + ", heads up, bot thinks you should have gained " + properGain
                                            + " command token" + (properGain == 1 ? "" : "s") + " due to " + reasons
                                            + ".");
                        } else {
                            if (netGain == properGain && netGain > 2 && cyber) {
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
                        previousMessage.delete().queue();
                    }
                });

                int cost = Helper.calculateCostOfProducedUnits(player, game, true);
                game.setStoredValue("producedUnitCostFor" + player.getFaction(), "" + cost);
                player.setTotalExpenses(
                        player.getTotalExpenses() + Helper.calculateCostOfProducedUnits(player, game, true));
                String message2 = player.getRepresentationUnfogged()
                        + ", please choose the planets you wish to exhaust to pay a cost of " + cost + ".";
                boolean warM = player.getSpentThingsThisWindow().contains("warmachine");

                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
                // if (player.hasTechReady("sar") && !"muaatagent".equalsIgnoreCase(buttonID)
                //     && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                //     buttons.add(
                //         Buttons.red("exhaustTech_sar", "Exhaust Self-Assembly Routines", TechEmojis.WarfareTech));
                // } //sar is handled elsewhere
                if (player.hasTechReady("htp")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    buttons.add(Buttons.red("exhaustTech_htp", "Exhaust Hegemonic Trade Policy", FactionEmojis.Winnu));
                }
                if (game.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")
                        && !buttonID.contains("generic")) {
                    ButtonHelperCommanders.titansCommanderUsage(event, game, player);
                }
                if (player.hasTechReady("dsbenty")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.green("exhaustTech_dsbenty", "Use Merged Replicators", FactionEmojis.bentor));
                }
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 0
                        && player.hasTechReady("aida")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red(
                            "exhaustTech_aida",
                            "Exhaust AI Development Algorithm (" + ButtonHelper.getNumberOfUnitUpgrades(player) + "r)",
                            TechEmojis.WarfareTech));
                }
                if (player.hasTechReady("st")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("useTech_st", "Use Sarween Tools", TechEmojis.CyberneticTech));
                }
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    buttons.add(Buttons.red("useRelic_boon", "Use Boon Of The Cerulean God Relic"));
                }
                if (player.hasTechReady("absol_st")) {
                    buttons.add(Buttons.red("useTech_absol_st", "Use Sarween Tools"));
                }
                if (player.hasUnexhaustedLeader("winnuagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red("exhaustAgent_winnuagent", "Use Winnu Agent", FactionEmojis.Winnu));
                }
                if (player.hasUnexhaustedLeader("gledgeagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_gledgeagent_" + player.getFaction(),
                            "Use Gledge Agent",
                            FactionEmojis.gledge));
                }
                if (player.hasUnexhaustedLeader("uydaiagent")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                        && !buttonID.contains("integrated")) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_uydaiagent_" + player.getFaction(), "Use Uydai Agent", FactionEmojis.gledge));
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
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    buttons.add(Buttons.red(
                            "exhaustAgent_rohdhnaagent_" + player.getFaction(),
                            "Use Roh'Dhna Agent",
                            FactionEmojis.rohdhna));
                }
                if (player.hasLeaderUnlocked("hacanhero")
                        && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)
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
                            || buttonID.contains("anarchy7Build")
                            || buttonID.contains("lumi7Build"))) {
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
                ButtonHelper.exploreDET(player, game, event);
                ButtonHelperFactionSpecific.cleanCavUp(game, event);
                if (player.hasAbility("cunning")) {
                    List<Button> trapButtons = new ArrayList<>();
                    for (Planet uH :
                            game.getTileByPosition(game.getActiveSystem()).getPlanetUnitHolders()) {
                        String planet = uH.getName();
                        trapButtons.add(
                                Buttons.gray("setTrapStep3_" + planet, Helper.getPlanetRepresentation(planet, game)));
                    }
                    trapButtons.add(Buttons.red("deleteButtons", "Decline"));
                    String msg = player.getRepresentationUnfogged()
                            + ", you may use the buttons to place a trap on a planet.";
                    if (trapButtons.size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, trapButtons);
                    }
                }
                if (player.hasUnexhaustedLeader("celdauriagent")) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.gray(
                            "exhaustAgent_celdauriagent_" + player.getFaction(),
                            "Use Celdauri Agent",
                            FactionEmojis.celdauri));
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged() + ", you may use "
                                    + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                                    + "George Nobin, the Celdauri"
                                    + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                                    + " agent, to place 1 space dock for 2 trade goods or 2 commodities.",
                            buttons);
                }
                List<Button> systemButtons2 = new ArrayList<>();
                if (!game.isAbsolMode()
                        && player.getRelics().contains("emphidia")
                        && !player.getExhaustedRelics().contains("emphidia")) {
                    String message = player.getRepresentationUnfogged()
                            + ", you may explore a planet using _The Crown of Emphidia_.";
                    systemButtons2.add(Buttons.green("crownofemphidiaexplore", "Use Crown of Emphidia To Explore"));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("sardakkagent")) {
                    String message = player.getRepresentationUnfogged() + ", you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "T'ro, the N'orr" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent.";
                    systemButtons2.addAll(ButtonHelperAgents.getSardakkAgentButtons(game));
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
                ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
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
        mainMessage.clearReactions().queue();
    }

    public static void checkForAllReactions(@NotNull ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getId();

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

    private static void respondAllPlayersReacted(ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getId();
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
                    .queue();
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
            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> {
                String message2 =
                        "All players have indicated scoring. Flip the relevant public objective using the buttons. This will automatically run status clean-up if it has not been run already.";
                Button draw2Stage2 = Buttons.green("reveal_stage_2x2", "Reveal 2 Stage 2");
                Button drawStage2 = Buttons.green("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Buttons.green("reveal_stage_1", "Reveal Stage 1");
                List<Button> buttons = new ArrayList<>();
                if (game.isRedTapeMode() || game.isCivilizedSocietyMode()) {
                    message2 =
                            "All players have indicated scoring. In this game mode, no objective is revealed at this stage."
                                    + " Please press one of the buttons below anyways though - don't worry, it won't reveal anything, it will just run cleanup.";
                }
                if (game.getRound() < 4 || !game.getPublicObjectives1Peakable().isEmpty()) {
                    buttons.add(drawStage1);
                }
                if ((game.getRound() > 3 || game.getPublicObjectives1Peakable().isEmpty())
                        && !game.isOmegaPhaseMode()) {
                    if ("456".equalsIgnoreCase(game.getStoredValue("homebrewMode"))) {
                        buttons.add(draw2Stage2);
                    } else {
                        buttons.add(drawStage2);
                    }
                }
                var endGameDeck = game.isOmegaPhaseMode()
                        ? game.getPublicObjectives1Peakable()
                        : game.getPublicObjectives2Peakable();
                var endGameRound = game.isOmegaPhaseMode() ? 9 : 7;
                if (game.getRound() > endGameRound || endGameDeck.isEmpty()) {
                    if (game.isFowMode()) {
                        message2 +=
                                "\n> - If there are no more objectives to reveal, use the button to continue as is.";
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
            case "pass_on_abilities" -> {
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getChannel(), "Please flip agenda now.", buttons);
                } else {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            game.getPing()
                                    + ", all players have indicated completion of Status Phase. Proceeding to Strategy Phase.");
                    StartPhaseService.startPhase(event, game, "strategy");
                }
                if (game.isFowMode()) {
                    game.setStoredValue("fowStatusDone", "");
                }
            }
            case "redistributeCCButtons" -> {
                if (game.isCustodiansScored() || game.isOmegaPhaseMode()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Buttons.blue("flip_agenda", "Flip Agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getChannel(),
                            "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                                    + " Please press the \"Flip Agenda\" button after they have finished redistributing tokens and you have fully resolved all other Status Phase effects.",
                            buttons);
                } else {
                    Button flipAgenda = Buttons.blue("startStrategyPhase", "Start Strategy Phase");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(
                            event.getChannel(),
                            "This message was triggered by the last player pressing \"Redistribute Command Tokens\"."
                                    + " As the Custodians token is still on Mecatol Rex, there will be no Agenda Phase this round."
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

    @ButtonHandler("placeHolderOfConInSystem_")
    public static void placeHolderOfConInSystem(
            GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace("placeHolderOfConInSystem_", "");
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
        Player constructionPlayer = player;
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(4)) {
                constructionPlayer = p2;
            }
        }
        CommandCounterHelper.addCC(event, constructionPlayer, tile);

        String colorName = Mapper.getColor(constructionPlayer.getColor()).getDisplayName();
        String message = player.getRepresentation() + " placed 1 " + colorName + " command token in the "
                + Helper.getPlanetRepresentation(planet, game)
                + " system due to use of " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                + " agent on **Construction**.";
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startYinSpinner")
    public static void startYinSpinner(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(
                event.getChannel(), player.getRepresentationNoPing() + " is using _Yin Spinner_.");
        List<Button> buttons =
                new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
        buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
        String message = "Use buttons to drop 2 infantry on a planet or with your ships.";
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
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

    @ButtonHandler("corefactoryAction")
    public static void coreFactoryAction(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteMessage(event);
        String tPlanet = "";
        for (String planet : player.getPlanets()) {
            Planet uH = game.getUnitHolderFromPlanet(planet);
            if (uH == null) {
                continue;
            }
            Set<String> tokens = new HashSet<>(uH.getTokenList());
            for (String token : tokens) {
                if (token.contains("corefactory")) {
                    uH.removeToken(token);
                    tPlanet = planet;
                }
            }
        }
        Planet uH = game.getUnitHolderFromPlanet(tPlanet);
        List<Button> facilities = new ArrayList<>();
        List<String> usedFacilities = ButtonHelperSCs.findUsedFacilities(game, player);

        String facilityID = "facilitytransitnode";
        if (!usedFacilities.contains(facilityID)) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Transit Node"));
        }
        facilityID = "facilityresearchlab";
        if (!usedFacilities.contains(facilityID) && !uH.getTechSpecialities().isEmpty()) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Research Lab"));
        }
        facilityID = "facilitynavalbase";
        if (!usedFacilities.contains(facilityID)
                && (uH.getPlanetTypes().contains("industrial") || "mr".equalsIgnoreCase(tPlanet) || uH.isLegendary())) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Naval Base"));
        }
        facilityID = "facilitylogisticshub";
        if (!usedFacilities.contains(facilityID)
                && (uH.getPlanetTypes().contains("industrial")
                        || uH.getPlanetTypes().contains("hazardous"))) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Logistics Hub"));
        }
        facilityID = "facilityembassy";
        boolean hasEmbassy = false;
        for (String fac : usedFacilities) {
            if (fac.contains("facilityembassy")) {
                hasEmbassy = true;
                break;
            }
        }
        if (!hasEmbassy && (uH.getPlanetTypes().contains("industrial") || "mr".equalsIgnoreCase(tPlanet))) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Embassy"));
        }
        int colonies = 0;
        facilityID = "facilitycolony";
        for (String fac : usedFacilities) {
            if (fac.contains(facilityID)) {
                colonies++;
            }
        }
        if (colonies < 2) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Colony"));
        }
        colonies = 0;
        facilityID = "facilityrefinery";
        for (String fac : usedFacilities) {
            if (fac.contains(facilityID)) {
                colonies++;
            }
        }
        if (colonies < 2) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Refinery"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose the facility you wish to replace the Core Factory.",
                facilities);

        doAnotherAction(event, player, game);
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

    @ButtonHandler("pay1tgToAnnounceARetreat")
    public static void pay1tgToAnnounceARetreat(ButtonInteractionEvent event, Player player) {
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " paid 1 trade good to announce a retreat " + player.gainTG(-1)
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("announceReadyForDice_")
    public static void announceReadyForDice(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String p1Color = buttonID.split("_")[1];
        Player p1 = game.getPlayerFromColorOrFaction(p1Color);
        if (p1 == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Unable to determine player for color or faction `" + p1Color + "`.");
            return;
        }
        String p2Color = buttonID.split("_")[2];
        Player p2 = game.getPlayerFromColorOrFaction(p2Color);
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Unable to determine player for color or faction `" + p2Color + "`.");
            return;
        }
        if (player != p1 && player != p2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", don't press buttons that aren't meant for you.");
            return;
        }
        String msg = ", your opponent has declared they are ready to roll combat dice if you are.";
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
            msg +=
                    "\n> Since they have Jack Hallard, the Nokar commander, this means they may cancel 2 hits in this coming combat round.";
        }
        String combatName =
                "combatRoundTracker" + game.getActivePlayer().getFaction() + game.getActiveSystem() + "space";
        if (game.getActivePlayer() != null
                && game.getActivePlayer() != player
                && game.getActivePlayer().hasAbility("cargo_raiders")
                && game.getStoredValue(combatName).isEmpty()) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("pay1tgToAnnounceARetreat", "Pay 1 Trade Good"));
            buttons.add(Buttons.red("deleteButtons", "I Don't Have to Pay"));
            String raiders = "\n" + player.getRepresentation()
                    + ", a reminder that your opponent has the **Cargo Raiders** ability,"
                    + " which means you might have to pay 1 trade good to announce a retreat if they choose.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg + raiders, buttons);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        }
        FOWCombatThreadMirroring.mirrorMessage(event, game, msg.replace("## ", ""));

        if (Helper.getCCCount(game, player.getColor()) > 15) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", a reminder that you are at the command token limit right now,"
                            + " so may need to pull a command token off your command sheet in order to retreat (unless you retreat to a system that already has one).");
        }
    }

    @ButtonHandler("doAnotherAction")
    @ButtonHandler("finishComponentAction")
    private static void doAnotherAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
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

    @ButtonHandler("useLawsOrder")
    public static void useLawsOrder(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmojiOrColor()
                        + " is paying 1 trade good or 1 commodity to ignore laws for the turn.");
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Please spend 1 commodity or 1 trade good.", buttons);
        ButtonHelper.deleteTheOneButton(event);
        game.setStoredValue("lawsDisabled", "yes");
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
        buttons.add(Buttons.gray("checkUnlocked", "Show Only Unlocked Units"));
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
        event.getMessage()
                .editMessage(exhaustedMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
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
        event.getMessage()
                .editMessage(exhaustedMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
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
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("run_status_cleanup")
    public static void runStatusCleanup(ButtonInteractionEvent event, Game game, Player player) {
        StatusCleanupService.runStatusCleanup(game);
        ButtonHelper.deleteTheOneButton(event);
        ReactionService.addReaction(
                event, game, player, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
    }

    @ButtonHandler("willRevolution")
    public static void willRevolution(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("willRevolution", "active");
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Reversed strategy card picking order.");
    }

    public static void lastMinuteDeliberation(
            ButtonInteractionEvent event, Player player, Game game, MessageChannel actionsChannel) {
        ButtonHelper.deleteMessage(event);
        String message = player.getRepresentation() + ", please choose the (up to) 2 planets you wish to ready.";
        List<Button> buttons = Helper.getPlanetRefreshButtons(player, game);
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying Planets")); // spitItOut
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        AgendaHelper.revealAgenda(event, false, game, actionsChannel);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Sent buttons to ready 2 planets to the player who pressed the button.");
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
        String planetName = labelP.substring(labelP.lastIndexOf(' ') + 1);
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

    @ButtonHandler("decrease_fleet_cc")
    public static void decreaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() - 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("decrease_tactic_cc")
    public static void decreaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() - 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
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
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_fleet_cc")
    public static void increaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
        if (ButtonHelper.isLawInPlay(game, "regulations") && player.getFleetCC() > 4) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " reminder that under the _Fleet Regulations_ law, fleet pools are limited to 4 command tokens.");
        }
    }

    @ButtonHandler("resetCCs")
    public static void resetCCs(ButtonInteractionEvent event, Player player, Game game) {
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        ButtonHelper.resetCCs(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: 0.";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_tactic_cc")
    public static void increaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("resetProducedThings")
    public static void resetProducedThings(ButtonInteractionEvent event, Player player, Game game) {
        Helper.resetProducedUnits(player, game, event);
        event.getMessage()
                .editMessage(Helper.buildProducedUnitsMessage(player, game))
                .queue();
    }

    @ButtonHandler("yssarilMinisterOfPolicy")
    public static void yssarilMinisterOfPolicy(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " is drawing their _Minister of Policy_ action card.");
        String message;
        if (player.hasAbility("scheming")) {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            message =
                    player.getFactionEmoji() + " drew 2 action cards with **Scheming**. Please discard 1 action card.";
            ActionCardHelper.sendActionCardInfo(game, player, event);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
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
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("proceed_to_strategy")
    public static void proceedToStrategy(ButtonInteractionEvent event, Game game) {
        String readiedCardsString = "All planets have been readied at the end of the Agenda Phase.";
        if (game.isOmegaPhaseMode()) {
            readiedCardsString = "All cards have been readied at the end of the Omega Phase.";
        }
        if (game.hasAnyPriorityTrackMode()) {
            if (PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Please fill the priority track before starting the Strategy Phase.");
                PriorityTrackHelper.PrintPriorityTrack(game);
                return;
            }
        }
        Map<String, Player> players = game.getPlayers();
        if (game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
            for (Player player_ : players.values()) {
                RefreshCardsService.refreshPlayerCards(game, player_, false);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), readiedCardsString);
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
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

    @ButtonHandler("lose1CC")
    public static void lose1CC(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + "! Your current command tokens are "
                + player.getCCRepresentation() + ".";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());

        String finChecker = player.finChecker();
        Button loseTactic = Buttons.red(finChecker + "decrease_tactic_cc", "Lose 1 Tactic Token");
        Button loseFleet = Buttons.red(finChecker + "decrease_fleet_cc", "Lose 1 Fleet Token");
        Button loseStrat = Buttons.red(finChecker + "decrease_strategy_cc", "Lose 1 Strategy Token");
        Button doneGainingCC = Buttons.blue(finChecker + "deleteButtons_spitItOut", "Done Losing 1 Command Token");
        Button resetCC = Buttons.gray(finChecker + "resetCCs", "Reset Command Tokens");

        List<Button> buttons = Arrays.asList(loseTactic, loseFleet, loseStrat, doneGainingCC, resetCC);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentation() + " has chosen to lose 1 command token.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("redistributeCCButtons") // Buttons.REDISTRIBUTE_CCs
    public static void redistributeCCButtons(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + "! Your current command tokens are "
                + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());

        String finChecker = player.finChecker();
        Button getTactic = Buttons.green(finChecker + "increase_tactic_cc", "Gain 1 Tactic Token");
        Button getFleet = Buttons.green(finChecker + "increase_fleet_cc", "Gain 1 Fleet Token");
        Button getStrat = Buttons.green(finChecker + "increase_strategy_cc", "Gain 1 Strategy Token");
        Button loseTactic = Buttons.red(finChecker + "decrease_tactic_cc", "Lose 1 Tactic Token");
        Button loseFleet = Buttons.red(finChecker + "decrease_fleet_cc", "Lose 1 Fleet Token");
        Button loseStrat = Buttons.red(finChecker + "decrease_strategy_cc", "Lose 1 Strategy Token");
        Button doneGainingCC = Buttons.blue(finChecker + "deleteButtons", "Done Redistributing Command Tokens");
        Button resetCC = Buttons.gray(finChecker + "resetCCs", "Reset Command Tokens");

        List<Button> buttons =
                Arrays.asList(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat, doneGainingCC, resetCC);
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
            for (Player p2 : game.getRealPlayers()) {
                if (p2.isNpc() && !game.getCurrentACDrawStatusInfo().contains(p2.getFaction())) {
                    ButtonHelper.drawStatusACs(game, p2, event);
                    ReactionService.addReaction(event, game, p2);
                }
            }
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
                    reasons += (properGain == 1 ? "" : ", ") + "_Hyper Metabolism_";
                }
                if (cyber) {
                    properGain += 1;
                    reasons += (properGain == 1 ? "" : ", ") + "_Cybernetic Enhancements_";
                }
                if (properGain > 2) {
                    MessageHelper.sendMessageToChannel(
                            player.getCardsInfoThread(),
                            "## " + player.getRepresentationUnfogged()
                                    + ", heads up, the bot thinks you should gain " + properGain + " command token"
                                    + (properGain == 1 ? "" : "s") + " now due to: " + reasons + ".");
                }
            }
            if (game.isCcNPlasticLimit()) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        "Your highest fleet count in a system is currently "
                                + ButtonHelper.checkFleetInEveryTile(player, game)
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
        if (game.hasAnyPriorityTrackMode()
                && PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Please fill the priority track before starting the Strategy Phase.");
            PriorityTrackHelper.PrintPriorityTrack(game);
            return;
        }
        StartPhaseService.startPhase(event, game, "strategy");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("deployTyrant")
    public static void deployTyrant(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to place the _Tyrant's Lament_ with your ships.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "tyrantslament", "placeOneNDone_skipbuild"));
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " is deploying the _Tyrant's Lament_.");
        player.addOwnedUnitByID("tyrantslament");
    }

    @ButtonHandler("nekroTechExhaust")
    public static void nekroTechExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", please choose the planets you wish to exhaust.";
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
        String message = player.getRepresentationUnfogged() + ", please choose the planets you wish to exhaust.";
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
                // Helper.resolveSOScoringQueue(game, event);
            }
        }
        if (!game.getStoredValue("newStatusScoringMode").isEmpty()) {
            String msg = "Please score objectives.";
            msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
            event.getMessage().editMessage(msg).queue();
        }
        ReactionService.addReaction(event, game, player);
        // checkForAllReactions(event, game);
    }

    @ButtonHandler(value = "refreshStatusSummary", save = false)
    public static void refreshStatusSummary(ButtonInteractionEvent event, Game game) {
        String msg = "Please score objectives.";
        msg += "\n" + Helper.getNewStatusScoringRepresentation(game);
        event.getMessage().editMessage(msg).queue();
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
            event.getMessage().editMessage(msg).queue();
        }
    }

    @ButtonHandler("applytempcombatmod__" + "tech" + "__")
    public static void applytempcombatmodtech(ButtonInteractionEvent event, Player player) {
        String acAlias = "sc";
        TemporaryCombatModifierModel combatModAC =
                CombatTempModHelper.getPossibleTempModifier("tech", acAlias, player.getNumberOfTurns());
        if (combatModAC != null) {
            player.addNewTempCombatMod(combatModAC);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getFactionEmoji()
                            + ", +1 modifier will be applied the next time you push the combat roll button due to _Supercharge_.");
        }
        player.exhaustTech("sc");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("rollIxthian")
    public static void rollIxthian(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (game.getSpeakerUserID().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
            AgendaHelper.rollIxthian(game, true);
        } else {
            Button ixthianButton =
                    Buttons.green("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact", PlanetEmojis.Mecatol);
            String msg = "The speaker should roll for _Ixthain Artifact_. Click this button to roll anyway!";
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
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        List<Button> systemButtons = TacticalActionService.getLandingTroopsButtons(game, player, tile);
        event.getMessage()
                .editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons))
                .queue();
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
                event.getMessageChannel(), "Use buttons to remove token.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("primaryOfWarfare")
    public static void primaryOfWarfare(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "warfare");
        MessageChannel channel = player.getCorrectChannel();
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
    }

    @ButtonHandler("drawAgenda_2")
    public static void drawAgenda2(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.getStoredValue("hasntSetSpeaker").isEmpty() && !game.isHomebrewSCMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you need to assign speaker first before drawing agendas. You can override this restriction with `/agenda draw`.");
            return;
        }
        AgendaHelper.drawAgenda(2, game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentation(true, false) + " drew 2 agendas");
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
                && PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
            PriorityTrackHelper.CreateDefaultPriorityTrack(game);
            if (PriorityTrackHelper.GetPriorityTrack(game).stream().anyMatch(Objects::isNull)) {
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
        SwapFactionService.secondHalfOfSwap(
                game, player, game.getPlayerFromColorOrFaction(faction), event.getUser(), event);
    }
}
