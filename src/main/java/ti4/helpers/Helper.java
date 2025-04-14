package ti4.helpers;

import java.awt.Point;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import ti4.ResourceHelper;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.map.manage.GameManager;
import ti4.map.manage.ManagedGame;
import ti4.message.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.ColorModel;
import ti4.model.LeaderModel;
import ti4.model.MapTemplateModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.GMService;
import ti4.service.game.SetOrderService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyDraftTile;
import ti4.service.objectives.ScorePublicObjectiveService;
import ti4.service.unit.RemoveUnitService;

public class Helper {

    public static int getCurrentHour() {
        long currentTime = System.currentTimeMillis();
        currentTime /= 1000;
        currentTime %= (60 * 60 * 24);
        currentTime /= (60 * 60);
        return (int) currentTime;
    }

    public static List<String> unplayedACs(Game game) {
        List<String> acs = new ArrayList<>(game.getActionCards());
        for (Player p : game.getRealPlayers())
            acs.addAll(p.getActionCards().keySet());
        Collections.sort(acs);
        return acs;
    }

    public static boolean isSaboAllowed(Game game, Player player) {
        if (checkForAllSabotagesDiscarded(game) || checkAcd2ForAllSabotagesDiscarded(game)) {
            return false;
        }
        if (player.hasTech("tp") && game.getActivePlayerID() != null && game.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
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

    public static String noSaboReason(Game game, Player player) {
        if (checkForAllSabotagesDiscarded(game) || checkAcd2ForAllSabotagesDiscarded(game)) {
            return "All _Sabotages_ are in the discard.";
        }
        if (player.hasTech("tp") && game.getActivePlayerID() != null && game.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) continue;
                if (!p2.isPassed()) return null;
            }
            return "Player has " + FactionEmojis.Yssaril + " _Transparasteel Plating_, and all other players have passed.";
        }
        return null;
    }

    private static boolean checkForAllSabotagesDiscarded(Game game) {
        return game.getDiscardActionCards().containsKey("sabo1")
            && game.getDiscardActionCards().containsKey("sabo2")
            && game.getDiscardActionCards().containsKey("sabo3")
            && game.getDiscardActionCards().containsKey("sabo4");
    }

    private static boolean checkAcd2ForAllSabotagesDiscarded(Game game) {
        return "action_deck_2".equals(game.getAcDeckID())
            && game.getDiscardActionCards().containsKey("sabotage1_acd2")
            && game.getDiscardActionCards().containsKey("sabotage2_acd2")
            && game.getDiscardActionCards().containsKey("sabotage3_acd2")
            && game.getDiscardActionCards().containsKey("sabotage4_acd2");
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

    public static List<Player> getPlayersFromTech(Game game, String tech) {
        if (tech == null || Mapper.getTech(tech) == null)
            return Collections.emptyList();
        List<Player> players = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            if (player.isRealPlayer() && player.hasTech(tech)) {
                players.add(player);
            }
        }
        return players;
    }

