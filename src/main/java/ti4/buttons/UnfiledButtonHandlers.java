package ti4.buttons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
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
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.DrawAC;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.combat.StartCombat;
import ti4.commands.ds.ZelianHero;
import ti4.commands.explore.ExploreFrontier;
import ti4.commands.explore.ExplorePlanet;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.game.StartPhase;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.SCPick;
import ti4.commands.player.SCPlay;
import ti4.commands.player.Stats;
import ti4.commands.player.TurnStart;
import ti4.commands.special.NovaSeed;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tech.TechExhaust;
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
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.TransactionHelper;
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
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;

/*
 * Buttons methods which were factored out of {@link ButtonListener} which need to be filed away somewhere more appropriate
 */
public class UnfiledButtonHandlers { // TODO: move all of these methods to a better location, closer to the orignal button call and/or other related code
    
    public static void transactWith(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        if (p2 != null) {
            player.clearTransactionItemsWithPlayer(p2);
            List<Button> buttons = TransactionHelper.getStuffToTransButtonsOld(game, player, p2);
            if (!game.isFowMode() && game.isNewTransactionMethod()) {
                buttons = TransactionHelper.getStuffToTransButtonsNew(game, player, player, p2);
            }
            String message = player.getRepresentation() + " Use the buttons to select what you want to transact with " + p2.getRepresentation(false, false);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            TransactionHelper.checkTransactionLegality(game, player, p2);
            ButtonHelper.deleteMessage(event);
        }
    }

