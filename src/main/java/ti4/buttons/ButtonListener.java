package ti4.buttons;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.*;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.MapGenerator;
import ti4.MessageListener;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.agenda.PutAgendaBottom;
import ti4.commands.agenda.PutAgendaTop;
import ti4.commands.agenda.RevealAgenda;

import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.commands.cardsac.DiscardACRandom;
import ti4.map.UnitHolder;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.DealSOToAll;
import ti4.commands.cardsso.DiscardSO;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.explore.DrawRelic;
import ti4.commands.explore.ExpFrontier;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.game.StartPhase;
import ti4.commands.game.Swap;
import ti4.commands.uncategorized.ShowGame;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.player.Stats;
import ti4.commands.player.SCPick;
import ti4.commands.player.SCPlay;
import ti4.commands.player.Turn;
import ti4.helpers.Constants;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.map.Tile;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import ti4.generator.GenerateTile;
import ti4.generator.Mapper;

public class ButtonListener extends ListenerAdapter {
    public static final HashMap<Guild, HashMap<String, Emoji>> emoteMap = new HashMap<>();
    private static final HashMap<String, Set<Player>> playerUsedSC = new HashMap<>();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!MapGenerator.readyToReceiveCommands) {
            event.reply("Failed to press button. Please try again in a moment. The bot is rebooting.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        String id = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), id, "button", "no sub command");
        String buttonID = event.getButton().getId();
        String buttonLabel = event.getButton().getLabel();

        String lastchar = StringUtils.right(buttonLabel, 2).replace("#", "");
        String lastcharMod = StringUtils.right(buttonID, 1);
        if (buttonID == null) {
            event.getChannel().sendMessage("Button command not found").queue();
            return;
        }
        // BotLogger.log(event, ""); //TEMPORARY LOG ALL BUTTONS

        String messageID = event.getMessage().getId();

        String gameName = event.getChannel().getName();
        gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Game activeGame = GameManager.getInstance().getGame(gameName);
        Player player = activeGame.getPlayer(id);
        player = Helper.getGamePlayer(activeGame, player, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
            return;
        }

        MessageChannel privateChannel = event.getChannel();
        if (activeGame.isFoWMode()) {
            if (player.getPrivateChannel() == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Private channels are not set up for this game. Messages will be suppressed.");
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
        for (TextChannel textChannel_ : MapGenerator.jda.getTextChannels()) {
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
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "To " + Helper.getFactionIconFromDiscord(player.getFaction())
                        + ": you are not the faction who these buttons are meant for.");
                return;
            }
        }

        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
        String trueIdentity = Helper.getPlayerRepresentation(player, activeGame, event.getGuild(), true);
        String ident = Helper.getFactionIconFromDiscord(player.getFaction());
        try {
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
                        return;
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
            } else if (buttonID.startsWith("umbatTile_")) {
                ButtonHelperFactionSpecific.umbatTile(buttonID, event, activeGame, player, ident);
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
                new Swap().secondHalfOfSwap(activeGame, player, Helper.getPlayerFromColorOrFaction(activeGame, faction), event.getUser(), event);
            } else if (buttonID.startsWith("yinHeroInfantry_")) {
                ButtonHelperFactionSpecific.lastStepOfYinHero(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("arcExp_")) {
                ButtonHelper.resolveArcExpButtons(activeGame, player, buttonID, event, trueIdentity);
            } else if (buttonID.startsWith("cabalHeroTile_")) {
                ButtonHelperFactionSpecific.executeCabalHero(buttonID, player, activeGame, event);
            } else if (buttonID.startsWith("creussIFFStart_")) {
                ButtonHelperFactionSpecific.resolveCreussIFFStart(activeGame, player, buttonID, ident, event);
            } else if (buttonID.startsWith("creussIFFResolve_")) {
                ButtonHelperFactionSpecific.resolveCreussIFF(activeGame, player, buttonID, ident, event);
            } else if (buttonID.startsWith("acToSendTo_")) {
                ButtonHelperFactionSpecific.lastStepOfYinHero(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("yinHeroPlanet_")) {
                String planet = buttonID.replace("yinHeroPlanet_", "");
                MessageHelper.sendMessageToChannel(event.getChannel(), trueIdentity + " Chose to invade " + Helper.getPlanetRepresentation(planet, activeGame));
                List<Button> buttons = new ArrayList<>();
                for (int x = 1; x < 4; x++) {
                    buttons.add(Button.success(finsFactionCheckerPrefix + "yinHeroInfantry_" + planet + "_" + x, "Land " + x + " infantry")
                        .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry"))));
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Use buttons to select how many infantry you'd like to land on the planet", buttons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("yinHeroTarget_")) {
                String faction = buttonID.replace("yinHeroTarget_", "");
                List<Button> buttons = new ArrayList<>();
                Player target = Helper.getPlayerFromColorOrFaction(activeGame, faction);
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
            } else if (buttonID.startsWith("hacanAgentRefresh_")) {
                ButtonHelperFactionSpecific.hacanAgentRefresh(buttonID, event, activeGame, player, ident, trueIdentity);
            } else if (buttonID.startsWith("retreatGroundUnits_")) {
                ButtonHelperModifyUnits.retreatGroundUnits(buttonID, event, activeGame, player, ident, buttonLabel);
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
                String pos = buttonID.replace("retreat_", "");
                String message = trueIdentity + " Use buttons to select a system to retreat too. Warning: bot does not know what the valid retreat tiles are, you will need to verify these.";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ButtonHelperModifyUnits.getRetreatSystemButtons(player, activeGame, pos));
            } else if (buttonID.startsWith("exhaustAgent_")) {
                ButtonHelperFactionSpecific.exhaustAgent(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("sardakkcommander_")) {
                ButtonHelperFactionSpecific.resolveSardakkCommander(activeGame, player, buttonID, event, ident);
            } else if (buttonID.startsWith("peaceAccords_")) {
                ButtonHelperFactionSpecific.resolvePeaceAccords(buttonID, ident, player, activeGame, event);
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
                    return;
                }
            } else if (buttonID.startsWith(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)) {
                String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
                if (!player.getSCs().contains(3)) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Only the player who played Politics can assign Speaker");
                    return;
                }
                if (activeGame != null && !activeGame.isFoWMode()) {
                    for (Player player_ : activeGame.getPlayers().values()) {
                        if (player_.getFaction().equals(faction)) {
                            activeGame.setSpeaker(player_.getUserID());
                            String message = Emojis.SpeakerToken + " Speaker assigned to: "
                                + Helper.getPlayerRepresentation(player_, activeGame);
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
                    scnum = Integer.parseInt(lastcharMod);
                } catch (NumberFormatException e) {
                    setstatus = false;
                }
                if (setstatus) {
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
                Emoji emojiToUse = Emoji.fromFormatted(Helper.getFactionIconFromDiscord(player.getFaction()));

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
                        ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(player, activeGame, event);
                    }
                    String message = deductCC(player, event);

                    int scnum = 1;
                    boolean setstatus = true;
                    try {
                        scnum = Integer.parseInt(lastcharMod);
                    } catch (NumberFormatException e) {
                        setstatus = false;
                    }
                    if (setstatus) {
                        player.addFollowedSC(scnum);
                    }
                    ButtonHelper.addReaction(event, false, false, message, "");
                }
            } else if (buttonID.startsWith("sc_no_follow_")) {
                int scnum2 = 1;
                boolean setstatus = true;
                try {
                    scnum2 = Integer.parseInt(lastcharMod);
                } catch (NumberFormatException e) {
                    setstatus = false;
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
                new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeGame), info[1], info[2], player,
                    false, activeGame, 1, false);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("distant_suns_")) {
                ButtonHelperFactionSpecific.distantSuns(buttonID, event, activeGame, player, ident);
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
                Tile tile = Helper.getTileFromPlanet(planetName, activeGame);
                String messageText = Helper.getPlayerRepresentation(player, activeGame) + " explored " +
                    "Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile " + tile.getPosition() + ")*:\n" +
                    "> " + new ExpPlanet().displayExplore(cardID);
                new ExpPlanet().resolveExplore(event, cardID, tile, planetName, messageText, false, player,
                    activeGame);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("refresh_")) {
                String planetName = buttonID.replace("refresh_", "");
                new PlanetRefresh().doAction(player, planetName, activeGame);
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
                Player p1 = Helper.getPlayerFromColorOrFaction(activeGame, rest.split("_")[1]);
                Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, rest.split("_")[2]);
                String groundOrSpace = rest.split("_")[3];
                File systemWithContext = GenerateTile.getInstance().saveImage(activeGame, 0, pos, event);
                MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
                List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeGame, pos, p1, p2, groundOrSpace);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
            } else if (buttonID.startsWith("getDamageButtons_")) {
                String pos = buttonID.replace("getDamageButtons_", "");
                List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, activeGame, activeGame.getTileByPosition(pos));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " Use buttons to resolve", buttons);
            } else if (buttonID.startsWith("getRepairButtons_")) {
                String pos = buttonID.replace("getRepairButtons_", "");
                List<Button> buttons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, activeGame, activeGame.getTileByPosition(pos));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " Use buttons to resolve", buttons);
            } else if (buttonID.startsWith("assignHits_")) {
                ButtonHelperModifyUnits.assignHits(buttonID, event, activeGame, player, ident, buttonLabel);
            } else if (buttonID.startsWith("biostimsReady_")) {
                ButtonHelper.bioStimsReady(activeGame, event, player, buttonID);
                event.getMessage().delete().queue();
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
                List<TechnologyModel> techs = Helper.getAllTechOfAType(techType, player.getFaction(), player);
                List<Button> buttons = Helper.getTechButtons(techs, techType, player);
                buttons.add(Button.secondary("acquireATech", "Get Tech of a Different Type"));
                String message = Helper.getPlayerRepresentation(player, activeGame) + " Use the buttons to get the tech you want";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("getTech_")) {
                String tech = buttonID.replace("getTech_", "");
                String message = ident + " Acquired The Tech " + Helper.getTechRepresentation(AliasHandler.resolveTech(tech));
                TechnologyModel techM = Mapper.getTechs().get(AliasHandler.resolveTech(tech));
                if (techM != null && techM.getRequirements() != null && techM.getRequirements().length() > 1) {
                    if (player.getLeaderIDs().contains("zealotscommander") && !player.hasLeaderUnlocked("zealotscommander")) {
                        ButtonHelper.commanderUnlockCheck(player, activeGame, "zealots", event);
                    }
                }
                String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
                player.addTech(AliasHandler.resolveTech(tech));
                ButtonHelperFactionSpecific.resolveResearchAgreementCheck(player, tech, activeGame);
                ButtonHelperFactionSpecific.resolveNekroCommanderCheck(player, tech, activeGame);
                if ("iihq".equalsIgnoreCase(AliasHandler.resolveTech(tech))) {
                    message = message + "\n Automatically added the Custodia Vigilia planet";
                }
                if (player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "jolnar", event);
                }
                if (player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")) {
                    ButtonHelper.commanderUnlockCheck(player, activeGame, "nekro", event);
                }
                List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
                if (player.hasTechReady("aida")) {
                    Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
                    buttons.add(aiDEVButton);
                }
                Button DoneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
                buttons.add(DoneExhausting);

                if (activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    if (!player.hasAbility("technological_singularity")) {
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                    }

                } else {
                    String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-technology";
                    List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                    if (!activeGame.getComponentAction() && !player.hasAbility("technological_singularity")) {
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel(threadChannel_, message);
                            }
                        }
                    } else {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message);
                    }
                    if (!player.hasAbility("technological_singularity")) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame),
                            message2, buttons);
                    }
                }
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("riftUnit_")) {
                ButtonHelper.riftUnitButton(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("getRiftButtons_")) {
                Tile tile = activeGame.getTileByPosition(buttonID.replace("getRiftButtons_", ""));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ident + " use buttons to rift units",
                    ButtonHelper.getButtonsForRiftingUnitsInSystem(player, activeGame, tile));
            } else if (buttonID.startsWith("riftAllUnits_")) {
                ButtonHelper.riftAllUnitsButton(buttonID, event, activeGame, player, ident);
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
                Player player2 = Helper.getPlayerFromColorOrFaction(activeGame, player2Color);
                ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                ButtonHelperFactionSpecific.pillageCheck(player2, activeGame);
                ButtonHelper.checkTransactionLegality(activeGame, player, player2);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("sabotage_")) {
                String typeNName = buttonID.replace("sabotage_", "");
                String type = typeNName.substring(0, typeNName.indexOf("_"));
                String acName = typeNName.replace(type + "_", "");
                String message = "Cancelling the AC \"" + acName + "\" using ";
                String addMessage = "An AC has been cancelled!";
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
                        addMessage = "";
                        message = "Tried to play a sabo but found none in hand.";
                        sendReact = false;
                        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                            "Someone clicked the AC sabo button but did not have a sabo in hand.");
                    }
                }
                if (sendReact) {
                    MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(),
                        message + "\n" + Helper.getGamePing(activeGame.getGuild(), activeGame) + addMessage);
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
                    StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame))
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
            } else if (buttonID.startsWith("pillage_")) {
                ButtonHelperFactionSpecific.pillage(buttonID, event, activeGame, player, ident, finsFactionCheckerPrefix);
            } else if (buttonID.startsWith("exhaust_")) {
                AgendaHelper.exhaustStuffForVoting(buttonID, event, activeGame, player, ident, buttonLabel);
            } else if (buttonID.startsWith("diplo_")) {
                String planet = buttonID.replace("diplo_", "");
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
                for (Player player_ : activeGame.getPlayers().values()) {
                    if (player_ != player) {
                        String color = player_.getColor();
                        if (Mapper.isColorValid(color)) {
                            AddCC.addCC(event, color, tile);
                            Helper.isCCCountCorrect(event, activeGame, color);
                        }
                    }
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), ident + " chose to diplo the system containing "
                    + Helper.getPlanetRepresentation(planet, activeGame));
                event.getMessage().delete().queue();
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
            } else if (buttonID.startsWith("delete_buttons_")) {
                AgendaHelper.resolvingAnAgendaVote(buttonID, event, activeGame, player);
            } else if (buttonID.startsWith("combatRoll_")) {
                ButtonHelper.resolveCombatRoll(player, activeGame, event, buttonID);
            } else if (buttonID.startsWith("forceAbstainForPlayer_")) {
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), "Player was forcefully abstained");
                String faction = buttonID.replace("forceAbstainForPlayer_", "");
                Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
                AgendaHelper.resolvingAnAgendaVote("delete_buttons_0", event, activeGame, p2);
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
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        (Helper.getPlayerRepresentation(player, activeGame) + " exhausted tech: "
                            + Helper.getTechRepresentation(tech)));
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
                Player planetOwner = Helper.getPlayerFromColorOrFaction(activeGame, factionOrColor);
                String voteMessage = "Chose to vote for one of " + factionOrColor
                    + "'s planets. Click buttons for which outcome to vote for.";
                List<Button> outcomeActionRow;
                outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeGame, "outcome", null);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("indoctrinate_")) {
                ButtonHelperFactionSpecific.resolveFollowUpIndoctrinationQuestion(player, activeGame, buttonID, event);
            } else if (buttonID.startsWith("initialIndoctrination_")) {
                ButtonHelperFactionSpecific.resolveInitialIndoctrinationQuestion(player, activeGame, buttonID, event);
            } else if (buttonID.startsWith("utilizeSolCommander_")) {
                ButtonHelperFactionSpecific.resolveSolCommander(player, activeGame, buttonID, event);
            } else if (buttonID.startsWith("mercerMove_")) {
                ButtonHelperFactionSpecific.resolveMercerMove(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("tiedPlanets_")) {
                buttonID = buttonID.replace("tiedPlanets_", "");
                buttonID = buttonID.replace("delete_buttons_outcomeTie*_", "");
                String factionOrColor = buttonID;
                Player planetOwner = Helper.getPlayerFromColorOrFaction(activeGame, factionOrColor);
                String voteMessage = "Chose to break tie for one of " + factionOrColor
                    + "'s planets. Use buttons to select which one.";
                List<Button> outcomeActionRow;
                outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeGame,
                    "delete_buttons_outcomeTie*", null);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("planetRider_")) {
                buttonID = buttonID.replace("planetRider_", "");
                String factionOrColor = buttonID.substring(0, buttonID.indexOf("_"));
                Player planetOwner = Helper.getPlayerFromColorOrFaction(activeGame, factionOrColor);
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
                List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes, votes + 5);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("startCabalAgent_")) {
                ButtonHelperFactionSpecific.startCabalAgent(player, activeGame, buttonID, event);
            } else if (buttonID.startsWith("cabalAgentCapture_")) {
                ButtonHelperFactionSpecific.resolveCabalAgentCapture(buttonID, player, activeGame, event);
            } else if (buttonID.startsWith("arboAgentIn_")) {
                String pos = buttonID.substring(buttonID.indexOf("_") + 1);
                List<Button> buttons = ButtonHelperFactionSpecific.getUnitsToArboAgent(player, activeGame, event, activeGame.getTileByPosition(pos));
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " select which unit you'd like to replace", buttons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("arboAgentPutShip_")) {
                ButtonHelperFactionSpecific.arboAgentPutShip(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("arboAgentOn_")) {
                String pos = buttonID.split("_")[1];
                String unit = buttonID.split("_")[2];
                List<Button> buttons = ButtonHelperFactionSpecific.getArboAgentReplacementOptions(player, activeGame, event, activeGame.getTileByPosition(pos), unit);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), trueIdentity + " select which unit you'd like to place down", buttons);
                event.getMessage().delete().queue();

            } else if (buttonID.startsWith("resolveWithNoEffect")) {
                String voteMessage = "Resolving agenda with no effect. Click the buttons for next steps.";
                Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda");
                Button proceedToStrategyPhase = Button.success("proceed_to_strategy",
                    "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
                List<Button> resActionRow = List.of(flipNextAgenda, proceedToStrategyPhase);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("outcome_")) {
                AgendaHelper.offerVoteAmounts(buttonID, event, activeGame, player, ident, buttonLabel);
            } else if (buttonID.startsWith("votes_")) {
                AgendaHelper.exhaustPlanetsForVoting(buttonID, event, activeGame, player, ident, buttonLabel, finsFactionCheckerPrefix);
            } else if (buttonID.startsWith("dacxive_")) {
                String planet = buttonID.replace("dacxive_", "");
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planet)), "infantry " + planet,
                    activeGame);
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
                buttonID = buttonID.replace("deleteButtons_", "");
                String editedMessage = event.getMessage().getContentRaw();
                if (("Done Gaining CCs".equalsIgnoreCase(buttonLabel)
                    || "Done Redistributing CCs".equalsIgnoreCase(buttonLabel)) && editedMessage.contains("CCs have gone from")) {

                    String playerRep = Helper.getPlayerRepresentation(player, activeGame);
                    String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
                    String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                    shortCCs = shortCCs.replace("CCs have gone from ", "");
                    shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                    if (event.getMessage().getContentRaw().contains("Net gain")) {

                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        finalCCs = finalCCs + ". Net CC gain was " + netGain;
                    }
                    if (!activeGame.isFoWMode()) {
                        if ("Done Redistributing CCs".equalsIgnoreCase(buttonLabel)) {
                            MessageHelper.sendMessageToChannel(actionsChannel,
                                playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is " + finalCCs);
                        } else {
                            if ("leadership".equalsIgnoreCase(buttonID)) {
                                String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-leadership";
                                List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                                for (ThreadChannel threadChannel_ : threadChannels) {
                                    if (threadChannel_.getName().equals(threadName)) {
                                        MessageHelper.sendMessageToChannel(threadChannel_,
                                            playerRep + " Initial CCs were " + shortCCs + ". Final CC Allocation Is " + finalCCs);
                                    }
                                }

                            } else {
                                MessageHelper.sendMessageToChannel(actionsChannel,
                                    playerRep + " Final CC Allocation Is " + finalCCs);
                            }

                        }

                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            playerRep + " Final CC Allocation Is " + finalCCs);
                    }

                }
                if (("Done Exhausting Planets".equalsIgnoreCase(buttonLabel)
                    || "Done Producing Units".equalsIgnoreCase(buttonLabel))
                    && !event.getMessage().getContentRaw().contains("Click the names of the planets you wish")) {

                    if (activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                    } else {
                        if ("warfare".equalsIgnoreCase(buttonID)) {
                            String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-warfare";
                            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel(threadChannel_, editedMessage);
                                }
                            }
                        } else if ("leadership".equalsIgnoreCase(buttonID)) {
                            String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-leadership";
                            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel(threadChannel_, editedMessage);
                                }
                            }

                        } else if ("construction".equalsIgnoreCase(buttonID)) {
                            String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-construction";
                            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel(threadChannel_, editedMessage);
                                }
                            }
                        } else if ("technology".equalsIgnoreCase(buttonID)) {
                            String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-technology";
                            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();

                            if (!activeGame.getComponentAction()) {
                                for (ThreadChannel threadChannel_ : threadChannels) {
                                    if (threadChannel_.getName().equals(threadName)) {
                                        MessageHelper.sendMessageToChannel(threadChannel_, editedMessage);
                                    }
                                }
                            } else {
                                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), editedMessage);

                            }
                        } else {
                            MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                        }
                    }
                    if ("Done Producing Units".equalsIgnoreCase(buttonLabel)) {
                        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

                        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
                        if (player.hasTechReady("sar") && !"muaatagent".equalsIgnoreCase(buttonID)) {
                            Button sar = Button.danger("exhaustTech_sar", "Exhaust Self Assembly Routines");
                            buttons.add(sar);
                        }
                        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "titanscommander") && !"muaatagent".equalsIgnoreCase(buttonID)) {
                            Button sar2 = Button.success("titansCommanderUsage", "Use Titans Commander To Gain a TG");
                            buttons.add(sar2);
                        }
                        if (player.hasTechReady("aida") && !"muaatagent".equalsIgnoreCase(buttonID)) {
                            Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
                            buttons.add(aiDEVButton);
                        }
                        if (player.hasTechReady("st") && !"muaatagent".equalsIgnoreCase(buttonID)) {
                            Button sarweenButton = Button.danger("exhaustTech_st", "Use Sarween");
                            buttons.add(sarweenButton);
                        }
                        if (player.hasUnexhaustedLeader("winnuagent", activeGame) && !"muaatagent".equalsIgnoreCase(buttonID)) {
                            Button winnuButton = Button.danger("exhaustAgent_winnuagent", "Use Winnu Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("winnu")));
                            buttons.add(winnuButton);
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
                    if (buttonID.contains("tacticalAction")) {
                        ButtonHelper.exploreDET(player, activeGame, event);
                        List<Button> systemButtons2 = new ArrayList<>();
                        if (!activeGame.isAbsolMode() && player.getRelics().contains("emphidia") && !player.getExhaustedRelics().contains("emphidia")) {
                            String message = trueIdentity + " You can use the button to explore using crown of emphidia";
                            systemButtons2.add(Button.success("crownofemphidiaexplore", "Use Crown To Explore a Planet"));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                        }
                        systemButtons2 = new ArrayList<>();
                        if (player.hasUnexhaustedLeader("sardakkagent", activeGame)) {
                            String message = trueIdentity + " You can use the button to do sardakk agent";
                            systemButtons2.addAll(ButtonHelperFactionSpecific.getSardakkAgentButtons(activeGame, player));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                        }
                        if (player.hasUnexhaustedLeader("nomadagentmercer", activeGame)) {
                            String message = trueIdentity + " You can use the button to do General Mercer";
                            systemButtons2.addAll(ButtonHelperFactionSpecific.getMercerAgentInitialButtons(activeGame, player));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons2);
                        }

                        if (activeGame.getNaaluAgent()) {
                            player = Helper.getPlayerFromUnlockedLeader(activeGame, "naaluagent");
                            activeGame.setNaaluAgent(false);
                        }

                        String message = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Use buttons to end turn or do another action.";
                        List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                        MessageChannel channel = event.getMessageChannel();
                        if (activeGame.isFoWMode()) {
                            channel = player.getPrivateChannel();
                        }
                        MessageHelper.sendMessageToChannelWithButtons(channel, message, systemButtons);

                    }
                }
                if ("diplomacy".equalsIgnoreCase(buttonID)) {
                    if (!activeGame.isFoWMode()) {
                        String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-diplomacy";
                        List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel(threadChannel_, editedMessage);
                            }
                        }
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                    }
                }
                if ("spitItOut".equalsIgnoreCase(buttonID)) {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), editedMessage);
                }
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("reverse_")) {
                AgendaHelper.reverseRider(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("rider_")) {
                AgendaHelper.placeRider(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("construction_")) {
                player.addFollowedSC(4);
                ButtonHelper.addReaction(event, false, false, "", "");
                String unit = buttonID.replace("construction_", "");
                String message = trueIdentity + " Click the name of the planet you wish to put your unit on for construction";
                List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, unit, "place");
                if (!activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame),
                        message, buttons);
                } else {
                    MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                }
            } else if (buttonID.startsWith("jrStructure_")) {
                String unit = buttonID.replace("jrStructure_", "");
                if (!"tg".equalsIgnoreCase(unit)) {
                    String message = trueIdentity + " Click the name of the planet you wish to put your unit on";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, unit, "placeOneNDone_dontskip");
                    if (!activeGame.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame),
                            message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                } else {
                    MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ident + " tgs increased by 1 (" + player.getTg() + "->" + (player.getTg() + 1) + ")");
                    player.setTg(player.getTg() + 1);
                    ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                }
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("winnuStructure_")) {
                String unit = buttonID.replace("winnuStructure_", "").split("_")[0];
                String planet = buttonID.replace("winnuStructure_", "").split("_")[1];
                new AddUnits().unitParsing(event, player.getColor(),
                    activeGame.getTile(AliasHandler.resolveTile(planet)), unit + " " + planet, activeGame);
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, activeGame));

            } else if (buttonID.startsWith("produceOneUnitInTile_")) {
                buttonID = buttonID.replace("produceOneUnitInTile_", "");
                String type = buttonID.split("_")[1];
                String pos = buttonID.split("_")[0];
                List<Button> buttons;
                buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                    activeGame.getTileByPosition(pos), type, "placeOneNDone_dontskip");
                String message = Helper.getPlayerRepresentation(player, activeGame) + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("yinagent_")) {
                ButtonHelperFactionSpecific.yinAgent(buttonID, event, activeGame, player, ident, trueIdentity);
            } else if (buttonID.startsWith("resolveMaw")) {
                ButtonHelper.resolveMaw(activeGame, player, event);
            } else if (buttonID.startsWith("resolveCrownOfE")) {
                ButtonHelper.resolveCrownOfE(activeGame, player, event);
            } else if (buttonID.startsWith("jrResolution_")) {
                String faction2 = buttonID.split("_")[1];
                Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction2);
                Button sdButton = Button.success("jrStructure_sd", "Place A SD");
                sdButton = sdButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("spacedock")));
                Button pdsButton = Button.success("jrStructure_pds", "Place a PDS");
                pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("pds")));
                Button tgButton = Button.success("jrStructure_tg", "Gain a tg");
                List<Button> buttons = new ArrayList<>();
                buttons.add(sdButton);
                buttons.add(pdsButton);
                buttons.add(tgButton);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(p2, activeGame),
                    ButtonHelper.getTrueIdentity(p2, activeGame) + " Use buttons to decide what structure to build", buttons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("yssarilHeroRejection_")) {
                String playerFaction = buttonID.replace("yssarilHeroRejection_", "");
                Player notYssaril = Helper.getPlayerFromColorOrFaction(activeGame, playerFaction);
                if (notYssaril != null) {
                    String message = Helper.getPlayerRepresentation(notYssaril, activeGame, activeGame.getGuild(), true)
                        + " the player of the yssaril hero has rejected your offering and is forcing you to discard 3 random ACs. The ACs have been automatically discarded";
                    MessageHelper.sendMessageToChannel(notYssaril.getCardsInfoThread(activeGame), message);
                    new DiscardACRandom().discardRandomAC(event, activeGame, notYssaril, 3);
                    event.getMessage().delete().queue();
                }
            } else if (buttonID.startsWith("yssarilHeroInitialOffering_")) {
                List<Button> acButtons = new ArrayList<>();
                buttonID = buttonID.replace("yssarilHeroInitialOffering_", "");
                String acID = buttonID.split("_")[0];
                String yssarilFaction = buttonID.split("_")[1];
                Player yssaril = Helper.getPlayerFromColorOrFaction(activeGame, yssarilFaction);
                if (yssaril != null) {
                    String offerName = player.getFaction();
                    if (activeGame.isFoWMode()) {
                        offerName = player.getColor();
                    }
                    acButtons.add(Button.success("takeAC_" + acID + "_" + player.getFaction(), buttonLabel).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                    acButtons.add(Button.danger("yssarilHeroRejection_" + player.getFaction(), "Reject " + buttonLabel + " and force them to discard of 3 random ACs"));
                    String message = Helper.getPlayerRepresentation(yssaril, activeGame, activeGame.getGuild(), true) + " " + offerName + " has offered you the action card " + buttonLabel
                        + " for your Yssaril Hero play. Use buttons to accept or reject it";
                    MessageHelper.sendMessageToChannelWithButtons(yssaril.getCardsInfoThread(activeGame), message, acButtons);
                    event.getMessage().delete().queue();
                }
            } else if (buttonID.startsWith("statusInfRevival_")) {
                ButtonHelper.placeInfantryFromRevival(activeGame, event, player, buttonID);
            } else if (buttonID.startsWith("genericReact")) {
                String message = activeGame.isFoWMode() ? "Turned down window" : null;
                ButtonHelper.addReaction(event, false, false, message, "");
            } else if (buttonID.startsWith("placeOneNDone_")) {
                ButtonHelperModifyUnits.placeUnitAndDeleteButton(buttonID, event, activeGame, player, ident, trueIdentity);
            } else if (buttonID.startsWith("mitoMechPlacement_")) {
                ButtonHelperFactionSpecific.resolveMitosisMechPlacement(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("place_")) {
                ButtonHelperModifyUnits.genericPlaceUnit(buttonID, event, activeGame, player, ident, trueIdentity, finsFactionCheckerPrefix);
            } else if (buttonID.startsWith("yssarilcommander_")) {
                ButtonHelperFactionSpecific.yssarilCommander(buttonID, event, activeGame, player, ident);
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
                Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
                List<String> potentialTech = new ArrayList<>();
                potentialTech = ButtonHelperFactionSpecific.getPossibleTechForNekroToGainFromPlayer(player, p2, potentialTech, activeGame);
                List<Button> buttons = ButtonHelperFactionSpecific.getButtonsForPossibleTechForNekro(player, potentialTech, activeGame);
                if (buttons.size() > 0) {
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), trueIdentity + " get enemy tech using the buttons", buttons);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), trueIdentity + " no tech available to gain");
                }
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

            } else if (buttonID.startsWith("freelancersBuild_")) {
                String planet = buttonID.replace("freelancersBuild_", "");
                List<Button> buttons;
                Tile tile = activeGame.getTile(AliasHandler.resolveTile(planet));
                if (tile == null) {
                    tile = activeGame.getTileByPosition(planet);
                }
                buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                    tile, "freelancers", "placeOneNDone_dontskip");
                String message = Helper.getPlayerRepresentation(player, activeGame) + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("tacticalActionBuild_")) {
                String pos = buttonID.replace("tacticalActionBuild_", "");
                List<Button> buttons;
                buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                    activeGame.getTileByPosition(pos), "tacticalAction", "place");
                String message = Helper.getPlayerRepresentation(player, activeGame) + " Use the buttons to produce units. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("getModifyTiles")) {
                List<Button> buttons;
                buttons = ButtonHelper.getTilesToModify(player, activeGame, event);
                String message = Helper.getPlayerRepresentation(player, activeGame) + " Use the buttons to select the tile in which you wish to modify units. ";
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message, buttons);
            } else if (buttonID.startsWith("genericModify_")) {
                String pos = buttonID.replace("genericModify_", "");
                Tile tile = activeGame.getTileByPosition(pos);
                ButtonHelper.offerBuildOrRemove(player, activeGame, event, tile);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("genericBuild_")) {
                String pos = buttonID.replace("genericBuild_", "");
                List<Button> buttons;
                buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                    activeGame.getTileByPosition(pos), "genericBuild", "place");
                String message = Helper.getPlayerRepresentation(player, activeGame) + " Use the buttons to produce units. ";
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("starforgeTile_")) {
                ButtonHelperFactionSpecific.starforgeTile(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("starforge_")) {
                ButtonHelperFactionSpecific.starforge(buttonID, event, activeGame, player, ident);
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
            } else if (buttonID.startsWith("scPick_")) {
                Stats stats = new Stats();
                String num = buttonID.replace("scPick_", "");
                int scpick = Integer.parseInt(num);
                boolean pickSuccessful = stats.secondHalfOfPickSC(event, activeGame, player, scpick);
                if (pickSuccessful) {
                    new SCPick().secondHalfOfSCPick(event, player, activeGame, scpick);
                    event.getMessage().delete().queue();
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
                Player victim = Helper.getPlayerFromColorOrFaction(activeGame, faction);
                List<Button> buttons = ButtonHelperFactionSpecific.getButtonsToTakeSomeonesAC(activeGame, player, victim);
                ShowAllAC.showAll(victim, player, activeGame);
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame),
                    Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true)
                        + " Select which AC you would like to steal",
                    buttons);
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
                    if (abilities > 0) {
                        List<Button> buttons = new ArrayList<>();
                        buttons.add(Button.success(finsFactionCheckerPrefix + "doActivation_" + pos, "Confirm"));
                        buttons.add(Button.danger(finsFactionCheckerPrefix + "deleteButtons", "This activation was a mistake"));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            ident + " You are about to automatically trigger some abilities by activating this system, are you sure you want to proceed?", buttons);
                    }
                    for (Player player_ : activeGame.getRealPlayers()) {
                        if (!player.getFaction().equalsIgnoreCase(player_.getFaction()) && !player_.isPlayerMemberOfAlliance(player)
                            && FoWHelper.playerHasUnitsInSystem(player_, activeGame.getTileByPosition(pos))) {
                            String msgA = Helper.getPlayerRepresentation(player_, activeGame)
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
                if (!activeGame.isFoWMode() && playersWithPds2.size() > 0) {
                    StringBuilder pdsMessage = new StringBuilder(trueIdentity + " this is a courtesy notice that the selected system is in range of deep space cannon units owned by");

                    for (Player playerWithPds : playersWithPds2) {
                        pdsMessage.append(" ").append(Helper.getPlayerRepresentation(playerWithPds, activeGame, activeGame.getGuild(), false));
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), pdsMessage.toString());
                }
                List<Button> button2 = ButtonHelper.scanlinkResolution(player, activeGame, event);
                List<Button> button3 = ButtonHelperFactionSpecific.getL1Z1XAgentButtons(activeGame, player);
                if (player.hasUnexhaustedLeader("l1z1xagent", activeGame) && !button3.isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ButtonHelper.getTrueIdentity(player, activeGame) + " You can use buttons to resolve L1 Agent if you want",
                        button3);
                }
                if (player.getTechs().contains("sdn") && !button2.isEmpty()) {
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please resolve scanlink",
                        button2);
                    if (player.hasAbility("awaken")) {
                        ButtonHelper.resolveTitanShenanigansOnActivation(player, activeGame, activeGame.getTileByPosition(pos), event);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        "\n\nUse buttons to select the first system you want to move from", systemButtons);
                } else {
                    if (player.hasAbility("awaken")) {
                        ButtonHelper.resolveTitanShenanigansOnActivation(player, activeGame, activeGame.getTileByPosition(pos), event);
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        "Use buttons to select the first system you want to move from", systemButtons);
                }
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("genericRemove_")) {
                String pos = buttonID.replace("genericRemove_", "");
                activeGame.resetCurrentMovedUnitsFrom1System();
                activeGame.resetCurrentMovedUnitsFrom1TacticalAction();
                List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame,
                    activeGame.getTileByPosition(pos), "Remove");
                activeGame.resetCurrentMovedUnitsFrom1System();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Chose to remove units from "
                    + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to select the units you want to remove.", systemButtons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("tacticalMoveFrom_")) {
                String pos = buttonID.replace("tacticalMoveFrom_", "");
                List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeGame,
                    activeGame.getTileByPosition(pos), "Move");
                activeGame.resetCurrentMovedUnitsFrom1System();
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Chose to move from "
                        + activeGame.getTileByPosition(pos).getRepresentationForButtons(activeGame, player)
                        + ". Use buttons to select the units you want to move.",
                    systemButtons);
                event.getMessage().delete().queue();
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
                String message = Helper.getPlayerRepresentation(player, activeGame) + " purged fragments: "
                    + fragmentsToPurge.toString();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            } else if (buttonID.startsWith("unitTactical")) {
                ButtonHelperModifyUnits.movingUnitsInTacticalAction(buttonID, event, activeGame, player, ident, buttonLabel);
            } else if (buttonID.startsWith("landUnits_")) {
                ButtonHelperModifyUnits.landingUnits(buttonID, event, activeGame, player, ident, buttonLabel);
            } else if (buttonID.startsWith("spaceUnits_")) {
                ButtonHelperModifyUnits.spaceLandedUnits(buttonID, event, activeGame, player, ident, buttonLabel);
            } else if (buttonID.startsWith("reinforcements_cc_placement_")) {
                String playerRep = Helper.getPlayerRepresentation(player, activeGame);
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
                if (Mapper.isColorValid(color)) {
                    AddCC.addCC(event, color, tile);
                }

                if (activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                        playerRep + " Placed A CC From Reinforcements In The "
                            + Helper.getPlanetRepresentation(planet, activeGame) + " system");
                } else {
                    List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                    if (threadChannels == null)
                        return;
                    String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-construction";
                    // SEARCH FOR EXISTING OPEN THREAD
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            MessageHelper.sendMessageToChannel(threadChannel_,
                                playerRep + " Placed A CC From Reinforcements In The "
                                    + Helper.getPlanetRepresentation(planet, activeGame) + " system");
                        }
                    }
                }
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("transactWith_")) {
                String faction = buttonID.replace("transactWith_", "");
                Player p2 = Helper.getPlayerFromColorOrFaction(activeGame, faction);
                List<Button> buttons;
                buttons = ButtonHelper.getStuffToTransButtons(activeGame, player, p2);
                String message = Helper.getPlayerRepresentation(player, activeGame)
                    + " Use the buttons to select what you want to transact";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                event.getMessage().delete().queue();
            } else if (buttonID.startsWith("transact_")) {
                ButtonHelper.resolveSpecificTransButtons(activeGame, player, buttonID, event);
                event.getMessage().delete().queue();

            } else if (buttonID.startsWith("play_after_")) {
                String riderName = buttonID.replace("play_after_", "");
                ButtonHelper.addReaction(event, true, true, "Playing " + riderName, riderName + " Played");
                List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeGame,
                    finsFactionCheckerPrefix);

                List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
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
                MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel,
                    "Please select your rider target", activeGame, player, riderButtons);
                if ("Keleres Xxcha Hero".equalsIgnoreCase(riderName)) {
                    Leader playerLeader = player.getLeader("keleresheroodlynn").orElse(null);
                    if (playerLeader != null) {
                        StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeGame));
                        message.append(" played ");
                        message.append(Helper.getLeaderFullRepresentation(playerLeader));
                        boolean purged = player.removeLeader(playerLeader);
                        if (purged) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString() + " - Leader Oodlynn has been purged");
                        } else {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Leader was not purged - something went wrong");
                        }
                    }
                }
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                    "Please indicate no afters again.", activeGame, afterButtons, "after");
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
                    ButtonHelperFactionSpecific.resolveNekroCommanderCheck(player, tech, activeGame);
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
                ButtonHelperFactionSpecific.putSleeperOn(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("removeSleeperFromPlanet_")) {
                ButtonHelperFactionSpecific.removeSleeper(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("replaceSleeperWith_")) {
                ButtonHelperFactionSpecific.replaceSleeperWith(buttonID, event, activeGame, player, ident);
            } else if (buttonID.startsWith("topAgenda_")) {
                String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
                new PutAgendaTop().putTop(event, Integer.parseInt(agendaNumID), activeGame);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Put " + agendaNumID + " on the top of the agenda deck.");
            } else if (buttonID.startsWith("primaryOfWarfare")) {
                List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeGame, event, "warfare");
                MessageChannel channel = event.getMessageChannel();
                if (activeGame.isFoWMode()) {
                    channel = player.getPrivateChannel();
                }
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
                new PutAgendaBottom().putBottom(event, Integer.parseInt(agendaNumID),
                    activeGame);
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Put " + agendaNumID + " on the bottom of the agenda deck.");
            } else if (buttonID.startsWith("agendaResolution_")) {
                AgendaHelper.resolveAgenda(activeGame, buttonID, event, actionsChannel);
            } else {
                switch (buttonID) {
                    // AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
                    case Constants.PO_NO_SCORING -> {
                        String message = Helper.getPlayerRepresentation(player, activeGame)
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
                            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeGame), "Could not find a HS, sorry bro");
                        }
                        buttons = Helper.getPlaceUnitButtons(event, player, activeGame,
                            tile, "warfare", "place");
                        String message = Helper.getPlayerRepresentation(player, activeGame)
                            + " Use the buttons to produce. Reminder that when following warfare, you can only use 1 dock in your home system. "
                            + ButtonHelper.getListOfStuffAvailableToSpend(player, activeGame);
                        if (!activeGame.isFoWMode()) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), message,
                                buttons);
                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        }
                    }
                    case "transaction" -> {
                        List<Button> buttons;
                        buttons = ButtonHelper.getPlayersToTransact(activeGame, player);
                        String message = Helper.getPlayerRepresentation(player, activeGame)
                            + " Use the buttons to select which player you wish to transact with";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                    }
                    case "combatDrones" -> ButtonHelperModifyUnits.resolvingCombatDrones(event, activeGame, player, ident, buttonLabel);
                    case "acquireATech" -> {

                        List<Button> buttons = new ArrayList<>();

                        Button propulsionTech = Button.primary(finsFactionCheckerPrefix + "getAllTechOfType_propulsion", "Get a Blue Tech");
                        propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Propulsiontech")));
                        buttons.add(propulsionTech);

                        Button bioticTech = Button.success(finsFactionCheckerPrefix + "getAllTechOfType_biotic", "Get a Green Tech");
                        bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Biotictech")));
                        buttons.add(bioticTech);

                        Button cyberneticTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic", "Get a Yellow Tech");
                        cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Cybernetictech")));
                        buttons.add(cyberneticTech);

                        Button warfareTech = Button.danger(finsFactionCheckerPrefix + "getAllTechOfType_warfare", "Get a Red Tech");
                        warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Warfaretech")));
                        buttons.add(warfareTech);

                        Button unitupgradesTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade", "Get A Unit Upgrade Tech");
                        unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("UnitUpgradeTech")));
                        buttons.add(unitupgradesTech);

                        String message = Helper.getPlayerRepresentation(player, activeGame) + " What type of tech would you want?";
                        if (!activeGame.isFoWMode()) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), message, buttons);
                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        }
                    }
                    case Constants.SO_NO_SCORING -> {
                        String message = Helper.getPlayerRepresentation(player, activeGame)
                            + " - no Secret Objective scored.";
                        if (!activeGame.isFoWMode()) {
                            MessageHelper.sendMessageToChannel(event.getChannel(), message);
                        }
                        String reply = activeGame.isFoWMode() ? "No secret objective scored" : null;
                        ButtonHelper.addReaction(event, false, false, reply, "");
                    }
                    // AFTER AN ACTION CARD HAS BEEN PLAYED
                    case "no_sabotage" -> {
                        String message = activeGame.isFoWMode() ? "No sabotage" : null;
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    case "titansCommanderUsage" -> ButtonHelperFactionSpecific.titansCommanderUsage(buttonID, event, activeGame, player, ident);
                    case "passForRound" -> {
                        player.setPassed(true);
                        String text = Helper.getPlayerRepresentation(player, activeGame) + " PASSED";
                        MessageHelper.sendMessageToChannel(event.getChannel(), text);
                        Turn turn = new Turn();
                        turn.sendMessage(turn.pingNextPlayer(event, activeGame, player), event);
                    }
                    case "proceedToVoting" -> {
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Decided to skip waiting for afters and proceed to voting.");
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
                        String message = trueIdentity + "! Your current CCs are " + Helper.getPlayerCCs(player)
                            + ". Use buttons to gain CCs";
                        List<Button> buttons = List.of(getTactic, getFleet, getStrat, exhaust, DoneGainingCC);
                        if (!activeGame.isFoWMode()) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), message,
                                buttons);
                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        }
                    }
                    case "diploRefresh2" -> {
                        player.addFollowedSC(2);
                        ButtonHelper.addReaction(event, false, false, "", "");
                        String message = trueIdentity + " Click the names of the planets you wish to ready";

                        List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, activeGame);
                        Button DoneRefreshing = Button.danger("deleteButtons_diplomacy", "Done Readying Planets");
                        buttons.add(DoneRefreshing);
                        if (!activeGame.isFoWMode()) {
                            MessageHelper.sendMessageToChannelWithButtons(
                                player.getCardsInfoThread(activeGame), message, buttons);
                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        }
                        if (player.hasAbility("peace_accords")) {
                            List<Button> buttons2 = ButtonHelperFactionSpecific.getXxchaPeaceAccordsButtons(activeGame, player, event, finsFactionCheckerPrefix);
                            if (!buttons2.isEmpty()) {
                                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), trueIdentity + " use buttons to resolve peace accords", buttons2);
                            }
                        }
                    }
                    case "leadershipExhaust" -> {
                        ButtonHelper.addReaction(event, false, false, "", "");
                        String message = trueIdentity + " Click the names of the planets you wish to exhaust.";
                        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
                        Button DoneExhausting = Button.danger("deleteButtons_leadership", "Done Exhausting Planets");
                        buttons.add(DoneExhausting);
                        if (!activeGame.isFoWMode()) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), message,
                                buttons);
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
                            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), message,
                                buttons);
                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        }
                    }
                    case "endOfTurnAbilities" -> MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to do an end of turn ability", ButtonHelper.getEndOfTurnAbilities(player, activeGame));
                    case "redistributeCCButtons" -> {

                        String message = trueIdentity + "! Your current CCs are " + Helper.getPlayerCCs(player)
                            + ". Use buttons to gain CCs";

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

                        Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons",
                            "Done Redistributing CCs");
                        List<Button> buttons = List.of(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat,
                            DoneGainingCC);
                        if (!activeGame.isFoWMode()) {

                            MessageHelper.sendMessageToChannelWithButtons(
                                player.getCardsInfoThread(activeGame), message, buttons);

                            // MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message,
                            // buttons);

                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        }
                    }

                    case "leadershipGenerateCCButtons" -> {
                        player.addFollowedSC(1);
                        ButtonHelper.addReaction(event, false, false, "", "");
                        String message = trueIdentity + "! Your current CCs are " + Helper.getPlayerCCs(player)
                            + ". Use buttons to gain CCs";

                        Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc",
                            "Gain 1 Tactic CC");
                        Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                        Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc",
                            "Gain 1 Strategy CC");
                        // Button exhaust = Button.danger(finsFactionCheckerPrefix +
                        // "leadershipExhaust", "Exhaust Planets");
                        Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons_leadership",
                            "Done Gaining CCs");
                        List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                        if (!activeGame.isFoWMode()) {
                            MessageHelper.sendMessageToChannelWithButtons(
                                player.getCardsInfoThread(activeGame), message, buttons);
                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        }
                    }
                    case "diploSystem" -> {
                        String message = trueIdentity + " Click the name of the planet who's system you wish to diplo";

                        List<Button> buttons = Helper.getPlanetSystemDiploButtons(event, player, activeGame, false);
                        if (!activeGame.isFoWMode()) {
                            List<ThreadChannel> threadChannels = activeGame.getActionsChannel().getThreadChannels();
                            if (threadChannels == null)
                                return;
                            String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-diplomacy";
                            // SEARCH FOR EXISTING OPEN THREAD
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannelWithButtons(threadChannel_, message,
                                        buttons);
                                }
                            }
                        } else {
                            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                        }

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
                    }
                    case "sc_draw_so" -> {
                        boolean used = addUsedSCPlayer(messageID + "so", activeGame, player, event,
                            " Drew a " + Emojis.SecretObjective);
                        if (used) {
                            break;
                        }
                        String message = "Drew Secret Objective";
                        activeGame.drawSecretObjective(player.getUserID());
                        if (player.hasAbility("plausible_deniability")) {
                            activeGame.drawSecretObjective(player.getUserID());
                            message = message + ". Drew a second SO due to plausible deniability";
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
                        String message = deductCC(player, event);
                        player.addFollowedSC(5);
                        player.setCommodities(player.getCommoditiesTotal());
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
                        player.addFollowedSC(5);
                        player.setCommodities(player.getCommoditiesTotal());
                        ButtonHelper.addReaction(event, false, false, message, "");
                        ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                    }
                    case "sc_follow_leadership" -> {
                        String message = Helper.getPlayerPing(player) + " following.";
                        player.addFollowedSC(1);
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    case "sc_leadership_follow" -> {
                        String message = Helper.getPlayerPing(player) + " following.";
                        player.addFollowedSC(1);
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    case "sc_refresh" -> {
                        boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "Replenish");
                        if (used) {
                            break;
                        }
                        player.setCommodities(player.getCommoditiesTotal());
                        player.addFollowedSC(5);
                        ButtonHelper.addReaction(event, false, false, "Replenishing Commodities", "");
                        ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                        ButtonHelperFactionSpecific.cabalAgentInitiation(activeGame, player);
                    }
                    case "sc_refresh_and_wash" -> {
                        boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "Replenish and Wash");
                        if (used) {
                            break;
                        }
                        int commoditiesTotal = player.getCommoditiesTotal();
                        int tg = player.getTg();
                        player.setTg(tg + commoditiesTotal);
                        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                        player.setCommodities(0);
                        player.addFollowedSC(5);
                        ButtonHelper.addReaction(event, false, false, "Replenishing and washing", "");
                        ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                        ButtonHelperFactionSpecific.cabalAgentInitiation(activeGame, player);
                    }
                    case "sc_follow" -> {
                        boolean used = addUsedSCPlayer(messageID, activeGame, player, event, "");
                        if (used) {
                            break;
                        }
                        String message = deductCC(player, event);

                        ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(player, activeGame, event);
                        int scnum = 1;
                        boolean setstatus = true;
                        try {
                            scnum = Integer.parseInt(lastchar);
                        } catch (NumberFormatException e) {
                            setstatus = false;
                        }
                        if (setstatus) {
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
                        if (player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")) {
                            ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
                        }
                        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
                        player.setCommodities(player.getCommoditiesTotal());
                        ButtonHelper.addReaction(event, false, false, " gained 3" + Emojis.tg + " and replenished commodities ("
                            + player.getCommodities() + Emojis.comm + ")", "");
                        ButtonHelper.resolveMinisterOfCommerceCheck(activeGame, player, event);
                        ButtonHelperFactionSpecific.cabalAgentInitiation(activeGame, player);
                    }
                    case "score_imperial" -> {
                        if (player == null || activeGame == null) {
                            break;
                        }
                        if (!player.getSCs().contains(8)) {
                            MessageHelper.sendMessageToChannel(privateChannel, "Only the player who has "
                                + Helper.getSCBackRepresentation(activeGame, 8) + " can score the Imperial point");
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
                        // addPersistentReactions(event, activeMap, "when");
                        event.getMessage().delete().queue();
                    }
                    case "no_when" -> {
                        String message = activeGame.isFoWMode() ? "No whens" : null;
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    case "no_after" -> {
                        String message = activeGame.isFoWMode() ? "No afters" : null;
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    case "no_after_persistent" -> {
                        String message = activeGame.isFoWMode() ? "No afters (locked in)" : null;
                        activeGame.addPlayersWhoHitPersistentNoAfter(player.getFaction());
                        ButtonHelper.addReaction(event, false, false, message, "");
                    }
                    case "no_when_persistent" -> {
                        String message = activeGame.isFoWMode() ? "No whens (locked in)" : null;
                        activeGame.addPlayersWhoHitPersistentNoWhen(player.getFaction());
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
                            if (p.getSecrets().size() > 1) {
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please ensure everyone has discarded secrets before hitting this button. ");
                                return;
                            }
                        }
                        if (speaker == null) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please assign speaker before hitting this button (command is /player stats speaker:y)");
                            return;
                        }
                        new RevealStage1().revealS1(event, activeGame.getMainGameChannel());
                        new RevealStage1().revealS1(event, activeGame.getMainGameChannel());
                        ButtonHelper.startStrategyPhase(event, activeGame);
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
                        ButtonHelper.addReaction(event, false, false, message, "");
                        event.getMessage().delete().queue();
                        if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                            MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                        }

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
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
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
                        if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                            MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                        }
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
                        String message = hasSchemingAbility
                            ? "Spent 1 " + commOrTg + " to draw " + count2
                                + " Action Card (Scheming) - please discard an Action Card from your hand"
                            : "Spent 1 " + commOrTg + " to draw " + count2 + " AC";
                        ButtonHelper.addReaction(event, false, false, message, "");
                        event.getMessage().delete().queue();
                        if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
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
                        new AddUnits().unitParsing(event, player.getColor(),
                            activeGame.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName,
                            activeGame);
                        ButtonHelper.addReaction(event, false, false, "Spent 1 " + commOrTg + " for a mech on " + planetName, "");
                        event.getMessage().delete().queue();
                        if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                            MessageHelper.sendMessageToChannel(actionsChannel,
                                pF + " Spent 1 " + commOrTg + " for a mech on " + planetName);
                        }
                    }
                    case "increase_strategy_cc" -> {
                        String originalCCs = Helper.getPlayerCCs(player);
                        player.setStrategicCC(player.getStrategicCC() + 1);
                        String editedMessage = event.getMessage().getContentRaw();
                        if (editedMessage.contains("Use buttons to gain CCs")) {
                            editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                    + ". Net gain of: 1");
                        } else {
                            String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                            shortCCs = shortCCs.replace("CCs have gone from ", "");
                            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                            int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                            editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                        }
                        event.getMessage().editMessage(editedMessage).queue();
                    }
                    case "exhauste6g0network" -> {
                        player.addExhaustedRelic("e6-g0_network");
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " Chose to exhaust e6-g0_network");
                        String message = "Use buttons to draw an AC";
                        List<Button> buttons = new ArrayList<>();
                        if (player.hasAbility("scheming")) {
                            buttons.add(Button.success("draw_2_ACDelete", "Draw 2 AC (With Scheming)"));
                        } else {
                            buttons.add(Button.success("draw_1_ACDelete", "Draw 1 AC"));
                        }
                        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getIdent(player) + " " + message, buttons);
                    }
                    case "increase_tactic_cc" -> {
                        String originalCCs = Helper.getPlayerCCs(player);
                        player.setTacticalCC(player.getTacticalCC() + 1);
                        String editedMessage = event.getMessage().getContentRaw();
                        if (editedMessage.contains("Use buttons to gain CCs")) {
                            editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                    + ". Net gain of: 1");
                        } else {

                            String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                            shortCCs = shortCCs.replace("CCs have gone from ", "");
                            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                            int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                            editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                        }
                        event.getMessage().editMessage(editedMessage).queue();
                    }
                    case "increase_fleet_cc" -> {
                        String originalCCs = Helper.getPlayerCCs(player);
                        player.setFleetCC(player.getFleetCC() + 1);
                        String editedMessage = event.getMessage().getContentRaw();
                        if (editedMessage.contains("Use buttons to gain CCs")) {
                            editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                    + ". Net gain of: 1");
                        } else {
                            String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                            shortCCs = shortCCs.replace("CCs have gone from ", "");
                            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                            int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                            editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                        }
                        event.getMessage().editMessage(editedMessage).queue();
                    }
                    case "decrease_strategy_cc" -> {
                        String originalCCs = Helper.getPlayerCCs(player);
                        player.setStrategicCC(player.getStrategicCC() - 1);
                        String editedMessage = event.getMessage().getContentRaw();
                        if (editedMessage.contains("Use buttons to gain CCs")) {
                            editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                    + ". Net gain of: -1");
                        } else {
                            String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                            shortCCs = shortCCs.replace("CCs have gone from ", "");
                            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                            int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                            editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                        }
                        event.getMessage().editMessage(editedMessage).queue();
                    }
                    case "decrease_tactic_cc" -> {
                        String originalCCs = Helper.getPlayerCCs(player);
                        player.setTacticalCC(player.getTacticalCC() - 1);
                        String editedMessage = event.getMessage().getContentRaw();
                        if (editedMessage.contains("Use buttons to gain CCs")) {
                            editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                    + ". Net gain of: -1");
                        } else {
                            String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                            shortCCs = shortCCs.replace("CCs have gone from ", "");
                            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                            int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                            editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                        }
                        event.getMessage().editMessage(editedMessage).queue();
                    }
                    case "decrease_fleet_cc" -> {
                        String originalCCs = Helper.getPlayerCCs(player);
                        player.setFleetCC(player.getFleetCC() - 1);
                        String editedMessage = event.getMessage().getContentRaw();
                        if (editedMessage.contains("Use buttons to gain CCs")) {
                            editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                    + ". Net gain of: -1");
                        } else {
                            String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "));
                            shortCCs = shortCCs.replace("CCs have gone from ", "");
                            shortCCs = shortCCs.substring(0, shortCCs.indexOf(" "));
                            int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                            editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
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
                        }
                        ButtonHelper.addReaction(event, false, false, message, "");
                        if (!failed) {
                            event.getMessage().delete().queue();
                            if (!activeGame.isFoWMode() && (event.getChannel() != activeGame.getActionsChannel())) {
                                String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                                MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                            }
                        }
                    }
                    case "mallice_2_tg" -> {
                        String playerRep = Helper.getFactionIconFromDiscord(player.getFaction());
                        String message = playerRep + " exhausted Mallice ability and gained 2 tg (" + player.getTg() + "->"
                            + (player.getTg() + 2) + ").";
                        player.setTg(player.getTg() + 2);
                        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
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

                        String playerRep = Helper.getFactionIconFromDiscord(player.getFaction());

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
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                            MessageHelper.sendMessageToChannel(actionsChannel, pF + " declined explore");
                        }
                    }
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
                        ButtonHelper.exploreDET(player, activeGame, event);
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
                            if (!activeGame.getMovedUnitsFromCurrentActivation().isEmpty() && (tile.getUnitHolders().values().size() == 1) && player.hasUnexhaustedLeader("empyreanagent", activeGame)) {
                                Button empyButton = Button.secondary("exhaustAgent_empyreanagent", "Use Empyrean Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("empyrean")));
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
                                    String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-system-" + tile.getPosition() + "-" + player.getFaction() + "-vs-"
                                        + player2.getFaction();

                                    if (!activeGame.isFoWMode()) {
                                        ButtonHelper.makeACombatThread(activeGame, actionsChannel, player, player2, threadName, tile, event, "space");
                                    } else {
                                        threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-system-" + tile.getPosition() + "-" + player.getColor() + "-vs-" + player2.getColor()
                                            + "-private";
                                        ButtonHelper.makeACombatThread(activeGame, player.getPrivateChannel(), player, player2, threadName, tile, event, "space");
                                        ButtonHelper.makeACombatThread(activeGame, player2.getPrivateChannel(), player2, player, threadName, tile, event, "space");
                                    }
                                }
                            }
                        }
                        if (systemButtons.size() == 2) {
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
                    case "mitosisMech" -> ButtonHelperFactionSpecific.resolveMitosisMech(buttonID, event, activeGame, player, ident, finsFactionCheckerPrefix);
                    case "cardsInfo" -> {
                        String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " here is your cards info";
                        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
                        SOInfo.sendSecretObjectiveInfo(activeGame, player);
                        ACInfo.sendActionCardInfo(activeGame, player);
                        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
                    }
                    case "showGameAgain" -> new ShowGame().simpleShowGame(activeGame, event, null);
                    case "mitosisInf" -> ButtonHelperFactionSpecific.resolveMitosisInf(buttonID, event, activeGame, player, ident);
                    case "doneLanding" -> {
                        if (!event.getMessage().getContentRaw().contains("Moved all units to the space area.")) {
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
                        }

                        String message = "Landed troops. Use buttons to decide if you want to build or finish the activation";
                        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
                        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                            if ("space".equalsIgnoreCase(unitHolder.getName())) {
                                continue;
                            }
                            List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(activeGame, tile, unitHolder.getName());
                            if (players.size() > 1 && !player.getAllianceMembers().contains(players.get(0).getFaction()) && !player.getAllianceMembers().contains(players.get(1).getFaction())) {
                                Player player2 = players.get(0);
                                if (player2 == player) {
                                    player2 = players.get(1);
                                }
                                String threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-system-" + tile.getPosition() + "-" + player.getFaction() + "-vs-"
                                    + player2.getFaction();
                                if (!activeGame.isFoWMode()) {
                                    ButtonHelper.makeACombatThread(activeGame, actionsChannel, player, player2, threadName, tile, event, "ground");
                                } else {
                                    threadName = activeGame.getName() + "-round-" + activeGame.getRound() + "-system-" + tile.getPosition() + "-" + player.getColor() + "-vs-" + player2.getColor()
                                        + "-private";
                                    ButtonHelper.makeACombatThread(activeGame, player.getPrivateChannel(), player, player2, threadName, tile, event, "ground");
                                    ButtonHelper.makeACombatThread(activeGame, player2.getPrivateChannel(), player2, player, threadName, tile, event, "ground");
                                }
                            }
                        }
                        List<Button> systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeGame, event);

                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                        event.getMessage().delete().queue();
                    }
                    case "vote" -> {
                        String pfaction2 = null;
                        if (player != null) {
                            pfaction2 = player.getFaction();
                        }
                        if (pfaction2 != null) {
                            String voteMessage = "Chose to Vote. Click buttons for which outcome to vote for.";
                            String agendaDetails = activeGame.getCurrentAgendaInfo();
                            agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_") + 1,
                                agendaDetails.lastIndexOf("_"));
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
                            scnum2 = Integer.parseInt(lastchar);
                        } catch (NumberFormatException e) {
                            setstatus = false;
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
                        new Turn().execute(event, player, activeGame);
                        event.getMessage().delete().queue();
                        ButtonHelper.updateMap(activeGame, event);
                    }
                    case "quash" -> {
                        int stratCC = player.getStrategicCC();
                        player.setStrategicCC(stratCC - 1);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Quashed agenda. Strategic CCs went from " + stratCC + " -> " + (stratCC - 1));
                        ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(player, activeGame, event);
                        new RevealAgenda().revealAgenda(event, false, activeGame, activeGame.getMainGameChannel());
                        event.getMessage().delete().queue();
                    }
                    case "scoreAnObjective" -> {
                        List<Button> poButtons = new Turn().getScoreObjectiveButtons(event, activeGame);
                        poButtons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                        MessageChannel channel = event.getMessageChannel();
                        if (activeGame.isFoWMode()) {
                            channel = player.getPrivateChannel();
                        }
                        MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to score an objective", poButtons);
                    }
                    case "startChaosMapping" -> ButtonHelperFactionSpecific.firstStepOfChaos(activeGame, player, event);
                    case "orbitolDropFollowUp" -> ButtonHelperFactionSpecific.oribtalDropFollowUp(buttonID, event, activeGame, player, ident);
                    case "dropAMechToo" -> {
                        String message = "Please select the same planet you dropped the infantry on";
                        List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeGame, "mech", "place");
                        buttons.add(Button.danger("orbitolDropExhaust", "Pay for mech"));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                        event.getMessage().delete().queue();
                    }
                    case "orbitolDropExhaust" -> ButtonHelperFactionSpecific.oribtalDropExhaust(buttonID, event, activeGame, player, ident);
                    case "dominusOrb" -> {
                        activeGame.setDominusOrb(true);
                        String purgeOrExhaust = "Purged ";
                        String relicId = "dominusorb";
                        player.removeRelic(relicId);
                        player.removeExhaustedRelic(relicId);
                        String relicName = Mapper.getRelic(relicId).split(";")[0];
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), purgeOrExhaust + Emojis.Relic + " relic: " + relicName);
                        event.getMessage().delete().queue();
                        String message = "Choose a system to move from.";
                        List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeGame, event);
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    }
                    case "getDiscardButtonsACs" -> MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeGame), trueIdentity + " use buttons to discard",
                        ACInfo.getDiscardActionCardButtons(activeGame, player, false));
                    case "eraseMyVote" -> {
                        String pfaction = player.getFaction();
                        if (activeGame.isFoWMode()) {
                            pfaction = player.getColor();
                        }
                        AgendaHelper.eraseVotesOfFaction(activeGame, pfaction);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Erased previous votes made by " + Helper.getFactionIconFromDiscord(player.getFaction())
                                + "\n \n" + AgendaHelper.getSummaryOfVotes(activeGame, true));
                        Button Vote = Button.success(finsFactionCheckerPrefix + "vote",
                            StringUtils.capitalize(player.getFaction()) + " Choose To Vote");
                        Button Abstain = Button.danger(finsFactionCheckerPrefix + "delete_buttons_0",
                            StringUtils.capitalize(player.getFaction()) + " Choose To Abstain");
                        Button ForcedAbstain = Button.secondary("forceAbstainForPlayer_" + player.getFaction(),
                            "(For Others) Abstain for this player");
                        List<Button> buttons = List.of(Vote, Abstain, ForcedAbstain);
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            "Use buttons to vote again. Reminder that this erasing of old votes did not refresh any planets.",
                            buttons);
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
                            String message2 = trueIdentity + "! Your current CCs are " + Helper.getPlayerCCs(player)
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
                                String pF = Helper.getFactionIconFromDiscord(player.getFaction());
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
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with a button press", e);
        }
        GameSaveLoadManager.saveMap(activeGame, event);
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
            String message = "Player: " + Helper.getPlayerPing(player) + " already " + alreadyUsedMessage;
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
        String messageId = event.getInteraction().getMessage().getId();

        Message mainMessage = event.getMessageChannel().retrieveMessageById(messageId).completeAfter(500,
            TimeUnit.MILLISECONDS);

        int matchingFactionReactions = 0;
        for (Player player : activeGame.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                matchingFactionReactions++;
                continue;
            }

            String faction = player.getFaction();
            if (faction == null || faction.isEmpty() || "null".equals(faction)) {
                matchingFactionReactions++;
                continue;
            }

            Emoji reactionEmoji = Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction));
            if (activeGame.isFoWMode()) {
                int index = 0;
                for (Player player_ : activeGame.getPlayers().values()) {
                    if (player_ == player)
                        break;
                    index++;
                }
                reactionEmoji = Emoji.fromFormatted(Helper.getRandomizedEmoji(index, event.getMessageId()));
            }
            MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
            if (reaction != null)
                matchingFactionReactions++;
        }
        int numberOfPlayers = activeGame.getPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
            respondAllPlayersReacted(event, activeGame);
        }
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
                    guildMessageChannel.sendMessage("All players have reacted to this Strategy Card").queueAfter(10,
                        TimeUnit.SECONDS);
                    if (guildMessageChannel instanceof ThreadChannel)
                        ((ThreadChannel) guildMessageChannel).getManager().setArchived(true).queueAfter(5,
                            TimeUnit.MINUTES);
                }
            }
            case "no_when", "no_when_persistent" -> event.getInteraction().getMessage().reply("All players have indicated 'No Whens'").queueAfter(1,
                TimeUnit.SECONDS);
            case "no_after", "no_after_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Afters'").queue();
                AgendaHelper.startTheVoting(activeGame, event);
                event.getMessage().delete().queue();

            }
            case "no_sabotage" -> event.getInteraction().getMessage().reply("All players have indicated 'No Sabotage'").queueAfter(1,
                TimeUnit.SECONDS);

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
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now",
                        buttons);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getGamePing(event.getGuild(),
                        activeGame)
                        + " All players have indicated completion of status phase. Proceed to Strategy Phase.");
                    StartPhase.startPhase(event, activeGame, "strategy");
                }
            }
        }
    }
}
