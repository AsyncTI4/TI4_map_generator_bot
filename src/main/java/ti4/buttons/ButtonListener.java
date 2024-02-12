package ti4.buttons;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.AsyncTI4DiscordBot;
import ti4.MessageListener;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.agenda.PutAgendaBottom;
import ti4.commands.agenda.PutAgendaTop;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.DiscardACRandom;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardsso.DealSOToAll;
import ti4.commands.cardsso.DiscardSO;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.combat.StartCombat;
import ti4.commands.custom.PeakAtStage1;
import ti4.commands.custom.PeakAtStage2;
import ti4.commands.ds.TrapReveal;
import ti4.commands.ds.ZelianHero;
import ti4.commands.explore.DrawRelic;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.commands.explore.RelicInfo;
import ti4.commands.game.CreateGameButton;
import ti4.commands.game.GameEnd;
import ti4.commands.game.StartPhase;
import ti4.commands.game.Swap;
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetInfo;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.SCPick;
import ti4.commands.player.SCPlay;
import ti4.commands.player.Stats;
import ti4.commands.player.TurnEnd;
import ti4.commands.player.TurnStart;
import ti4.commands.player.UnitInfo;
import ti4.commands.special.FighterConscription;
import ti4.commands.special.NaaluCommander;
import ti4.commands.special.NovaSeed;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tech.TechInfo;
import ti4.commands.tokens.AddCC;
import ti4.commands.uncategorized.CardsInfo;
import ti4.commands.uncategorized.ShowGame;
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
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.FrankenDraftHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;

