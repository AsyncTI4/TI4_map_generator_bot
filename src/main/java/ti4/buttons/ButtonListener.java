package ti4.buttons;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import ti4.commands.agenda.PutAgendaBottom;
import ti4.commands.agenda.PutAgendaTop;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.commands.cardsac.DiscardACRandom;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.DealSOToAll;
import ti4.commands.cardsso.DiscardSO;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.custom.PeakAtStage1;
import ti4.commands.custom.PeakAtStage2;
import ti4.commands.explore.DrawRelic;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.game.GameEnd;
import ti4.commands.game.StartPhase;
import ti4.commands.game.Swap;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.player.SCPick;
import ti4.commands.player.SCPlay;
import ti4.commands.player.Stats;
import ti4.commands.player.TurnEnd;
import ti4.commands.special.FighterConscription;
import ti4.commands.special.NaaluCommander;
import ti4.commands.special.NovaSeed;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tokens.AddCC;
import ti4.commands.uncategorized.ShowGame;
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
import ti4.helpers.CombatModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.FrankenDraftHelper;
import ti4.helpers.Helper;
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
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;

public class ButtonListener extends ListenerAdapter {
    public static final HashMap<Guild, HashMap<String, Emoji>> emoteMap = new HashMap<>();
    private static final HashMap<String, Set<Player>> playerUsedSC = new HashMap<>();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!AsyncTI4DiscordBot.readyToReceiveCommands) {
            event.reply("Failed to press button. Please try again in a moment. The bot is rebooting.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        long timeNow = new Date().getTime();
        try {
            resolveButtonInteractionEvent(event);
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with button interaction", e);
        }
        if(new Date().getTime() - timeNow > 3000){
             BotLogger.log(event, "This button command took longer than 3000 ms ("+(new Date().getTime() - timeNow)+")");
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
        gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
        gameName = gameName.replace(Constants.BAG_INFO_THREAD_PREFIX, "");
        gameName = StringUtils.substringBefore(gameName, "-");
        Game activeGame = GameManager.getInstance().getGame(gameName);
        Player player = activeGame.getPlayer(id);
        player = Helper.getGamePlayer(activeGame, player, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
            return;
        }
        buttonID = buttonID.replace("delete_buttons_", "resolveAgendaVote_");
        activeGame.increaseButtonPressCount();

        MessageChannel privateChannel = event.getChannel();
        if (activeGame.isFoWMode()) {
            if (player.getPrivateChannel() == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Private channels are not set up for this game. Messages will be suppressed.");
                privateChannel = null;
            } else {
                privateChannel = player.getPrivateChannel();
            }
        }

        MessageChannel mainGameChannel = event.getChannel();
        if (activeGame.getMainGameChannel() != null) {
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
            String factionWhoIsUp = player.getFaction();
            if (!player.getFaction().equalsIgnoreCase(factionWhoGeneratedButton)
                && !buttonLabel.toLowerCase().contains(factionWhoIsUp)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "To " + player.getFactionEmoji()
                    + ": you are not the faction who these buttons are meant for.");
                return;
            }
        }
        

        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
        String trueIdentity = Helper.getPlayerRepresentation(player, activeGame, event.getGuild(), true);
        String ident = player.getFactionEmoji();
        player.setWhetherPlayerShouldBeTenMinReminded("doneWithTacticalAction".equalsIgnoreCase(buttonID));
        
        if (!"ultimateundo".equalsIgnoreCase(buttonID)) {
            ButtonHelper.saveButtons(event, activeGame, player);
            GameSaveLoadManager.saveMap(activeGame, event);
        }

        
        if (activeGame.getActivePlayer() != null && player.getUserID().equalsIgnoreCase(activeGame.getActivePlayer())) {
            activeGame.setLastActivePlayerPing(new Date());
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
                List<Button> scButtons = new ArrayList<Button>();
                scButtons.add(Button.success("resolveReverse_" + actionCardTitle, "Pick up " + actionCardTitle + " from the discard"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), false) + " After checking for sabos, use buttons to resolve reverse engineer", scButtons);
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
                        MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
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
                        List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, activeGame, "mech", "placeOneNDone_skipbuild"));
                        buttons.add(Button.danger("deleteButtons", "Decline to drop Mech"));
                        MessageHelper.sendMessageToChannelWithButtons(channel2, message3, buttons);
                        List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                        MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
                    }
                    ButtonHelper.checkACLimit(activeGame, event, player);
                    event.getMessage().delete().queue();

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
                    new DiscardSO().discardSO(event, player, soIndex, activeGame);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), ident + " discarded an SO");
                    }
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
            new Swap().secondHalfOfSwap(activeGame, player, activeGame.getPlayerFromColorOrFaction(faction), event.getUser(), event);
        } else if (buttonID.startsWith("yinHeroInfantry_")) {
            ButtonHelperHeroes.lastStepOfYinHero(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("arcExp_")) {
            ButtonHelperActionCards.resolveArcExpButtons(activeGame, player, buttonID, event, trueIdentity);
        } else if (buttonID.startsWith("augerHeroSwap_")) {
            ButtonHelperHeroes.augersHeroSwap(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("hacanMechTradeStepOne_")) {
            ButtonHelperFactionSpecific.resolveHacanMechTradeStepOne(player, activeGame, event, buttonID);
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
            String msg = trueIdentity + " the bot doesn't know if the next objective is a stage 1 or a stage 2. Please help it out and click the right button.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        } else if (buttonID.startsWith("cabalHeroTile_")) {
            ButtonHelperHeroes.executeCabalHero(buttonID, player, activeGame, event);
        } else if (buttonID.startsWith("creussIFFStart_")) {
            ButtonHelperFactionSpecific.resolveCreussIFFStart(activeGame, player, buttonID, ident, event);
        } else if (buttonID.startsWith("creussIFFResolve_")) {
            ButtonHelperFactionSpecific.resolveCreussIFF(activeGame, player, buttonID, ident, event);
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
        } else if (buttonID.startsWith("placeGhostCommanderFF_")) {
            ButtonHelperCommanders.resolveGhostCommanderPlacement(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("yinHeroPlanet_")) {
            String planet = buttonID.replace("yinHeroPlanet_", "");
            MessageHelper.sendMessageToChannel(event.getChannel(), trueIdentity + " Chose to invade " + Helper.getPlanetRepresentation(planet, activeGame));
            List<Button> buttons = new ArrayList<>();
            for (int x = 1; x < 4; x++) {
                buttons.add(Button.success(finsFactionCheckerPrefix + "yinHeroInfantry_" + planet + "_" + x, "Land " + x + " infantry")
                    .withEmoji(Emoji.fromFormatted(Emojis.infantry)));
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Use buttons to select how many infantry you'd like to land on the planet", buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("yinHeroTarget_")) {
            String faction = buttonID.replace("yinHeroTarget_", "");
            List<Button> buttons = new ArrayList<>();
            Player target = activeGame.getPlayerFromColorOrFaction(faction);
            if (target != null) {
                for (String planet : target.getPlanets()) {
                    buttons.add(Button.success(finsFactionCheckerPrefix + "yinHeroPlanet_" + planet, Helper.getPlanetRepresentation(planet, activeGame)));
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Use buttons to select which planet to invade", buttons);
                event.getMessage().delete().queue();
            }
        } else if (buttonID.startsWith("yinHeroStart")) {
            List<Button> buttons = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "yinHeroTarget", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Use buttons to select which player owns the planet you want to land on", buttons);
        } else if (buttonID.startsWith("psychoExhaust_")) {
            ButtonHelper.resolvePsychoExhaust(activeGame, event, player, buttonID);
        } else if (buttonID.startsWith("productionBiomes_")) {
            ButtonHelperFactionSpecific.resolveProductionBiomesStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("step2axisagent_")) {
            ButtonHelperAgents.resolveStep2OfAxisAgent(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("hacanAgentRefresh_")) {
            ButtonHelperAgents.hacanAgentRefresh(buttonID, event, activeGame, player, ident, trueIdentity);
        } else if (buttonID.startsWith("getPsychoButtons")) {
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), trueIdentity + " use buttons to get a tg per planet exhausted.",
                ButtonHelper.getPsychoTechPlanets(activeGame, player));
        } else if (buttonID.startsWith("retreatGroundUnits_")) {
            ButtonHelperModifyUnits.retreatGroundUnits(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("resolveShipOrder_")) {
            ButtonHelperAbilities.resolveAxisOrderExhaust(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("buyAxisOrder_")) {
            ButtonHelperAbilities.resolveAxisOrderBuy(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("naaluCommander")) {
            new NaaluCommander().secondHalfOfNaaluCommander(event, activeGame, player);
        } else if (buttonID.startsWith("mahactMechHit_")) {
            String pos = buttonID.split("_")[1];
            String color = buttonID.split("_")[2];
            Tile tile = activeGame.getTileByPosition(pos);
            Player attacker = activeGame.getPlayerFromColorOrFaction(color);
            ButtonHelper.resolveMahactMechAbilityUse(player, attacker, activeGame, tile, event);
        } else if (buttonID.startsWith("benedictionStep1_")) {
            String pos1 = buttonID.split("_")[1];
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                trueIdentity + " choose the tile you wish to send the ships in " + activeGame.getTileByPosition(pos1).getRepresentationForButtons(activeGame, player) + " to.",
                ButtonHelperHeroes.getBenediction2ndTileOptions(player, activeGame, pos1));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("mahactBenedictionFrom_")) {
            ButtonHelperHeroes.mahactBenediction(buttonID, event, activeGame, player);
            String pos1 = buttonID.split("_")[1];
            String pos2 = buttonID.split("_")[1];
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                ident + " moved all units in space from " + activeGame.getTileByPosition(pos1).getRepresentationForButtons(activeGame, player) + " to "
                    + activeGame.getTileByPosition(pos2).getRepresentationForButtons(activeGame, player)
                    + " using Mahact hero. If they moved themselves and wish to move ground forces, they can do so either with slash command or modify units button.");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("retreatUnitsFrom_")) {
            ButtonHelperModifyUnits.retreatSpaceUnits(buttonID, event, activeGame, player);
            String both = buttonID.replace("retreatUnitsFrom_", "");
            String pos1 = both.split("_")[0];
            String pos2 = both.split("_")[1];
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                ident + " retreated all units in space to " + activeGame.getTileByPosition(pos2).getRepresentationForButtons(activeGame, player));
            String message = trueIdentity + " Use below buttons to move any ground forces or conclude retreat.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatingGroundTroopsButtons(player, activeGame, event, pos1, pos2));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("retreat_")) {
            String pos = buttonID.split("_")[1];
            boolean skilled = false;
            if(buttonID.contains("skilled")){
                skilled = true;
                event.getMessage().delete().queue();
            }
            if(buttonID.contains("foresight")){
                MessageHelper.sendMessageToChannel(event.getChannel(), ident+" lost a strategy cc to resolve the foresight ability");
                player.setStrategicCC(player.getStrategicCC()-1);
                skilled = true;
            }
            String message = trueIdentity + " Use buttons to select a system to move too. Warning: bot does not always know what the valid retreat tiles are, you will need to verify these.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatSystemButtons(player, activeGame, pos, skilled));
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
            ButtonHelperFactionSpecific.resolveSelectedBeforeSwapSC(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("sardakkcommander_")) {
            ButtonHelperCommanders.resolveSardakkCommander(activeGame, player, buttonID, event, ident);
        } else if (buttonID.startsWith("useOmenDie_")) {
            ButtonHelperAbilities.useOmenDie(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("peaceAccords_")) {
            ButtonHelperAbilities.resolvePeaceAccords(buttonID, ident, player, activeGame, event);
        } else if (buttonID.startsWith("get_so_discard_buttons")) {
            String secretScoreMsg = "_ _\nClick a button below to discard your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveDiscardButtons(activeGame, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Only the player who played Politics can assign Speaker");
                return;
            }
            if (activeGame != null ) {
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
                    ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scnum, activeGame);
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
                                    buttonsToRemoveCC.add(Button.success(finChecker + "removeCCFromBoard_mahactAgent" + p2.getFaction() + "_" + tile.getPosition(),
                                        "Remove CC from " + tile.getRepresentationForButtons(activeGame, player)));
                                }
                                MessageHelper.sendMessageToChannelWithButtons(channel, trueIdentity + " Use buttons to remove a CC", buttonsToRemoveCC);
                            }
                        }
                    }
                }
            } else {
                MessageHelper.sendMessageToChannel(channel, trueIdentity + " exhausted Scepter of Empelar to follow SC#" + scnum);
                player.addExhaustedRelic("emelpar");
            }
            Emoji emojiToUse = Emoji.fromFormatted(player.getFactionEmoji());

            if (channel instanceof ThreadChannel) {
                activeGame.getActionsChannel().addReactionById(channel.getId(), emojiToUse).queue();
            } else {
                MessageHelper.sendMessageToChannel(channel, "Hey, something went wrong leaving a react, please just hit the no follow button on the SC to do so.");
            }
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("sc_follow_") && (!buttonID.contains("leadership"))
            && (!buttonID.contains("trade"))) {
            boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "");
            if (!used) {
                if (player.getStrategicCC() > 0) {
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
                }
                String message = deductCC(player, event);

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
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scnum, activeGame);
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
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            ButtonHelper.addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            String bID = buttonID.replace("movedNExplored_", "");
            String[] info = bID.split("_");
            new ExpPlanet().explorePlanet(event, activeGame.getTileFromPlanet(info[1]), info[1], info[2], player, false, activeGame, 1, false);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolveExp_Look_")) {
            String bID = buttonID.replace("resolveExp_Look_", "");
            String trait = bID;
            ArrayList<String> deck = activeGame.getExploreDeck(trait);
            ArrayList<String> discardPile = activeGame.getExploreDiscard(trait);
            ButtonHelper.addReaction(event, true, false, "Looked at top of the " + trait + " deck.", "");
            event.getMessage().delete().queue();
            String traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
            String playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
            if (deck.isEmpty() && discardPile.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            String topCard = deck.get(0);
            sb.append(new ExpPlanet().displayExplore(topCard));

            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, sb.toString());
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "top of " + traitNameWithEmoji + " explore deck has been set to " + playerFactionNameWithEmoji
                + " Cards info thread.");
        } else if (buttonID.startsWith("relic_look_top")) {
            List<String> relicDeck = activeGame.getAllRelics();
            if (relicDeck.isEmpty()) {
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, "Relic deck is empty");
                return;
            }
            String relicID = relicDeck.get(0);
            RelicModel relicModel = Mapper.getRelic(relicID);
            String rsb = "**Relic - Look at Top**\n" + player.getRepresentation() + "\n" + relicModel.getSimpleRepresentation();
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, rsb);
            ButtonHelper.addReaction(event, true, false, "Looked at top of the Relic deck.", "");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("explore_look_All")) {
            ArrayList<String> cdeck = activeGame.getExploreDeck("cultural");
            ArrayList<String> hdeck = activeGame.getExploreDeck("hazardous");
            ArrayList<String> ideck = activeGame.getExploreDeck("industrial");
            ArrayList<String> cdiscardPile = activeGame.getExploreDiscard("cultural");
            ArrayList<String> hdiscardPile = activeGame.getExploreDiscard("hazardous");
            ArrayList<String> idiscardPile = activeGame.getExploreDiscard("industrial");
            String trait = "cultural";
            String traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
            String playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
            if (cdeck.isEmpty() && cdiscardPile.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
            }

            StringBuilder csb = new StringBuilder();
            csb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            String ctopCard = cdeck.get(0);
            csb.append(new ExpPlanet().displayExplore(ctopCard));

            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, csb.toString());
            trait = "hazardous";
            traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
            playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
            if (hdeck.isEmpty() && hdiscardPile.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
            }

            StringBuilder hsb = new StringBuilder();
            hsb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            String htopCard = hdeck.get(0);
            hsb.append(new ExpPlanet().displayExplore(htopCard));

            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, hsb.toString());
            trait = "industrial";
            traitNameWithEmoji = Emojis.getEmojiFromDiscord(trait) + trait;
            playerFactionNameWithEmoji = Emojis.getFactionIconFromDiscord(player.getFaction());
            if (ideck.isEmpty() && idiscardPile.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), traitNameWithEmoji + " explore deck & discard is empty - nothing to look at.");
            }

            StringBuilder isb = new StringBuilder();
            isb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            String itopCard = ideck.get(0);
            isb.append(new ExpPlanet().displayExplore(itopCard));

            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, isb.toString());
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "top of Hazardous, Cultural and Industrial explore decks has been set to " + playerFactionNameWithEmoji
                + " Cards info thread.");
            ButtonHelper.addReaction(event, true, false, "Looked at top of Hazardous, Cultural and Industrial decks.", "");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("distant_suns_")) {
            ButtonHelperAbilities.distantSuns(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("getPlagiarizeButtons")) {
            activeGame.setComponentAction(true);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Select the tech you want", ButtonHelperActionCards.getPlagiarizeButtons(activeGame, player));
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
            Button DoneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Click the names of the planets you wish to exhaust to pay the 5 influence", buttons);
            event.getMessage().delete().queue();
            //"saarHeroResolution_"
        } else if (buttonID.startsWith("arboHeroBuild_")) {
            ButtonHelperHeroes.resolveArboHeroBuild(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("saarHeroResolution_")) {
            ButtonHelperHeroes.resolveSaarHero(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("increaseTGonSC_")) {
            String sc = buttonID.replace("increaseTGonSC_", "");
            int scNum = Integer.parseInt(sc);
            LinkedHashMap<Integer, Integer> scTradeGoods = activeGame.getScTradeGoods();
            int tgCount = scTradeGoods.get(scNum);
            activeGame.setScTradeGood(scNum, (tgCount + 1));
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Added 1tg to SC #" + scNum + ". There are now " + (tgCount + 1) + " tgs on it.");
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
            String messageText = player.getRepresentation() + " explored " +
                "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tile.getPosition() + ")*:\n" +
                "> " + new ExpPlanet().displayExplore(cardID);
            new ExpPlanet().resolveExplore(event, cardID, tile, planetName, messageText, false, player, activeGame);
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
        } else if (buttonID.startsWith("assignDamage_")) {
            ButtonHelperModifyUnits.assignDamage(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("repairDamage_")) {
            ButtonHelperModifyUnits.repairDamage(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("refreshViewOfSystem_")) {
            String rest = buttonID.replace("refreshViewOfSystem_", "");
            String pos = rest.split("_")[0];
            Player p1 = activeGame.getPlayerFromColorOrFaction(rest.split("_")[1]);
            Player p2 = activeGame.getPlayerFromColorOrFaction(rest.split("_")[2]);
            String groundOrSpace = rest.split("_")[3];
            FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, 0, pos, event);
            MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
            List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeGame, pos, p1, p2, groundOrSpace);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
        } else if (buttonID.startsWith("getDamageButtons_")) {// "repealLaw_"
            String pos = buttonID.replace("getDamageButtons_", "");
            List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, activeGame.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " Use buttons to resolve", buttons);
         } else if (buttonID.startsWith("repealLaw_")) {// "repealLaw_"
            ButtonHelperActionCards.repealLaw(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("getRepairButtons_")) {
            String pos = buttonID.replace("getRepairButtons_", "");
            List<Button> buttons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, activeGame, activeGame.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " Use buttons to resolve", buttons);
        } else if (buttonID.startsWith("assignHits_")) {
            ButtonHelperModifyUnits.assignHits(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("seedySpace_")) {
            ButtonHelper.resolveSeedySpace(activeGame, buttonID, player, event);
        } else if (buttonID.startsWith("prophetsTears_")) {
            player.addExhaustedRelic("prophetstears");
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " Chose to exhaust The Prophets Tears");
            if (buttonID.contains("AC")) {
                String message = "";
                if (player.hasAbility("scheming")) {
                    activeGame.drawActionCard(player.getUserID());
                    activeGame.drawActionCard(player.getUserID());
                    message = ButtonHelper.getIdent(player) + " Drew 2 AC With Scheming. Please Discard An AC with the blue buttons";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                } else {
                    activeGame.drawActionCard(player.getUserID());
                    message = ButtonHelper.getIdent(player) + " Drew 1 AC";
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                }
                if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
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
            Button concludeRefreshing = Button.danger(finsFactionCheckerPrefix + "votes_" + votes, "Done readying planets.");
            voteActionRow.add(concludeRefreshing);
            String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("getAllTechOfType_")) {
            String techType = buttonID.replace("getAllTechOfType_", "");
            List<TechnologyModel> techs = Helper.getAllTechOfAType(activeGame, techType, player.getFaction(), player);
            List<Button> buttons = Helper.getTechButtons(techs, techType, player);
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
            MessageHelper.sendMessageToChannelWithButtons(channel, msg, ButtonHelper.getButtonsForRiftingUnitsInSystem(player, activeGame, tile));
        } else if (buttonID.startsWith("riftAllUnits_")) {
            ButtonHelper.riftAllUnitsButton(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("cabalVortextCapture_")) {
            ButtonHelperFactionSpecific.resolveVortexCapture(buttonID, player, activeGame, event);
        } else if (buttonID.startsWith("takeAC_")) {
            ButtonHelperFactionSpecific.mageon(buttonID, event, activeGame, player, ident, trueIdentity);
        } else if (buttonID.startsWith("spend_")) {
            String planetName = buttonID.substring(buttonID.indexOf("_") + 1);
            new PlanetExhaust().doAction(player, planetName, activeGame);
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
            String exhaustedMessage = event.getMessage().getContentRaw();
            if (!exhaustedMessage.contains("Click the names")) {
                exhaustedMessage = exhaustedMessage + ", exhausted "
                    + Helper.getPlanetRepresentation(planetName, activeGame);
            } else {
                exhaustedMessage = ident + " exhausted "
                    + Helper.getPlanetRepresentation(planetName, activeGame);
            }
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        } else if (buttonID.startsWith("finishTransaction_")) {
            String player2Color = buttonID.split("_")[1];
            Player player2 = activeGame.getPlayerFromColorOrFaction(player2Color);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAbilities.pillageCheck(player2, activeGame);
            ButtonHelper.checkTransactionLegality(activeGame, player, player2);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("sabotage_")) {
            String typeNName = buttonID.replace("sabotage_", "");
            String type = typeNName.substring(0, typeNName.indexOf("_"));
            String acName = typeNName.replace(type + "_", "");
            String message = "Cancelling the AC \"" + acName + "\" using ";
            Integer count = activeGame.getAllActionCardsSabod().get(acName);
            if(count == null){
                activeGame.setSpecificActionCardSaboCount(acName, 1);
            }else{
                activeGame.setSpecificActionCardSaboCount(acName, 1+count);
            }
            if (activeGame.getMessageIDsForSabo().contains(event.getMessageId())) {
                activeGame.removeMessageIDForSabo(event.getMessageId());
            }
            boolean sendReact = true;
            if ("empy".equalsIgnoreCase(type)) {
                message = message + "a Watcher mech! The Watcher should be removed now by the owner.";
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
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Someone clicked the Instinct Training button but did not have the tech.");
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
                    PlayAC.playAC(event, activeGame, player, saboID, activeGame.getActionsChannel(), activeGame.getGuild());
                } else {
                    message = "Tried to play a sabo but found none in hand.";
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "Someone clicked the AC sabo button but did not have a sabo in hand.");
                }
            }

            if (acName.contains("Rider") || acName.contains("Sanction")) {
                AgendaHelper.reverseRider("reverse_" + acName, event, activeGame, player, ident);
                //MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "Reversed the rider "+ acName);
            }
            if (sendReact) {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), message + "\n" + Helper.getGamePing(activeGame.getGuild(), activeGame));
            }
        } else if (buttonID.startsWith("reduceTG_")) {
            int tgLoss = Integer.parseInt(buttonID.replace("reduceTG_", ""));
            String message = ident + " reduced tgs by " + tgLoss + " (" + player.getTg() + "->"
                + (player.getTg() - tgLoss) + ")";
            if (tgLoss > player.getTg()) {
                message = "You dont have " + tgLoss + " tgs. No change made.";
            } else {
                player.setTg(player.getTg() - tgLoss);
            }
            String editedMessage = event.getMessage().getContentRaw() + " " + message;
            if (editedMessage.contains("Click the names")) {
                editedMessage = message;
            }
            event.getMessage().editMessage(editedMessage).queue();
        } else if (buttonID.startsWith("reduceComm_")) {
            int tgLoss = Integer.parseInt(buttonID.replace("reduceComm_", ""));
            String message = ident + " reduced comms by " + tgLoss + " (" + player.getCommodities() + "->"
                + (player.getCommodities() - tgLoss) + ")";

            if (tgLoss > player.getCommodities()) {
                message = "You dont have " + tgLoss + " comms. No change made.";
            } else {
                player.setCommodities(player.getCommodities() - tgLoss);
            }
            String editedMessage = event.getMessage().getContentRaw() + " " + message;

            if (editedMessage.contains("Click the names")) {
                editedMessage = message;
            }

            Leader playerLeader = player.getLeader("keleresagent").orElse(null);
            if (playerLeader != null && !playerLeader.isExhausted()) {
                playerLeader.setExhausted(true);
                StringBuilder messageText = new StringBuilder(player.getRepresentation())
                    .append(" exhausted ").append(Helper.getLeaderFullRepresentation(playerLeader));
                if (activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(player.getPrivateChannel(), messageText.toString());
                } else {
                    MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), messageText.toString());
                }
            }
            event.getMessage().editMessage(editedMessage).queue();

            // MessageHelper.sendMessageToChannel(event.getChannel(), message);resFrontier_
        } else if (buttonID.startsWith("resFrontier_")) {
            buttonID = buttonID.replace("resFrontier_", "");
            String[] stuff = buttonID.split("_");
            String cardChosen = stuff[0];
            String pos = stuff[1];
            String cardRefused = stuff[2];
            activeGame.addExplore(cardRefused);
            new ExpFrontier().expFrontAlreadyDone(event, activeGame.getTileByPosition(pos), activeGame, player, cardChosen);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("finishComponentAction_")) {
            String message = "Use buttons to end turn or do another action.";
            List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
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
        } else if (buttonID.startsWith("diplo_")) {
            ButtonHelper.resolveDiploPrimary(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("doneWithOneSystem_")) {
            String pos = buttonID.replace("doneWithOneSystem_", "");
            Tile tile = activeGame.getTileByPosition(pos);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "From system "
                + tile.getRepresentationForButtons(activeGame, player) + "\n" + event.getMessage().getContentRaw());
            String message = "Choose a different system to move from, or finalize movement.";
            activeGame.resetCurrentMovedUnitsFrom1System();
            List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeGame, event);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolveAgendaVote_")) {
            AgendaHelper.resolvingAnAgendaVote(buttonID, event, activeGame, player);
        } else if (buttonID.startsWith("bombardConfirm_")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.secondary(buttonID.replace("bombardConfirm_", ""), "Roll Bombardment"));
            String message = ButtonHelper.getTrueIdentity(player, activeGame)
                + " please declare what units are bombarding what planet before hitting this button (if you have two dreads and are splitting their bombardment across two planets, specify which planet the first one is hitting). The bot does not track this.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        } else if (buttonID.startsWith("combatRoll_")) {
            ButtonHelper.resolveCombatRoll(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("transitDiodes_")) {
            ButtonHelper.resolveTransitDiodesStep2(activeGame, player, event, buttonID);
        } else if (buttonID.startsWith("novaSeed_")) {
            new NovaSeed().secondHalfOfNovaSeed(player, event, activeGame.getTileByPosition(buttonID.split("_")[1]), activeGame);
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
        } else if (buttonID.startsWith("exhaustTech_")) {
            String tech = buttonID.replace("exhaustTech_", "");
            if (!"st".equals(tech)) {
                if ("bs".equals(tech)) {
                    ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeGame, event, player);
                }
                if ("td".equals(tech)) {
                    ButtonHelper.resolveTransitDiodesStep1(activeGame, player, event);
                }
                if ("aida".equals(tech) || "sar".equals(tech)) {
                    if (!activeGame.isFoWMode() && event.getMessageChannel() != activeGame.getActionsChannel()) {
                        String msg = " exhausted tech: " + Helper.getTechRepresentation(tech);
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
                }
                player.exhaustTech(tech);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), (player.getRepresentation() + " exhausted tech: " + Helper.getTechRepresentation(tech)));
                if ("pi".equals(tech)) {
                    List<Button> redistributeButton = new ArrayList<>();
                    Button redistribute = Button.success("redistributeCCButtons", "Redistribute CCs");
                    Button deleButton = Button.danger("FFCC_" + player.getFaction() + "_" + "deleteButtons", "Delete These Buttons");
                    redistributeButton.add(redistribute);
                    redistributeButton.add(deleButton);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), false) + " use buttons to redistribute", redistributeButton);
                }
            } else {
                String msg = " used tech: "
                    + Helper.getTechRepresentation(tech);
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
            ButtonHelperFactionSpecific.resolveLetnevMech(player, activeGame, buttonID, event);
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
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeGame, "resolveAgendaVote_outcomeTie*", null);
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
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeGame, finsFactionCheckerPrefix, buttonID);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("distinguished_")) {
            String voteMessage = "You added 5 votes to your total. Please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1);
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes, votes + 5);
            voteActionRow.add(Button.secondary("distinguishedReverse_" + votes, "Decrease Votes"));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("distinguishedReverse_")) {
            String voteMessage = "You subtracted 5 votes to your total. Please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1);
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes - 5, votes);
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
        } else if (buttonID.startsWith("cabalAgentCapture_")) {
            ButtonHelperAgents.resolveCabalAgentCapture(buttonID, player, activeGame, event);
        } else if (buttonID.startsWith("cabalRelease_")) {
            ButtonHelperFactionSpecific.resolveReleaseButton(player, activeGame, buttonID, event);
        } else if (buttonID.startsWith("getReleaseButtons")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " you can release units one at a time with the buttons",
                ButtonHelperFactionSpecific.getReleaseButtons(player, activeGame));
        } else if (buttonID.startsWith("arboAgentIn_")) {
            String pos = buttonID.substring(buttonID.indexOf("_") + 1);
            List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, activeGame, event, activeGame.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " select which unit you'd like to replace", buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("arboAgentPutShip_")) {
            ButtonHelperAgents.arboAgentPutShip(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("setAutoPassMedian_")) {
            String hours = buttonID.split("_")[1];
            int median = Integer.parseInt(hours);
            player.setAutoSaboPassMedian(median);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set median time to " + median + " hours");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("arboAgentOn_")) {
            String pos = buttonID.split("_")[1];
            String unit = buttonID.split("_")[2];
            List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(player, activeGame, event, activeGame.getTileByPosition(pos), unit);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " select which unit you'd like to place down", buttons);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("resolveWithNoEffect")) {
            String voteMessage = "Resolving agenda with no effect. Click the buttons for next steps.";
            Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda");
            Button proceedToStrategyPhase = Button.success("proceed_to_strategy", "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            List<Button> resActionRow = List.of(flipNextAgenda, proceedToStrategyPhase);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("outcome_")) {
            AgendaHelper.offerVoteAmounts(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("votes_")) {
            AgendaHelper.exhaustPlanetsForVoting(buttonID, event, activeGame, player, ident, buttonLabel, finsFactionCheckerPrefix);
        } else if (buttonID.startsWith("dacxive_")) {
            String planet = buttonID.replace("dacxive_", "");
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planet)), "infantry " + planet, activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(), ident + " placed 1 infantry on " + Helper.getPlanetRepresentation(planet, activeGame) + " via the tech Dacxive Animators");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("autoresolve_")) {
            String result = buttonID.substring(buttonID.indexOf("_") + 1);
            if ("manual".equalsIgnoreCase(result)) {
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
        } else if (buttonID.startsWith("rider_")) {
            AgendaHelper.placeRider(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("startToScuttleAUnit_")) {
            ButtonHelperActionCards.resolveScuttleStart(player, activeGame, event, buttonID);
         } else if (buttonID.startsWith("endScuttle_")) {
            ButtonHelperActionCards.resolveScuttleEnd(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("scuttleOn_")) {
            ButtonHelperActionCards.resolveScuttleRemoval(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("scuttleIn_")) {
            ButtonHelperActionCards.resolveScuttleTileSelection(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("startToScuttleAUnit_")) {
            ButtonHelperActionCards.resolveScuttleStart(player, activeGame, event, buttonID);
         } else if (buttonID.startsWith("endScuttle_")) {
            ButtonHelperActionCards.resolveScuttleEnd(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("scuttleOn_")) {
            ButtonHelperActionCards.resolveScuttleRemoval(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("scuttleIn_")) {
            ButtonHelperActionCards.resolveScuttleTileSelection(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("winnuHero_")) {
            ButtonHelperHeroes.resolveWinnuHeroSC(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("construction_")) {
            if (!player.getFollowedSCs().contains(4)) {
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 4, activeGame);
            }
            player.addFollowedSC(4);
            ButtonHelper.addReaction(event, false, false, "", "");
            String unit = buttonID.replace("construction_", "");
            String message = trueIdentity + " Click the name of the planet you wish to put your unit on for construction";
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
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, unit, "placeOneNDone_dontskip");
                if (!activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                }
            } else {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ident + " tgs increased by 1 (" + player.getTg() + "->" + (player.getTg() + 1) + ")");
                player.setTg(player.getTg() + 1);
                ButtonHelperAbilities.pillageCheck(player, activeGame);
                ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
            }
            event.getMessage().delete().queue();//"resolveReverse_"
        } else if (buttonID.startsWith("resolveReverse_")) {
            ButtonHelperActionCards.resolveReverse(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("winnuStructure_")) {
            String unit = buttonID.replace("winnuStructure_", "").split("_")[0];
            String planet = buttonID.replace("winnuStructure_", "").split("_")[1];
            new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planet)), unit + " " + planet, activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, activeGame));

        } else if (buttonID.startsWith("produceOneUnitInTile_")) {
            buttonID = buttonID.replace("produceOneUnitInTile_", "");
            String type = buttonID.split("_")[1];
            String pos = buttonID.split("_")[0];
            List<Button> buttons;
            buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos), type, "placeOneNDone_dontskip");
            String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("yinagent_")) {
            ButtonHelperAgents.yinAgent(buttonID, event, activeGame, player, ident, trueIdentity);
        } else if (buttonID.startsWith("resolveMaw")) {
            ButtonHelper.resolveMaw(activeGame, player, event);
        } else if (buttonID.startsWith("resolveCrownOfE")) {
            ButtonHelper.resolveCrownOfE(activeGame, player, event);
        } else if (buttonID.startsWith("deployMykoSD_")) {
            ButtonHelperFactionSpecific.deployMykoSD(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("jrResolution_")) {
            String faction2 = buttonID.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction2);
            Button sdButton = Button.success("jrStructure_sd", "Place A SD");
            sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
            Button pdsButton = Button.success("jrStructure_pds", "Place a PDS");
            pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
            Button tgButton = Button.success("jrStructure_tg", "Gain a tg");
            List<Button> buttons = new ArrayList<>();
            buttons.add(sdButton);
            buttons.add(pdsButton);
            buttons.add(tgButton);
            String msg = ButtonHelper.getTrueIdentity(p2, activeGame) + " Use buttons to decide what structure to build";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame), msg, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("ruins_")) {
            ButtonHelper.resolveWarForgeRuins(activeGame, buttonID, player, event);
        } else if (buttonID.startsWith("yssarilHeroRejection_")) {
            String playerFaction = buttonID.replace("yssarilHeroRejection_", "");
            Player notYssaril = activeGame.getPlayerFromColorOrFaction(playerFaction);
            if (notYssaril != null) {
                String message = Helper.getPlayerRepresentation(notYssaril, activeGame, activeGame.getGuild(), true)
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
                acButtons.add(Button.success("takeAC_" + acID + "_" + player.getFaction(), buttonLabel).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                acButtons.add(Button.danger("yssarilHeroRejection_" + player.getFaction(), "Reject " + buttonLabel + " and force them to discard of 3 random ACs"));
                String message = Helper.getPlayerRepresentation(yssaril, activeGame, activeGame.getGuild(), true) + " " + offerName + " has offered you the action card " + buttonLabel
                    + " for your Yssaril Hero play. Use buttons to accept or reject it";
                MessageHelper.sendMessageToChannelWithButtons(yssaril.getCardsInfoThread(), message, acButtons);
                String acStringID = "";
                for(String acStrId : player.getActionCards().keySet()){
                    if((player.getActionCards().get(acStrId)+"").equalsIgnoreCase(acID)){
                        acStringID = acStrId;
                    }
                }
                
                
                ActionCardModel ac = Mapper.getActionCard(acStringID);
                if(ac != null){
                    MessageHelper.sendMessageToChannel(yssaril.getCardsInfoThread(), "For your reference, the text of the AC offered reads as follows: \n"+ac.getText());
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
            ButtonHelperModifyUnits.genericPlaceUnit(buttonID, event, activeGame, player, ident, trueIdentity, finsFactionCheckerPrefix);
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
            potentialTech = ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(player, p2, potentialTech, activeGame);
            List<Button> buttons = ButtonHelperAbilities.getButtonsForPossibleTechForNekro(player, potentialTech, activeGame);
            if (buttons.size() > 0) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " get enemy tech using the buttons", buttons);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), trueIdentity + " no tech available to gain");
            }
         } else if (buttonID.startsWith("mentakCommander_")) {
            String color = buttonID.split("_")[1];
            Player p2 = activeGame.getPlayerFromColorOrFaction(color);
            List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(activeGame, player, p2);
            String message = ButtonHelper.getTrueIdentity(p2, activeGame)
                + " You have been hit with mentak commander. Please select the PN you would like to send";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), message, stuffToTransButtons);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Sent "+color+" the buttons for resolving mentak commander");
            ButtonHelper.deleteTheOneButton(event);
        } else if (buttonID.startsWith("mahactStealCC_")) {
            String color = buttonID.replace("mahactStealCC_", "");
            if (!player.getMahactCC().contains(color)) {
                player.addMahactCC(color);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " added a " + color + " CC to their fleet pool");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " already had a " + color + " CC in their fleet pool");
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
            buttons = Helper.getPlaceUnitButtons(event, player, activeGame, tile, "freelancers", "placeOneNDone_dontskip");
            String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tacticalActionBuild_")) {
            String pos = buttonID.replace("tacticalActionBuild_", "");
            List<Button> buttons= Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos), "tacticalAction", "place");
            String message = player.getRepresentation() + " Use the buttons to produce units. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("getModifyTiles")) {
            List<Button> buttons= ButtonHelper.getTilesToModify(player, activeGame, event);
            String message = player.getRepresentation() + " Use the buttons to select the tile in which you wish to modify units. ";
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
        } else if (buttonID.startsWith("genericModify_")) {
            String pos = buttonID.replace("genericModify_", "");
            Tile tile = activeGame.getTileByPosition(pos);
            ButtonHelper.offerBuildOrRemove(player, activeGame, event, tile);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("genericBuild_")) {
            String pos = buttonID.replace("genericBuild_", "");
            List<Button> buttons;
            buttons = Helper.getPlaceUnitButtons(event, player, activeGame, activeGame.getTileByPosition(pos), "genericBuild", "place");
            String message = player.getRepresentation() + " Use the buttons to produce units. ";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("starforgeTile_")) {
            ButtonHelperAbilities.starforgeTile(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("starforge_")) {
            ButtonHelperAbilities.starforge(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("getSwapButtons_")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Swap", ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, activeGame, true));
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
        } else if (buttonID.startsWith("checksNBalancesPt2_")) {
            new SCPick().resolvePt2ChecksNBalances(event, player, activeGame, buttonID);
        } else if (buttonID.startsWith("scPick_")) {
            Stats stats = new Stats();
            String num = buttonID.replace("scPick_", "");
            int scpick = Integer.parseInt(num);
            if(activeGame.getFactionsThatReactedToThis("Public Disgrace")!= null &&  activeGame.getFactionsThatReactedToThis("Public Disgrace").contains("_"+scpick)){
                for(Player p2: activeGame.getRealPlayers()){
                    if(p2 == player){
                        continue;
                    }
                    if(activeGame.getFactionsThatReactedToThis("Public Disgrace").contains(p2.getFaction())&&p2.getActionCards().keySet().contains("disgrace")){
                        PlayAC.playAC(event, activeGame, p2, "disgrace", activeGame.getMainGameChannel(), event.getGuild());
                        activeGame.setCurrentReacts("Public Disgrace", "");
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation()+" you have been public disgraced. If this is a mistake or the disgrace is sabod, feel free to pick the SC again. Otherwise, pick a different SC.");
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

            //            System.out.println("MILTY");
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
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true)
                + " Select which AC you would like to steal", buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("specialRex_")) {
            ButtonHelper.resolveSpecialRex(player, activeGame, buttonID, ident, event);
        } else if (buttonID.startsWith("doActivation_")) {
            String pos = buttonID.replace("doActivation_", "");
            ButtonHelper.resolveOnActivationEnemyAbilities(activeGame, activeGame.getTileByPosition(pos), player, false);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("ringTile_")) {
            String pos = buttonID.replace("ringTile_", "");
            activeGame.setActiveSystem(pos);
            List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeGame, event);
            MessageHelper.sendMessageToChannel(event.getChannel(), trueIdentity + " activated "
                + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player));

            List<Player> playersWithPds2 = new ArrayList<>();
            if (!activeGame.isFoWMode()) {
                playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, activeGame, pos);
                int abilities = ButtonHelper.resolveOnActivationEnemyAbilities(activeGame, activeGame.getTileByPosition(pos), player, true);
                if (abilities > 0 || activeGame.getL1Hero()) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Button.success(finsFactionCheckerPrefix + "doActivation_" + pos, "Confirm"));
                    buttons.add(Button.danger(finsFactionCheckerPrefix + "deleteButtons", "This activation was a mistake"));
                    String msg = ident + " You are about to automatically trigger some abilities by activating this system, are you sure you want to proceed?";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                }
                for (Player player_ : activeGame.getRealPlayers()) {
                    if (!activeGame.getL1Hero() && !player.getFaction().equalsIgnoreCase(player_.getFaction()) && !player_.isPlayerMemberOfAlliance(player)
                        && FoWHelper.playerHasUnitsInSystem(player_, activeGame.getTileByPosition(pos))) {
                        String msgA = player_.getRepresentation()
                            + " has units in the system and has a potential window to play ACs like forward supply base, possibly counterstroke, possibly Decoy Operation, possibly ceasefire. You can proceed and float them unless you think they are particularly relevant, or wish to offer a pleading window. ";
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msgA);
                    }
                }
            } else {
                List<Player> playersAdj = FoWHelper.getAdjacentPlayers(activeGame, pos, true);
                for (Player player_ : playersAdj) {
                    String playerMessage = Helper.getPlayerRepresentation(player_, activeGame, event.getGuild(), true) + " - System " + pos + " has been activated ";
                    MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, playerMessage);
                }
                ButtonHelper.resolveOnActivationEnemyAbilities(activeGame, activeGame.getTileByPosition(pos), player, false);
            }
            if (!activeGame.isFoWMode() && playersWithPds2.size() > 0 && !activeGame.getL1Hero()) {
                StringBuilder pdsMessage = new StringBuilder(trueIdentity + " this is a courtesy notice that the selected system is in range of space cannon units owned by");
                List<Button> buttons2 = new ArrayList<Button>();
                buttons2.add(Button.secondary("combatRoll_" + pos + "_space_spacecannonoffence", "Roll Space Cannon Offence"));
                buttons2.add(Button.danger("declinePDS", "Decline PDS"));
                for (Player playerWithPds : playersWithPds2) {
                    pdsMessage.append(" ").append(Helper.getPlayerRepresentation(playerWithPds, activeGame, activeGame.getGuild(), false));
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), pdsMessage.toString(), buttons2);
            }
            List<Button> button2 = ButtonHelper.scanlinkResolution(player, activeGame, event);
            List<Button> button3 = ButtonHelperAgents.getL1Z1XAgentButtons(activeGame, player);
            if (player.hasUnexhaustedLeader("l1z1xagent") && !button3.isEmpty() && !activeGame.getL1Hero()) {
                String msg = ButtonHelper.getTrueIdentity(player, activeGame) + " You can use buttons to resolve L1 Agent if you want";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, button3);
            }
            if (player.getTechs().contains("sdn") && !button2.isEmpty() && !activeGame.getL1Hero()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please resolve scanlink", button2);
                if (player.hasAbility("awaken")) {
                    ButtonHelper.resolveTitanShenanigansOnActivation(player, activeGame, activeGame.getTileByPosition(pos), event);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "\n\nUse buttons to select the first system you want to move from", systemButtons);
            } else {
                if (player.hasAbility("awaken")) {
                    ButtonHelper.resolveTitanShenanigansOnActivation(player, activeGame, activeGame.getTileByPosition(pos), event);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to select the first system you want to move from", systemButtons);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("genericRemove_")) {
            String pos = buttonID.replace("genericRemove_", "");
            activeGame.resetCurrentMovedUnitsFrom1System();
            activeGame.resetCurrentMovedUnitsFrom1TacticalAction();
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame, activeGame.getTileByPosition(pos), "Remove");
            activeGame.resetCurrentMovedUnitsFrom1System();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Chose to remove units from "
                + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to select the units you want to remove.", systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tacticalMoveFrom_")) {
            String pos = buttonID.replace("tacticalMoveFrom_", "");
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame, activeGame.getTileByPosition(pos), "Move");
            activeGame.resetCurrentMovedUnitsFrom1System();
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Chose to move from "
                + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player)
                + ". Use buttons to select the units you want to move.", systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolvePreassignment_")) {
            ButtonHelper.resolvePreAssignment(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("removePreset_")) {
            ButtonHelper.resolveRemovalOfPreAssignment(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("purge_Frags_")) {
            String typeNAmount = buttonID.replace("purge_Frags_", "");
            String type = typeNAmount.split("_")[0];
            int count = Integer.parseInt(typeNAmount.split("_")[1]);
            List<String> fragmentsToPurge = new ArrayList<>();
            ArrayList<String> playerFragments = player.getFragments();
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
            }
            String message = player.getRepresentation() + " purged fragments: "
                + fragmentsToPurge;
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else if (buttonID.startsWith("unitTactical")) {
            ButtonHelperModifyUnits.movingUnitsInTacticalAction(buttonID, event, activeGame, player, ident, buttonLabel);
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
         } else if (buttonID.startsWith("prismStep2_")) {
            new PlanetExhaustAbility().resolvePrismStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("prismStep3_")) {
            new PlanetExhaustAbility().resolvePrismStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("showDeck_")) {
            ButtonHelper.resolveDeckChoice(activeGame, event, buttonID, player);
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
            ButtonHelper.resolveSetupStep4And5(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("setupStep5_")) {
            ButtonHelper.resolveSetupStep4And5(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep2_")) {
            ButtonHelperActionCards.resolveSignalJammingStep2(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep3_")) {
            ButtonHelperActionCards.resolveSignalJammingStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("signalJammingStep4_")) {
            ButtonHelperActionCards.resolveSignalJammingStep4(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("reactorMeltdownStep2_")) {
            ButtonHelperActionCards.resolveReactorMeltdownStep2(player, activeGame, event, buttonID);
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
            ButtonHelperActionCards.resolveSalvageStep2(player, activeGame, event, buttonID);
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
        } else if (buttonID.startsWith("asnStep2_")) {
            ButtonHelperFactionSpecific.resolveASNStep2(activeGame, player, buttonID, event);
        } else if (buttonID.startsWith("unstableStep2_")) {
            ButtonHelperActionCards.resolveUnstableStep2(player, activeGame, event, buttonID);
         } else if (buttonID.startsWith("unstableStep3_")) {
            ButtonHelperActionCards.resolveUnstableStep3(player, activeGame, event, buttonID);
        } else if (buttonID.startsWith("spaceUnits_")) {
            ButtonHelperModifyUnits.spaceLandedUnits(buttonID, event, activeGame, player, ident, buttonLabel);
        } else if (buttonID.startsWith("reinforcements_cc_placement_")) {
            //String playerRep = player.getRepresentation();
            String planet = buttonID.replace("reinforcements_cc_placement_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeGame.getTile(tileID);
            if (tile == null) {
                tile = activeGame.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            if (Mapper.isColorValid(color)) {
                AddCC.addCC(event, color, tile);
            }
            //String message = playerRep + " Placed A CC From Reinforcements In The " + Helper.getPlanetRepresentation(planet, activeGame) + " system";
            ButtonHelper.sendMessageToRightStratThread(player, activeGame, messageID, "construction");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("placeHolderOfConInSystem_")) {
            //String playerRep = player.getRepresentation();
            String planet = buttonID.replace("placeHolderOfConInSystem_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeGame.getTile(tileID);
            if (tile == null) {
                tile = activeGame.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            for (Player p2 : activeGame.getRealPlayers()) {
                if (p2.getSCs().contains(4)) {
                    color = p2.getColor();
                }
            }

            if (Mapper.isColorValid(color)) {
                AddCC.addCC(event, color, tile);
            }
            //String message = playerRep + " Placed A " + StringUtils.capitalize(color) + " CC  In The " + Helper.getPlanetRepresentation(planet, activeGame) + " system due to use of Mahact agent";
            ButtonHelper.sendMessageToRightStratThread(player, activeGame, messageID, "construction");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("transactWith_")) {
            String faction = buttonID.replace("transactWith_", "");
            Player p2 = activeGame.getPlayerFromColorOrFaction(faction);
            List<Button> buttons;
            buttons = ButtonHelper.getStuffToTransButtons(activeGame, player, p2);
            String message = player.getRepresentation()
                + " Use the buttons to select what you want to transact";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("transact_")) {
            ButtonHelper.resolveSpecificTransButtons(activeGame, player, buttonID, event);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("transact_")) {
            ButtonHelper.resolveSpecificTransButtons(activeGame, player, buttonID, event);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("play_after_")) {
            String riderName = buttonID.replace("play_after_", "");
            ButtonHelper.addReaction(event, true, true, "Playing " + riderName, riderName + " Played");
            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeGame, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
            if("Keleres Rider".equalsIgnoreCase(riderName) || "Edyn Rider".equalsIgnoreCase(riderName)){
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
            }else{
                if (riderName.contains("Unity Algorithm")) {
                    player.exhaustTech("dsedyng");
                }
                MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your rider target", activeGame, player, riderButtons);
                if ("Keleres Xxcha Hero".equalsIgnoreCase(riderName)) {
                    Leader playerLeader = player.getLeader("keleresheroodlynn").orElse(null);
                    if (playerLeader != null) {
                        StringBuilder message = new StringBuilder(player.getRepresentation());
                        message.append(" played ");
                        message.append(Helper.getLeaderFullRepresentation(playerLeader));
                        boolean purged = player.removeLeader(playerLeader);
                        if (purged) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Leader Oodlynn has been purged");
                        } else {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Leader was not purged - something went wrong");
                        }
                    }
                }
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", activeGame, afterButtons, "after");
            }
            //"dspnedyn"
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
                MessageHelper.sendMessageToChannel(event.getChannel(), "Added ionstorm alpha to " + tile.getRepresentation());

            } else {
                String tokenFilename = Mapper.getTokenID("ionbeta");
                tile.addToken(tokenFilename, Constants.SPACE);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Added ionstorm beta to " + tile.getRepresentation());
            }

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("terraformPlanet_")) {
            ButtonHelperFactionSpecific.terraformPlanet(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("nanoforgePlanet_")) {
            String planet = buttonID.replace("nanoforgePlanet_", "");
            UnitHolder unitHolder = activeGame.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            planetReal.addToken("attachment_nanoforge.png");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Attached nanoforge to " + Helper.getPlanetRepresentation(planet, activeGame));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolvePNPlay_")) {
            String pnID = buttonID.replace("resolvePNPlay_", "");
            if (pnID.contains("ra_")) {
                String tech = pnID.replace("ra_", "");
                pnID = pnID.replace("_" + tech, "");
                String message = ident + " Acquired The Tech " + Helper.getTechRepresentation(AliasHandler.resolveTech(tech)) + " via Research Agreement";
                player.addTech(AliasHandler.resolveTech(tech));
                ButtonHelperCommanders.resolveNekroCommanderCheck(player, tech, activeGame);
                if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "jolnar", event);
                }
                if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "nekro", event);
                }
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
            }
            ButtonHelper.resolvePNPlay(pnID, player, activeGame, event);
            if (!"bmf".equalsIgnoreCase(pnID)) {
                event.getMessage().delete().queue();
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
        } else if (buttonID.startsWith("removeSleeperFromPlanet_")) {
            ButtonHelperAbilities.removeSleeper(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("replaceSleeperWith_")) {
            ButtonHelperAbilities.replaceSleeperWith(buttonID, event, activeGame, player, ident);
        } else if (buttonID.startsWith("topAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
            new PutAgendaTop().putTop(event, Integer.parseInt(agendaNumID), activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Put " + agendaNumID + " on the top of the agenda deck.");
        } else if (buttonID.startsWith("primaryOfWarfare")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "warfare");
            MessageChannel channel = ButtonHelper.getCorrectChannel(player, activeGame);
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        } else if (buttonID.startsWith("mahactCommander")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "mahactCommander");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.", buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("useTA_")) {
            String ta = buttonID.replace("useTA_", "") + "_ta";
            ButtonHelper.resolvePNPlay(ta, player, activeGame, event);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("removeCCFromBoard_")) {
            ButtonHelper.resolveRemovingYourCC(player, activeGame, event, buttonID);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("bottomAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
            new PutAgendaBottom().putBottom(event, Integer.parseInt(agendaNumID), activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Put " + agendaNumID + " on the bottom of the agenda deck.");
        } else if (buttonID.startsWith("agendaResolution_")) {
            AgendaHelper.resolveAgenda(activeGame, buttonID, event, actionsChannel);
        } else if (buttonID.startsWith("rollIxthian")) {
            if (activeGame.getSpeaker().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
                AgendaHelper.rollIxthian(activeGame);
            } else {
                Button ixthianButton = Button.success("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact")
                        .withEmoji(Emoji.fromFormatted(Emojis.MecatolRex));
                String msg = "The speaker should roll for Ixthain Artifact. Click this button to roll anyway!";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), msg, ixthianButton);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("applytempcombatmod__" + Constants.AC + "__")) {
            String acAlias = buttonID.substring(buttonID.lastIndexOf("__") + 2);
            TemporaryCombatModifierModel combatModAC = CombatModHelper.GetPossibleTempModifier(Constants.AC, acAlias,
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
                    String message = player.getRepresentation()
                        + " - no Public Objective scored.";
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = activeGame.isFoWMode() ? "No public objective scored" : null;
                    ButtonHelper.addReaction(event, false, false, reply, "");
                }
                case "warfareBuild" -> {
                    List<Button> buttons;
                    Tile tile = activeGame.getTile(AliasHandler.resolveTile(player.getFaction()));
                    if (tile == null) {
                        tile = ButtonHelper.getTileOfPlanetWithNoTrait(player, activeGame);
                    }
                    if (tile == null) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Could not find a HS, sorry bro");
                    }
                    buttons = Helper.getPlaceUnitButtons(event, player, activeGame, tile, "warfare", "place");
                    String message = player.getRepresentation()
                        + " Use the buttons to produce. Reminder that when following warfare, you can only use 1 dock in your home system. "
                        + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "getKeleresTechOptions"->{
                    ButtonHelperFactionSpecific.offerKeleresStartingTech(player, activeGame, event);
                }
                case "transaction" -> {
                    List<Button> buttons;
                    buttons = ButtonHelper.getPlayersToTransact(activeGame, player);
                    String message = player.getRepresentation()
                        + " Use the buttons to select which player you wish to transact with";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                }
                case "combatDrones" -> ButtonHelperModifyUnits.resolvingCombatDrones(event, activeGame, player, ident, buttonLabel);
                case "acquireATech" -> {

                    List<Button> buttons = new ArrayList<>();

                    Button propulsionTech = Button.primary(finsFactionCheckerPrefix + "getAllTechOfType_propulsion", "Get a Blue Tech");
                    propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
                    buttons.add(propulsionTech);

                    Button bioticTech = Button.success(finsFactionCheckerPrefix + "getAllTechOfType_biotic", "Get a Green Tech");
                    bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
                    buttons.add(bioticTech);

                    Button cyberneticTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic", "Get a Yellow Tech");
                    cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                    buttons.add(cyberneticTech);

                    Button warfareTech = Button.danger(finsFactionCheckerPrefix + "getAllTechOfType_warfare", "Get a Red Tech");
                    warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                    buttons.add(warfareTech);

                    Button unitupgradesTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade", "Get A Unit Upgrade Tech");
                    unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Emojis.UnitUpgradeTech));
                    buttons.add(unitupgradesTech);

                    String message = player.getRepresentation() + " What type of tech would you want?";
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
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
                case "titansCommanderUsage" -> ButtonHelperCommanders.titansCommanderUsage(buttonID, event, activeGame, player, ident);
                case "passForRound" -> {
                    player.setPassed(true);
                    String text = player.getRepresentation() + " PASSED";
                    MessageHelper.sendMessageToChannel(event.getChannel(), text);
                    TurnEnd.pingNextPlayer(event, activeGame, player);
                    ButtonHelper.updateMap(activeGame, event);
                }
                case "proceedToVoting" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Decided to skip waiting for afters and proceed to voting.");
                    try {
                        AgendaHelper.startTheVoting(activeGame, event);
                    } catch (Exception e) {
                        BotLogger.log(event, "Could not start the voting", e);
                    }

                    //event.getMessage().delete().queue();
                }
                case "drawAgenda_2" -> {
                    new DrawAgenda().drawAgenda(event, 2, activeGame, player);
                    event.getMessage().delete().queue();
                }
                case "nekroFollowTech" -> {
                    Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                    Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                    Button exhaust = Button.danger("nekroTechExhaust", "Exhaust Planets");
                    Button DoneGainingCC = Button.danger("deleteButtons_technology", "Done Gaining CCs");
                    String message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                    List<Button> buttons2 = List.of(exhaust);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), "Exhaust using this", buttons2);
                    } else {

                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), "Exhaust using this", buttons2);
                    }
                }
                case "diploRefresh2" -> {
                    if (!player.getFollowedSCs().contains(2)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 2, activeGame);
                    }
                    player.addFollowedSC(2);
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to ready";

                    List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, activeGame);
                    Button DoneRefreshing = Button.danger("deleteButtons_diplomacy", "Done Readying Planets");
                    buttons.add(DoneRefreshing);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(
                            player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                    if (player.hasAbility("peace_accords")) {
                        List<Button> buttons2 = ButtonHelperAbilities.getXxchaPeaceAccordsButtons(activeGame, player, event, finsFactionCheckerPrefix);
                        if (!buttons2.isEmpty()) {
                            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), trueIdentity + " use buttons to resolve peace accords", buttons2);
                        }
                    }
                }
                case "getOmenDice" -> {
                    ButtonHelperAbilities.offerOmenDiceButtons(activeGame, player);
                }
                case "leadershipExhaust" -> {
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
                    Button DoneExhausting = Button.danger("deleteButtons_leadership", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "nekroTechExhaust" -> {
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
                    Button DoneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                    event.getMessage().delete().queue();
                }
                case "endOfTurnAbilities" -> {
                    String msg = "Use buttons to do an end of turn ability";
                    List<Button> buttons = ButtonHelper.getEndOfTurnAbilities(player, activeGame);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                }
                case "redistributeCCButtons" -> {
                    String message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation() + ". Use buttons to gain CCs";

                    Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc", "Gain 1 Tactic CC");
                    Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc", "Gain 1 Strategy CC");
                    Button loseTactic = Button.danger(finsFactionCheckerPrefix + "decrease_tactic_cc", "Lose 1 Tactic CC");
                    Button loseFleet = Button.danger(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet CC");
                    Button loseStrat = Button.danger(finsFactionCheckerPrefix + "decrease_strategy_cc", "Lose 1 Strategy CC");

                    Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons", "Done Redistributing CCs");
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat, DoneGainingCC);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }

                    if(!activeGame.isFoWMode() && activeGame.isCustodiansScored() && !player.getRelics().contains("mawofworlds") &&  !player.getActionCards().containsKey("stability") ){
                        ButtonHelper.addReaction(event, false, false, "", "");
                    }
                }
                case "leadershipGenerateCCButtons" -> {
                    if (!player.getFollowedSCs().contains(1)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 1, activeGame);
                    }
                    player.addFollowedSC(1);
                    ButtonHelper.addReaction(event, false, false, "", "");
                    String message = trueIdentity + "! Your current CCs are " + player.getCCRepresentation()
                        + ". Use buttons to gain CCs";

                    Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc", "Gain 1 Tactic CC");
                    Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc", "Gain 1 Strategy CC");
                    // Button exhaust = Button.danger(finsFactionCheckerPrefix +
                    // "leadershipExhaust", "Exhaust Planets");
                    Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons_leadership", "Done Gaining CCs");
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "spyNetYssarilChooses" -> {ButtonHelperFactionSpecific.resolveSpyNetYssarilChooses(player, activeGame, event); }
                case "spyNetPlayerChooses" -> {ButtonHelperFactionSpecific.resolveSpyNetPlayerChooses(player, activeGame, event); }
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
                        ? "Drew 3 Actions Cards (Scheming) - please discard an Action Card from your hand"
                        : "Drew 2 Actions cards";
                    int count = hasSchemingAbility ? 3 : 2;
                    for (int i = 0; i < count; i++) {
                        activeGame.drawActionCard(player.getUserID());
                    }
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                    ButtonHelper.checkACLimit(activeGame, event, player);
                    ButtonHelper.addReaction(event, false, false, message, "");
                    if (hasSchemingAbility) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }
                }
                case "resolveMykoMech" -> {
                    ButtonHelperFactionSpecific.resolveMykoMech(player, activeGame);
                }
                case "offerNecrophage" -> {
                    ButtonHelperFactionSpecific.offerNekrophageButtons(player, activeGame, event);
                }
                case "resolveMykoCommander" -> {
                    ButtonHelperCommanders.mykoCommanderUsage(player, activeGame, event);
                }
                case "checkForAllACAssignments" -> {
                    ButtonHelperActionCards.checkForAllAssignmentACs(activeGame, player);
                }
                case "sc_draw_so" -> {
                    boolean used = addUsedSCPlayer(messageID + "so", activeGame, player, event, " Drew a " + Emojis.SecretObjective);
                    if (used) {
                        break;
                    }
                    String message = "Drew Secret Objective";
                    activeGame.drawSecretObjective(player.getUserID());
                    if (player.hasAbility("plausible_deniability")) {
                        activeGame.drawSecretObjective(player.getUserID());
                        message = message + ". Drew a second SO due to plausible deniability";
                    }
                    if (!player.getFollowedSCs().contains(8)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 8, activeGame);
                    }
                    player.addFollowedSC(8);
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
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, activeGame);
                    }
                    player.addFollowedSC(5);
                    player.setCommodities(player.getCommoditiesTotal());
                    if (player.getLeaderIDs().contains("mykomentoricommander") && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                    if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
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
                    LinkedHashMap<String, Player> players = activeGame.getPlayers();
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
                    String message = deductCC(player, event);
                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, activeGame);
                    }
                    player.addFollowedSC(5);
                    player.setCommodities(player.getCommoditiesTotal());
                    if (player.getLeaderIDs().contains("mykomentoricommander") && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                    if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                    ButtonHelper.addReaction(event, false, false, message, "");
                    ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "sc_follow_leadership" -> {
                    String message = player.getRepresentation() + " following.";
                    if (!player.getFollowedSCs().contains(1)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 1, activeGame);
                    }
                    player.addFollowedSC(1);
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "sc_leadership_follow" -> {
                    String message = player.getRepresentation() + " following.";
                    if (!player.getFollowedSCs().contains(1)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 1, activeGame);
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
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, activeGame);
                    }
                    player.addFollowedSC(5);
                    ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                    ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
                    if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander") && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                }
                case "sc_refresh_and_wash" -> {
                    if (player.hasAbility("military_industrial_complex")) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame)
                            + " since you cannot send players commodities due to your faction ability, washing here seems likely an error. Nothing has been processed as a result. Try a different route if this correction is wrong");
                        return;
                    }

                    if(!player.getPromissoryNotes().containsKey(player.getColor() + "_ta")){
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame)
                            + " since you do not currently hold your TA, washing here seems likely an error and will mess with the TA resolution. Nothing has been processed as a result. Try a different route of washing your comms if this correction is wrong");
                        return;
                    }
                    boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "Replenish and Wash");
                    if (used) {
                        break;
                    }
                    int commoditiesTotal = player.getCommoditiesTotal();
                    int tg = player.getTg();
                    player.setTg(tg + commoditiesTotal);
                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                    player.setCommodities(0);
                    for(Player p2: activeGame.getRealPlayers()){
                        if(p2.getSCs().contains(5) && p2.getCommodities() > 0){
                            p2.setTg(p2.getTg()+p2.getCommodities());
                            p2.setCommodities(0);
                            ButtonHelperAbilities.pillageCheck(p2, activeGame);
                            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame), ButtonHelper.getTrueIdentity(p2, activeGame)+ " your commodities got washed in the process of washing "+ButtonHelper.getIdentOrColor(player, activeGame));
                        }
                    }
                    if (!player.getFollowedSCs().contains(5)) {
                        ButtonHelperFactionSpecific.resolveVadenSCDebt(player, 5, activeGame);
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
                            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scnum, activeGame);
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
                    if (player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
                    }

                    ButtonHelperAbilities.pillageCheck(player, activeGame);
                    player.setCommodities(player.getCommoditiesTotal());
                    ButtonHelper.addReaction(event, false, false, " gained 3" + Emojis.getTGorNomadCoinEmoji(activeGame) + " and replenished commodities (" + player.getCommodities() + Emojis.comm + ")", "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                    ButtonHelperAgents.cabalAgentInitiation(activeGame, player);
                    if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander") && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                }
                case "score_imperial" -> {
                    if (player == null || activeGame == null) {
                        break;
                    }
                    if (!player.getSCs().contains(8)) {
                        MessageHelper.sendMessageToChannel(privateChannel, "Only the player who has "
                            + Helper.getSCRepresentation(activeGame, 8) + " can score the Imperial point");
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID + "score_imperial", activeGame, player, event, " scored Imperial");
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
                    MessageHelper.sendMessageToChannelWithPersistentReacts(actionsChannel, "Please indicate no whens again.", activeGame, whenButtons, "when");
                    List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", activeGame, afterButtons, "after");
                    // addPersistentReactions(event, activeMap, "when");
                    event.getMessage().delete().queue();
                }
                case "no_when" -> {
                    String message = activeGame.isFoWMode() ? "No whens" : null;
                    if(activeGame.getFactionsThatReactedToThis("noWhenThisAgenda") == null){
                        activeGame.setCurrentReacts("noWhenThisAgenda","");
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_after" -> {
                    String message = activeGame.isFoWMode() ? "No afters" : null;
                    if(activeGame.getFactionsThatReactedToThis("noAfterThisAgenda") == null){
                        activeGame.setCurrentReacts("noAfterThisAgenda","");
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_after_persistent" -> {
                    String message = activeGame.isFoWMode() ? "No afters (locked in)" : null;
                    activeGame.addPlayersWhoHitPersistentNoAfter(player.getFaction());
                    if(activeGame.getFactionsThatReactedToThis("noAfterThisAgenda") == null){
                        activeGame.setCurrentReacts("noAfterThisAgenda","");
                    }
                    //activeGame.getFactionsThatReactedToThis("noAfterThisAgenda").contains(player.getFaction())
                    if(!"".equalsIgnoreCase(activeGame.getFactionsThatReactedToThis("noAfterThisAgenda"))){
                        activeGame.setCurrentReacts("noAfterThisAgenda", activeGame.getFactionsThatReactedToThis("noAfterThisAgenda")+"_"+player.getFaction());
                    }else{
                        activeGame.setCurrentReacts("noAfterThisAgenda", player.getFaction());
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
                case "no_when_persistent" -> {
                    String message = activeGame.isFoWMode() ? "No whens (locked in)" : null;
                    activeGame.addPlayersWhoHitPersistentNoWhen(player.getFaction());
                    if(activeGame.getFactionsThatReactedToThis("noWhenThisAgenda") == null){
                        activeGame.setCurrentReacts("noWhenThisAgenda","");
                    }
                    if(!"".equalsIgnoreCase(activeGame.getFactionsThatReactedToThis("noWhenThisAgenda"))){
                        activeGame.setCurrentReacts("noWhenThisAgenda", activeGame.getFactionsThatReactedToThis("noWhenThisAgenda")+"_"+player.getFaction());
                    }else{
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
                        if (p.getSecrets().size() > 1  && !activeGame.isExtraSecretMode()) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please ensure everyone has discarded secrets before hitting this button. ");
                            return;
                        }
                    }
                    if (speaker == null) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please assign speaker before hitting this button.");
                        ButtonHelper.offerSpeakerButtons(activeGame, player);
                        return;
                    }
                    RevealStage1.revealTwoStage1(event, activeGame.getMainGameChannel());
                    ButtonHelper.startStrategyPhase(event, activeGame);
                    ButtonHelper.offerSetAutoPassOnSaboButtons(activeGame);
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
                    if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander") && !player.hasLeaderUnlocked("mykomentoricommander")) {
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
                    List<Button> buttons = new ArrayList<Button>();
                    MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player)+" Chose to Use Yin Spinner");
                    buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeGame, "2gf", "placeOneNDone_skipbuild"));
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
                        message = "Converted 1 Commodities to 1 tg (" + (player.getTg() - 1) + "->" + player.getTg() + ")";
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
                        message = " Gained 1 Commodity (" + (player.getCommodities() - 1) + "->" + player.getCommodities() + ")";
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                    event.getMessage().delete().queue();
                    if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                            ButtonHelper.getTrueIdentity(player, activeGame) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
                    }
                    if (player.getLeaderIDs().contains("mykomentoricommander") && !player.hasLeaderUnlocked("mykomentoricommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
                    }
                    if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }
                }
                case "startPlayerSetup" -> {
                    ButtonHelper.resolveSetupStep0(player, activeGame, event);
                }
                case "gain_1_comm_from_MahactInf" -> {
                    String message;
                    if (player.getCommodities() + 1 > player.getCommoditiesTotal()) {
                        player.setCommodities(player.getCommoditiesTotal());
                        message = " Gained No Commodities (at max already)";
                    } else {
                        player.setCommodities(player.getCommodities() + 1);
                        message = " Gained 1 Commodity (" + (player.getCommodities() - 1) + "->" + player.getCommodities() + ")";
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), ButtonHelper.getIdent(player) + message);
                    if (player.getLeaderIDs().contains("mykomentoricommander") && !player.hasLeaderUnlocked("mykomentoricommander")) {
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
                        ButtonHelper.addReaction(event, false, false, "Didn't have any comms/tg to spend, no AC drawn", "");
                        break;
                    }
                    for (int i = 0; i < count2; i++) {
                        activeGame.drawActionCard(player.getUserID());
                    }
                    ButtonHelper.checkACLimit(activeGame, event, player);
                    if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                    if (hasSchemingAbility) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                    }
                    String message = hasSchemingAbility
                        ? "Spent 1 " + commOrTg + " to draw " + count2
                            + " Action Card (Scheming) - please discard an Action Card from your hand"
                        : "Spent 1 " + commOrTg + " to draw " + count2 + " AC";
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
                        ButtonHelper.addReaction(event, false, false, "Didn't have any comms/tg to spend, no mech placed", "");
                        break;
                    }
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName, activeGame);
                    ButtonHelper.addReaction(event, false, false, "Spent 1 " + commOrTg + " for a mech on " + planetName, "");
                    event.getMessage().delete().queue();
                    if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                        String pF = player.getFactionEmoji();
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " Spent 1 " + commOrTg + " for a mech on " + planetName);
                    }
                }
                case "increase_strategy_cc" -> {
                    String originalCCs = player.getCCRepresentation();
                    player.setStrategicCC(player.getStrategicCC() + 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                            "Your current CCs are " + originalCCs + ". Use buttons to gain CCs", "CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation()
                                + ". Net gain of: 1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "exhauste6g0network" -> {
                    player.addExhaustedRelic("e6-g0_network");
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " Chose to exhaust e6-g0_network");
                    String message = "";
                    if (player.hasAbility("scheming")) {
                        activeGame.drawActionCard(player.getUserID());
                        activeGame.drawActionCard(player.getUserID());
                        message = ButtonHelper.getIdent(player) + " Drew 2 AC With Scheming. Please Discard An AC";
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
                            ACInfo.getDiscardActionCardButtons(activeGame, player, false));

                    } else {
                        activeGame.drawActionCard(player.getUserID());
                        ACInfo.sendActionCardInfo(activeGame, player, event);
                        message = ButtonHelper.getIdent(player) + " Drew 1 AC";
                    }
                    if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }

                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
                    ButtonHelper.checkACLimit(activeGame, event, player);
                    ButtonHelper.deleteTheOneButton(event);

                }
                case "increase_tactic_cc" -> {
                    String originalCCs = player.getCCRepresentation();
                    player.setTacticalCC(player.getTacticalCC() + 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                            "Your current CCs are " + originalCCs + ". Use buttons to gain CCs", "CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation()
                                + ". Net gain of: 1");
                    } else {

                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "increase_fleet_cc" -> {
                    String originalCCs = player.getCCRepresentation();
                    player.setFleetCC(player.getFleetCC() + 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                            "Your current CCs are " + originalCCs + ". Use buttons to gain CCs", "CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation()
                                + ". Net gain of: 1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_strategy_cc" -> {
                    String originalCCs = player.getCCRepresentation();
                    player.setStrategicCC(player.getStrategicCC() - 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                            "Your current CCs are " + originalCCs + ". Use buttons to gain CCs", "CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation()
                                + ". Net gain of: -1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_tactic_cc" -> {
                    String originalCCs = player.getCCRepresentation();
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                            "Your current CCs are " + originalCCs + ". Use buttons to gain CCs", "CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation()
                                + ". Net gain of: -1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_fleet_cc" -> {
                    String originalCCs = player.getCCRepresentation();
                    player.setFleetCC(player.getFleetCC() - 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                            "Your current CCs are " + originalCCs + ". Use buttons to gain CCs", "CCs have gone from " + originalCCs + " -> " + player.getCCRepresentation()
                                + ". Net gain of: -1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                            + player.getCCRepresentation() + ". Net gain of: " + netGain;
                    }
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
                    if (player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")) {
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
                case "miningInitiative" -> {
                    ButtonHelperActionCards.miningInitiative(player, activeGame, event);
                }
                case "forwardSupplyBase" -> {
                    ButtonHelperActionCards.resolveForwardSupplyBaseStep1(player, activeGame, event, buttonID);
                }
                case "economicInitiative" -> {
                    ButtonHelperActionCards.economicInitiative(player, activeGame, event);
                }
                case "getRepealLawButtons" -> {
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Use buttons to select Law to repeal", ButtonHelperActionCards.getRepealLawButtons(activeGame, player));
                }
                case "resolveCounterStroke" -> {
                    ButtonHelperActionCards.resolveCounterStroke(activeGame, player, event);
                }
                case "getDivertFundingButtons" -> {
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Use buttons to select tech to return", ButtonHelperActionCards.getDivertFundingLoseTechOptions(player, activeGame));
                }
                case "focusedResearch" -> {
                    ButtonHelperActionCards.resolveFocusedResearch(activeGame, player, buttonID, event);
                }
                case "resolveReparationsStep1" -> {
                    ButtonHelperActionCards.resolveReparationsStep1(player, activeGame, event, buttonID);
                }
                case "resolveSeizeArtifactStep1" -> {
                    ButtonHelperActionCards.resolveSeizeArtifactStep1(player, activeGame, event, buttonID);
                }
                case "resolveDiplomaticPressureStep1" -> {
                    ButtonHelperActionCards.resolveDiplomaticPressureStep1(player, activeGame, event, buttonID);
                }
                case "resolveImpersonation" -> {
                    ButtonHelperActionCards.resolveImpersonation(player, activeGame, event, buttonID);
                }
                case "resolveUprisingStep1" -> {
                    ButtonHelperActionCards.resolveUprisingStep1(player, activeGame, event, buttonID);
                }
                case "offerDeckButtons" -> {
                    ButtonHelper.offerDeckButtons(activeGame, event);
                }
                case "resolveAssRepsStep1" -> {
                    ButtonHelperActionCards.resolveAssRepsStep1(player, activeGame, event, buttonID);
                }
                case "resolveSignalJammingStep1" -> {
                    ButtonHelperActionCards.resolveSignalJammingStep1(player, activeGame, event, buttonID);
                }
                case "resolvePlagueStep1" -> {
                    ButtonHelperActionCards.resolvePlagueStep1(player, activeGame, event, buttonID);
                }
                case "resolveCrippleDefensesStep1" -> {
                    ButtonHelperActionCards.resolveCrippleDefensesStep1(player, activeGame, event, buttonID);
                }
                case "resolveInfiltrateStep1" -> {
                    ButtonHelperActionCards.resolveInfiltrateStep1(player, activeGame, event, buttonID);
                }
                case "resolveReactorMeltdownStep1" -> {
                    ButtonHelperActionCards.resolveReactorMeltdownStep1(player, activeGame, event, buttonID);
                }
                case "resolveSpyStep1" -> {
                    ButtonHelperActionCards.resolveSpyStep1(player, activeGame, event, buttonID);
                }
                case "resolveUnexpected" -> {
                    ButtonHelperActionCards.resolveUnexpectedAction(player, activeGame, event, buttonID);
                }
                case "resolveFrontline" -> {
                    ButtonHelperActionCards.resolveFrontlineDeployment(player, activeGame, event, buttonID);
                }
                case "resolveInsubStep1" -> {
                    ButtonHelperActionCards.resolveInsubStep1(player, activeGame, event, buttonID);
                }
                case "resolveUnstableStep1" -> {
                    ButtonHelperActionCards.resolveUnstableStep1(player, activeGame, event, buttonID);
                }
                case "resolveABSStep1" -> {
                    ButtonHelperActionCards.resolveABSStep1(player, activeGame, event, buttonID);
                }
                case "resolveWarEffort" -> {
                    ButtonHelperActionCards.resolveWarEffort(activeGame, player,event);
                }
                case "resolveInsiderInformation" -> {
                    ButtonHelperActionCards.resolveInsiderInformation(player, activeGame, event);
                }
                case "resolveSalvageStep1" -> {
                    ButtonHelperActionCards.resolveSalvageStep1(player, activeGame, event, buttonID);
                }
                case "resolveGhostShipStep1" -> {
                    ButtonHelperActionCards.resolveGhostShipStep1(player, activeGame, event, buttonID);
                }
                case "resolveTacticalBombardmentStep1" -> {
                    ButtonHelperActionCards.resolveTacticalBombardmentStep1(player, activeGame, event, buttonID);
                }
                case "resolveProbeStep1" -> {
                    ButtonHelperActionCards.resolveProbeStep1(player, activeGame, event, buttonID);
                }
                case "resolvePSStep1" -> {
                    ButtonHelperActionCards.resolvePSStep1(player, activeGame, event, buttonID);
                }
                case "resolveRally" -> {
                    ButtonHelperActionCards.resolveRally(activeGame, player, event);
                }
                case "resolveHarness" -> {
                    ButtonHelperActionCards.resolveHarnessEnergy(activeGame, player, event);
                }
                case "resolveSummit" -> {
                    ButtonHelperActionCards.resolveSummit(activeGame, player, event);
                }
                case "resolveRefitTroops" -> {
                    ButtonHelperActionCards.resolveRefitTroops(player, activeGame, event, buttonID, finsFactionCheckerPrefix);
                }
                case "industrialInitiative" -> {
                    ButtonHelperActionCards.industrialInitiative(player, activeGame, event);
                }
                case "confirm_cc" -> {
                    if (player.getMahactCC().size() > 0) {
                        ButtonHelper.addReaction(event, true, false, "Confirmed CCs: " + player.getTacticalCC() + "/" + player.getFleetCC() + "(+"
                            + player.getMahactCC().size() + ")/" + player.getStrategicCC(), "");
                    } else {
                        ButtonHelper.addReaction(event, true, false, "Confirmed CCs: " + player.getTacticalCC() + "/"
                            + player.getFleetCC() + "/" + player.getStrategicCC(), "");
                    }
                }
                case "draw_1_AC" -> {
                    activeGame.drawActionCard(player.getUserID());
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                    if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }
                    ButtonHelper.addReaction(event, true, false, "Drew 1 AC", "");
                    ButtonHelper.checkACLimit(activeGame, event, player);
                }
                case "drawStatusACs" -> ButtonHelper.drawStatusACs(activeGame, player, event);
                case "draw_1_ACDelete" -> {
                    activeGame.drawActionCard(player.getUserID());
                    if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                    ButtonHelper.addReaction(event, true, false, "Drew 1 AC", "");
                    event.getMessage().delete().queue();
                    ButtonHelper.checkACLimit(activeGame, event, player);
                }

                case "draw_2_ACDelete" -> {
                    activeGame.drawActionCard(player.getUserID());
                    activeGame.drawActionCard(player.getUserID());
                    if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
                    }
                    ACInfo.sendActionCardInfo(activeGame, player, event);
                    ButtonHelper.addReaction(event, true, false, "Drew 2 AC With Scheming. Please Discard An AC", "");
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, activeGame) + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(activeGame, player, false));

                    event.getMessage().delete().queue();
                    ButtonHelper.checkACLimit(activeGame, event, player);
                }
                case "pass_on_abilities" -> ButtonHelper.addReaction(event, false, false, " Is " + event.getButton().getLabel(), "");
                case "tacticalAction" -> {
                    if (player.getTacticalCC() < 1) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " does not have any tactical cc.");
                        return;
                    }
                    activeGame.setNaaluAgent(false);
                    activeGame.setL1Hero(false);
                    String message = "Doing a tactical action. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex. Mallice is in the corner";
                    List<Button> ringButtons = ButtonHelper.getPossibleRings(player, activeGame);
                    activeGame.resetCurrentMovedUnitsFrom1TacticalAction();
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
                }
                case "ChooseDifferentDestination" -> {
                    String message = "Choosing a different system to activate. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex. Mallice is in the corner";
                    List<Button> ringButtons = ButtonHelper.getPossibleRings(player, activeGame);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
                    event.getMessage().delete().queue();
                }
                case "componentAction" -> {
                    String message = "Use Buttons to decide what kind of component action you want to do";
                    List<Button> systemButtons = ButtonHelper.getAllPossibleCompButtons(activeGame, player, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);

                }
                case "drawRelicFromFrag" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Drew Relic");
                    DrawRelic.drawRelicAndNotify(player, event, activeGame);
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "startArbiter" -> {
                    ButtonHelper.resolveImperialArbiter(event, activeGame, player);
                }
                case "pay1tgforKeleres" -> {
                    ButtonHelperCommanders.pay1tgToUnlockKeleres(player, activeGame, event);
                }
                case "announceARetreat" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " announces a retreat");
                }
                case "declinePDS" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " officially declines to fire PDS");
                }
                case "startQDN" -> {
                    ButtonHelperFactionSpecific.resolveQuantumDataHubNodeStep1(player, activeGame, event, buttonID);
                }
                case "finishComponentAction" -> {
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "crownofemphidiaexplore" -> {
                    player.addExhaustedRelic("emphidia");
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), ident + " Exhausted crown of emphidia");
                    List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, activeGame);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore", buttons);
                }
                case "doneWithTacticalAction" -> {
                    if(!activeGame.getL1Hero()){
                        ButtonHelper.exploreDET(player, activeGame, event);
                    }
                    
                    if (!activeGame.isAbsolMode() && player.getRelics().contains("emphidia") && !player.getExhaustedRelics().contains("emphidia")) {
                        String message = trueIdentity + " You can use the button to explore using crown of emphidia";
                        List<Button> systemButtons2 = new ArrayList<>();
                        systemButtons2.add(Button.success("crownofemphidiaexplore", "Use Crown To Explore a Planet"));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                    }
                    if (activeGame.getNaaluAgent()) {
                        player = activeGame.getPlayer(activeGame.getActivePlayer());
                        activeGame.setNaaluAgent(false);
                    }
                    activeGame.setL1Hero(false);

                    String message = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                    MessageChannel channel = event.getMessageChannel();
                    if (activeGame.isFoWMode()) {
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannelWithButtons(channel, message, systemButtons);
                    event.getMessage().delete().queue();
                    //ButtonHelper.updateMap(activeMap, event);

                }
                case "doAnotherAction" -> {
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "ministerOfWar"-> {AgendaHelper.resolveMinisterOfWar(activeGame, player, event);}
                case "concludeMove" -> {
                    String message = "Moved all units to the space area.";
                    Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
                    List<Button> systemButtons;
                    if (activeGame.getMovedUnitsFromCurrentActivation().isEmpty() && !activeGame.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")) {
                        message = "Nothing moved. Use buttons to decide if you want to build (if you can) or finish the activation";
                        systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
                        systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeGame, event);
                    } else {
                        ButtonHelper.resolveEmpyCommanderCheck(player, activeGame, tile, event);
                        List<Button> empyButtons = new ArrayList<>();
                        if (!activeGame.getMovedUnitsFromCurrentActivation().isEmpty() && (tile.getUnitHolders().values().size() == 1) && player.hasUnexhaustedLeader("empyreanagent")) {
                            Button empyButton = Button.secondary("exhaustAgent_empyreanagent", "Use Empyrean Agent").withEmoji(Emoji.fromFormatted(Emojis.Empyrean));
                            empyButtons.add(empyButton);
                            empyButtons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " use button to exhaust Empy agent", empyButtons);
                        }
                        systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeGame, event);
                        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                            if (!"space".equalsIgnoreCase(unitHolder.getName())) {
                                continue;
                            }
                            List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, activeGame, tile);
                            if (players.size() > 0 && !player.getAllianceMembers().contains(players.get(0).getFaction())) {
                                Player player2 = players.get(0);
                                if (player2 == player) {
                                    player2 = players.get(1);
                                }

                                String threadName = ButtonHelper.combatThreadName(activeGame, player, player2, tile);
                                if (!activeGame.isFoWMode()) {
                                    ButtonHelper.makeACombatThread(activeGame, actionsChannel, player, player2, threadName, tile, event, "space");
                                } else {
                                    ButtonHelper.makeACombatThread(activeGame, player.getPrivateChannel(), player, player2, threadName, tile, event, "space");
                                    ButtonHelper.makeACombatThread(activeGame, player2.getPrivateChannel(), player2, player, threadName, tile, event, "space");
                                    for (Player player3 : activeGame.getRealPlayers()) {
                                        if (player3 == player2 || player3 == player) {
                                            continue;
                                        }
                                        if (!tile.getRepresentationForButtons(activeGame, player3).contains("(")) {
                                            continue;
                                        }
                                        ButtonHelper.makeACombatThread(activeGame, player3.getPrivateChannel(), player3, player3, threadName, tile, event, "space");
                                    }
                                }
                            }
                        }
                    }
                    if (systemButtons.size() == 2 || activeGame.getL1Hero()) {
                        systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeGame, event);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                    // ButtonHelper.updateMap(activeMap, event);

                }
                case "doneRemoving" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
                    event.getMessage().delete().queue();
                    ButtonHelper.updateMap(activeGame, event);
                }
                case "mitosisMech" -> ButtonHelperAbilities.resolveMitosisMech(buttonID, event, activeGame, player, ident, finsFactionCheckerPrefix);
                case "cardsInfo" -> {
                    String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " here is your cards info";
                    MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
                    SOInfo.sendSecretObjectiveInfo(activeGame, player);
                    ACInfo.sendActionCardInfo(activeGame, player);
                    PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
                }
                case "showGameAgain" -> ShowGame.simpleShowGame(activeGame, event);
                case "mitosisInf" -> ButtonHelperAbilities.resolveMitosisInf(buttonID, event, activeGame, player, ident);
                case "doneLanding" -> {
                    ButtonHelperModifyUnits.finishLanding(buttonID, event, activeGame, player);
                }
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
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(activeGame, null, "planetOutcomes", null);
                        } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                            outcomeActionRow = AgendaHelper.getSecretOutcomeButtons(activeGame, null, "outcome");
                        } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
                            outcomeActionRow = AgendaHelper.getStrategyOutcomeButtons(null, "outcome");
                        } else if (agendaDetails.contains("unit upgrade") || agendaDetails.contains("unit upgrade")) {
                            outcomeActionRow = AgendaHelper.getUnitUpgradeOutcomeButtons(activeGame, null, "outcome");
                        } else {
                            outcomeActionRow = AgendaHelper.getLawOutcomeButtons(activeGame, null, "outcome");
                        }
                        event.getMessage().delete().queue();
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
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
                    ButtonHelper.updateMap(activeGame, event);
                }
                case "getDiplomatsButtons" -> {
                    ButtonHelperAbilities.resolveGetDiplomatButtons(buttonID, event, activeGame, player);
                }
                case "gameEnd" -> {
                    GameEnd.secondHalfOfGameEnd(event, activeGame, true, true);
                    event.getMessage().delete().queue();
                }
                case "purgeHacanHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("hacanhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message + " - Leader " + "hacanhero" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Leader was not purged - something went wrong");
                    }
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeSardakkHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("sardakkhero");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message + " - Leader " + "sardakkhero" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Leader was not purged - something went wrong");
                    }
                    ButtonHelperHeroes.killShipsSardakkHero(player, activeGame, event);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                        ButtonHelper.getTrueIdentity(player, activeGame) + " All ships have been removed, continue to land troops.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "purgeKeleresAHero" -> {
                    Leader playerLeader = player.unsafeGetLeader("keleresherokuuasi");
                    StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                    boolean purged = player.removeLeader(playerLeader);
                    if (purged) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Leader " + "keleresherokuuasi" + " has been purged");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Leader was not purged - something went wrong");
                    }
                    new AddUnits().unitParsing(event, player.getColor(), activeGame.getTileByPosition(activeGame.getActiveSystem()), "2 cruiser, 1 flagship", activeGame);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " 2 cruisers and a flagship added.");
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "quash" -> {
                    int stratCC = player.getStrategicCC();
                    player.setStrategicCC(stratCC - 1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Quashed agenda. Strategic CCs went from " + stratCC + " -> " + (stratCC - 1));
                    ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
                    new RevealAgenda().revealAgenda(event, false, activeGame, activeGame.getMainGameChannel());
                    event.getMessage().delete().queue();
                }
                case "scoreAnObjective" -> {
                    List<Button> poButtons = TurnEnd.getScoreObjectiveButtons(event, activeGame, finsFactionCheckerPrefix);
                    poButtons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                    MessageChannel channel = event.getMessageChannel();
                    if (activeGame.isFoWMode()) {
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to score an objective", poButtons);
                }
                case "startChaosMapping" -> ButtonHelperFactionSpecific.firstStepOfChaos(activeGame, player, event);
                case "useLawsOrder" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), ident + " is paying 1 influence to ignore laws for the turn.");
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
                    Button DoneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Click the names of the planets you wish to exhaust to pay the 1 influence", buttons);
                    ButtonHelper.deleteTheOneButton(event);
                }
                case "orbitolDropFollowUp" -> ButtonHelperAbilities.oribtalDropFollowUp(buttonID, event, activeGame, player, ident);
                case "dropAMechToo" -> {
                    String message = "Please select the same planet you dropped the infantry on";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, "mech", "place");
                    buttons.add(Button.danger("orbitolDropExhaust", "Pay for mech"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    event.getMessage().delete().queue();
                }
                case "orbitolDropExhaust" -> ButtonHelperAbilities.oribtalDropExhaust(buttonID, event, activeGame, player, ident);
                case "dominusOrb" -> {
                    activeGame.setDominusOrb(true);
                    String purgeOrExhaust = "Purged ";
                    String relicId = "dominusorb";
                    player.removeRelic(relicId);
                    player.removeExhaustedRelic(relicId);
                    String relicName = Mapper.getRelic(relicId).getName();
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), purgeOrExhaust + Emojis.Relic + " relic: " + relicName);
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
                            for(String buttonString2 : activeGame.getSavedButtons()){
                                if(buttonString2.contains("Show Game")){
                                    showGame = true;
                                }
                            }
                            if (player != activeGame.getPlayerFromColorOrFaction(buttonString.split(";")[0]) && !showGame) {
                                MessageHelper.sendMessageToChannel(event.getChannel(),
                                    "You were not the player who pressed the latest button. Use /game undo if you truly want to undo " + activeGame.getLatestCommand());
                                return;
                            }
                        }
                    }
                    
                    GameSaveLoadManager.undo(activeGame, event);
                    
                    if ("action".equalsIgnoreCase(activeGame.getCurrentPhase())) {
                        event.getMessage().delete().queue();
                    }
                }
                case "getDiscardButtonsACs" -> {
                    String msg = trueIdentity + " use buttons to discard";
                    List<Button> buttons = ACInfo.getDiscardActionCardButtons(activeGame, player, false);
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
                }
                case "eraseMyRiders" -> {
                    AgendaHelper.reverseAllRiders(event, activeGame, player);
                }
                case "eraseMyVote" -> {
                    String pfaction = player.getFaction();
                    if (activeGame.isFoWMode()) {
                        pfaction = player.getColor();
                    }
                    AgendaHelper.eraseVotesOfFaction(activeGame, pfaction);
                    String eraseMsg = "Erased previous votes made by " + player.getFactionEmoji() + "\n\n" + AgendaHelper.getSummaryOfVotes(activeGame, true);
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), eraseMsg);
                    Button Vote = Button.success(finsFactionCheckerPrefix + "vote", StringUtils.capitalize(player.getFaction()) + " Choose To Vote");
                    Button Abstain = Button.danger(finsFactionCheckerPrefix + "resolveAgendaVote_0", StringUtils.capitalize(player.getFaction()) + " Choose To Abstain");
                    Button ForcedAbstain = Button.secondary("forceAbstainForPlayer_" + player.getFaction(), "(For Others) Abstain for this player");

                    String buttonMsg = "Use buttons to vote again. Reminder that this erasing of old votes did not refresh any planets.";
                    List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
                    MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), buttonMsg, buttons);
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
                        Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                        Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                        Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                        Button DoneGainingCC = Button.danger("deleteButtons_explore", "Done Gaining CCs");
                        List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
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

    private void deleteButtons(ButtonInteractionEvent event, String buttonID, String buttonLabel, Game activeGame, Player player, MessageChannel actionsChannel, String trueIdentity) {
        buttonID = buttonID.replace("deleteButtons_", "");
        String editedMessage = event.getMessage().getContentRaw();
        if (("Done Gaining CCs".equalsIgnoreCase(buttonLabel)
            || "Done Redistributing CCs".equalsIgnoreCase(buttonLabel)) && editedMessage.contains("CCs have gone from")) {

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
                                "# " + ButtonHelper.getTrueIdentity(player, activeGame) + " heads up, bot thinks you should have gained " + properGain + " cc due to: " + reasons);
                        }
                    }
                }
            }

            if ("Done Redistributing CCs".equalsIgnoreCase(buttonLabel)) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is " + finalCCs);
            } else {
                if ("leadership".equalsIgnoreCase(buttonID)) {
                    String message = playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is " + finalCCs;
                    ButtonHelper.sendMessageToRightStratThread(player, activeGame, message, "leadership");
                } else {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), playerRep + " Final CC Allocation Is " + finalCCs);
                }

            }

        }
        if (("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
            || "Done Producing Units".equalsIgnoreCase(buttonLabel))
            && !event.getMessage().getContentRaw().contains("Click the names of the planets you wish")) {

            ButtonHelper.sendMessageToRightStratThread(player, activeGame, editedMessage, buttonID);
            if ("Done Producing Units".equalsIgnoreCase(buttonLabel)) {
                String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
                if (player.hasTechReady("sar") && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sar = Button.danger("exhaustTech_sar", "Exhaust Self Assembly Routines");
                    buttons.add(sar);
                }
                if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "titanscommander") && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sar2 = Button.success("titansCommanderUsage", "Use Titans Commander To Gain a TG");
                    buttons.add(sar2);
                }
                if (player.hasTechReady("aida") && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
                    buttons.add(aiDEVButton);
                }
                if (player.hasTechReady("st") && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button sarweenButton = Button.danger("exhaustTech_st", "Use Sarween");
                    buttons.add(sarweenButton);
                }
                if (player.hasUnexhaustedLeader("winnuagent") && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button winnuButton = Button.danger("exhaustAgent_winnuagent", "Use Winnu Agent").withEmoji(Emoji.fromFormatted(Emojis.Winnu));
                    buttons.add(winnuButton);
                }
                if (player.hasLeaderUnlocked("hacanhero") && !"muaatagent".equalsIgnoreCase(buttonID) && !"arboHeroBuild".equalsIgnoreCase(buttonID)) {
                    Button hacanButton = Button.danger("purgeHacanHero", "Purge Hacan Hero").withEmoji(Emoji.fromFormatted(Emojis.Hacan));
                    buttons.add(hacanButton);
                }
                Button DoneExhausting;
                if (!buttonID.contains("deleteButtons")) {
                    DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
                } else {
                    DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
                }
                ButtonHelper.updateMap(activeGame, event);
                buttons.add(DoneExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }
        }
        if ("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)) {
            if(player.hasTech("asn") && (buttonID.contains("tacticalAction")||buttonID.contains("warfare"))){
                ButtonHelperFactionSpecific.offerASNButtonsStep1(activeGame, player, buttonID);
            } 
            if (buttonID.contains("tacticalAction")) {
                ButtonHelper.exploreDET(player, activeGame, event);
                List<Button> systemButtons2 = new ArrayList<>();
                if (!activeGame.isAbsolMode() && player.getRelics().contains("emphidia") && !player.getExhaustedRelics().contains("emphidia")) {
                    String message = trueIdentity + " You can use the button to explore using crown of emphidia";
                    systemButtons2.add(Button.success("crownofemphidiaexplore", "Use Crown To Explore a Planet"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                systemButtons2 = new ArrayList<>();
                if (player.hasUnexhaustedLeader("sardakkagent")) {
                    String message = trueIdentity + " You can use the button to do sardakk agent";
                    systemButtons2.addAll(ButtonHelperAgents.getSardakkAgentButtons(activeGame, player));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }
                if (player.hasUnexhaustedLeader("nomadagentmercer")) {
                    String message = trueIdentity + " You can use the button to do General Mercer";
                    systemButtons2.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(activeGame, player));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                }

                if (activeGame.getNaaluAgent()) {
                    player = activeGame.getPlayer(activeGame.getActivePlayer());
                    activeGame.setNaaluAgent(false);
                }

                String message = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Use buttons to end turn or do another action.";
                List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, systemButtons);
                player.resetOlradinPolicyFlags();
            }
        }
        if ("diplomacy".equalsIgnoreCase(buttonID)) {
            ButtonHelper.sendMessageToRightStratThread(player, activeGame, editedMessage, "diplomacy", null);
        }
        if ("spitItOut".equalsIgnoreCase(buttonID)) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), editedMessage);
        }
        event.getMessage().delete().queue();
    }

    public boolean addUsedSCPlayer(String messageID, Game activeGame, Player player, @NotNull ButtonInteractionEvent event, String defaultText) {
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
        //String messageId = mainMessage.getId();
        //RestAction<Message> messageRestAction = event.getChannel().retrieveMessageById(messageId);
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
            if(buttonID.contains("no_after")){
                if(activeGame.getFactionsThatReactedToThis("noAfterThisAgenda").contains(player.getFaction())){
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
                if (reaction != null){
                    factionReacted = true;
                }
            }
            if(buttonID.contains("no_when")){
                if(activeGame.getFactionsThatReactedToThis("noWhenThisAgenda").contains(player.getFaction())){
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
                if (reaction != null){
                    factionReacted = true;
                }
            }
            if (factionReacted || (activeGame.getFactionsThatReactedToThis(messageId) != null && activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction()))){
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
            
            if ((activeGame.getFactionsThatReactedToThis(messageId) != null && activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction()))){
                matchingFactionReactions++;
            }
        }
        int numberOfPlayers = activeGame.getRealPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            Message mainMessage = activeGame.getMainGameChannel().retrieveMessageById(messageId).completeAfter(100, TimeUnit.MILLISECONDS);
            mainMessage.reply("All players have indicated 'No Sabotage'").queueAfter(1, TimeUnit.SECONDS);
            if (activeGame.getMessageIDsForSabo().contains(messageId)) {
                activeGame.removeMessageIDForSabo(messageId);
            }
        }
    }

    public static boolean checkForASpecificPlayerReact(String messageId, Player player, Game activeGame) {
        
        activeGame.setShushing(false);
        try {
            if(activeGame.getFactionsThatReactedToThis(messageId) != null && activeGame.getFactionsThatReactedToThis(messageId).contains(player.getFaction())){
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
                if(reaction != null){
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
            case Constants.SC_FOLLOW, "sc_no_follow", "sc_refresh", "sc_refresh_and_wash", "trade_primary", "sc_ac_draw", "sc_draw_so", "sc_trade_follow", "sc_leadership_follow" -> {
                if (activeGame.isFoWMode()) {
                    event.getInteraction().getMessage().reply("All players have reacted to this Strategy Card")
                        .queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage("All players have reacted to this Strategy Card").queueAfter(10, TimeUnit.SECONDS);
                    if (guildMessageChannel instanceof ThreadChannel)
                        ((ThreadChannel) guildMessageChannel).getManager().setArchived(true).queueAfter(5, TimeUnit.MINUTES);
                }
            }
            case "no_when", "no_when_persistent" -> event.getInteraction().getMessage().reply("All players have indicated 'No Whens'").queueAfter(1, TimeUnit.SECONDS);
            case "no_after", "no_after_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Afters'").queue();
                AgendaHelper.startTheVoting(activeGame, event);
                event.getMessage().delete().queue();

            }
            case "no_sabotage" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Sabotage'").queueAfter(1, TimeUnit.SECONDS);
                if (activeGame.getMessageIDsForSabo().contains(event.getMessageId())) {
                    activeGame.removeMessageIDForSabo(event.getMessageId());
                }
            }

            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> {
                String message2 = "All players have indicated scoring. Flip the relevant PO using the buttons. This will automatically run status clean-up if it has not been run already.";
                Button drawStage2 = Button.success("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Button.success("reveal_stage_1", "Reveal Stage 1");
                // Button runStatusCleanup = Button.primary("run_status_cleanup", "Run Status
                // Cleanup");
                List<Button> buttons = List.of(drawStage1, drawStage2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                event.getMessage().delete().queueAfter(20, TimeUnit.SECONDS);
            }
            case "pass_on_abilities" -> {
                if (activeGame.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Button.primary("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now", buttons);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getGamePing(event.getGuild(), activeGame)
                        + " All players have indicated completion of status phase. Proceed to Strategy Phase.");
                    StartPhase.startPhase(event, activeGame, "strategy");
                }
            }
            case "redistributeCCButtons" ->{
                if (activeGame.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Button.primary("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now", buttons);
                }
            }
        }
    }
}
