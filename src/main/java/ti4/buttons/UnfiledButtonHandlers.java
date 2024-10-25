package ti4.buttons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.agenda.PutAgendaBottom;
import ti4.commands.agenda.PutAgendaTop;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.DrawAC;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.PlayPN;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.combat.StartCombat;
import ti4.commands.explore.ExploreFrontier;
import ti4.commands.explore.ExplorePlanet;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.game.StartPhase;
import ti4.commands.game.Swap;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.Pass;
import ti4.commands.player.SCPlay;
import ti4.commands.player.TurnEnd;
import ti4.commands.player.TurnStart;
import ti4.commands.relic.RelicDraw;
import ti4.commands.special.FighterConscription;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands.units.AddUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.ExploreHelper;
import ti4.helpers.Helper;
import ti4.helpers.PlayerPreferenceHelper;
import ti4.helpers.PlayerTitleHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;

/*
 * Buttons methods which were factored out of {@link ButtonListener} which need to be filed away somewhere more appropriate
 */
public class UnfiledButtonHandlers { // TODO: move all of these methods to a better location, closer to the orignal button call and/or other related code

    @ButtonHandler("purgeKortaliHero_")
    public static void purgeKortaliHero(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Leader playerLeader = player.unsafeGetLeader("kortalihero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + " - Queen Nadalia, the Kortali hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Queen Nadalia, the Kortali hero, was not purged - something went wrong.");
        }
        ButtonHelperHeroes.offerStealRelicButtons(game, player, buttonID, event);
    }

    @ButtonHandler("declareUse_")
    public static void declareUse(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String msg = player.getFactionEmojiOrColor() + " is using " + buttonID.split("_")[1];
        if (msg.contains("Vaylerian")) {
            msg = msg + " to add +2 capacity to a ship with capacity";
        }
        if (msg.contains("Tnelis")) {
            msg = msg
                + " to apply 1 hit against their **non-fighter** ships in the system and give **1** of their ships a +1 boost. This ability may only be used once per activation.";
            String pos = buttonID.split("_")[2];
            List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game,
                game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                player.getRepresentationUnfogged() + " Use buttons to assign 1 hit", buttons);
            game.setStoredValue("tnelisCommanderTracker", player.getFaction());
        }
        if (msg.contains("Ghemina")) {
            msg = msg + " to gain 1TG after winning the space combat";
            player.setTg(player.getTg() + 1);
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
            ButtonHelperAbilities.pillageCheck(player, game);
        }
        if (msg.contains("Lightning")) {
            msg = msg + " Drives to boost the move value of each unit not transporting fighters or infantry by 1";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("unlockCommander_")
    public static void unlockCommander(ButtonInteractionEvent event, Player player, String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        CommanderUnlockCheck.checkPlayer(player, buttonID.split("_")[1]);
    }

    @ButtonHandler("fogAllianceAgentStep3_")
    public static void fogAllianceAgentStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperHeroes.argentHeroStep3(game, player, event, buttonID);
    }