public class ButtonListener extends ListenerAdapter {
    public static final Map<Guild, Map<String, Emoji>> emoteMap = new HashMap<>();
    private static final Map<String, Set<Player>> playerUsedSC = new HashMap<>();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to handle button presses.")
                    .setEphemeral(true).queue();
            return;
        }
        event.deferEdit().queue();
        BotLogger.logButton(event);
        long startTime = new Date().getTime();
        try {
            resolveButtonInteractionEvent(event);
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with button interaction", e);
        }
        long endTime = new Date().getTime();
        if (endTime - startTime > 3000) {
            BotLogger.log(event, "This button command took longer than 3000 ms (" + (endTime - startTime) + ")");
        }
    }

    public void resolveButtonInteractionEvent(ButtonInteractionEvent event) {
        String id = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), id, "button", "no sub command");
        String buttonID = event.getButton().getId();
        String buttonLabel = event.getButton().getLabel();

        String lastchar = StringUtils.right(buttonLabel, 2).replace("#", "");
        if (buttonID == null) {
            event.getChannel().sendMessage("Button command not found").queue();
            return;
        }
        // BotLogger.log(event, ""); //TEMPORARY LOG ALL BUTTONS

        String messageID = event.getMessage().getId();

        String gameName = event.getChannel().getName();
        gameName = gameName.replace(Constants.CARDS_INFO_THREAD_PREFIX, "");
        gameName = gameName.replace(Constants.BAG_INFO_THREAD_PREFIX, "");
        gameName = StringUtils.substringBefore(gameName, "-");
        Game activeGame = GameManager.getInstance().getGame(gameName);
        Player player = null;
        MessageChannel privateChannel = event.getChannel();
        MessageChannel mainGameChannel = event.getChannel();
        if (activeGame != null) {
            player = activeGame.getPlayer(id);
            player = Helper.getGamePlayer(activeGame, player, event.getMember(), id);
            if (player == null && !"showGameAgain".equalsIgnoreCase(buttonID)) {
                event.getChannel().sendMessage("You're not a player of the game").queue();
                return;
            }
            buttonID = buttonID.replace("delete_buttons_", "resolveAgendaVote_");
            activeGame.increaseButtonPressCount();

            if (activeGame.isFoWMode()) {
                if (player != null && player.getPrivateChannel() == null) {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Private channels are not set up for this game. Messages will be suppressed.");
                    privateChannel = null;
                } else if (player != null) {
                    privateChannel = player.getPrivateChannel();
                }
            }
        }

        if (activeGame != null && activeGame.getMainGameChannel() != null) {
            mainGameChannel = activeGame.getMainGameChannel();
        }

        MessageChannel actionsChannel = null;
        for (TextChannel textChannel_ : AsyncTI4DiscordBot.jda.getTextChannels()) {
            if (textChannel_.getName().equals(gameName + Constants.ACTIONS_CHANNEL_SUFFIX)) {
                actionsChannel = textChannel_;
                break;
            }
        }

        if (buttonID.startsWith("FFCC_")) {
            buttonID = buttonID.replace("FFCC_", "");
            String factionWhoGeneratedButton = buttonID.substring(0, buttonID.indexOf("_"));
            buttonID = buttonID.replaceFirst(factionWhoGeneratedButton + "_", "");
            String factionWhoIsUp = player == null ? "nullPlayer" : player.getFaction();
            if (player != null && !player.getFaction().equalsIgnoreCase(factionWhoGeneratedButton)
                    && !buttonLabel.toLowerCase().contains(factionWhoIsUp)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "To " + player.getFactionEmoji()
                        + ": you are not the faction who these buttons are meant for.");
                return;
            }
        }
        String finsFactionCheckerPrefix = player == null ? "FFCC_nullPlayer_" : player.getFinsFactionCheckerPrefix();
        String trueIdentity = null;
        String fowIdentity = null;
        String ident = null;

        if (player != null) {
            trueIdentity = player.getRepresentation(true, true);
            fowIdentity = player.getRepresentation(false, true);
            ident = player.getFactionEmoji();
        }

        if (activeGame != null && !"ultimateundo".equalsIgnoreCase(buttonID)
                && !"showGameAgain".equalsIgnoreCase(buttonID)) {
            ButtonHelper.saveButtons(event, activeGame, player);
            GameSaveLoadManager.saveMap(activeGame, event);
        }

        if (player != null && activeGame != null && activeGame.getActivePlayerID() != null
                && player.getUserID().equalsIgnoreCase(activeGame.getActivePlayerID())) {
            activeGame.setLastActivePlayerPing(new Date());
        }

        if (buttonID.contains("deleteThisButton")) {
            buttonID = buttonID.replace("deleteThisButton", "");
            ButtonHelper.deleteTheOneButton(event);
        }

        if (buttonID.startsWith(Constants.AC_PLAY_FROM_HAND)) {
            String acID = buttonID.replace(Constants.AC_PLAY_FROM_HAND, "");
            MessageChannel channel;
            if (activeGame.getMainGameChannel() != null) {
                channel = activeGame.getMainGameChannel();
            } else {
                channel = actionsChannel;
            }
            if (acID.contains("reverse_")) {
                String actionCardTitle = acID.split("_")[2];
                acID = acID.split("_")[0];
                List<Button> scButtons = new ArrayList<>();
                scButtons.add(Button.success("resolveReverse_" + actionCardTitle,
                        "Pick up " + actionCardTitle + " from the discard"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        fowIdentity + " After checking for sabos, use buttons to resolve reverse engineer.", scButtons);
            }

            if (channel != null) {
                try {
                    String error = PlayAC.playAC(event, activeGame, player, acID, channel, event.getGuild());
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
        } else if (buttonID.startsWith("ac_discard_from_hand_")) {
            String acIndex = buttonID.replace("ac_discard_from_hand_", "");
            boolean stalling = false;
            if (acIndex.contains("stall")) {
                acIndex = acIndex.replace("stall", "");
                stalling = true;
            }

            MessageChannel channel;
            if (activeGame.getMainGameChannel() != null) {
                channel = activeGame.getMainGameChannel();
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

                    boolean removed = activeGame.discardActionCard(player.getUserID(), Integer.parseInt(acIndex));
                    if (!removed) {
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                                "No such Action Card ID found, please retry");
                        return;
                    }
                    String sb = "Player: " + player.getUserName() + " - " +
                            "Discarded Action Card:" + "\n" +
                            Mapper.getActionCard(acID).getRepresentation() + "\n";
                    MessageChannel channel2 = activeGame.getMainGameChannel();
                    if (activeGame.isFoWMode()) {
                        channel2 = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel2, sb);
                    ACInfo.sendActionCardInfo(activeGame, player);
                    String message = "Use buttons to end turn or do another action.";
                    if (stalling) {
                        String message3 = "Use buttons to drop a mech on a planet or decline";
                        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame,
                                "mech", "placeOneNDone_skipbuild"));
                        buttons.add(Button.danger("deleteButtons", "Decline to drop Mech"));
                        MessageHelper.sendMessageToChannelWithButtons(channel2, message3, buttons);
                        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
                        MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
                    }
                    ButtonHelper.checkACLimit(activeGame, event, player);
                    event.getMessage().delete().queue();
                    if (player.hasUnexhaustedLeader("cymiaeagent") && player.getStrategicCC() > 0) {
                        List<Button> buttons2 = new ArrayList<>();
                        Button hacanButton = Button
                                .secondary("exhaustAgent_cymiaeagent_" + player.getFaction(), "Use Cymiae Agent")
                                .withEmoji(Emoji.fromFormatted(Emojis.cymiae));
                        buttons2.add(hacanButton);
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation(true, true) + " you can use Cymiae agent to draw an AC",
                                buttons2);
                    }

                } catch (Exception e) {
                    BotLogger.log(event, "Something went wrong discarding", e);
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
        } else if (buttonID.startsWith(Constants.SO_SCORE_FROM_HAND)) {
            String soID = buttonID.replace(Constants.SO_SCORE_FROM_HAND, "");
            MessageChannel channel;
            if (activeGame.isFoWMode()) {
                channel = privateChannel;
            } else if (activeGame.isCommunityMode() && activeGame.getMainGameChannel() != null) {
                channel = mainGameChannel;
            } else {
                channel = actionsChannel;
            }
            if (channel != null) {
                try {
                    int soIndex = Integer.parseInt(soID);
                    ScoreSO.scoreSO(event, activeGame, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please Score manually.")
                            .queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("SODISCARD_")) {
            String soID = buttonID.replace("SODISCARD_", "");
            MessageChannel channel;
            if (activeGame.isFoWMode()) {
                channel = privateChannel;
            } else if (activeGame.isCommunityMode() && activeGame.getMainGameChannel() != null) {
                channel = mainGameChannel;
            } else {
                channel = actionsChannel;
            }

            if (channel != null) {
                try {
                    int soIndex = Integer.parseInt(soID);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            ident + " discarded an SO");
                    new DiscardSO().discardSO(event, player, soIndex, activeGame);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please discard manually.")
                            .queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("mantleCrack_")) {
            ButtonHelperAbilities.mantleCracking(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("umbatTile_")) {
            ButtonHelperAgents.umbatTile(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("get_so_score_buttons")) {
            String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveButtons(activeGame, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
            //
        } else if (buttonID.startsWith("swapToFaction_")) {
            String faction = buttonID.replace("swapToFaction_", "");
            new Swap().secondHalfOfSwap(activeGame, player, activeGame.getPlayerFromColorOrFaction(faction),
                    event.getUser(), event);
        } else if (buttonID.startsWith("yinHeroInfantry_")) {
            ButtonHelperHeroes.lastStepOfYinHero(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("contagion_")) {
            ButtonHelperAbilities.lastStepOfContagion(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("drawSpecificSO_")) {
            DiscardSO.drawSpecificSO(event, player, buttonID.split("_")[1], activeGame);
        } else if (buttonID.startsWith("olradinHeroFlip_")) {
            ButtonHelperHeroes.olradinHeroFlipPolicy(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("tnelisHeroAttach_")) {
            ButtonHelperHeroes.resolveTnelisHeroAttach(player, activeGame, buttonID.split("_")[1], event);
        } else if (buttonID.startsWith("arcExp_")) {
            ButtonHelperActionCards.resolveArcExpButtons(activeGame, player, buttonID, event, trueIdentity);
        } else if (buttonID.startsWith("augerHeroSwap_")) {
            ButtonHelperHeroes.augersHeroSwap(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("hacanMechTradeStepOne_")) {
            ButtonHelperFactionSpecific.resolveHacanMechTradeStepOne(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("rollForAmbush_")) {
            ButtonHelperFactionSpecific.rollAmbush(player, activeGame,
                    activeGame.getTileByPosition(buttonID.split("_")[1]), event);
        } else if (buttonID.startsWith("raghsCallStepOne_")) {
            ButtonHelperFactionSpecific.resolveRaghsCallStepOne(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("raghsCallStepTwo_")) {
            ButtonHelperFactionSpecific.resolveRaghsCallStepTwo(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("hacanMechTradeStepTwo_")) {
            ButtonHelperFactionSpecific.resolveHacanMechTradeStepTwo(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("resolveDMZTrade_")) {
            ButtonHelper.resolveDMZTrade(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("resolveAlliancePlanetTrade_")) {
            ButtonHelper.resolveAllianceMemberPlanetTrade(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("augersHeroStart_")) {
            ButtonHelperHeroes.augersHeroResolution(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("augersPeak_")) {
            if ("1".equalsIgnoreCase(buttonID.split("_")[1])) {
                new PeakAtStage1().secondHalfOfPeak(event, activeGame, player, 1);
            } else {
                new PeakAtStage2().secondHalfOfPeak(event, activeGame, player, 1);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("initialPeak")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("augersPeak_1", "Peek At Next Stage 1"));
            buttons.add(Button.success("augersPeak_2", "Peek At Next Stage 2"));
            String msg = trueIdentity
                    + " the bot doesn't know if the next objective is a stage 1 or a stage 2. Please help it out and click the right button.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        } else if (buttonID.startsWith("cabalHeroTile_")) {
            ButtonHelperHeroes.executeCabalHero(buttonID, player, activeGame, event);
        } else if (buttonID.startsWith("creussMechStep1_")) {
            ButtonHelperFactionSpecific.creussMechStep1(activeGame, player);
        } else if (buttonID.startsWith("creussMechStep2_")) {
            ButtonHelperFactionSpecific.creussMechStep2(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("creussMechStep3_")) {
            ButtonHelperFactionSpecific.creussMechStep3(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("creussIFFStart_")) {
            ButtonHelperFactionSpecific.resolveCreussIFFStart(activeGame, player, buttonID, ident, event);
        } else if (buttonID.startsWith("creussIFFResolve_")) {
            ButtonHelperFactionSpecific.resolveCreussIFF(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("acToSendTo_")) {
            ButtonHelperHeroes.lastStepOfYinHero(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("creussHeroStep1_")) {
            ButtonHelperHeroes.getGhostHeroTilesStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("resolveUpgrade_")) {
            ButtonHelperActionCards.resolveUpgrade(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("resolveEmergencyRepairs_")) {
            ButtonHelperActionCards.resolveEmergencyRepairs(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("creussHeroStep2_")) {
            ButtonHelperHeroes.resolveGhostHeroStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("yinCommanderStep1_")) {
            ButtonHelperCommanders.yinCommanderStep1(player, activeGame, event);
        } else if (buttonID.startsWith("yinCommanderRemoval_")) {
            ButtonHelperCommanders.resolveYinCommanderRemoval(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("cheiranCommanderBlock_")) {
            ButtonHelperCommanders.cheiranCommanderBlock(player, activeGame, event);
        } else if (buttonID.startsWith("kortaliCommanderBlock_")) {
            ButtonHelperCommanders.kortaliCommanderBlock(player, activeGame, event);
        } else if (buttonID.startsWith("placeGhostCommanderFF_")) {
            ButtonHelperCommanders.resolveGhostCommanderPlacement(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("placeKhraskCommanderInf_")) {
            ButtonHelperCommanders.resolveKhraskCommanderPlacement(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("yinHeroPlanet_")) {
            String planet = buttonID.replace("yinHeroPlanet_", "");
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    trueIdentity + " Chose to invade " + Helper.getPlanetRepresentation(planet, activeGame));
            List<Button> buttons = new ArrayList<>();
            for (int x = 1; x < 4; x++) {
                buttons.add(Button
                        .success(finsFactionCheckerPrefix + "yinHeroInfantry_" + planet + "_" + x,
                                "Land " + x + " infantry")
                        .withEmoji(Emoji.fromFormatted(Emojis.infantry)));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    "Use buttons to select how many infantry you'd like to land on the planet", buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("yinHeroTarget_")) {
            String faction = buttonID.replace("yinHeroTarget_", "");
            List<Button> buttons = new ArrayList<>();
            Player target = activeGame.getPlayerFromColorOrFaction(faction);
            if (target != null) {
                for (String planet : target.getPlanets()) {
                    buttons.add(Button.success(finsFactionCheckerPrefix + "yinHeroPlanet_" + planet,
                            Helper.getPlanetRepresentation(planet, activeGame)));
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "Use buttons to select which planet to invade", buttons);
                event.getMessage().delete().queue();
            }
        } else if (buttonID.startsWith("yinHeroStart")) {
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "yinHeroTarget", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    "Use buttons to select which player owns the planet you want to land on", buttons);
        } else if (buttonID.startsWith("psychoExhaust_")) {
            ButtonHelper.resolvePsychoExhaust(activeGame, event, player, buttonID);
        } else if (buttonID.startsWith("productionBiomes_")) {
            ButtonHelperFactionSpecific.resolveProductionBiomesStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("getAgentSelection_")) {
            ButtonHelper.deleteTheOneButton(event);
            List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(activeGame, buttonID.split("_")[1]);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    trueIdentity + " choose the target of your agent", buttons);
        } else if (buttonID.startsWith("step2axisagent_")) {
            ButtonHelperAgents.resolveStep2OfAxisAgent(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("hacanAgentRefresh_")) {
            ButtonHelperAgents.hacanAgentRefresh(buttonID, event, activeGame, player, ident, trueIdentity);
        } else if (buttonID.startsWith("vaylerianAgent_")) {
            ButtonHelperAgents.resolveVaylerianAgent(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("nekroAgentRes_")) {
            ButtonHelperAgents.nekroAgentRes(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("kolleccAgentRes_")) {
            ButtonHelperAgents.kolleccAgentResStep1(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("scourPlanet_")) {
            ButtonHelperFactionSpecific.resolveScour(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("cheiranAgentStep2_")) {
            ButtonHelperAgents.resolveCheiranAgentStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("freeSystemsAgentStep2_")) {
            ButtonHelperAgents.resolveFreeSystemsAgentStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("florzenAgentStep2_")) {
            ButtonHelperAgents.resolveFlorzenAgentStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("florzenHeroStep2_")) {
            ButtonHelperHeroes.resolveFlorzenHeroStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("florzenAgentStep3_")) {
            ButtonHelperAgents.resolveFlorzenAgentStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("attachAttachment_")) {
            ButtonHelperHeroes.resolveAttachAttachment(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("findAttachmentInDeck_")) {
            ButtonHelperHeroes.findAttachmentInDeck(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("cheiranAgentStep3_")) {
            ButtonHelperAgents.resolveCheiranAgentStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("celdauriAgentStep3_")) {
            ButtonHelperAgents.resolveCeldauriAgentStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("kolleccAgentResStep2_")) {
            ButtonHelperAgents.kolleccAgentResStep2(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("getPsychoButtons")) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    trueIdentity + " use buttons to get a tg per planet exhausted.",
                    ButtonHelper.getPsychoTechPlanets(activeGame, player));
        } else if (buttonID.startsWith("retreatGroundUnits_")) {
            ButtonHelperModifyUnits.retreatGroundUnits(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("resolveShipOrder_")) {
            ButtonHelperAbilities.resolveAxisOrderExhaust(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("buyAxisOrder_")) {
            ButtonHelperAbilities.resolveAxisOrderBuy(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("bindingDebtsRes_")) {
            ButtonHelperAbilities.bindingDebtRes(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("mercenariesStep1_")) {
            ButtonHelperAbilities.mercenariesStep1(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("mercenariesStep2_")) {
            ButtonHelperAbilities.mercenariesStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("mercenariesStep3_")) {
            ButtonHelperAbilities.mercenariesStep3(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("mercenariesStep4_")) {
            ButtonHelperAbilities.mercenariesStep4(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("rallyToTheCauseStep2_")) {
            ButtonHelperAbilities.rallyToTheCauseStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("startRallyToTheCause")) {
            ButtonHelperAbilities.startRallyToTheCause(activeGame, player, event);
        } else if (buttonID.startsWith("startFacsimile_")) {
            ButtonHelperAbilities.startFacsimile(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("facsimileStep2_")) {
            ButtonHelperAbilities.resolveFacsimileStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("naaluCommander")) {
            new NaaluCommander().secondHalfOfNaaluCommander(event, activeGame, player);
        } else if (buttonID.startsWith("mahactMechHit_")) {
            String pos = buttonID.split("_")[1];
            String color = buttonID.split("_")[2];
            Tile tile = activeGame.getTileByPosition(pos);
            Player attacker = activeGame.getPlayerFromColorOrFaction(color);
            ButtonHelper.resolveMahactMechAbilityUse(player, attacker, activeGame, tile, event);
        } else if (buttonID.startsWith("nullificationField_")) {
            String pos = buttonID.split("_")[1];
            String color = buttonID.split("_")[2];
            Tile tile = activeGame.getTileByPosition(pos);
            Player attacker = activeGame.getPlayerFromColorOrFaction(color);
            ButtonHelper.resolveNullificationFieldUse(player, attacker, activeGame, tile, event);
        } else if (buttonID.startsWith("benedictionStep1_")) {
            String pos1 = buttonID.split("_")[1];
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    trueIdentity + " choose the tile you wish to send the ships in "
                            + activeGame.getTileByPosition(pos1).getRepresentationForButtons(activeGame, player)
                            + " to.",
                    ButtonHelperHeroes.getBenediction2ndTileOptions(player, activeGame, pos1));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("mahactBenedictionFrom_")) {
            ButtonHelperHeroes.mahactBenediction(buttonID, event, activeGame, player);
            String pos1 = buttonID.split("_")[1];
            String pos2 = buttonID.split("_")[2];
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    ident + " moved all units in space from "
                            + activeGame.getTileByPosition(pos1).getRepresentationForButtons(activeGame, player)
                            + " to "
                            + activeGame.getTileByPosition(pos2).getRepresentationForButtons(activeGame, player)
                            + " using Mahact hero. If they moved themselves and wish to move ground forces, they can do so either with slash command or modify units button.");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("retreatUnitsFrom_")) {
            ButtonHelperModifyUnits.retreatSpaceUnits(buttonID, event, activeGame, player);
            String both = buttonID.replace("retreatUnitsFrom_", "");
            String pos1 = both.split("_")[0];
            String pos2 = both.split("_")[1];
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    ident + " retreated all units in space to "
                            + activeGame.getTileByPosition(pos2).getRepresentationForButtons(activeGame, player));
            String message = trueIdentity + " Use below buttons to move any ground forces or conclude retreat.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                    ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, activeGame, event, pos1, pos2));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("retreat_")) {
            String pos = buttonID.split("_")[1];
            boolean skilled = false;
            if (buttonID.contains("skilled")) {
                skilled = true;
                event.getMessage().delete().queue();
            }
            if (buttonID.contains("foresight")) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        ident + " lost a strategy cc to resolve the foresight ability");
                player.setStrategicCC(player.getStrategicCC() - 1);
                skilled = true;
            }
            String message = trueIdentity
                    + " Use buttons to select a system to move to. Warning: bot does not always know what the valid retreat tiles are, you will need to verify these.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message,
                    ButtonHelperModifyUnits.getRetreatSystemButtons(player, activeGame, pos, skilled));
        } else if (buttonID.startsWith("exhaustAgent_")) {
            ButtonHelperAgents.exhaustAgent(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("exhaustTCS_")) {
            ButtonHelperFactionSpecific.resolveTCSExhaust(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("swapSCs_")) {
            ButtonHelperFactionSpecific.resolveSwapSC(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("domnaStepThree_")) {
            ButtonHelperModifyUnits.resolveDomnaStep3Buttons(event, activeGame, player, buttonID);
        } else if (buttonID.startsWith("domnaStepTwo_")) {
            ButtonHelperModifyUnits.offerDomnaStep3Buttons(event, activeGame, player, buttonID);
        } else if (buttonID.startsWith("setHourAsAFK_")) {
            ButtonHelper.resolveSetAFKTime(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("domnaStepOne_")) {
            ButtonHelperModifyUnits.offerDomnaStep2Buttons(event, activeGame, player, buttonID);
        } else if (buttonID.startsWith("selectBeforeSwapSCs_")) {
            ButtonHelperFactionSpecific.resolveSelectedBeforeSwapSC(player, activeGame, buttonID);
        } else if (buttonID.startsWith("sardakkcommander_")) {
            ButtonHelperCommanders.resolveSardakkCommander(activeGame, player, buttonID, event, ident);
        } else if (buttonID.startsWith("olradinCommanderStep2_")) {
            ButtonHelperCommanders.olradinCommanderStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("useOmenDie_")) {
            ButtonHelperAbilities.useOmenDie(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("peaceAccords_")) {
            ButtonHelperAbilities.resolvePeaceAccords(buttonID, ident, player, activeGame, event);
        } else if (buttonID.startsWith("gheminaLordHero_")) {
            ButtonHelperHeroes.resolveGheminaLordHero(buttonID, ident, player, activeGame, event);
        } else if (buttonID.startsWith("gheminaLadyHero_")) {
            ButtonHelperHeroes.resolveGheminaLadyHero(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("get_so_discard_buttons")) {
            String secretScoreMsg = "_ _\nClick a button below to discard your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveDiscardButtons(activeGame, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            if ("true".equalsIgnoreCase(activeGame.getFactionsThatReactedToThis("forcedScoringOrder"))) {
                List<Player> players = Helper.getInitativeOrder(activeGame);
                String factionsThatHaveResolved = activeGame.getFactionsThatReactedToThis("factionsThatScored");
                if (!Helper.hasEveryoneResolvedBeforeMe(player, factionsThatHaveResolved, players)) {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getIdent(player)
                                    + " the bot has been told to follow a strict public scoring order, and not everyone before you has resolved");
                    return;
                } else {
                    activeGame.setCurrentReacts("factionsThatScored",
                            activeGame.getFactionsThatReactedToThis("factionsThatScored") + "_" + player.getFaction());
                }

            } else {
                activeGame.setCurrentReacts("factionsThatScored",
                        activeGame.getFactionsThatReactedToThis("factionsThatScored") + "_" + player.getFaction());
            }
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublic.scorePO(event, privateChannel, activeGame, player, poIndex);
                ButtonHelper.addReaction(event, false, false, null, "");
            } catch (Exception e) {
                BotLogger.log(event, "Could not parse PO ID: " + poID, e);
                event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.").queue();
            }
        } else if (buttonID.startsWith(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)) {
            String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
            if (!player.getSCs().contains(3)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Only the player who played Politics can assign Speaker");
                return;
            }
            if (activeGame != null) {
                for (Player player_ : activeGame.getPlayers().values()) {
                    if (player_.getFaction().equals(faction)) {
                        activeGame.setSpeaker(player_.getUserID());
                        String message = Emojis.SpeakerToken + " Speaker assigned to: "
                                + ButtonHelper.getIdentOrColor(player_, activeGame);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    }
                }
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("assignSpeaker_")) {
            String faction = StringUtils.substringAfter(buttonID, "assignSpeaker_");
            if (activeGame != null && !activeGame.isFoWMode()) {
                for (Player player_ : activeGame.getPlayers().values()) {
                    if (player_.getFaction().equals(faction)) {
                        activeGame.setSpeaker(player_.getUserID());
                        String message = Emojis.SpeakerToken + " Speaker assigned to: " + player_.getRepresentation();
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    }
                }
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("reveal_stage_")) {
            String lastC = buttonID.replace("reveal_stage_", "");
            if ("2".equalsIgnoreCase(lastC)) {
                new RevealStage2().revealS2(event, event.getChannel());
            } else {
                new RevealStage1().revealS1(event, event.getChannel());
            }

            ButtonHelper.startStatusHomework(event, activeGame);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("scepterE_follow_") || buttonID.startsWith("mahactA_follow_")) {
            boolean setstatus = true;
            int scnum = 1;
            try {
                scnum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
            } catch (NumberFormatException e) {
                try {
                    scnum = Integer.parseInt(lastchar);
                } catch (NumberFormatException e2) {
                    setstatus = false;
                }
            }
            if (setstatus) {
                if (!player.getFollowedSCs().contains(scnum)) {
                    ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scnum, activeGame, event);
                }
                player.addFollowedSC(scnum);
            }
            MessageChannel channel = ButtonHelper.getSCFollowChannel(activeGame, player, scnum);
            if (buttonID.contains("mahact")) {
                MessageHelper.sendMessageToChannel(channel, ident + " exhausted Mahact agent to follow SC#" + scnum);
                Leader playerLeader = player.unsafeGetLeader("mahactagent");
                if (playerLeader != null) {
                    playerLeader.setExhausted(true);
                    for (Player p2 : activeGame.getPlayers().values()) {
                        for (Integer sc2 : p2.getSCs()) {
                            if (sc2 == scnum) {
                                List<Button> buttonsToRemoveCC = new ArrayList<>();
                                String finChecker = "FFCC_" + player.getFaction() + "_";
                                for (Tile tile : ButtonHelper.getTilesWithYourCC(p2, activeGame, event)) {
                                    buttonsToRemoveCC.add(Button.success(
                                            finChecker + "removeCCFromBoard_mahactAgent" + p2.getFaction() + "_"
                                                    + tile.getPosition(),
                                            "Remove CC from " + tile.getRepresentationForButtons(activeGame, player)));
                                }
                                MessageHelper.sendMessageToChannelWithButtons(channel,
                                        trueIdentity + " Use buttons to remove a CC", buttonsToRemoveCC);
                            }
                        }
                    }
                }
            } else {
                MessageHelper.sendMessageToChannel(channel,
                        trueIdentity + " exhausted Scepter of Silly Spelling to follow SC#" + scnum);
                player.addExhaustedRelic("emelpar");
            }
            Emoji emojiToUse = Emoji.fromFormatted(player.getFactionEmoji());

            if (channel instanceof ThreadChannel) {
                activeGame.getActionsChannel().addReactionById(channel.getId(), emojiToUse).queue();
            } else {
                MessageHelper.sendMessageToChannel(channel,
                        "Hey, something went wrong leaving a react, please just hit the no follow button on the SC to do so.");
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("spendAStratCC")) {
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
            }
            String message = deductCC(player, event);
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("sc_follow_") && (!buttonID.contains("leadership"))
                && (!buttonID.contains("trade"))) {
            int scnum = 1;
            boolean setstatus = true;
            try {
                scnum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
            } catch (NumberFormatException e) {
                try {
                    scnum = Integer.parseInt(lastchar);
                } catch (NumberFormatException e2) {
                    setstatus = false;
                }
            }
            if (player.getSCs().contains(scnum)) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player
                        .getRepresentation()
                        + " you have the SC card in hand and therefore should not be spending a CC here. You can override this protection via /player stats");
                return;
            }
            boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "");
            if (!used) {
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
                }
                String message = deductCC(player, event);

                if (setstatus) {
                    if (!player.getFollowedSCs().contains(scnum)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scnum, activeGame, event);
                    }
                    player.addFollowedSC(scnum);
                }
                ButtonHelper.addReaction(event, false, false, message, "");
            }
        } else if (buttonID.startsWith("sc_no_follow_")) {
            int scnum2 = 1;
            boolean setstatus = true;
            try {
                scnum2 = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
            } catch (NumberFormatException e) {
                try {
                    scnum2 = Integer.parseInt(lastchar);
                } catch (NumberFormatException e2) {
                    setstatus = false;
                }
            }
            if (setstatus) {
                player.addFollowedSC(scnum2);
            }
            ButtonHelper.addReaction(event, false, false, "Not Following", "");
            Set<Player> players = playerUsedSC.get(messageID);
            if (players == null) {
                players = new HashSet<>();
            }
            players.remove(player);
            playerUsedSC.put(messageID, players);
            if (scnum2 == 8 && !activeGame.isHomeBrewSCMode()) {
                String key = "factionsThatAreNotDiscardingSOs";
                String key2 = "queueToDrawSOs";
                String key3 = "potentialBlockers";
                if (activeGame.getFactionsThatReactedToThis(key2).contains(player.getFaction() + "*")) {
                    activeGame.setCurrentReacts(key2,
                            activeGame.getFactionsThatReactedToThis(key2).replace(player.getFaction() + "*", ""));
                }
                if (!activeGame.getFactionsThatReactedToThis(key).contains(player.getFaction() + "*")) {
                    activeGame.setCurrentReacts(key,
                            activeGame.getFactionsThatReactedToThis(key) + player.getFaction() + "*");
                }
                if (activeGame.getFactionsThatReactedToThis(key3).contains(player.getFaction() + "*")) {
                    activeGame.setCurrentReacts(key3,
                            activeGame.getFactionsThatReactedToThis(key3).replace(player.getFaction() + "*", ""));
                    Helper.resolveQueue(activeGame, event);
                }

            }
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            ButtonHelper.addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            String bID = buttonID.replace("movedNExplored_", "");
            String[] info = bID.split("_");
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2], player, false,
                    activeGame, 1, false);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolveExp_Look_")) {
            String deckType = buttonID.replace("resolveExp_Look_", "");
            ButtonHelperFactionSpecific.resolveExpLook(player, activeGame, event, deckType);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("discardExploreTop_")) {
            String deckType = buttonID.replace("discardExploreTop_", "");
            ButtonHelperFactionSpecific.resolveExpDiscard(player, activeGame, event, deckType);
        } else if (buttonID.startsWith("relic_look_top")) {
            List<String> relicDeck = activeGame.getAllRelics();
            if (relicDeck.isEmpty()) {
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, "Relic deck is empty");
                return;
            }
            String relicID = relicDeck.get(0);
            RelicModel relicModel = Mapper.getRelic(relicID);
            String rsb = "**Relic - Look at Top**\n" + player.getRepresentation() + "\n"
                    + relicModel.getSimpleRepresentation();
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, rsb);
            ButtonHelper.addReaction(event, true, false, "Looked at top of the Relic deck.", "");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("explore_look_All")) {
            List<String> cdeck = activeGame.getExploreDeck("cultural");
            List<String> hdeck = activeGame.getExploreDeck("hazardous");
            List<String> ideck = activeGame.getExploreDeck("industrial");
            List<String> cdiscardPile = activeGame.getExploreDiscard("cultural");
            List<String> hdiscardPile = activeGame.getExploreDiscard("hazardous");
            List<String> idiscardPile = activeGame.getExploreDiscard("industrial");
            String trait = "cultural";
            String traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
            String playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
            if (cdeck.isEmpty() && cdiscardPile.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
            }

            StringBuilder csb = new StringBuilder();
            csb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            String ctopCard = cdeck.get(0);
            csb.append(ExploreSubcommandData.displayExplore(ctopCard));

            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, csb.toString());
            trait = "hazardous";
            traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
            playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
            if (hdeck.isEmpty() && hdiscardPile.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
            }

            StringBuilder hsb = new StringBuilder();
            hsb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            String htopCard = hdeck.get(0);
            hsb.append(ExploreSubcommandData.displayExplore(htopCard));

            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, hsb.toString());
            trait = "industrial";
            traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
            playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
            if (ideck.isEmpty() && idiscardPile.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
            }

            StringBuilder isb = new StringBuilder();
            isb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            String itopCard = ideck.get(0);
            isb.append(ExploreSubcommandData.displayExplore(itopCard));

            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, isb.toString());
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "top of Hazardous, Cultural and Industrial explore decks has been set to "
                            + playerFactionNameWithEmoji
                            + " Cards info thread.");
            ButtonHelper.addReaction(event, true, false, "Looked at top of Hazardous, Cultural and Industrial decks.",
                    "");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("distant_suns_")) {
            ButtonHelperAbilities.distantSuns(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("autoAssignGroundHits_")) {// "autoAssignGroundHits_"
            ButtonHelperModifyUnits.autoAssignGroundCombatHits(player, activeGame, buttonID.split("_")[1],
                    Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("autoAssignSpaceHits_")) {// "autoAssignGroundHits_"
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, activeGame,
                            activeGame.getTileByPosition(buttonID.split("_")[1]),
                            Integer.parseInt(buttonID.split("_")[2]), event, false));
        } else if (buttonID.startsWith("cancelSpaceHits_")) {// "autoAssignGroundHits_"
            Tile tile = activeGame.getTileByPosition(buttonID.split("_")[1]);
            int h = Integer.parseInt(buttonID.split("_")[2]) - 1;
            Player opponent = player;
            String msg = "\n" + opponent.getRepresentation(true, true) + " cancelled 1 hit with an ability";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Button.success(finChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h,
                    "Auto-assign Hits"));
            buttons.add(Button.danger("getDamageButtons_" + tile.getPosition(), "Manually Assign Hits"));
            buttons.add(Button.secondary("cancelSpaceHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
            String msg2 = "You can automatically assign hits. The hits would be assigned in the following way:\n\n"
                    + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, activeGame, tile, h, event, true);
            // MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
            event.getMessage().editMessage(msg2).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                    .queue();
        } else if (buttonID.startsWith("autoAssignAFBHits_")) {// "autoAssignGroundHits_"
            ButtonHelperModifyUnits.autoAssignAntiFighterBarrageHits(player, activeGame, buttonID.split("_")[1],
                    Integer.parseInt(buttonID.split("_")[2]), event);
        } else if (buttonID.startsWith("getPlagiarizeButtons")) {
            activeGame.setComponentAction(true);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Select the tech you want",
                    ButtonHelperActionCards.getPlagiarizeButtons(activeGame, player));
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "inf");
            Button doneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
            buttons.add(doneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    "Click the names of the planets you wish to exhaust to pay the 5 influence", buttons);
            event.getMessage().delete().queue();
            // "saarHeroResolution_"
        } else if (buttonID.startsWith("forceARefresh_")) {
            ButtonHelper.forceARefresh(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("arboHeroBuild_")) {
            ButtonHelperHeroes.resolveArboHeroBuild(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("saarHeroResolution_")) {
            ButtonHelperHeroes.resolveSaarHero(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("refreshWithOlradinAgent_")) {
            ButtonHelperAgents.resolveRefreshWithOlradinAgent(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("resolveGrace_")) {
            ButtonHelperAbilities.resolveGrace(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("increaseTGonSC_")) {
            String sc = buttonID.replace("increaseTGonSC_", "");
            int scNum = Integer.parseInt(sc);
            Map<Integer, Integer> scTradeGoods = activeGame.getScTradeGoods();
            int tgCount = scTradeGoods.get(scNum);
            activeGame.setScTradeGood(scNum, (tgCount + 1));
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Added 1tg to SC #" + scNum + ". There are now " + (tgCount + 1) + " tgs on it.");
        } else if (buttonID.startsWith("strategicAction_")) {
            int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
            new SCPlay().playSC(event, scNum, activeGame, mainGameChannel, player);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolve_explore_")) {
            String bID = buttonID.replace("resolve_explore_", "");
            String[] info = bID.split("_");
            String cardID = info[0];
            String planetName = info[1];
            Tile tile = activeGame.getTileFromPlanet(planetName);
            String messageText = player.getRepresentation() + " explored " + "Planet "
                    + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tile.getPosition() + ")*:";
            if (buttonID.contains("_distantSuns")) {
                messageText = player.getFactionEmoji() + " chose to resolve: ";
            }
            ExploreSubcommandData.resolveExplore(event, cardID, tile, planetName, messageText, player, activeGame);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("refresh_")) {
            String planetName = buttonID.split("_")[1];
            Player p2 = player;
            if (StringUtils.countMatches(buttonID, "_") > 1) {
                String faction = buttonID.split("_")[2];
                p2 = activeGame.getPlayerFromColorOrFaction(faction);
            }

            new PlanetRefresh().doAction(p2, planetName, activeGame);
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
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeGame);
            } else {
                totalVotesSoFar = ident + " Readied "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeGame);
            }
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
        } else if (buttonID.startsWith("assignDamage_")) {// removeThisTypeOfUnit_
            ButtonHelperModifyUnits.assignDamage(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("removeThisTypeOfUnit_")) {
            ButtonHelperModifyUnits.removeThisTypeOfUnit(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("repairDamage_")) {
            ButtonHelperModifyUnits.repairDamage(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("assCannonNDihmohn_")) {
            ButtonHelperModifyUnits.resolveAssaultCannonNDihmohnCommander(buttonID, event, player, activeGame);
        } else if (buttonID.startsWith("refreshViewOfSystem_")) {
            String rest = buttonID.replace("refreshViewOfSystem_", "");
            String pos = rest.split("_")[0];
            Player p1 = activeGame.getPlayerFromColorOrFaction(rest.split("_")[1]);
            Player p2 = activeGame.getPlayerFromColorOrFaction(rest.split("_")[2]);
            String groundOrSpace = rest.split("_")[3];
            FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, 0, pos, event);
            MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
            List<Button> buttons = StartCombat.getGeneralCombatButtons(activeGame, pos, p1, p2, groundOrSpace);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
        } else if (buttonID.startsWith("getDamageButtons_")) {// "repealLaw_"
            if (buttonID.contains("deleteThis")) {
                buttonID = buttonID.replace("deleteThis", "");
                event.getMessage().delete().queue();
            }
            String pos = buttonID.replace("getDamageButtons_", "");
            List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame,
                    activeGame.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    trueIdentity + " Use buttons to resolve", buttons);
        } else if (buttonID.startsWith("repealLaw_")) {// "repealLaw_"
            ButtonHelperActionCards.repealLaw(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("getRepairButtons_")) {
            String pos = buttonID.replace("getRepairButtons_", "");
            List<Button> buttons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, activeGame,
                    activeGame.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    trueIdentity + " Use buttons to resolve", buttons);
            // ("autoneticMemoryStep2
        } else if (buttonID.startsWith("codexCardPick_")) {
            ButtonHelper.deleteTheOneButton(event);
            ButtonHelper.pickACardFromDiscardStep1(activeGame, player);

        } else if (buttonID.startsWith("pickFromDiscard_")) {
            ButtonHelper.pickACardFromDiscardStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("autoneticMemoryStep2_")) {
            ButtonHelperAbilities.autoneticMemoryStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("autoneticMemoryDecline_")) {
            ButtonHelperAbilities.autoneticMemoryDecline(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("autoneticMemoryStep3")) {
            if (buttonID.contains("autoneticMemoryStep3a")) {
                ButtonHelperAbilities.autoneticMemoryStep3a(activeGame, player, event);
            } else {
                ButtonHelperAbilities.autoneticMemoryStep3b(activeGame, player, event);
            }
        } else if (buttonID.startsWith("assignHits_")) {

            ButtonHelperModifyUnits.assignHits(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("seedySpace_")) {
            ButtonHelper.resolveSeedySpace(activeGame, buttonID, player, event);
        } else if (buttonID.startsWith("prophetsTears_")) {
            player.addExhaustedRelic("prophetstears");
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    ButtonHelper.getIdent(player) + " Chose to exhaust The Prophets Tears");
            if (buttonID.contains("AC")) {
                String message;
                if (player.hasAbility("scheming")) {
                    activeGame.drawActionCard(player.getUserID());
                    activeGame.drawActionCard(player.getUserID());
                    message = ButtonHelper.getIdent(player)
                            + " Drew 2 AC With Scheming. Please Discard An AC with the blue buttons";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentation(true, true) + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                } else if (player.hasAbility("autonetic_memory")) {
                    ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 1);
                    message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
                } else {
                    activeGame.drawActionCard(player.getUserID());
                    message = ButtonHelper.getIdent(player) + " Drew 1 AC";
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                }
                if (player.getLeaderIDs().contains("yssarilcommander")
                        && !player.hasLeaderUnlocked("yssarilcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                }

                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
                ButtonHelper.checkACLimit(activeGame, event, player);
                ButtonHelper.deleteTheOneButton(event);
            } else {
                String msg = " exhausted the prophets tears";
                String exhaustedMessage = event.getMessage().getContentRaw();
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
                if (!exhaustedMessage.contains("Click the names")) {
                    exhaustedMessage = exhaustedMessage + ", " + msg;
                } else {
                    exhaustedMessage = ident + msg;
                }
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            }
        } else if (buttonID.startsWith("swapTechs_")) {
            ButtonHelperHeroes.resolveAJolNarSwapStep2(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("jnHeroSwapOut_")) {
            ButtonHelperHeroes.resolveAJolNarSwapStep1(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("jolNarAgentRemoval_")) {
            ButtonHelperAgents.resolveJolNarAgentRemoval(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("biostimsReady_")) {
            ButtonHelper.bioStimsReady(activeGame, event, player, buttonID);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("nekroHeroStep2_")) {
            ButtonHelperHeroes.resolveNekroHeroStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("refreshVotes_")) {
            String votes = buttonID.replace("refreshVotes_", "");
            List<Button> voteActionRow = Helper.getPlanetRefreshButtons(event, player, activeGame);
            Button concludeRefreshing = Button.danger(finsFactionCheckerPrefix + "votes_" + votes,
                    "Done readying planets.");
            voteActionRow.add(concludeRefreshing);
            String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("getAllTechOfType_")) {
            String techType = buttonID.replace("getAllTechOfType_", "");
            boolean noPay = false;
            if (techType.contains("_")) {
                techType = techType.split("_")[0];
                noPay = true;
            }
            List<TechnologyModel> techs = Helper.getAllTechOfAType(activeGame, techType, player);
            List<Button> buttons = Helper.getTechButtons(techs, techType, player);
            if (noPay) {
                buttons = Helper.getTechButtons(techs, techType, player, "nekro");
            }

            buttons.add(Button.secondary("acquireATech", "Get Tech of a Different Type"));
            String message = player.getRepresentation() + " Use the buttons to get the tech you want";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("getTech_")) {
            ButtonHelper.getTech(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("riftUnit_")) {
            ButtonHelper.riftUnitButton(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("getRiftButtons_")) {
            Tile tile = activeGame.getTileByPosition(buttonID.replace("getRiftButtons_", ""));
            MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
            String msg = ident + " use buttons to rift units";
            MessageHelper.sendMessageToChannelWithButtons(channel, msg,
                    ButtonHelper.getButtonsForRiftingUnitsInSystem(player, activeGame, tile));
        } else if (buttonID.startsWith("riftAllUnits_")) {
            ButtonHelper.riftAllUnitsButton(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("cabalVortextCapture_")) {
            ButtonHelperFactionSpecific.resolveVortexCapture(buttonID, player, activeGame, event);
        } else if (buttonID.startsWith("takeAC_")) {
            ButtonHelperFactionSpecific.mageon(buttonID, event, activeGame, player, trueIdentity);
        } else if (buttonID.startsWith("spend_")) {
            String planetName = buttonID.split("_")[1];
            String whatIsItFor = "both";
            if (buttonID.split("_").length > 2) {
                whatIsItFor = buttonID.split("_")[2];
            }
            new PlanetExhaust().doAction(player, planetName, activeGame);
            player.addSpentThing(planetName);
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
            if(whatIsItFor.contains("tech") && player.hasAbility("ancient_knowledge")){
                String planet = planetName;
                if((Mapper.getPlanet(planet).getTechSpecialties() != null&& Mapper.getPlanet(planet).getTechSpecialties().size() > 0)|| ButtonHelper.checkForTechSkipAttachments(activeGame, planet)){
                    String msg = player.getRepresentation()+" due to your ancient knowledge ability, you may be eligible to receive a tech here if you exhausted this planet ("+planet+") for its tech skip";
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Button.primary("gain_1_comms", "Gain 1 Commodity").withEmoji(Emoji.fromFormatted(Emojis.comm)));
                    buttons.add(Button.danger("deleteButtons", "Didn't use it for tech speciality"));
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getFactionEmoji()+" may have the opportunity to gain a comm from their ancient knowledge ability due to exhausting a tech skip planet");
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
                }
            }
            String exhaustedMessage = event.getMessage().getContentRaw();
            // if (!exhaustedMessage.contains("Click the names")) {
            // exhaustedMessage = exhaustedMessage + ", exhausted "
            // + Helper.getPlanetRepresentation(planetName, activeGame);
            // } else {
            // exhaustedMessage = ident + " exhausted "
            // + Helper.getPlanetRepresentation(planetName, activeGame);
            // }
            exhaustedMessage = Helper.buildSpentThingsMessage(player, activeGame, whatIsItFor);
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        } else if (buttonID.startsWith("finishTransaction_")) {
            String player2Color = buttonID.split("_")[1];
            Player player2 = activeGame.getPlayerFromColorOrFaction(player2Color);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAbilities.pillageCheck(player2, activeGame);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("sabotage_")) {
            String typeNName = buttonID.replace("sabotage_", "");
            String type = typeNName.substring(0, typeNName.indexOf("_"));
            String acName = typeNName.replace(type + "_", "");
            String message = "Cancelling the AC \"" + acName + "\" using ";
            Integer count = activeGame.getAllActionCardsSabod().get(acName);
            if (count == null) {
                activeGame.setSpecificActionCardSaboCount(acName, 1);
            } else {
                activeGame.setSpecificActionCardSaboCount(acName, 1 + count);
            }
            if (activeGame.getMessageIDsForSabo().contains(event.getMessageId())) {
                activeGame.removeMessageIDForSabo(event.getMessageId());
            }
            boolean sendReact = true;
            if ("empy".equalsIgnoreCase(type)) {
                message = message + "a Watcher mech! The Watcher should be removed now by the owner.";
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                        "Remove the watcher",
                        ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, activeGame, "mech"));
                event.getMessage().delete().queue();
            } else if ("xxcha".equalsIgnoreCase(type)) {
                message = message
                        + "the \"Instinct Training\" tech! The tech has been exhausted and a strategy CC removed.";
                if (player.hasTech(AliasHandler.resolveTech("Instinct Training"))) {
                    player.exhaustTech(AliasHandler.resolveTech("Instinct Training"));
                    if (player.getStrategicCC() > 0) {
                        player.setStrategicCC(player.getStrategicCC() - 1);
                    }
                    event.getMessage().delete().queue();
                } else {
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Someone clicked the Instinct Training button but did not have the tech.");
                }
            } else if ("ac".equalsIgnoreCase(type)) {
                message = message + "A Sabotage!";
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
                    PlayAC.playAC(event, activeGame, player, saboID, activeGame.getActionsChannel(),
                            activeGame.getGuild());
                } else {
                    message = "Tried to play a sabo but found none in hand.";
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                            "Someone clicked the AC sabo button but did not have a sabo in hand.");
                }
            }

            if (acName.contains("Rider") || acName.contains("Sanction")) {
                AgendaHelper.reverseRider("reverse_" + acName, event, activeGame, player, ident);
                // MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "Reversed
                // the rider "+ acName);
            }
            if (sendReact) {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                        message + "\n" + activeGame.getPing());
            }
        } else if (buttonID.startsWith("reduceTG_")) {
            int tgLoss = Integer.parseInt(buttonID.split("_")[1]);

            String whatIsItFor = "both";
            if (buttonID.split("_").length > 2) {
                whatIsItFor = buttonID.split("_")[2];
            }
            String message = ident + " reduced tgs by " + tgLoss + " (" + player.getTg() + "->"
                    + (player.getTg() - tgLoss) + ")";
            if (tgLoss > player.getTg()) {
                message = "You dont have " + tgLoss + " tgs. No change made.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            } else {
                player.setTg(player.getTg() - tgLoss);
                player.increaseTgsSpentThisWindow(tgLoss);
            }
            if (tgLoss > player.getTg()) {
                ButtonHelper.deleteTheOneButton(event);
            }
            String editedMessage = Helper.buildSpentThingsMessage(player, activeGame, whatIsItFor);
            event.getMessage().editMessage(editedMessage).queue();
        } else if (buttonID.startsWith("reduceComm_")) {
            int tgLoss = Integer.parseInt(buttonID.split("_")[1]);
            String whatIsItFor = "both";
            if (buttonID.split("_").length > 2) {
                whatIsItFor = buttonID.split("_")[2];
            }
            String message = ident + " reduced comms by " + tgLoss + " (" + player.getCommodities() + "->"
                    + (player.getCommodities() - tgLoss) + ")";

            if (tgLoss > player.getCommodities()) {
                message = "You dont have " + tgLoss + " comms. No change made.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            } else {
                player.setCommodities(player.getCommodities() - tgLoss);
                player.addSpentThing(message);
            }
            String editedMessage = Helper.buildSpentThingsMessage(player, activeGame, whatIsItFor);
            Leader playerLeader = player.getLeader("keleresagent").orElse(null);
            if (playerLeader != null && !playerLeader.isExhausted()) {
                playerLeader.setExhausted(true);
                String messageText = player.getRepresentation() +
                        " exhausted " + Helper.getLeaderFullRepresentation(playerLeader);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), messageText);

            }
            event.getMessage().editMessage(editedMessage).queue();
        } else if (buttonID.startsWith("lanefirAgentRes_")) {
            ButtonHelperAgents.resolveLanefirAgent(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("absolsdn_")) {
            ButtonHelper.resolveAbsolScanlink(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("resFrontier_")) {
            buttonID = buttonID.replace("resFrontier_", "");
            String[] stuff = buttonID.split("_");
            String cardChosen = stuff[0];
            String pos = stuff[1];
            String cardRefused = stuff[2];
            activeGame.addExplore(cardRefused);
            new ExpFrontier().expFrontAlreadyDone(event, activeGame.getTileByPosition(pos), activeGame, player,
                    cardChosen);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("finishComponentAction_")) {
            String message = "Use buttons to end turn or do another action.";
            List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("pillage_")) {
            ButtonHelperAbilities.pillage(buttonID, event, activeGame, player, ident, finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("mykoheroSteal_")) {
            ButtonHelperHeroes.resolveMykoHero(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("exhaust_")) {
            AgendaHelper.exhaustStuffForVoting(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("exhaustViaDiplomats_")) {
            ButtonHelperAbilities.resolveDiplomatExhaust(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("exhaustForVotes_")) {
            AgendaHelper.exhaustForVotes(event, player, activeGame, buttonID);
        } else if (buttonID.startsWith("diplo_")) {
            ButtonHelper.resolveDiploPrimary(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("doneWithOneSystem_")) {
            ButtonHelperTacticalAction.finishMovingFromOneTile(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("cavStep2_")) {
            ButtonHelperFactionSpecific.resolveCavStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("resolveAgendaVote_")) {
            AgendaHelper.resolvingAnAgendaVote(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("bombardConfirm_")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.secondary(buttonID.replace("bombardConfirm_", ""), "Roll Bombardment"));
            String message = player.getRepresentation(true, true)
                    + " please declare what units are bombarding what planet before hitting this button (if you have two dreads and are splitting their bombardment across two planets, specify which planet the first one is hitting). The bot does not track this.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        } else if (buttonID.startsWith("combatRoll_")) {
            ButtonHelper.resolveCombatRoll(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("transitDiodes_")) {
            ButtonHelper.resolveTransitDiodesStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("novaSeed_")) {
            new NovaSeed().secondHalfOfNovaSeed(player, event, activeGame.getTileByPosition(buttonID.split("_")[1]),
                    activeGame);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("celestialImpact_")) {
            new ZelianHero().secondHalfOfCelestialImpact(player, event,
                    activeGame.getTileByPosition(buttonID.split("_")[1]), activeGame);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("echoPlaceFrontier_")) {
            ButtonHelper.resolveEchoPlaceFrontier(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("forceAbstainForPlayer_")) {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Player was forcefully abstained");
            String faction = buttonID.replace("forceAbstainForPlayer_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            AgendaHelper.resolvingAnAgendaVote("resolveAgendaVote_0", event, activeGame, p2);
        } else if (buttonID.startsWith("fixerVotes_")) {
            String voteMessage = "Thank you for specifying, please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1);
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes - 9, votes);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("useTech_")) {
            String tech = buttonID.replace("useTech_", "");
            String techRepresentation = Mapper.getTech(tech).getRepresentation(false);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    (player.getRepresentation() + " used tech: " + techRepresentation));
            switch (tech) {
                case "st" -> { // Sarween Tools
                    player.addSpentThing("sarween");
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, activeGame, "res");
                    ButtonHelper.deleteTheOneButton(event);
                    event.getMessage().editMessage(exhaustedMessage).queue();
                }
                case "absol_st" -> { // Absol's Sarween Tools
                    player.addSpentThing("absol_sarween");
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, activeGame, "res");
                    ButtonHelper.deleteTheOneButton(event);
                    event.getMessage().editMessage(exhaustedMessage).queue();
                }
                case "absol_pa" -> { // Absol's Psychoarcheology
                    List<Button> absolPAButtons = new ArrayList<>();
                    absolPAButtons.add(Button.primary("getDiscardButtonsACs", "Discard")
                            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                    for (String planetID : player.getReadiedPlanets()) {
                        Planet planet = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planetID, activeGame);
                        if (planet != null && planet.getOriginalPlanetType() != null) {
                            List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(activeGame, planet,
                                    player);
                            absolPAButtons.addAll(planetButtons);
                        }
                    }
                    ButtonHelper.deleteTheOneButton(event);
                    MessageHelper
                            .sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                                    player.getRepresentation(true, true)
                                            + " use buttons to discard an AC and explore a readied Planet",
                                    absolPAButtons);
                }
            }
        } else if (buttonID.startsWith("exhaustTech_")) {
            String tech = buttonID.replace("exhaustTech_", "");
            TechnologyModel techModel = Mapper.getTech(tech);
            String exhaustMessage = player.getRepresentation() + " exhausted tech "
                    + techModel.getRepresentation(false);
            if (tech.contains("absol")) {
                switch (activeGame.getOutputVerbosity()) {
                    case Constants.VERBOSITY_VERBOSE -> MessageHelper.sendMessageToChannelWithEmbed(
                            event.getMessageChannel(), exhaustMessage, techModel.getRepresentationEmbed());
                    default -> MessageHelper.sendMessageToChannel(event.getMessageChannel(), exhaustMessage);
                }
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), exhaustMessage);
            }
            player.exhaustTech(tech);
            switch (tech) {
                case "bs" -> { // Bio-stims
                    ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeGame, event, player, false);
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "absol_bs" -> { // Bio-stims
                    ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeGame, event, player, true);
                }
                case "absol_nm" -> { // Absol's Neural Motivator
                    event.getMessage().delete().queue();
                    Button draw2ACButton = Button
                            .secondary(player.getFinsFactionCheckerPrefix() + "sc_ac_drawdeleteThisButton",
                                    "Draw 2 Action Cards")
                            .withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
                    MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", draw2ACButton);
                    ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
                }
                case "td" -> { // Transit Diodes
                    ButtonHelper.resolveTransitDiodesStep1(activeGame, player);
                }
                case "miltymod_hm" -> { // MiltyMod Hyper Metabolism (Gain a CC)
                    Button gainCC = Button.success(player.getFinsFactionCheckerPrefix() + "gain_CC", "Gain CC");
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            player.getFactionEmojiOrColor() + " use button to gain a CC:", List.of(gainCC));
                }
                case "aida", "sar", "htp" -> {
                    ButtonHelper.deleteTheOneButton(event);
                    if (buttonLabel.contains("(")) {
                        player.addSpentThing(tech + "_");
                    } else {
                        player.addSpentThing(tech);
                    }
                    String exhaustedMessage = Helper.buildSpentThingsMessage(player, activeGame, "res");
                    event.getMessage().editMessage(exhaustedMessage).queue();
                }
                case "pi" -> { // Predictive Intelligence
                    ButtonHelper.deleteTheOneButton(event);
                    Button deleButton = Button.danger("FFCC_" + player.getFaction() + "_" + "deleteButtons",
                            "Delete These Buttons");
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            fowIdentity + " use buttons to redistribute",
                            List.of(Buttons.REDISTRIBUTE_CCs, deleButton));
                }
                case "gls" -> { // Graviton Laser System
                    // Do Nothing
                }
                case "mi" -> { // Mageon
                    event.getMessage().delete().queue();
                    List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "getACFrom", null);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            player.getRepresentation(true, true) + " Select who you would like to Mageon.", buttons);
                    ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
                }
                case "vtx", "absol_vtx" -> { // Vortex
                    event.getMessage().delete().queue();
                    List<Button> buttons = ButtonHelperFactionSpecific.getUnitButtonsForVortex(player, activeGame,
                            event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            player.getRepresentation(true, true) + " Select what unit you would like to capture",
                            buttons);
                    ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
                }
                case "wg" -> { // Wormhole Generator
                    event.getMessage().delete().queue();
                    List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                    String message = player.getRepresentation(true, true) + " select type of wormhole you wish to drop";
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            message, buttons);
                    ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
                }
                case "absol_wg" -> { // Absol's Wormhole Generator
                    event.getMessage().delete().queue();
                    List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
                    String message = player.getRepresentation(true, true) + " select type of wormhole you wish to drop";
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            message, buttons);
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            message, buttons);
                    ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
                }
                case "pm" -> { // Production Biomes
                    event.getMessage().delete().queue();
                    ButtonHelperFactionSpecific.resolveProductionBiomesStep1(player, activeGame, event);
                    ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
                }
                case "lgf" -> { // Lazax Gate Folding
                    if (player.getPlanets().contains("mr")) {
                        event.getMessage().delete().queue();
                        new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileFromPlanet("mr"),
                                "inf mr", activeGame);
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getFactionEmoji() + " added 1 infantry to Mecatol Rex using Laxax Gate Folding");
                        ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
                    } else {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation() + " You do not control Mecatol Rex");
                        player.refreshTech("lgf");
                    }
                }
                case "sr" -> { // Sling Relay
                    event.getMessage().delete().queue();
                    List<Button> buttons = new ArrayList<>();
                    List<Tile> tiles = new ArrayList<>(ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player,
                            UnitType.Spacedock, UnitType.CabalSpacedock, UnitType.PlenaryOrbital));
                    if (player.hasUnit("ghoti_flagship")) {
                        tiles.addAll(
                                ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship));
                    }
                    List<String> pos2 = new ArrayList<>();
                    for (Tile tile : tiles) {
                        if (!pos2.contains(tile.getPosition())) {
                            Button tileButton = Button.success("produceOneUnitInTile_" + tile.getPosition() + "_sling",
                                    tile.getRepresentationForButtons(activeGame, player));
                            buttons.add(tileButton);
                            pos2.add(tile.getPosition());
                        }
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            "Select which tile you would like to sling in.", buttons);
                    ButtonHelper.serveNextComponentActionButtons(event, activeGame, player);
                }
            }
        } else if (buttonID.startsWith("planetOutcomes_")) {
            String factionOrColor = buttonID.substring(buttonID.indexOf("_") + 1);
            Player planetOwner = activeGame.getPlayerFromColorOrFaction(factionOrColor);
            String voteMessage = "Chose to vote for one of " + factionOrColor
                    + "'s planets. Click buttons for which outcome to vote for.";
            List<Button> outcomeActionRow;
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeGame, "outcome", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("indoctrinate_")) {
            ButtonHelperAbilities.resolveFollowUpIndoctrinationQuestion(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("assimilate_")) {
            UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(buttonID.split("_")[1], activeGame);
            ButtonHelperModifyUnits.infiltratePlanet(player, activeGame, uH, event);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("letnevMechRes_")) {
            ButtonHelperFactionSpecific.resolveLetnevMech(player, activeGame, buttonID, event);// winnuPNPlay
        } else if (buttonID.startsWith("winnuPNPlay_")) {
            ButtonHelperFactionSpecific.resolveWinnuPN(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("initialIndoctrination_")) {
            ButtonHelperAbilities.resolveInitialIndoctrinationQuestion(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("utilizeSolCommander_")) {
            ButtonHelperCommanders.resolveSolCommander(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("mercerMove_")) {
            ButtonHelperAgents.resolveMercerMove(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("tiedPlanets_")) {
            buttonID = buttonID.replace("tiedPlanets_", "");
            buttonID = buttonID.replace("resolveAgendaVote_outcomeTie*_", "");
            String factionOrColor = buttonID;
            Player planetOwner = activeGame.getPlayerFromColorOrFaction(factionOrColor);
            String voteMessage = "Chose to break tie for one of " + factionOrColor
                    + "'s planets. Use buttons to select which one.";
            List<Button> outcomeActionRow;
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeGame,
                    "resolveAgendaVote_outcomeTie*", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("planetRider_")) {
            buttonID = buttonID.replace("planetRider_", "");
            String factionOrColor = buttonID.substring(0, buttonID.indexOf("_"));
            Player planetOwner = activeGame.getPlayerFromColorOrFaction(factionOrColor);
            String voteMessage = "Chose to rider for one of " + factionOrColor
                    + "'s planets. Use buttons to select which one.";
            List<Button> outcomeActionRow;
            buttonID = buttonID.replace(factionOrColor + "_", "");
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeGame,
                    finsFactionCheckerPrefix, buttonID);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("distinguished_")) {
            String voteMessage = "You added 5 votes to your total. Please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1);
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtonsVersion2(votes, votes + 5);
            voteActionRow.add(Button.secondary("distinguishedReverse_" + votes, "Decrease Votes"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("distinguishedReverse_")) {
            String voteMessage = "You subtracted 5 votes to your total. Please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1);
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtonsVersion2(votes - 5, votes);
            voteActionRow.add(Button.secondary("distinguishedReverse_" + votes, "Decrease Votes"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("startCabalAgent_")) {
            ButtonHelperAgents.startCabalAgent(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("stellarConvert_")) {
            ButtonHelper.resolveStellar(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("forwardSupplyBaseStep2_")) {
            ButtonHelperActionCards.resolveForwardSupplyBaseStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("divertFunding@")) {
            ButtonHelperActionCards.divertFunding(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("newPrism@")) {
            PlanetExhaustAbility.newPrismPart2(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("cabalAgentCapture_")) {
            ButtonHelperAgents.resolveCabalAgentCapture(buttonID, player, activeGame, event);
        } else if (buttonID.startsWith("cabalRelease_")) {
            ButtonHelperFactionSpecific.resolveReleaseButton(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("kolleccRelease_")) {
            ButtonHelperFactionSpecific.resolveKolleccReleaseButton(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("shroudOfLithStart")) {
            ButtonHelper.deleteTheOneButton(event);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Select up to 2 ships and 2 ground forces to place in the space area",
                    ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, activeGame));
        } else if (buttonID.startsWith("getReleaseButtons")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    trueIdentity + " you can release units one at a time with the buttons",
                    ButtonHelperFactionSpecific.getReleaseButtons(player, activeGame));
        } else if (buttonID.startsWith("ghotiHeroIn_")) {
            String pos = buttonID.substring(buttonID.indexOf("_") + 1);
            List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, activeGame, event,
                    activeGame.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    trueIdentity + " select which unit you'd like to replace", buttons);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("glimmersHeroIn_")) {
            String pos = buttonID.substring(buttonID.indexOf("_") + 1);
            List<Button> buttons = ButtonHelperHeroes.getUnitsToGlimmersHero(player, activeGame, event,
                    activeGame.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    trueIdentity + " select which unit you'd like to duplicate", buttons);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("arboAgentIn_")) {
            String pos = buttonID.substring(buttonID.indexOf("_") + 1);
            List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, activeGame, event,
                    activeGame.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    trueIdentity + " select which unit you'd like to replace", buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("saarMechRes_")) {
            ButtonHelperFactionSpecific.placeSaarMech(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("cymiaeCommanderRes_")) {
            ButtonHelperCommanders.cymiaeCommanderRes(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("arboAgentPutShip_")) {
            ButtonHelperAgents.arboAgentPutShip(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("setAutoPassMedian_")) {
            String hours = buttonID.split("_")[1];
            int median = Integer.parseInt(hours);
            player.setAutoSaboPassMedian(median);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set median time to " + median + " hours");
            if (median > 0) {
                if (player.hasAbility("quash") || player.ownsPromissoryNote("rider")
                        || player.getPromissoryNotes().keySet().contains("riderm")
                        || player.hasAbility("radiance") || player.hasAbility("galactic_threat") || player.hasAbility("conspirators")
                        || player.ownsPromissoryNote("riderx")
                        || player.ownsPromissoryNote("riderm") || player.ownsPromissoryNote("ridera")
                        || player.hasTechReady("gr")) {
                } else {
                    List<Button> buttons = new ArrayList<>();
                    String msg = player.getRepresentation()
                            + " The bot can also auto react for you when you have no whens/afters, using the same interval. Default for this is off. This will only apply to this game. If you have any whens or afters or related when/after abilities, it will not do anything. ";
                    buttons.add(Button.success("playerPrefDecision_true_agenda", "Turn on"));
                    buttons.add(Button.success("playerPrefDecision_false_agenda", "Turn off"));
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg, buttons);
                }
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("arboAgentOn_")) {
            String pos = buttonID.split("_")[1];
            String unit = buttonID.split("_")[2];
            List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(player, activeGame, event,
                    activeGame.getTileByPosition(pos), unit);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                    trueIdentity + " select which unit you'd like to place down", buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("glimmersHeroOn_")) {
            String pos = buttonID.split("_")[1];
            String unit = buttonID.split("_")[2];
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(pos), unit, activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    ident + " chose to duplicate a " + unit + " in "
                            + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player));
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("resolveWithNoEffect")) {
            String voteMessage = "Resolving agenda with no effect. Click the buttons for next steps.";
            String agendaCount = activeGame.getFactionsThatReactedToThis("agendaCount");
            int aCount = 0;
            if (agendaCount.isEmpty()) {
                aCount = 1;
            } else {
                aCount = Integer.parseInt(agendaCount) + 1;
            }
            Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda #" + aCount);
            Button proceedToStrategyPhase = Button.success("proceed_to_strategy",
                    "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            List<Button> resActionRow = List.of(flipNextAgenda, proceedToStrategyPhase);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("outcome_")) {
            // AgendaHelper.offerVoteAmounts(buttonID, event, activeGame, player, ident,
            // buttonLabel);
            if (activeGame.getLaws() != null && (activeGame.getLaws().containsKey("rep_govt")
                    || activeGame.getLaws().containsKey("absol_government"))) {
                player.resetSpentThings();
                player.addSpentThing("representative_1");
                if (activeGame.getLaws().containsKey("absol_government") && player.getPlanets().contains("mr")) {
                    player.addSpentThing("absolRexControlRepresentative_1");
                }
                String outcome = buttonID.substring(buttonID.indexOf("_") + 1);
                String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome);
                activeGame.setLatestOutcomeVotedFor(outcome);
                MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
                AgendaHelper.proceedToFinalizingVote(activeGame, player, event);
            } else {
                AgendaHelper.exhaustPlanetsForVotingVersion2(buttonID, event, activeGame, player);
            }
        } else if (buttonID.startsWith("votes_")) {
            AgendaHelper.exhaustPlanetsForVoting(buttonID, event, activeGame, player, ident, buttonLabel,
                    finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("dacxive_")) {
            String planet = buttonID.replace("dacxive_", "");
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planet)),
                    "infantry " + planet, activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(), ident + " placed 1 infantry on "
                    + Helper.getPlanetRepresentation(planet, activeGame) + " via the tech Dacxive Animators");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("autoresolve_")) {
            String result = buttonID.substring(buttonID.indexOf("_") + 1);
            if (result.contains("manual")) {
                if (result.contains("committee")) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdentOrColor(player,
                            activeGame)
                            + " has chosen to discard Committee Formation to choose the winner. Note that afters can be played before this occurs, and that confounding can still be played");
                    boolean success = activeGame.removeLaw(activeGame.getLaws().get("committee"));
                }
                String resMessage3 = "Please select the winner.";
                List<Button> deadlyActionRow3 = AgendaHelper.getAgendaButtons(null, activeGame, "agendaResolution");
                deadlyActionRow3.add(Button.danger("resolveWithNoEffect", "Resolve with no result"));
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), resMessage3, deadlyActionRow3);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("deleteButtons")) {
            deleteButtons(event, buttonID, buttonLabel, activeGame, player, actionsChannel, trueIdentity);
        } else if (buttonID.startsWith("reverse_")) {
            AgendaHelper.reverseRider(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("moveGlory_")) {
            ButtonHelperAgents.moveGlory(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("placeGlory_")) {
            ButtonHelperAgents.placeGlory(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("rider_")) {
            AgendaHelper.placeRider(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("startToScuttleAUnit_")) {
            ButtonHelperActionCards.resolveScuttleStart(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("startToLuckyShotAUnit_")) {
            ButtonHelperActionCards.resolveLuckyShotStart(player, activeGame, event);
        } else if (buttonID.startsWith("endScuttle_")) {
            ButtonHelperActionCards.resolveScuttleEnd(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("scuttleOn_")) {
            ButtonHelperActionCards.resolveScuttleRemoval(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("luckyShotOn_")) {
            ButtonHelperActionCards.resolveLuckyShotRemoval(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("scuttleIn_")) {
            ButtonHelperActionCards.resolveScuttleTileSelection(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("luckyShotIn_")) {
            ButtonHelperActionCards.resolveLuckyShotTileSelection(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("winnuHero_")) {
            ButtonHelperHeroes.resolveWinnuHeroSC(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("construction_")) {
            if (!player.getFollowedSCs().contains(4)) {
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 4, activeGame, event);
            }
            // player.addFollowedSC(4);
            ButtonHelper.addReaction(event, false, false, "", "");
            String unit = buttonID.replace("construction_", "");
            String message = trueIdentity
                    + " Click the name of the planet you wish to put your unit on for construction";
            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, unit, "place");
            if (!activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            }
        } else if (buttonID.startsWith("jrStructure_")) {
            String unit = buttonID.replace("jrStructure_", "");
            if (!"tg".equalsIgnoreCase(unit)) {
                String message = trueIdentity + " Click the name of the planet you wish to put your unit on";
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, unit,
                        "placeOneNDone_dontskip");
                if (!activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                }
            } else {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                        ident + " tgs increased by 1 (" + player.getTg() + "->" + (player.getTg() + 1) + ")");
                player.setTg(player.getTg() + 1);
                ButtonHelperAbilities.pillageCheck(player, activeGame);
                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
            }
            event.getMessage().delete().queue();// "resolveReverse_"
        } else if (buttonID.startsWith("resolveReverse_")) {
            ButtonHelperActionCards.resolveReverse(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("removeAllStructures_")) {
            event.getMessage().delete().queue();
            String planet = buttonID.split("_")[1];
            UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            plan.removeAllUnits(player.getColor());
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Removed all units on " + planet + " for " + player.getRepresentation());
            AddRemoveUnits.addPlanetToPlayArea(event, activeGame.getTileFromPlanet(planet), planet, activeGame);
        } else if (buttonID.startsWith("winnuStructure_")) {
            String unit = buttonID.replace("winnuStructure_", "").split("_")[0];
            String planet = buttonID.replace("winnuStructure_", "").split("_")[1];
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planet)),
                    unit + " " + planet, activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    ButtonHelper.getIdent(player) + " Placed a " + unit + " on "
                            + Helper.getPlanetRepresentation(planet, activeGame));

        } else if (buttonID.startsWith("produceOneUnitInTile_")) {
            buttonID = buttonID.replace("produceOneUnitInTile_", "");
            String type = buttonID.split("_")[1];
            String pos = buttonID.split("_")[0];
            List<Button> buttons;
            buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos), type,
                    "placeOneNDone_dontskip");
            String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("yinagent_")) {
            ButtonHelperAgents.yinAgent(buttonID, event, activeGame, player, ident, trueIdentity);
        } else if (buttonID.startsWith("resolveMaw")) {
            ButtonHelper.resolveMaw(activeGame, player, event);
        } else if (buttonID.startsWith("playerPref_")) {
            ButtonHelper.resolvePlayerPref(player, event, buttonID, activeGame);
        } else if (buttonID.startsWith("riskDirectHit_")) {
            ButtonHelper.resolveRiskDirectHit(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("setPersonalAutoPingInterval_")) {
            int interval = Integer.parseInt(buttonID.split("_")[1]);
            player.setPersonalPingInterval(interval);
            Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
            for (Game activeGame2 : mapList.values()) {
                for (Player player2 : activeGame2.getRealPlayers()) {
                    if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                        player2.setPersonalPingInterval(interval);
                        GameSaveLoadManager.saveMap(activeGame2);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set interval as " + interval + " hours");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("playerPrefDecision_")) {
            ButtonHelper.resolvePlayerPrefDecision(player, event, buttonID, activeGame);
        } else if (buttonID.startsWith("resolveCrownOfE")) {
            ButtonHelper.resolveCrownOfE(activeGame, player, event);
        } else if (buttonID.startsWith("yssarilAgentAsJr")) {
            ButtonHelperFactionSpecific.yssarilAgentAsJr(activeGame, player, event);
        } else if (buttonID.startsWith("sarMechStep1_")) {
            ButtonHelper.resolveSARMechStep1(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("sarMechStep2_")) {
            ButtonHelper.resolveSARMechStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("deployMykoSD_")) {
            ButtonHelperFactionSpecific.deployMykoSD(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("jrResolution_")) {
            String faction2 = buttonID.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction2);
            if (p2 != null) {
                Button sdButton = Button.success("jrStructure_sd", "Place A SD");
                sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                Button pdsButton = Button.success("jrStructure_pds", "Place a PDS");
                pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
                Button tgButton = Button.success("jrStructure_tg", "Gain a tg");
                List<Button> buttons = new ArrayList<>();
                buttons.add(sdButton);
                buttons.add(pdsButton);
                buttons.add(tgButton);
                String msg = p2.getRepresentation(true, true) + " Use buttons to decide what structure to build";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), msg,
                        buttons);
                event.getMessage().delete().queue();
            } // "colonialRedTarget_"
        } else if (buttonID.startsWith("colonialRedTarget_")) {
            AgendaHelper.resolveColonialRedTarget(activeGame, buttonID, event);
        } else if (buttonID.startsWith("ruins_")) {
            ButtonHelper.resolveWarForgeRuins(activeGame, buttonID, player, event);
        } else if (buttonID.startsWith("createGameChannels")) {
            CreateGameButton.decodeButtonMsg(event);
        } else if (buttonID.startsWith("yssarilHeroRejection_")) {
            String playerFaction = buttonID.replace("yssarilHeroRejection_", "");
            Player notYssaril = activeGame.getPlayerFromColorOrFaction(playerFaction);
            if (notYssaril != null) {
                String message = notYssaril.getRepresentation(true, true)
                        + " the player of the yssaril hero has rejected your offering and is forcing you to discard 3 random ACs. The ACs have been automatically discarded";
                MessageHelper.sendMessageToChannel(notYssaril.getCardsInfoThread(), message);
                new DiscardACRandom().discardRandomAC(event, activeGame, notYssaril, 3);
                event.getMessage().delete().queue();
            }
        } else if (buttonID.startsWith("yssarilHeroInitialOffering_")) {
            List<Button> acButtons = new ArrayList<>();
            buttonID = buttonID.replace("yssarilHeroInitialOffering_", "");
            String acID = buttonID.split("_")[0];
            String yssarilFaction = buttonID.split("_")[1];
            Player yssaril = activeGame.getPlayerFromColorOrFaction(yssarilFaction);
            if (yssaril != null) {
                String offerName = player.getFaction();
                if (activeGame.isFoWMode()) {
                    offerName = player.getColor();
                }
                event.getMessage().delete().queue();
                acButtons.add(Button.success("takeAC_" + acID + "_" + player.getFaction(), buttonLabel)
                        .withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                acButtons.add(Button.danger("yssarilHeroRejection_" + player.getFaction(),
                        "Reject " + buttonLabel + " and force them to discard of 3 random ACs"));
                String message = yssaril.getRepresentation(true, true) + " " + offerName
                        + " has offered you the action card " + buttonLabel
                        + " for your Yssaril Hero play. Use buttons to accept or reject it";
                MessageHelper.sendMessageToChannelWithButtons(yssaril.getCardsInfoThread(), message, acButtons);
                String acStringID = "";
                for (String acStrId : player.getActionCards().keySet()) {
                    if ((player.getActionCards().get(acStrId) + "").equalsIgnoreCase(acID)) {
                        acStringID = acStrId;
                    }
                }

                ActionCardModel ac = Mapper.getActionCard(acStringID);
                if (ac != null) {
                    MessageHelper.sendMessageToChannelWithEmbed(
                        yssaril.getCardsInfoThread(), "For your reference, the text of the AC offered reads as", ac.getRepresentationEmbed());
                    
                }

            }
        } else if (buttonID.startsWith("statusInfRevival_")) {
            ButtonHelper.placeInfantryFromRevival(activeGame, event, player, buttonID);
        } else if (buttonID.startsWith("genericReact")) {
            String message = activeGame.isFoWMode() ? "Turned down window" : null;
            ButtonHelper.addReaction(event, false, false, message, "");
        } else if (buttonID.startsWith("placeOneNDone_")) {
            ButtonHelperModifyUnits.placeUnitAndDeleteButton(buttonID, event, activeGame, player, ident, trueIdentity);
        } else if (buttonID.startsWith("mitoMechPlacement_")) {
            ButtonHelperAbilities.resolveMitosisMechPlacement(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("sendTradeHolder_")) {
            ButtonHelper.sendTradeHolderSomething(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("place_")) {
            ButtonHelperModifyUnits.genericPlaceUnit(buttonID, event, activeGame, player, ident, trueIdentity,
                    finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("yssarilcommander_")) {
            ButtonHelperCommanders.yssarilCommander(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("exploreFront_")) {
            String pos = buttonID.replace("exploreFront_", "");
            new ExpFrontier().expFront(event, activeGame.getTileByPosition(pos), activeGame, player);
            List<ActionRow> actionRow2 = new ArrayList<>();
            String exhaustedMessage = event.getMessage().getContentRaw();
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
            if (exhaustedMessage == null || "".equalsIgnoreCase(exhaustedMessage)) {
                exhaustedMessage = "Explore";
            }
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            } else {
                event.getMessage().delete().queue();
            }
        } else if (buttonID.startsWith("nekroStealTech_")) {
            String faction = buttonID.replace("nekroStealTech_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            List<String> potentialTech = new ArrayList<>();
            activeGame.setComponentAction(true);
            potentialTech = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, potentialTech,
                    activeGame);
            List<Button> buttons = ButtonHelperAbilities.getButtonsForPossibleTechForNekro(player, potentialTech,
                    activeGame);
            if (buttons.size() > 0 && p2 != null && !p2.getPromissoryNotesInPlayArea().contains("antivirus")) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        trueIdentity + " get enemy tech using the buttons", buttons);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        trueIdentity + " no tech available to gain (maybe other player has antivirus)");
            }
        } else if (buttonID.startsWith("mentakCommander_")) {
            String color = buttonID.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(color);
            if (p2 != null) {
                List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(activeGame, player, p2);
                String message = p2.getRepresentation(true, true)
                        + " You have been hit with mentak commander. Please select the PN you would like to send";
                MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Sent " + color + " the buttons for resolving mentak commander");
                ButtonHelper.deleteTheOneButton(event);
            }
        } else if (buttonID.startsWith("mahactStealCC_")) {
            String color = buttonID.replace("mahactStealCC_", "");
            if (!player.getMahactCC().contains(color)) {
                player.addMahactCC(color);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        ident + " added a " + color + " CC to their fleet pool");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        ident + " already had a " + color + " CC in their fleet pool");
            }
            if (player.getLeaderIDs().contains("mahactcommander") && !player.hasLeaderUnlocked("mahactcommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "mahact", event);
            }
        } else if (buttonID.startsWith("returnFFToSpace_")) {
            ButtonHelperFactionSpecific.returnFightersToSpace(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("freelancersBuild_")) {
            String planet = buttonID.replace("freelancersBuild_", "");
            List<Button> buttons;
            Tile tile = activeGame.getTile(AliasHandler.resolveTile(planet));
            if (tile == null) {
                tile = activeGame.getTileByPosition(planet);
            }
            buttons = Helper.getPlaceUnitButtons(event, player, activeGame, tile, "freelancers",
                    "placeOneNDone_dontskipfreelancers");
            String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("arboCommanderBuild_")) {
            String planet = buttonID.replace("arboCommanderBuild_", "");
            List<Button> buttons;
            Tile tile = activeGame.getTile(AliasHandler.resolveTile(planet));
            if (tile == null) {
                tile = activeGame.getTileByPosition(planet);
            }
            buttons = Helper.getPlaceUnitButtons(event, player, activeGame, tile, "arboCommander",
                    "placeOneNDone_dontskiparboCommander");
            String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tacticalActionBuild_")) {
            ButtonHelperTacticalAction.buildWithTacticalAction(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("getModifyTiles")) {
            List<Button> buttons = ButtonHelper.getTilesToModify(player, activeGame, event);
            String message = player.getRepresentation()
                    + " Use the buttons to select the tile in which you wish to modify units. ";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message,
                    buttons);
        } else if (buttonID.startsWith("genericModify_")) {
            String pos = buttonID.replace("genericModify_", "");
            Tile tile = activeGame.getTileByPosition(pos);
            ButtonHelper.offerBuildOrRemove(player, activeGame, event, tile);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("genericBuild_")) {
            String pos = buttonID.replace("genericBuild_", "");
            List<Button> buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                    activeGame.getTileByPosition(pos), "genericBuild", "place");
            String message = player.getRepresentation() + " Use the buttons to produce units. ";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("starforgeTile_")) {
            ButtonHelperAbilities.starforgeTile(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("starforge_")) {
            ButtonHelperAbilities.starforge(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("getSwapButtons_")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Swap",
                    ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, activeGame, true));
        } else if (buttonID.startsWith("planetAbilityExhaust_")) {
            String planet = buttonID.replace("planetAbilityExhaust_", "");
            new PlanetExhaustAbility().doAction(player, planet, activeGame);
            List<ActionRow> actionRow2 = new ArrayList<>();
            String exhaustedMessage = event.getMessage().getContentRaw();
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
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            } else {
                event.getMessage().delete().queue();
            }
        } else if (buttonID.startsWith("checksNBalancesPt2_")) {// "freeSystemsHeroPlanet_"
            new SCPick().resolvePt2ChecksNBalances(event, player, activeGame, buttonID);
        } else if (buttonID.startsWith("freeSystemsHeroPlanet_")) {// "freeSystemsHeroPlanet_"
            ButtonHelperHeroes.freeSystemsHeroPlanet(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("scPick_")) {
            Stats stats = new Stats();
            String num = buttonID.replace("scPick_", "");
            int scpick = Integer.parseInt(num);
            if (activeGame.getFactionsThatReactedToThis("Public Disgrace") != null
                    && activeGame.getFactionsThatReactedToThis("Public Disgrace").contains("_" + scpick)
                    && (activeGame.getFactionsThatReactedToThis("Public Disgrace Only").isEmpty() || activeGame
                            .getFactionsThatReactedToThis("Public Disgrace Only").contains(player.getFaction()))) {
                for (Player p2 : activeGame.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (activeGame.getFactionsThatReactedToThis("Public Disgrace").contains(p2.getFaction())
                            && p2.getActionCards().containsKey("disgrace")) {
                        PlayAC.playAC(event, activeGame, p2, "disgrace", activeGame.getMainGameChannel(),
                                event.getGuild());
                        activeGame.setCurrentReacts("Public Disgrace", "");
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation()
                                        + " you have been public disgraced because someone preset it to occur when the number "
                                        + scpick
                                        + " was chosen. If this is a mistake or the disgrace is sabod, feel free to pick the SC again. Otherwise, pick a different SC.");
                        return;
                    }
                }
            }

            if (activeGame.getLaws().containsKey("checks") || activeGame.getLaws().containsKey("absol_checks")) {
                new SCPick().secondHalfOfSCPickWhenChecksNBalances(event, player, activeGame, scpick);
            } else {
                boolean pickSuccessful = stats.secondHalfOfPickSC(event, activeGame, player, scpick);
                if (pickSuccessful) {
                    new SCPick().secondHalfOfSCPick(event, player, activeGame, scpick);
                    event.getMessage().delete().queue();
                }
            }

        } else if (buttonID.startsWith("milty_")) {

            // System.out.println("MILTY");
        } else if (buttonID.startsWith("ring_")) {
            List<Button> ringButtons = ButtonHelper.getTileInARing(player, activeGame, buttonID, event);
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
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("getACFrom_")) {
            String faction = buttonID.replace("getACFrom_", "");
            Player victim = activeGame.getPlayerFromColorOrFaction(faction);
            List<Button> buttons = ButtonHelperFactionSpecific.getButtonsToTakeSomeonesAC(activeGame, player, victim);
            ShowAllAC.showAll(victim, player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentation(true, true)
                            + " Select which AC you would like to steal",
                    buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("steal2tg_")) {
            new TrapReveal().steal2Tg(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("steal3comm_")) {
            new TrapReveal().steal3Comm(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("specialRex_")) {
            ButtonHelper.resolveSpecialRex(player, activeGame, buttonID, ident, event);
        } else if (buttonID.startsWith("doActivation_")) {
            String pos = buttonID.replace("doActivation_", "");
            ButtonHelper.resolveOnActivationEnemyAbilities(activeGame, activeGame.getTileByPosition(pos), player, false,
                    event);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("getTilesThisFarAway_")) {
            ButtonHelperTacticalAction.getTilesThisFarAway(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("ringTile_")) {
            ButtonHelperTacticalAction.selectActiveSystem(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("genericRemove_")) {
            String pos = buttonID.replace("genericRemove_", "");
            activeGame.resetCurrentMovedUnitsFrom1System();
            activeGame.resetCurrentMovedUnitsFrom1TacticalAction();
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame,
                    activeGame.getTileByPosition(pos), "Remove");
            activeGame.resetCurrentMovedUnitsFrom1System();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Chose to remove units from "
                    + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Use buttons to select the units you want to remove.", systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tacticalMoveFrom_")) {
            ButtonHelperTacticalAction.selectTileToMoveFrom(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("resolvePreassignment_")) {
            ButtonHelper.resolvePreAssignment(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("removePreset_")) {
            ButtonHelper.resolveRemovalOfPreAssignment(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("purge_Frags_")) {
            String typeNAmount = buttonID.replace("purge_Frags_", "");
            String type = typeNAmount.split("_")[0];
            int count = Integer.parseInt(typeNAmount.split("_")[1]);
            List<String> fragmentsToPurge = new ArrayList<>();
            List<String> playerFragments = player.getFragments();
            for (String fragid : playerFragments) {
                if (fragid.contains(type.toLowerCase())) {
                    fragmentsToPurge.add(fragid);
                }
            }
            while (fragmentsToPurge.size() > count) {
                fragmentsToPurge.remove(0);
            }

            for (String fragid : fragmentsToPurge) {
                player.removeFragment(fragid);
                activeGame.setNumberOfPurgedFragments(activeGame.getNumberOfPurgedFragments() + 1);
            }

            Player lanefirPlayer = activeGame.getPlayers().values().stream().filter(
                    p -> p.getLeaderIDs().contains("lanefircommander") && !p.hasLeaderUnlocked("lanefircommander"))
                    .findFirst().orElse(null);

            if (lanefirPlayer != null) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "lanefir", event);
            }

            String message = player.getRepresentation() + " purged fragments: "
                    + fragmentsToPurge;
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);

            if (player.hasTech("dslaner")) {
                player.setAtsCount(player.getAtsCount() + 1);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getRepresentation() + " Put 1 commodity on ATS Armaments");
            }
        } else if (buttonID.startsWith("unitTactical")) {
            ButtonHelperTacticalAction.movingUnitsInTacticalAction(buttonID, event, activeGame, player, buttonLabel);
        } else if (buttonID.startsWith("naaluHeroInitiation")) {
            ButtonHelperHeroes.resolveNaaluHeroInitiation(player, activeGame, event);
        } else if (buttonID.startsWith("naaluHeroSend")) {
            ButtonHelperHeroes.resolveNaaluHeroSend(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("landUnits_")) {
            ButtonHelperModifyUnits.landingUnits(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("reparationsStep2_")) {
            ButtonHelperActionCards.resolveReparationsStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("seizeArtifactStep2_")) {
            ButtonHelperActionCards.resolveSeizeArtifactStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("diplomaticPressureStep2_")) {
            ButtonHelperActionCards.resolveDiplomaticPressureStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("decoyOperationStep2_")) {
            ButtonHelperActionCards.resolveDecoyOperationStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("resolveDecoyOperationStep1_")) {
            ButtonHelperActionCards.resolveDecoyOperationStep1(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("seizeArtifactStep3_")) {
            ButtonHelperActionCards.resolveSeizeArtifactStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("reparationsStep3_")) {
            ButtonHelperActionCards.resolveReparationsStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("uprisingStep2_")) {
            ButtonHelperActionCards.resolveUprisingStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("bestowTitleStep1_")) {
            ButtonHelper.resolveBestowTitleStep1(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("bestowTitleStep2_")) {
            ButtonHelper.resolveBestowTitleStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep2_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep3Exhaust_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep3Exhaust(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep4Exhaust_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep4Exhaust(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep3Ready_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep3Ready(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("khraskHeroStep4Ready_")) {
            ButtonHelperHeroes.resolveKhraskHeroStep4Ready(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("drawRelicAtPosition_")) {
            DrawRelic.resolveDrawRelicAtPosition(player, event, activeGame, buttonID);
        } else if (buttonID.startsWith("setTrapStep2_")) {
            ButtonHelperAbilities.setTrapStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("removeTrapStep2_")) {
            ButtonHelperAbilities.removeTrapStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("revealTrapStep2_")) {
            ButtonHelperAbilities.revealTrapStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("setTrapStep3_")) {
            ButtonHelperAbilities.setTrapStep3(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("setTrapStep4_")) {
            ButtonHelperAbilities.setTrapStep4(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("lanefirATS_")) {
            ButtonHelperFactionSpecific.resolveLanefirATS(player, event, buttonID);
        } else if (buttonID.startsWith("rohdhnaIndustrious_")) {
            ButtonHelperFactionSpecific.resolveRohDhnaIndustrious(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("rohdhnaRecycle_")) {
            ButtonHelperFactionSpecific.resolveRohDhnaRecycle(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("stymiePlayerStep1_")) {
            ButtonHelperFactionSpecific.resolveStymiePlayerStep1(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("stymiePlayerStep2_")) {
            ButtonHelperFactionSpecific.resolveStymiePlayerStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("prismStep2_")) {
            new PlanetExhaustAbility().resolvePrismStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("prismStep3_")) {
            new PlanetExhaustAbility().resolvePrismStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("showDeck_")) {
            ButtonHelper.resolveDeckChoice(activeGame, event, buttonID, player);
        } else if (buttonID.startsWith("setForThalnos_")) {
            ButtonHelper.resolveSetForThalnos(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("rollThalnos_")) {
            ButtonHelper.resolveRollForThalnos(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("startThalnos_")) {
            ButtonHelper.resolveThalnosStart(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("showTextOfDeck_")) {
            ButtonHelper.resolveShowFullTextDeckChoice(activeGame, event, buttonID, player);
        } else if (buttonID.startsWith("assRepsStep2_")) {
            ButtonHelperActionCards.resolveAssRepsStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("setupStep1_")) {
            ButtonHelper.resolveSetupStep1(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("setupStep2_")) {
            ButtonHelper.resolveSetupStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("setupStep3_")) {
            ButtonHelper.resolveSetupStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("setupStep4_")) {
            ButtonHelper.resolveSetupStep4And5(activeGame, event, buttonID);
        } else if (buttonID.startsWith("setupStep5_")) {
            ButtonHelper.resolveSetupStep4And5(activeGame, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep2_")) {
            ButtonHelperActionCards.resolveSignalJammingStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep3_")) {
            ButtonHelperActionCards.resolveSignalJammingStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("edynAgendaStuffStep2_")) {
            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("edynAgendaStuffStep3_")) {
            ButtonHelperFactionSpecific.resolveEdynAgendaStuffStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep4_")) {
            ButtonHelperActionCards.resolveSignalJammingStep4(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("reactorMeltdownStep2_")) {
            ButtonHelperActionCards.resolveReactorMeltdownStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("declareUse_")) {
            String msg = ident + " is using " + buttonID.split("_")[1];
            if (msg.contains("Vaylerian")) {
                msg = msg + " to add +2 capacity to a ship with capacity";
            }
            if (msg.contains("Tnelis")) {
                msg = msg
                        + " to apply 1 hit against their **non-fighter** ships in the system and give **1** of their ships a +1 boost. This ability can only be used once per activation.";
                String pos = buttonID.split("_")[2];
                List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame,
                        activeGame.getTileByPosition(pos));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        trueIdentity + " Use buttons to assign 1 hit", buttons);
                activeGame.setCurrentReacts("tnelisCommanderTracker", player.getFaction());
            }
            if (msg.contains("Ghemina")) {
                msg = msg + " to gain 1tg after winning the space combat";
                player.setTg(player.getTg() + 1);
                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                ButtonHelperAbilities.pillageCheck(player, activeGame);
            }
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("spyStep2_")) {
            ButtonHelperActionCards.resolveSpyStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("insubStep2_")) {
            ButtonHelperActionCards.resolveInsubStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("absStep2_")) {
            ButtonHelperActionCards.resolveABSStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("ghostShipStep2_")) {
            ButtonHelperActionCards.resolveGhostShipStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("tacticalBombardmentStep2_")) {
            ButtonHelperActionCards.resolveTacticalBombardmentStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("probeStep2_")) {
            ButtonHelperActionCards.resolveProbeStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("salvageStep2_")) {
            ButtonHelperActionCards.resolveSalvageStep2(player, activeGame, event, buttonID);// "salvageOps_"
        } else if (buttonID.startsWith("salvageOps_")) {
            ButtonHelperFactionSpecific.resolveSalvageOps(player, event, buttonID, activeGame);
        } else if (buttonID.startsWith("psStep2_")) {
            ButtonHelperActionCards.resolvePSStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("plagueStep2_")) {
            ButtonHelperActionCards.resolvePlagueStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("crippleStep2_")) {
            ButtonHelperActionCards.resolveCrippleStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("infiltrateStep2_")) {
            ButtonHelperActionCards.resolveInfiltrateStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("spyStep3_")) {
            ButtonHelperActionCards.resolveSpyStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("plagueStep3_")) {
            ButtonHelperActionCards.resolvePlagueStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("crippleStep3_")) {
            ButtonHelperActionCards.resolveCrippleStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("infiltrateStep3_")) {
            ButtonHelperActionCards.resolveInfiltrateStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("reactorMeltdownStep3_")) {
            ButtonHelperActionCards.resolveReactorMeltdownStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("uprisingStep3_")) {
            ButtonHelperActionCards.resolveUprisingStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("axisHeroStep3_")) {
            ButtonHelperHeroes.resolveAxisHeroStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("axisHeroStep2_")) {
            ButtonHelperHeroes.resolveAxisHeroStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("purgeKortaliHero_")) {
            Leader playerLeader = player.unsafeGetLeader("kortalihero");
            StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                    .append(Helper.getLeaderFullRepresentation(playerLeader));
            boolean purged = player.removeLeader(playerLeader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        message + " - Leader " + "kortalihero" + " has been purged");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Leader was not purged - something went wrong");
            }
            ButtonHelperHeroes.offerStealRelicButtons(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("purgeCeldauriHero_")) {
            ButtonHelperHeroes.purgeCeldauriHero(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("asnStep2_")) {
            ButtonHelperFactionSpecific.resolveASNStep2(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("unstableStep2_")) {// "titansConstructionMechDeployStep2_"
            ButtonHelperActionCards.resolveUnstableStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("titansConstructionMechDeployStep2_")) {
            ButtonHelperFactionSpecific.handleTitansConstructionMechDeployStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("titansConstructionMechDeployStep1")) {
            ButtonHelperFactionSpecific.handleTitansConstructionMechDeployStep1(activeGame, player);
        } else if (buttonID.startsWith("unstableStep3_")) {
            ButtonHelperActionCards.resolveUnstableStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("spaceUnits_")) {
            ButtonHelperModifyUnits.spaceLandedUnits(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("reinforcements_cc_placement_")) {
            // String playerRep = player.getRepresentation();
            String planet = buttonID.replace("reinforcements_cc_placement_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeGame.getTile(tileID);
            if (tile == null) {
                tile = activeGame.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            if (Mapper.isValidColor(color)) {
                AddCC.addCC(event, color, tile);
            }
            String message = ident + " Placed A CC From Reinforcements In The "
                    + Helper.getPlanetRepresentation(planet, activeGame) + " system";
            ButtonHelper.sendMessageToRightStratThread(player, activeGame, message, "construction");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("placeHolderOfConInSystem_")) {
            // String playerRep = player.getRepresentation();
            String planet = buttonID.replace("placeHolderOfConInSystem_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeGame.getTile(tileID);
            if (tile == null) {
                tile = activeGame.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            for (Player p2 : activeGame.getRealPlayers()) {
                if (p2.getSCs().contains(4)) {
                    color = p2.getColor();
                }
            }

            if (Mapper.isValidColor(color)) {
                AddCC.addCC(event, color, tile);
            }
            // String message = playerRep + " Placed A " + StringUtils.capitalize(color) + "
            // CC In The " + Helper.getPlanetRepresentation(planet, activeGame) + " system
            // due to use of Mahact agent";
            ButtonHelper.sendMessageToRightStratThread(player, activeGame, messageID, "construction");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("greyfire_")) {
            ButtonHelperFactionSpecific.resolveGreyfire(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("transactWith_")) {
            String faction = buttonID.replace("transactWith_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            List<Button> buttons;
            buttons = ButtonHelper.getStuffToTransButtons(activeGame, player, p2);
            String message = player.getRepresentation()
                    + " Use the buttons to select what you want to transact";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            ButtonHelper.checkTransactionLegality(activeGame, player, p2);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("transact_")) {
            ButtonHelper.resolveSpecificTransButtons(activeGame, player, buttonID, event);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("play_after_")) {
            String riderName = buttonID.replace("play_after_", "");
            ButtonHelper.addReaction(event, true, true, "Playing " + riderName, riderName + " Played");
            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeGame, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
            if ("Keleres Rider".equalsIgnoreCase(riderName) || "Edyn Rider".equalsIgnoreCase(riderName)
                    || "Kyro Rider".equalsIgnoreCase(riderName)) {
                if ("Keleres Rider".equalsIgnoreCase(riderName)) {
                    String pnKey = "fin";
                    for (String pn : player.getPromissoryNotes().keySet()) {
                        if (pn.contains("rider")) {
                            pnKey = pn;
                        }
                    }
                    if ("fin".equalsIgnoreCase(pnKey)) {
                        MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have a Keleres Rider");
                        return;
                    }
                    ButtonHelper.resolvePNPlay(pnKey, player, activeGame, event);
                }
                if ("Edyn Rider".equalsIgnoreCase(riderName)) {
                    String pnKey = "fin";
                    for (String pn : player.getPromissoryNotes().keySet()) {
                        if (pn.contains("dspnedyn")) {
                            pnKey = pn;
                        }
                    }
                    if ("fin".equalsIgnoreCase(pnKey)) {
                        MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have a Edyn Rider");
                        return;
                    }
                    ButtonHelper.resolvePNPlay(pnKey, player, activeGame, event);
                }
                if ("Kyro Rider".equalsIgnoreCase(riderName)) {
                    String pnKey = "fin";
                    for (String pn : player.getPromissoryNotes().keySet()) {
                        if (pn.contains("dspnkyro")) {
                            pnKey = pn;
                        }
                    }
                    if ("fin".equalsIgnoreCase(pnKey)) {
                        MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have a Kyro Rider");
                        return;
                    }
                    ButtonHelper.resolvePNPlay(pnKey, player, activeGame, event);
                }
            } else {
                if (riderName.contains("Unity Algorithm")) {
                    player.exhaustTech("dsedyng");
                }
                if(riderName.equalsIgnoreCase("conspirators")){
                    activeGame.setCurrentReacts("conspiratorsFaction", player.getFaction());
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), activeGame.getPing() + " The conspirators ability has been used, which means the player will vote after the speaker. This ability can be used once per agenda phase");
                    if(!activeGame.isFoWMode()){
                        ListVoteCount.turnOrder(event, activeGame, activeGame.getMainGameChannel());
                    }
                }else{
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your rider target",
                            activeGame, player, riderButtons);
                    if ("Keleres Xxcha Hero".equalsIgnoreCase(riderName)) {
                        Leader playerLeader = player.getLeader("keleresheroodlynn").orElse(null);
                        if (playerLeader != null) {
                            StringBuilder message = new StringBuilder(player.getRepresentation());
                            message.append(" played ");
                            message.append(Helper.getLeaderFullRepresentation(playerLeader));
                            boolean purged = player.removeLeader(playerLeader);
                            if (purged) {
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                        message + " - Leader Oodlynn has been purged");
                            } else {
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                        "Leader was not purged - something went wrong");
                            }
                        }
                    }
                }
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                        "Please indicate no afters again.", activeGame, afterButtons, "after");
            }
            // "dspnedyn"
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("componentActionRes_")) {
            ButtonHelper.resolvePressedCompButton(activeGame, player, event, buttonID);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("addIonStorm_")) {
            String pos = buttonID.substring(buttonID.lastIndexOf("_") + 1);
            Tile tile = activeGame.getTileByPosition(pos);
            if (buttonID.contains("alpha")) {
                String tokenFilename = Mapper.getTokenID("ionalpha");
                tile.addToken(tokenFilename, Constants.SPACE);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Added ionstorm alpha to " + tile.getRepresentation());

            } else {
                String tokenFilename = Mapper.getTokenID("ionbeta");
                tile.addToken(tokenFilename, Constants.SPACE);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Added ionstorm beta to " + tile.getRepresentation());
            }

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("terraformPlanet_")) {
            ButtonHelperFactionSpecific.terraformPlanet(buttonID, event, activeGame);
        } else if (buttonID.startsWith("veldyrAttach_")) {
            ButtonHelperFactionSpecific.resolveBranchOffice(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("nanoforgePlanet_")) {
            String planet = buttonID.replace("nanoforgePlanet_", "");
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            planetReal.addToken("attachment_nanoforge.png");
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Attached nanoforge to " + Helper.getPlanetRepresentation(planet, activeGame));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolvePNPlay_")) {
            String pnID = buttonID.replace("resolvePNPlay_", "");

            if (pnID.contains("ra_")) {
                String tech = AliasHandler.resolveTech(pnID.replace("ra_", ""));
                TechnologyModel techModel = Mapper.getTech(tech);
                pnID = pnID.replace("_" + tech, "");
                String message = ident + " Acquired The Tech " + techModel.getRepresentation(false)
                        + " via Research Agreement";
                player.addTech(tech);
                ButtonHelperCommanders.resolveNekroCommanderCheck(player, tech, activeGame);
                if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "jolnar", event);
                }
                if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "nekro", event);
                }
                if (player.getLeaderIDs().contains("mirvedacommander")
                        && !player.hasLeaderUnlocked("mirvedacommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "mirveda", event);
                }
                if (player.getLeaderIDs().contains("dihmohncommander")
                        && !player.hasLeaderUnlocked("dihmohncommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "dihmohn", event);
                }
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
            }
            ButtonHelper.resolvePNPlay(pnID, player, activeGame, event);
            if (!"bmfNotHand".equalsIgnoreCase(pnID)) {
                event.getMessage().delete().queue();
            }

            var posssibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.PROMISSORY_NOTES, pnID,
                    player.getNumberTurns());
            if (posssibleCombatMod != null) {
                player.addNewTempCombatMod(posssibleCombatMod);
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        "Combat modifier will be applied next time you push the combat roll button.");
            }

        } else if (buttonID.startsWith("send_")) {
            ButtonHelper.resolveSpecificTransButtonPress(activeGame, player, buttonID, event);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("replacePDSWithFS_")) {
            ButtonHelperFactionSpecific.replacePDSWithFS(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("putSleeperOnPlanet_")) {
            ButtonHelperAbilities.putSleeperOn(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("frankenDraftAction;")) {
            FrankenDraftHelper.resolveFrankenDraftAction(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("presetEdynAgentStep1")) {
            ButtonHelperAgents.presetEdynAgentStep1(activeGame, player);
        } else if (buttonID.startsWith("presetEdynAgentStep2_")) {
            ButtonHelperAgents.presetEdynAgentStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("presetEdynAgentStep3_")) {
            ButtonHelperAgents.presetEdynAgentStep3(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("removeSleeperFromPlanet_")) {
            ButtonHelperAbilities.removeSleeper(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("replaceSleeperWith_")) {
            ButtonHelperAbilities.replaceSleeperWith(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("relicSwapStep2")) {
            ButtonHelperHeroes.resolveRelicSwapStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("relicSwapStep1")) {
            ButtonHelperHeroes.resolveRelicSwapStep1(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("topAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
            new PutAgendaTop().putTop(event, Integer.parseInt(agendaNumID), activeGame);
            AgendaModel agenda = Mapper.getAgenda(activeGame.lookAtTopAgenda(0));
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Put " + agenda.getName() + " on the top of the agenda deck.");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("primaryOfWarfare")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "warfare");
            MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        } else if (buttonID.startsWith("mahactCommander")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "mahactCommander");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.",
                    buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("useTA_")) {
            String ta = buttonID.replace("useTA_", "") + "_ta";
            ButtonHelper.resolvePNPlay(ta, player, activeGame, event);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("combatDroneConvert_")) {
            ButtonHelperModifyUnits.resolvingCombatDrones(event, activeGame, player, ident, buttonID);
        } else if (buttonID.startsWith("cloakedFleets_")) {// kolleccMechCapture_
            ButtonHelperModifyUnits.resolveCloakedFleets(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("kolleccMechCapture_")) {// kolleccMechCapture_
            ButtonHelperModifyUnits.resolveKolleccMechCapture(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("refreshLandingButtons")) {
            List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
            event.getMessage().editMessage(event.getMessage().getContentRaw())
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        } else if (buttonID.startsWith("resolveMirvedaCommander_")) {
            ButtonHelperModifyUnits.resolvingMirvedaCommander(event, activeGame, player, ident, buttonID);
        } else if (buttonID.startsWith("removeCCFromBoard_")) {
            ButtonHelper.resolveRemovingYourCC(player, activeGame, event, buttonID);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("bottomAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
            new PutAgendaBottom().putBottom(event, Integer.parseInt(agendaNumID), activeGame);
            AgendaModel agenda = Mapper.getAgenda(activeGame.lookAtBottomAgenda(0));
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Put " + agenda.getName() + " on the bottom of the agenda deck.");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("discardAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
            String agendaID = activeGame.revealAgenda(false);
            AgendaModel agendaDetails = Mapper.getAgenda(agendaID);
            String agendaName = agendaDetails.getName();
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    ButtonHelper.getIdentOrColor(player, activeGame) + "discarded " + agendaName + " using Edyn Agent");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("agendaResolution_")) {
            AgendaHelper.resolveAgenda(activeGame, buttonID, event, actionsChannel);
        } else if (buttonID.startsWith("rollIxthian")) {
            if (activeGame.getSpeaker().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
                AgendaHelper.rollIxthian(activeGame, true);
            } else {
                Button ixthianButton = Button.success("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact")
                        .withEmoji(Emoji.fromFormatted(Emojis.Mecatol));
                String msg = "The speaker should roll for Ixthain Artifact. Click this button to roll anyway!";
                MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg, ixthianButton);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("applytempcombatmod__" + "tech" + "__")) {
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
        } else if (buttonID.startsWith("applytempcombatmod__" + Constants.AC + "__")) {
            String acAlias = buttonID.substring(buttonID.lastIndexOf("__") + 2);
            TemporaryCombatModifierModel combatModAC = CombatTempModHelper.GetPossibleTempModifier(Constants.AC,
                    acAlias,
                    player.getNumberTurns());
            if (combatModAC != null) {
                player.addNewTempCombatMod(combatModAC);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Combat modifier will be applied next time you push the combat roll button.");
            }
        } else {
            switch (buttonID) {
                // AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
                case Constants.PO_NO_SCORING -> {
                    activeGame.setCurrentReacts("factionsThatScored",
                            activeGame.getFactionsThatReactedToThis("factionsThatScored") + "_" + player.getFaction());
                    String message = player.getRepresentation()
                            + " - no Public Objective scored.";
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = activeGame.isFoWMode() ? "No public objective scored" : null;
                    ButtonHelper.addReaction(event, false, false, reply, "");
                }
                case Constants.REFRESH_RELIC_INFO -> RelicInfo.sendRelicInfo(activeGame, player, event);
                case Constants.REFRESH_TECH_INFO -> TechInfo.sendTechInfo(activeGame, player, event);
                case Constants.REFRESH_UNIT_INFO -> UnitInfo.sendUnitInfo(activeGame, player, event);
                case Constants.REFRESH_LEADER_INFO -> LeaderInfo.sendLeadersInfo(activeGame, player, event);
                case Constants.REFRESH_PLANET_INFO -> PlanetInfo.sendPlanetInfo(player);
                case "warfareBuild" -> {
                    List<Button> buttons;
                    Tile tile = activeGame.getTile(AliasHandler.resolveTile(player.getFaction()));
                    if (player.hasAbility("mobile_command") && ButtonHelper
                            .getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship).size() > 0) {
                        tile = ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Flagship)
                                .get(0);
                    }
                    if (tile == null) {
                        tile = ButtonHelper.getTileOfPlanetWithNoTrait(player, activeGame);
                    }
                    if (tile == null) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                "Could not find a HS, sorry bro");
                    }
                    buttons = Helper.getPlaceUnitButtons(event, player, activeGame, tile, "warfare", "place");
                    int val = Helper.getProductionValue(player, activeGame, tile, true);
                    String message = player.getRepresentation()
                            + " Use the buttons to produce. Reminder that when following warfare, you can only use 1 dock in your home system. "
                            + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame) + "\n"
                            + "The bot believes you have " + val + " PRODUCTION value in this system";
                    if (val > 0 && activeGame.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
                        message = message
                                + ". You also have cabal commander which allows you to produce 2 ff/inf that dont count towards production limit";
                    }
                    if (val > 0 && ButtonHelper.isPlayerElected(activeGame, player, "prophecy")) {
                        message = message
                                + ". Reminder that you have prophecy of Ixth and should produce 2 fighters if you want to keep it. Its removal is not automated";
                    }
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Produce Units",
                                buttons);
                    } else {
                        MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message);
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Produce Units",
                                buttons);
                    }
                }
                case "getKeleresTechOptions" ->
                    ButtonHelperFactionSpecific.offerKeleresStartingTech(player, activeGame, event);
                case "transaction" -> {
                    List<Button> buttons;
                    buttons = ButtonHelper.getPlayersToTransact(activeGame, player);
                    String message = player.getRepresentation()
                            + " Use the buttons to select which player you wish to transact with";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                }
                case "combatDrones" -> ButtonHelperModifyUnits.offerCombatDroneButtons(event, activeGame, player);
                case "offerMirvedaCommander" ->
                    ButtonHelperModifyUnits.offerMirvedaCommanderButtons(event, activeGame, player);
                case "acquireAFreeTech" -> { // Buttons.GET_A_FREE_TECH
                    List<Button> buttons = new ArrayList<>();

                    Button propulsionTech = Button
                            .primary(finsFactionCheckerPrefix + "getAllTechOfType_propulsion_noPay", "Get a Blue Tech");
                    propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
                    buttons.add(propulsionTech);

                    Button bioticTech = Button.success(finsFactionCheckerPrefix + "getAllTechOfType_biotic_noPay",
                            "Get a Green Tech");
                    bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
                    buttons.add(bioticTech);

                    Button cyberneticTech = Button.secondary(
                            finsFactionCheckerPrefix + "getAllTechOfType_cybernetic_noPay", "Get a Yellow Tech");
                    cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                    buttons.add(cyberneticTech);

                    Button warfareTech = Button.danger(finsFactionCheckerPrefix + "getAllTechOfType_warfare_noPay",
                            "Get a Red Tech");
                    warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                    buttons.add(warfareTech);

                    Button unitupgradesTech = Button.secondary(
                            finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade_noPay", "Get A Unit Upgrade Tech");
                    unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Emojis.UnitUpgradeTech));
                    buttons.add(unitupgradesTech);

                    String message = player.getRepresentation() + " What type of tech would you want?";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    event.getMessage().delete().queue();
                }
                case "acquireATech" -> { // Buttons.GET_A_TECH
                    List<Button> buttons = new ArrayList<>();

                    Button propulsionTech = Button.primary(finsFactionCheckerPrefix + "getAllTechOfType_propulsion",
                            "Get a Blue Tech");
                    propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
                    buttons.add(propulsionTech);

                    Button bioticTech = Button.success(finsFactionCheckerPrefix + "getAllTechOfType_biotic",
                            "Get a Green Tech");
                    bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
                    buttons.add(bioticTech);

                    Button cyberneticTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic",
                            "Get a Yellow Tech");
                    cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                    buttons.add(cyberneticTech);

                    Button warfareTech = Button.danger(finsFactionCheckerPrefix + "getAllTechOfType_warfare",
                            "Get a Red Tech");
                    warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                    buttons.add(warfareTech);

                    Button unitupgradesTech = Button.secondary(
                            finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade", "Get A Unit Upgrade Tech");
                    unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Emojis.UnitUpgradeTech));
                    buttons.add(unitupgradesTech);

                    String message = player.getRepresentation() + " What type of tech would you want?";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);

                }
                case Constants.SO_NO_SCORING -> {
                    String message = player.getRepresentation()
                            + " - no Secret Objective scored.";

                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
                }
                // AFTER AN ACTION CARD HAS BEEN PLAYED
                case "no_sabotage" -> {
                    String message = activeGame.isFoWMode() ? "No sabotage" : null;
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "titansCommanderUsage" ->
                    ButtonHelperCommanders.titansCommanderUsage(buttonID, event, activeGame, player, ident);
                case "ghotiATG" -> ButtonHelperAgents.ghotiAgentForTg(buttonID, event, activeGame, player);
                case "ghotiAProd" -> ButtonHelperAgents.ghotiAgentForProduction(buttonID, event, activeGame, player);
                case "passForRound" -> {
                    player.setPassed(true);
                    if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "olradincommander")) {
                        ButtonHelperCommanders.olradinCommanderStep1(player, activeGame);
                    }
                    
                    String text = player.getRepresentation() + " PASSED";
                    MessageHelper.sendMessageToChannel(event.getChannel(), text);
                    if(player.hasTech("absol_aida")){
                        String msg = player.getRepresentation()+" since you have absol AIDEV, you can research 1 Unit Upgrade here for 6 influence";
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), msg);
                        if (!player.hasAbility("propagation")) {
                            activeGame.setComponentAction(true);
                            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                                    player.getRepresentation(true, true) + " you can use the button to get your tech",
                                    List.of(Buttons.GET_A_TECH));
                        } else {
                            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                            String message2 = player.getRepresentation() + "! Your current CCs are " + player.getCCRepresentation()
                                    + ". Use buttons to gain CCs";
                            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                            activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                        }
                    }
                    TurnEnd.pingNextPlayer(event, activeGame, player, true);
                    event.getMessage().delete().queue();
                    ButtonHelper.updateMap(activeGame, event, "End of Turn (PASS) " + player.getTurnCount() + ", Round "
                            + activeGame.getRound() + " for " + ButtonHelper.getIdent(player));
                }
                case "proceedToVoting" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Decided to skip waiting for afters and proceed to voting.");
                    try {
                        AgendaHelper.startTheVoting(activeGame);
                    } catch (Exception e) {
                        BotLogger.log(event, "Could not start the voting", e);
                    }

                    // event.getMessage().delete().queue();
                }
                case "forceACertainScoringOrder" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), activeGame.getPing()+ 
                            "vPlayers will be forced to score in order. Players will not be prevented from declaring they dont score, and are in fact encouraged to do so without delay if that is the case. This forced scoring order also does not yet affect SOs, it only restrains POs");
                    activeGame.setCurrentReacts("forcedScoringOrder", "true");
                    event.getMessage().delete().queue();
                }
                case "proceedToFinalizingVote" -> {
                    AgendaHelper.proceedToFinalizingVote(activeGame, player, event);
                }
                case "drawAgenda_2" -> {
                    DrawAgenda.drawAgenda(event, 2, activeGame, player);
                    event.getMessage().delete().queue();
                }
                case "nekroFollowTech" -> {
                    Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                    Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                    Button exhaust = Button.danger("nekroTechExhaust", "Exhaust Planets");
                    Button doneGainingCC = Button.danger("deleteButtons_technology", "Done Gaining CCs");
                    activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                    String message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                            + ". Use buttons to gain CCs";
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, doneGainingCC);
                    List<Button> buttons2 = List.of(exhaust);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Exhaust using this",
                                buttons2);
                    } else {

                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Exhaust using this",
                                buttons2);
                    }
                }
                case "diploRefresh2" -> {
                    if (!player.getFollowedSCs().contains(2)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 2, activeGame, event);
                    }
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to ready";

                    List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, activeGame);
                    Button doneRefreshing = Button.danger("deleteButtons_diplomacy", "Done Readying Planets");
                    buttons.add(doneRefreshing);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                    if (player.hasAbility("peace_accords")) {
                        List<Button> buttons2 = ButtonHelperAbilities.getXxchaPeaceAccordsButtons(activeGame, player,
                                event, finsFactionCheckerPrefix);
                        if (!buttons2.isEmpty()) {
                            MessageHelper.sendMessageToChannelWithButtons(
                                    ButtonHelper.getCorrectChannel(player, activeGame),
                                    trueIdentity + " use buttons to resolve peace accords", buttons2);
                        }
                    }
                }
                case "getOmenDice" -> ButtonHelperAbilities.offerOmenDiceButtons(activeGame, player);
                case "leadershipExhaust" -> {
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "inf");
                    Button doneExhausting = Button.danger("deleteButtons_leadership", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "nekroTechExhaust" -> {
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "res");
                    Button doneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                    event.getMessage().delete().queue();
                }
                case "deployTyrant" -> {
                    String message = "Use buttons to put a tyrant with your ships";
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message,
                            Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "tyrantslament",
                                    "placeOneNDone_skipbuild"));
                    ButtonHelper.deleteTheOneButton(event);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            player.getFactionEmoji() + " is deploying the Tyrants Lament");
                    player.addOwnedUnitByID("tyrantslament");
                }
                case "startStrategyPhase" -> {
                    StartPhase.startPhase(event, activeGame, "strategy");
                    event.getMessage().delete().queue();
                }
                case "endOfTurnAbilities" -> {
                    String msg = "Use buttons to do an end of turn ability";
                    List<Button> buttons = ButtonHelper.getEndOfTurnAbilities(player, activeGame);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                }
                case "redistributeCCButtons" -> { // Buttons.REDISTRIBUTE_CCs
                    String message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                            + ". Use buttons to gain CCs";
                    activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                    Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc",
                            "Gain 1 Tactic CC");
                    Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc",
                            "Gain 1 Strategy CC");
                    Button loseTactic = Button.danger(finsFactionCheckerPrefix + "decrease_tactic_cc",
                            "Lose 1 Tactic CC");
                    Button loseFleet = Button.danger(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet CC");
                    Button loseStrat = Button.danger(finsFactionCheckerPrefix + "decrease_strategy_cc",
                            "Lose 1 Strategy CC");

                    Button doneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons",
                            "Done Redistributing CCs");
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat,
                            doneGainingCC);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }

                    if (!activeGame.isFoWMode() && "statusHomework".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                        ButtonHelper.addReaction(event, false, false, "", "");
                    }

                    if ("statusHomework".equalsIgnoreCase(activeGame.getCurrentPhase())) {
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
                            if (properGain > 2) {
                                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                                        player.getRepresentation(true, true) + " heads up, bot thinks you should gain "
                                                + properGain + " cc now due to: " + reasons);
                            }

                        }
                    }
                }
                case "leadershipGenerateCCButtons" -> {
                    if (!player.getFollowedSCs().contains(1)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 1, activeGame, event);
                    }
                    player.addFollowedSC(1);
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                            + ". Use buttons to gain CCs";
                    activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                    Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc",
                            "Gain 1 Tactic CC");
                    Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc",
                            "Gain 1 Strategy CC");
                    // Button exhaust = Button.danger(finsFactionCheckerPrefix +
                    // "leadershipExhaust", "Exhaust Planets");
                    Button doneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons_leadership",
                            "Done Gaining CCs");
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, doneGainingCC);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "spyNetYssarilChooses" ->
                    ButtonHelperFactionSpecific.resolveSpyNetYssarilChooses(player, activeGame, event);
                case "spyNetPlayerChooses" ->
                    ButtonHelperFactionSpecific.resolveSpyNetPlayerChooses(player, activeGame, event);
                case "diploSystem" -> {
                    String message = trueIdentity + " Click the name of the planet who's system you wish to diplo";
                    List<Button> buttons = Helper.getPlanetSystemDiploButtons(event, player, activeGame, false, null);
                    ButtonHelper.sendMessageToRightStratThread(player, activeGame, message, "diplomacy", buttons);
                }
                case "sc_ac_draw" -> {
                    boolean used = addUsedSCPlayer(messageID + "ac", activeGame, player, event, "");
                    if (used) {
                        break;
                    }
                    boolean hasSchemingAbility = player.hasAbility("scheming");
                    String message = hasSchemingAbility
                            ? "Drew 3 Action Cards (Scheming) - please discard an Action Card from your hand"
                            : "Drew 2 Action cards";
                    int count = hasSchemingAbility ? 3 : 2;
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, count);
                        message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";

                    } else {
                        for (int i = 0; i < count; i++) {
                            activeGame.drawActionCard(player.getUserID());
                        }
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        ButtonHelper.checkACLimit(activeGame, event, player);
                    }

                    ButtonHelper.addReaction(event, false, false, message, "");
                    if (hasSchemingAbility) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                                player.getRepresentation(true, true) + " use buttons to discard",
                                ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander")
                            && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }
                    if (player.hasAbility("contagion")) {
                        List<Button> buttons2 = ButtonHelperAbilities.getKyroContagionButtons(activeGame, player,
                                event, finsFactionCheckerPrefix);
                        if (!buttons2.isEmpty()) {
                            MessageHelper.sendMessageToChannelWithButtons(
                                    player.getCardsInfoThread(),
                                    trueIdentity + " use buttons to resolve contagion planet #1", buttons2);
                            MessageHelper.sendMessageToChannelWithButtons(
                                        player.getCardsInfoThread(),
                                        trueIdentity + " use buttons to resolve contagion planet #2", buttons2);
                        }
                    }
                }
                case "resolveDistinguished" -> ButtonHelperActionCards.resolveDistinguished(player, activeGame, event);
                case "resolveMykoMech" -> ButtonHelperFactionSpecific.resolveMykoMech(player, activeGame);
                case "offerNecrophage" -> ButtonHelperFactionSpecific.offerNekrophageButtons(player, event);
                case "resolveMykoCommander" -> ButtonHelperCommanders.mykoCommanderUsage(player, activeGame, event);
                case "checkForAllACAssignments" -> ButtonHelperActionCards.checkForAllAssignmentACs(activeGame, player);
                case "sc_draw_so" -> {
                    boolean used = addUsedSCPlayer(messageID + "so", activeGame, player, event,
                            " Drew a " + Emojis.SecretObjective);
                    if (used) {
                        break;
                    }
                    if (!player.getFollowedSCs().contains(8)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 8, activeGame, event);
                    }

                    Player imperialHolder = Helper.getPlayerWithThisSC(activeGame, 8);
                    String key = "factionsThatAreNotDiscardingSOs";
                    String key2 = "queueToDrawSOs";
                    String key3 = "potentialBlockers";
                    String message = "Drew Secret Objective";
                    for (Player player2 : Helper.getSpeakerOrderFromThisPlayer(imperialHolder, activeGame)) {
                        if (player2 == player) {
                            activeGame.drawSecretObjective(player.getUserID());
                            if (player.hasAbility("plausible_deniability")) {
                                activeGame.drawSecretObjective(player.getUserID());
                                message = message + ". Drew a second SO due to plausible deniability";
                            }
                            SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
                            break;
                        }
                        if (activeGame.getFactionsThatReactedToThis(key3).contains(player2.getFaction() + "*")) {
                            message = "Wants to draw an SO but has people ahead of them in speaker order who need to resolve first. They have been queued and will automatically draw an SO when everyone ahead of them is clear. ";
                            if (!activeGame.isFoWMode()) {
                                message = message + player2.getRepresentation(true, true)
                                        + " is the one the game is currently waiting on";
                            }
                            activeGame.setCurrentReacts(key2,
                                    activeGame.getFactionsThatReactedToThis(key2) + player.getFaction() + "*");
                            break;
                        }
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "non_sc_draw_so" -> {
                    String message = "Drew Secret Objective";
                    activeGame.drawSecretObjective(player.getUserID());
                    if (player.hasAbility("plausible_deniability")) {
                        activeGame.drawSecretObjective(player.getUserID());
                        message = message + ". Drew a second SO due to plausible deniability";
                    }
                    SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "edynCommanderSODraw" -> {
                    if (!activeGame.playerHasLeaderUnlockedOrAlliance(player, "edyncommander")) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                ButtonHelper.getIdent(player) + " you dont have Edyn Commander silly");
                    }
                    String message = "Drew Secret Objective instead of scoring PO, using Edyn Commander";
                    activeGame.drawSecretObjective(player.getUserID());
                    if (player.hasAbility("plausible_deniability")) {
                        activeGame.drawSecretObjective(player.getUserID());
                        message = message + ". Drew a second SO due to plausible deniability";
                    }
                    SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "sc_trade_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "");
                    if (used) {
                        break;
                    }
                    if (player.getStrategicCC() > 0) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
                    }
                    String message = deductCC(player, event);
                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, activeGame, event);
                    }
                    player.addFollowedSC(5);
                    player.setCommodities(player.getCommoditiesTotal());
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                            && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                    if (player.hasAbility("military_industrial_complex")
                            && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                                ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                    ButtonHelper.addReaction(event, false, false, message, "");
                    ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "flip_agenda" -> {
                    new RevealAgenda().revealAgenda(event, false, activeGame, event.getChannel());
                    event.getMessage().delete().queue();

                }
                case "hack_election" -> {
                    activeGame.setHackElectionStatus(false);
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Set Order Back To Normal.");
                    event.getMessage().delete().queue();
                }
                case "proceed_to_strategy" -> {
                    Map<String, Player> players = activeGame.getPlayers();
                    for (Player player_ : players.values()) {
                        player_.cleanExhaustedPlanets(false);
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda cleanup run!");
                    ButtonHelper.startStrategyPhase(event, activeGame);
                    event.getMessage().delete().queue();

                }
                case "sc_follow_trade" -> {
                    boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "");
                    if (used) {
                        break;
                    }
                    if (player.getStrategicCC() > 0) {
                        ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
                    }
                    String message = deductCC(player, event);

                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, activeGame, event);
                    }
                    player.addFollowedSC(5);
                    player.setCommodities(player.getCommoditiesTotal());
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                            && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                    if (player.hasAbility("military_industrial_complex")
                            && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                                ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                    ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
                    ButtonHelper.addReaction(event, false, false, message, "");
                    ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "sc_follow_leadership" -> {
                    String message = player.getRepresentation() + " following.";
                    if (!player.getFollowedSCs().contains(1)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 1, activeGame, event);
                    }
                    player.addFollowedSC(1);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "sc_leadership_follow" -> {
                    String message = player.getRepresentation() + " following.";
                    if (!player.getFollowedSCs().contains(1)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 1, activeGame, event);
                    }
                    player.addFollowedSC(1);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "sc_refresh" -> {
                    boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "Replenish");
                    if (used) {
                        break;
                    }
                    player.setCommodities(player.getCommoditiesTotal());
                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, activeGame, event);
                    }
                    player.addFollowedSC(5);
                    ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                    ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
                    if (player.hasAbility("military_industrial_complex")
                            && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                                ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                            && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                }
                case "sc_refresh_and_wash" -> {
                    if (player.hasAbility("military_industrial_complex")) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player
                                .getRepresentation(true, true)
                                + " since you cannot send players commodities due to your faction ability, washing here seems likely an error. Nothing has been processed as a result. Try a different route if this correction is wrong");
                        return;
                    }

                    if (!player.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation(true,
                                true)
                                + " since you do not currently hold your TA, washing here seems likely an error and will mess with the TA resolution. Nothing has been processed as a result. Try a different route of washing your comms if this correction is wrong");
                        return;
                    }

                    boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "Replenish and Wash");
                    if (used) {
                        break;
                    }
                    int washedCommsPower = player.getCommoditiesTotal() + player.getTg();
                    int commoditiesTotal = player.getCommoditiesTotal();
                    int tg = player.getTg();
                    player.setTg(tg + commoditiesTotal);
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                    player.setCommodities(0);

                    for (Player p2 : activeGame.getRealPlayers()) {
                        if (p2.getSCs().contains(5) && p2.getCommodities() > 0) {
                            if (p2.getCommodities() > washedCommsPower) {
                                p2.setTg(p2.getTg() + washedCommsPower);
                                p2.setCommodities(p2.getCommodities() - washedCommsPower);
                                ButtonHelperAbilities.pillageCheck(p2, activeGame);
                                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                                        p2.getRepresentation(true, true) + " " + washedCommsPower
                                                + " of your commodities got washed in the process of washing "
                                                + ButtonHelper.getIdentOrColor(player, activeGame));
                                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, player, p2,
                                        player.getCommoditiesTotal());
                                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p2, player,
                                        p2.getCommoditiesTotal());
                            } else {
                                p2.setTg(p2.getTg() + p2.getCommodities());
                                p2.setCommodities(0);
                                ButtonHelperAbilities.pillageCheck(p2, activeGame);
                                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                                        p2.getRepresentation(true, true)
                                                + " your commodities got washed in the process of washing "
                                                + ButtonHelper.getIdentOrColor(player, activeGame));
                                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, player, p2,
                                        player.getCommoditiesTotal());
                                ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, p2, player,
                                        p2.getCommoditiesTotal());
                            }
                        }
                        if (p2.getSCs().contains(5)) {
                            ButtonHelper.checkTransactionLegality(activeGame, player, p2);
                        }
                    }
                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, activeGame, event);
                    }
                    player.addFollowedSC(5);
                    ButtonHelper.addReaction(event, false, false, "Replenishing and washing", "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                    ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
                }
                case "sc_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);

                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
                    int scnum = 1;
                    boolean setstatus = true;
                    try {
                        scnum = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
                    } catch (NumberFormatException e) {
                        try {
                            scnum = Integer.parseInt(lastchar);
                        } catch (NumberFormatException e2) {
                            setstatus = false;
                        }
                    }
                    if (setstatus) {
                        if (!player.getFollowedSCs().contains(scnum)) {
                            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scnum, activeGame, event);
                        }
                        player.addFollowedSC(scnum);
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");

                }
                case "trade_primary" -> {
                    if (!player.getSCs().contains(5)) {
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "Trade Primary");
                    if (used) {
                        break;
                    }
                    int tg = player.getTg();
                    player.setTg(tg + 3);
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 3);
                    if (player.getLeaderIDs().contains("hacancommander")
                            && !player.hasLeaderUnlocked("hacancommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
                    }

                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                    player.setCommodities(player.getCommoditiesTotal());
                    ButtonHelper.addReaction(event, false, false,
                            " gained 3" + Emojis.getTGorNomadCoinEmoji(activeGame) + " and replenished commodities ("
                                    + player.getCommodities() + Emojis.comm + ")",
                            "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                    ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
                    if (player.hasAbility("military_industrial_complex")
                            && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                                ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                            && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                }
                case "score_imperial" -> {
                    if (player == null || activeGame == null) {
                        break;
                    }
                    if (!player.getPlanetsAllianceMode().contains("mr")) {
                        MessageHelper.sendMessageToChannel(privateChannel,
                                "Only the player who controls Rex can score the Imperial point");
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID + "score_imperial", activeGame, player, event,
                            " scored Imperial");
                    if (used) {
                        break;
                    }
                    ButtonHelperFactionSpecific.KeleresIIHQCCGainCheck(player, activeGame);
                    ScorePublic.scorePO(event, privateChannel, activeGame, player, 0);
                }
                // AFTER AN AGENDA HAS BEEN REVEALED
                case "play_when" -> {
                    clearAllReactions(event);
                    ButtonHelper.addReaction(event, true, true, "Playing When", "When Played");
                    List<Button> whenButtons = AgendaHelper.getWhenButtons(activeGame);
                    Date newTime = new Date();
                    activeGame.setLastActivePlayerPing(newTime);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(actionsChannel,
                            "Please indicate no whens again.", activeGame, whenButtons, "when");
                    List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                            "Please indicate no afters again.", activeGame, afterButtons, "after");
                    // addPersistentReactions(event, activeMap, "when");
                    event.getMessage().delete().queue();
                }
                case "no_when" -> {
                    String message = activeGame.isFoWMode() ? "No whens" : null;
                    if (activeGame.getFactionsThatReactedToThis("noWhenThisAgenda") == null) {
                        activeGame.setCurrentReacts("noWhenThisAgenda", "");
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "offerPlayerPref" -> {
                    ButtonHelper.offerPlayerPreferences(player, event);
                }
                case "no_after" -> {
                    String message = activeGame.isFoWMode() ? "No afters" : null;
                    if (activeGame.getFactionsThatReactedToThis("noAfterThisAgenda") == null) {
                        activeGame.setCurrentReacts("noAfterThisAgenda", "");
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_after_persistent" -> {
                    String message = activeGame.isFoWMode() ? "No afters (locked in)" : null;
                    activeGame.addPlayersWhoHitPersistentNoAfter(player.getFaction());
                    if (activeGame.getFactionsThatReactedToThis("noAfterThisAgenda") == null) {
                        activeGame.setCurrentReacts("noAfterThisAgenda", "");
                    }
                    // activeGame.getFactionsThatReactedToThis("noAfterThisAgenda").contains(player.getFaction())
                    if (!"".equalsIgnoreCase(activeGame.getFactionsThatReactedToThis("noAfterThisAgenda"))) {
                        activeGame.setCurrentReacts("noAfterThisAgenda",
                                activeGame.getFactionsThatReactedToThis("noAfterThisAgenda") + "_"
                                        + player.getFaction());
                    } else {
                        activeGame.setCurrentReacts("noAfterThisAgenda", player.getFaction());
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_when_persistent" -> {
                    String message = activeGame.isFoWMode() ? "No whens (locked in)" : null;
                    activeGame.addPlayersWhoHitPersistentNoWhen(player.getFaction());
                    if (activeGame.getFactionsThatReactedToThis("noWhenThisAgenda") == null) {
                        activeGame.setCurrentReacts("noWhenThisAgenda", "");
                    }
                    if (!"".equalsIgnoreCase(activeGame.getFactionsThatReactedToThis("noWhenThisAgenda"))) {
                        activeGame.setCurrentReacts("noWhenThisAgenda",
                                activeGame.getFactionsThatReactedToThis("noWhenThisAgenda") + "_"
                                        + player.getFaction());
                    } else {
                        activeGame.setCurrentReacts("noWhenThisAgenda", player.getFaction());
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "deal2SOToAll" -> {
                    new DealSOToAll().dealSOToAll(event, 2, activeGame);
                    event.getMessage().delete().queue();
                }
                case "startOfGameObjReveal" -> {
                    Player speaker = null;
                    if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
                        speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
                    }
                    for (Player p : activeGame.getRealPlayers()) {
                        if (p.getSecrets().size() > 1 && !activeGame.isExtraSecretMode()) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                    "Please ensure everyone has discarded secrets before hitting this button. ");
                            return;
                        }
                    }
                    if (speaker == null) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Please assign speaker before hitting this button.");
                        ButtonHelper.offerSpeakerButtons(activeGame, player);
                        return;
                    }
                    RevealStage1.revealTwoStage1(event, activeGame.getMainGameChannel());
                    ButtonHelper.startStrategyPhase(event, activeGame);
                    ButtonHelper.offerSetAutoPassOnSaboButtons(activeGame, null);
                    event.getMessage().delete().queue();
                }
                case "gain_2_comms" -> {
                    String message;
                    if (player.getCommodities() + 2 > player.getCommoditiesTotal()) {
                        player.setCommodities(player.getCommoditiesTotal());
                        message = "Gained Commodities to Max";
                    } else {
                        player.setCommodities(player.getCommodities() + 2);
                        message = "Gained 2 Commodities";

                    }
                    if (player.hasAbility("military_industrial_complex")
                            && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                                ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                            && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                    event.getMessage().delete().queue();
                    if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }

                }
                case "startYinSpinner" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            ButtonHelper.getIdent(player) + " Chose to Use Yin Spinner");
                    List<Button> buttons = new ArrayList<>(
                            Helper.getPlanetPlaceUnitButtons(player, activeGame, "2gf", "placeOneNDone_skipbuild"));
                    String message = "Use buttons to drop 2 infantry on a planet. Technically you can also drop 2 infantry with your ships, but this aint supported yet via button. ";
                    ButtonHelper.deleteTheOneButton(event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                }
                case "convert_2_comms" -> {
                    String message;
                    if (player.getCommodities() > 1) {
                        player.setCommodities(player.getCommodities() - 2);
                        player.setTg(player.getTg() + 2);
                        message = "Converted 2 Commodities to 2 tg";

                    } else {
                        player.setTg(player.getTg() + player.getCommodities());
                        player.setCommodities(0);
                        message = "Converted all remaining commodies (less than 2) into tg";
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");

                    event.getMessage().delete().queue();
                    if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }
                }
                case "convert_1_comms" -> {
                    String message;
                    if (player.getCommodities() > 0) {
                        player.setCommodities(player.getCommodities() - 1);
                        player.setTg(player.getTg() + 1);
                        message = "Converted 1 Commodities to 1 tg (" + (player.getTg() - 1) + "->" + player.getTg()
                                + ")";
                    } else {
                        player.setTg(player.getTg() + player.getCommodities());
                        player.setCommodities(0);
                        message = "Had no commidities to convert into tg";
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + message);

                }
                case "gain_1_comms" -> {
                    String message;
                    if (player.getCommodities() + 1 > player.getCommoditiesTotal()) {
                        player.setCommodities(player.getCommoditiesTotal());
                        message = "Gained No Commodities (at max already)";
                    } else {
                        player.setCommodities(player.getCommodities() + 1);
                        message = " Gained 1 Commodity (" + (player.getCommodities() - 1) + "->"
                                + player.getCommodities() + ")";
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                    event.getMessage().delete().queue();
                    if (player.hasAbility("military_industrial_complex")
                            && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame),
                                player.getRepresentation(true, true) + " you have the opportunity to buy axis orders",
                                ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                            && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                    if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }
                }
                case "startPlayerSetup" -> ButtonHelper.resolveSetupStep0(player, activeGame, event);
                case "gain_1_comm_from_MahactInf" -> {
                    String message;
                    if (player.getCommodities() + 1 > player.getCommoditiesTotal()) {
                        player.setCommodities(player.getCommoditiesTotal());
                        message = " Gained No Commodities (at max already)";
                    } else {
                        player.setCommodities(player.getCommodities() + 1);
                        message = " Gained 1 Commodity (" + (player.getCommodities() - 1) + "->"
                                + player.getCommodities() + ")";
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + message);
                    if (player.getLeaderIDs().contains("mykomentoricommander")
                            && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                }
                case "comm_for_AC" -> {
                    boolean hasSchemingAbility = player.hasAbility("scheming");
                    int count2 = hasSchemingAbility ? 2 : 1;
                    String commOrTg;
                    if (player.getCommodities() > 0) {
                        commOrTg = "commodity";
                        player.setCommodities(player.getCommodities() - 1);

                    } else if (player.getTg() > 0) {
                        player.setTg(player.getTg() - 1);
                        commOrTg = "trade good";
                    } else {
                        ButtonHelper.addReaction(event, false, false, "Didn't have any comms/tg to spend, no AC drawn",
                                "");
                        break;
                    }
                    String message = hasSchemingAbility
                            ? "Spent 1 " + commOrTg + " to draw " + count2
                                    + " Action Card (Scheming) - please discard an Action Card from your hand"
                            : "Spent 1 " + commOrTg + " to draw " + count2 + " AC";
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, count2);
                        message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
                    } else {
                        for (int i = 0; i < count2; i++) {
                            activeGame.drawActionCard(player.getUserID());
                        }
                        ButtonHelper.checkACLimit(activeGame, event, player);
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                    }

                    if (player.getLeaderIDs().contains("yssarilcommander")
                            && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }

                    if (hasSchemingAbility) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                                player.getRepresentation(true, true) + " use buttons to discard",
                                ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                    }

                    ButtonHelper.addReaction(event, false, false, message, "");
                    event.getMessage().delete().queue();
                    if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }
                }
                case "comm_for_mech" -> {
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1);
                    String commOrTg;
                    if (player.getCommodities() > 0) {
                        player.setCommodities(player.getCommodities() - 1);
                        commOrTg = "commodity";
                    } else if (player.getTg() > 0) {
                        player.setTg(player.getTg() - 1);
                        commOrTg = "tg";
                    } else {
                        ButtonHelper.addReaction(event, false, false,
                                "Didn't have any comms/tg to spend, no mech placed", "");
                        break;
                    }
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName, activeGame);
                    ButtonHelper.addReaction(event, false, false,
                            "Spent 1 " + commOrTg + " for a mech on " + planetName, "");
                    event.getMessage().delete().queue();
                    if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(actionsChannel,
                                pF + " Spent 1 " + commOrTg + " for a mech on " + planetName);
                    }
                }
                case "increase_strategy_cc" -> {
                    player.setStrategicCC(player.getStrategicCC() + 1);
                    String originalCCs = activeGame
                            .getFactionsThatReactedToThis("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "yssarilMinisterOfPolicy" -> {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getIdent(player) + " is drawing Minister of Policy AC(s)");
                    String message;
                    if (player.hasAbility("scheming")) {
                        activeGame.drawActionCard(player.getUserID());
                        activeGame.drawActionCard(player.getUserID());
                        message = ButtonHelper.getIdent(player) + " Drew 2 AC With Scheming. Please Discard An AC";
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                                player.getRepresentation(true, true) + " use buttons to discard",
                                ACInfo.getDiscardActionCardButtons(activeGame, player, false));

                    } else if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 1);
                        message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
                    } else {
                        activeGame.drawActionCard(player.getUserID());
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        message = ButtonHelper.getIdent(player) + " Drew 1 AC";
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander")
                            && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }

                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
                    ButtonHelper.checkACLimit(activeGame, event, player);
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "resetProducedThings" -> {
                    Helper.resetProducedUnits(player, activeGame, event);
                    event.getMessage().editMessage(Helper.buildProducedUnitsMessage(player, activeGame)).queue();
                }
                case "exhauste6g0network" -> {
                    player.addExhaustedRelic("e6-g0_network");
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getIdent(player) + " Chose to exhaust e6-g0_network");
                    String message;
                    if (player.hasAbility("scheming")) {
                        activeGame.drawActionCard(player.getUserID());
                        activeGame.drawActionCard(player.getUserID());
                        message = ButtonHelper.getIdent(player) + " Drew 2 AC With Scheming. Please Discard An AC";
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                                player.getRepresentation(true, true) + " use buttons to discard",
                                ACInfo.getDiscardActionCardButtons(activeGame, player, false));

                    } else if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 1);
                        message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
                    } else {
                        activeGame.drawActionCard(player.getUserID());
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        message = ButtonHelper.getIdent(player) + " Drew 1 AC";
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander")
                            && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }

                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
                    ButtonHelper.checkACLimit(activeGame, event, player);
                    ButtonHelper.deleteTheOneButton(event);

                }
                case "increase_tactic_cc" -> {

                    player.setTacticalCC(player.getTacticalCC() + 1);
                    String originalCCs = activeGame
                            .getFactionsThatReactedToThis("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "increase_fleet_cc" -> {
                    player.setFleetCC(player.getFleetCC() + 1);
                    String originalCCs = activeGame
                            .getFactionsThatReactedToThis("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_strategy_cc" -> {
                    player.setStrategicCC(player.getStrategicCC() - 1);
                    String originalCCs = activeGame
                            .getFactionsThatReactedToThis("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_tactic_cc" -> {
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    String originalCCs = activeGame
                            .getFactionsThatReactedToThis("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_fleet_cc" -> {
                    player.setFleetCC(player.getFleetCC() - 1);
                    String originalCCs = activeGame
                            .getFactionsThatReactedToThis("originalCCsFor" + player.getFaction());
                    int netGain = ButtonHelper.checkNetGain(player, originalCCs);
                    String editedMessage = player.getRepresentation() + " CCs have gone from " + originalCCs + " -> "
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    event.getMessage().editMessage(editedMessage).queue();
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "gain_1_tg" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1);
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + ButtonHelper.mechOrInfCheck(planetName, activeGame, player);
                        failed = message.contains("Please try again.");
                    }
                    if (!failed) {
                        message = message + "Gained 1 tg (" + player.getTg() + "->" + (player.getTg() + 1) + ").";
                        player.setTg(player.getTg() + 1);
                        ButtonHelperAbilities.pillageCheck(player, activeGame);
                        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                    if (!failed) {
                        event.getMessage().delete().queue();
                        if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                            String pF = player.getFactionEmoji();
                            MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                        }
                    }

                }
                case "mallice_2_tg" -> {
                    String playerRep = player.getFactionEmoji();
                    String message = playerRep + " exhausted Mallice ability and gained 2 tg (" + player.getTg() + "->"
                            + (player.getTg() + 2) + ").";
                    player.setTg(player.getTg() + 2);
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                    ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 2);
                    if (player.getLeaderIDs().contains("hacancommander")
                            && !player.hasLeaderUnlocked("hacancommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
                    }
                    if (!activeGame.isFoWMode() && event.getMessageChannel() != activeGame.getMainGameChannel()) {
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message);
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    event.getMessage().delete().queue();
                }
                case "mallice_convert_comm" -> {

                    String playerRep = player.getFactionEmoji();

                    String message = playerRep + " exhausted Mallice ability and converted comms to tg (TGs: "
                            + player.getTg() + "->" + (player.getTg() + player.getCommodities()) + ").";
                    player.setTg(player.getTg() + player.getCommodities());
                    player.setCommodities(0);
                    if (!activeGame.isFoWMode() && event.getMessageChannel() != activeGame.getMainGameChannel()) {
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), message);
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    event.getMessage().delete().queue();
                }
                case "decline_explore" -> {
                    ButtonHelper.addReaction(event, false, false, "Declined Explore", "");
                    event.getMessage().delete().queue();
                    if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " declined explore");
                    }
                }
                case "temporaryPingDisable" -> {
                    activeGame.setTemporaryPingDisable(true);
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Disabled autopings for this turn");
                    event.getMessage().delete().queue();
                }
                case "riseOfAMessiah" -> {
                    new RiseOfMessiah().doRise(player, event, activeGame);
                    event.getMessage().delete().queue();
                }
                case "fighterConscription" -> {
                    new FighterConscription().doFfCon(event, player, activeGame);
                    event.getMessage().delete().queue();
                }
                case "shuffleExplores" -> {
                    activeGame.shuffleExplores();
                    event.getMessage().delete().queue();
                }
                case "miningInitiative" -> ButtonHelperActionCards.miningInitiative(player, activeGame, event);
                case "forwardSupplyBase" ->
                    ButtonHelperActionCards.resolveForwardSupplyBaseStep1(player, activeGame, event, buttonID);
                case "economicInitiative" -> ButtonHelperActionCards.economicInitiative(player, activeGame, event);
                case "getRepealLawButtons" -> MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                        "Use buttons to select Law to repeal",
                        ButtonHelperActionCards.getRepealLawButtons(activeGame, player));
                case "resolveCounterStroke" -> ButtonHelperActionCards.resolveCounterStroke(activeGame, player, event);
                case "getDivertFundingButtons" -> {
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                            "Use buttons to select tech to return",
                            ButtonHelperActionCards.getDivertFundingLoseTechOptions(player, activeGame));
                    event.getMessage().delete().queue();
                }
                case "focusedResearch" ->
                    ButtonHelperActionCards.resolveFocusedResearch(activeGame, player, buttonID, event);
                case "resolveReparationsStep1" ->
                    ButtonHelperActionCards.resolveReparationsStep1(player, activeGame, event, buttonID);
                case "resolveSeizeArtifactStep1" ->
                    ButtonHelperActionCards.resolveSeizeArtifactStep1(player, activeGame, event, buttonID);
                case "resolveDiplomaticPressureStep1" ->
                    ButtonHelperActionCards.resolveDiplomaticPressureStep1(player, activeGame, event, buttonID);
                case "resolveImpersonation" ->
                    ButtonHelperActionCards.resolveImpersonation(player, activeGame, event, buttonID);
                case "resolveUprisingStep1" ->
                    ButtonHelperActionCards.resolveUprisingStep1(player, activeGame, event, buttonID);
                case "setTrapStep1" -> ButtonHelperAbilities.setTrapStep1(activeGame, player);
                case "revealTrapStep1" -> ButtonHelperAbilities.revealTrapStep1(activeGame, player);
                case "removeTrapStep1" -> ButtonHelperAbilities.removeTrapStep1(activeGame, player);
                case "offerDeckButtons" -> ButtonHelper.offerDeckButtons(activeGame, event);
                case "resolveAssRepsStep1" ->
                    ButtonHelperActionCards.resolveAssRepsStep1(player, activeGame, event, buttonID);
                case "resolveSignalJammingStep1" ->
                    ButtonHelperActionCards.resolveSignalJammingStep1(player, activeGame, event, buttonID);
                case "resolvePlagueStep1" ->
                    ButtonHelperActionCards.resolvePlagueStep1(player, activeGame, event, buttonID);
                case "resolveCrippleDefensesStep1" ->
                    ButtonHelperActionCards.resolveCrippleDefensesStep1(player, activeGame, event, buttonID);
                case "resolveInfiltrateStep1" ->
                    ButtonHelperActionCards.resolveInfiltrateStep1(player, activeGame, event, buttonID);
                case "resolveReactorMeltdownStep1" ->
                    ButtonHelperActionCards.resolveReactorMeltdownStep1(player, activeGame, event, buttonID);
                case "resolveSpyStep1" -> ButtonHelperActionCards.resolveSpyStep1(player, activeGame, event, buttonID);
                case "resolveUnexpected" ->
                    ButtonHelperActionCards.resolveUnexpectedAction(player, activeGame, event, buttonID);
                case "resolveFrontline" ->
                    ButtonHelperActionCards.resolveFrontlineDeployment(player, activeGame, event, buttonID);
                case "resolveInsubStep1" ->
                    ButtonHelperActionCards.resolveInsubStep1(player, activeGame, event, buttonID);
                case "resolveUnstableStep1" ->
                    ButtonHelperActionCards.resolveUnstableStep1(player, activeGame, event, buttonID);
                case "resolveABSStep1" -> ButtonHelperActionCards.resolveABSStep1(player, activeGame, event, buttonID);
                case "resolveWarEffort" -> ButtonHelperActionCards.resolveWarEffort(activeGame, player, event);
                case "resolveInsiderInformation" ->
                    ButtonHelperActionCards.resolveInsiderInformation(player, activeGame, event);
                case "resolveEmergencyMeeting" ->
                    ButtonHelperActionCards.resolveEmergencyMeeting(player, activeGame, event);
                case "resolveSalvageStep1" ->
                    ButtonHelperActionCards.resolveSalvageStep1(player, activeGame, event, buttonID);
                case "resolveGhostShipStep1" ->
                    ButtonHelperActionCards.resolveGhostShipStep1(player, activeGame, event, buttonID);
                case "resolveTacticalBombardmentStep1" ->
                    ButtonHelperActionCards.resolveTacticalBombardmentStep1(player, activeGame, event, buttonID);
                case "resolveProbeStep1" ->
                    ButtonHelperActionCards.resolveProbeStep1(player, activeGame, event, buttonID);
                case "resolvePSStep1" -> ButtonHelperActionCards.resolvePSStep1(player, activeGame, event, buttonID);
                case "resolveRally" -> ButtonHelperActionCards.resolveRally(activeGame, player, event);
                case "resolveHarness" -> ButtonHelperActionCards.resolveHarnessEnergy(activeGame, player, event);
                case "resolveSummit" -> ButtonHelperActionCards.resolveSummit(activeGame, player, event);
                case "resolveRefitTroops" -> ButtonHelperActionCards.resolveRefitTroops(player, activeGame, event,
                        buttonID, finsFactionCheckerPrefix);
                case "industrialInitiative" -> ButtonHelperActionCards.industrialInitiative(player, activeGame, event);
                case "confirm_cc" -> {
                    if (player.getMahactCC().size() > 0) {
                        ButtonHelper.addReaction(event, true, false,
                                "Confirmed CCs: " + player.getTacticalCC() + "/" + player.getFleetCC() + "(+"
                                        + player.getMahactCC().size() + ")/" + player.getStrategicCC(),
                                "");
                    } else {
                        ButtonHelper.addReaction(event, true, false, "Confirmed CCs: " + player.getTacticalCC() + "/"
                                + player.getFleetCC() + "/" + player.getStrategicCC(), "");
                    }
                }
                case "draw_1_AC" -> {
                    String message = "";
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 1);
                        message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
                    } else {
                        activeGame.drawActionCard(player.getUserID());
                        if (player.getLeaderIDs().contains("yssarilcommander")
                                && !player.hasLeaderUnlocked("yssarilcommander")) {
                            ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                        }
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        message = "Drew 1 AC";
                    }
                    ButtonHelper.addReaction(event, true, false, message, "");
                    ButtonHelper.checkACLimit(activeGame, event, player);
                }
                case "drawStatusACs" -> ButtonHelper.drawStatusACs(activeGame, player, event);
                case "draw_1_ACDelete" -> {
                    String message = "";
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 1);
                        message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
                    } else {
                        activeGame.drawActionCard(player.getUserID());
                        if (player.getLeaderIDs().contains("yssarilcommander")
                                && !player.hasLeaderUnlocked("yssarilcommander")) {
                            ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                        }
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        message = "Drew 1 AC";
                    }
                    ButtonHelper.addReaction(event, true, false, message, "");
                    event.getMessage().delete().queue();
                    ButtonHelper.checkACLimit(activeGame, event, player);
                }

                case "draw_2_ACDelete" -> {
                    String message = "";
                    if (player.hasAbility("autonetic_memory")) {
                        ButtonHelperAbilities.autoneticMemoryStep1(activeGame, player, 2);
                        message = ButtonHelper.getIdent(player) + " Triggered Autonetic Memory Option";
                    } else {
                        activeGame.drawActionCard(player.getUserID());
                        activeGame.drawActionCard(player.getUserID());
                        if (player.getLeaderIDs().contains("yssarilcommander")
                                && !player.hasLeaderUnlocked("yssarilcommander")) {
                            ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                        }
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        message = "Drew 2 AC With Scheming. Please Discard An AC";
                    }
                    ButtonHelper.addReaction(event, true, false, message, "");
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                            player.getRepresentation(true, true) + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(activeGame, player, false));

                    event.getMessage().delete().queue();
                    ButtonHelper.checkACLimit(activeGame, event, player);
                }
                case "pass_on_abilities" ->
                    ButtonHelper.addReaction(event, false, false, " Is " + event.getButton().getLabel(), "");
                case "tacticalAction" -> {
                    ButtonHelperTacticalAction.selectRingThatActiveSystemIsIn(player, activeGame, event);
                }
                case "ChooseDifferentDestination" -> {
                    String message = "Choosing a different system to activate. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex. Mallice is in the corner";
                    List<Button> ringButtons = ButtonHelper.getPossibleRings(player, activeGame);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
                    event.getMessage().delete().queue();
                }
                case "componentAction" -> {
                    player.setWhetherPlayerShouldBeTenMinReminded(false);
                    String message = "Use Buttons to decide what kind of component action you want to do";
                    List<Button> systemButtons = ButtonHelper.getAllPossibleCompButtons(activeGame, player, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);

                }
                case "drawRelicFromFrag" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Drew Relic");
                    DrawRelic.drawRelicAndNotify(player, event, activeGame);
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "startArbiter" -> ButtonHelper.resolveImperialArbiter(event, activeGame, player);
                case "pay1tgforKeleres" -> ButtonHelperCommanders.pay1tgToUnlockKeleres(player, activeGame, event);
                case "announceARetreat" -> {
                    String msg = ident + " announces a retreat";
                    if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "nokarcommander")) {
                        msg = msg
                                + ". Since they have nokar commander, this means they can cancel 2 hits in this coming combat round";
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                }
                case "declinePDS" -> MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        ident + " officially declines to fire PDS");
                case "startQDN" ->
                    ButtonHelperFactionSpecific.resolveQuantumDataHubNodeStep1(player, activeGame, event);
                case "finishComponentAction" -> {
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "crownofemphidiaexplore" -> {
                    player.addExhaustedRelic("emphidia");
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            ident + " Exhausted crown of emphidia");
                    List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, activeGame);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore",
                            buttons);
                }
                case "doneWithTacticalAction" -> {
                    ButtonHelperTacticalAction.concludeTacticalAction(player, activeGame, event);
                    // ButtonHelper.updateMap(activeMap, event);
                }
                case "doAnotherAction" -> {
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "ministerOfPeace" -> ButtonHelper.resolveMinisterOfPeace(player, activeGame, event);
                case "ministerOfWar" -> AgendaHelper.resolveMinisterOfWar(activeGame, player, event);
                case "concludeMove" -> {
                    ButtonHelperTacticalAction.finishMovingForTacticalAction(player, activeGame, event);
                }
                case "doneRemoving" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
                    event.getMessage().delete().queue();
                    ButtonHelper.updateMap(activeGame, event);
                }
                case "mitosisMech" -> ButtonHelperAbilities.resolveMitosisMech(buttonID, event, activeGame, player,
                        ident, finsFactionCheckerPrefix);
                case "cardsInfo" -> CardsInfo.sendCardsInfo(activeGame, player, event);
                case "showGameAgain" -> ShowGame.simpleShowGame(activeGame, event);
                case "mitosisInf" ->
                    ButtonHelperAbilities.resolveMitosisInf(buttonID, event, activeGame, player, ident);
                case "doneLanding" -> ButtonHelperModifyUnits.finishLanding(buttonID, event, activeGame, player);
                case "vote" -> {
                    String pfaction2 = null;
                    if (player != null) {
                        pfaction2 = player.getFaction();
                    }
                    if (pfaction2 != null) {
                        String voteMessage = "Chose to Vote. Click buttons for which outcome to vote for.";
                        String agendaDetails = activeGame.getCurrentAgendaInfo().split("_")[1];
                        List<Button> outcomeActionRow;
                        outcomeActionRow = AgendaHelper.getAgendaButtons(null, activeGame, "outcome");
                        if (agendaDetails.contains("For") || agendaDetails.contains("for")) {
                            outcomeActionRow = AgendaHelper.getForAgainstOutcomeButtons(null, "outcome");
                        } else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "outcome", null);
                        } else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
                            voteMessage = "Chose to Vote. Too many planets in the game to represent all as buttons. Click buttons for which player owns the planet you wish to elect.";
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "planetOutcomes",
                                    null);
                        } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                            outcomeActionRow = AgendaHelper.getSecretOutcomeButtons(activeGame, null, "outcome");
                        } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
                            outcomeActionRow = AgendaHelper.getStrategyOutcomeButtons(null, "outcome");
                        } else if (agendaDetails.contains("unit upgrade")) {
                            outcomeActionRow = AgendaHelper.getUnitUpgradeOutcomeButtons(activeGame, null, "outcome");
                        } else {
                            outcomeActionRow = AgendaHelper.getLawOutcomeButtons(activeGame, null, "outcome");
                        }
                        event.getMessage().delete().queue();
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,
                                outcomeActionRow);
                    }
                }
                case "planet_ready" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1);
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + ButtonHelper.mechOrInfCheck(planetName, activeGame, player);
                        failed = message.contains("Please try again.");
                    }

                    if (!failed) {
                        new PlanetRefresh().doAction(player, planetName, activeGame);
                        message = message + "Readied " + planetName;
                        ButtonHelper.addReaction(event, false, false, message, "");
                        event.getMessage().delete().queue();
                    } else {
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }

                }
                case "sc_no_follow" -> {
                    int scnum2 = 1;
                    boolean setstatus = true;
                    try {
                        scnum2 = Integer.parseInt(StringUtils.substringAfterLast(buttonID, "_"));
                    } catch (NumberFormatException e) {
                        try {
                            scnum2 = Integer.parseInt(lastchar);
                        } catch (NumberFormatException e2) {
                            setstatus = false;
                        }
                    }
                    if (setstatus) {
                        player.addFollowedSC(scnum2);
                    }
                    ButtonHelper.addReaction(event, false, false, "Not Following", "");
                    Set<Player> players = playerUsedSC.get(messageID);
                    if (players == null) {
                        players = new HashSet<>();
                    }
                    players.remove(player);
                    playerUsedSC.put(messageID, players);
                }
                case "turnEnd" -> {
                    TurnEnd.pingNextPlayer(event, activeGame, player);
                    event.getMessage().delete().queue();

                    ButtonHelper.updateMap(activeGame, event, "End of Turn " + player.getTurnCount() + ", Round "
                            + activeGame.getRound() + " for " + ButtonHelper.getIdent(player));
                }
                case "getDiplomatsButtons" ->
                    ButtonHelperAbilities.resolveGetDiplomatButtons(buttonID, event, activeGame, player);
                case "gameEnd" -> {
                    GameEnd.secondHalfOfGameEnd(event, activeGame, true, true);
                    event.getMessage().delete().queue();
                }
                case "rematch" -> {
                    ButtonHelper.rematch(activeGame, event);
                }
                case "purgeHacanHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("hacanhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                            .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                message + " - Leader " + "hacanhero" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Leader was not purged - something went wrong");
                    }
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeSardakkHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("sardakkhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                            .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                message + " - Leader " + "sardakkhero" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Leader was not purged - something went wrong");
                    }
                    ButtonHelperHeroes.killShipsSardakkHero(player, activeGame, event);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            player.getRepresentation(true, true)
                                    + " All ships have been removed, continue to land troops.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeRohdhnaHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("rohdhnahero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                            .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                message + " - Leader " + "rohdhnahero" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Leader was not purged - something went wrong");
                    }
                    List<Button> buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                            activeGame.getTileByPosition(activeGame.getActiveSystem()), "rohdhnaBuild", "place");
                    String message2 = player.getRepresentation() + " Use the buttons to produce units. ";
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeVaylerianHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("vaylerianhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                            .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                message + " - Leader " + "vaylerianhero" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Leader was not purged - something went wrong");
                    }
                    if (!activeGame.getNaaluAgent()) {
                        player.setTacticalCC(player.getTacticalCC() - 1);
                        AddCC.addCC(event, player.getColor(),
                                activeGame.getTileByPosition(activeGame.getActiveSystem()));
                        activeGame.setCurrentReacts("vaylerianHeroActive", "true");
                    }
                    for (Tile tile : ButtonHelperAgents.getGloryTokenTiles(activeGame)) {
                        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event,
                                "vaylerianhero");
                        if (buttons.size() > 0) {
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                    "Use buttons to remove a token from the board", buttons);
                        }
                    }
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            player.getFactionEmoji() + " can gain 1 CC");
                    List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                    String message2 = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                            + ". Use buttons to gain CCs";
                    MessageHelper.sendMessageToChannelWithButtons((MessageChannel) event.getChannel(), message2,
                            buttons);
                    ButtonHelper.deleteTheOneButton(event);
                    activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
                }
                case "purgeKeleresAHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("keleresherokuuasi");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                            .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                message + " - Leader " + "keleresherokuuasi" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Leader was not purged - something went wrong");
                    }
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTileByPosition(activeGame.getActiveSystem()), "2 cruiser, 1 flagship",
                            activeGame);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            player.getRepresentation(true, true) + " 2 cruisers and a flagship added.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeDihmohnHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("dihmohnhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
                            .append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                message + " - Leader " + "dihmohnhero" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                                "Leader was not purged - something went wrong");
                    }
                    ButtonHelperHeroes.resolvDihmohnHero(activeGame);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation(true, true)
                            + " sustained everything. Reminder you do not take hits this round.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "quash" -> {
                    int stratCC = player.getStrategicCC();
                    player.setStrategicCC(stratCC - 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Quashed agenda. Strategic CCs went from " + stratCC + " -> " + (stratCC - 1));
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
                    String agendaCount = activeGame.getFactionsThatReactedToThis("agendaCount");
                    int aCount = 0;
                    if (agendaCount.isEmpty()) {
                        aCount = 0;
                    } else {
                        aCount = Integer.parseInt(agendaCount) - 1;
                    }
                    activeGame.setCurrentReacts("agendaCount", aCount + "");
                    new RevealAgenda().revealAgenda(event, false, activeGame, activeGame.getMainGameChannel());
                    event.getMessage().delete().queue();
                }
                case "scoreAnObjective" -> {
                    List<Button> poButtons = TurnEnd.getScoreObjectiveButtons(event, activeGame,
                            finsFactionCheckerPrefix);
                    poButtons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                    MessageChannel channel = event.getMessageChannel();
                    if (activeGame.isFoWMode()) {
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to score an objective",
                            poButtons);
                }
                case "startChaosMapping" -> ButtonHelperFactionSpecific.firstStepOfChaos(activeGame, player, event);
                case "useLawsOrder" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            ident + " is paying 1 influence to ignore laws for the turn.");
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "inf");
                    Button doneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(doneExhausting);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                            "Click the names of the planets you wish to exhaust to pay the 1 influence", buttons);
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "orbitolDropFollowUp" ->
                    ButtonHelperAbilities.oribtalDropFollowUp(buttonID, event, activeGame, player, ident);
                case "dropAMechToo" -> {
                    String message = "Please select the same planet you dropped the infantry on";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, "mech", "place");
                    buttons.add(Button.danger("orbitolDropExhaust", "Pay for mech"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            message, buttons);
                    event.getMessage().delete().queue();
                }
                case "orbitolDropExhaust" ->
                    ButtonHelperAbilities.oribtalDropExhaust(buttonID, event, activeGame, player, ident);
                case "dominusOrb" -> {
                    activeGame.setDominusOrb(true);
                    String purgeOrExhaust = "Purged ";
                    String relicId = "dominusorb";
                    player.removeRelic(relicId);
                    player.removeExhaustedRelic(relicId);
                    String relicName = Mapper.getRelic(relicId).getName();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            purgeOrExhaust + Emojis.Relic + " relic: " + relicName);
                    event.getMessage().delete().queue();
                    String message = "Choose a system to move from.";
                    List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeGame, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                }
                case "ultimateUndo" -> {
                    if (activeGame.getSavedButtons().size() > 0 && !activeGame.getCurrentPhase().contains("status")) {
                        String buttonString = activeGame.getSavedButtons().get(0);
                        if (activeGame.getPlayerFromColorOrFaction(buttonString.split(";")[0]) != null) {
                            boolean showGame = false;
                            for (String buttonString2 : activeGame.getSavedButtons()) {
                                if (buttonString2.contains("Show Game")) {
                                    showGame = true;
                                    break;
                                }
                            }
                            if (player != activeGame.getPlayerFromColorOrFaction(buttonString.split(";")[0])
                                    && !showGame) {
                                MessageHelper.sendMessageToChannel(event.getChannel(),
                                        "You were not the player who pressed the latest button. Use /game undo if you truly want to undo "
                                                + activeGame.getLatestCommand());
                                return;
                            }
                        }
                    }

                    GameSaveLoadManager.undo(activeGame, event);

                    if ("action".equalsIgnoreCase(activeGame.getCurrentPhase())
                            || "agendaVoting".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                        if (!event.getMessage().getContentRaw().contains(finsFactionCheckerPrefix)) {
                            event.getMessage().delete().queue();
                        }
                    }
                }
                case "getDiscardButtonsACs" -> {
                    String msg = trueIdentity + " use buttons to discard";
                    List<Button> buttons = ACInfo.getDiscardActionCardButtons(activeGame, player, false);
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
                }
                case "eraseMyRiders" -> AgendaHelper.reverseAllRiders(event, activeGame, player);
                case "eraseMyVote" -> {
                    String pfaction = player.getFaction();
                    if (activeGame.isFoWMode()) {
                        pfaction = player.getColor();
                    }
                    AgendaHelper.eraseVotesOfFaction(activeGame, pfaction);
                    String eraseMsg = "Erased previous votes made by " + player.getFactionEmoji() + "\n\n"
                            + AgendaHelper.getSummaryOfVotes(activeGame, true);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), eraseMsg);
                    Button vote = Button.success(finsFactionCheckerPrefix + "vote",
                            StringUtils.capitalize(player.getFaction()) + " Choose To Vote");
                    Button abstain = Button.danger(finsFactionCheckerPrefix + "resolveAgendaVote_0",
                            StringUtils.capitalize(player.getFaction()) + " Choose To Abstain");
                    Button forcedAbstain = Button.secondary("forceAbstainForPlayer_" + player.getFaction(),
                            "(For Others) Abstain for this player");

                    String buttonMsg = "Use buttons to vote again. Reminder that this erasing of old votes did not refresh any planets.";
                    List<Button> buttons = List.of(vote, abstain, forcedAbstain);
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            buttonMsg, buttons);
                }
                case "setOrder" -> {
                    Helper.setOrder(activeGame);
                    event.getMessage().delete().queue();
                }
                case "gain_CC" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1);
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + ButtonHelper.mechOrInfCheck(planetName, activeGame, player);
                        failed = message.contains("Please try again.");
                    }
                    if (!failed) {
                        String message2 = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                                + ". Use buttons to gain CCs";
                        activeGame.setCurrentReacts("originalCCsFor" + player.getFaction(),
                                player.getCCRepresentation());
                        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                    }

                    ButtonHelper.addReaction(event, false, false, message, "");
                    if (!failed && !event.getMessage().getContentRaw().contains("fragment")) {
                        event.getMessage().delete().queue();
                        if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                            String pF = player.getFactionEmoji();
                            MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                        }
                    }
                }
                case "run_status_cleanup" -> {
                    new Cleanup().runStatusCleanup(activeGame);
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
                    String message3 = event.getMessage().getContentRaw();
                    event.getMessage().editMessage(message3).setComponents(actionRow2).queue();

                    ButtonHelper.addReaction(event, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");

                }
                default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
            }
        }
    }

    private void deleteButtons(ButtonInteractionEvent event, String buttonID, String buttonLabel, Game activeGame,
            Player player, MessageChannel actionsChannel, String trueIdentity) {
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
                if ("statusHomework".equalsIgnoreCase(activeGame.getCurrentPhase())) {
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
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                                    "# " + player.getRepresentation(true, true)
                                            + " heads up, bot thinks you should have gained " + properGain
                                            + " cc due to: " + reasons);
                        }
                    }
                }
                player.setTotalExpenses(player.getTotalExpenses() + netGain * 3);
            }

            if ("Done Redistributing CCs".equalsIgnoreCase(buttonLabel)) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                        playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is " + finalCCs);
            } else {
                if ("leadership".equalsIgnoreCase(buttonID)) {
                    String message = playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is "
                            + finalCCs;
                    ButtonHelper.sendMessageToRightStratThread(player, activeGame, message, "leadership");
                } else {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            playerRep + " Final CC Allocation Is " + finalCCs);
                }

            }
            ButtonHelper.checkFleetInEveryTile(player, activeGame, event);

        }
        if (("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
                || "Done Producing Units".equalsIgnoreCase(buttonLabel))
                && !event.getMessage().getContentRaw().contains("Click the names of the planets you wish")) {
            Tile tile = null;
            if("Done Producing Units".equalsIgnoreCase(buttonLabel) && buttonID.contains("_")){
                String pos = buttonID.split("_")[1];
                buttonID = buttonID.split("_")[0];
                tile = activeGame.getTileByPosition(pos);
            }
            ButtonHelper.sendMessageToRightStratThread(player, activeGame, editedMessage, buttonID);
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel)) {

                player.setTotalExpenses(
                        player.getTotalExpenses() + Helper.calculateCostOfProducedUnits(player, activeGame, true));
                String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "res");
                if (player.hasTechReady("sar") && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sar = Button.danger("exhaustTech_sar", "Exhaust Self Assembly Routines");
                    buttons.add(sar);
                }
                if (player.hasTechReady("htp") && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sar = Button.danger("exhaustTech_htp", "Exhaust Hegemonic Trade Policy");
                    buttons.add(sar);
                }
                if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "titanscommander")
                        && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sar2 = Button.success("titansCommanderUsage", "Use Titans Commander To Gain a TG");
                    buttons.add(sar2);
                }
                if (ButtonHelper.getNumberOfUnitUpgrades(player) > 0 && player.hasTechReady("aida")
                        && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button aiDEVButton = Button.danger("exhaustTech_aida",
                            "Exhaust AIDEV (" + ButtonHelper.getNumberOfUnitUpgrades(player) + "r)");
                    buttons.add(aiDEVButton);
                }
                if (player.hasTechReady("st") && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sarweenButton = Button.danger("useTech_st", "Use Sarween");
                    buttons.add(sarweenButton);
                }
                if (player.hasTechReady("absol_st")) {
                    Button sarweenButton = Button.danger("useTech_absol_st", "Use Sarween Tools");
                    buttons.add(sarweenButton);
                }
                if (player.hasUnexhaustedLeader("winnuagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button winnuButton = Button.danger("exhaustAgent_winnuagent", "Use Winnu Agent")
                            .withEmoji(Emoji.fromFormatted(Emojis.Winnu));
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("gledgeagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button winnuButton = Button
                            .danger("exhaustAgent_gledgeagent_" + player.getFaction(), "Use Gledge Agent")
                            .withEmoji(Emoji.fromFormatted(Emojis.gledge));
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("ghotiagent")) {
                    Button winnuButton = Button
                            .danger("exhaustAgent_ghotiagent_" + player.getFaction(), "Use Ghoti Agent")
                            .withEmoji(Emoji.fromFormatted(Emojis.ghoti));
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("mortheusagent")) {
                    Button winnuButton = Button
                            .danger("exhaustAgent_mortheusagent_" + player.getFaction(), "Use Mortheus Agent")
                            .withEmoji(Emoji.fromFormatted(Emojis.mortheus));
                    buttons.add(winnuButton);
                }
                if (player.hasUnexhaustedLeader("rohdhnaagent") && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button winnuButton = Button
                            .danger("exhaustAgent_rohdhnaagent_" + player.getFaction(), "Use Rohdhna Agent")
                            .withEmoji(Emoji.fromFormatted(Emojis.rohdhna));
                    buttons.add(winnuButton);
                }
                if (player.hasLeaderUnlocked("hacanhero") && !"muaatagent".equalsIgnoreCase(buttonID)
                        && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button hacanButton = Button.danger("purgeHacanHero", "Purge Hacan Hero")
                            .withEmoji(Emoji.fromFormatted(Emojis.Hacan));
                    buttons.add(hacanButton);
                }
                Button doneExhausting;
                if (!buttonID.contains("deleteButtons")) {
                    doneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
                } else {
                    doneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
                }
                ButtonHelper.updateMap(activeGame, event,
                        "Result of build on turn " + player.getTurnCount() + " for " + ButtonHelper.getIdent(player));
                buttons.add(doneExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                if(tile != null && player.hasAbility("rally_to_the_cause") && FoWHelper.getPlayerHS(activeGame, player) == tile && ButtonHelperAbilities.getTilesToRallyToTheCause(activeGame, player).size() > 0){
                    String msg = player.getRepresentation()+" due to your rally to the cause ability, if you just produced a ship in your HS, you can produce up to 2 ships in a system that contains a planet with a trait but no legendary planets and no opponent units. Press button to resolve";
                    List<Button> buttons2 = new ArrayList<>();
                    buttons2.add(Button.success("startRallyToTheCause","Rally To The Cause"));
                    buttons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),msg,buttons2);

                }
            }
        }
        if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            if (player.hasTech("asn") && (buttonID.contains("tacticalAction") || buttonID.contains("warfare"))) {
                ButtonHelperFactionSpecific.offerASNButtonsStep1(activeGame, player, buttonID);
            }
            if (buttonID.contains("tacticalAction")) {
                ButtonHelper.exploreDET(player, activeGame, event);
                ButtonHelperFactionSpecific.cleanCavUp(activeGame, event);
                if (player.hasAbility("cunning")) {
                    List<Button> trapButtons = new ArrayList<>();
                    for (UnitHolder uH : activeGame.getTileByPosition(activeGame.getActiveSystem()).getUnitHolders()
                            .values()) {
                        if (uH instanceof Planet) {
                            String planet = uH.getName();
                            trapButtons.add(Button.secondary("setTrapStep3_" + planet,
                                    Helper.getPlanetRepresentation(planet, activeGame)));
                        }
                    }
                    trapButtons.add(Button.danger("deleteButtons", "Decline"));
                    String msg = player.getRepresentation(true, true)
                            + " you can use the buttons to place a trap on a planet";
                    if (trapButtons.size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                ButtonHelper.getCorrectChannel(player, activeGame), msg, trapButtons);
                    }
                }
                if (player.hasUnexhaustedLeader("celdauriagent")) {
                    List<Button> buttons = new ArrayList<>();
                    Button hacanButton = Button
                            .secondary("exhaustAgent_celdauriagent_" + player.getFaction(), "Use Celdauri Agent")
                            .withEmoji(Emoji.fromFormatted(Emojis.celdauri));
                    buttons.add(hacanButton);
                    buttons.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            player.getRepresentation(true, true)
                                    + " you can use Celdauri agent to place an SD for 2tg/2comm",
                            buttons);
                }
                List<Button> systemButtons2 = new ArrayList<>();
                if (!activeGame.isAbsolMode() && player.getRelics().contains("emphidia")
                        && !player.getExhaustedRelics().contains("emphidia")) {
                    String message = trueIdentity + " You can use the button to explore using crown of emphidia";
                    systemButtons2.add(Button.success("crownofemphidiaexplore", "Use Crown To Explore a Planet"));
                    systemButtons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("sardakkagent")) {
                    String message = trueIdentity + " You can use the button to do sardakk agent";
                    systemButtons2.addAll(ButtonHelperAgents.getSardakkAgentButtons(activeGame, player));
                    systemButtons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("nomadagentmercer")) {
                    String message = trueIdentity + " You can use the button to do General Mercer";
                    systemButtons2.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(activeGame, player));
                    systemButtons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }

                if (activeGame.getNaaluAgent()) {
                    player = activeGame.getPlayer(activeGame.getActivePlayerID());
                    activeGame.setNaaluAgent(false);
                }
                activeGame.setCurrentReacts("tnelisCommanderTracker", "");

                String message = player.getRepresentation(true, true)
                        + " Use buttons to end turn or do another action.";
                List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, activeGame, true, event);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        message, systemButtons);
                player.resetOlradinPolicyFlags();
            }
        }
        if ("diplomacy".equalsIgnoreCase(buttonID)) {
            ButtonHelper.sendMessageToRightStratThread(player, activeGame, editedMessage, "diplomacy", null);
        }
        if ("spitItOut".equalsIgnoreCase(buttonID) && !"Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), editedMessage);
        }
        event.getMessage().delete().queue();
    }

    public boolean addUsedSCPlayer(String messageID, Game activeGame, Player player,
            @NotNull ButtonInteractionEvent event, String defaultText) {
        Set<Player> players = playerUsedSC.get(messageID);
        if (players == null) {
            players = new HashSet<>();
        }
        boolean contains = players.contains(player);
        players.add(player);
        playerUsedSC.put(messageID, players);
        if (contains) {
            String alreadyUsedMessage = defaultText.isEmpty() ? "used Secondary of Strategy Card" : defaultText;
            String message = player.getRepresentation() + " already " + alreadyUsedMessage;
            if (activeGame.isFoWMode()) {
                MessageHelper.sendPrivateMessageToPlayer(player, activeGame, message);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
            }
        }
        return contains;
    }

    @NotNull
    public String deductCC(Player player, @NotNull ButtonInteractionEvent event) {
        int strategicCC = player.getStrategicCC();
        String message;
        if (strategicCC == 0) {
            message = "Have 0 CC in Strategy, can't follow";
        } else {
            strategicCC--;
            player.setStrategicCC(strategicCC);
            message = " following SC, deducted 1 CC from Strategy Tokens";
        }
        return message;
    }

    public void clearAllReactions(@NotNull ButtonInteractionEvent event) {
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

    public void checkForAllReactions(@NotNull ButtonInteractionEvent event, Game activeGame) {
        String buttonID = event.getButton().getId();

        String messageId = event.getInteraction().getMessage().getId();
        int matchingFactionReactions = 0;
        for (Player player : activeGame.getRealPlayers()) {
            boolean factionReacted = false;
            if (buttonID.contains("no_after")) {
                if (activeGame.getFactionsThatReactedToThis("noAfterThisAgenda").contains(player.getFaction())) {
                    factionReacted = true;
                }
                Message mainMessage = event.getMessage();
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (activeGame.isFoWMode()) {
                    int index = 0;
                    for (Player player_ : activeGame.getPlayers().values()) {
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
                if (activeGame.getFactionsThatReactedToThis("noWhenThisAgenda").contains(player.getFaction())) {
                    factionReacted = true;
                }
                Message mainMessage = event.getMessage();
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (activeGame.isFoWMode()) {
                    int index = 0;
                    for (Player player_ : activeGame.getPlayers().values()) {
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
            if (factionReacted || (activeGame.getFactionsThatReactedToThis(messageId) != null
                    && activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction()))) {
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = activeGame.getRealPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            respondAllPlayersReacted(event, activeGame);
            activeGame.removeMessageIDFromCurrentReacts(messageId);
        }
    }

    public void checkForAllReactions(String messageId, Game activeGame) {
        int matchingFactionReactions = 0;
        for (Player player : activeGame.getRealPlayers()) {

            if ((activeGame.getFactionsThatReactedToThis(messageId) != null
                    && activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction()))) {
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = activeGame.getRealPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            activeGame.getMainGameChannel().retrieveMessageById(messageId).queue(msg -> {
                if (activeGame.getLatestAfterMsg().equalsIgnoreCase(messageId)) {
                    msg.reply("All players have indicated 'No Afters'").queueAfter(1000, TimeUnit.MILLISECONDS);
                    AgendaHelper.startTheVoting(activeGame);
                    msg.delete().queue();
                } else if (activeGame.getLatestWhenMsg().equalsIgnoreCase(messageId)) {
                    msg.reply("All players have indicated 'No Whens'").queueAfter(10, TimeUnit.MILLISECONDS);

                } else {
                    String msg2 = "All players have indicated 'No Sabotage'";
                    if (activeGame.getMessageIDsForSabo().contains(messageId)) {
                        String faction = "bob" + activeGame.getFactionsThatReactedToThis(messageId);
                        faction = faction.split("_")[1];
                        Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
                        if (p2 != null && !activeGame.isFoWMode()) {
                            msg2 = p2.getRepresentation() + " " + msg2;
                        }
                    }
                    msg.reply(msg2).queueAfter(1, TimeUnit.SECONDS);
                }
            });

            if (activeGame.getMessageIDsForSabo().contains(messageId)) {
                activeGame.removeMessageIDForSabo(messageId);
            }
        }
    }

    public static boolean checkForASpecificPlayerReact(String messageId, Player player, Game activeGame) {

        activeGame.setShushing(false);
        try {
            if (activeGame.getFactionsThatReactedToThis(messageId) != null
                    && activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction())) {
                return true;
            }
            activeGame.getMainGameChannel().retrieveMessageById(messageId).queue(mainMessage -> {
                Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                if (activeGame.isFoWMode()) {
                    int index = 0;
                    for (Player player_ : activeGame.getPlayers().values()) {
                        if (player_ == player)
                            break;
                        index++;
                    }
                    reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, messageId));
                }
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction != null) {
                    activeGame.setShushing(true);
                }
            });
        } catch (Exception e) {
            activeGame.removeMessageIDForSabo(messageId);
            return true;
        }
        return activeGame.getBotShushing();

    }

    private static void respondAllPlayersReacted(ButtonInteractionEvent event, Game activeGame) {
        String buttonID = event.getButton().getId();
        if (activeGame == null || buttonID == null) {
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
            case Constants.SC_FOLLOW, "sc_no_follow", "sc_refresh", "sc_refresh_and_wash", "trade_primary",
                    "sc_ac_draw", "sc_draw_so", "sc_trade_follow", "sc_leadership_follow" -> {
                if (activeGame.isFoWMode()) {
                    event.getInteraction().getMessage().reply("All players have reacted to this Strategy Card")
                            .queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage("All players have reacted to this Strategy Card").queueAfter(10,
                            TimeUnit.SECONDS);
                    if (guildMessageChannel instanceof ThreadChannel)
                        ((ThreadChannel) guildMessageChannel).getManager().setArchived(true).queueAfter(5,
                                TimeUnit.MINUTES);
                }
            }
            case "no_when", "no_when_persistent" -> event.getInteraction().getMessage()
                    .reply("All players have indicated 'No Whens'").queueAfter(1, TimeUnit.SECONDS);
            case "no_after", "no_after_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Afters'").queue();
                AgendaHelper.startTheVoting(activeGame);
                event.getMessage().delete().queue();

            }
            case "no_sabotage" -> {
                String msg = "All players have indicated 'No Sabotage'";
                if (activeGame.getMessageIDsForSabo().contains(event.getMessageId())) {
                    String faction = activeGame.getFactionsThatReactedToThis(event.getMessageId());
                    System.err.println(faction);
                    faction = faction.split("_")[0];
                    Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
                    if (p2 != null && !activeGame.isFoWMode()) {
                        msg = p2.getRepresentation() + " " + msg;
                    }
                    activeGame.removeMessageIDForSabo(event.getMessageId());
                }
                event.getInteraction().getMessage().reply(msg).queueAfter(1, TimeUnit.SECONDS);

            }

            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> {
                String message2 = "All players have indicated scoring. Flip the relevant PO using the buttons. This will automatically run status clean-up if it has not been run already.";
                Button drawStage2 = Button.success("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Button.success("reveal_stage_1", "Reveal Stage 1");
                // Button runStatusCleanup = Button.primary("run_status_cleanup", "Run Status
                // Cleanup");
                List<Button> buttons = new ArrayList<>();

                if (activeGame.getRound() < 4) {
                    buttons.add(drawStage1);
                }
                if (activeGame.getRound() > 2) {
                    buttons.add(drawStage2);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                event.getMessage().delete().queueAfter(20, TimeUnit.SECONDS);
            }
            case "pass_on_abilities" -> {
                if (activeGame.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Button.primary("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now",
                            buttons);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), activeGame.getPing()
                            + " All players have indicated completion of status phase. Proceed to Strategy Phase.");
                    StartPhase.startPhase(event, activeGame, "strategy");
                }
            }
            case "redistributeCCButtons" -> {
                if (activeGame.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Button.primary("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now",
                            buttons);
                } else {
                    Button flipAgenda = Button.primary("startStrategyPhase", "Press this to start Strategy Phase");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Start Strategy Phase", buttons);
                }
            }
        }
    }
}
