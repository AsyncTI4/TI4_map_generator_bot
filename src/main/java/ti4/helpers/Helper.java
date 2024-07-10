package ti4.helpers;

import java.awt.Point;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import ti4.ResourceHelper;
import ti4.buttons.ButtonListener;
import ti4.commands.bothelper.ArchiveOldThreads;
import ti4.commands.bothelper.ListOldThreads;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.game.SetOrder;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.commands.status.ScorePublic;
import ti4.commands.tokens.AddCC;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.ColorModel;
import ti4.model.LeaderModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class Helper {

    @Nullable
    public static Player getGamePlayer(Game game, Player initialPlayer, GenericInteractionCreateEvent event, String userID) {
        return getGamePlayer(game, initialPlayer, event.getMember(), userID);
    }

    public static int getCurrentHour() {
        long currentTime = new Date().getTime();
        currentTime = currentTime / 1000;
        currentTime = currentTime % (60 * 60 * 24);
        currentTime = currentTime / (60 * 60);
        return (int) currentTime;
    }

    @Nullable
    public static Player getGamePlayer(Game game, Player initialPlayer, Member member, String userID) {
        Collection<Player> players = game.getPlayers().values();
        if (!game.isCommunityMode()) {
            Player player = game.getPlayer(userID);
            if (player != null)
                return player;
            return initialPlayer;
        }
        if (member == null) {
            Player player = game.getPlayer(userID);
            if (player != null)
                return player;
            return initialPlayer;
        }
        List<Role> roles = member.getRoles();
        for (Player player : players) {
            if (roles.contains(player.getRoleForCommunity())) {
                return player;
            }
            if (player.getTeamMateIDs().contains(member.getUser().getId())) {
                return player;
            }
        }
        return initialPlayer != null ? initialPlayer : game.getPlayer(userID);
    }

    @Nullable
    public static Player getPlayer(Game game, Player player, SlashCommandInteractionEvent event) {
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            if (game.getPlayer(playerID) != null) {
                player = game.getPlayers().get(playerID);
            } else {
                player = null;
            }
        } else if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            factionColor = StringUtils.substringBefore(factionColor, " "); // TO HANDLE UNRESOLVED AUTOCOMPLETE
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : game.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) ||
                    Objects.equals(factionColor, player_.getColor())) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    public static boolean isSaboAllowed(Game game, Player player) {
        if ("pbd100".equalsIgnoreCase(game.getName())) {
            return true;
        }
        if (game.getDiscardActionCards().containsKey("sabo1")
            && game.getDiscardActionCards().containsKey("sabo2")
            && game.getDiscardActionCards().containsKey("sabo3")
            && game.getDiscardActionCards().containsKey("sabo4")) {
            return false;
        }
        if (player.hasTech("tp") && game.getActivePlayerID() != null
            && game.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (!p2.isPassed()) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public static boolean doesAnyoneOwnPlanet(Game game, String planet) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPlanets().contains(planet)) {
                return true;
            }
        }
        return false;
    }

    public static boolean doesAllianceMemberOwnPlanet(Game game, String planet, Player p1) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPlanets().contains(planet) && p1.getAllianceMembers().contains(player.getFaction())) {
                return true;
            }
        }
        return false;
    }

    public static Player getPlayerFromAbility(Game game, String ability) {
        Player player = null;
        if (ability != null) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.isRealPlayer() && player_.hasAbility(ability)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    // TODO: Jazz: This method *should* include base game + pok tiles (+ DS tiles IFF DS mode is set)
    //     - Once the bot is using milty draft settings, we can make this accurately pull in tiles
    //     - from every source available to the active game
    public static List<MiltyDraftTile> getUnusedTiles(Game game) {
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.init(game);

        List<MiltyDraftTile> allTiles = new ArrayList<>(draftManager.getAll()).stream()
            .filter(tile -> game.getTile(tile.getTile().getTileID()) == null)
            .toList();
        return new ArrayList<>(allTiles);
    }

    public static void getRandomBlueTile(Game game, GenericInteractionCreateEvent event) {
        List<MiltyDraftTile> unusedBlueTiles = new ArrayList<>(getUnusedTiles(game).stream()
            .filter(tile -> tile.getTierList().isBlue())
            .toList());
        if (unusedBlueTiles.size() == 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no blue tiles available to draw.");
        } else {
            Collections.shuffle(unusedBlueTiles);
            Tile tile = unusedBlueTiles.get(0).getTile();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You randomly drew the tile: " + tile.getRepresentation());
        }
    }

    public static void getRandomRedTile(Game game, GenericInteractionCreateEvent event) {
        List<MiltyDraftTile> unusedRedTiles = new ArrayList<>(getUnusedTiles(game).stream()
            .filter(tile -> !tile.getTierList().isBlue())
            .toList());
        if (unusedRedTiles.size() == 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no red tiles available to draw.");
        } else {
            Collections.shuffle(unusedRedTiles);
            Tile tile = unusedRedTiles.get(0).getTile();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You randomly drew the tile: " + tile.getRepresentation());
        }
    }

    public static boolean canPlayerConceivablySabo(Player player, Game game) {
        if (player.hasTechReady("it") && player.getStrategicCC() > 0) {
            return true;
        }
        if (player.hasUnit("empyrean_mech")
            && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech).size() > 0) {
            return true;
        }
        return player.getAc() > 0;
    }

    public static boolean shouldPlayerLeaveAReact(Player player, Game game, String messageID) {

        if (player.hasTechReady("it") && player.getStrategicCC() > 0) {
            return false;
        }
        if ((player.getActionCards().containsKey("sabo1") || player.getActionCards().containsKey("sabotage_ds")
            || player.getActionCards().containsKey("sabo2") ||
            player.getActionCards().containsKey("sabo3") || player.getActionCards().containsKey("sabo4")
            || (game.getActionCardDeckSize() + game.getDiscardActionCards().size()) > 180)
            && !ButtonHelper.isPlayerElected(game, player, "censure")
            && !ButtonHelper.isPlayerElected(game, player, "absol_censure")) {
            return false;
        }
        if (player.hasUnit("empyrean_mech")
            && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech).size() > 0) {
            return false;
        }
        if (player.getAc() == 0) {
            return !ButtonListener.checkForASpecificPlayerReact(messageID, player, game);
        }
        if (player.isAFK()) {
            return false;
        }
        if (player.getAutoSaboPassMedian() == 0) {
            return false;
        }
        return !ButtonListener.checkForASpecificPlayerReact(messageID, player, game);
        // int highNum = player.getAutoSaboPassMedian()*6*3/2;
        // int result = ThreadLocalRandom.current().nextInt(1,highNum+1);
        // if(result == highNum){
        // return true;
        // }else{
        // return false;
        // }
    }

    public static void giveMeBackMyAgendaButtons(Game game) {
        List<Button> proceedButtons = new ArrayList<>();
        String msg = "Press this button if the last person forgot to react, but verbally said no whens/afters";
        proceedButtons.add(Button.danger("proceedToVoting", "Skip waiting and start the voting for everyone"));
        proceedButtons.add(Button.primary("transaction", "Transaction"));
        proceedButtons.add(Button.danger("eraseMyVote", "Erase my vote & have me vote again"));
        proceedButtons.add(Button.danger("eraseMyRiders", "Erase my riders"));
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), msg, proceedButtons);
    }

    public static List<Player> getInitativeOrder(Game game) {
        HashMap<Integer, Player> order = new HashMap<>();
        int naaluSC = 0;
        for (Player player : game.getRealPlayers()) {
            int sc = player.getLowestSC();
            String scNumberIfNaaluInPlay = game.getSCNumberIfNaaluInPlay(player, Integer.toString(sc));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                naaluSC = sc;
            }
            order.put(sc, player);
        }
        List<Player> initiativeOrder = new ArrayList<>();
        Integer max = Collections.max(game.getScTradeGoods().keySet());
        if (ButtonHelper.getKyroHeroSC(game) != 1000) {
            max = max + 1;
        }
        if (naaluSC != 0) {
            Player p3 = order.get(naaluSC);
            initiativeOrder.add(p3);
        }
        for (int i = 1; i <= max; i++) {
            if (naaluSC != 0 && i == naaluSC) {
                continue;
            }
            Player p2 = order.get(i);
            if (p2 != null) {
                initiativeOrder.add(p2);
            }
        }
        return initiativeOrder;

    }

    public static List<Player> getInitativeOrderFromThisPlayer(Player p1, Game game) {
        List<Player> players = new ArrayList<>();

        List<Player> initiativeOrder = getInitativeOrder(game);
        boolean found = false;
        for (Player p2 : initiativeOrder) {
            if (p2 == p1) {
                found = true;
                players.add(p1);
            } else {
                if (found) {
                    players.add(p2);
                }
            }
        }
        for (Player p2 : initiativeOrder) {
            if (p2 == p1) {
                found = false;
            } else {
                if (found) {
                    players.add(p2);
                }
            }
        }
        return players;
    }

    public static void resolveQueue(Game game) {

        Player imperialHolder = getPlayerWithThisSC(game, 8);
        //String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        if (game.getStoredValue(key2).length() < 2) {
            return;
        }

        for (Player player : getSpeakerOrderFromThisPlayer(imperialHolder, game)) {
            String message = player.getRepresentation(true, true) + " Drew Queued Secret Objective From Imperial. ";
            if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                game.drawSecretObjective(player.getUserID());
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message = message + ". Drew a second SO due to plausible deniability";
                }
                SOInfo.sendSecretObjectiveInfo(game, player);
                game.setStoredValue(key2,
                    game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (!game.isFoWMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "imperial");
                }
            }
            if (game.getStoredValue(key3).contains(player.getFaction() + "*")
                && game.getStoredValue(key2).length() > 2) {
                if (!game.isFoWMode()) {
                    message = player.getRepresentation(true, true)
                        + " is the one the game is currently waiting on before advancing to the next person, with regards to queued Imperial follows";
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (!game.isFoWMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "imperial");
                }
                break;
            }
        }
    }

    public static void resolvePOScoringQueue(Game game, GenericInteractionCreateEvent event) {
        String key2 = "queueToScorePOs";
        String key3 = "potentialScorePOBlockers";
        String key2b = "queueToScoreSOs";
        String key3b = "potentialScoreSOBlockers";
        if (game.getStoredValue(key2).length() < 2
            || game.getHighestScore() + 1 > game.getVp()) {
            return;
        }
        for (Player player : getInitativeOrder(game)) {
            if (game.getHighestScore() + 1 > game.getVp()) {
                return;
            }
            if (game.getStoredValue(key2).contains(player.getFaction() + "*")
                || game.getStoredValue(key2b).contains(player.getFaction() + "*")) {
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    int poIndex = Integer
                        .parseInt(game.getStoredValue(player.getFaction() + "queuedPOScore"));
                    ScorePublic.scorePO(event, game.getMainGameChannel(), game, player, poIndex);
                    game.setStoredValue(key2,
                        game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                    game.setStoredValue(key3,
                        game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
                }
                if (game.getHighestScore() + 1 > game.getVp()) {
                    return;
                }
                if (game.getStoredValue(key2b).contains(player.getFaction() + "*")) {
                    int soIndex = Integer
                        .parseInt(game.getStoredValue(player.getFaction() + "queuedSOScore"));
                    ScoreSO.scoreSO(event, game, player, soIndex, game.getMainGameChannel());
                    game.setStoredValue(key2b,
                        game.getStoredValue(key2b).replace(player.getFaction() + "*", ""));
                    game.setStoredValue(key3b,
                        game.getStoredValue(key3b).replace(player.getFaction() + "*", ""));
                }
            } else {
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")
                    && game.getStoredValue(key2).length() > 2) {
                    String message = player.getRepresentation(true, true)
                        + " is the one the game is currently waiting on before advancing to the next person, with regards to queued PO Scores";
                    if (game.isFoWMode()) {
                        message = "Waiting on someone else before proceeding with scoring";
                    }
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    break;
                }
                if (game.getStoredValue(key3b).contains(player.getFaction() + "*")
                    && game.getStoredValue(key2).length() > 2) {
                    String message = player.getRepresentation(true, true)
                        + " is the one the game is currently waiting on before advancing to the next person, with regards to queued SO Scores";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    if (game.isFoWMode()) {
                        message = "Waiting on someone else before proceeding with scoring";
                    }
                    break;
                }
            }
        }
    }

    public static void resolveSOScoringQueue(Game game, GenericInteractionCreateEvent event) {
        String key2 = "queueToScoreSOs";
        String key3 = "potentialScoreSOBlockers";
        if (game.getStoredValue(key2).length() < 2
            || game.getHighestScore() + 1 > game.getVp()) {
            return;
        }
        for (Player player : getInitativeOrder(game)) {
            if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                int soIndex = Integer
                    .parseInt(game.getStoredValue(player.getFaction() + "queuedSOScore"));
                ScoreSO.scoreSO(event, game, player, soIndex, game.getMainGameChannel());
                game.setStoredValue(key2,
                    game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                game.setStoredValue(key3,
                    game.getStoredValue(key3).replace(player.getFaction() + "*", ""));
            } else {
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")
                    && game.getStoredValue(key2).length() > 2) {
                    String message = player.getRepresentation(true, true)
                        + " is the one the game is currently waiting on before advancing to the next person, with regards to queued SO Scores";
                    if (game.isFoWMode()) {
                        message = "Waiting on someone else before proceeding with scoring";
                    }
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    break;
                }
            }
        }
    }

    public static Player getPlayerWithThisSC(Game game, int sc) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(sc)) {
                return p2;
            }
        }
        return null;
    }

    public static List<Player> getSpeakerOrderFromThisPlayer(Player player, Game game) {
        List<Player> players = new ArrayList<>();
        boolean found = false;
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                found = true;
                players.add(player);
            } else {
                if (found) {
                    players.add(p2);
                }
            }
        }

        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                found = false;
            } else {
                if (found) {
                    players.add(p2);
                }
            }
        }
        return players;
    }

    public static boolean hasEveryoneResolvedBeforeMe(Player player, String factionsThatHaveResolved,
        List<Player> orderList) {
        for (Player p2 : orderList) {
            if (p2 == player) {
                return true;
            }
            if (!factionsThatHaveResolved.contains(p2.getFaction())) {
                return false;
            }
        }
        return true;
    }

    public static void startOfTurnSaboWindowReminders(Game game, Player player) {
        List<String> messageIDs = new ArrayList<>(game.getMessageIDsForSabo());
        for (String messageID : messageIDs) {
            if (!ButtonListener.checkForASpecificPlayerReact(messageID, player, game)) {
                game.getMainGameChannel().retrieveMessageById(messageID).queue(mainMessage -> {
                    Emoji reactionEmoji = Emoji.fromFormatted(player.getFactionEmoji());
                    if (game.isFoWMode()) {
                        int index = 0;
                        for (Player player_ : game.getPlayers().values()) {
                            if (player_ == player)
                                break;
                            index++;
                        }
                        reactionEmoji = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, messageID));
                    }

                    MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                    if (reaction == null) {
                        Calendar rightNow = Calendar.getInstance();
                        if (rightNow.get(Calendar.DAY_OF_YEAR) - mainMessage.getTimeCreated().getDayOfYear() > 2 || rightNow.get(Calendar.DAY_OF_YEAR) - mainMessage.getTimeCreated().getDayOfYear() < -100) {
                            game.removeMessageIDForSabo(messageID);
                        }
                    }
                });
            }
        }
    }

    public static void checkAllSaboWindows(Game game) {
        List<String> messageIDs = new ArrayList<>(game.getMessageIDsForSabo());
        for (Player player : game.getRealPlayers()) {
            if (player.getAutoSaboPassMedian() == 0) {
                continue;
            }
            int highNum = player.getAutoSaboPassMedian() * 6 * 3 / 2;
            int result = ThreadLocalRandom.current().nextInt(1, highNum + 1);
            boolean shouldDoIt = result == highNum;
            if (shouldDoIt || !canPlayerConceivablySabo(player, game)) {
                for (String messageID : messageIDs) {
                    if (shouldPlayerLeaveAReact(player, game, messageID)) {
                        String message = game.isFoWMode() ? "No sabotage" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, messageID, game);
                    }
                }
            }
            if ("agendawaiting".equals(game.getCurrentPhase())) {
                int highNum2 = player.getAutoSaboPassMedian() * 4 / 2;
                int result2 = ThreadLocalRandom.current().nextInt(1, highNum2 + 1);
                boolean shouldDoIt2 = result2 == highNum2;
                if (shouldDoIt2) {
                    String whensID = game.getLatestWhenMsg();
                    if (!AgendaHelper.doesPlayerHaveAnyWhensOrAfters(player)
                        && !ButtonListener.checkForASpecificPlayerReact(whensID, player, game)) {
                        String message = game.isFoWMode() ? "No whens" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, whensID, game);
                    }
                    String aftersID = game.getLatestAfterMsg();
                    if (!AgendaHelper.doesPlayerHaveAnyWhensOrAfters(player)
                        && !ButtonListener.checkForASpecificPlayerReact(aftersID, player, game)) {
                        String message = game.isFoWMode() ? "No afters" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, aftersID, game);
                    }
                }
            }

        }
    }

    public static Player getPlayerFromUnlockedLeader(Game game, String leader) {
        Player player = null;
        if (leader != null) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.isRealPlayer() && player_.hasLeaderUnlocked(leader)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    public static Player getPlayerFromUnit(Game game, String unit) {
        Player player = null;
        if (unit != null) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_.isRealPlayer() && player_.getUnitsOwned().contains(unit)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    @Nullable
    public static String getColor(Game game, SlashCommandInteractionEvent event) {
        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
        if (factionColorOption != null) {
            String colorFromString = getColorFromString(game, factionColorOption.getAsString());
            if (Mapper.isValidColor(colorFromString)) {
                return colorFromString;
            }
        } else {
            String userID = event.getUser().getId();
            Player foundPlayer = game.getPlayers().values().stream()
                .filter(player -> player.getUserID().equals(userID)).findFirst().orElse(null);
            foundPlayer = getGamePlayer(game, foundPlayer, event, null);
            if (foundPlayer != null) {
                return foundPlayer.getColor();
            }
        }
        return null;
    }

    public static String getColorFromString(Game game, String factionColor) {
        factionColor = AliasHandler.resolveColor(factionColor);
        factionColor = AliasHandler.resolveFaction(factionColor);
        for (Player player_ : game.getPlayers().values()) {
            if (Objects.equals(factionColor, player_.getFaction()) ||
                Objects.equals(factionColor, player_.getColor())) {
                return player_.getColor();
            }
        }
        return factionColor;
    }

    @Nullable
    public static String getDamagePath() {
        String tokenPath = ResourceHelper.getInstance().getResourceFromFolder("extra/", "marker_damage.png",
            "Could not find damage token file");
        if (tokenPath == null) {
            BotLogger.log("Could not find token: marker_damage");
            return null;
        }
        return tokenPath;
    }

    @Nullable
    public static String getAdjacencyOverridePath(int direction) {
        String file = "adjacent_";
        switch (direction) {
            case 0, 5, 1 -> file += "north.png";
            case 2, 4, 3 -> file += "south.png";
        }
        String tokenPath = ResourceHelper.getInstance().getResourceFromFolder("extra/", file,
            "Could not find adjacency file for direction: " + direction);
        if (tokenPath == null) {
            BotLogger.log("Could not find token: " + file);
            return null;
        }
        return tokenPath;
    }

    public static void addMirageToTile(Tile tile) {
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        if (unitHolders.get(Constants.MIRAGE) == null) {
            Point mirageCenter = new Point(Constants.MIRAGE_POSITION.x + Constants.MIRAGE_CENTER_POSITION.x,
                Constants.MIRAGE_POSITION.y + Constants.MIRAGE_CENTER_POSITION.y);
            Planet planetObject = new Planet(Constants.MIRAGE, mirageCenter);
            unitHolders.put(Constants.MIRAGE, planetObject);
        }
    }

    public static String getDateRepresentation(long dateInfo) {
        Date date = new Date(dateInfo);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd");
        return simpleDateFormat.format(date);
    }

    public static String getDateRepresentationTIGL(long dateInfo) {
        Date date = new Date(dateInfo);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM.dd.yyyy");
        return simpleDateFormat.format(date);
    }

    public static String getDateTimeRepresentation(long dateInfo) {
        Date date = new Date(dateInfo);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return simpleDateFormat.format(date);
    }

    public static int getDateDifference(String date1, String date2) {
        if (date1 == null || date1.length() == 0) {
            return 1000;
        }
        date1 = date1.replace(".", "_");
        date2 = date2.replace(".", "_");
        int year1 = Integer.parseInt(date1.split("_")[0]);
        int year2 = Integer.parseInt(date2.split("_")[0]);
        int month1 = Integer.parseInt(date1.split("_")[1]);
        int month2 = Integer.parseInt(date2.split("_")[1]);
        int day1 = Integer.parseInt(date1.split("_")[2]);
        int day2 = Integer.parseInt(date2.split("_")[2]);
        return (year2 - year1) * 365 + (month2 - month1) * 30 + (day2 - day1);
    }

    public static String getRoleMentionByName(Guild guild, String roleName) {
        if (roleName == null) {
            return "[@Oopsidoops no name]";
        }
        List<Role> roles = guild.getRolesByName(roleName, true);
        if (!roles.isEmpty()) {
            return roles.get(0).getAsMention();
        }
        return "[@" + roleName + "]";
    }

    public static String getSCAsMention(int sc, Game game) {
        //StrategyCardModel scModel = game.getStrategyCardSet().getStrategyCardModelByInitiative(sc).orElse(null);
        if (game.isHomeBrewSCMode()) {
            return getSCName(sc, game);
        }
        return switch (sc) {
            case 1 -> Emojis.SC1Mention;
            case 2 -> Emojis.SC2Mention;
            case 3 -> Emojis.SC3Mention;
            case 4 -> Emojis.SC4Mention;
            case 5 -> Emojis.SC5Mention;
            case 6 -> Emojis.SC6Mention;
            case 7 -> Emojis.SC7Mention;
            case 8 -> Emojis.SC8Mention;
            default -> "**SC" + sc + "**";
        };
    }

    public static String getSCRepresentation(Game game, int sc) {
        if (game.isHomeBrewSCMode())
            return "SC #" + sc + " " + getSCName(sc, game);
        return getSCAsMention(sc, game);
    }

    public static String getSCName(int sc, Game game) {
        if (Optional.ofNullable(game.getScSetID()).isPresent() && !"null".equals(game.getScSetID())) {
            return Mapper.getStrategyCardSets().get(game.getScSetID()).getCardValues().get(sc);
        }
        return "**SC" + sc + "**";
    }

    public static Integer getSCNumber(String sc) {
        return switch (sc.toLowerCase()) {
            case "leadership" -> 1;
            case "diplomacy" -> 2;
            case "politics" -> 3;
            case "construction" -> 4;
            case "trade" -> 5;
            case "warfare" -> 6;
            case "technology" -> 7;
            case "imperial" -> 8;
            default -> 0;
        };
    }

    public static File getSCImageFile(Integer sc, Game game) {
        String scSet = game.getScSetID();
        if (Optional.ofNullable(game.getScSetID()).isEmpty()
            || "null".equals(game.getScSetID())) { // I don't know *why* this is a thing that can happen, but it is
            scSet = "pok";
        }
        boolean gameWithGroupedSCs = "pbd100".equals(game.getName()) || "pbd500".equals(game.getName()) && !"tribunal".equals(scSet);
        if (gameWithGroupedSCs) {
            //char scValue = String.valueOf(sc).charAt(0);
            scSet = scSet.replace("pbd100", "pok");
            scSet = scSet.replace("pbd1000", "pok");
        }
        StrategyCardModel scModel = game.getStrategyCardSet().getStrategyCardModelByInitiative(sc).orElse(null);
        String scImagePath = scModel.getImageFilePath();
        if (scImagePath == null)
            scImagePath = ResourceHelper.getInstance().getResourceFromFolder("strat_cards/", "sadFace.png",
                "Could not find SC image!");

        return new File(scImagePath);
    }

    public static Emoji getPlayerEmoji(Game game, Player player, Message message) {
        Emoji emojiToUse;
        emojiToUse = Emoji.fromFormatted(player.getFactionEmoji());
        String messageId = message.getId();

        if (game.isFoWMode()) {
            int index = 0;
            for (Player player_ : game.getPlayers().values()) {
                if (player_ == player)
                    break;
                index++;
            }
            emojiToUse = Emoji.fromFormatted(Emojis.getRandomizedEmoji(index, messageId));
        }

        return emojiToUse;
    }

    public static String getPlanetRepresentationPlusEmoji(String planet) {
        String planetProper = Mapper.getPlanetRepresentations().get(planet);
        return Emojis.getPlanetEmoji(planet) + " " + (Objects.isNull(planetProper) ? planet : planetProper);
    }

    public static String getBasicTileRep(String tileID) {
        StringBuilder name = new StringBuilder(TileHelper.getTile(tileID).getName());
        if (TileHelper.getTile(tileID).getPlanets().size() > 0) {
            name.append(" (");
        }
        for (String planet : TileHelper.getTile(tileID).getPlanets()) {
            name.append(Mapper.getPlanet(planet).getResources()).append("/")
                .append(Mapper.getPlanet(planet).getInfluence()).append(", ");
        }
        if (TileHelper.getTile(tileID).getPlanets().size() > 0) {
            name = new StringBuilder(name.substring(0, name.length() - 2) + ")");
        }
        return name.toString();
    }

    public static String getPlanetRepresentation(String planet, Game game) {
        planet = planet.toLowerCase().replace(" ", "");
        planet = planet.replace("'", "");
        planet = planet.replace("-", "");
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planet));
        Planet planet2 = unitHolder;
        if (planet2 == null) {
            return planet + " bot error. Tell fin";
        }
        boolean containsDMZ = unitHolder.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
        if (unitHolder != null && containsDMZ) {
            return Mapper.getPlanetRepresentations().get(AliasHandler.resolvePlanet(planet)) + " (" + planet2.getResources()
                + "/" + planet2.getInfluence() + ") [DMZ]";
        }
        return Mapper.getPlanetRepresentations().get(AliasHandler.resolvePlanet(planet)) + " (" + planet2.getResources()
            + "/" + planet2.getInfluence() + ")";
    }

    public static void acceptTransactionOffer(Player p1, Player p2, Game game, ButtonInteractionEvent event) {

        String summary = "The following transaction between " + p1.getRepresentation(false, false) + " and" + p2.getRepresentation(false, false) + " has been accepted:\n" + buildTransactionOffer(p1, p2, game, false);
        List<String> transactionItems = p1.getTransactionItems();
        List<Player> players = new ArrayList<Player>();
        players.add(p1);
        players.add(p2);
        boolean debtOnly = true;
        MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), "A transaction between " + p1.getRepresentation(false, false) + " and" + p2.getRepresentation(false, false) + " has been ratified");
        for (Player sender : players) {
            Player receiver = p2;
            if (sender == p2) {
                receiver = p1;
            }
            for (String item : transactionItems) {
                if (item.contains("sending" + sender.getFaction()) && item.contains("receiving" + receiver.getFaction())) {
                    String thingToTransact = item.split("_")[2];
                    String furtherDetail = item.split("_")[3];
                    int amountToTransact = 1;
                    if (((thingToTransact.equalsIgnoreCase("ACs") || thingToTransact.equalsIgnoreCase("PNs")) && furtherDetail.contains("generic"))) {
                        amountToTransact = Integer.parseInt("" + furtherDetail.charAt(furtherDetail.length() - 1));
                        furtherDetail = furtherDetail.substring(0, furtherDetail.length() - 1);
                    }
                    String spoofedButtonID = "send_" + thingToTransact + "_" + receiver.getFaction() + "_" + furtherDetail;
                    if (!thingToTransact.toLowerCase().contains("debt")) {
                        debtOnly = false;
                    }
                    switch (thingToTransact) {
                        case "ACs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    for (int x = 0; x < amountToTransact; x++) {
                                        String buttonID = "transact_ACs_" + sender.getFaction();
                                        ButtonHelper.resolveSpecificTransButtonsOld(game, sender, buttonID, event);
                                    }
                                }
                                default -> {
                                    ButtonHelper.resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                                }
                            }
                        }
                        case "PNs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, sender, p2);
                                    String message = sender.getRepresentation(true, true)
                                        + "Please select the PN you would like to send";
                                    MessageHelper.sendMessageToChannelWithButtons(sender.getCardsInfoThread(), message, stuffToTransButtons);
                                }
                                default -> {
                                    ButtonHelper.resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                                }
                            }
                        }

                        case "Planets" -> {
                            ButtonHelperFactionSpecific.resolveHacanMechTradeStepOne(sender, game, event, "send_" + furtherDetail + "_" + receiver.getFaction());
                        }
                        case "AlliancePlanets" -> {
                            String exhausted = "exhausted";
                            if (!furtherDetail.contains(exhausted)) {
                                exhausted = "refreshed";
                            }
                            furtherDetail = furtherDetail.replace(exhausted, "");

                            ButtonHelper.resolveAllianceMemberPlanetTrade(sender, game, event, "send_" + furtherDetail + "_" + receiver.getFaction() + "_" + exhausted);
                        }
                        case "dmz" -> {
                            ButtonHelper.resolveDMZTrade(sender, game, event, "send_" + furtherDetail + "_" + receiver.getFaction());
                        }
                        default -> {
                            ButtonHelper.resolveSpecificTransButtonPress(game, sender, spoofedButtonID, event, false);
                        }
                    }

                }
            }
        }
        p1.clearTransactionItemsWith(p2);
        if (!debtOnly) {
            ButtonHelperAbilities.pillageCheck(p2, game);
            ButtonHelperAbilities.pillageCheck(p1, game);
        }
        MessageHelper.sendMessageToChannel(p1.getCardsInfoThread(), summary);
        MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), summary);
    }

    public static String buildTransactionOffer(Player p1, Player p2, Game game, boolean publiclyShared) {
        List<String> transactionItems = p1.getTransactionItems();
        String wholeSummary = "";
        List<Player> players = new ArrayList<Player>();
        players.add(p1);
        players.add(p2);
        for (Player sender : players) {
            Player receiver = p2;
            if (sender == p2) {
                receiver = p1;
            }
            int num = 1;
            String summary = "**" + sender.getRepresentation(false, false) + " gives " + receiver.getRepresentation(false, false) + " the following:**\n";
            if (publiclyShared) {
                summary = sender.getFactionEmoji() + " gives " + receiver.getFactionEmoji() + " the following: ";
                num = 0;
            }
            for (String item : transactionItems) {
                if (item.contains("sending" + sender.getFaction()) && item.contains("receiving" + receiver.getFaction())) {
                    String thingToTransact = item.split("_")[2];
                    String furtherDetail = item.split("_")[3];
                    int amountToTransact = 1;
                    if (thingToTransact.equalsIgnoreCase("tgs") || thingToTransact.contains("Debt") || thingToTransact.equalsIgnoreCase("comms")) {
                        amountToTransact = Integer.parseInt(furtherDetail);
                    }
                    if (thingToTransact.equalsIgnoreCase("frags") || ((thingToTransact.equalsIgnoreCase("PNs") || thingToTransact.equalsIgnoreCase("ACs")) && furtherDetail.contains("generic"))) {
                        amountToTransact = Integer.parseInt("" + furtherDetail.charAt(furtherDetail.length() - 1));
                        furtherDetail = furtherDetail.substring(0, furtherDetail.length() - 1);
                    }
                    switch (thingToTransact) {
                        case "TGs" -> {
                            summary = summary + amountToTransact + " " + Emojis.tg + "\n";
                        }
                        case "SendDebt" -> {
                            summary = summary + "Send " + amountToTransact + " debt\n";
                        }
                        case "ClearDebt" -> {
                            summary = summary + "Clear " + amountToTransact + " debt\n";
                        }
                        case "Comms" -> {
                            summary = summary + amountToTransact + " " + Emojis.comm + "\n";
                        }
                        case "shipOrders" -> {
                            summary = summary + Mapper.getRelic(furtherDetail).getName() + Emojis.axis + "\n";
                        }
                        case "starCharts" -> {
                            summary = summary + Mapper.getRelic(furtherDetail).getName() + Emojis.DiscordantStars + "\n";
                        }
                        case "ACs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    summary = summary + amountToTransact + " " + Emojis.ActionCard + " to be specified verbally\n";
                                }
                                default -> {
                                    int acNum = Integer.parseInt(furtherDetail);
                                    String acID = null;
                                    if (!sender.getActionCards().containsValue(acNum)) {
                                        continue;
                                    }
                                    for (Map.Entry<String, Integer> ac : sender.getActionCards().entrySet()) {
                                        if (ac.getValue().equals(acNum)) {
                                            acID = ac.getKey();
                                        }
                                    }
                                    if (publiclyShared) {
                                        summary = summary + Emojis.ActionCard + "\n";
                                    } else {
                                        summary = summary + Emojis.ActionCard + " " + Mapper.getActionCard(acID).getName() + "\n";
                                    }
                                }
                            }
                        }
                        case "PNs" -> {
                            switch (furtherDetail) {
                                case "generic" -> {
                                    summary = summary + amountToTransact + " " + Emojis.PN + " to be specified verbally\n";
                                }
                                default -> {
                                    String id = null;
                                    int pnIndex;
                                    try {
                                        pnIndex = Integer.parseInt(furtherDetail);
                                        for (Map.Entry<String, Integer> pn : sender.getPromissoryNotes().entrySet()) {
                                            if (pn.getValue().equals(pnIndex)) {
                                                id = pn.getKey();
                                            }
                                        }
                                    } catch (NumberFormatException e) {
                                        id = furtherDetail.replace("fin9", "_");
                                    }
                                    if (id == null) {
                                        continue;
                                    }
                                    if (publiclyShared) {
                                        summary = summary + Emojis.PN + "\n";
                                    } else {
                                        summary = summary + Emojis.PN + " " + StringUtils.capitalize(Mapper.getPromissoryNote(id).getColor().orElse("")) + " " + Mapper.getPromissoryNote(id).getName() + "\n";
                                    }
                                }
                            }
                        }
                        case "Frags" -> {
                            summary = summary + amountToTransact + " " + getFragEmoji(furtherDetail) + "\n";
                        }
                        case "Planets", "AlliancePlanets", "dmz" -> {
                            summary = summary + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(furtherDetail, game) + "\n";
                        }
                        case "action" -> {
                            summary = summary + "An in-game " + furtherDetail + " action\n";
                        }
                    }

                }
            }
            if (StringUtils.countMatches(summary, "\n") > num) {
                if (publiclyShared) {
                    summary = summary.replace("\n", "; ");
                    summary = summary.substring(0, summary.length() - 2);
                }
                wholeSummary = wholeSummary + "\n" + summary;

            } else {
                wholeSummary = wholeSummary + "\n" + summary + getNothingMessage();
            }
        }

        return wholeSummary;
    }

    public static String getFragEmoji(String frag) {
        frag = frag.toLowerCase();
        switch (frag) {
            case "crf":
                return Emojis.CFrag;
            case "irf":
                return Emojis.IFrag;
            case "hrf":
                return Emojis.HFrag;
        }
        return Emojis.UFrag;
    }

    public static String getNothingMessage() {
        int result = ThreadLocalRandom.current().nextInt(1, 11);
        switch (result) {
            case 1:
                return "Nothing But Respect And Good Will";
            case 2:
                return "Some Pocket Lint";
            case 3:
                return "Sunshine and Rainbows";
            case 4:
                return "A Feeling Of Accomplishment";
            case 5:
                return "A Crisp High Five";
            case 6:
                return "A Well Written Thank-You Note";
            case 7:
                return "Heartfelt Thanks";
            case 8:
                return "The Best Vibes";
            case 9:
                return "A Bot's Blessing For Your Trouble";
            case 10:
                return "Good Karma";
        }
        return "Nothing";
    }

    public static String getPlanetRepresentationPlusEmojiPlusResourceInfluence(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = unitHolder;
            String techType = "";
            String techEmoji = "";
            if (Mapper.getPlanet(planetID) != null && Mapper.getPlanet(planetID).getTechSpecialties() != null
                && Mapper.getPlanet(planetID).getTechSpecialties().size() > 0) {
                techType = Mapper.getPlanet(planetID).getTechSpecialties().get(0).toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(game, AliasHandler.resolvePlanet(planetID));
            }
            if (!"".equalsIgnoreCase(techType)) {
                switch (techType) {
                    case "propulsion" -> techEmoji = Emojis.PropulsionTech;
                    case "warfare" -> techEmoji = Emojis.WarfareTech;
                    case "cybernetic" -> techEmoji = Emojis.CyberneticTech;
                    case "biotic" -> techEmoji = Emojis.BioticTech;
                }
            }
            return getPlanetRepresentationPlusEmoji(planetID) + " " + Emojis.getResourceEmoji(planet.getResources())
                + Emojis.getInfluenceEmoji(planet.getInfluence()) + techEmoji;
        }
    }

    public static String getPlanetRepresentationPlusEmojiPlusInfluence(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = unitHolder;
            return getPlanetRepresentationPlusEmoji(planetID) + " " + Emojis.getInfluenceEmoji(planet.getInfluence());
        }
    }

    public static String getPlanetRepresentationPlusEmojiPlusResources(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = unitHolder;
            String techType = "";
            String techEmoji = "";
            if (Mapper.getPlanet(planetID).getTechSpecialties() != null
                && Mapper.getPlanet(planetID).getTechSpecialties().size() > 0) {
                techType = Mapper.getPlanet(planetID).getTechSpecialties().get(0).toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(game, planetID);
            }
            if (!"".equalsIgnoreCase(techType)) {
                switch (techType) {
                    case "propulsion" -> techEmoji = Emojis.PropulsionTech;
                    case "warfare" -> techEmoji = Emojis.WarfareTech;
                    case "cybernetic" -> techEmoji = Emojis.CyberneticTech;
                    case "biotic" -> techEmoji = Emojis.BioticTech;
                }
            }
            return getPlanetRepresentationPlusEmoji(planetID) + " " + Emojis.getResourceEmoji(planet.getResources())
                + techEmoji;
        }
    }

    public static List<Button> getPlanetRefreshButtons(GenericInteractionCreateEvent event, Player player,
        Game game) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : planets) {
            Button button = Button.success("refresh_" + planet, getPlanetRepresentation(planet, game));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static String getPlayerDependingOnFog(Game game, Player player) {
        String ident;

        if (game.isFoWMode()) {
            ident = player.getColor();
        } else {
            ident = player.getFactionEmoji();
        }
        return ident;
    }

    public static List<Button> getRemainingSCButtons(GenericInteractionCreateEvent event, Game game,
        Player playerPicker) {
        List<Button> scButtons = new ArrayList<>();

        for (Integer sc : game.getSCList()) {
            if (sc <= 0)
                continue; // some older games have a 0 in the list of SCs
            boolean held = false;
            for (Player player : game.getPlayers().values()) {
                if (player == null || player.getFaction() == null) {
                    continue;
                }
                if (player.getSCs() != null && player.getSCs().contains(sc) && !game.isFoWMode()) {
                    held = true;
                    break;
                }
            }
            if (held)
                continue;
            Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
            Button button;
            String label = " ";
            if (game.getScTradeGoods().get(sc) > 0 && !game.isFoWMode()) {
                label = "[has " + game.getScTradeGoods().get(sc) + " tg]";
            }
            if (sc == ButtonHelper.getKyroHeroSC(game)) {
                label = label + " Kyro Hero Cursed";
            }
            if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")
                && !game.isHomeBrewSCMode()) {

                button = Button.secondary("FFCC_" + playerPicker.getFaction() + "_scPick_" + sc, label)
                    .withEmoji(scEmoji);
            } else {
                button = Button.secondary("FFCC_" + playerPicker.getFaction() + "_scPick_" + sc, "" + sc + label);
            }
            scButtons.add(button);
        }
        return scButtons;
    }

    public static List<Button> getPlanetExhaustButtons(Player player, Game game) {
        return getPlanetExhaustButtons(player, game, "both");
    }

    public static List<Button> getPlanetExhaustButtons(Player player, Game game, String whatIsItFor) {
        if (game.getStoredValue("resetSpend").isEmpty()) {
            player.resetSpentThings();
        } else {
            game.setStoredValue("resetSpend", "");
        }
        player.resetOlradinPolicyFlags();
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        for (String planet : planets) {

            if (planet.contains("custodia") || planet.contains("ghoti")) {
                Button button = Button.danger("spend_" + planet, getPlanetRepresentation(planet, game));
                planetButtons.add(button);
                continue;
            }
            String techType = "none";
            if (Mapper.getPlanet(planet).getTechSpecialties() != null
                && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) {
                techType = Mapper.getPlanet(planet).getTechSpecialties().get(0).toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(game, planet);
            }
            if ("none".equalsIgnoreCase(techType)) {
                Button button = Button.danger("spend_" + planet + "_" + whatIsItFor,
                    getPlanetRepresentation(planet, game));
                planetButtons.add(button);
            } else {
                Button techB = Button.danger("spend_" + planet + "_" + whatIsItFor,
                    getPlanetRepresentation(planet, game));
                switch (techType) {
                    case "propulsion" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
                    case "warfare" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                    case "cybernetic" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                    case "biotic" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
                }
                planetButtons.add(techB);
            }

        }
        return planetButtons;
    }

    public static List<Button> getPlanetPlaceUnitButtons(Player player, Game game, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        player.resetProducedUnits();
        for (String planet : planets) {
            if (planet.contains("ghoti") || planet.contains("custodia")) {
                continue;
            }
            Button button = Button.danger("FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + planet,
                getPlanetRepresentation(planet, game));
            button = button.withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit)));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getHSPlanetPlaceUnitButtons(Player player, Game game, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        player.resetProducedUnits();
        for (String planet : planets) {
            if (planet.contains("ghoti") || planet.contains("custodia")) {
                continue;
            }
            if (game.getTileFromPlanet(planet) != player.getHomeSystemTile()) {
                continue;
            }
            Button button = Button.danger("FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + planet,
                getPlanetRepresentation(planet, game));
            button = button.withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord(unit)));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getTileWithShipsPlaceUnitButtons(Player player, Game game, String unit,
        String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        for (Tile tile : tiles) {
            Button button = Button.danger(
                "FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getTileWithTrapsPlaceUnitButtons(Player player, Game game, String unit,
        String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithTrapsInTheSystem(game);
        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                Button button = Button.danger(
                    "FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player));
                planetButtons.add(button);
            }
        }
        return planetButtons;
    }

    public static List<Button> getTileForCheiranHeroPlaceUnitButtons(Player player, Game game, String unit,
        String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesForCheiranHero(player, game);
        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                Button button = Button.danger(
                    "FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player));
                planetButtons.add(button);
            }
        }
        return planetButtons;
    }

    public static List<Button> getTileWithShipsNTokenPlaceUnitButtons(Player player, Game game, String unit,
        String prefix, @Nullable ButtonInteractionEvent event) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        for (Tile tile : tiles) {
            if (AddCC.hasCC(event, player.getColor(), tile)) {
                Button button = Button.danger(
                    "FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player));
                planetButtons.add(button);
            }
        }
        return planetButtons;
    }

    public static String buildSpentThingsMessageForVoting(Player player, Game game, boolean justVoteTotal) {
        List<String> spentThings = player.getSpentThingsThisWindow();
        String msg = player.getFactionEmoji() + " used the following: \n";
        int votes = 0;
        int tg = player.getSpentTgsThisWindow();
        for (String thing : spentThings) {
            int count = 0;
            if (!thing.contains("_")) {
                BotLogger.log("Caught the following thing in the voting " + thing + " in game " + game.getName());
                continue;
            }
            String secondHalf = thing.split("_")[1];
            String flavor = thing.split("_")[0];
            if (flavor.contains("planet")) {
                count = AgendaHelper.getSpecificPlanetsVoteWorth(player, game, secondHalf);
            } else {
                count = Integer.parseInt(thing.split("_")[1]);
            }
            if (flavor.contains("tg")) {
                votes = votes + count * 2;
            } else {
                votes = votes + count;
            }
            msg = msg + "> ";
            switch (flavor) {
                case "tg" -> {
                    msg = msg + "Spent " + player.getSpentTgsThisWindow() + " tgs for " + tg * 2 + " votes " + "\n";
                }
                case "infantry" -> {
                    msg = msg + "Spent " + player.getSpentInfantryThisWindow() + " infantry for "
                        + player.getSpentInfantryThisWindow() + " votes " + "\n";
                }
                case "planet" -> {
                    msg = msg + getPlanetRepresentation(secondHalf, game) + " for " + count + " votes " + "\n";
                }
                case "absolShard" -> {
                    msg = msg + "Used Absol Shard of the Throne for " + count + " votes " + "\n";
                }
                case "dsghotg" -> {
                    msg = msg + "Exhausted some silly Ghoti Tech for " + count + " votes " + "\n";
                }
                case "absolsyncretone" -> {
                    msg = msg + "Used Absol Syncretone for " + count + " votes " + "\n";
                }
                case "augerscommander" -> {
                    msg = msg + "Used Augurs Commander for " + count + " votes " + "\n";
                }
                case "zeal" -> {
                    msg = msg + "Used Zeal Ability for " + count + " votes " + "\n";
                }
                case "predictive" -> {
                    msg = msg + "Used Predictive Intelligence for " + count + " votes " + "\n";
                }
                case "specialVotes" -> {
                    msg = msg + "Used Special Votes for " + count + " votes " + "\n";
                }
                case "representative" -> {
                    msg = msg + "Got 1 vote for representative government " + "\n";
                }
                case "distinguished" -> {
                    msg = msg + "Used the AC Distinguished Councillor for 5 votes " + "\n";
                }
                case "absolRexControlRepresentative" -> {
                    msg = msg + "Got 1 vote for controlling rex while representative government is in play" + "\n";
                }
                case "bloodPact" -> {
                    msg = msg + "Got 4 votes from voting the same way as another Blood Pact member" + "\n";
                }

            }
        }
        if (game.getCurrentAgendaInfo().contains("Secret") && Mapper.getSecretObjectivesJustNames().get(game.getLatestOutcomeVotedFor()) != null) {
            msg = msg + "For a total of **" + votes + "** votes on the outcome "
                + Mapper.getSecretObjectivesJustNames().get(game.getLatestOutcomeVotedFor());
        } else {
            msg = msg + "For a total of **" + votes + "** votes on the outcome "
                + StringUtils.capitalize(game.getLatestOutcomeVotedFor());
        }
        if (justVoteTotal) {
            return "" + votes;
        }

        return msg;
    }

    public static void refreshPlanetsOnTheRevote(Player player, Game game) {
        List<String> spentThings = player.getSpentThingsThisWindow();
        int tg = player.getSpentTgsThisWindow();
        player.setTg(player.getTg() + tg);
        for (String thing : spentThings) {
            if (!thing.contains("_")) {
                BotLogger.log("Caught the following thing in the voting " + thing + " in game " + game.getName());
                continue;
            }
            String secondHalf = thing.split("_")[1];
            String flavor = thing.split("_")[0];
            if (flavor.contains("planet") && player.getExhaustedPlanets().contains(secondHalf)) {
                player.refreshPlanet(secondHalf);
            }
        }
        player.resetSpentThings();

    }

    public static boolean doesListContainButtonID(List<Button> buttons, String buttonID) {
        for (Button button : buttons) {
            if (button.getId().equalsIgnoreCase(buttonID)) {
                return true;
            }
        }
        return false;
    }

    public static void refreshPlanetsOnTheRespend(Player player, Game game) {
        List<String> spentThings = new ArrayList<>();
        spentThings.addAll(player.getSpentThingsThisWindow());
        int tg = player.getSpentTgsThisWindow();

        player.setTg(player.getTg() + tg);
        for (String thing : spentThings) {
            if (thing.contains("tg_")) {
                player.removeSpentThing(thing);
            }
            if (thing.contains("_")) {
                continue;
            }

            if (player.getExhaustedPlanets().contains(thing)) {
                player.refreshPlanet(thing);
                player.removeSpentThing(thing);
            }
        }
        game.setStoredValue("resetSpend", "no");

    }

    public static String buildSpentThingsMessage(Player player, Game game, String resOrInfOrBoth) {
        List<String> spentThings = player.getSpentThingsThisWindow();
        String msg = player.getFactionEmoji() + " exhausted the following: \n";
        int res = 0;
        int inf = 0;
        //boolean tech = false;
        if (resOrInfOrBoth.contains("tech")) {
            resOrInfOrBoth = resOrInfOrBoth.replace("tech", "");
            //tech = true;
        }
        int tg = player.getSpentTgsThisWindow();
        boolean xxcha = player.hasLeaderUnlocked("xxchahero");
        int bestRes = 0;
        int keleresAgent = 0;
        for (String thing : spentThings) {
            boolean found = false;
            System.out.println("Spent thing: " + thing);
            switch (thing) {
                case "sarween" -> {
                    msg += "> Used Sarween Tools " + Emojis.CyberneticTech + "\n";
                    res += 1;
                    found = true;
                }
                case "absol_sarween" -> {
                    int sarweenVal = 1 + calculateCostOfProducedUnits(player, game, true) / 5;
                    msg += "> Used Sarween Tools " + Emojis.CyberneticTech + " for " + sarweenVal + " resources\n";
                    res += sarweenVal;
                    found = true;
                }
            }
            if (!found && !thing.contains("tg_") && !thing.contains("boon")
                && !thing.contains("ghoti") && !thing.contains("aida")
                && !thing.contains("commander") && !thing.contains("Agent")) {
                Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(thing));
                msg = msg + "> ";
                if (unitHolder == null) {
                    if (thing.contains("reduced comms")) {
                        String comms = StringUtils.substringAfter(thing, "by ");
                        comms = StringUtils.substringBefore(comms, " (");
                        keleresAgent = Integer.parseInt(comms);
                    }
                    msg = msg + thing + "\n";
                } else {
                    Planet planet = unitHolder;
                    Tile t = game.getTileFromPlanet(planet.getName());
                    if (t != null && !t.isHomeSystem()) {
                        if (planet.getResources() > bestRes) {
                            bestRes = planet.getResources();
                        }
                    }
                    if ("res".equalsIgnoreCase(resOrInfOrBoth)) {
                        if (xxcha) {
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game) + "\n";
                            res = res + planet.getSumResourcesInfluence();
                        } else {
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResources(thing, game) + "\n";
                            res = res + planet.getResources();
                        }
                    } else if ("inf".equalsIgnoreCase(resOrInfOrBoth)) {
                        if (xxcha) {
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game) + "\n";
                            inf = inf + planet.getSumResourcesInfluence();
                        } else {
                            msg = msg + getPlanetRepresentationPlusEmojiPlusInfluence(thing, game) + "\n";
                            inf = inf + planet.getInfluence();
                        }
                    } else if ("freelancers".equalsIgnoreCase(resOrInfOrBoth)) {
                        if (xxcha) {
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game) + "\n";
                            res = res + planet.getSumResourcesInfluence();
                        } else {
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game) + "\n";
                            res = res + Math.max(planet.getInfluence(), planet.getResources());
                        }
                    } else {
                        if (xxcha) {
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game) + "\n";
                            inf = inf + planet.getSumResourcesInfluence();
                            res = res + planet.getSumResourcesInfluence();
                        } else {
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game) + "\n";
                            inf = inf + planet.getInfluence();
                            res = res + planet.getResources();
                        }
                    }
                }
            } else {

                if (thing.contains("boon")) {
                    msg = msg + "> Used Boon Relic " + Emojis.Relic + "\n";
                    res = res + 1;
                }
                if (thing.contains("warmachine")) {
                    msg = msg + "> Used War Machine " + Emojis.ActionCard + "\n";
                    res = res + 1;
                }
                if (thing.contains("aida")) {
                    msg = msg + "> Exhausted AIDEV ";
                    if (thing.contains("_")) {
                        res = res + ButtonHelper.getNumberOfUnitUpgrades(player);
                        msg = msg + " for " + ButtonHelper.getNumberOfUnitUpgrades(player) + " resources ";
                    } else {
                        msg = msg + " for a tech skip on a unit upgrade ";
                    }
                    msg = msg + Emojis.WarfareTech + "\n";
                }
                if (thing.contains("commander") || thing.contains("Gledge Agent")) {
                    msg = msg + "> " + thing + "\n";
                } else if (thing.contains("Winnu Agent")) {
                    msg = msg + "> " + thing + "\n";
                    res = res + 2;
                } else if (thing.contains("Zealots Agent")) {
                    msg = msg + "> " + thing + "(Best Resources found were " + bestRes + ")\n";
                    inf = inf + bestRes;
                } else if (thing.contains("Agent")) {
                    msg = msg + "> " + thing + "\n";
                } else if (thing.contains("custodia")) {
                    //game.getPlanetsInfo().get("custodiavigilia")
                    msg = msg + "> " + "Custodia Vigilia (2/3)" + "\n";
                    res = res + 2;
                    inf = inf + 3;
                } else if (thing.contains("ghoti")) {
                    msg = msg + "> " + "Ghoti (3/3)" + "\n";
                    res = res + 3;
                    inf = inf + 3;
                }
            }
        }
        res = res + tg + keleresAgent;
        inf = inf + tg + keleresAgent;
        if (tg > 0) {
            msg = msg + "> Spent " + tg + " tgs " + Emojis.getTGorNomadCoinEmoji(game) + " ("
                + (player.getTg() + tg) + "->" + player.getTg() + ") \n";
            if (player.hasTech("mc")) {
                res = res + tg + keleresAgent;
                inf = inf + tg + keleresAgent;
                msg = msg + "> Counted the trade goods twice due to Mirror Computing \n";
            }
        }

        if ("res".equalsIgnoreCase(resOrInfOrBoth)) {
            msg = msg + "For a total spend of **" + res + " Resources**";
        } else if ("inf".equalsIgnoreCase(resOrInfOrBoth)) {
            msg = msg + "For a total spend of **" + inf + " Influence**";
        } else if ("freelancers".equalsIgnoreCase(resOrInfOrBoth)) {
            msg = msg + "For a total spend of **" + res + " Resources** (counting influence as resources)";
        } else {
            msg = msg + "For a total spend of **" + res + " Resources** or **" + inf + " Influence**";
        }
        return msg;
    }

    public static String buildProducedUnitsMessage(Player player, Game game) {
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        String msg = "";
        List<String> uniquePlaces = new ArrayList<>();
        for (String unit : producedUnits.keySet()) {
            String tilePos = unit.split("_")[1];
            String planetOrSpace = unit.split("_")[2];
            if (!uniquePlaces.contains(tilePos + "_" + planetOrSpace)) {
                uniquePlaces.add(tilePos + "_" + planetOrSpace);
            }
        }
        for (String uniquePlace : uniquePlaces) {
            String tilePos2 = uniquePlace.split("_")[0];
            String planetOrSpace2 = uniquePlace.split("_")[1];
            Tile tile = game.getTileByPosition(tilePos2);
            String localPlace = "__**In " + tile.getRepresentationForButtons(game, player) + " ";
            if ("space".equalsIgnoreCase(planetOrSpace2)) {
                localPlace = localPlace + " in the space area:**__ \n";
            } else {
                localPlace = localPlace + " on the planet " + getPlanetRepresentation(planetOrSpace2, game)
                    + ":**__ \n";
            }
            for (String unit : producedUnits.keySet()) {
                String tilePos = unit.split("_")[1];
                String planetOrSpace = unit.split("_")[2];
                String un = unit.split("_")[0];
                UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(un), player.getColor());
                UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
                if (uniquePlace.equalsIgnoreCase(tilePos + "_" + planetOrSpace)) {
                    localPlace = localPlace + player.getFactionEmoji() + " produced " + producedUnits.get(unit)
                        + " " + removedUnit.getUnitEmoji() + "\n";
                }
            }
            msg = msg + localPlace;
        }
        msg = msg + "For the total cost of: **" + calculateCostOfProducedUnits(player, game, true)
            + " Resources**";
        if (calculateCostOfProducedUnits(player, game, false) > 2) {
            msg = msg + " (total units produced: " + calculateCostOfProducedUnits(player, game, false) + ").";
        }
        return msg;
    }

    public static void resetProducedUnits(Player player, Game game, GenericInteractionCreateEvent event) {
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();

        for (String unit : producedUnits.keySet()) {
            String tilePos = unit.split("_")[1];
            String planetOrSpace = unit.split("_")[2];
            if ("space".equalsIgnoreCase(planetOrSpace)) {
                planetOrSpace = "";
            } else {
                planetOrSpace = " " + planetOrSpace;
            }
            Tile tile = game.getTileByPosition(tilePos);
            String un = unit.split("_")[0];
            // UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(un),
            // player.getColor());
            new ti4.commands.units.RemoveUnits().unitParsing(event, player.getColor(), tile,
                producedUnits.get(unit) + " " + AliasHandler.resolveUnit(un) + planetOrSpace, game);
        }

        player.resetProducedUnits();
    }

    public static int getProductionValueOfUnitHolder(Player player, Game game, Tile tile, UnitHolder uH) {
        int productionValueTotal = 0;
        for (UnitKey unit : uH.getUnits().keySet()) {
            if (unit.getColor().equalsIgnoreCase(player.getColor())) {
                if (unit.getUnitType() == UnitType.TyrantsLament && player.getUnitsByAsyncID(unit.asyncID()).isEmpty()) {
                    player.addOwnedUnitByID("tyrantslament");
                }
                if (unit.getUnitType() == UnitType.PlenaryOrbital && player.getUnitsByAsyncID(unit.asyncID()).isEmpty()) {
                    player.addOwnedUnitByID("plenaryorbital");
                }
                if (player == null || player.getUnitsByAsyncID(unit.asyncID()).size() < 1) {
                    continue;
                }
                UnitModel unitModel = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                int productionValue = unitModel.getProductionValue();
                if ("fs".equals(unitModel.getAsyncId()) && player.ownsUnit("ghoti_flagship")) {
                    productionValueTotal = productionValueTotal + player.getFleetCC();
                }
                if ("sd".equals(unitModel.getAsyncId()) && (productionValue == 2 || productionValue == 4 || player.ownsUnit("mykomentori_spacedock2") || player.ownsUnit("miltymod_spacedock2"))) {
                    if (uH instanceof Planet planet) {
                        if (player.hasUnit("celdauri_spacedock") || player.hasUnit("celdauri_spacedock2")) {
                            productionValue = Math.max(planet.getResources(), planet.getInfluence()) + productionValue;
                        } else {
                            productionValue = planet.getResources() + productionValue;
                        }
                    }
                    if (ButtonHelper.isPlayerElected(game, player, "absol_minsindus")) {
                        productionValue = productionValue + 4;
                    }
                }
                if (productionValue > 0 && player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValue++;
                }
                productionValueTotal = productionValueTotal + productionValue * uH.getUnits().get(unit);
            }
        }
        String planet = uH.getName();
        int planetUnitVal = 0;
        if (Constants.MECATOLS.contains(planet) && player.hasTech("iihq") && player.controlsMecatol(false)) {
            productionValueTotal = productionValueTotal + 3;
            planetUnitVal = 3;
        }
        for (String token : uH.getTokenList()) {
            if (token.contains("orbital_foundries") && planetUnitVal < 2) {
                productionValueTotal = productionValueTotal + 2;
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValueTotal++;
                }
                planetUnitVal = 2;
            }

            if (token.contains("automatons") && planetUnitVal < 3) {
                productionValueTotal = productionValueTotal - planetUnitVal;
                planetUnitVal = 3;
                productionValueTotal = productionValueTotal + 3;
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValueTotal++;
                }
            }
        }
        if (player.hasTech("ah") && planetUnitVal < 1 && (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0
            || uH.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)) {
            productionValueTotal = productionValueTotal + 1;
            planetUnitVal = 1;
            if (player.hasRelic("boon_of_the_cerulean_god")) {
                productionValueTotal++;
            }
        } else {
            if (player.hasTech("absol_ie") && planetUnitVal < 1 && player.getPlanets().contains(uH.getName())) {
                productionValueTotal = productionValueTotal + 1;
                planetUnitVal = 1;
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValueTotal++;
                }
            } else {
                if (player.getPlanets().contains(uH.getName())
                    && player.hasTech("dsbentg") && planetUnitVal < 1
                    && (!uH.getTokenList().isEmpty() || (Mapper.getPlanet(planet).getTechSpecialties() != null
                        && Mapper.getPlanet(planet).getTechSpecialties().size() > 0))) {
                    productionValueTotal = productionValueTotal + 1;
                    if (player.hasRelic("boon_of_the_cerulean_god")) {
                        productionValueTotal++;
                    }
                    planetUnitVal = 1;
                }
            }
        }
        if (player.getPlanets().contains(uH.getName()) && player.getLeader("nokarhero").map(Leader::isActive).orElse(false)) {
            productionValueTotal = productionValueTotal + 3;
            productionValueTotal = productionValueTotal - planetUnitVal;
            planetUnitVal = 3;
            if (player.hasRelic("boon_of_the_cerulean_god")) {
                productionValueTotal++;
            }
        }

        return productionValueTotal;

    }

    public static int getProductionValue(Player player, Game game, Tile tile, boolean singleDock) {
        int productionValueTotal = 0;
        if (!singleDock) {
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                productionValueTotal = productionValueTotal + getProductionValueOfUnitHolder(player, game, tile, uH);
            }
            if (tile.isSupernova() && player.hasTech("mr") && FoWHelper.playerHasUnitsInSystem(player, tile)) {
                productionValueTotal = productionValueTotal + 5;
            }
            if (tile.getUnitHolders().size() == 1 && player.hasTech("dsmorty")
                && FoWHelper.playerHasShipsInSystem(player, tile)) {
                productionValueTotal = productionValueTotal + 2;
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValueTotal++;
                }
            }
        } else {
            int highestProd = 0;
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                for (UnitKey unit : uH.getUnits().keySet()) {
                    if (unit.getColor().equalsIgnoreCase(player.getColor())) {
                        UnitModel unitModel = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                        if (!"sd".equals(unitModel.getAsyncId())) {
                            continue;
                        }
                        int productionValue = unitModel.getProductionValue();
                        if ("sd".equals(unitModel.getAsyncId()) && (productionValue == 2 || productionValue == 4
                            || player.ownsUnit("mykomentori_spacedock2")
                            || player.ownsUnit("miltymod_spacedock2"))) {
                            if (uH instanceof Planet planet) {
                                if (player.hasUnit("celdauri_spacedock") || player.hasUnit("celdauri_spacedock2")) {
                                    productionValue = Math.max(planet.getResources(), planet.getInfluence()) + productionValue;
                                } else {
                                    productionValue = planet.getResources() + productionValue;
                                }
                            }
                        }
                        if (productionValue > 0 && player.hasRelic("boon_of_the_cerulean_god")) {
                            productionValue++;
                        }
                        if (productionValue > highestProd) {
                            highestProd = productionValue;
                        }
                    }
                }
            }
            productionValueTotal = highestProd;
        }
        if (productionValueTotal > 0 && game.playerHasLeaderUnlockedOrAlliance(player, "gledgecommander")) {
            productionValueTotal = productionValueTotal
                + ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "sd");
        }
        if (productionValueTotal > 0 && player.hasAbility("policy_the_environment_plunder")) {
            productionValueTotal = productionValueTotal - 2;
        }
        return productionValueTotal;
    }

    public static int calculateCostOfProducedUnits(Player player, Game game, boolean wantCost) {
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        int cost = 0;
        int numInf = 0;
        int numFF = 0;
        int totalUnits = 0;
        boolean regulated = ButtonHelper.isLawInPlay(game, "conscription")
            || ButtonHelper.isLawInPlay(game, "absol_conscription");
        for (String unit : producedUnits.keySet()) {
            String unit2 = unit.split("_")[0];
            if (unit.contains("gf")) {
                numInf = numInf + producedUnits.get(unit);
            } else if (unit.contains("ff")) {
                numFF = numFF + producedUnits.get(unit);
            } else {
                UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit2), player.getColor());
                UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
                if ("flagship".equalsIgnoreCase(removedUnit.getBaseType())
                    && game.playerHasLeaderUnlockedOrAlliance(player, "nomadcommander")) {
                    //cost = cost; // nomad alliance
                } else {
                    cost = cost + (int) removedUnit.getCost() * producedUnits.get(unit);
                }
                totalUnits = totalUnits + producedUnits.get(unit);
            }
        }
        if (regulated) {
            cost = cost + numInf + numFF;
        } else {
            if (player.ownsUnit("cymiae_infantry") || player.ownsUnit("cymiae_infantry2")) {
                cost = cost + numInf;
            } else {
                cost = cost + ((numInf + 1) / 2);
            }
            cost = cost + ((numFF + 1) / 2);
        }
        totalUnits = totalUnits + numInf + numFF;
        if (wantCost) {
            return cost;
        } else {
            return totalUnits;
        }

    }

    public static List<Button> getPlaceUnitButtonsForSaarCommander(Player player, Tile origTile, Game game,
        String placePrefix) {
        List<Button> unitButtons = new ArrayList<>();

        if (game.playerHasLeaderUnlockedOrAlliance(player, "saarcommander")) {
            for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)) {
                if (tile.getPosition().equalsIgnoreCase(origTile.getPosition())
                    || FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                    continue;
                }
                for (UnitHolder uH : tile.getUnitHolders().values()) {
                    if (player.getUnitsOwned().contains("saar_spacedock")
                        || player.getUnitsOwned().contains("saar_spacedock2")
                        || uH.getUnitCount(UnitType.Spacedock, player) > 0) {
                        if (uH instanceof Planet planet) {
                            String pp = planet.getName();
                            Button inf1Button = Button.success(
                                "FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_" + pp,
                                "Produce 1 Infantry on " + getPlanetRepresentation(pp, game));
                            inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Emojis.Saar));
                            unitButtons.add(inf1Button);
                        } else {
                            Button inf1Button = Button.success(
                                "FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_space"
                                    + tile.getPosition(),
                                "Produce 1 Inf in " + tile.getPosition() + " space");
                            inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Emojis.Saar));
                            unitButtons.add(inf1Button);
                        }
                    }
                }
                Button ff1Button = Button.success(
                    "FFCC_" + player.getFaction() + "_" + placePrefix + "_fighter_" + tile.getPosition(),
                    "Produce 1 Fighter in " + tile.getPosition());
                ff1Button = ff1Button.withEmoji(Emoji.fromFormatted(Emojis.Saar));
                unitButtons.add(ff1Button);
            }
        }

        return unitButtons;
    }

    public static List<Button> getPlaceUnitButtons(GenericInteractionCreateEvent event, Player player, Game game, Tile tile, String warfareNOtherstuff, String placePrefix) {
        List<Button> unitButtons = new ArrayList<>();
        player.resetProducedUnits();
        int resourcelimit = 100;
        String planetInteg = "";
        if (warfareNOtherstuff.contains("integrated")) {
            planetInteg = warfareNOtherstuff.replace("integrated", "");
            UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planetInteg, game);
            if (plan != null && plan instanceof Planet planetUh) {
                resourcelimit = planetUh.getResources();
            }
        }
        boolean regulated = ButtonHelper.isLawInPlay(game, "conscription")
            || ButtonHelper.isLawInPlay(game, "absol_conscription");
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String tp = tile.getPosition();
        if (!"muaatagent".equalsIgnoreCase(warfareNOtherstuff)) {
            if (player.hasWarsunTech() && resourcelimit > 9) {
                Button wsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_warsun_" + tp,
                    "Produce Warsun");
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "warsun") > 1) {
                    wsButton = Button.secondary("FFCC_" + player.getFaction() + "_" + placePrefix + "_warsun_" + tp,
                        "Produce Warsun");
                }
                wsButton = wsButton.withEmoji(Emoji.fromFormatted(Emojis.warsun));
                unitButtons.add(wsButton);
            }
            if (player.ownsUnit("ghemina_flagship_lady") && resourcelimit > 7) {
                Button wsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_lady_" + tp,
                    "Produce The Lady");
                wsButton = wsButton.withEmoji(Emoji.fromFormatted(Emojis.flagship));
                unitButtons.add(wsButton);
            }
            Button fsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_flagship_" + tp,
                "Produce Flagship");
            if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "flagship") > 0) {
                fsButton = Button.secondary("FFCC_" + player.getFaction() + "_" + placePrefix + "_flagship_" + tp,
                    "Produce Flagship");
            }
            fsButton = fsButton.withEmoji(Emoji.fromFormatted(Emojis.flagship));
            if (resourcelimit > 7) {
                unitButtons.add(fsButton);
            }
        }
        Button dnButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_dreadnought_" + tp,
            "Produce Dreadnought");
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought") > 4) {
            dnButton = Button.secondary("FFCC_" + player.getFaction() + "_" + placePrefix + "_dreadnought_" + tp,
                "Produce Dreadnought");
        }
        dnButton = dnButton.withEmoji(Emoji.fromFormatted(Emojis.dreadnought));
        if (resourcelimit > 3) {
            unitButtons.add(dnButton);
        }
        Button cvButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_carrier_" + tp,
            "Produce Carrier");
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "carrier") > 3) {
            cvButton = Button.secondary("FFCC_" + player.getFaction() + "_" + placePrefix + "_carrier_" + tp,
                "Produce Carrier");
        }
        cvButton = cvButton.withEmoji(Emoji.fromFormatted(Emojis.carrier));
        if (resourcelimit > 2) {
            unitButtons.add(cvButton);
        }
        Button caButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_cruiser_" + tp,
            "Produce Cruiser");
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser") > 7) {
            caButton = Button.secondary("FFCC_" + player.getFaction() + "_" + placePrefix + "_cruiser_" + tp,
                "Produce Cruiser");
        }
        caButton = caButton.withEmoji(Emoji.fromFormatted(Emojis.cruiser));
        if (resourcelimit > 1) {
            unitButtons.add(caButton);
        }
        Button ddButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_destroyer_" + tp,
            "Produce Destroyer");
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "destroyer") > 7) {
            ddButton = Button.secondary("FFCC_" + player.getFaction() + "_" + placePrefix + "_destroyer_" + tp,
                "Produce Destroyer");
        }
        ddButton = ddButton.withEmoji(Emoji.fromFormatted(Emojis.destroyer));
        unitButtons.add(ddButton);
        Button ff1Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_fighter_" + tp,
            "Produce 1 Fighter");
        ff1Button = ff1Button.withEmoji(Emoji.fromFormatted(Emojis.fighter));
        unitButtons.add(ff1Button);
        if (!"arboCommander".equalsIgnoreCase(warfareNOtherstuff) && !"freelancers".equalsIgnoreCase(warfareNOtherstuff)
            && unitHolders.size() < 4 && !regulated && !"sling".equalsIgnoreCase(warfareNOtherstuff)
            && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)
            && getPlaceUnitButtonsForSaarCommander(player, tile, game, placePrefix).size() == 0) {
            Button ff2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2ff_" + tp,
                "Produce 2 Fighters");
            ff2Button = ff2Button.withEmoji(Emoji.fromFormatted(Emojis.fighter));
            unitButtons.add(ff2Button);
        }

        if (!"arboCommander".equalsIgnoreCase(warfareNOtherstuff) && !"freelancers".equalsIgnoreCase(warfareNOtherstuff)
            && !"sling".equalsIgnoreCase(warfareNOtherstuff) && !warfareNOtherstuff.contains("integrated")
            && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {

            if (player.hasUnexhaustedLeader("argentagent")) {
                Button argentButton = Button.success(
                    "FFCC_" + player.getFaction() + "_" + "exhaustAgent_argentagent_" + tile.getPosition(),
                    "Use Argent Agent");
                argentButton = argentButton.withEmoji(Emoji.fromFormatted(Emojis.Argent));
                unitButtons.add(argentButton);
            }
            if (player.hasTechReady("sar")) {
                Button argentButton = Button.success("sarMechStep1_" + tile.getPosition() + "_" + warfareNOtherstuff,
                    "Use Self Assembly Routines");
                argentButton = argentButton.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                unitButtons.add(argentButton);
            }
            if (player.getActionCards().containsKey("war_machine1")
                || player.getActionCards().containsKey("war_machine2")
                || player.getActionCards().containsKey("war_machine3")
                || player.getActionCards().containsKey("war_machine4")
                || player.getActionCards().containsKey("war_machine_ds")) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation()
                    + " Reminder that you have war machine and this is the window for it");
            }
        }
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet planet && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                if ("warfare".equalsIgnoreCase(warfareNOtherstuff) && !"mr".equalsIgnoreCase(unitHolder.getName())) {
                    if (unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) < 1
                        && unitHolder.getUnitCount(UnitType.CabalSpacedock, player.getColor()) < 1
                        && !player.hasUnit("saar_spacedock") && !player.hasUnit("absol_saar_spacedock")
                        && !player.hasUnit("absol_saar_spacedock2") && !player.hasUnit("saar_spacedock2")
                        && !player.hasUnit("ghoti_flagship")) {
                        continue;
                    }
                }
                if ("tacticalAction".equalsIgnoreCase(warfareNOtherstuff) && getProductionValueOfUnitHolder(player, game, tile, unitHolder) == 0 && getProductionValueOfUnitHolder(player, game, tile, tile.getUnitHolders().get("space")) == 0) {
                    continue;
                }
                if (warfareNOtherstuff.contains("integrated") && !unitHolder.getName().equalsIgnoreCase(planetInteg)) {
                    continue;
                }

                String pp = planet.getName();
                if ("genericBuild".equalsIgnoreCase(warfareNOtherstuff)) {
                    Button sdButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_sd_" + pp,
                        "Place 1 Space Dock on " + getPlanetRepresentation(pp, game));
                    sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                    unitButtons.add(sdButton);
                    Button pdsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_pds_" + pp,
                        "Place 1 PDS on " + getPlanetRepresentation(pp, game));
                    pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
                    unitButtons.add(pdsButton);
                }
                Button inf1Button = Button.success(
                    "FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_" + pp,
                    "Produce 1 Infantry on " + getPlanetRepresentation(pp, game));
                inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                unitButtons.add(inf1Button);
                if (!"genericBuild".equalsIgnoreCase(warfareNOtherstuff)
                    && !"freelancers".equalsIgnoreCase(warfareNOtherstuff)
                    && !"arboCommander".equalsIgnoreCase(warfareNOtherstuff) && !regulated && unitHolders.size() < 4
                    && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)
                    && getPlaceUnitButtonsForSaarCommander(player, tile, game, placePrefix).size() == 0) {
                    Button inf2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_" + pp,
                        "Produce 2 Infantry on " + getPlanetRepresentation(pp, game));
                    inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    unitButtons.add(inf2Button);
                }
                Button mfButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_mech_" + pp, "Produce Mech on " + getPlanetRepresentation(pp, game));
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") > 3) {
                    mfButton = Button.secondary("FFCC_" + player.getFaction() + "_" + placePrefix + "_mech_" + pp, "Produce Mech on " + getPlanetRepresentation(pp, game));
                }
                mfButton = mfButton.withEmoji(Emoji.fromFormatted(Emojis.mech));
                if (resourcelimit > 1) {
                    unitButtons.add(mfButton);
                }

            } else if (ButtonHelper.canIBuildGFInSpace(game, player, tile, warfareNOtherstuff)
                && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                Button inf1Button = Button.success(
                    "FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_space" + tile.getPosition(),
                    "Produce 1 Infantry in space");
                inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                unitButtons.add(inf1Button);
                if (!"genericBuild".equalsIgnoreCase(warfareNOtherstuff)
                    && !"freelancers".equalsIgnoreCase(warfareNOtherstuff)
                    && !"arboCommander".equalsIgnoreCase(warfareNOtherstuff) && unitHolders.size() < 4
                    && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)
                    && getPlaceUnitButtonsForSaarCommander(player, tile, game, placePrefix).size() == 0) {
                    Button inf2Button = Button.success(
                        "FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_space" + tile.getPosition(),
                        "Produce 2 Infantry in space");
                    inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    unitButtons.add(inf2Button);
                }
                Button mfButton = Button.success(
                    "FFCC_" + player.getFaction() + "_" + placePrefix + "_mech_space" + tile.getPosition(),
                    "Produce Mech in space");
                mfButton = mfButton.withEmoji(Emoji.fromFormatted(Emojis.mech));
                unitButtons.add(mfButton);
            }
        }
        if (!"sling".equalsIgnoreCase(warfareNOtherstuff) && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {
            unitButtons.addAll(getPlaceUnitButtonsForSaarCommander(player, tile, game, placePrefix));
        }
        if ("place".equalsIgnoreCase(placePrefix)) {
            Button DoneProducingUnits = Button.danger("deleteButtons_" + warfareNOtherstuff + "_" + tile.getPosition(), "Done Producing Units");
            unitButtons.add(DoneProducingUnits);
            unitButtons.add(Button.secondary("resetProducedThings", "Reset Build"));
        }
        if (player.hasTech("yso")) {
            if ("sling".equalsIgnoreCase(warfareNOtherstuff)) {
                List<Button> unitButtons2 = new ArrayList<>();
                unitButtons2.add(Button.secondary("startYinSpinner", "Yin Spin 2 Duders")
                    .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation(true, true) + " you can use this to Yin Spin", unitButtons2);
            } else {
                unitButtons.add(Button.secondary("startYinSpinner", "Yin Spin 2 Duders")
                    .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
            }
        }

        return unitButtons;
    }

    public static List<Button> getPlanetSystemDiploButtons(GenericInteractionCreateEvent event, Player player,
        Game game, boolean ac, Player mahact) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
        if (mahact == null) {
            for (String planet : planets) {
                if (!getPlanetRepresentation(planet, game).toLowerCase().contains("mecatol") || ac) {
                    Button button = Button.secondary(finsFactionCheckerPrefix + "diplo_" + planet + "_" + "diploP",
                        getPlanetRepresentation(planet, game) + " System");
                    planetButtons.add(button);
                }
            }
        } else {
            for (Tile tile : game.getTileMap().values()) {
                if (FoWHelper.playerHasUnitsInSystem(player, tile) && !tile.isHomeSystem()) {
                    Button button = Button.secondary(finsFactionCheckerPrefix + "diplo_" + tile.getPosition() + "_"
                        + "mahact" + mahact.getColor(), tile.getRepresentation() + " System");
                    planetButtons.add(button);
                }
            }

        }

        return planetButtons;
    }

    public static int getPlanetResources(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return 0;
        } else {
            Planet planet = unitHolder;
            return planet.getResources();
        }
    }

    public static int getPlanetInfluence(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return 0;
        } else {
            Planet planet = unitHolder;
            return planet.getInfluence();
        }
    }

    @Deprecated
    public static String getLeaderRepresentation(Leader leader, boolean includeTitle, boolean includeAbility,
        boolean includeUnlockCondition) {
        String leaderID = leader.getId();

        LeaderModel leaderModel = Mapper.getLeader(leaderID);
        if (leaderModel == null) {
            BotLogger.log("Invalid `leaderID=" + leaderID + "` caught within `Helper.getLeaderRepresentation`");
            return leader.getId();
        }

        String leaderName = leaderModel.getName();
        String leaderTitle = leaderModel.getTitle();
        String heroAbilityName = leaderModel.getAbilityName().orElse("");
        String leaderAbilityWindow = leaderModel.getAbilityWindow();
        String leaderAbilityText = leaderModel.getAbilityText();
        String leaderUnlockCondition = leaderModel.getUnlockCondition();

        StringBuilder representation = new StringBuilder();
        representation.append(Emojis.getFactionLeaderEmoji(leader)).append(" **").append(leaderName).append("**");
        if (includeTitle)
            representation.append(": ").append(leaderTitle); // add title
        if (includeAbility && Constants.HERO.equals(leader.getType()))
            representation.append(" - ").append("__**").append(heroAbilityName).append("**__"); // add hero ability name
        if (includeAbility)
            representation.append(" - *").append(leaderAbilityWindow).append("* ").append(leaderAbilityText); // add
                                                                                                                                  // ability
        if (includeUnlockCondition)
            representation.append(" *Unlock:* ").append(leaderUnlockCondition);

        return representation.toString();
    }

    public static String getLeaderRepresentation(Player player, String leaderID, boolean includeTitle,
        boolean includeAbility) {
        return getLeaderRepresentation(player.getLeader(leaderID).orElse(null), includeTitle, includeAbility, false);
    }

    public static String getLeaderRepresentation(Leader leader, boolean includeTitle, boolean includeAbility) {
        return getLeaderRepresentation(leader, includeTitle, includeAbility, false);
    }

    public static String getLeaderShortRepresentation(Leader leader) {
        return getLeaderRepresentation(leader, false, false, false);
    }

    public static String getLeaderMediumRepresentation(Leader leader) {
        return getLeaderRepresentation(leader, true, false, false);
    }

    public static String getLeaderFullRepresentation(Leader leader) {
        return getLeaderRepresentation(leader, true, true, false);
    }

    public static String getLeaderLockedRepresentation(Leader leader) {
        return getLeaderRepresentation(leader, true, true, true);
    }

    public static void isCCCountCorrect(GenericInteractionCreateEvent event, Game game, String color) {
        int ccCount = getCCCount(game, color);
        informUserCCOverLimit(event, game, color, ccCount);
    }

    public static int getCCCount(Game game, String color) {
        int ccCount = 0;
        if (color == null) {
            return 0;
        }
        Map<String, Tile> tileMap = game.getTileMap();
        for (Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
            Tile tile = tileEntry.getValue();
            boolean hasCC = AddCC.hasCC(null, color, tile);
            if (hasCC) {
                ccCount++;
            }
        }
        String factionColor = AliasHandler.resolveColor(color.toLowerCase());
        factionColor = AliasHandler.resolveFaction(factionColor);
        Player player = game.getPlayerFromColorOrFaction(factionColor);
        if (player == null) {
            return 0;
        }
        for (Player player_ : game.getRealPlayers()) {
            if (player == player_) {
                ccCount += player_.getStrategicCC();
                ccCount += player_.getTacticalCC();
                ccCount += player_.getFleetCC();
            } else if (player_.hasAbility("imperia") || player_.hasAbility("edict")) {
                for (String color_ : player_.getMahactCC()) {
                    ColorModel ccColor = Mapper.getColor(color_);
                    if (player.getColor().equalsIgnoreCase(ccColor.getName())) {
                        ccCount++;
                    }
                }
            }
        }
        return ccCount;
    }

    private static void informUserCCOverLimit(GenericInteractionCreateEvent event, Game game, String color,
        int ccCount) {
        boolean ccCountIsOver = ccCount > 16;
        if (ccCountIsOver && game.getCCNPlasticLimit()) {
            Player player = null;
            String factionColor = AliasHandler.resolveColor(color.toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : game.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) ||
                    Objects.equals(factionColor, player_.getColor())) {
                    player = player_;
                }
            }

            String msg = game.getPing() + " ";
            if (!game.isFoWMode()) {
                if (player != null) {
                    msg += player.getFactionEmoji() + " " + player.getFaction() + " ";
                    msg += player.getPing() + " ";
                }
            }

            msg += "(" + color + ") is over the CC limit of 16. CC used: " + ccCount;
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        }
    }

    /**
     * @param game : ti4.map.Map object
     * @return String : TTS/TTPG Map String
     */
    public static String getMapString(Game game) {
        List<String> tilePositions = new ArrayList<>();
        tilePositions.add("000");

        int ringCountMax = game.getRingCount();
        int ringCount = 1;
        int tileCount = 1;
        while (ringCount <= ringCountMax) {
            String position = "" + ringCount + (tileCount < 10 ? "0" + tileCount : tileCount);
            tilePositions.add(position);
            tileCount++;
            if (tileCount > ringCount * 6) {
                tileCount = 1;
                ringCount++;
            }
        }

        List<String> sortedTilePositions = tilePositions.stream().sorted(Comparator.comparingInt(Integer::parseInt))
            .toList();
        Map<String, Tile> tileMap = new HashMap<>(game.getTileMap());
        StringBuilder sb = new StringBuilder();
        for (String position : sortedTilePositions) {
            boolean missingTile = true;
            for (Tile tile : tileMap.values()) {
                if (tile.getPosition().equals(position)) {
                    String tileID = AliasHandler.resolveStandardTile(tile.getTileID()).toUpperCase();
                    if ("000".equalsIgnoreCase(position) && "18".equalsIgnoreCase(tileID)) { // Mecatol Rex in Centre
                                                                                             // Position
                        sb.append("{18}");
                    } else if ("000".equalsIgnoreCase(position) && !"18".equalsIgnoreCase(tileID)) { // Something else
                                                                                                     // is in the Centre
                                                                                                     // Position
                        sb.append("{").append(tileID).append("}");
                    } else {
                        sb.append(tileID);
                    }
                    missingTile = false;
                    break;
                }
            }
            if (missingTile && "000".equalsIgnoreCase(position)) {
                sb.append("{-1}");
            } else if (missingTile) {
                sb.append("-1");
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    public static Integer getPlayerResourcesAvailable(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        int resourcesCount = 0;
        if (player.hasLeaderUnlocked("xxchahero")) {
            int resourcesCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> planet).mapToInt(Planet::getInfluence).sum();
            resourcesCount += resourcesCountFromPlanetsRes;
        }

        int resourcesCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getResources).sum();

        resourcesCount += resourcesCountFromPlanets;
        return resourcesCount;
    }

    public static Integer getPlayerResourcesTotal(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        int resourcesCount = 0;
        if (player.hasLeaderUnlocked("xxchahero")) {
            int resourcesCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> planet).mapToInt(Planet::getInfluence).sum();
            resourcesCount += resourcesCountFromPlanetsRes;
        }
        int resourcesCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getResources).sum();

        resourcesCount += resourcesCountFromPlanets;
        return resourcesCount;
    }

    public static Integer getPlayerOptimalResourcesAvailable(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getOptimalResources).sum();
    }

    public static Integer getPlayerOptimalResourcesTotal(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getOptimalResources).sum();
    }

    public static Integer getPlayerInfluenceAvailable(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        int influenceCount = 0;
        if (player.hasLeaderUnlocked("xxchahero")) {
            int influenceCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> planet).mapToInt(Planet::getResources).sum();
            influenceCount += influenceCountFromPlanetsRes;
        }

        int influenceCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getInfluence).sum();

        influenceCount += influenceCountFromPlanets;
        return influenceCount;
    }

    public static Integer getPlayerInfluenceTotal(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        int influenceCount = 0;
        if (player.hasLeaderUnlocked("xxchahero")) {
            int influenceCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> planet).mapToInt(Planet::getResources).sum();
            influenceCount += influenceCountFromPlanetsRes;
        }

        int influenceCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getInfluence).sum();

        influenceCount += influenceCountFromPlanets;
        return influenceCount;
    }

    public static Integer getPlayerOptimalInfluenceAvailable(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> planet)
                .mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getOptimalInfluence).sum();
    }

    public static Integer getPlayerOptimalInfluenceTotal(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getOptimalInfluence).sum();
    }

    public static Integer getPlayerFlexResourcesInfluenceAvailable(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> planet)
                .mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getFlexResourcesOrInfluence).sum();
    }

    public static Integer getPlayerFlexResourcesInfluenceTotal(Player player, Game game) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> planet)
                .mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> planet).mapToInt(Planet::getFlexResourcesOrInfluence).sum();
    }

    public static String getPlayerResourceInfluenceRepresentation(Player player, Game game) {
        return player.getRepresentation() + ":\n" +
            "Resources: " + getPlayerResourcesAvailable(player, game) + "/"
            + getPlayerResourcesTotal(player, game) + "  Optimal: "
            + getPlayerOptimalResourcesAvailable(player, game)
            + "/" + getPlayerOptimalResourcesTotal(player, game) + "\n" +
            "Influence: " + getPlayerInfluenceAvailable(player, game) + "/"
            + getPlayerInfluenceTotal(player, game) + "  Optimal: "
            + getPlayerOptimalInfluenceAvailable(player, game)
            + "/" + getPlayerOptimalInfluenceTotal(player, game) + "\n";
    }

    public static Map<String, Integer> getLastEntryInHashMap(Map<String, Integer> linkedHashMap) {
        int count = 1;
        for (Map.Entry<String, Integer> it : linkedHashMap.entrySet()) {
            if (count == linkedHashMap.size()) {
                Map<String, Integer> lastEntry = new HashMap<>();
                lastEntry.put(it.getKey(), it.getValue());
                return lastEntry;
            }
            count++;
        }
        return null;
    }

    /**
     * @param text string to add spaces on the left
     * @param length minimum length of string
     * @return left padded string
     */
    public static String leftpad(String text, int length) {
        return String.format("%" + length + "." + length + "s", text);
    }

    /**
     * @param text string to add spaces on the right
     * @param length minimum length of string
     * @return right padded string
     */
    public static String rightpad(String text, int length) {
        return String.format("%-" + length + "." + length + "s", text);
    }

    public static void checkThreadLimitAndArchive(Guild guild) {
        if (guild == null)
            return;
        long threadCount = guild.getThreadChannels().stream().filter(c -> !c.isArchived()).count();
        int closeCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.THREAD_AUTOCLOSE_COUNT.toString(),
            Integer.class, 20);
        int maxThreadCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.MAX_THREAD_COUNT.toString(),
            Integer.class, 975);

        if (threadCount >= maxThreadCount) {
            BotLogger.log("`Helper.checkThreadLimitAndArchive:` Server: **" + guild.getName()
                + "** thread count is too high ( " + threadCount + " ) - auto-archiving  " + closeCount
                + " threads. The oldest thread was " + ListOldThreads.getHowOldOldestThreadIs(guild));
            ArchiveOldThreads.archiveOldThreads(guild, closeCount);
        }
    }

    public static boolean isInteger(String str) {
        if (str == null) {
            return false;
        }
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static void fixGameChannelPermissions(@NotNull Guild guild, @NotNull Game game) {
        if (!game.isFoWMode() && !game.isCommunityMode()) {
            String gameName = game.getName();
            List<Role> roles = guild.getRolesByName(gameName, true);
            Role role = null;
            if (!roles.isEmpty()) {
                if (roles.size() > 1) {
                    BotLogger.log("There are " + roles.size() + " roles that match the game name: `" + gameName
                        + "` - please investigate, as this may cause issues.");
                    return;
                }
                role = roles.get(0);
            }

            if (role == null) { // make sure players have access to the game channels
                addMapPlayerPermissionsToGameChannels(guild, game);
            } else { // make sure players have the role
                addGameRoleToMapPlayers(guild, game, role);
            }
        }
    }

    public static void addMapPlayerPermissionsToGameChannels(Guild guild, Game game) {
        TextChannel tableTalkChannel = game.getTableTalkChannel();
        if (tableTalkChannel != null) {
            addPlayerPermissionsToGameChannel(guild, game, tableTalkChannel);
        }
        TextChannel actionsChannel = game.getMainGameChannel();
        if (actionsChannel != null) {
            addPlayerPermissionsToGameChannel(guild, game, actionsChannel);
        }
        String gameName = game.getName();
        List<GuildChannel> channels = guild.getChannels().stream().filter(c -> c.getName().startsWith(gameName))
            .toList();
        for (GuildChannel channel : channels) {
            addPlayerPermissionsToGameChannel(guild, game, channel);
        }
    }

    public static void addBotHelperPermissionsToGameChannels(GenericInteractionCreateEvent event) {
        Guild guild = event.getGuild();
        // long role = 1093925613288562768L;
        long role = 1166011604488425482L;
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game game : mapList.values()) {
            if (!game.isHasEnded()) {
                TextChannel tableTalkChannel = game.getTableTalkChannel();
                if (tableTalkChannel != null && game.getGuild() == guild) {
                    addRolePermissionsToGameChannel(guild, tableTalkChannel, role);
                }
                TextChannel actionsChannel = game.getMainGameChannel();
                if (actionsChannel != null && game.getGuild() == guild) {
                    addRolePermissionsToGameChannel(guild, actionsChannel, role);
                }
                String gameName = game.getName();
                List<GuildChannel> channels = guild.getChannels().stream().filter(c -> c.getName().startsWith(gameName))
                    .toList();
                for (GuildChannel channel : channels) {
                    addRolePermissionsToGameChannel(guild, channel, role);
                }
            }
        }
    }

    private static void addPlayerPermissionsToGameChannel(Guild guild, Game game, GuildChannel channel) {
        TextChannel textChannel = guild.getTextChannelById(channel.getId());
        if (textChannel != null) {
            TextChannelManager textChannelManager = textChannel.getManager();
            for (String playerID : game.getPlayerIDs()) {
                Member member = guild.getMemberById(playerID);
                if (member == null)
                    continue;
                long allow = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();
                textChannelManager.putMemberPermissionOverride(member.getIdLong(), allow, 0);
            }
            textChannelManager.queue();
            // textChannel.sendMessage("This channel's permissions have been
            // updated.").queue();
        }
    }

    private static void addRolePermissionsToGameChannel(Guild guild, GuildChannel channel, long role) {
        TextChannel textChannel = guild.getTextChannelById(channel.getId());
        if (textChannel != null) {
            TextChannelManager textChannelManager = textChannel.getManager();
            long allow = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();
            textChannelManager.putRolePermissionOverride(role, allow, 0);
            textChannelManager.queue();
            // textChannel.sendMessage("This channel's permissions have been
            // updated.").queue();
        }
    }

    private static void addGameRoleToMapPlayers(Guild guild, Game game, Role role) {
        for (String playerID : game.getPlayerIDs()) {
            if (game.getRound() > 1 && game.getPlayer(playerID).getFaction() == null) {
                continue;
            }
            Member member = guild.getMemberById(playerID);
            if (member != null && !member.getRoles().contains(role))
                guild.addRoleToMember(member, role).queue();
        }
    }

    public static GuildMessageChannel getThreadChannelIfExists(ButtonInteractionEvent event) {
        String messageID = event.getInteraction().getMessage().getId();
        MessageChannel messageChannel = event.getMessageChannel();
        List<ThreadChannel> threadChannels = event.getGuild().getThreadChannels();
        try {
            for (ThreadChannel threadChannel : threadChannels) {
                if (threadChannel.getId().equals(messageID)) {
                    return threadChannel;
                }
            }
            return (GuildMessageChannel) messageChannel;
        } catch (Exception e) {
            BotLogger.log(event, ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    public static List<Button> getTechButtons(List<TechnologyModel> techs, String techType, Player player) {
        return getTechButtons(techs, player, "nope");
    }

    public static List<Button> getTechButtons(List<TechnologyModel> techs, Player player, String buttonPrefixType) {
        List<Button> techButtons = new ArrayList<>();

        techs.sort(TechnologyModel.sortByTechRequirements);

        for (TechnologyModel tech : techs) {
            String techName = tech.getName();
            String techID = tech.getAlias();
            String buttonID;
            if ("nope".equalsIgnoreCase(buttonPrefixType)) { // default
                buttonID = "FFCC_" + player.getFaction() + "_getTech_" + techID;
            } else if ("nekro".equalsIgnoreCase(buttonPrefixType)) {
                buttonID = "FFCC_" + player.getFaction() + "_getTech_" + techID + "__noPay";
            } else {
                buttonID = "FFCC_" + player.getFaction() + "_swapTechs__" + buttonPrefixType + "__" + techID;
            }

            ButtonStyle style;
            String requirementsEmoji = tech.getCondensedReqsEmojis(true);
            if (tech.isPropulsionTech()) {
                style = ButtonStyle.PRIMARY;
            } else if (tech.isCyberneticTech()) {
                style = ButtonStyle.SECONDARY;
            } else if (tech.isBioticTech()) {
                style = ButtonStyle.SUCCESS;
            } else if (tech.isWarfareTech()) {
                style = ButtonStyle.DANGER;
            } else {
                style = ButtonStyle.SECONDARY;
            }

            techButtons.add(Button.of(style, buttonID, techName, Emoji.fromFormatted(requirementsEmoji)));
        }
        return techButtons;
    }

    public static List<TechnologyModel> getAllTechOfAType(Game game, String techType, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        Mapper.getTechs().values().stream()
            .filter(tech -> game.getTechnologyDeck().contains(tech.getAlias()))
            .filter(tech -> tech.isType(techType) || game.getStoredValue("colorChange" + tech.getAlias()).equalsIgnoreCase(techType))
            .filter(tech -> !player.getPurgedTechs().contains(tech.getAlias()))
            .filter(tech -> !player.hasTech(tech.getAlias()))
            .filter(tech -> tech.getFaction().isEmpty() || "".equalsIgnoreCase(tech.getFaction().get()) || player.getNotResearchedFactionTechs().contains(tech.getAlias()))
            .forEach(techs::add);

        List<TechnologyModel> techs2 = new ArrayList<>();
        for (TechnologyModel tech : techs) {
            boolean addTech = true;
            if (tech.isUnitUpgrade()) {
                for (String factionTech : player.getNotResearchedFactionTechs()) {
                    TechnologyModel fTech = Mapper.getTech(factionTech);
                    if (fTech != null && !fTech.getAlias().equalsIgnoreCase(tech.getAlias())
                        && fTech.isUnitUpgrade()
                        && fTech.getBaseUpgrade().orElse("bleh").equalsIgnoreCase(tech.getAlias())) {
                        addTech = false;
                    }
                }
            }
            if (addTech) {
                techs2.add(tech);
            }
        }
        return techs2;
    }

    public static List<TechnologyModel> getAllNonFactionUnitUpgradeTech(Game game, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel tech : getAllNonFactionUnitUpgradeTech(game)) {
            if (player.hasTech(tech.getAlias())) {
                techs.add(tech);
            }
        }
        return techs;
    }

    public static List<TechnologyModel> getAllNonFactionUnitUpgradeTech(Game game) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel tech : Mapper.getTechs().values()) {
            if (tech.isUnitUpgrade() && tech.getFaction().isEmpty() && game.getTechnologyDeck().contains(tech.getAlias())) {
                techs.add(tech);
            }
        }
        return techs;
    }

    /**
     * DEPRECATED - Use TechnologyModel.getRepresentation() instead
     */
    @Deprecated
    public static String getTechRepresentation(String techID) {
        TechnologyModel tech = Mapper.getTechs().get(techID);
        return tech.getRepresentation(false);
    }

    /**
     * DEPRECATED - Use TechnologyModel.getRepresentation(true) instead
     */
    @Deprecated
    public static String getTechRepresentationLong(String techID) {
        TechnologyModel tech = Mapper.getTechs().get(techID);
        return tech.getRepresentation(true);
    }

    /**
     * DEPRECATED - Use AgendaModel.getRepresentation() instead
     */
    @Deprecated
    public static String getAgendaRepresentation(@NotNull String agendaID) {
        return getAgendaRepresentation(agendaID, null);
    }

    @Deprecated
    public static String getAgendaRepresentation(@NotNull String agendaID, @Nullable Integer uniqueID) {
        AgendaModel agendaDetails = Mapper.getAgenda(agendaID);
        return agendaDetails.getRepresentation(uniqueID);
    }

    public static void checkIfHeroUnlocked(Game game, Player player) {
        Leader playerLeader = player.getLeader(Constants.HERO).orElse(null);
        if (playerLeader != null && playerLeader.isLocked()) {
            int scoredSOCount = player.getSecretsScored().size();
            int scoredPOCount = 0;
            Map<String, List<String>> playerScoredPublics = game.getScoredPublicObjectives();
            for (Entry<String, List<String>> scoredPublic : playerScoredPublics.entrySet()) {
                if (Mapper.getPublicObjectivesStage1().containsKey(scoredPublic.getKey())
                    || Mapper.getPublicObjectivesStage2().containsKey(scoredPublic.getKey())
                    || game.getSoToPoList().contains(scoredPublic.getKey())
                    || scoredPublic.getKey().contains("Throne of the False Emperor")) {
                    if (scoredPublic.getValue().contains(player.getUserID())) {
                        scoredPOCount++;
                    }
                }
            }
            int scoredObjectiveCount = scoredPOCount + scoredSOCount;
            if (scoredObjectiveCount >= 3) {
                // UnlockLeader ul = new UnlockLeader();
                UnlockLeader.unlockLeader("hero", game, player);
            }
        }
    }

    public static Role getEventGuildRole(GenericInteractionCreateEvent event, String roleName) {
        try {
            return event.getGuild().getRolesByName(roleName, true).get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setOrder(Game game) {
        List<Integer> hsLocations = new ArrayList<>();
        LinkedHashMap<Integer, Player> unsortedPlayers = new LinkedHashMap<>();
        boolean different = false;
        for (Player player : game.getRealPlayers()) {
            Tile tile = game.getTile(AliasHandler.resolveTile(player.getFaction()));
            if (tile == null) {
                tile = player.getHomeSystemTile();
            }
            boolean ghosty = player.getPlayerStatsAnchorPosition() != null
                && game.getTileByPosition(player.getPlayerStatsAnchorPosition()) != null
                && "17".equals(game.getTileByPosition(player.getPlayerStatsAnchorPosition()).getTileID());
            if ((player.getFaction().contains("ghost") && game.getTile("17") != null) || ghosty) {
                tile = game.getTile("17");
            }
            hsLocations.add(Integer.parseInt(tile.getPosition()));
            unsortedPlayers.put(Integer.parseInt(tile.getPosition()), player);
        }
        Collections.sort(hsLocations);
        int ringWithHomes = 0;
        for (int location : hsLocations) {
            int ringNum = location / 100;
            if (ringWithHomes == 0) {
                ringWithHomes = ringNum;
            }
            if (ringWithHomes != ringNum) {
                different = true;
            }
            ringWithHomes = ringNum;
        }
        String msg = game.getPing() + " set order in the following way: \n";
        if (!different) {
            List<Player> sortedPlayers = new ArrayList<>();
            for (Integer location : hsLocations) {
                sortedPlayers.add(unsortedPlayers.get(location));
            }
            Map<String, Player> newPlayerOrder = new LinkedHashMap<>();
            Map<String, Player> players = new LinkedHashMap<>(game.getPlayers());
            Map<String, Player> playersBackup = new LinkedHashMap<>(game.getPlayers());
            try {
                for (Player player : sortedPlayers) {
                    new SetOrder().setPlayerOrder(newPlayerOrder, players, player);
                    if (player.isSpeaker()) {
                        msg = msg + player.getRepresentation(true, true) + " " + Emojis.SpeakerToken + " \n";
                    } else {
                        msg = msg + player.getRepresentation(true, true) + " \n";
                    }
                }
                if (!players.isEmpty()) {
                    newPlayerOrder.putAll(players);
                }
                game.setPlayers(newPlayerOrder);
            } catch (Exception e) {
                game.setPlayers(playersBackup);
            }
        } else {
            msg = "Detected an abnormal map, so did not assign speaker order automatically. Set the speaker order with /game set_order, with the speaker as the first player";
        }
        if (!game.isFoWMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        }
        List<Tile> tiles = new ArrayList<>();
        tiles.addAll(game.getTileMap().values());
        for (Tile tile : tiles) {
            if (tile == null) {
                continue;
            }
            if (tile.getTileID().equals("0g") || tile.getTileID().equals("-1") || tile.getTileID().equals("0gray")) {
                game.removeTile(tile.getPosition());
            }
        }
    }

    public static void checkEndGame(Game game, Player player) {
        if (player.getTotalVictoryPoints() >= game.getVp()) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("gameEnd", "End Game"));
            buttons.add(Button.primary("rematch", "Rematch (make new game with same players/channels)"));
            buttons.add(Button.danger("deleteButtons", "Mistake, delete these"));
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                game.getPing() + " it seems like " + ButtonHelper.getIdentOrColor(player, game)
                    + " has won the game. Press the end game button when you are done with the channels, or ignore this if it was a mistake/more complicated.",
                buttons);
        }
    }

    public static boolean mechCheck(String planetName, Game game, Player player) {
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        return unitHolder.getUnitCount(UnitType.Mech, player.getColor()) > 0;
    }

    /**
     * @return List of Strings
     */
    public static List<String> getListFromCSV(String commaSeparatedString) {
        StringTokenizer tokenizer = new StringTokenizer(commaSeparatedString, ",");
        List<String> values = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            values.add(tokenizer.nextToken().trim());
        }
        return values;
    }

    public static void removePoKComponents(Game game, String codex) {
        boolean removeCodex = "y".equalsIgnoreCase(codex);

        // removing Action Cards
        Map<String, ActionCardModel> actionCards = Mapper.getActionCards();
        for (ActionCardModel ac : actionCards.values()) {
            if ("pok".equals(ac.getSource().name())) {
                game.removeACFromGame(ac.getAlias());
            } else if ("codex1".equals(ac.getSource().name()) && removeCodex) {
                game.removeACFromGame(ac.getAlias());
            }
        }

        // removing SOs
        Map<String, SecretObjectiveModel> soList = Mapper.getSecretObjectives();
        for (SecretObjectiveModel so : soList.values()) {
            if ("pok".equals(so.getSource().name())) {
                game.removeSOFromGame(so.getAlias());
            }
        }

        // removing POs
        Map<String, PublicObjectiveModel> poList = Mapper.getPublicObjectives();
        for (PublicObjectiveModel po : poList.values()) {
            if ("pok".equals(po.getSource().name())) {
                if (po.getPoints() == 1) {
                    game.removePublicObjective1(po.getAlias());
                }
                if (po.getPoints() == 2) {
                    game.removePublicObjective2(po.getAlias());
                }
            }
        }
    }

    /**
     * @return Set of Strings (no duplicates)
     */
    public static Set<String> getSetFromCSV(String commaSeparatedString) {
        return new HashSet<>(getListFromCSV(commaSeparatedString));
    }

    @Deprecated
    public static boolean isFakeAttachment(String attachmentName) {
        attachmentName = AliasHandler.resolveAttachment(attachmentName);
        return Mapper.getSpecialCaseValues("fake_attachments").contains(attachmentName);
    }

    // Function to find the
    // duplicates in a Stream
    public static <T> Set<T> findDuplicateInList(List<T> list) {
        // Set to store the duplicate elements
        Set<T> items = new HashSet<>();

        // Return the set of duplicate elements
        return list.stream()
            // Set.add() returns false if the element was already present in the set.
            // Hence filter such elements
            .filter(n -> !items.add(n))
            // Collect duplicate elements in the set
            .collect(Collectors.toSet());
    }

    public static String getGuildInviteURL(Guild guild, int uses) {
        return getGuildInviteURL(guild, uses, false);
    }

    public static String getGuildInviteURL(Guild guild, int uses, boolean forever) {
        return guild.getDefaultChannel().createInvite().setMaxUses(uses).setMaxAge((long) (forever ? 0 : 4), TimeUnit.DAYS).complete().getUrl();
    }

    public static String getTimeRepresentationToSeconds(long totalMillis) {
        long totalSeconds = totalMillis / 1000; // total seconds (truncates)
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60; // total minutes (truncates)
        long minutes = totalMinutes % 60;
        long hours = totalMinutes / 60; // total hours (truncates)

        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }

    public static String getTimeRepresentationToMillis(long totalMillis) {
        long millis = totalMillis % 1000;
        long totalSeconds = totalMillis / 1000; // total seconds (truncates)
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60; // total minutes (truncates)
        long minutes = totalMinutes % 60;
        long hours = totalMinutes / 60; // total hours (truncates)

        return String.format("%02dh:%02dm:%02ds:%03d", hours, minutes, seconds, millis);
    }

    public static String getTimeRepresentationNanoSeconds(long totalNanoSeconds) {
        long totalMicroSeconds = totalNanoSeconds / 1000;
        long totalMilliSeconds = totalMicroSeconds / 1000;
        long totalSeconds = totalMilliSeconds / 1000;
        // long totalMinutes = totalSeconds / 60;
        // long totalHours = totalMinutes / 60;
        // long totalDays = totalHours / 24;

        long nanoSeconds = totalNanoSeconds % 1000;
        long microSeconds = totalMicroSeconds % 1000;
        long milleSeconds = totalMilliSeconds % 1000;
        long seconds = totalSeconds;
        // long minutes = totalMinutes % 60;
        // long hours = totalHours % 24;
        // long days = totalDays;

        // sb.append(String.format("%d:", days));
        // sb.append(String.format("%02dh:", hours));
        // sb.append(String.format("%02dm:", minutes));

        return String.format("%02ds:", seconds) +
            String.format("%03d:", milleSeconds) +
            String.format("%03d:", microSeconds) +
            String.format("%03d", nanoSeconds);
    }

    public static long median(List<Long> turnTimes) {
        List<Long> turnTimesSorted = new ArrayList<>(turnTimes);
        Collections.sort(turnTimesSorted);
        int middle = turnTimesSorted.size() / 2;
        if (turnTimesSorted.size() % 2 == 1) {
            return turnTimesSorted.get(middle);
        } else {
            return (turnTimesSorted.get(middle - 1) + turnTimesSorted.get(middle)) / 2;
        }
    }

    public static boolean embedContainsSearchTerm(MessageEmbed messageEmbed, String searchString) {
        if (messageEmbed == null)
            return false;
        if (searchString == null)
            return true;
        searchString = searchString.toLowerCase();

        if (messageEmbed.getTitle() != null && messageEmbed.getTitle().toLowerCase().contains(searchString))
            return true;
        if (messageEmbed.getDescription() != null && messageEmbed.getDescription().toLowerCase().contains(searchString))
            return true;
        if (messageEmbed.getFooter() != null && messageEmbed.getFooter().getText() != null
            && messageEmbed.getFooter().getText().toLowerCase().contains(searchString))
            return true;
        for (MessageEmbed.Field field : messageEmbed.getFields()) {
            if (field.getName() != null && field.getName().toLowerCase().contains(searchString))
                return true;
            if (field.getValue() != null && field.getValue().toLowerCase().contains(searchString))
                return true;
        }

        return false;
    }

    public static String getUnitListEmojis(String unitList) {
        String[] units = unitList.split(",");
        StringBuilder sb = new StringBuilder();
        for (String desc : units) {
            String[] split = desc.trim().split(" ");
            String alias;
            int count;
            if (StringUtils.isNumeric(split[0])) {
                count = Integer.parseInt(split[0]);
                alias = split[1];
            } else {
                count = 1;
                alias = split[0];
            }
            sb.append(StringUtils.repeat(Emojis.getEmojiFromDiscord(Mapper.getUnitBaseTypeFromAsyncID(AliasHandler.resolveUnit(alias))), count));
        }
        return sb.toString();
    }
}