    @ButtonHandler("requestAllFollow_")
    public static void requestAllFollow(ButtonInteractionEvent event, Game game) {
        event.getMessage().reply(game.getPing() + " someone has requested that everyone resolve this SC before play continues. Please do so as soon as you can. The active player should not take an action until this is done").queue();
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
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to select the units you want to remove.", systemButtons);
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
        List<Button> buttons = ButtonHelperFactionSpecific.getButtonsToTakeSomeonesAC(game, player, victim);
        ShowAllAC.showAll(victim, player, game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged() + " Select which AC you would like to steal", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ring_")
    public static void ring(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        List<Button> ringButtons = ButtonHelper.getTileInARing(player, game, buttonID, event);
        String num = buttonID.replace("ring_", "");
        String message;
        if (!"corners".equalsIgnoreCase(num)) {
            int ring = Integer.parseInt(num.charAt(0) + "");
            if (ring > 4 && !num.contains("left") && !num.contains("right")) {
                message = "That ring is very large. Specify if your tile is on the left or right side of the map (center will be counted in both).";
            } else {
                message = "Click the tile that you want to activate.";
            }
        } else {
            message = "Click the tile that you want to activate.";
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        ButtonHelper.deleteMessage(event);
    }

    public static void miltyFactionInfo(Player player, String buttonID, Game game) {
        String remainOrPicked = buttonID.replace("miltyFactionInfo_", "");
        List<FactionModel> displayFactions = new ArrayList<>();
        switch (remainOrPicked) {
            case "all" -> displayFactions.addAll(game.getMiltyDraftManager().allFactions());
            case "picked" -> displayFactions.addAll(game.getMiltyDraftManager().pickedFactions());
            case "remaining" -> displayFactions.addAll(game.getMiltyDraftManager().remainingFactions());
        }

        boolean first = true;
        List<MessageEmbed> embeds = displayFactions.stream().map(FactionModel::fancyEmbed).toList();
        for (MessageEmbed e : embeds) {
            String message = "";
            if (first)
                message = player.getRepresentationUnfogged() + " Here's an overview of the factions:";
            MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
            first = false;
        }
    }

    @ButtonHandler("garboziaAbilityExhaust_")
    public static void garboziaAbilityExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String planet = "garbozia";
        player.exhaustPlanetAbility(planet);
        new ExplorePlanet().explorePlanet(event, game.getTileFromPlanet(planet), planet, "INDUSTRIAL", player, true, game, 1, false);
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

    @ButtonHandler("genericModify_")
    public static void genericModify(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModify_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.offerBuildOrRemove(player, game, event, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getModifyTiles")
    public static void getModifyTiles(Player player, Game game) {
        List<Button> buttons = ButtonHelper.getTilesToModify(player, game);
        String message = player.getRepresentation() + " Use the buttons to select the tile in which you wish to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("genericReact")
    public static void genericReact(ButtonInteractionEvent event, Game game) {
        String message = game.isFowMode() ? "Turned down window" : null;
        ButtonHelper.addReaction(event, false, false, message, "");
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
            + " Using " + Emojis.CyberneticTech + "**Integrated Economy** on " + Helper.getPlanetRepresentation(planet, game)
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
        new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planet)), unit + " " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " Placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, game));
    }

    @ButtonHandler("removeAllStructures_")
    public static void removeAllStructures(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        plan.removeAllUnits(player.getColor());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Removed all units on " + planet + " for " + player.getRepresentation());
        AddRemoveUnits.addPlanetToPlayArea(event, game.getTileFromPlanet(planet), planet, game);
    }

    @ButtonHandler("jrStructure_")
    public static void jrStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("jrStructure_", "");
        if (!"tg".equalsIgnoreCase(unit)) {
            String message = player.getRepresentationUnfogged() + " Click the name of the planet you wish to put your unit on";
            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "placeOneNDone_dontskip");
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            }
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmojiOrColor() + " TGs increased by 1 " + player.gainTG(1));
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dacxive_")
    public static void daxcive(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("dacxive_", "");
        new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planet)), "infantry " + planet, game);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor() + " placed 1 infantry on " + Helper.getPlanetRepresentation(planet, game) + " via the tech Dacxive Animators");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("glimmersHeroOn_")
    public static void glimmerHeroOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos), unit, game);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor() + " chose to duplicate a " + unit + " in " + game.getTileByPosition(pos).getRepresentationForButtons(game, player));
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
            if (player.hasAbility("quash") || player.ownsPromissoryNote("rider")
                || player.getPromissoryNotes().containsKey("riderm")
                || player.hasAbility("radiance") || player.hasAbility("galactic_threat")
                || player.hasAbility("conspirators")
                || player.ownsPromissoryNote("riderx")
                || player.ownsPromissoryNote("riderm") || player.ownsPromissoryNote("ridera")
                || player.hasTechReady("gr")) {
            } else {
                List<Button> buttons = new ArrayList<>();
                String msg = player.getRepresentation()
                    + " The bot may also auto react for you when you have no whens/afters, using the same interval. Default for this is off. This will only apply to this game. If you have any whens or afters or related when/after abilities, it will not do anything. ";
                buttons.add(Buttons.green("playerPrefDecision_true_agenda", "Turn on"));
                buttons.add(Buttons.green("playerPrefDecision_false_agenda", "Turn off"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("glimmersHeroIn_")
    public static void glimmersHeroIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperHeroes.getUnitsToGlimmersHero(player, game, event, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), player.getRepresentationUnfogged() + " select which unit you'd like to duplicate", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("arboAgentIn_")
    public static void arboAgentIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game, event, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), player.getRepresentationUnfogged() + " select which unit you'd like to replace", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ghotiHeroIn_")
    public static void ghotiHeroIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game, event, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), player.getRepresentationUnfogged() + " select which unit you'd like to replace", buttons);
        ButtonHelper.deleteTheOneButton(event);
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
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
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
            player.getFaction() + " used X-89 Bacterial Weapon to remove all ground forces on " + planet);
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        Map<UnitKey, Integer> units = new HashMap<>();
        units.putAll(uH.getUnits());
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
            String useMessage = player.getRepresentation() + " used tech: " + techModel.getRepresentation(false);
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
                absolPAButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard", Emojis.ActionCard));
                for (String planetID : player.getReadiedPlanets()) {
                    Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetID, game);
                    if (planet != null && planet.getOriginalPlanetType() != null) {
                        List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
                        absolPAButtons.addAll(planetButtons);
                    }
                }
                ButtonHelper.deleteTheOneButton(event);
                MessageHelper
                    .sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " use buttons to discard 1 AC and explore a readied planet",
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

    @ButtonHandler("forceAbstainForPlayer_")
    public static void forceAbstainForPlayer(ButtonInteractionEvent event, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Player was forcefully abstained");
        String faction = buttonID.replace("forceAbstainForPlayer_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        AgendaHelper.resolvingAnAgendaVote("resolveAgendaVote_0", event, game, p2);
    }

    @ButtonHandler("combatRoll_")
    public static void combatRoll(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.resolveCombatRoll(player, game, event, buttonID);
        if (buttonID.contains("bombard")) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    @ButtonHandler("bombardConfirm_")
    public static void bombardConfirm(ButtonInteractionEvent event, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(buttonID.replace("bombardConfirm_", ""), "Roll Bombardment"));
        String message = player.getRepresentationUnfogged()
            + " please declare what units are bombarding what planet before hitting this button"
            + " (e.g. if you have two dreadnoughts and are splitting their bombardment across two planets, specify which planet the first one is hitting)."
            + " The bot does not track this.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler("deflectSC_")
    public static void deflectSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.split("_")[1];
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("deflectedSC", sc);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Put Deflection on " + sc);
    }

    @ButtonHandler("finishComponentAction_")
    public static void finishComponentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
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
        new ExploreFrontier().expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player, cardChosen);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("reduceComm_")
    public static void reduceComm(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        String message = player.getFactionEmojiOrColor() + " reduced comms by " + tgLoss + " (" + player.getCommodities() + "->"
            + (player.getCommodities() - tgLoss) + ")";

        if (tgLoss > player.getCommodities()) {
            message = "You don't have " + tgLoss + " comm" + (tgLoss == 1 ? "" : "s") + ". No change made.";
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
            String message = "You don't have " + tgLoss + " TG" + (tgLoss == 1 ? "" : "s") + ". No change made.";
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
        String message = "Cancelling the AC \"" + acName + "\" using ";
        Integer count = game.getAllActionCardsSabod().get(acName);
        if (count == null) {
            game.setSpecificActionCardSaboCount(acName, 1);
        } else {
            game.setSpecificActionCardSaboCount(acName, 1 + count);
        }
        if (game.getMessageIDsForSabo().contains(event.getMessageId())) {
            game.removeMessageIDForSabo(event.getMessageId());
        }
        boolean sendReact = true;
        if ("empy".equalsIgnoreCase(type)) {
            message += "a Watcher mech! The Watcher should be removed now by the owner.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Remove the watcher",
                ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "mech"));
            ButtonHelper.deleteMessage(event);
        } else if ("xxcha".equalsIgnoreCase(type)) {
            message += "the \"Instinct Training\" tech! The tech has been exhausted and a strategy CC removed.";
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
                    "Someone clicked the Instinct Training button but did not have the tech.");
            }
        } else if ("ac".equalsIgnoreCase(type)) {
            message += "A Sabotage!";
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
                PlayAC.playAC(event, game, player, saboID, game.getActionsChannel());
            } else {
                message = "Tried to play a Sabo but found none in hand.";
                sendReact = false;
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + " You clicked the AC Sabo button but did not have a Sabotage in hand.");
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
                    + " due to the arcane citadel";
                new AddUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + planetName, game);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        if (whatIsItFor.contains("tech") && player.hasAbility("ancient_knowledge")) {
            String planet = planetName;
            if ((Mapper.getPlanet(planet).getTechSpecialties() != null
                && Mapper.getPlanet(planet).getTechSpecialties().size() > 0)
                || ButtonHelper.checkForTechSkips(game, planet)) {
                String msg = player.getRepresentation()
                    + " due to your ancient knowledge ability, you may be eligible to receive a tech here if you exhausted this planet ("
                    + planet
                    + ") for its tech skip";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue("gain_1_comms", "Gain 1 Commodity", Emojis.comm));
                buttons.add(Buttons.red("deleteButtons", "Didn't use it for tech speciality"));
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getFactionEmoji()
                        + " may have the opportunity to gain a comm from their ancient knowledge ability due to exhausting a tech skip planet");
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg,
                    buttons);
            }
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
    }

    @ButtonHandler("getAllTechOfType_")
    public static void getAllTechOfType(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String techType = buttonID.replace("getAllTechOfType_", "");
        String payType = null;
        if (techType.contains("_")) {
            final String[] split = techType.split("_");
            techType = split[0];
            payType = split[1];
        }
        List<TechnologyModel> techs = Helper.getAllTechOfAType(game, techType, player);

        List<Button> buttons;
        if (payType == null) {
            buttons = Helper.getTechButtons(techs, player);
        } else {
            buttons = Helper.getTechButtons(techs, player, payType);
        }

        if (game.isComponentAction()) {
            buttons.add(Buttons.gray("acquireATech", "Get Tech of a Different Type"));
        } else {
            buttons.add(Buttons.gray("acquireATechWithSC", "Get Tech of a Different Type"));
        }

        String message = player.getRepresentation() + " Use the buttons to get the tech you want";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
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
        String msg2 = player.getFactionEmoji() + " is choosing to resolve their Autonetic Memory ability";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        buttons.add(Buttons.green("autoneticMemoryStep3a", "Pick A Card From the Discard"));
        buttons.add(Buttons.blue("autoneticMemoryStep3b", "Drop 1 infantry"));
        String msg = player.getRepresentationUnfogged()
            + " you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        buttons = new ArrayList<>();
        buttons.add(
            Buttons.green("cymiaeHeroStep1_" + (game.getRealPlayers().size()), "Resolve Cymiae Hero"));
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation() + " resolve hero after doing Autonetic Memory steps", buttons);
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
        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(game, 0, pos, event);
        MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
        List<Button> buttons = StartCombat.getGeneralCombatButtons(game, pos, p1, p2, groundOrSpace, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
    }

    @ButtonHandler("refresh_")
    public static void refresh(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        Player p2 = player;
        if (StringUtils.countMatches(buttonID, "_") > 1) {
            String faction = buttonID.split("_")[2];
            p2 = game.getPlayerFromColorOrFaction(faction);
        }

        PlanetRefresh.doAction(p2, planetName, game);
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        String totalVotesSoFar = event.getMessage().getContentRaw();
        if (totalVotesSoFar.contains("Readied")) {
            totalVotesSoFar += ", " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        } else {
            totalVotesSoFar = player.getFactionEmojiOrColor() + " Readied " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        }
        if (actionRow2.size() > 0) {
            event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
        }
    }

    @ButtonHandler("resolve_explore_")
    public static void resolveExplore(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String bID = buttonID.replace("resolve_explore_", "");
        String[] info = bID.split("_");
        String cardID = info[0];
        String planetName = info[1];
        Tile tile = game.getTileFromPlanet(planetName);
        String tileName = tile == null ? "no tile" : tile.getPosition();
        String messageText = player.getRepresentation() + " explored " + "Planet "
            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " *(tile " + tileName + ")*:";
        if (buttonID.contains("_distantSuns")) {
            messageText = player.getFactionEmoji() + " chose to resolve: ";
        }
        ExploreSubcommandData.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
        ButtonHelper.deleteMessage(event);
    }

    // @ButtonHandler("strategicAction_")
    public static void strategicAction(ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel mainGameChannel) {
        int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
        SCPlay.playSC(event, scNum, game, mainGameChannel, player);
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
            "Added 1TG to " + Helper.getSCName(scNum, game) + ". There are now " + (tgCount + 1) + " TG"
                + (tgCount == 0 ? "" : "s") + " on it.");
    }

    @ButtonHandler("getPlagiarizeButtons")
    public static void getPlagiarizeButtons(ButtonInteractionEvent event, Player player, Game game) {
        game.setComponentAction(true);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Select the tech you want",
            ButtonHelperActionCards.getPlagiarizeButtons(game, player));
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        Button doneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            "Click the names of the planets you wish to exhaust to pay the 5 influence", buttons);
        ButtonHelper.deleteMessage(event);
        // "saarHeroResolution_"
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
        Player opponent = player;
        String msg = "\n" + opponent.getRepresentationUnfogged() + " cancelled 1 hit with an ability.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + opponent.getFaction() + "_";
        buttons.add(Buttons.green(finChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + h,
            "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "_afb",
            "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelAFBHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = "You may automatically assign " + h + " AFB hit" + (h == 1 ? "" : "s") + ".";
        event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
            .queue();
    }

    @ButtonHandler("cancelPdsOffenseHits_")
    public static void cancelPDSOffenseHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        Player opponent = player;
        String msg = "\n" + opponent.getRepresentationUnfogged() + " cancelled 1 hit with an ability";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + opponent.getFaction() + "_";
        buttons.add(Buttons.green(finChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h,
            "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "_pds",
            "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelPdsOffenseHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = "You may automatically assign " + (h == 1 ? "the hit" : "hits") + ". The hit"
            + (h == 1 ? "" : "s") + " would be assigned in the following way:\n\n"
            + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, h, event, true, true);
        event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
            .queue();
    }

    @ButtonHandler("cancelGroundHits_")
    public static void cancelGroundHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
        Player opponent = player;
        String msg = "\n" + opponent.getRepresentationUnfogged() + " cancelled 1 hit with an ability";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + opponent.getFaction() + "_";
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
        Player opponent = player;
        String msg = "\n" + opponent.getRepresentationUnfogged() + " cancelled 1 hit with an ability";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        List<Button> buttons = new ArrayList<>();
        String finChecker = "FFCC_" + opponent.getFaction() + "_";
        buttons.add(Buttons.green(finChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
            "Auto-assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(
            Buttons.red("getDamageButtons_" + tile.getPosition() + "_spacecombat",
                "Manually Assign Hit" + (h == 1 ? "" : "s")));
        buttons.add(Buttons.gray("cancelSpaceHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
        String msg2 = "You may automatically assign " + (h == 1 ? "the hit" : "hits") + ". The hit"
            + (h == 1 ? "" : "s") + " would be assigned in the following way:\n\n"
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

            String traitNameWithEmoji = Emojis.getEmojiFromDiscord(type) + type;
            if (deck.isEmpty() && discard.isEmpty()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
                continue;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            ExploreModel exp = Mapper.getExplore(deck.get(0));
            sb.append(exp.textRepresentation());
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, sb.toString());
        }

        String playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            "Top of Cultural, Industrial, and Hazardous explore decks has been set to "
                + playerFactionNameWithEmoji + " Cards info thread.");
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
            bID = bID.replace("dsdihmy_", "");
            dsdihmy = true;
        }
        String[] info = bID.split("_");
        Tile tile = game.getTileFromPlanet(info[1]);
        new ExplorePlanet().explorePlanet(event, game.getTileFromPlanet(info[1]), info[1], info[2], player, false, game,
            1, false);
        if (dsdihmy) {
            player.exhaustPlanet(info[1]);
            MessageHelper.sendMessageToChannel(mainGameChannel,
                info[1] + " was exhausted by Impressment Programs!");
        }
        if (tile != null && player.getTechs().contains("dsdihmy")) {
            List<Button> produce = new ArrayList<>();
            String pos = tile.getPosition();
            produce.add(Buttons.blue("dsdihmy_" + pos, "Produce (1) Units"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation()
                    + " You explored a planet and due to Impressment Programs you may produce 1 ship in the system.",
                produce);
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("spendAStratCC")
    public static void spendAStratCC(ButtonInteractionEvent event, Player player, Game game) {
        if (player.getStrategicCC() > 0) {
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
        }
        String message = ButtonHelperSCs.deductCC(player, event);
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
        if (!game.isRedTapeMode()) {
            if ("2".equalsIgnoreCase(stage)) {
                new RevealStage2().revealS2(event, event.getChannel());
            } else if ("2x2".equalsIgnoreCase(stage)) {
                new RevealStage2().revealTwoStage2(event, event.getChannel());
            } else {
                new RevealStage1().revealS1(event, event.getChannel());
            }
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "In Red Tape, no objective is revealed at this stage");
            int playersWithSCs = 0;
            for (Player player2 : game.getRealPlayers()) {
                if (player2.getSCs() != null && !player2.getSCs().isEmpty() && !player2.getSCs().contains(0)) {
                    playersWithSCs++;
                }
            }
            if (playersWithSCs > 0) {
                new Cleanup().runStatusCleanup(game);
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    game.getPing() + " **Status Cleanup Run!**");
            }
        }

        StartPhase.startStatusHomework(event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("assignSpeaker_")
    public static void assignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = StringUtils.substringAfter(buttonID, "assignSpeaker_");
        game.setStoredValue("hasntSetSpeaker", "");
        if (game != null && !game.isFowMode()) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.getFaction().equals(faction)) {
                    game.setSpeaker(player_.getUserID());
                    String message = Emojis.SpeakerToken + " Speaker assigned to: " + player_.getRepresentation(false, true);
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
        if (game != null) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.getFaction().equals(faction)) {
                    game.setSpeaker(player_.getUserID());
                    String message = Emojis.SpeakerToken + " Speaker assigned to: " + player_.getRepresentation(false, true);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    if (game.isFowMode() && player != player_) {
                        MessageHelper.sendMessageToChannel(player_.getPrivateChannel(), message);
                    }
                    if (!game.isFowMode()) {
                        ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                    }
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void poScoring(ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel privateChannel) {
        // key2
        if ("true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
            String key2 = "queueToScorePOs";
            String key3 = "potentialScorePOBlockers";
            String key3b = "potentialScoreSOBlockers";
            String message = "Drew A Secret Objective";
            for (Player player2 : Helper.getInitativeOrder(game)) {
                if (player2 == player) {
                    if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                        game.setStoredValue(key2, game.getStoredValue(key2)
                            .replace(player.getFaction() + "*", ""));
                    }

                    String poID = buttonID.replace(Constants.PO_SCORING, "");
                    int poIndex = Integer.parseInt(poID);
                    ScorePublic.scorePO(event, privateChannel, game, player, poIndex);
                    ButtonHelper.addReaction(event, false, false, null, "");
                    if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                        game.setStoredValue(key3, game.getStoredValue(key3)
                            .replace(player.getFaction() + "*", ""));
                        if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                            Helper.resolvePOScoringQueue(game, event);
                            // Helper.resolveSOScoringQueue(game, event);
                        }
                    }
                    break;
                }
                if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                    message = "Wants to score a PO but has people ahead of them in iniative order who need to resolve first. They have been queued and will automatically score their PO when everyone ahead of them is clear. ";
                    if (!game.isFowMode()) {
                        message += player2.getRepresentationUnfogged()
                            + " is the one the game is currently waiting on";
                    }
                    String poID = buttonID.replace(Constants.PO_SCORING, "");
                    try {
                        int poIndex = Integer.parseInt(poID);
                        game.setStoredValue(player.getFaction() + "queuedPOScore", "" + poIndex);
                    } catch (Exception e) {
                        BotLogger.log(event, "Could not parse PO ID: " + poID, e);
                        event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.")
                            .queue();
                    }
                    game.setStoredValue(key2,
                        game.getStoredValue(key2) + player.getFaction() + "*");
                    ButtonHelper.addReaction(event, false, false, message, "");
                    break;
                }
            }

        } else {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublic.scorePO(event, privateChannel, game, player, poIndex);
                ButtonHelper.addReaction(event, false, false, null, "");
            } catch (Exception e) {
                BotLogger.log(event, "Could not parse PO ID: " + poID, e);
                event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.")
                    .queue();
            }
        }
    }

    @ButtonHandler("get_so_discard_buttons")
    public static void getSODiscardButtons(ButtonInteractionEvent event, Player player, Game game) {
        String secretScoreMsg = "_ _\nClick a button below to discard your Secret Objective";
        List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveDiscardButtons(game, player);
        if (soButtons != null && !soButtons.isEmpty()) {
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
            MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor() + " lost a strategy CC to resolve the " + Emojis.Naalu + "**Foresight** ability");
            player.setStrategicCC(player.getStrategicCC() - 1);
            skilled = true;
        }
        String message = player.getRepresentationUnfogged() + " Use buttons to select a system to move to.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, pos, skilled));
    }

    @ButtonHandler("retreatUnitsFrom_")
    public static void retreatUnitsFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperModifyUnits.retreatSpaceUnits(buttonID, event, game, player);
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmojiOrColor() + " retreated all units in space to " + game.getTileByPosition(pos2).getRepresentationForButtons(game, player));
        String message = player.getRepresentationUnfogged() + " Use below buttons to move any ground forces or conclude retreat.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, game, event, pos1, pos2));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("mahactBenedictionFrom_")
    public static void mahactBenedictionFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperHeroes.mahactBenediction(buttonID, event, game, player);
        String pos1 = buttonID.split("_")[1];
        String pos2 = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmojiOrColor() + " moved all units in space from "
                + game.getTileByPosition(pos1).getRepresentationForButtons(game, player) + " to "
                + game.getTileByPosition(pos2).getRepresentationForButtons(game, player)
                + " using Airo Shir Aur, the Mahact hero. If they moved themselves and wish to move ground forces, they may do so either with slash command or modify units button.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("benedictionStep1_")
    public static void benedictionStep1(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos1 = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " choose the tile you wish to send the ships in "
                + game.getTileByPosition(pos1).getRepresentationForButtons(game, player) + " to.",
            ButtonHelperHeroes.getBenediction2ndTileOptions(player, game, pos1));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nullificationField_")
    public static void nullificationField(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String color = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        Player attacker = game.getPlayerFromColorOrFaction(color);
        ButtonHelper.resolveNullificationFieldUse(player, attacker, game, tile, event);
    }

    @ButtonHandler("mahactMechHit_")
    public static void mahactMechHit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String color = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        Player attacker = game.getPlayerFromColorOrFaction(color);
        ButtonHelper.resolveMahactMechAbilityUse(player, attacker, game, tile, event);
    }

    @ButtonHandler("getPsychoButtons")
    public static void offerPsychoButtons(Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " use buttons to gain " + Emojis.tg + " per planet exhausted.",
            ButtonHelper.getPsychoTechPlanets(game, player));
    }

    @ButtonHandler("getAgentSelection_")
    public static void getAgentSelection(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteTheOneButton(event);
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentationUnfogged() + " choose the target of your agent", buttons);
    }

    @ButtonHandler("get_so_score_buttons")
    public static void getSoScoreButtons(ButtonInteractionEvent event, Game game, Player player) {
        String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
        List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveButtons(game, player);
        if (soButtons != null && !soButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
        }
    }

    public static void soScoreFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player,
        MessageChannel privateChannel, MessageChannel mainGameChannel,
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
            if (player.getSecret(soIndex2) != null && "status".equalsIgnoreCase(player.getSecret(soIndex2).getPhase())
                && "true".equalsIgnoreCase(game.getStoredValue("forcedScoringOrder"))) {
                String key2 = "queueToScoreSOs";
                String key3 = "potentialScoreSOBlockers";
                String key3b = "potentialScorePOBlockers";
                String message = "Drew A Secret Objective";
                for (Player player2 : Helper.getInitativeOrder(game)) {
                    if (player2 == player) {
                        int soIndex = Integer.parseInt(soID);
                        ScoreSO.scoreSO(event, game, player, soIndex, channel);
                        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key2, game.getStoredValue(key2)
                                .replace(player.getFaction() + "*", ""));
                        }
                        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
                            game.setStoredValue(key3, game.getStoredValue(key3)
                                .replace(player.getFaction() + "*", ""));
                            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                                Helper.resolvePOScoringQueue(game, event);
                                // Helper.resolveSOScoringQueue(game, event);
                            }
                        }

                        break;
                    }
                    if (game.getStoredValue(key3).contains(player2.getFaction() + "*")) {
                        message = player.getRepresentation()
                            + " Wants to score an SO but has people ahead of them in initiative order who need to resolve first. They have been queued and will automatically score their SO when everyone ahead of them is clear. ";
                        if (!game.isFowMode()) {
                            message += player2.getRepresentationUnfogged()
                                + " is the one the game is currently waiting on";
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
                    ScoreSO.scoreSO(event, game, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please Score manually.")
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
        if (acIndex.contains("stall")) {
            acIndex = acIndex.replace("stall", "");
            stalling = true;
        }
        if (acIndex.endsWith("redraw")) {
            acIndex.replace("redraw", "");
            drawReplacement = true;
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
                String sb = player.getRepresentation() + " - " +
                    "Discarded Action Card:" + "\n" +
                    Mapper.getActionCard(acID).getRepresentation() + "\n";
                MessageChannel channel2 = game.getMainGameChannel();
                if (game.isFowMode()) {
                    channel2 = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannel(channel2, sb);
                ACInfo.sendActionCardInfo(game, player);
                String message = "Use buttons to end turn or do another action.";
                if (stalling) {
                    String message3 = "Use buttons to drop 1 mech on a planet or decline";
                    List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game,
                        "mech", "placeOneNDone_skipbuild"));
                    buttons.add(Buttons.red("deleteButtons", "Decline to drop Mech"));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message3, buttons);
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
                }
                if (drawReplacement) {
                    DrawAC.drawActionCards(game, player, 1, true);
                }
                ButtonHelper.checkACLimit(game, event, player);
                ButtonHelper.deleteMessage(event);
                if (player.hasUnexhaustedLeader("cymiaeagent")) {
                    List<Button> buttons2 = new ArrayList<>();
                    Button hacanButton = Buttons.gray("exhaustAgent_cymiaeagent_" + player.getFaction(), "Use Cymiae Agent", Emojis.cymiae);
                    buttons2.add(hacanButton);
                    MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "Skhot Unit X-12, the Cymiae"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent, to make yourself draw 1AC.",
                        buttons2);
                }
                PlayAC.serveReverseEngineerButtons(game, player, List.of(acID));
            } catch (Exception e) {
                BotLogger.log(event, "Something went wrong discarding", e);
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
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentation() + " please play Sabotage by clicking the Sabo button on the AC you wish to Sabo");
            return;
        }
        if (acID.contains("reverse_")) {
            String actionCardTitle = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolveReverse_" + actionCardTitle,
                "Pick up " + actionCardTitle + " from the discard"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + " After checking for Sabos, use buttons to resolve Reverse Engineer.", scButtons);
        }
        if (acID.contains("counterstroke_")) {
            String tilePos = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolveCounterStroke_" + tilePos,
                "Counterstroke in " + tilePos));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + " After checking for Sabos, use buttons to resolve Counterstroke.", scButtons);
        }
        if (channel != null) {
            try {
                String error = PlayAC.playAC(event, game, player, acID, channel);
                if (error != null) {
                    event.getChannel().sendMessage(error).queue();
                }
            } catch (Exception e) {
                BotLogger.log(event, "Could not parse AC ID: " + acID, e);
                event.getChannel().asThreadChannel()
                    .sendMessage("Could not parse AC ID: " + acID + " Please play manually.").queue();
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
    }

    @ButtonHandler("deleteButtons")
    public static void deleteButtons(ButtonInteractionEvent event, String buttonID, Game game, Player player, MessageChannel actionsChannel) {
        String buttonLabel = event.getButton().getLabel();
        buttonID = buttonID.replace("deleteButtons_", "");
        String editedMessage = event.getMessage().getContentRaw();
        if (("Done Gaining CCs".equalsIgnoreCase(buttonLabel)
            || "Done Redistributing CCs".equalsIgnoreCase(buttonLabel)
            || "Done Losing CCs".equalsIgnoreCase(buttonLabel)) && editedMessage.contains("CCs have gone from")) {

            String playerRep = player.getRepresentation();
            String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
            String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
            shortCCs = shortCCs.replace("CCs have gone from ", "");
            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
            if (event.getMessage().getContentRaw().contains("Net gain")) {
                boolean cyber = false;
                int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                finalCCs = finalCCs + ". Net CC gain was " + netGain;
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
                            properGain = properGain + 1;
                            reasons = "versatile ";
                        }
                        if (player.hasTech("hm")) {
                            properGain = properGain + 1;
                            reasons = reasons + "hypermetabolism ";
                        }
                        if (cyber) {
                            properGain = properGain + 1;
                            reasons = reasons + "cybernetics ";
                        }
                        if (netGain < properGain && netGain != 1) {
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                                "# " + player.getRepresentationUnfogged()
                                    + " heads up, bot thinks you should have gained " + properGain
                                    + " CC due to: " + reasons);
                        }
                    }
                }
                player.setTotalExpenses(player.getTotalExpenses() + netGain * 3);
            }

            if ("Done Redistributing CCs".equalsIgnoreCase(buttonLabel)) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is " + finalCCs);
            } else {
                if ("leadership".equalsIgnoreCase(buttonID)) {
                    String message = playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is "
                        + finalCCs;
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "leadership");
                } else {
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        playerRep + " Final CC Allocation Is " + finalCCs);
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
                MessageHistory mHistory = event.getChannel().getHistory();
                RestAction<List<Message>> lis = mHistory.retrievePast(3);
                Message previousM = lis.complete().get(1);
                System.out.println(previousM.getContentRaw());
                if (previousM.getContentRaw().contains("You have available to you")) {
                    previousM.delete().queue();
                }
                player.setTotalExpenses(
                    player.getTotalExpenses() + Helper.calculateCostOfProducedUnits(player, game, true));
                String message2 = player.getRepresentationUnfogged() + " Click the names of the planets you wish to exhaust.";
                boolean warM = player.getSpentThingsThisWindow().contains("warmachine");

                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
                if (player.hasTechReady("sar") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button sar = Buttons.red("exhaustTech_sar", "Exhaust Self Assembly Routines", Emojis.WarfareTech);
                    buttons.add(sar);
                }
                if (player.hasTechReady("htp") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sar = Buttons.red("exhaustTech_htp", "Exhaust Hegemonic Trade Policy", Emojis.Winnu);
                    buttons.add(sar);
                }
                if (game.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated")) {
                    ButtonHelperCommanders.titansCommanderUsage(event, game, player);
                }
                if (player.hasTechReady("dsbenty")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated")) {
                    Button sar2 = Buttons.green("exhaustTech_dsbenty", "Use Merged Replicators", Emojis.bentor);
                    buttons.add(sar2);
                }
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 0 && player.hasTechReady("aida")
                    && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)
                    && !buttonID.contains("integrated")) {
                    Button aiDEVButton = Buttons.red("exhaustTech_aida",
                        "Exhaust AI Development Algorithm (" + ButtonHelper.getNumberOfUnitUpgrades(player) + "r)", Emojis.WarfareTech);
                    buttons.add(aiDEVButton);
                }
                if (player.hasTechReady("st") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button sarweenButton = Buttons.red("useTech_st", "Use Sarween Tools", Emojis.CyberneticTech);
                    buttons.add(sarweenButton);
                }
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    Button sarweenButton = Buttons.red("useRelic_boon", "Use Boon Of The Cerulean God Relic");
                    buttons.add(sarweenButton);
                }
                if (player.hasTechReady("absol_st")) {
                    Button sarweenButton = Buttons.red("useTech_absol_st", "Use Sarween Tools");
                    buttons.add(sarweenButton);
                }
                if (player.hasUnexhaustedLeader("winnuagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button winnuButton = Buttons.red("exhaustAgent_winnuagent",
                        "Use Winnu Agent", Emojis.Winnu);
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("gledgeagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button winnuButton = Buttons.red("exhaustAgent_gledgeagent_" + player.getFaction(), "Use Gledge Agent", Emojis.gledge);
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("ghotiagent")) {
                    Button winnuButton = Buttons.red("exhaustAgent_ghotiagent_" + player.getFaction(), "Use Ghoti Agent", Emojis.ghoti);
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("mortheusagent")) {
                    Button winnuButton = Buttons.red("exhaustAgent_mortheusagent_" + player.getFaction(), "Use Mortheus Agent", Emojis.mortheus);
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("rohdhnaagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button rohdhnaButton = Buttons.red("exhaustAgent_rohdhnaagent_" + player.getFaction(), "Use Roh'Dhna Agent", Emojis.rohdhna);
                    buttons.add(rohdhnaButton);
                }
                if (player.hasLeaderUnlocked("hacanhero") && !"muaatagent".equalsIgnoreCase(buttonID)
                    && !"arboHeroBuild".equalsIgnoreCase(buttonID) && !buttonID.contains("integrated")) {
                    Button hacanButton = Buttons.red("purgeHacanHero", "Purge Hacan Hero", Emojis.Hacan);
                    buttons.add(hacanButton);
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
                ButtonHelper.updateMap(game, event,
                    "Result of build on turn " + player.getTurnCount() + " for " + player.getFactionEmoji());
                buttons.add(doneExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                if (tile != null && player.hasAbility("rally_to_the_cause")
                    && player.getHomeSystemTile() == tile
                    && ButtonHelperAbilities.getTilesToRallyToTheCause(game, player).size() > 0) {
                    String msg = player.getRepresentation()
                        + " due to your Rally to the Cause ability, if you just produced a ship in your HS, you may produce up to 2 ships in a system that contains a planet with a trait but no legendary planets and no opponent units. Press button to resolve";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Buttons.green("startRallyToTheCause", "Rally To The Cause"));
                    buttons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg,
                        buttons2);

                }
            }
        }
        if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            if (player.hasTech("asn") && (buttonID.contains("tacticalAction") || buttonID.contains("warfare"))) {
                ButtonHelperFactionSpecific.offerASNButtonsStep1(game, player, buttonID);
            }
            player.resetSpentThings();
            if (buttonID.contains("tacticalAction")) {
                ButtonHelper.exploreDET(player, game, event);
                ButtonHelperFactionSpecific.cleanCavUp(game, event);
                if (player.hasAbility("cunning")) {
                    List<Button> trapButtons = new ArrayList<>();
                    for (UnitHolder uH : game.getTileByPosition(game.getActiveSystem()).getUnitHolders()
                        .values()) {
                        if (uH instanceof Planet) {
                            String planet = uH.getName();
                            trapButtons.add(Buttons.gray("setTrapStep3_" + planet,
                                Helper.getPlanetRepresentation(planet, game)));
                        }
                    }
                    trapButtons.add(Buttons.red("deleteButtons", "Decline"));
                    String msg = player.getRepresentationUnfogged()
                        + " you may use the buttons to place a trap on a planet.";
                    if (trapButtons.size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(), msg, trapButtons);
                    }
                }
                if (player.hasUnexhaustedLeader("celdauriagent")) {
                    List<Button> buttons = new ArrayList<>();
                    Button hacanButton = Buttons.gray("exhaustAgent_celdauriagent_" + player.getFaction(), "Use Celdauri Agent", Emojis.celdauri);
                    buttons.add(hacanButton);
                    buttons.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                            + " you may use "
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                            + "George Nobin, the Celdauri"
                            + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                            + " agent, to place 1 space dock for 2TGs or 2 commodities.",
                        buttons);
                }
                List<Button> systemButtons2 = new ArrayList<>();
                if (!game.isAbsolMode() && player.getRelics().contains("emphidia")
                    && !player.getExhaustedRelics().contains("emphidia")) {
                    String message = player.getRepresentationUnfogged() + " You may use the button to explore a planet using " + Emojis.Relic
                        + "Crown of Emphidia.";
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
                    systemButtons2.addAll(ButtonHelperAgents.getSardakkAgentButtons(game, player));
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
                List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
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
        // String messageId = mainMessage.getId();
        // RestAction<Message> messageRestAction =
        // event.getChannel().retrieveMessageById(messageId);
        // messageRestAction.queue(m -> {
        // RestAction<Void> voidRestAction = m.clearReactions();
        // voidRestAction.queue();
        // });
    }

    public static void checkForAllReactions(@NotNull ButtonInteractionEvent event, Game game) {
        String buttonID = event.getButton().getId();

        String messageId = event.getInteraction().getMessage().getId();
        int matchingFactionReactions = 0;
        for (Player player : game.getRealPlayers()) {
            boolean factionReacted = false;
            if (buttonID.contains("no_after")) {
                if (game.getPlayersWhoHitPersistentNoAfter().contains(player.getFaction())) {
                    factionReacted = true;
                }
                Message mainMessage = event.getMessage();
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (game.isFowMode()) {
                    int index = 0;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, event.getMessageId()));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction != null) {
                    factionReacted = true;
                }
            }
            if (buttonID.contains("no_when")) {
                if (game.getPlayersWhoHitPersistentNoWhen().contains(player.getFaction())) {
                    factionReacted = true;
                }
                Message mainMessage = event.getMessage();
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (game.isFowMode()) {
                    int index = 0;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, event.getMessageId()));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction != null) {
                    factionReacted = true;
                }
            }
            if (factionReacted || (game.getStoredValue(messageId) != null
                && game.getStoredValue(messageId).contains(player.getFaction()))) {
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = game.getRealPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            respondAllPlayersReacted(event, game);
            game.removeStoredValue(messageId);
        }
    }

    public static void checkForAllReactions(String messageId, Game game) {
        int matchingFactionReactions = 0;
        for (Player player : game.getRealPlayers()) {

            if ((game.getStoredValue(messageId) != null
                && game.getStoredValue(messageId).contains(player.getFaction()))) {
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = game.getRealPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            game.getMainGameChannel().retrieveMessageById(messageId).queue(msg -> {
                if (game.getLatestAfterMsg().equalsIgnoreCase(messageId)) {
                    msg.reply("All players have indicated 'No Afters'").queueAfter(1000, TimeUnit.MILLISECONDS);
                    AgendaHelper.startTheVoting(game);
                } else if (game.getLatestWhenMsg().equalsIgnoreCase(messageId)) {
                    msg.reply("All players have indicated 'No Whens'").queueAfter(10, TimeUnit.MILLISECONDS);

                } else {
                    String msg2 = "All players have indicated 'No Sabotage'";
                    // if (game.getMessageIDsForSabo().contains(messageId)) {
                    String faction = "bob_" + game.getStoredValue(messageId) + "_";
                    faction = faction.split("_")[1];
                    Player p2 = game.getPlayerFromColorOrFaction(faction);
                    if (p2 != null && !game.isFowMode()) {
                        msg2 = p2.getRepresentation() + " " + msg2;
                    }
                    // }
                    msg.reply(msg2).queueAfter(1, TimeUnit.SECONDS);
                }
            });

            if (game.getMessageIDsForSabo().contains(messageId)) {
                game.removeMessageIDForSabo(messageId);
            }
        }
    }

    public static boolean checkForASpecificPlayerReact(String messageId, Player player, Game game) {
        boolean foundReact = false;
        try {
            if (game.getStoredValue(messageId) != null
                && game.getStoredValue(messageId).contains(player.getFaction())) {
                return true;
            }
            game.getMainGameChannel().retrieveMessageById(messageId).queue(mainMessage -> {
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (game.isFowMode()) {
                    int index = 0;
                    for (Player player_ : game.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, messageId));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction != null) {
                }
            });
        } catch (Exception e) {
            game.removeMessageIDForSabo(messageId);
            return true;
        }
        return foundReact;
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
            case Constants.SC_FOLLOW, "sc_no_follow", "sc_refresh", "sc_refresh_and_wash", "trade_primary", "sc_ac_draw", "sc_draw_so", "sc_trade_follow" -> {
                String message = "All players have reacted to this Strategy Card";
                if (game.isFowMode()) {
                    event.getInteraction().getMessage().reply(message).queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage(message).queueAfter(10, TimeUnit.SECONDS);
                }
            }
            case "no_when", "no_when_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Whens'").queueAfter(1,
                    TimeUnit.SECONDS);
            }
            case "no_after", "no_after_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Afters'").queue();
                AgendaHelper.startTheVoting(game);

            }
            case "no_sabotage" -> {
                String msg = "All players have indicated 'No Sabotage'";
                String faction = "bob_" + game.getStoredValue(event.getMessageId()) + "_";
                faction = faction.split("_")[1];
                Player p2 = game.getPlayerFromColorOrFaction(faction);
                if (p2 != null && !game.isFowMode()) {
                    msg = p2.getRepresentation() + " " + msg;
                }
                if (game.getMessageIDsForSabo().contains(event.getMessageId())) {
                    game.removeMessageIDForSabo(event.getMessageId());
                }
                event.getInteraction().getMessage().reply(msg).queueAfter(1, TimeUnit.SECONDS);

            }

            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> {
                String message2 = "All players have indicated scoring. Flip the relevant PO using the buttons. This will automatically run status clean-up if it has not been run already.";
                Button draw2Stage2 = Buttons.green("reveal_stage_2x2", "Reveal 2 Stage 2");
                Button drawStage2 = Buttons.green("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Buttons.green("reveal_stage_1", "Reveal Stage 1");
                // Button runStatusCleanup = Buttons.blue("run_status_cleanup", "Run Status
                // Cleanup");
                List<Button> buttons = new ArrayList<>();
                if (game.isRedTapeMode()) {
                    message2 = "All players have indicated scoring. This game is red tape mode, which means no objective is revealed at this stage. Please press one of the buttons below anyways though -- don't worry, it won't reveal anything, it will just run cleanup.";
                }
                if (game.getRound() < 4) {
                    buttons.add(drawStage1);
                }
                if (game.getRound() > 2 || game.getPublicObjectives1Peakable().size() == 0) {
                    if ("456".equalsIgnoreCase(game.getStoredValue("homebrewMode"))) {
                        buttons.add(draw2Stage2);
                    } else {
                        buttons.add(drawStage2);
                    }
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                // event.getMessage().delete().queueAfter(20, TimeUnit.SECONDS);
            }
            case "pass_on_abilities" -> {
                if (game.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Buttons.blue("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now",
                        buttons);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), game.getPing()
                        + " All players have indicated completion of status phase. Proceed to Strategy Phase.");
                    StartPhase.startPhase(event, game, "strategy");
                }
            }
            case "redistributeCCButtons" -> {
                if (game.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Buttons.blue("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "This message was triggered by the last person pressing redistribute CCs. Please flip agenda after they finish redistributing",
                        buttons);
                } else {
                    Button flipAgenda = Buttons.blue("startStrategyPhase", "Press this to start Strategy Phase");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Start Strategy Phase", buttons);
                }
            }
        }
    }

    @ButtonHandler("relic_look_top")
    public static void relicLookTop(ButtonInteractionEvent event, Game game, Player player) {
        List<String> relicDeck = game.getAllRelics();
        if (relicDeck.isEmpty()) {
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "Relic deck is empty");
            return;
        }
        String relicID = relicDeck.get(0);
        RelicModel relicModel = Mapper.getRelic(relicID);
        String rsb = "**Relic - Look at Top**\n" + player.getRepresentation() + "\n" + relicModel.getSimpleRepresentation();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, rsb);
        ButtonHelper.addReaction(event, true, false, "Looked at top of the Relic deck.", "");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("refreshVotes_")
    public static void refreshVotes(GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String votes = buttonID.replace("refreshVotes_", "");
        List<Button> voteActionRow = Helper.getPlanetRefreshButtons(event, player, game);
        Button concludeRefreshing = Buttons.red(player.getFinsFactionCheckerPrefix() + "votes_" + votes, "Done readying planets.");
        voteActionRow.add(concludeRefreshing);
        String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), voteMessage2, voteActionRow);
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
        String color = player.getColor();
        if (Mapper.isValidColor(color)) {
            AddCC.addCC(event, color, tile);
        }
        String message = player.getFactionEmojiOrColor() + " Placed 1 CC from reinforcements in the " + Helper.getPlanetRepresentation(planet, game) + " system";
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
        String color = player.getColor();
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(4)) {
                color = p2.getColor();
            }
        }

        if (Mapper.isValidColor(color)) {
            AddCC.addCC(event, color, tile);
        }
        String message = player.getRepresentation() + " Placed 1 " + StringUtils.capitalize(color) + " CC In The "
            + Helper.getPlanetRepresentation(planet, game)
            + " system due to use of " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
            + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "") + " agent.";
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startYinSpinner")
    public static void startYinSpinner(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmoji() + " Chose to Use Yin Spinner");
        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
        String message = "Use buttons to drop 2 infantry on a planet. Technically you may also drop 2 infantry with your ships, but this ain't supported yet via button."; //TODO: support yin spinner into space
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("componentAction")
    public static void componentAction(ButtonInteractionEvent event, Player player, Game game) {
        player.setWhetherPlayerShouldBeTenMinReminded(false);
        String message = "Use Buttons to decide what kind of component action you want to do";
        List<Button> systemButtons = ComponentActionHelper.getAllPossibleCompButtons(game, player, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        if (!game.isFowMode()) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("drawRelicFromFrag")
    public static void drawRelicFromFrag(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Drew Relic");
        RelicDraw.drawRelicAndNotify(player, event, game);
        doAnotherAction(event, player, game);
    }

    @ButtonHandler("drawRelic")
    public static void drawRelic(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Drew Relic");
        RelicDraw.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thronePoint")
    public static void thronePoint(ButtonInteractionEvent event, Player player, Game game) {
        Integer poIndex = game.addCustomPO("Throne of the False Emperor", 1);
        game.scorePublicObjective(player.getUserID(), poIndex);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " scored a Secret (they'll specify which one)");
        Helper.checkEndGame(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pay1tg") // TODO: delete this specific handler after Dec 2024
    @ButtonHandler("pay1tgToAnnounceARetreat")
    public static void pay1tgToAnnounceARetreat(ButtonInteractionEvent event, Player player) {
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor() + " paid 1TG to announce a retreat " + player.gainTG(-1));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("announceARetreat")
    public static void announceARetreat(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "# " + player.getFactionEmojiOrColor() + " announces a retreat";
        if (game.playerHasLeaderUnlockedOrAlliance(player, "nokarcommander")) {
            msg += ". Since they have Jack Hallard, the Nokar commander, this means they may cancel 2 hits in this coming combat round.";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        if (game.getActivePlayer() != null && game.getActivePlayer() != player && game.getActivePlayer().hasAbility("cargo_raiders")) {
            String combatName = "combatRoundTracker" + game.getActivePlayer().getFaction() + game.getActiveSystem() + "space";
            if (game.getStoredValue(combatName).isEmpty()) {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green("pay1tgToAnnounceARetreat", "Pay 1TG"));
                buttons.add(Buttons.red("deleteButtons", "I don't have to pay"));
                String raiders = player.getRepresentation() + " reminder that your opponent has the cargo raiders ability, which means you might have to pay 1TG to announce a retreat if they choose.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), raiders, buttons);
            }
        }
    }

    @ButtonHandler("crownofemphidiaexplore")
    public static void crownOfEmphidiaExplore(ButtonInteractionEvent event, Player player, Game game) {
        player.addExhaustedRelic("emphidia");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmojiOrColor() + " Exhausted " + Emojis.Relic + "Crown of Emphidia");
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doAnotherAction")
    @ButtonHandler("finishComponentAction")
    public static void doAnotherAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("doneRemoving")
    public static void doneRemoving(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        ButtonHelper.deleteMessage(event);
        ButtonHelper.updateMap(game, event);
    }

    @ButtonHandler("offerToGiveTitles")
    public static void offerToGiveTitles(ButtonInteractionEvent event, Game game) {
        PlayerTitleHelper.offerEveryoneTitlePossibilities(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("purgeHacanHero")
    public static void purgeHacanHero(ButtonInteractionEvent event, Player player) {
        Leader playerLeader = player.unsafeGetLeader("hacanhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - Harrugh Gefhara, the Hacan hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Harrugh Gefhara, the Hacan hero, was not purged - something went wrong.");
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeSardakkHero")
    public static void purgeSardakkHero(ButtonInteractionEvent event, Player player, Game game) {
        Leader playerLeader = player.unsafeGetLeader("sardakkhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - Sh'val, Harbinger, the N'orr hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Sh'val, Harbinger, the N'orr hero, was not purged - something went wrong.");
        }
        ButtonHelperHeroes.killShipsSardakkHero(player, game, event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " All ships have been removed, continue to land troops.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeRohdhnaHero")
    public static void purgeRohdhnaHero(ButtonInteractionEvent event, Player player, Game game) {
        Leader playerLeader = player.unsafeGetLeader("rohdhnahero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - Roh’Vhin Dhna mk4, the Roh'Dhna hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Roh’Vhin Dhna mk4, the Roh'Dhna hero, was not purged - something went wrong.");
        }
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game,
            game.getTileByPosition(game.getActiveSystem()), "rohdhnaBuild", "place");
        String message2 = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeVaylerianHero")
    public static void purgeVaylerianHero(ButtonInteractionEvent event, Player player, Game game) {
        Leader playerLeader = player.unsafeGetLeader("vaylerianhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - Dyln Harthuul, the Vaylerian hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Dyln Harthuul, the Vaylerian hero, was not purged - something went wrong.");
        }
        if (!game.isNaaluAgent()) {
            player.setTacticalCC(player.getTacticalCC() - 1);
            AddCC.addCC(event, player.getColor(),
                game.getTileByPosition(game.getActiveSystem()));
            game.setStoredValue("vaylerianHeroActive", "true");
        }
        for (Tile tile : ButtonHelperAgents.getGloryTokenTiles(game)) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event,
                "vaylerianhero");
            if (buttons.size() > 0) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Use buttons to remove a token from the board", buttons);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " may gain 1 CC");
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 = player.getRepresentationUnfogged() + "! Your current CCs are " + player.getCCRepresentation()
            + ". Use buttons to gain CCs";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        ButtonHelper.deleteTheOneButton(event);
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
    }

    @ButtonHandler("purgeKeleresAHero")
    public static void purgeKeleresAHero(ButtonInteractionEvent event, Player player, Game game) {
        Leader playerLeader = player.unsafeGetLeader("keleresherokuuasi");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + " - Kuuasi Aun Jalatai, the Keleres (Argent) hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Kuuasi Aun Jalatai, the Keleres (Argent) hero, was not purged - something went wrong.");
        }
        new AddUnits().unitParsing(event, player.getColor(),
            game.getTileByPosition(game.getActiveSystem()), "2 cruiser, 1 flagship",
            game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " 2 cruisers and 1 flagship added.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeDihmohnHero")
    public static void purgeDihmohnHero(ButtonInteractionEvent event, Player player, Game game) {
        Leader playerLeader = player.unsafeGetLeader("dihmohnhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + " - Verrisus Ypru, the Dih-Mohn hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Verrisus Ypru, the Dih-Mohn hero, was not purged - something went wrong.");
        }
        ButtonHelperHeroes.resolvDihmohnHero(game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentationUnfogged()
            + " sustained everything. Reminder you do not take hits this round.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("quash")
    public static void quash(ButtonInteractionEvent event, Player player, Game game) {
        int stratCC = player.getStrategicCC();
        player.setStrategicCC(stratCC - 1);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Quashed agenda. Strategic CCs went from " + stratCC + " -> " + (stratCC - 1));
        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "Quash");
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount = 0;
        if (agendaCount.isEmpty()) {
            aCount = 0;
        } else {
            aCount = Integer.parseInt(agendaCount) - 1;
        }
        game.setStoredValue("agendaCount", aCount + "");
        String agendaid = game.getCurrentAgendaInfo().split("_")[2];
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = game.revealAgenda(false);
            Map<String, Integer> discardAgendas = game.getDiscardAgendas();
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# The hidden agenda was " + agendaName + "! You may find it in the discard.");
        }
        RevealAgenda.revealAgenda(event, false, game, game.getMainGameChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("scoreAnObjective")
    public static void scoreAnObjective(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> poButtons = TurnEnd.getScoreObjectiveButtons(event, game, player.getFinsFactionCheckerPrefix());
        poButtons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        MessageChannel channel = event.getMessageChannel();
        if (game.isFowMode()) {
            channel = player.getPrivateChannel();
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to score an objective", poButtons);
    }

    @ButtonHandler("useLawsOrder")
    public static void useLawsOrder(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor() + " is paying " + Emojis.Influence_1 + " influence to ignore laws for the turn.");
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
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            purgeOrExhaust + Emojis.Relic + " relic: " + relicName);
        ButtonHelper.deleteMessage(event);
        String message = "Choose a system to move from.";
        List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, game, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
    }

    @ButtonHandler("getDiscardButtonsACs")
    public static void getDiscardButtonsACs(Player player, Game game) {
        String msg = player.getRepresentationUnfogged() + " use buttons to discard";
        List<Button> buttons = ACInfo.getDiscardActionCardButtons(game, player, false);
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

    @ButtonHandler("eraseMyVote")
    public static void eraseMyVote(Player player, Game game) {
        String pfaction = player.getFaction();
        if (game.isFowMode()) {
            pfaction = player.getColor();
        }
        Helper.refreshPlanetsOnTheRevote(player, game);
        AgendaHelper.eraseVotesOfFaction(game, pfaction);
        String eraseMsg = "Erased previous votes made by " + player.getFactionEmoji() + " and readied the planets they previously exhausted\n\n" + AgendaHelper.getSummaryOfVotes(game, true);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), eraseMsg);
        Button vote = Buttons.green(player.getFinsFactionCheckerPrefix() + "vote", player.getFlexibleDisplayName() + " Choose To Vote");
        Button abstain = Buttons.red(player.getFinsFactionCheckerPrefix() + "resolveAgendaVote_0", player.getFlexibleDisplayName() + " Choose To Abstain");
        Button forcedAbstain = Buttons.gray("forceAbstainForPlayer_" + player.getFaction(), "(For Others) Abstain for this player");

        String buttonMsg = "Use buttons to vote again. Reminder that this erasing of old votes did not refresh any planets.";
        List<Button> buttons = Arrays.asList(vote, abstain, forcedAbstain);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), buttonMsg, buttons);
    }

    @ButtonHandler("setOrder")
    public static void setOrder(ButtonInteractionEvent event, Game game) {
        Helper.setOrder(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("gain_CC")
    public static void gainCC(ButtonInteractionEvent event, Player player, Game game) {
        String message = "";

        String message2 = player.getRepresentationUnfogged() + "! Your current CCs are " + player.getCCRepresentation()
            + ". Use buttons to gain CCs";
        game.setStoredValue("originalCCsFor" + player.getFaction(),
            player.getCCRepresentation());
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

        ButtonHelper.addReaction(event, false, false, message, "");
        if (!event.getMessage().getContentRaw().contains("fragment")) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("run_status_cleanup")
    public static void runStatusCleanup(ButtonInteractionEvent event, Game game) {
        new Cleanup().runStatusCleanup(game);
        ButtonHelper.deleteTheOneButton(event);
        ButtonHelper.addReaction(event, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
    }

    @ButtonHandler("ChooseDifferentDestination")
    public static void chooseDifferentDestination(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Choosing a different system to activate. Please select the ring of the map that the system you want to activate is located in."
            + " Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Mecatol Rex. The Wormhole Nexus is in the corner.";
        List<Button> ringButtons = ButtonHelper.getPossibleRings(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("willRevolution")
    public static void willRevolution(ButtonInteractionEvent event, Game game) {
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("willRevolution", "active");
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Reversed SC Picking order");
    }

    public static void lastMinuteDeliberation(ButtonInteractionEvent event, Player player, Game game, MessageChannel actionsChannel) {
        ButtonHelper.deleteMessage(event);
        String message = player.getRepresentation() + " Click the names of up to 2 planets you wish to ready ";
        List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, game);
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Readying Planets")); // spitItOut
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message, buttons);
        RevealAgenda.revealAgenda(event, false, game, actionsChannel);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent buttons to ready 2 planets to the person who pressed the button");
    }

    @ButtonHandler("draw_2_ACDelete")
    public static void draw2ACDelete(ButtonInteractionEvent event, Player player, Game game) {
        String message = "";
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 2);
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            CommanderUnlockCheck.checkPlayer(player, "yssaril");
            ACInfo.sendActionCardInfo(game, player, event);
            message = "Drew 2 ACs With Scheming. Please Discard 1 AC.";
        }
        ButtonHelper.addReaction(event, true, false, message, "");
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " use buttons to discard",
            ACInfo.getDiscardActionCardButtons(game, player, false));

        ButtonHelper.deleteMessage(event);
        ButtonHelper.checkACLimit(game, event, player);
    }

    @ButtonHandler("draw_1_ACDelete")
    public static void draw1ACDelete(ButtonInteractionEvent event, Player player, Game game) {
        String message = "";
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(player.getUserID());
            CommanderUnlockCheck.checkPlayer(player, "yssaril");
            ACInfo.sendActionCardInfo(game, player, event);
            message = "Drew 1 AC";
        }
        ButtonHelper.addReaction(event, true, false, message, "");
        ButtonHelper.deleteMessage(event);
        ButtonHelper.checkACLimit(game, event, player);
    }

    @ButtonHandler("draw_1_AC")
    public static void draw1AC(ButtonInteractionEvent event, Player player, Game game) {
        String message = "";
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(player.getUserID());
            CommanderUnlockCheck.checkPlayer(player, "yssaril");
            ACInfo.sendActionCardInfo(game, player, event);
            message = "Drew 1 AC";
        }
        ButtonHelper.addReaction(event, true, false, message, "");
        ButtonHelper.checkACLimit(game, event, player);
    }

    @ButtonHandler("confirm_cc")
    public static void confirmCC(ButtonInteractionEvent event, Player player) {
        String message = "Confirmed CCs: " + player.getTacticalCC() + "/" + player.getFleetCC();
        if (!player.getMahactCC().isEmpty()) {
            message += "(+" + player.getMahactCC().size() + ")";
        }
        message += "/" + player.getStrategicCC();
        ButtonHelper.addReaction(event, true, false, message, "");
    }

    @ButtonHandler("getDivertFundingButtons")
    public static void getDivertFundingButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            "Use buttons to select tech to return",
            ButtonHelperActionCards.getDivertFundingLoseTechOptions(player, game));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getRepealLawButtons")
    public static void getRepealLawButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            "Use buttons to select Law to repeal",
            ButtonHelperActionCards.getRepealLawButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("shuffleExplores")
    public static void shuffleExplores(ButtonInteractionEvent event, Game game) {
        game.shuffleExplores();
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("fighterConscription")
    public static void fighterConscription(ButtonInteractionEvent event, Player player, Game game) {
        FighterConscription.doFfCon(event, player, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("riseOfAMessiah")
    public static void riseOfAMessiah(ButtonInteractionEvent event, Player player, Game game) {
        RiseOfMessiah.doRise(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("temporaryPingDisable")
    public static void temporaryPingDisable(ButtonInteractionEvent event, Game game) {
        game.setTemporaryPingDisable(true);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Disabled autopings for this turn");
        ButtonHelper.deleteMessage(event);
    }

    public static void declineExplore(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        ButtonHelper.addReaction(event, false, false, "Declined Explore", "");
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(mainGameChannel, pF + " declined explore");
        }
    }

    @ButtonHandler("mallice_convert_comm")
    public static void malliceConvertComm(ButtonInteractionEvent event, Player player, Game game) {
        String playerRep = player.getFactionEmoji();

        String message = playerRep + " exhausted Mallice ability and converted comms to TGs (TGs: "
            + player.getTg() + "->" + (player.getTg() + player.getCommodities()) + ").";
        player.setTg(player.getTg() + player.getCommodities());
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
        String message = playerRep + " exhausted Mallice ability and gained 2TGs " + player.gainTG(2) + ".";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 2);
        CommanderUnlockCheck.checkPlayer(player, "hacan");
        if (!game.isFowMode() && event.getMessageChannel() != game.getMainGameChannel()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), message);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @Deprecated
    public static void gain1tgFromCommander(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " Gained 1TG " + player.gainTG(1) + " from their commander";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromMuaatCommander(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " Gained 1TG " + player.gainTG(1) + " from Magmus, the Muaat commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
        ButtonHelper.deleteMessage(event);
    }

    public static void gain1tgFromLetnevCommander(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        String message = player.getRepresentation() + " Gained 1TG " + player.gainTG(1) + " from Rear Admiral Farran, the Letnev commander.";
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
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
            message += "Gained 1TG " + player.gainTG(1, true) + ".";
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        }
        ButtonHelper.addReaction(event, false, false, message, "");
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
        String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
            + player.getCCRepresentation() + ". Net gain of: " + netGain;
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("decrease_tactic_cc")
    public static void decreaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() - 1);
        String originalCCs = game
            .getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
            + player.getCCRepresentation() + ". Net gain of: " + netGain;
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("decrease_strategy_cc")
    public static void decreaseStrategyCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setStrategicCC(player.getStrategicCC() - 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain;
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_fleet_cc")
    public static void increaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain;
        event.getMessage().editMessage(editedMessage).queue();
        if (ButtonHelper.isLawInPlay(game, "regulations") && player.getFleetCC() > 4) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " reminder that there is Fleet Regulations in place, which is limiting fleet pool to 4");
        }
    }

    @ButtonHandler("resetCCs")
    public static void resetCCs(ButtonInteractionEvent event, Player player, Game game) {
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        ButtonHelper.resetCCs(player, originalCCs);
        String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: 0";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_tactic_cc")
    public static void increaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain;
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("exhauste6g0network")
    public static void exhaustE6G0Network(ButtonInteractionEvent event, Player player, Game game) {
        player.addExhaustedRelic("e6-g0_network");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " Chose to exhaust e6-g0_network");
        String message;
        if (player.hasAbility("scheming")) {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            message = player.getFactionEmoji() + " Drew 2 ACs With Scheming. Please Discard 1 AC.";
            ACInfo.sendActionCardInfo(game, player, event);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(game, player, false));
        } else if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(player.getUserID());
            ACInfo.sendActionCardInfo(game, player, event);
            message = player.getFactionEmoji() + " Drew 1 AC";
        }
        CommanderUnlockCheck.checkPlayer(player, "yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.checkACLimit(game, event, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("resetProducedThings")
    public static void resetProducedThings(ButtonInteractionEvent event, Player player, Game game) {
        Helper.resetProducedUnits(player, game, event);
        event.getMessage().editMessage(Helper.buildProducedUnitsMessage(player, game)).queue();
    }

    @ButtonHandler("yssarilMinisterOfPolicy")
    public static void yssarilMinisterOfPolicy(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " is drawing Minister of Policy AC(s)");
        String message;
        if (player.hasAbility("scheming")) {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            message = player.getFactionEmoji() + " Drew 2 ACs With Scheming. Please Discard 1 AC.";
            ACInfo.sendActionCardInfo(game, player, event);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(game, player, false));

        } else if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
        } else {
            game.drawActionCard(player.getUserID());
            ACInfo.sendActionCardInfo(game, player, event);
            message = player.getFactionEmoji() + " Drew 1 AC";
        }
        player.checkCommanderUnlock("yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.checkACLimit(game, event, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("increase_strategy_cc")
    public static void increaseStrategyCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setStrategicCC(player.getStrategicCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain;
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("no_when_persistent")
    public static void noWhenPersistent(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No whens (locked in)" : null;
        game.addPlayersWhoHitPersistentNoWhen(player.getFaction());
        ButtonHelper.addReaction(event, false, false, message, "");
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "You hit no whens for this entire agenda. If you change your mind, you can just play a when or remove this setting by hitting no whens (for now)");
    }

    @ButtonHandler("no_after_persistent")
    public static void noAfterPersistent(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No afters (locked in)" : null;
        game.addPlayersWhoHitPersistentNoAfter(player.getFaction());
        ButtonHelper.addReaction(event, false, false, message, "");
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "You hit no afters for this entire agenda. If you change your mind, you can just play an after or remove this setting by hitting no afters (for now)");
    }

    @ButtonHandler("no_after")
    public static void noAfter(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No afters" : null;
        game.removePlayersWhoHitPersistentNoAfter(player.getFaction());
        ButtonHelper.addReaction(event, false, false, message, "");
    }

    @ButtonHandler("no_when")
    public static void noWhen(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No whens" : null;
        game.removePlayersWhoHitPersistentNoWhen(player.getFaction());
        ButtonHelper.addReaction(event, false, false, message, "");
    }

    public static void playWhen(ButtonInteractionEvent event, Game game, MessageChannel mainGameChannel) {
        UnfiledButtonHandlers.clearAllReactions(event);
        ButtonHelper.addReaction(event, true, true, "Playing When", "When Played");
        List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
        Date newTime = new Date();
        game.setLastActivePlayerPing(newTime);
        MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no whens again.", game, whenButtons, "when");
        List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
        MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", game, afterButtons, "after");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("proceed_to_strategy")
    public static void proceedToStrategy(ButtonInteractionEvent event, Game game) {
        Map<String, Player> players = game.getPlayers();
        if (game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
            for (Player player_ : players.values()) {
                player_.cleanExhaustedPlanets(false);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), "Refreshed all planets at the end of the agenda phase");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Did not refresh planets due to the Checks and Balances resolving against. Players have been sent buttons to refresh up to 3 planets.");
        }
        StartPhase.startStrategyPhase(event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("hack_election")
    public static void hackElection(ButtonInteractionEvent event, Game game) {
        game.setHasHackElectionBeenPlayed(false);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set Order Back To Normal.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveVeto")
    public static void resolveVeto(ButtonInteractionEvent event, Game game) {
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount = 0;
        if (agendaCount.isEmpty()) {
            aCount = 0;
        } else {
            aCount = Integer.parseInt(agendaCount) - 1;
        }
        game.setStoredValue("agendaCount", aCount + "");
        String agendaid = game.getCurrentAgendaInfo().split("_")[2];
        if ("CL".equalsIgnoreCase(agendaid)) {
            String id2 = game.revealAgenda(false);
            Map<String, Integer> discardAgendas = game.getDiscardAgendas();
            AgendaModel agendaDetails = Mapper.getAgenda(id2);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "# The hidden agenda was " + agendaName
                    + "! You may find it in the discard.");
        }
        flipAgenda(event, game);
    }

    @ButtonHandler("flip_agenda")
    public static void flipAgenda(ButtonInteractionEvent event, Game game) {
        RevealAgenda.revealAgenda(event, false, game, event.getChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("edynCommanderSODraw")
    public static void edynCommanderSODraw(ButtonInteractionEvent event, Player player, Game game) {
        if (!game.playerHasLeaderUnlockedOrAlliance(player, "edyncommander")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " you don't have Kadryn, the Edyn commander, silly.");
        }
        String message = "Drew A Secret Objective instead of scoring PO, using Kadryn, the Edyn commander.";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message += ". Drew a second SO due to Plausible Deniability";
        }
        SOInfo.sendSecretObjectiveInfo(game, player, event);
        ButtonHelper.addReaction(event, false, false, message, "");
    }

    @ButtonHandler("non_sc_draw_so")
    public static void nonSCDrawSO(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Drew A Secret Objective";
        game.drawSecretObjective(player.getUserID());
        if (player.hasAbility("plausible_deniability")) {
            game.drawSecretObjective(player.getUserID());
            message += ". Drew a second SO due to Plausible Deniability";
        }
        SOInfo.sendSecretObjectiveInfo(game, player, event);
        ButtonHelper.addReaction(event, false, false, message, "");
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
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";

        } else {
            for (int i = 0; i < count; i++) {
                game.drawActionCard(player.getUserID());
            }
            ACInfo.sendActionCardInfo(game, player, event);
            ButtonHelper.checkACLimit(game, event, player);
        }

        ButtonHelper.addReaction(event, false, false, message, "");
        if (hasSchemingAbility) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(game, player, false));
        }
        CommanderUnlockCheck.checkPlayer(player, "yssaril");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("diploSystem")
    public static void diploSystem(Player player, Game game) {
        String message = player.getRepresentationUnfogged() + " Click the name of the planet whose system you wish to diplo";
        List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "diplomacy", buttons);
    }

    @ButtonHandler("redistributeCCButtons") // Buttons.REDISTRIBUTE_CCs
    public static void redistributeCCButtons(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain CCs";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        String finsFactionCheckerPrefix = player.getFinsFactionCheckerPrefix();
        Button getTactic = Buttons.green(finsFactionCheckerPrefix + "increase_tactic_cc", "Gain 1 Tactic CC");
        Button getFleet = Buttons.green(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
        Button getStrat = Buttons.green(finsFactionCheckerPrefix + "increase_strategy_cc", "Gain 1 Strategy CC");
        Button loseTactic = Buttons.red(finsFactionCheckerPrefix + "decrease_tactic_cc", "Lose 1 Tactic CC");
        Button loseFleet = Buttons.red(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet CC");
        Button loseStrat = Buttons.red(finsFactionCheckerPrefix + "decrease_strategy_cc", "Lose 1 Strategy CC");

        Button doneGainingCC = Buttons.red(finsFactionCheckerPrefix + "deleteButtons", "Done Redistributing CCs");
        Button resetCC = Buttons.gray(finsFactionCheckerPrefix + "resetCCs", "Reset CCs");
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
            ButtonHelper.addReaction(event, false, false, "", "");
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
                    properGain = properGain + 1;
                    reasons = "Versatile ";
                }
                if (player.hasTech("hm")) {
                    properGain = properGain + 1;
                    reasons = reasons + "Hypermetabolism ";
                }
                if (cyber) {
                    properGain = properGain + 1;
                    reasons = reasons + "Cybernetic Enhancements (L1Z1X PN) ";
                }
                if (properGain > 2) {
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentationUnfogged() + " heads up, bot thinks you should gain " + properGain + " CC now due to: " + reasons);
                }
            }
            if (game.isCcNPlasticLimit()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "Your highest fleet count in a system is currently "
                        + ButtonHelper.checkFleetInEveryTile(player, game, event)
                        + ". That's how many fleet CC you need to avoid removing ships");
            }
        }
    }

    @ButtonHandler("endOfTurnAbilities")
    public static void endOfTurnAbilities(ButtonInteractionEvent event, Player player, Game game) {
        String msg = "Use buttons to do an end of turn ability";
        List<Button> buttons = ButtonHelper.getEndOfTurnAbilities(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("startStrategyPhase")
    public static void startStrategyPhase(ButtonInteractionEvent event, Game game) {
        StartPhase.startPhase(event, game, "strategy");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("deployTyrant")
    public static void deployTyrant(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to place the **Tyrant's Lament** with your ships";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message, Helper.getTileWithShipsPlaceUnitButtons(player, game, "tyrantslament", "placeOneNDone_skipbuild"));
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " is deploying the **Tyrant's Lament**");
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
        ButtonHelper.addReaction(event, false, false, "", "");
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
            + " Players will be forced to score in order. Players will not be prevented from declaring they don't score, and are in fact encouraged to do so without delay if that is the case. This forced scoring order also does not yet affect SOs, it only restrains POs");
        game.setStoredValue("forcedScoringOrder", "true");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("proceedToVoting")
    public static void proceedToVoting(ButtonInteractionEvent event, Game game) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Decided to skip waiting for afters and proceed to voting.");
        try {
            AgendaHelper.startTheVoting(game);
        } catch (Exception e) {
            BotLogger.log(event, "Could not start the voting", e);
        }
    }

    @ButtonHandler("passForRound")
    public static void passForRound(ButtonInteractionEvent event, Player player, Game game) {
        Pass.passPlayerForRound(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("no_sabotage")
    public static void noSabotage(ButtonInteractionEvent event, Game game) {
        String message = game.isFowMode() ? "No Sabotage" : null;
        ButtonHelper.addReaction(event, false, false, message, "");
    }

    @ButtonHandler(Constants.SO_NO_SCORING)
    public static void soNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation() + " - no Secret Objective scored.";

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
    }

    @ButtonHandler("acquireAFreeTech") // Buttons.GET_A_FREE_TECH
    public static void acquireAFreeTech(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String finsFactionCheckerPrefix = player.getFinsFactionCheckerPrefix();
        game.setComponentAction(true);
        Button propulsionTech = Buttons.blue(finsFactionCheckerPrefix + "getAllTechOfType_propulsion_noPay", "Get a Blue Tech");
        propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
        buttons.add(propulsionTech);

        Button bioticTech = Buttons.green(finsFactionCheckerPrefix + "getAllTechOfType_biotic_noPay", "Get a Green Tech");
        bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
        buttons.add(bioticTech);

        Button cyberneticTech = Buttons.gray(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic_noPay", "Get a Yellow Tech");
        cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
        buttons.add(cyberneticTech);

        Button warfareTech = Buttons.red(finsFactionCheckerPrefix + "getAllTechOfType_warfare_noPay", "Get a Red Tech");
        warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
        buttons.add(warfareTech);

        Button unitupgradesTech = Buttons.gray(finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade_noPay", "Get A Unit Upgrade Tech");
        unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Emojis.UnitUpgradeTech));
        buttons.add(unitupgradesTech);
        String message = player.getRepresentation() + " What type of tech would you want?";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(Constants.PO_NO_SCORING)
    public static void poNoScoring(ButtonInteractionEvent event, Player player, Game game) {
        // AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
        String message = player.getRepresentation() + " - no Public Objective scored.";
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        String reply = game.isFowMode() ? "No public objective scored" : null;
        ButtonHelper.addReaction(event, false, false, reply, "");
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
            game.setStoredValue(key2,
                game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
        }
        if (game.getStoredValue(key3).contains(player.getFaction() + "*")) {
            game.setStoredValue(key3,
                game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            String key3b = "potentialScoreSOBlockers";
            if (!game.getStoredValue(key3b).contains(player.getFaction() + "*")) {
                Helper.resolvePOScoringQueue(game, event);
                // Helper.resolveSOScoringQueue(game, event);
            }
        }
    }

    @ButtonHandler("drawActionCards_")
    public static void drawActionCards(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        try {
            int count = Integer.parseInt(buttonID.replace("drawActionCards_", ""));
            DrawAC.drawActionCards(game, player, count, true);
            ButtonHelper.deleteTheOneButton(event);
        } catch (Exception ignored) {
        }
    }

    @ButtonHandler("applytempcombatmod__" + Constants.AC + "__")
    public static void applytempcombatmodAC(ButtonInteractionEvent event, Player player, String buttonID) {
        String acAlias = buttonID.substring(buttonID.lastIndexOf("__") + 2);
        TemporaryCombatModifierModel combatModAC = CombatTempModHelper.GetPossibleTempModifier(Constants.AC,
            acAlias,
            player.getNumberTurns());
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
        TemporaryCombatModifierModel combatModAC = CombatTempModHelper.GetPossibleTempModifier("tech", acAlias,
            player.getNumberTurns());
        if (combatModAC != null) {
            player.addNewTempCombatMod(combatModAC);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getFactionEmoji()
                    + " +1 Modifier will be applied the next time you push the combat roll button due to supercharge.");
        }
        player.exhaustTech("sc");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("rollIxthian")
    public static void rollIxthian(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (game.getSpeaker().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
            AgendaHelper.rollIxthian(game, true);
        } else {
            Button ixthianButton = Buttons.green("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact", Emojis.Mecatol);
            String msg = "The speaker should roll for Ixthain Artifact. Click this button to roll anyway!";
            MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg, ixthianButton);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("discardAgenda_")
    public static void discardAgenda(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        String agendaID = game.revealAgenda(false);
        AgendaModel agendaDetails = Mapper.getAgenda(agendaID);
        String agendaName = agendaDetails.getName();
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmojiOrColor() + "discarded " + agendaName + " using "
                + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Allant, the Edyn" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                + " agent.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bottomAgenda_")
    public static void bottomAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        new PutAgendaBottom().putBottom(event, Integer.parseInt(agendaNumID), game);
        AgendaModel agenda = Mapper.getAgenda(game.lookAtBottomAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
            "Put " + agenda.getName()
                + " on the bottom of the agenda deck. You may use this button to undo that and reassign it.",
            reassign);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "bottom");
        } else {
            game.setStoredValue(key, game.getStoredValue(key) + "_bottom");
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
        PlayPN.resolvePNPlay(ta, player, game, event);
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

    @ButtonHandler("topAgenda_")
    public static void topAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        new PutAgendaTop().putTop(event, Integer.parseInt(agendaNumID), game);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "top");
        } else {
            game.setStoredValue(key, game.getStoredValue(key) + "_top");
        }
        AgendaModel agenda = Mapper.getAgenda(game.lookAtTopAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
            "Put " + agenda.getName()
                + " on the top of the agenda deck. You may use this button to undo that and reassign it.",
            reassign);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("retrieveAgenda_")
    public static void retrieveAgenda(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String agendaID = buttonID.substring(buttonID.indexOf("_") + 1);
        DrawAgenda.drawSpecificAgenda(agendaID, game, player);
        ButtonHelper.deleteTheOneButton(event);
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
            ButtonHelper.addReaction(event, false, false, "Didn't have any comms/TGs to spend, no AC drawn", "");
        }
        String message = hasSchemingAbility
            ? "Spent 1 " + commOrTg + " to draw " + count2 + " Action Card (Scheming) - please discard 1 action card from your hand"
            : "Spent 1 " + commOrTg + " to draw " + count2 + " AC";
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count2);
            message = player.getFactionEmoji() + " Triggered Autonetic Memory Option";
        } else {
            for (int i = 0; i < count2; i++) {
                game.drawActionCard(player.getUserID());
            }
            ButtonHelper.checkACLimit(game, event, player);
            ACInfo.sendActionCardInfo(game, player, event);
        }

        CommanderUnlockCheck.checkPlayer(player, "yssaril");

        if (hasSchemingAbility) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ACInfo.getDiscardActionCardButtons(game, player, false));
        }

        ButtonHelper.addReaction(event, false, false, message, "");
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
        DrawAgenda.drawAgenda(event, 2, game, player);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation(true, false) + " drew 2 agendas");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startOfGameObjReveal")
    public static void startOfGameObjReveal(ButtonInteractionEvent event, Game game, Player player) {
        for (Player p : game.getRealPlayers()) {
            if (p.getSecrets().size() > 1 && !game.isExtraSecretMode()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please ensure everyone has discarded secrets before hitting this button. ");
                return;
            }
        }

        Player speaker = null;
        if (game.getPlayer(game.getSpeaker()) != null) {
            speaker = game.getPlayers().get(game.getSpeaker());
        }
        if (speaker == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please assign speaker before hitting this button.");
            ButtonHelper.offerSpeakerButtons(game, player);
            return;
        }
        RevealStage1.revealTwoStage1(event, game.getMainGameChannel());
        StartPhase.startStrategyPhase(event, game);
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
        Swap.secondHalfOfSwap(game, player, game.getPlayerFromColorOrFaction(faction), event.getUser(), event);
    }
}