    public static List<Player> getPlayersFromReadyTech(Game game, String tech) {
        if (tech == null || Mapper.getTech(tech) == null)
            return Collections.emptyList();
        List<Player> players = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            if (player.isRealPlayer() && player.hasTechReady(tech)) {
                players.add(player);
            }
        }
        return players;
    }

    // TODO: (Jazz): This method *should* include base game + pok tiles (+ DS tiles if and only if DS mode is set)
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

    public static void giveMeBackMyAgendaButtons(Game game) {
        List<Button> proceedButtons = new ArrayList<>();
        String msg = "These buttons can help with bugs/issues that occur during the agenda phase";
        proceedButtons.add(Buttons.red("proceedToVoting", "Skip Waiting"));
        proceedButtons.add(Buttons.blue("transaction", "Transaction"));
        proceedButtons.add(Buttons.red("eraseMyVote", "Erase My Vote And Have Me Vote Again"));
        proceedButtons.add(Buttons.red("eraseMyRiders", "Erase My Riders"));
        MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), msg, proceedButtons);
    }

    public static void resolveQueue(Game game) {
        Player imperialHolder = getPlayerWithThisSC(game, 8);
        if (game.getPhaseOfGame().contains("agenda")) {
            imperialHolder = game.getPlayer(game.getSpeakerUserID());
        }
        //String key = "factionsThatAreNotDiscardingSOs";
        String key2 = "queueToDrawSOs";
        String key3 = "potentialBlockers";
        if (game.getStoredValue(key2).length() < 2) {
            return;
        }

        for (Player player : getSpeakerOrderFromThisPlayer(imperialHolder, game)) {
            String message = player.getRepresentationUnfogged() + " drew their queued secret objective from **Imperial**. ";
            if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                game.drawSecretObjective(player.getUserID());
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    message += " Drew a second secret objective due to **Plausible Deniability**.";
                }
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
                game.setStoredValue(key2,
                    game.getStoredValue(key2).replace(player.getFaction() + "*", ""));
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (!game.isFowMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "imperial");
                }
            }
            if (game.getStoredValue(key3).contains(player.getFaction() + "*")
                && game.getStoredValue(key2).length() > 2) {
                if (!game.isFowMode()) {
                    message = player.getRepresentationUnfogged()
                        + " is the one the game is currently waiting on before advancing to the next player, with regards to queued **Imperial** follows.";
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if (!game.isFowMode()) {
                    ButtonHelper.sendMessageToRightStratThread(player, game, message, "imperial");
                }
                break;
            }
        }
    }

    public static boolean canPlayerScorePOs(Game game, Player player) {
        if (player.hasAbility("nomadic")) {
            return true;
        }
        if (player.hasAbility("mobile_command") && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship).isEmpty()) {
            return false;
        }
        Tile hs = player.getHomeSystemTile();
        if (hs != null) {
            for (Planet planet : hs.getPlanetUnitHolders()) {
                if (!player.getPlanets().contains(planet.getName())) {
                    return false;
                }
            }
        }

        return true;
    }

    public static String getNewStatusScoringRepresentation(Game game) {
        if (game.getPhaseOfGame().equalsIgnoreCase("action")) {
            return "";
        }
        StringBuilder rep = new StringBuilder("# __Scoring Summary__\n");
        if (game.getRealPlayers().size() > 10) {
            return "This game is too large to display a scoring summary";
        }
        for (Player player : game.getActionPhaseTurnOrder()) {
            int sc = player.getLowestSC();
            rep.append(CardEmojis.getSCBackFromInteger(sc)).append(player.getRepresentation(false, false)).append("\n");
            String poMessage = "";
            String soMessage = CardEmojis.SecretObjective + " ";
            String po = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "PO");
            String so = game.getStoredValue(player.getFaction() + "round" + game.getRound() + "SO");
            if (po.isEmpty() || po.equalsIgnoreCase("Queued") || po.equalsIgnoreCase("None")) {
                poMessage += CardEmojis.Public1 + " " + CardEmojis.Public2 + " ";
                if (po.isEmpty()) {
                    poMessage += "❓";
                }
                if (po.equalsIgnoreCase("Queued")) {
                    poMessage += "Queued";
                }
                if (po.equalsIgnoreCase("None")) {
                    poMessage += "🙅";
                }
            } else {
                poMessage = CardEmojis.Public1 + " ✅ ";
                for (String poObj : game.getRevealedPublicObjectives().keySet()) {
                    if (Mapper.getPublicObjective(poObj) != null) {
                        if (Mapper.getPublicObjective(poObj).getName().equalsIgnoreCase(po)) {
                            if (Mapper.getPublicObjective(poObj).getPoints() == 2) {
                                poMessage = CardEmojis.Public2 + " ✅ ";
                            }
                        }
                    }
                }
                poMessage += po;
            }
            if (so.isEmpty() || so.equalsIgnoreCase("Queued") || so.equalsIgnoreCase("None")) {
                if (so.isEmpty()) {
                    soMessage += "❓";
                }
                if (so.equalsIgnoreCase("Queued")) {
                    soMessage += "Queued";
                }
                if (so.equalsIgnoreCase("None")) {
                    soMessage += "🙅";
                }
            } else {
                soMessage += " ✅ " + so;
            }
            rep.append("> ").append(poMessage).append("\n");
            rep.append("> ").append(soMessage).append("\n");
        }

        return rep.toString();
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
        for (Player player : game.getActionPhaseTurnOrder()) {
            if (game.getHighestScore() + 1 > game.getVp()) {
                return;
            }
            if (game.getStoredValue(key2).contains(player.getFaction() + "*")
                || game.getStoredValue(key2b).contains(player.getFaction() + "*")) {
                if (game.getStoredValue(key2).contains(player.getFaction() + "*")) {
                    int poIndex = Integer
                        .parseInt(game.getStoredValue(player.getFaction() + "queuedPOScore"));
                    ScorePublicObjectiveService.scorePO(event, player.getCorrectChannel(), game, player, poIndex);
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
                    SecretObjectiveHelper.scoreSO(event, game, player, soIndex, player.getCorrectChannel());
                    game.setStoredValue(key2b,
                        game.getStoredValue(key2b).replace(player.getFaction() + "*", ""));
                    game.setStoredValue(key3b,
                        game.getStoredValue(key3b).replace(player.getFaction() + "*", ""));
                }
            } else {
                if (game.getStoredValue(key3).contains(player.getFaction() + "*")
                    && game.getStoredValue(key2).length() > 2) {
                    String message = player.getRepresentationUnfogged()
                        + " is the one the game is currently waiting on before advancing to the next player, with regards to queued public objective scoring.";
                    if (game.isFowMode()) {
                        message = "Waiting on someone else before proceeding with scoring.";
                    }
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    break;
                }
                if (game.getStoredValue(key3b).contains(player.getFaction() + "*")
                    && game.getStoredValue(key2).length() > 2) {
                    String message = player.getRepresentationUnfogged()
                        + " is the one the game is currently waiting on before advancing to the next player, with regards to queued secret objective scoring.";
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

    public static int getPlayerSpeakerNumber(Player player, Game game) {
        Player speaker;
        if (game.getPlayer(game.getSpeakerUserID()) != null) {
            speaker = game.getPlayers().get(game.getSpeakerUserID());
        } else {
            return 1;
        }
        List<Player> players = new ArrayList<>();
        boolean found = false;
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == speaker) {
                found = true;
                players.add(speaker);
            } else {
                if (found) {
                    players.add(p2);
                }
            }
        }

        for (Player p2 : game.getRealPlayers()) {
            if (p2 == speaker) {
                found = false;
            } else {
                if (found) {
                    players.add(p2);
                }
            }
        }
        int count = 1;
        for (Player p2 : players) {
            if (player == p2) {
                return count;
            } else {
                count++;
            }
        }
        return count;
    }

    public static void startOfTurnSaboWindowReminders(Game game, Player player) {
        var gameMessages = GameMessageManager.getAll(game.getName(), GameMessageType.ACTION_CARD);
        for (GameMessageManager.GameMessage gameMessage : gameMessages) {
            if (ReactionService.checkForSpecificPlayerReact(gameMessage.messageId(), player, game)) continue;

            game.getMainGameChannel().retrieveMessageById(gameMessage.messageId()).queue(mainMessage -> {
                Emoji reactionEmoji = getPlayerReactionEmoji(game, player, gameMessage.messageId());
                MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
                if (reaction == null) {
                    Calendar rightNow = Calendar.getInstance();
                    if (rightNow.get(Calendar.DAY_OF_YEAR) - mainMessage.getTimeCreated().getDayOfYear() > 2 ||
                        rightNow.get(Calendar.DAY_OF_YEAR) - mainMessage.getTimeCreated().getDayOfYear() < -100) {
                        GameMessageManager.remove(game.getName(), gameMessage.messageId());
                    }
                }
            });
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
    public static String getDamagePath() {
        String tokenPath = ResourceHelper.getResourceFromFolder("extra/", "marker_damage.png");
        if (tokenPath == null) {
            BotLogger.warning("Could not find token: marker_damage");
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

    public static int getDateDifference(String date1, String date2) {
        if (date1 == null || date1.isEmpty()) {
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

    public static String getSCAsMention(int sc, Game game) {
        if (game.isHomebrewSCMode()) {
            return getSCName(sc, game);
        }
        return switch (sc) {
            case 1 -> CardEmojis.SC1Mention();
            case 2 -> CardEmojis.SC2Mention();
            case 3 -> CardEmojis.SC3Mention();
            case 4 -> CardEmojis.SC4Mention();
            case 5 -> CardEmojis.SC5Mention();
            case 6 -> CardEmojis.SC6Mention();
            case 7 -> CardEmojis.SC7Mention();
            case 8 -> CardEmojis.SC8Mention();
            default -> "**SC" + sc + "**";
        };
    }

    public static String getSCRepresentation(Game game, int sc) {
        if (game.isHomebrewSCMode())
            return "SC #" + sc + " " + getSCName(sc, game);
        return getSCAsMention(sc, game);
    }

    public static String getSCName(int sc, Game game) {
        if (Optional.ofNullable(game.getScSetID()).isPresent() && !"null".equals(game.getScSetID())) {
            return game.getStrategyCardSet().getSCName(sc);
        }
        return "SC#" + sc;
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

    public static String getScImageUrl(Integer sc, Game game) {
        String scImagePath = game.getStrategyCardSet()
            .getStrategyCardModelByInitiative(sc)
            .map(StrategyCardModel::getImageFileName)
            .orElse("sadFace.png");
        return "https://raw.githubusercontent.com/AsyncTI4/TI4_map_generator_bot/refs/heads/master/src/main/resources/strat_cards/" + scImagePath + ".png";
    }

    public static Emoji getPlayerReactionEmoji(Game game, Player player, Message message) {
        return getPlayerReactionEmoji(game, player, message.getId());
    }

    public static Emoji getPlayerReactionEmoji(Game game, Player player, String messageId) {
        Emoji emojiToUse = Emoji.fromFormatted(player.getFactionEmoji());
        if (game.isFowMode()) {
            int index = 0;
            for (Player player_ : game.getPlayers().values()) {
                if (player_ == player)
                    break;
                index++;
            }
            emojiToUse = Emoji.fromFormatted(TI4Emoji.getRandomizedEmoji(index, messageId).toString());
        }
        return emojiToUse;
    }

    public static String getPlanetRepresentationPlusEmoji(String planet) {
        String planetProper = Mapper.getPlanetRepresentations().get(planet);
        return PlanetEmojis.getPlanetEmoji(planet) + " " + (Objects.isNull(planetProper) ? planet : planetProper);
    }

    public static String getPlanetName(String planetID) {
        return Mapper.getPlanetRepresentations().get(AliasHandler.resolvePlanet(planetID));
    }

    public static String getUnitHolderRepresentation(Tile tile, String planetOrSpace, Game game, Player player) {
        if (planetOrSpace.equals("space")) {
            return tile.getRepresentationForButtons(game, player);
        } else {
            return getPlanetRepresentation(planetOrSpace, game);
        }
    }

    public static String getPlanetRepresentationNoResInf(String planetID, Game game) {
        planetID = planetID.toLowerCase().replace(" ", "");
        planetID = planetID.replace("'", "");
        planetID = planetID.replace("-", "");
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return "Unable to find planet unitholder for " + planetID;
        }
        boolean containsDMZ = unitHolder.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
        String rep = Mapper.getPlanetRepresentations().get(AliasHandler.resolvePlanet(planetID));
        return rep + (containsDMZ ? " [DMZ]" : "");
    }

    public static String getPlanetRepresentation(String planetID, Game game) {
        planetID = planetID.toLowerCase().replace(" ", "");
        planetID = planetID.replace("'", "");
        planetID = planetID.replace("-", "");
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return "Unable to find planet unitholder for " + planetID;
        }
        boolean containsDMZ = unitHolder.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
        if (containsDMZ) {
            return Mapper.getPlanetRepresentations().get(AliasHandler.resolvePlanet(planetID)) + " (" + unitHolder.getResources()
                + "/" + unitHolder.getInfluence() + ") [DMZ]";
        }
        return Mapper.getPlanetRepresentations().get(AliasHandler.resolvePlanet(planetID)) + " (" + unitHolder.getResources()
            + "/" + unitHolder.getInfluence() + ")";
    }

    public static String getPlanetRepresentationPlusEmojiPlusResourceInfluence(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            String techType;
            String techEmoji = "";
            if (Mapper.getPlanet(planetID) != null && Mapper.getPlanet(planetID).getTechSpecialties() != null
                && !Mapper.getPlanet(planetID).getTechSpecialties().isEmpty()) {
                techType = Mapper.getPlanet(planetID).getTechSpecialties().getFirst().toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(game, AliasHandler.resolvePlanet(planetID));
            }
            if (!"".equalsIgnoreCase(techType)) {
                switch (techType) {
                    case "propulsion" -> techEmoji = TechEmojis.PropulsionTech.toString();
                    case "warfare" -> techEmoji = TechEmojis.WarfareTech.toString();
                    case "cybernetic" -> techEmoji = TechEmojis.CyberneticTech.toString();
                    case "biotic" -> techEmoji = TechEmojis.BioticTech.toString();
                }
            }
            return getPlanetRepresentationPlusEmoji(planetID) + " " + MiscEmojis.getResourceEmoji(unitHolder.getResources())
                + MiscEmojis.getInfluenceEmoji(unitHolder.getInfluence()) + techEmoji;
        }
    }

    public static String getPlanetRepresentationPlusEmojiPlusInfluence(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            return getPlanetRepresentationPlusEmoji(planetID) + " " + MiscEmojis.getInfluenceEmoji(unitHolder.getInfluence());
        }
    }

    public static String getPlanetRepresentationPlusEmojiPlusResources(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            String techType = "";
            String techEmoji = "";
            if (Mapper.getPlanet(planetID).getTechSpecialties() != null
                && !Mapper.getPlanet(planetID).getTechSpecialties().isEmpty()) {
                techType = Mapper.getPlanet(planetID).getTechSpecialties().getFirst().toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(game, planetID);
            }
            if (!"".equalsIgnoreCase(techType)) {
                switch (techType) {
                    case "propulsion" -> techEmoji = TechEmojis.PropulsionTech.toString();
                    case "warfare" -> techEmoji = TechEmojis.WarfareTech.toString();
                    case "cybernetic" -> techEmoji = TechEmojis.CyberneticTech.toString();
                    case "biotic" -> techEmoji = TechEmojis.BioticTech.toString();
                }
            }
            return getPlanetRepresentationPlusEmoji(planetID) + " " + MiscEmojis.getResourceEmoji(unitHolder.getResources()) + techEmoji;
        }
    }

    public static List<Button> getPlanetRefreshButtons(Player player, Game game) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : planets) {
            Button button = Buttons.green("refresh_" + planet, getPlanetRepresentation(planet, game));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getRemainingSCButtons(Game game, Player playerPicker) {
        List<Button> scButtons = new ArrayList<>();

        for (Integer sc : game.getSCList()) {
            if (sc <= 0)
                continue; // some older games have a 0 in the list of SCs
            boolean held = false;
            for (Player player : game.getPlayers().values()) {
                if (player == null || player.getFaction() == null) {
                    continue;
                }
                if (player.getSCs() != null && player.getSCs().contains(sc) && !game.isFowMode()) {
                    held = true;
                    break;
                }
            }
            if (held)
                continue;
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
            Button button;
            String label = getSCName(sc, game);
            if (game.getScTradeGoods().get(sc) > 0 && !game.isFowMode()) {
                label += " [Has " + game.getScTradeGoods().get(sc) + " Trade Good" + (game.getScTradeGoods().get(sc) == 1 ? "" : "s") + "]";
            }
            if (sc == ButtonHelper.getKyroHeroSC(game)) {
                label += " - Kyro Hero Cursed";
            }
            if (!game.getStoredValue("exhaustedSC" + sc).isEmpty()) {
                label += " - Exhausted";
            }
            if (scEmoji != CardEmojis.SCBackBlank && !game.isHomebrewSCMode()) {
                button = Buttons.gray("FFCC_" + playerPicker.getFaction() + "_scPick_" + sc, label, scEmoji);
            } else {
                button = Buttons.gray("FFCC_" + playerPicker.getFaction() + "_scPick_" + sc, sc + " " + label, scEmoji);
            }
            scButtons.add(button);
        }
        return scButtons;
    }

    public static List<Integer> getRemainingSCs(Game game) {
        List<Integer> scButtons = new ArrayList<>();

        for (Integer sc : game.getSCList()) {
            if (sc <= 0)
                continue; // some older games have a 0 in the list of SCs
            boolean held = false;
            for (Player player : game.getPlayers().values()) {
                if (player == null || player.getFaction() == null) {
                    continue;
                }
                if (player.getSCs() != null && player.getSCs().contains(sc) && !game.isFowMode()) {
                    held = true;
                    break;
                }
            }
            if (held)
                continue;
            scButtons.add(sc);
        }
        return scButtons;
    }

    public static List<Button> getPlanetExhaustButtons(Player player, Game game) {
        return getPlanetExhaustButtons(player, game, "both");
    }

    public static List<Button> getPlanetExhaustButtons(Player player, Game game, String whatIsItFor) {
        if (game.getStoredValue("resetSpend").isEmpty()) {
            player.resetSpentThings();
            game.setStoredValue("ledSpend" + player.getFaction(), "");
        } else {
            game.setStoredValue("resetSpend", "");

        }
        player.resetOlradinPolicyFlags();
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        for (String planet : planets) {

            if (planet.contains("custodia") || planet.contains("ghoti")) {
                Button button = Buttons.red("spend_" + planet, getPlanetRepresentation(planet, game));
                planetButtons.add(button);
                continue;
            }
            String techType;
            if (Mapper.getPlanet(planet).getTechSpecialties() != null
                && !Mapper.getPlanet(planet).getTechSpecialties().isEmpty()) {
                techType = Mapper.getPlanet(planet).getTechSpecialties().getFirst().toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(game, planet);
            }
            if ("none".equalsIgnoreCase(techType)) {
                Button button = Buttons.red("spend_" + planet + "_" + whatIsItFor,
                    getPlanetRepresentation(planet, game));
                planetButtons.add(button);
            } else {
                Button techB = Buttons.red("spend_" + planet + "_" + whatIsItFor, getPlanetRepresentation(planet, game));
                switch (techType) {
                    case "propulsion" -> techB = techB.withEmoji(TechEmojis.PropulsionTech.asEmoji());
                    case "warfare" -> techB = techB.withEmoji(TechEmojis.WarfareTech.asEmoji());
                    case "cybernetic" -> techB = techB.withEmoji(TechEmojis.CyberneticTech.asEmoji());
                    case "biotic" -> techB = techB.withEmoji(TechEmojis.BioticTech.asEmoji());
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
            UnitHolder uH = game.getUnitHolderFromPlanet(planet);
            boolean containsDMZ = uH.getTokenList().stream().anyMatch(token -> token.contains("dmz"));
            if (containsDMZ) {
                continue;
            }
            if (unit.equalsIgnoreCase("spacedock")) {

                if (uH == null || uH.getUnitCount(UnitType.Spacedock, player) > 0) {
                    continue;
                }
            }
            Button button = Buttons.green("FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + planet, getPlanetRepresentation(planet, game));
            if (unit.equalsIgnoreCase("2gf") || unit.equalsIgnoreCase("3gf")) {
                button = button.withEmoji(UnitEmojis.infantry.asEmoji());
            }
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getHSPlanetPlaceUnitButtons(Player player, Game game, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        player.resetProducedUnits();
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID());
        for (String planet : planets) {
            if (planet.contains("ghoti") || planet.contains("custodia")) {
                continue;
            }
            if (game.getTileFromPlanet(planet) != player.getHomeSystemTile()) {
                continue;
            }
            Button button = Buttons.red("FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + planet,
                getPlanetRepresentation(planet, game), unitKey.unitEmoji());
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getTileWithShipsPlaceUnitButtons(Player player, Game game, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        for (Tile tile : tiles) {
            Button button = Buttons.red(
                "FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(),
                tile.getRepresentationForButtons(game, player));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getTileWithTrapsPlaceUnitButtons(Player player, Game game, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithTrapsInTheSystem(game);
        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                Button button = Buttons.red(
                    "FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player));
                planetButtons.add(button);
            }
        }
        return planetButtons;
    }

    public static List<Button> getTileForCheiranHeroPlaceUnitButtons(Player player, Game game, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesForCheiranHero(player, game);
        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                Button button = Buttons.red(
                    "FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player));
                planetButtons.add(button);
            }
        }
        return planetButtons;
    }

    public static List<Button> getTileWithShipsNTokenPlaceUnitButtons(Player player, Game game, String unit, String prefix, @Nullable ButtonInteractionEvent event) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        for (Tile tile : tiles) {
            if (CommandCounterHelper.hasCC(event, player.getColor(), tile)) {
                Button button = Buttons.red(
                    "FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player));
                planetButtons.add(button);
            }
        }
        return planetButtons;
    }

    public static String buildSpentThingsMessageForVoting(Player player, Game game, boolean justVoteTotal) {
        List<String> spentThings = player.getSpentThingsThisWindow();
        Set<String> set = new HashSet<>(spentThings);
        StringBuilder msg = new StringBuilder(player.getFactionEmoji() + " used the following: \n");
        int votes = 0;
        int tg = player.getSpentTgsThisWindow();
        for (String thing : set) {
            int count;
            if (!thing.contains("_")) {
                BotLogger.info(new BotLogger.LogMessageOrigin(game), "Caught the following thing in the voting " + thing + " in game " + game.getName());
                continue;
            }
            String secondHalf = thing.split("_")[1];
            String flavor = thing.split("_")[0];
            if (flavor.contains("planet")) {
                count = AgendaHelper.getSpecificPlanetsVoteWorth(player, game, secondHalf);
            } else {
                count = Integer.parseInt(thing.split("_")[1]);
            }
            if (flavor.contains("tg") && !flavor.contains("dsgh")) {
                votes += count * 2;
            } else {
                votes += count;
            }
            msg.append("> ");
            switch (flavor) {
                case "tg" -> msg.append("Spent ").append(tg).append(" trade good").append(tg == 1 ? "" : "s").append(" for ").append(tg * 2).append(" votes.\n");
                case "infantry" -> msg.append("Spent ").append(player.getSpentInfantryThisWindow()).append(" infantry for ").append(player.getSpentInfantryThisWindow()).append(" vote").append(player.getSpentInfantryThisWindow() == 1 ? "" : "s").append(".\n");
                case "planet" -> msg.append(getPlanetRepresentation(secondHalf, game)).append(" for ").append(count).append(" vote").append(count == 1 ? "" : "s").append(".\n");
                case "absolShard" -> msg.append("Used _Shard of the Throne_ for ").append(count).append(" vote").append(count == 1 ? "" : "s").append(".\n");
                case "dsghotg" -> msg.append("Exhausted _Some Silly Ghoti Technology_ for ").append(count).append(" vote").append(count == 1 ? "" : "s").append(".\n");
                case "absolsyncretone" -> msg.append("Used Syncretone for ").append(count).append(" vote").append(count == 1 ? "" : "s").append(".\n");
                case "augerscommander" -> msg.append("Used Ilyxum Commander for ").append(count).append(" vote").append(count == 1 ? "" : "s").append(".\n");
                case "zeal" -> msg.append("Used **Zeal** ability for ").append(count).append(" vote").append(count == 1 ? "" : "s").append(".\n");
                case "predictive" -> msg.append("Used _Predictive Intelligence_ for ").append(count).append(" vote").append(count == 1 ? "" : "s").append(".\n");
                case "specialVotes" -> msg.append("Used Special Votes for ").append(count).append(" vote").append(count == 1 ? "" : "s").append(".\n");
                case "representative" -> msg.append("Got 1 vote for _Representative Government_.\n");
                case "distinguished" -> msg.append("Used _Distinguished Councilor_ for 5 votes.\n");
                case "absolRexControlRepresentative" -> msg.append("Got 1 vote for controlling Mecatol Rex while _Representative Government_ is in play.\n");
                case "bloodPact" -> msg.append("Got 4 votes from voting the same way as another _Blood Pact_ member.\n");

            }
        }
        String outcome = game.getStoredValue("latestOutcomeVotedFor" + player.getFaction());
        if (game.getCurrentAgendaInfo().contains("Secret") && Mapper.getSecretObjectivesJustNames().get(outcome) != null) {
            msg.append("For a total of **").append(votes).append("** vote").append(votes == 1 ? "" : "s").append(" on the outcome \"_")
                .append(Mapper.getSecretObjectivesJustNames().get(outcome)).append("_\".");
        } else if (game.getCurrentAgendaInfo().contains("Elect Strategy Card")) {
            msg.append("For a total of **").append(votes).append("** vote").append(votes == 1 ? "" : "s").append(" on the outcome \"**")
                .append(Helper.getSCName(Integer.parseInt(outcome), game)).append("**\".");
        } else {
            msg.append("For a total of **").append(votes).append("** vote").append(votes == 1 ? "" : "s").append(" on the outcome \"")
                .append(StringUtils.capitalize(outcome)).append("\".");
        }
        if (justVoteTotal) {
            return "" + votes;
        }

        return msg.toString();
    }

    public static void refreshPlanetsOnTheRevote(Player player, Game game) {
        List<String> spentThings = player.getSpentThingsThisWindow();
        int tg = player.getSpentTgsThisWindow();
        player.setTg(player.getTg() + tg);
        for (String thing : spentThings) {
            if (!thing.contains("_")) {
                BotLogger.warning(new BotLogger.LogMessageOrigin(player), "Caught the following thing in the voting " + thing + " in game " + game.getName());
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
        List<String> spentThings = new ArrayList<>(player.getSpentThingsThisWindow());
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
        StringBuilder msg = new StringBuilder(player.getRepresentationNoPing() + " exhausted the following: \n");
        int res = 0;
        int inf = 0;
        if (resOrInfOrBoth.contains("tech")) {
            resOrInfOrBoth = resOrInfOrBoth.replace("tech", "");
        }
        int tg = player.getSpentTgsThisWindow();
        boolean xxchaHero = player.hasLeaderUnlocked("xxchahero");
        int bestRes = 0;
        int keleresAgent = 0;
        for (String thing : spentThings) {
            boolean found = false;
            switch (thing) {
                case "sarween" -> {
                    msg.append("> Used _Sarween Tools_ " + TechEmojis.CyberneticTech + "\n");
                    res += 1;
                    found = true;
                }
                case "absol_sarween" -> {
                    int sarweenVal = 1 + calculateCostOfProducedUnits(player, game, true) / 10;
                    msg.append("> Used _Sarween Tools_ " + TechEmojis.CyberneticTech + " for ").append(sarweenVal).append(" resources\n");
                    res += sarweenVal;
                    found = true;
                }
            }
            if (!found && !thing.contains("tg_") && !thing.contains("boon") && !thing.contains("warmachine")
                && !thing.contains("aida") && !thing.contains("commander") && !thing.contains("Agent")) {
                Planet planet = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(thing));
                msg.append("> ");
                if (planet == null) {
                    if (thing.contains("reduced commodities")) {
                        String comms = StringUtils.substringAfter(thing, "by ");
                        comms = StringUtils.substringBefore(comms, " (");
                        keleresAgent = Integer.parseInt(comms);
                        msg.append("Keleres Agent for ").append(comms).append(" comms\n");
                    } else {
                        msg.append(thing).append("\n");
                    }
                } else {
                    Tile t = game.getTileFromPlanet(planet.getName());
                    if (t != null && !t.isHomeSystem()) {
                        if (planet.getResources() > bestRes) {
                            bestRes = planet.getResources();
                        }
                    }
                    if ("res".equalsIgnoreCase(resOrInfOrBoth)) {
                        if (xxchaHero) {
                            msg.append(getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game)).append("\n");
                            res += planet.getSumResourcesInfluence();
                        } else {
                            msg.append(getPlanetRepresentationPlusEmojiPlusResources(thing, game)).append("\n");
                            res += planet.getResources();
                        }
                    } else if ("inf".equalsIgnoreCase(resOrInfOrBoth)) {
                        if (xxchaHero) {
                            msg.append(getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game)).append("\n");
                            inf += planet.getSumResourcesInfluence();
                        } else {
                            msg.append(getPlanetRepresentationPlusEmojiPlusInfluence(thing, game)).append("\n");
                            inf += planet.getInfluence();
                        }
                    } else if ("freelancers".equalsIgnoreCase(resOrInfOrBoth)) {
                        msg.append(getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game)).append("\n");
                        if (xxchaHero) {
                            res += planet.getSumResourcesInfluence();
                        } else {
                            res += planet.getMaxResInf();
                        }
                    } else {
                        msg.append(getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, game)).append("\n");
                        if (xxchaHero) {
                            inf += planet.getSumResourcesInfluence();
                            res += planet.getSumResourcesInfluence();
                        } else {
                            inf += planet.getInfluence();
                            res += planet.getResources();
                        }
                    }
                }
            } else {

                if (thing.contains("boon")) {
                    msg.append("> Used Boon Relic ").append(ExploreEmojis.Relic).append("\n");
                    res += 1;
                }
                if (thing.contains("warmachine")) {
                    msg.append("> Used _War Machine_ ").append(CardEmojis.ActionCard).append("\n");
                    res += 1;
                }
                if (thing.contains("aida")) {
                    msg.append("> Exhausted ").append(TechEmojis.WarfareTech).append("_AI Development Algorithm_ ");
                    if (thing.contains("_")) {
                        int upgrades = ButtonHelper.getNumberOfUnitUpgrades(player);
                        res += upgrades;
                        msg.append("for ").append(upgrades).append(" resource").append(upgrades == 1 ? "" : "s");
                    } else {
                        msg.append("to ignore a prerequisite on a unit upgrade technology");
                    }
                    msg.append(".\n");
                }
                if (thing.contains("commander") || thing.contains("Gledge Agent")) {
                    msg.append("> ").append(thing).append("\n");
                } else if (thing.contains("Winnu Agent")) {
                    msg.append("> ").append(thing).append("\n");
                    res += 2;
                } else if (thing.contains("Zealots Agent")) {
                    msg.append("> ").append(thing).append("(Best resources found were ").append(bestRes).append(")\n");
                    inf += bestRes;
                } else if (thing.contains("Agent")) {
                    msg.append("> ").append(thing).append("\n");
                }
            }
        }
        res += tg + keleresAgent;
        inf += tg + keleresAgent;
        if (tg > 0) {
            msg.append("> Spent ").append(tg).append(" trade good").append(tg == 1 ? "" : "s").append(" ").append(MiscEmojis.getTGorNomadCoinEmoji(game))
                .append(" (").append(player.getTg() + tg).append("->").append(player.getTg()).append(") \n");
            if (player.hasTech("mc")) {
                res += tg + keleresAgent;
                inf += tg + keleresAgent;
                msg.append("> Counted the trade goods twice due to _Mirror Computing_\n");
            }
        }

        if ("res".equalsIgnoreCase(resOrInfOrBoth)) {
            msg.append("for a total spend of ").append(res).append(" resources.");
        } else if ("inf".equalsIgnoreCase(resOrInfOrBoth)) {
            msg.append("for a total spend of ").append(inf).append(" influence.");
        } else if ("freelancers".equalsIgnoreCase(resOrInfOrBoth)) {
            msg.append("for a total spend of ").append(res).append(" resources (counting influence as resources).");
        } else {
            msg.append("for a total spend of ").append(res).append(" resources or ").append(inf).append(" influence.");
        }
        return msg.toString();
    }

    public static String buildProducedUnitsMessage(Player player, Game game) {
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        StringBuilder msg = new StringBuilder();
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
            StringBuilder localPlace = new StringBuilder();
            if (msg.isEmpty()) {
                localPlace.append(player.getRepresentationNoPing()).append(" is producing units in ").append(tile.getRepresentationForButtons(game, player));
            } else {
                localPlace.append("And is producing units in ").append(tile.getRepresentationForButtons(game, player));
            }
            if ("space".equalsIgnoreCase(planetOrSpace2)) {
                localPlace.append(" in the space area.\n");
            } else {
                localPlace.append(" on the planet ").append(getPlanetRepresentation(planetOrSpace2, game)).append(".\n");
            }
            for (String unit : producedUnits.keySet()) {
                String tilePos = unit.split("_")[1];
                String planetOrSpace = unit.split("_")[2];
                String un = unit.split("_")[0];
                UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(un), player.getColor());
                UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();
                if (uniquePlace.equalsIgnoreCase(tilePos + "_" + planetOrSpace)) {
                    if (producedUnits.get(unit) < 10) {
                        localPlace.append("> ").append(removedUnit.getUnitEmoji().toString().repeat(producedUnits.get(unit))).append("\n");
                    } else {
                        localPlace.append("> ").append(producedUnits.get(unit)).append("x ").append(removedUnit.getUnitEmoji()).append("\n");
                    }
                }
            }
            msg.append(localPlace);
        }
        int cost = calculateCostOfProducedUnits(player, game, true);
        int unitCount = calculateCostOfProducedUnits(player, game, false);
        if (unitCount <= 1) {
            msg.append("For a cost of ").append(cost).append(" resource").append(cost == 1 ? "" : "s").append(".");
        } else {
            msg.append("Producing a total of ").append(unitCount).append(" unit").append(unitCount == 1 ? "" : "s")
                .append(" for a total cost of ").append(cost).append(" resource").append(cost == 1 ? "" : "s").append(".");
        }
        return msg.toString();
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
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), producedUnits.get(unit) + " " + AliasHandler.resolveUnit(un) + planetOrSpace);
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
                if (player.getUnitsByAsyncID(unit.asyncID()).isEmpty()) {
                    continue;
                }
                UnitModel unitModel = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
                int productionValue = unitModel.getProductionValue();
                if ("fs".equals(unitModel.getAsyncId()) && player.ownsUnit("ghoti_flagship")) {
                    productionValueTotal += player.getFleetCC();
                }
                if (unitModel.getBaseType().equalsIgnoreCase("mech") && ButtonHelper.isLawInPlay(game, "articles_war")) {
                    productionValue = 0;
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
                        productionValue += 4;
                    }
                }
                if (productionValue > 0 && player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValue++;
                }
                productionValueTotal += productionValue * uH.getUnits().get(unit);
            }
        }
        String planet = uH.getName();
        int planetUnitVal = 0;
        if (uH.getName().equals("space")) {
            if (tile.isSupernova() && player.hasTech("mr") && FoWHelper.playerHasUnitsInSystem(player, tile)) {
                productionValueTotal += 5;
            }
        }
        if (!player.getPlanets().contains(uH.getName())) {
            return productionValueTotal;
        }
        if (Constants.MECATOLS.contains(planet) && player.hasIIHQ() && player.controlsMecatol(true)) {
            productionValueTotal += 3;
            planetUnitVal = 3;
        }
        for (String token : uH.getTokenList()) {
            if (token.contains("orbital_foundries") && planetUnitVal < 2) {
                productionValueTotal += 2;
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValueTotal++;
                }
                planetUnitVal = 2;
            }

            if (token.contains("automatons") && planetUnitVal < 3) {
                productionValueTotal -= planetUnitVal;
                planetUnitVal = 3;
                productionValueTotal += 3;
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValueTotal++;
                }
            }
        }
        if (player.hasTech("absol_ah") && (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0
            || uH.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)) {
            int structures = uH.getUnitCount(UnitType.Spacedock, player.getColor()) + uH.getUnitCount(UnitType.Pds, player.getColor());
            productionValueTotal += structures;
            planetUnitVal = structures;
            if (player.hasRelic("boon_of_the_cerulean_god")) {
                productionValueTotal++;
            }
        }
        if (player.hasTech("ah") && planetUnitVal < 1 && (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0
            || uH.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)) {
            productionValueTotal += 1;
            planetUnitVal = 1;
            if (player.hasRelic("boon_of_the_cerulean_god")) {
                productionValueTotal++;
            }
        } else {
            if (player.hasTech("absol_ie") && planetUnitVal < 1 && player.getPlanets().contains(uH.getName())) {
                productionValueTotal += 1;
                planetUnitVal = 1;
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValueTotal++;
                }
            } else {
                if (player.getPlanets().contains(uH.getName())
                    && player.hasTech("dsbentg") && planetUnitVal < 1
                    && (!uH.getTokenList().isEmpty() || (Mapper.getPlanet(planet).getTechSpecialties() != null
                        && !Mapper.getPlanet(planet).getTechSpecialties().isEmpty()))) {
                    productionValueTotal += 1;
                    if (player.hasRelic("boon_of_the_cerulean_god")) {
                        productionValueTotal++;
                    }
                    planetUnitVal = 1;
                }
            }
        }
        if (player.getPlanets().contains(uH.getName()) && player.getLeader("nokarhero").map(Leader::isActive).orElse(false)) {
            productionValueTotal += 3;
            productionValueTotal -= planetUnitVal;
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
                productionValueTotal += getProductionValueOfUnitHolder(player, game, tile, uH);
            }
            if (tile.getUnitHolders().size() == 1 && player.hasTech("dsmorty")
                && FoWHelper.playerHasShipsInSystem(player, tile)) {
                productionValueTotal += 2;
                if (player.hasRelic("boon_of_the_cerulean_god")) {
                    productionValueTotal++;
                }
            }
        } else {
            int highestProd = 0;
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                for (UnitKey unit : uH.getUnits().keySet()) {
                    if (unit.getColor().equalsIgnoreCase(player.getColor())) {
                        UnitModel unitModel = player.getUnitsByAsyncID(unit.asyncID()).getFirst();
                        if (!"sd".equals(unitModel.getAsyncId())) {
                            continue;
                        }
                        int productionValue = unitModel.getProductionValue();
                        if (unitModel.getBaseType().equalsIgnoreCase("mech") && ButtonHelper.isLawInPlay(game, "articles_war")) {
                            productionValue = 0;
                        }
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
            productionValueTotal -= 2;
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
                numInf += producedUnits.get(unit);
            } else if (unit.contains("ff")) {
                numFF += producedUnits.get(unit);
            } else {
                UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit2), player.getColor());
                UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();
                if (!"flagship".equalsIgnoreCase(removedUnit.getBaseType()) ||
                    !game.playerHasLeaderUnlockedOrAlliance(player, "nomadcommander")) {
                    cost += (int) removedUnit.getCost() * producedUnits.get(unit);
                }
                totalUnits += producedUnits.get(unit);
            }
        }
        if (regulated) {
            cost += numInf + numFF;
        } else {
            if (player.ownsUnit("cymiae_infantry") || player.ownsUnit("cymiae_infantry2")) {
                cost += numInf;
            } else {
                cost += ((numInf + 1) / 2);
            }
            cost += ((numFF + 1) / 2);
        }
        totalUnits += numInf + numFF;
        if (wantCost) {
            return cost;
        } else {
            return totalUnits;
        }

    }

    public static List<Button> getPlaceUnitButtonsForSaarCommander(Player player, Tile origTile, Game game, String placePrefix) {
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
                            if (player.getPlanetsAllianceMode().contains(uH.getName())) {
                                String pp = planet.getName();
                                Button inf1Button = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_" + pp,
                                    "Produce 1 Infantry on " + getPlanetRepresentation(pp, game), FactionEmojis.Saar);
                                unitButtons.add(inf1Button);
                            }
                        } else {
                            Button inf1Button = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_space" + tile.getPosition(),
                                "Produce 1 Inf in " + tile.getPosition() + " space", FactionEmojis.Saar);
                            unitButtons.add(inf1Button);
                        }
                    }
                }
                Button ff1Button = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_fighter_" + tile.getPosition(),
                    "Produce 1 Fighter in " + tile.getPosition(), FactionEmojis.Saar);
                unitButtons.add(ff1Button);
            }
        }

        return unitButtons;
    }

    public static List<Button> getPlaceUnitButtons(GenericInteractionCreateEvent event, Player player, Game game, Tile tile, String warfareNOtherstuff, String placePrefix) {
        List<Button> unitButtons = new ArrayList<>();
        player.resetProducedUnits();
        boolean asn = warfareNOtherstuff.contains("asn");
        warfareNOtherstuff = warfareNOtherstuff.replace("asn", "");
        int resourcelimit = 100;
        String planetInteg = "";
        if (warfareNOtherstuff.contains("integrated")) {
            planetInteg = warfareNOtherstuff.replace("integrated", "");
            UnitHolder plan = ButtonHelper.getUnitHolderFromPlanetName(planetInteg, game);
            if (plan instanceof Planet planetUh) {
                resourcelimit = planetUh.getResources();
            }
        }
        boolean regulated = ButtonHelper.isLawInPlay(game, "conscription")
            || ButtonHelper.isLawInPlay(game, "absol_conscription");
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String tp = tile.getPosition();
        if (!"muaatagent".equalsIgnoreCase(warfareNOtherstuff)) {
            if (player.hasWarsunTech() && resourcelimit > 9) {
                Button wsButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_warsun_" + tp, "Produce War Sun", UnitEmojis.warsun);
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "warsun") > 1) {
                    wsButton = Buttons.gray("FFCC_" + player.getFaction() + "_" + placePrefix + "_warsun_" + tp, "Produce War Sun", UnitEmojis.warsun);
                }
                unitButtons.add(wsButton);
            }
            if (player.ownsUnit("ghemina_flagship_lady") && resourcelimit > 7) {
                Button wsButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_lady_" + tp, "Produce The Lady", UnitEmojis.flagship);
                unitButtons.add(wsButton);
            }
            Button fsButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_flagship_" + tp, "Produce Flagship", UnitEmojis.flagship);
            if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "flagship") > 0) {
                fsButton = Buttons.gray("FFCC_" + player.getFaction() + "_" + placePrefix + "_flagship_" + tp, "Produce Flagship", UnitEmojis.flagship);
            }
            if (resourcelimit > 7) {
                unitButtons.add(fsButton);
            }
        }
        Button dnButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_dreadnought_" + tp, "Produce Dreadnought", UnitEmojis.dreadnought);
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "dreadnought") > 4) {
            dnButton = Buttons.gray("FFCC_" + player.getFaction() + "_" + placePrefix + "_dreadnought_" + tp, "Produce Dreadnought", UnitEmojis.dreadnought);
        }
        if (resourcelimit > 3) {
            unitButtons.add(dnButton);
        }
        Button cvButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_carrier_" + tp, "Produce Carrier", UnitEmojis.carrier);
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "carrier") > 3) {
            cvButton = cvButton.withStyle(ButtonStyle.SECONDARY);
        }
        if (resourcelimit > 2) {
            unitButtons.add(cvButton);
        }
        Button caButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_cruiser_" + tp, "Produce Cruiser", UnitEmojis.cruiser);
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "cruiser") > 7) {
            caButton = Buttons.gray("FFCC_" + player.getFaction() + "_" + placePrefix + "_cruiser_" + tp, "Produce Cruiser", UnitEmojis.cruiser);
        }
        if (resourcelimit > 1) {
            unitButtons.add(caButton);
        }
        Button ddButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_destroyer_" + tp, "Produce Destroyer", UnitEmojis.destroyer);
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "destroyer") > 7) {
            ddButton = Buttons.gray("FFCC_" + player.getFaction() + "_" + placePrefix + "_destroyer_" + tp, "Produce Destroyer", UnitEmojis.destroyer);
        }
        unitButtons.add(ddButton);
        unitButtons.add(Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_fighter_" + tp, "Produce 1 Fighter", UnitEmojis.fighter));
        if (!"arboCommander".equalsIgnoreCase(warfareNOtherstuff) && !"freelancers".equalsIgnoreCase(warfareNOtherstuff)
            && unitHolders.size() < 4 && !regulated && !"sling".equalsIgnoreCase(warfareNOtherstuff)
            && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)
            && getPlaceUnitButtonsForSaarCommander(player, tile, game, placePrefix).isEmpty()) {
            Button ff2Button = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_2ff_" + tp, "Produce 2 Fighters", UnitEmojis.fighter);
            unitButtons.add(ff2Button);
        }

        if (!"arboCommander".equalsIgnoreCase(warfareNOtherstuff) && !"arboHeroBuild".equalsIgnoreCase(warfareNOtherstuff) && !"freelancers".equalsIgnoreCase(warfareNOtherstuff)
            && !"sling".equalsIgnoreCase(warfareNOtherstuff) && !warfareNOtherstuff.contains("integrated")
            && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {

            if (player.hasUnexhaustedLeader("argentagent")) {
                unitButtons.add(Buttons.blue("FFCC_" + player.getFaction() + "_" + "exhaustAgent_argentagent_" + tile.getPosition(), "Use Argent Agent", FactionEmojis.Argent));
            }
            if (player.hasTechReady("sar")) {
                unitButtons.add(Buttons.green("sarMechStep1_" + tile.getPosition() + "_" + warfareNOtherstuff, "Use Self-Assembly Routines", TechEmojis.WarfareTech));
            }
            if (playerHasWarMachine(player)) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + " Reminder that you have _War Machine_ and this is the window for it.");
            }
        }
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet planet && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                boolean singleDock = "warfare".equalsIgnoreCase(warfareNOtherstuff) && !asn;
                if (singleDock) {
                    if (unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) < 1
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
                if (!player.getPlanetsAllianceMode().contains(unitHolder.getName()) && !"genericModifyAllTiles".equals(warfareNOtherstuff)) {
                    continue;
                }

                String pp = planet.getName();
                if ("genericBuild".equalsIgnoreCase(warfareNOtherstuff)) {
                    Button sdButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_sd_" + pp,
                        "Place 1 Space Dock on " + getPlanetRepresentation(pp, game), UnitEmojis.spacedock);
                    unitButtons.add(sdButton);
                    Button pdsButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_pds_" + pp,
                        "Place 1 PDS on " + getPlanetRepresentation(pp, game), UnitEmojis.pds);
                    unitButtons.add(pdsButton);
                }
                Button inf1Button = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_" + pp,
                    "Produce 1 Infantry on " + getPlanetRepresentation(pp, game), UnitEmojis.infantry);
                unitButtons.add(inf1Button);
                if (!"genericBuild".equalsIgnoreCase(warfareNOtherstuff)
                    && !"freelancers".equalsIgnoreCase(warfareNOtherstuff)
                    && !"arboCommander".equalsIgnoreCase(warfareNOtherstuff) && !regulated && unitHolders.size() < 4
                    && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)
                    && getPlaceUnitButtonsForSaarCommander(player, tile, game, placePrefix).isEmpty()) {
                    Button inf2Button = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_" + pp,
                        "Produce 2 Infantry on " + getPlanetRepresentation(pp, game), UnitEmojis.infantry);
                    unitButtons.add(inf2Button);
                }
                Button mfButton = Buttons.green("FFCC_" + player.getFaction() + "_" + placePrefix + "_mech_" + pp, "Produce Mech on " + getPlanetRepresentation(pp, game), UnitEmojis.mech);
                if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") > 3) {
                    mfButton = Buttons.gray("FFCC_" + player.getFaction() + "_" + placePrefix + "_mech_" + pp, "Produce Mech on " + getPlanetRepresentation(pp, game), UnitEmojis.mech);
                }
                if (resourcelimit > 1) {
                    unitButtons.add(mfButton);
                }

            } else if (ButtonHelper.canIBuildGFInSpace(player, tile, warfareNOtherstuff)
                && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                Button inf1Button = Buttons.green(
                    "FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_space" + tile.getPosition(),
                    "Produce 1 Infantry in space", UnitEmojis.infantry);
                unitButtons.add(inf1Button);
                if (!"genericBuild".equalsIgnoreCase(warfareNOtherstuff)
                    && !"freelancers".equalsIgnoreCase(warfareNOtherstuff)
                    && !"arboCommander".equalsIgnoreCase(warfareNOtherstuff) && unitHolders.size() < 4
                    && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)
                    && getPlaceUnitButtonsForSaarCommander(player, tile, game, placePrefix).isEmpty()) {
                    Button inf2Button = Buttons.green(
                        "FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_space" + tile.getPosition(),
                        "Produce 2 Infantry in space", UnitEmojis.infantry);
                    unitButtons.add(inf2Button);
                }
                Button mfButton = Buttons.green(
                    "FFCC_" + player.getFaction() + "_" + placePrefix + "_mech_space" + tile.getPosition(),
                    "Produce Mech in space", UnitEmojis.mech);
                unitButtons.add(mfButton);
            }
        }
        if (!"sling".equalsIgnoreCase(warfareNOtherstuff) && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {
            unitButtons.addAll(getPlaceUnitButtonsForSaarCommander(player, tile, game, placePrefix));
        }
        if ("place".equalsIgnoreCase(placePrefix)) {
            Button DoneProducingUnits = Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons_" + warfareNOtherstuff + "_" + tile.getPosition(), "Done Producing Units");
            unitButtons.add(DoneProducingUnits);
            unitButtons.add(Buttons.gray(player.getFinsFactionCheckerPrefix() + "resetProducedThings", "Reset Build"));
        }
        if (player.hasTech("yso")) {
            if ("sling".equalsIgnoreCase(warfareNOtherstuff) || "freelancers".equalsIgnoreCase(warfareNOtherstuff) || "chaosM".equalsIgnoreCase(warfareNOtherstuff)) {
                List<Button> unitButtons2 = new ArrayList<>();
                unitButtons2.add(Buttons.gray("startYinSpinner", "Yin Spin 2 Duders", FactionEmojis.Yin));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentationUnfogged() + " you may use this to Yin Spin.", unitButtons2);
            } else {
                unitButtons.add(Buttons.gray("startYinSpinner", "Yin Spin 2 Duders", FactionEmojis.Yin));
            }
        }

        return unitButtons;
    }

    private static boolean playerHasWarMachine(Player player) {
        return player.getActionCards().containsKey("war_machine1")
            || player.getActionCards().containsKey("war_machine2")
            || player.getActionCards().containsKey("war_machine3")
            || player.getActionCards().containsKey("war_machine4")
            || player.getActionCards().containsKey("war_machine_ds")
            || player.getActionCards().containsKey("war_machine1_acd2")
            || player.getActionCards().containsKey("war_machine2_acd2")
            || player.getActionCards().containsKey("war_machine3_acd2")
            || player.getActionCards().containsKey("war_machine4_acd2");
    }

    public static List<Button> getPlanetSystemDiploButtons(Player player, Game game, boolean ac, Player mahact) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> tilePos = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
        if (mahact == null) {
            for (String planet : planets) {
                if (planet.equalsIgnoreCase("ghoti") || planet.contains("custodia")) {
                    continue;
                }
                Tile tile = game.getTileFromPlanet(planet);
                if (!getPlanetRepresentation(planet, game).toLowerCase().contains("mecatol") || ac) {
                    if (tile != null && !tilePos.contains(tile.getPosition())) {
                        tilePos.add(tile.getPosition());
                        Button button = Buttons.gray(finsFactionCheckerPrefix + "diplo_" + planet + "_" + "diploP",
                            tile.getRepresentationForButtons());
                        planetButtons.add(button);
                    }
                }
            }
        } else {
            for (Tile tile : game.getTileMap().values()) {
                if (FoWHelper.playerHasUnitsInSystem(player, tile) && !tile.isHomeSystem()) {
                    Button button = Buttons.gray(finsFactionCheckerPrefix + "diplo_" + tile.getPosition() + "_"
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
        }
        return unitHolder.getResources();
    }

    public static int getPlanetInfluence(String planetID, Game game) {
        Planet unitHolder = game.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return 0;
        }
        return unitHolder.getInfluence();
    }

    @Deprecated
    public static String getLeaderRepresentation(Leader leader, boolean includeTitle, boolean includeAbility, boolean includeUnlockCondition) {
        String leaderID = leader.getId();

        LeaderModel leaderModel = Mapper.getLeader(leaderID);
        if (leaderModel == null) {
            BotLogger.warning("Invalid `leaderID=" + leaderID + "` caught within `Helper.getLeaderRepresentation`");
            return leader.getId();
        }

        String leaderName = leaderModel.getName();
        String leaderTitle = leaderModel.getTitle();
        String heroAbilityName = leaderModel.getAbilityName().orElse("");
        String leaderAbilityWindow = leaderModel.getAbilityWindow();
        String leaderAbilityText = leaderModel.getAbilityText();
        String leaderUnlockCondition = leaderModel.getUnlockCondition();

        StringBuilder representation = new StringBuilder();
        representation.append(LeaderEmojis.getLeaderEmoji(leader)).append(" __**").append(leaderName).append("**");
        if (includeTitle)
            representation.append(" - ").append(leaderTitle).append("__"); // add title
        if (includeAbility && Constants.HERO.equals(leader.getType()))
            representation.append("\n").append("**").append(heroAbilityName).append("**"); // add hero ability name
        if (includeAbility)
            if (leaderAbilityWindow.equalsIgnoreCase("action:")) {
                representation.append("\n*ACTION:*").append(leaderAbilityText); // add ability
            } else {
                representation.append("\n*").append(leaderAbilityWindow).append("*\n").append(leaderAbilityText); // add ability
            }
        if (includeUnlockCondition) {
            representation.append("\n*UNLOCK:* ").append(leaderUnlockCondition);
        }

        return representation.toString();
    }

    public static String getLeaderRepresentation(Player player, String leaderID, boolean includeTitle, boolean includeAbility) {
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

    public static void isCCCountCorrect(Player player) {
        int ccCount = getCCCount(player.getGame(), player.getColor());
        informUserCCOverLimit(player.getGame(), player.getColor(), ccCount);
    }

    public static void isCCCountCorrect(GenericInteractionCreateEvent event, Game game, String color) {
        int ccCount = getCCCount(game, color);
        informUserCCOverLimit(game, color, ccCount);
    }

    public static int getCCCount(Game game, String color) {
        int ccCount = 0;
        if (color == null) {
            return 0;
        }
        Map<String, Tile> tileMap = game.getTileMap();
        for (Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
            Tile tile = tileEntry.getValue();
            boolean hasCC = CommandCounterHelper.hasCC(null, color, tile);
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

    private static void informUserCCOverLimit(Game game, String color, int ccCount) {
        int limit = 16;
        if (!game.getStoredValue("ccLimit").isEmpty()) {
            limit = Integer.parseInt(game.getStoredValue("ccLimit"));
        }
        boolean ccCountIsOver = ccCount > limit;
        if (ccCountIsOver && game.isCcNPlasticLimit()) {
            Player player = game.getPlayerFromColorOrFaction(color);
            if (player == null) {
                return;
            }

            String msg = player.getRepresentationUnfogged() + " is over the command token limit of " + limit + ". Command tokens used: " + ccCount;
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        }
    }

    private static Integer getTotalPlanetSumValue(Game game, Player player, ToIntFunction<Planet> valueFunction) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();

        if (player.hasLeaderUnlocked("xxchahero")) {
            valueFunction = p -> p.getResources() + p.getInfluence();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull).mapToInt(valueFunction).sum();
    }

    private static Integer getAvailablePlanetSumValue(Game game, Player player, ToIntFunction<Planet> valueFunction) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();

        if (player.hasLeaderUnlocked("xxchahero")) {
            valueFunction = p -> p.getResources() + p.getInfluence();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull).mapToInt(valueFunction).sum();
    }

    public static Integer getPlayerResourcesAvailable(Player player, Game game) {
        return getAvailablePlanetSumValue(game, player, Planet::getResources);
    }

    public static Integer getPlayerResourcesTotal(Player player, Game game) {
        return getTotalPlanetSumValue(game, player, Planet::getResources);
    }

    public static Integer getPlayerOptimalResourcesAvailable(Player player, Game game) {
        return getAvailablePlanetSumValue(game, player, Planet::getOptimalResources);
    }

    public static Integer getPlayerOptimalResourcesTotal(Player player, Game game) {
        return getTotalPlanetSumValue(game, player, Planet::getOptimalResources);
    }

    public static Integer getPlayerInfluenceAvailable(Player player, Game game) {
        return getAvailablePlanetSumValue(game, player, Planet::getInfluence);
    }

    public static Integer getPlayerInfluenceTotal(Player player, Game game) {
        return getTotalPlanetSumValue(game, player, Planet::getInfluence);
    }

    public static Integer getPlayerOptimalInfluenceAvailable(Player player, Game game) {
        return getAvailablePlanetSumValue(game, player, Planet::getOptimalInfluence);
    }

    public static Integer getPlayerOptimalInfluenceTotal(Player player, Game game) {
        return getTotalPlanetSumValue(game, player, Planet::getOptimalInfluence);
    }

    public static Integer getPlayerFlexResourcesInfluenceAvailable(Player player, Game game) {
        return getAvailablePlanetSumValue(game, player, Planet::getFlexResourcesOrInfluence);
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
        if (text.length() > length) {
            return text;
        }
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
        if (game.isCommunityMode()) {
            return;
        }
        if (game.isFowMode()) {
            addPlayerPermissionsToPrivateChannels(game);
            return;
        }

        String gameName = game.getName();
        List<Role> roles = guild.getRolesByName(gameName, true);
        Role role = null;
        if (!roles.isEmpty()) {
            if (roles.size() > 1) {
                BotLogger.warning(new BotLogger.LogMessageOrigin(game), "There are " + roles.size() + " roles that match the game name: `" + gameName
                    + "` - please investigate, as this may cause issues.");
                return;
            }
            role = roles.getFirst();
        }

        if (role == null) { // make sure players have access to the game channels
            addMapPlayerPermissionsToGameChannels(guild, game.getName());
        } else { // make sure players have the role
            addGameRoleToMapPlayers(guild, role, game);
        }
    }

    public static void addPlayerPermissionsToPrivateChannels(Game game) {
        //Make sure everyone has access to their own private thread
        long permission = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();
        for (Player player : game.getPlayers().values()) {
            MessageChannel channel = player.getPrivateChannel();
            if (channel != null) {
                ((TextChannel) channel).getManager().putMemberPermissionOverride(player.getMember().getIdLong(), permission, 0).queue();
            }
        }
    }

    public static void addMapPlayerPermissionsToGameChannels(Guild guild, String gameName) {
        if (!GameManager.isValid(gameName)) {
            return;
        }
        ManagedGame game = GameManager.getManagedGame(gameName);
        var players = game.getPlayerIds();
        TextChannel tableTalkChannel = game.getTableTalkChannel();
        if (tableTalkChannel != null) {
            addPlayerPermissionsToGameChannel(guild, tableTalkChannel, players);
        }
        TextChannel actionsChannel = game.getMainGameChannel();
        if (actionsChannel != null) {
            addPlayerPermissionsToGameChannel(guild, actionsChannel, players);
        }
        List<GuildChannel> channels = guild.getChannels().stream().filter(c -> c.getName().startsWith(gameName)).toList();
        for (GuildChannel channel : channels) {
            addPlayerPermissionsToGameChannel(guild, channel, players);
        }
    }

    public static void addBotHelperPermissionsToGameChannels(GenericInteractionCreateEvent event) {
        var guild = event.getGuild();
        if (guild == null) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "Guild was null in addBotHelperPermissionsToGameChannels.");
            return;
        }
        // long role = 1093925613288562768L;
        long role = 1166011604488425482L;

        for (ManagedGame game : GameManager.getManagedGames()) {
            if (!game.isHasEnded()) {
                if (game.getGuild() != null && game.getGuild().equals(guild)) {
                    var tableTalkChannel = game.getTableTalkChannel();
                    if (tableTalkChannel != null) {
                        addRolePermissionsToGameChannel(guild, tableTalkChannel, role);
                    }
                    var mainGameChannel = game.getMainGameChannel();
                    if (mainGameChannel != null) {
                        addRolePermissionsToGameChannel(guild, mainGameChannel, role);
                    }
                }
                String gameName = game.getName();
                List<GuildChannel> channels = guild.getChannels().stream().filter(c -> c.getName().startsWith(gameName)).toList();
                for (GuildChannel channel : channels) {
                    addRolePermissionsToGameChannel(guild, channel, role);
                }
            }
        }
    }

    private static void addPlayerPermissionsToGameChannel(Guild guild, GuildChannel channel, Collection<String> playerIds) {
        TextChannel textChannel = guild.getTextChannelById(channel.getId());
        if (textChannel != null) {
            TextChannelManager textChannelManager = textChannel.getManager();
            for (String playerID : playerIds) {
                Member member = guild.getMemberById(playerID);
                if (member == null)
                    continue;
                long allow = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();
                textChannelManager.putMemberPermissionOverride(member.getIdLong(), allow, 0);
            }
            textChannelManager.queue();
        }
    }

    private static void addRolePermissionsToGameChannel(Guild guild, GuildChannel channel, long role) {
        TextChannel textChannel = guild.getTextChannelById(channel.getId());
        if (textChannel != null) {
            TextChannelManager textChannelManager = textChannel.getManager();
            long allow = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();
            textChannelManager.putRolePermissionOverride(role, allow, 0);
            textChannelManager.queue();
        }
    }

    private static void addGameRoleToMapPlayers(Guild guild, Role role, Game game) {
        for (var playerId : game.getPlayerIDs()) {
            if (game.getRound() > 1 && !game.getPlayer(playerId).isRealPlayer()) {
                continue;
            }
            Member member = guild.getMemberById(playerId);
            if (member != null && !member.getRoles().contains(role)) guild.addRoleToMember(member, role).queue();
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
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "Something went wrong getting thread channels.", e);
            return null;
        }
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

    public static Role getEventGuildRole(GenericInteractionCreateEvent event, String roleName) {
        try {
            return event.getGuild().getRolesByName(roleName, true).getFirst();
        } catch (Exception e) {
            return null;
        }
    }

    public static void reverseSpeakerOrder(Game game) {
        Map<String, Player> newPlayerOrder = new LinkedHashMap<>();
        Map<String, Player> players = new LinkedHashMap<>(game.getPlayers());
        List<Player> sortedPlayers1 = game.getRealPlayers();
        List<Player> sortedPlayers = new ArrayList<>();
        for (Player player : sortedPlayers1) {
            sortedPlayers.add(0, player);
        }
        Map<String, Player> playersBackup = new LinkedHashMap<>(game.getPlayers());
        try {
            for (Player player : sortedPlayers) {
                SetOrderService.setPlayerOrder(newPlayerOrder, players, player);

            }
            if (!players.isEmpty()) {
                newPlayerOrder.putAll(players);
            }
            game.setPlayers(newPlayerOrder);
        } catch (Exception e) {
            game.setPlayers(playersBackup);
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
            if (tile != null) {
                int parsedLocation = 9999;
                try {
                    parsedLocation = Integer.parseInt(tile.getPosition());
                } catch (Exception ignored) {}
                hsLocations.add(parsedLocation);
                unsortedPlayers.put(parsedLocation, player);
            }
        }
        MapTemplateModel template = Mapper.getMapTemplate(game.getMapTemplateID());
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
        if (different && template != null) {
            hsLocations = template.getSortedHomeSystemLocations();
        }

        StringBuilder msg = new StringBuilder(game.getPing() + " set order in the following way: \n");
        if (!different || template != null) {
            List<Player> sortedPlayers = new ArrayList<>();
            for (Integer location : hsLocations) {
                sortedPlayers.add(unsortedPlayers.get(location));
            }

            Map<String, Player> newPlayerOrder = new LinkedHashMap<>();
            Map<String, Player> players = new LinkedHashMap<>(game.getPlayers());
            Map<String, Player> playersBackup = new LinkedHashMap<>(game.getPlayers());
            try {
                for (Player player : sortedPlayers) {
                    SetOrderService.setPlayerOrder(newPlayerOrder, players, player);
                    if (player.isSpeaker()) {
                        msg.append(player.getRepresentationUnfogged()).append(" ").append(MiscEmojis.SpeakerToken).append(" \n");
                    } else {
                        msg.append(player.getRepresentationUnfogged()).append(" \n");
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
            msg = new StringBuilder("Detected an abnormal map, so did not assign speaker order automatically. Set the speaker order with `/game set_order`, with the speaker as the first player.");
        }
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg.toString());

            List<Tile> tiles = new ArrayList<>(game.getTileMap().values());
            for (Tile tile : tiles) {
                if (tile == null) {
                    continue;
                }
                if (tile.getTileID().equals("0g") || tile.getTileID().equals("-1") || tile.getTileID().equals("0gray")) {
                    game.removeTile(tile.getPosition());
                }
            }
        }
    }

    public static void checkEndGame(Game game, Player player) {
        if (player.getTotalVictoryPoints() >= game.getVp()) {
            List<Button> buttons = new ArrayList<>();
            if (!game.isFowMode()) {
                buttons.add(Buttons.green("gameEnd", "End Game"));
                buttons.add(Buttons.blue("rematch", "Rematch (make new game with same players/channels)"));
            } else {
                buttons.add(Buttons.green("gameEndConfirmation", "End and Delete Game"));
            }
            buttons.add(Buttons.red("deleteButtons", "Mistake, delete these"));
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                "# " + game.getPing() + " it appears as though " + player.getRepresentationNoPing()
                    + " has won the game!\nPress the **End Game** button when you are done with the channels, or ignore this if it was a mistake/more complicated.",
                buttons);
            if (game.isFowMode()) {
                GMService.sendMessageToGMChannel(game, "# GAME HAS ENDED", true);
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), """
                    ## Note about FoW
                    When you press **End Game** all the game channels will be deleted immediately!
                    A new thread will be generated under the **#fow-war-stories** channel.
                    Round Summaries will be shared there. So it is advised to hold end-of-game chat until then.""");
                List<Button> titleButton = new ArrayList<>();
                titleButton.add(Buttons.blue("offerToGiveTitles", "Offer to bestow a Title"));
                titleButton.add(Buttons.gray("deleteButtons", "No titles for this game"));
                MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
                    "### Offer everyone a chance to bestow a title. This is totally optional.\n"
                        + "Press **End Game** only after done giving titles.",
                    titleButton);
            }
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

    // TODO (Jazz): Make one TokenModel to rule them all
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
        DefaultGuildChannelUnion defaultChannel = guild.getDefaultChannel();
        if (!(defaultChannel instanceof TextChannel tc)) {
            BotLogger.error(new BotLogger.LogMessageOrigin(guild), "Default channel is not available or is not a text channel on " + guild.getName());
        } else {
            return tc.createInvite()
                .setMaxUses(uses)
                .setMaxAge((long) (forever ? 0 : 4), TimeUnit.DAYS)
                .complete()
                .getUrl();
        }
        return "Whoops invalid url. Have one of the players on the server generate an invite";
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
            if (alias.isEmpty()) {
                continue;
            }
            UnitType ut = Units.findUnitType(AliasHandler.resolveUnit(alias));
            sb.append(StringUtils.repeat(ut.getUnitTypeEmoji().toString(), count));
        }
        return sb.toString();
    }
}