    public static void resetSpend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Helper.refreshPlanetsOnTheRespend(player, game);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 1) {
            whatIsItFor = buttonID.split("_")[1];
        }

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor);
        List<ActionRow> actionRow2 = new ArrayList<>();
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
        event.getMessage().editMessage(exhaustedMessage)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons)).queue();
    }

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

    public static void declareUse(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity, String ident) {
        String msg = ident + " is using " + buttonID.split("_")[1];
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
                trueIdentity + " Use buttons to assign 1 hit", buttons);
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

    public static void unlockCommander(ButtonInteractionEvent event, Player player, String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        CommanderUnlockCheck.checkPlayer(player, buttonID.split("_")[1]);
    }

    public static void fogAllianceAgentStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperHeroes.argentHeroStep3(game, player, event, buttonID);
    }

    @ButtonHandler("requestAllFollow_")
    public static void requestAllFollow(ButtonInteractionEvent event, Game game) {
        event.getMessage().reply(game.getPing() + " someone has requested that everyone resolve this SC before play continues. Please do so as soon as you can. The active player should not take an action until this is done").queue();
    }

    public static void starChartsStep1(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelper.starChartStep1(game, player, buttonID.split("_")[1]);
    }

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

    public static void doActivation(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("doActivation_", "");
        ButtonHelper.resolveOnActivationEnemyAbilities(game, game.getTileByPosition(pos), player, false, event);
        ButtonHelper.deleteMessage(event);
    }

    public static void getACFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("getACFrom_", "");
        Player victim = game.getPlayerFromColorOrFaction(faction);
        List<Button> buttons = ButtonHelperFactionSpecific.getButtonsToTakeSomeonesAC(game, player, victim);
        ShowAllAC.showAll(victim, player, game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentationUnfogged() + " Select which AC you would like to steal", buttons);
        ButtonHelper.deleteMessage(event);
    }

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

    public static void garboziaAbilityExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String planet = "garbozia";
        player.exhaustPlanetAbility(planet);
        new ExplorePlanet().explorePlanet(event, game.getTileFromPlanet(planet), planet, "INDUSTRIAL", player, true, game, 1, false);
    }

    public static void planetAbilityExhaust(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("planetAbilityExhaust_", "");
        PlanetExhaustAbility.doAction(event, player, planet, game, true);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void genericBuild(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericBuild_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), "genericBuild", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void genericModify(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.replace("genericModify_", "");
        Tile tile = game.getTileByPosition(pos);
        ButtonHelper.offerBuildOrRemove(player, game, event, tile);
        ButtonHelper.deleteMessage(event);
    }

    public static void getModifyTiles(Player player, Game game) {
        List<Button> buttons = ButtonHelper.getTilesToModify(player, game);
        String message = player.getRepresentation() + " Use the buttons to select the tile in which you wish to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    public static void genericReact(ButtonInteractionEvent event, Game game) {
        String message = game.isFowMode() ? "Turned down window" : null;
        ButtonHelper.addReaction(event, false, false, message, "");
    }

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

    public static void produceOneUnitInTile(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        buttonID = buttonID.replace("produceOneUnitInTile_", "");
        String type = buttonID.split("_")[1];
        String pos = buttonID.split("_")[0];
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos), type, "placeOneNDone_dontskip");
        String message = player.getRepresentation() + " Use the buttons to produce 1 unit.\n> " + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void winnuStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String unit = buttonID.replace("winnuStructure_", "").split("_")[0];
        String planet = buttonID.replace("winnuStructure_", "").split("_")[1];
        new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planet)), unit + " " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " Placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, game));
    }

    public static void removeAllStructures(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        plan.removeAllUnits(player.getColor());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Removed all units on " + planet + " for " + player.getRepresentation());
        AddRemoveUnits.addPlanetToPlayArea(event, game.getTileFromPlanet(planet), planet, game);
    }

    public static void jrStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity, String ident) {
        String unit = buttonID.replace("jrStructure_", "");
        if (!"tg".equalsIgnoreCase(unit)) {
            String message = trueIdentity + " Click the name of the planet you wish to put your unit on";
            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, game, unit, "placeOneNDone_dontskip");
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            }
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), ident + " TGs increased by 1 " + player.gainTG(1));
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void autoResolve(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String result = buttonID.substring(buttonID.indexOf("_") + 1);
        if (result.contains("manual")) {
            if (result.contains("committee")) {
                MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor()
                    + " has chosen to discard Committee Formation to choose the winner. Note that afters may be played before this occurs, and that confounding may still be played. You should probably wait and confirm no confounding before resolving");
                boolean success = game.removeLaw(game.getLaws().get("committee"));
                String message = game.getPing() + " please confirm no Confounding Legal Texts.";
                Button noConfounding = Buttons.blue("generic_button_id_3", "Refuse Confounding Legal Text");
                List<Button> buttons = List.of(noConfounding);
                MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(), message, game, buttons, "shenanigans");
                if (game.isACInDiscard("Confounding")) {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Confounding was found in the discard pile, so you should be good to resolve");
                }
            }
            String resMessage3 = "Please select the winner.";
            List<Button> deadlyActionRow3 = AgendaHelper.getAgendaButtons(null, game, "agendaResolution");
            deadlyActionRow3.add(Buttons.red("resolveWithNoEffect", "Resolve with no result"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), resMessage3, deadlyActionRow3);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void daxcive(ButtonInteractionEvent event, Player player, String buttonID, Game game, String ident) {
        String planet = buttonID.replace("dacxive_", "");
        new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planet)), "infantry " + planet, game);
        MessageHelper.sendMessageToChannel(event.getChannel(), ident + " placed 1 infantry on " + Helper.getPlanetRepresentation(planet, game) + " via the tech Dacxive Animators");
        ButtonHelper.deleteMessage(event);
    }

    public static void glimmerHeroOn(ButtonInteractionEvent event, Player player, String buttonID, Game game, String ident) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos), unit, game);
        MessageHelper.sendMessageToChannel(event.getChannel(), ident + " chose to duplicate a " + unit + " in " + game.getTileByPosition(pos).getRepresentationForButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    public static void arboAgentOn(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(player, game, event, game.getTileByPosition(pos), unit);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " select which unit you'd like to place down", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void outcome(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (game.getLaws() != null
            && (game.getLaws().containsKey("rep_govt") || game.getLaws().containsKey("absol_government"))) {
            player.resetSpentThings();
            player.addSpentThing("representative_1");
            boolean playerHasMr = CollectionUtils.containsAny(player.getPlanets(), Constants.MECATOLS);
            if (game.getLaws().containsKey("absol_government") && playerHasMr) {
                player.addSpentThing("absolRexControlRepresentative_1");
            }
            String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
            String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome);
            game.setStoredValue("latestOutcomeVotedFor" + player.getFaction(), outcome);
            game.setLatestOutcomeVotedFor(outcome);
            MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
            AgendaHelper.proceedToFinalizingVote(game, player, event);
        } else {
            AgendaHelper.exhaustPlanetsForVotingVersion2(buttonID, event, game, player);
        }
    }

    public static void resolveWithNoEffect(ButtonInteractionEvent event, Game game) {
        String voteMessage = "Resolving agenda with no effect. Click the buttons for next steps.";
        String agendaCount = game.getStoredValue("agendaCount");
        int aCount;
        if (agendaCount.isEmpty()) {
            aCount = 1;
        } else {
            aCount = Integer.parseInt(agendaCount) + 1;
        }
        Button flipNextAgenda = Buttons.blue("flip_agenda", "Flip Agenda #" + aCount);
        Button proceedToStrategyPhase = Buttons.green("proceed_to_strategy", "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
        List<Button> resActionRow = Arrays.asList(flipNextAgenda, proceedToStrategyPhase);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);
        ButtonHelper.deleteMessage(event);
    }

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

    public static void glimmersHeroIn(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperHeroes.getUnitsToGlimmersHero(player, game, event, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " select which unit you'd like to duplicate", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void arboAgentIn(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game, event, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " select which unit you'd like to replace", buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static void ghotiHeroIn(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game, event, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " select which unit you'd like to replace", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void getReleaseButtons(ButtonInteractionEvent event, Player player, Game game, String trueIdentity) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            trueIdentity
                + " you may release units one at a time with the buttons. Reminder that captured units may only be released as part of an ability or a transaction.",
            ButtonHelperFactionSpecific.getReleaseButtons(player, game));
    }

    public static void shroudOfLithStart(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Select up to 2 ships and 2 ground forces to place in the space area",
            ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game));
    }

    public static void distinguishedReverse(ButtonInteractionEvent event, String buttonID) {
        String voteMessage = "Please select from the available buttons your total vote amount. If your desired amount is not available, you may use the buttons to increase or decrease by multiples of 5 until you arrive at it.";
        String vote = buttonID.substring(buttonID.indexOf("_") + 1);
        int votes = Integer.parseInt(vote);
        List<Button> voteActionRow = AgendaHelper.getVoteButtonsVersion2(votes - 5, votes);
        voteActionRow.add(Buttons.gray("distinguishedReverse_" + (votes - 5), "Decrease Votes"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        ButtonHelper.deleteMessage(event);
    }

    public static void distinguished(ButtonInteractionEvent event, String buttonID) {
        String voteMessage = "Please select from the available buttons your total vote amount. If your desired amount is not available, you may use the buttons to increase or decrease by multiples of 5 until you arrive at it.";
        String vote = buttonID.substring(buttonID.indexOf("_") + 1);
        int votes = Integer.parseInt(vote);
        List<Button> voteActionRow = AgendaHelper.getVoteButtonsVersion2(votes, votes + 5);
        voteActionRow.add(Buttons.gray("distinguishedReverse_" + votes, "Decrease Votes"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
        ButtonHelper.deleteMessage(event);
    }

    public static void planetRider(ButtonInteractionEvent event, String buttonID, Game game, String finsFactionCheckerPrefix) {
        buttonID = buttonID.replace("planetRider_", "");
        String factionOrColor = buttonID.substring(0, buttonID.indexOf("_"));
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to Rider for one of " + factionOrColor
            + "'s planets. Use buttons to select which one.";
        List<Button> outcomeActionRow;
        buttonID = buttonID.replace(factionOrColor + "_", "");
        outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, game,
            finsFactionCheckerPrefix, buttonID);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    public static void tiedPlanets(ButtonInteractionEvent event, String buttonID, Game game) {
        buttonID = buttonID.replace("tiedPlanets_", "");
        buttonID = buttonID.replace("resolveAgendaVote_outcomeTie*_", "");
        String factionOrColor = buttonID;
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to break tie for one of " + factionOrColor
            + "'s planets. Use buttons to select which one.";
        List<Button> outcomeActionRow;
        outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, game,
            "resolveAgendaVote_outcomeTie*", null);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    public static void assimilate(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(buttonID.split("_")[1], game);
        ButtonHelperModifyUnits.infiltratePlanet(player, game, uH, event);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void planetOutcomes(ButtonInteractionEvent event, String buttonID, Game game) {
        String factionOrColor = buttonID.substring(buttonID.indexOf("_") + 1);
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to vote for one of " + factionOrColor
            + "'s planets. Click buttons for which outcome to vote for.";
        List<Button> outcomeActionRow;
        outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, game, "outcome", null);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    public static void exhaustTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tech = buttonID.replace("exhaustTech_", "");
        TechExhaust.exhaustTechAndResolve(event, game, player, tech);
    }

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

    public static void useRelic(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String relic = buttonID.replace("useRelic_", "");
        ButtonHelper.deleteTheOneButton(event);
        if ("boon".equals(relic)) {// Sarween Tools
            player.addSpentThing("boon");
            String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
            event.getMessage().editMessage(exhaustedMessage).queue();
        }
    }

    public static void forceAbstainForPlayer(ButtonInteractionEvent event, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Player was forcefully abstained");
        String faction = buttonID.replace("forceAbstainForPlayer_", "");
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        AgendaHelper.resolvingAnAgendaVote("resolveAgendaVote_0", event, game, p2);
    }

    public static void novaSeed(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        new NovaSeed().secondHalfOfNovaSeed(player, event, game.getTileByPosition(buttonID.split("_")[1]), game);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void celestialImpact(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        new ZelianHero().secondHalfOfCelestialImpact(player, event, game.getTileByPosition(buttonID.split("_")[1]), game);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void combatRoll(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.resolveCombatRoll(player, game, event, buttonID);
        if (buttonID.contains("bombard")) {
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    public static void bombardConfirm(ButtonInteractionEvent event, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(buttonID.replace("bombardConfirm_", ""), "Roll Bombardment"));
        String message = player.getRepresentationUnfogged()
            + " please declare what units are bombarding what planet before hitting this button"
            + " (e.g. if you have two dreadnoughts and are splitting their bombardment across two planets, specify which planet the first one is hitting)."
            + " The bot does not track this.";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    public static void deflectSC(ButtonInteractionEvent event, String buttonID, Game game) {
        String sc = buttonID.split("_")[1];
        ButtonHelper.deleteMessage(event);
        game.setStoredValue("deflectedSC", sc);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Put Deflection on " + sc);
    }

    public static void finishComponentAction(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.deleteMessage(event);
    }

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

    public static void reduceComm(ButtonInteractionEvent event, Player player, String buttonID, Game game, String ident) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        String message = ident + " reduced comms by " + tgLoss + " (" + player.getCommodities() + "->"
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

    public static void sabotage(ButtonInteractionEvent event, Player player, String buttonID, Game game, String ident) {
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
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    player.getRepresentation()
                        + " You clicked the AC Sabo button but did not have a Sabotage in hand.");
            }
        }

        if (acName.contains("Rider") || acName.contains("Sanction")) {
            AgendaHelper.reverseRider("reverse_" + acName, event, game, player, ident);
            // MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Reversed
            // the rider "+ acName);
        }
        if (sendReact) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(),
                message + "\n" + game.getPing());
        }
    }

    public static void demandSomething(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String player2Color = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(player2Color);
        if (p2 != null) {
            List<Button> buttons = TransactionHelper.getStuffToTransButtonsOld(game, p2, player);
            String message = p2.getRepresentation()
                + " you have been given something on the condition that you give something in return. Hopefully the player explained what. If you don't hand it over, please return what they sent. Use buttons to send something to "
                + ButtonHelper.getIdentOrColor(player, game);
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        }
    }

    public static void finishTransaction(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String player2Color = buttonID.split("_")[1];
        Player player2 = game.getPlayerFromColorOrFaction(player2Color);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAbilities.pillageCheck(player2, game);
        ButtonHelper.deleteMessage(event);
    }

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

    public static void autoneticMemoryStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (buttonID.contains("autoneticMemoryStep3a")) {
            ButtonHelperAbilities.autoneticMemoryStep3a(game, player, event);
        } else {
            ButtonHelperAbilities.autoneticMemoryStep3b(game, player, event);
        }
    }

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

    public static void getRepairButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity) {
        String pos = buttonID.replace("getRepairButtons_", "");
        List<Button> buttons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " Use buttons to resolve", buttons);
    }

    public static void getDamageButtons(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity) {
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
            trueIdentity + " Use buttons to resolve", buttons);
    }

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

    public static void refresh(ButtonInteractionEvent event, Player player, String buttonID, Game game, String ident) {
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
            totalVotesSoFar += ", "
                + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        } else {
            totalVotesSoFar = ident + " Readied "
                + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        }
        if (actionRow2.size() > 0) {
            event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
        }
    }

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

    public static void strategicAction(ButtonInteractionEvent event, Player player, String buttonID, Game game, MessageChannel mainGameChannel) {
        int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
        SCPlay.playSC(event, scNum, game, mainGameChannel, player);
        ButtonHelper.deleteMessage(event);
    }

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

    public static void autoAssignAFBHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelperModifyUnits.autoAssignAntiFighterBarrageHits(player, game, buttonID.split("_")[1],
            Integer.parseInt(buttonID.split("_")[2]), event);
    }

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

    public static void autoAssignSpaceCannonOffenceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game,
                game.getTileByPosition(buttonID.split("_")[1]),
                Integer.parseInt(buttonID.split("_")[2]), event, false, true));
    }

    public static void autoAssignSpaceHits(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game,
                game.getTileByPosition(buttonID.split("_")[1]),
                Integer.parseInt(buttonID.split("_")[2]), event, false));
    }

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

    public static void discardExploreTop(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String deckType = buttonID.replace("discardExploreTop_", "");
        ButtonHelperFactionSpecific.resolveExpDiscard(player, game, event, deckType);
    }

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

    public static void spendAStratCC(ButtonInteractionEvent event, Player player, Game game) {
        if (player.getStrategicCC() > 0) {
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
        }
        String message = ButtonHelperSCs.deductCC(player, event);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteTheOneButton(event);
    }

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

    public static void revealPOStage(ButtonInteractionEvent event, String buttonID, Game game) {
        String lastC = buttonID.replace("reveal_stage_", "");
        if (!game.isRedTapeMode()) {
            if ("2".equalsIgnoreCase(lastC)) {
                new RevealStage2().revealS2(event, event.getChannel());
            } else if ("2x2".equalsIgnoreCase(lastC)) {
                new RevealStage2().revealTwoStage2(event, event.getChannel());
            } else {
                new RevealStage1().revealS1(event, event.getChannel());
            }
        } else {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "In Red Tape, no objective is revealed at this stage");
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

    public static void assignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = StringUtils.substringAfter(buttonID, "assignSpeaker_");
        game.setStoredValue("hasntSetSpeaker", "");
        if (game != null && !game.isFowMode()) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.getFaction().equals(faction)) {
                    game.setSpeaker(player_.getUserID());
                    String message = Emojis.SpeakerToken + " Speaker assigned to: "
                        + player_.getRepresentation(false, true);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    if (!game.isFowMode()) {
                        ButtonHelper.sendMessageToRightStratThread(player, game, message, "politics");
                    }
                }
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void sc3AssignSpeaker(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
        game.setStoredValue("hasntSetSpeaker", "");
        if (game != null) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.getFaction().equals(faction)) {
                    game.setSpeaker(player_.getUserID());
                    String message = Emojis.SpeakerToken + " Speaker assigned to: "
                        + player_.getRepresentation(false, true);
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

    public static void getSODiscardButtons(ButtonInteractionEvent event, Player player, Game game) {
        String secretScoreMsg = "_ _\nClick a button below to discard your Secret Objective";
        List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveDiscardButtons(game, player);
        if (soButtons != null && !soButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
        }
    }

    public static void retreat(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity, String ident) {
        String pos = buttonID.split("_")[1];
        boolean skilled = false;
        if (buttonID.contains("skilled")) {
            skilled = true;
            ButtonHelper.deleteMessage(event);
        }
        if (buttonID.contains("foresight")) {
            MessageHelper.sendMessageToChannel(event.getChannel(), ident + " lost a strategy CC to resolve the foresight ability");
            player.setStrategicCC(player.getStrategicCC() - 1);
            skilled = true;
        }
        String message = trueIdentity + " Use buttons to select a system to move to.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatSystemButtons(player, game, pos, skilled));
    }

    public static void retreatUnitsFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity, String ident) {
        ButtonHelperModifyUnits.retreatSpaceUnits(buttonID, event, game, player);
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " retreated all units in space to " + game.getTileByPosition(pos2).getRepresentationForButtons(game, player));
        String message = trueIdentity + " Use below buttons to move any ground forces or conclude retreat.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, game, event, pos1, pos2));
        ButtonHelper.deleteMessage(event);
    }

    public static void mahactBenedictionFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game, String ident) {
        ButtonHelperHeroes.mahactBenediction(buttonID, event, game, player);
        String pos1 = buttonID.split("_")[1];
        String pos2 = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ident + " moved all units in space from "
                + game.getTileByPosition(pos1).getRepresentationForButtons(game, player)
                + " to "
                + game.getTileByPosition(pos2).getRepresentationForButtons(game, player)
                + " using Airo Shir Aur, the Mahact hero. If they moved themselves and wish to move ground forces, they may do so either with slash command or modify units button.");
        ButtonHelper.deleteMessage(event);
    }

    public static void benedictionStep1(ButtonInteractionEvent event, Player player, String buttonID, Game game, String trueIdentity) {
        String pos1 = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            trueIdentity + " choose the tile you wish to send the ships in "
                + game.getTileByPosition(pos1).getRepresentationForButtons(game, player)
                + " to.",
            ButtonHelperHeroes.getBenediction2ndTileOptions(player, game, pos1));
        ButtonHelper.deleteMessage(event);
    }

    public static void nullificationField(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String color = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        Player attacker = game.getPlayerFromColorOrFaction(color);
        ButtonHelper.resolveNullificationFieldUse(player, attacker, game, tile, event);
    }

    public static void mahactMechHit(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String color = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        Player attacker = game.getPlayerFromColorOrFaction(color);
        ButtonHelper.resolveMahactMechAbilityUse(player, attacker, game, tile, event);
    }

    public static void getPsychoButtons(Player player, Game game, String trueIdentity) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            trueIdentity + " use buttons to get 1TG per planet exhausted.",
            ButtonHelper.getPsychoTechPlanets(game, player));
    }

    public static void getAgentSelection(ButtonInteractionEvent event, String buttonID, Game game, String trueIdentity) {
        ButtonHelper.deleteTheOneButton(event);
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " choose the target of your agent", buttons);
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

    public static void acPlayFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player, MessageChannel actionsChannel, String fowIdentity) { //TODO: bake this into /ac play
        String acID = buttonID.replace(Constants.AC_PLAY_FROM_HAND, "");
        MessageChannel channel;
        if (game.getMainGameChannel() != null) {
            channel = game.getMainGameChannel();
        } else {
            channel = actionsChannel;
        }
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                fowIdentity + " please play Sabotage by clicking the Sabo button on the AC you wish to Sabo");
            return;
        }
        if (acID.contains("reverse_")) {
            String actionCardTitle = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolveReverse_" + actionCardTitle,
                "Pick up " + actionCardTitle + " from the discard"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                fowIdentity + " After checking for Sabos, use buttons to resolve Reverse Engineer.", scButtons);
        }
        if (acID.contains("counterstroke_")) {
            String tilePos = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolveCounterStroke_" + tilePos,
                "Counterstroke in " + tilePos));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                fowIdentity + " After checking for Sabos, use buttons to resolve Counterstroke.", scButtons);
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

    public static void deleteButtons(ButtonInteractionEvent event, String buttonID, String buttonLabel, Game game, Player player, MessageChannel actionsChannel, String trueIdentity) {
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
                String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";
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
                    ButtonHelperCommanders.titansCommanderUsage(buttonID, event, game, player, player.getRepresentation(false, false));
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
                    String message = trueIdentity + " You may use the button to explore a planet using " + Emojis.Relic
                        + "Crown of Emphidia.";
                    systemButtons2.add(Buttons.green("crownofemphidiaexplore", "Use Crown of Emphidia To Explore"));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("sardakkagent")) {
                    String message = trueIdentity + " You may use the button to use "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "T'ro, the N'orr" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                        + " agent.";
                    systemButtons2.addAll(ButtonHelperAgents.getSardakkAgentButtons(game, player));
                    systemButtons2.add(Buttons.red("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("nomadagentmercer")) {
                    String message = trueIdentity + " You may use the button to to use "
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

    public static void refreshVotes(GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String votes = buttonID.replace("refreshVotes_", "");
        List<Button> voteActionRow = Helper.getPlanetRefreshButtons(event, player, game);
        Button concludeRefreshing = Buttons.red(player.getFinsFactionCheckerPrefix() + "votes_" + votes, "Done readying planets.");
        voteActionRow.add(concludeRefreshing);
        String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), voteMessage2, voteActionRow);
        ButtonHelper.deleteMessage(event);
    }

    public static void automateGroundCombat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction1 = buttonID.split("_")[1];
        String faction2 = buttonID.split("_")[2];
        Player p1 = game.getPlayerFromColorOrFaction(faction1);
        Player p2 = game.getPlayerFromColorOrFaction(faction2);
        Player opponent = null;
        String planet = buttonID.split("_")[3];
        String confirmed = buttonID.split("_")[4];
        if (player != p1 && player != p2) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This button is only for combat participants");
            return;
        }
        if (player == p2) {
            opponent = p1;
        } else {
            opponent = p2;
        }
        ButtonHelper.deleteTheOneButton(event);
        if (opponent == null || opponent.isDummy() || confirmed.equalsIgnoreCase("confirmed")) {
            ButtonHelperModifyUnits.autoMateGroundCombat(p1, p2, planet, game, event);
        } else if (p1 != null && p2 != null) {
            Button automate = Buttons.green(opponent.getFinsFactionCheckerPrefix() + "automateGroundCombat_"
                + p1.getFaction() + "_" + p2.getFaction() + "_" + planet + "_confirmed", "Automate Combat");
            MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), opponent.getRepresentation() + " Your opponent has voted to automate the entire combat. Press to confirm:", automate);
        }
    }

    public static void scPick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String num = buttonID.replace("scPick_", "");
        int scpick = Integer.parseInt(num);
        if (game.getStoredValue("Public Disgrace") != null
            && game.getStoredValue("Public Disgrace").contains("_" + scpick)
            && (game.getStoredValue("Public Disgrace Only").isEmpty() || game.getStoredValue("Public Disgrace Only").contains(player.getFaction()))) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (game.getStoredValue("Public Disgrace").contains(p2.getFaction())
                    && p2.getActionCards().containsKey("disgrace")) {
                    PlayAC.playAC(event, game, p2, "disgrace", game.getMainGameChannel());
                    game.setStoredValue("Public Disgrace", "");
                    Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
                    int scNumber = scpick;
                    Integer tgCount = scTradeGoods.get(scNumber);
                    String msg = player.getRepresentationUnfogged() +
                        "\n> Picked: " + Helper.getSCRepresentation(game, scNumber);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentation()
                            + " you have been Public Disgrace'd because someone preset it to occur when the number " + scpick
                            + " was chosen. If this is a mistake or the Public Disgrace is Sabo'd, feel free to pick the strategy card again. Otherwise, pick a different strategy card.");
                    return;
                }
            }
        }
        if (game.getStoredValue("deflectedSC").equalsIgnoreCase(num)) {
            if (player.getStrategicCC() < 1) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " You cant pick this SC because it has the deflection ability on it and you have no strat CC to spend");
                return;
            } else {
                player.setStrategicCC(player.getStrategicCC() - 1);
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " spent 1 strat CC due to deflection");
            }
        }

        if (game.getLaws().containsKey("checks") || game.getLaws().containsKey("absol_checks")) {
            SCPick.secondHalfOfSCPickWhenChecksNBalances(event, player, game, scpick);
        } else {
            boolean pickSuccessful = Stats.secondHalfOfPickSC(event, game, player, scpick);
            if (pickSuccessful) {
                SCPick.secondHalfOfSCPick(event, player, game, scpick);
                ButtonHelper.deleteMessage(event);
            }
        }
    }

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
}
