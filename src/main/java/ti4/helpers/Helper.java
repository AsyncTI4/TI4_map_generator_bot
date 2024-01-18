package ti4.helpers;

import java.awt.Point;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.buttons.ButtonListener;
import ti4.commands.bothelper.ArchiveOldThreads;
import ti4.commands.bothelper.ListOldThreads;
import ti4.commands.capture.RemoveUnits;
import ti4.commands.game.SetOrder;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.commands.milty.StartMilty;
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
import ti4.model.LeaderModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

public class Helper {

    @Nullable
    public static Player getGamePlayer(Game activeGame, Player initialPlayer, GenericInteractionCreateEvent event, String userID) {
        return getGamePlayer(activeGame, initialPlayer, event.getMember(), userID);
    }

    public static int getCurrentHour() {
        long currentTime = new Date().getTime();
        currentTime = currentTime / 1000;
        currentTime = currentTime % (60 * 60 * 24);
        currentTime = currentTime / (60 * 60);
        return (int) currentTime;
    }

    @Nullable
    public static Player getGamePlayer(Game activeGame, Player initialPlayer, Member member, String userID) {
        Collection<Player> players = activeGame.getPlayers().values();
        if (!activeGame.isCommunityMode()) {
            Player player = activeGame.getPlayer(userID);
            if (player != null) return player;
            return initialPlayer;
        }
        if (member == null) {
            Player player = activeGame.getPlayer(userID);
            if (player != null) return player;
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
        return initialPlayer != null ? initialPlayer : activeGame.getPlayer(userID);
    }

    @Nullable
    public static Player getPlayer(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            if (activeGame.getPlayer(playerID) != null) {
                player = activeGame.getPlayers().get(playerID);
            } else {
                player = null;
            }
        } else if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            factionColor = StringUtils.substringBefore(factionColor, " "); //TO HANDLE UNRESOLVED AUTOCOMPLETE
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeGame.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) ||
                    Objects.equals(factionColor, player_.getColor())) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    public static boolean isSaboAllowed(Game activeGame, Player player) {
        if ("pbd100".equalsIgnoreCase(activeGame.getName())) {
            return true;
        }
        if (activeGame.getDiscardActionCards().containsKey("sabo1") && activeGame.getDiscardActionCards().containsKey("sabo2") && activeGame.getDiscardActionCards().containsKey("sabo3")
            && activeGame.getDiscardActionCards().containsKey("sabo4")) {
            return false;
        }
        if (player.hasTech("tp") && activeGame.getActivePlayerID() != null && activeGame.getActivePlayerID().equalsIgnoreCase(player.getUserID())) {
            for (Player p2 : activeGame.getRealPlayers()) {
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

    public static boolean doesAnyoneOwnPlanet(Game activeGame, String planet) {
        for (Player player : activeGame.getRealPlayers()) {
            if (player.getPlanets().contains(planet)) {
                return true;
            }
        }
        return false;
    }

    public static boolean doesAllianceMemberOwnPlanet(Game activeGame, String planet, Player p1) {
        for (Player player : activeGame.getRealPlayers()) {
            if (player.getPlanets().contains(planet) && p1.getAllianceMembers().contains(player.getFaction())) {
                return true;
            }
        }
        return false;
    }

    public static Player getPlayerFromAbility(Game activeGame, String ability) {
        Player player = null;
        if (ability != null) {
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_.isRealPlayer() && player_.hasAbility(ability)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    public static void getRandomBlueTile(Game activeGame, GenericInteractionCreateEvent event) {
        MiltyDraftManager draftManager = activeGame.getMiltyDraftManager();
        new StartMilty().initDraftTiles(draftManager);
        List<MiltyDraftTile> allTiles;
        allTiles = draftManager.getHigh();
        allTiles.addAll(draftManager.getMid());
        allTiles.addAll(draftManager.getLow());
        boolean inMap = true;
        int counter = 1;
        while (inMap && counter < 1000) {
            int result = ThreadLocalRandom.current().nextInt(1, allTiles.size());

            MiltyDraftTile tile = allTiles.get(result);
            tile.getTile().getTileID();
            boolean foundInMap = false;
            for (Tile mapTile : activeGame.getTileMap().values()) {
                if (mapTile.getTileID().equalsIgnoreCase(tile.getTile().getTileID())) {
                    foundInMap = true;
                    break;
                }
            }
            if (!foundInMap) {
                inMap = false;
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You randomly drew the tile: " + tile.getTile().getRepresentation());
            }
            counter++;
        }
    }

    public static boolean shouldPlayerLeaveAReact(Player player, Game activeGame, String messageID) {
        if (player.getAutoSaboPassMedian() == 0) {
            return false;
        }
        if (player.isAFK()) {
            return false;
        }
        if (player.hasTechReady("it") && player.getStrategicCC() > 0) {
            return false;
        }
        if ((player.getActionCards().containsKey("sabo1") || player.getActionCards().containsKey("sabotage_ds") || player.getActionCards().containsKey("sabo2") ||
            player.getActionCards().containsKey("sabo3") || player.getActionCards().containsKey("sabo4")
            || (activeGame.getActionCardDeckSize() + activeGame.getDiscardActionCards().size()) > 180) && !ButtonHelper.isPlayerElected(activeGame, player, "censure") && !ButtonHelper.isPlayerElected(activeGame, player, "absol_censure") ) {
            return false;
        }
        if (player.hasUnit("empyrean_mech") && ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Mech).size() > 0) {
            return false;
        }
        return !ButtonListener.checkForASpecificPlayerReact(messageID, player, activeGame);
        // int highNum = player.getAutoSaboPassMedian()*6*3/2;
        // int result = ThreadLocalRandom.current().nextInt(1,highNum+1);
        // if(result == highNum){
        //     return true;
        // }else{
        //     return false;
        // }
    }

    public static void giveMeBackMyAgendaButtons(Game activeGame){
        List<Button> proceedButtons = new ArrayList<>();
        String msg = "Press this button if the last person forgot to react, but verbally said no whens/afters";
        proceedButtons.add(Button.danger("proceedToVoting", "Skip waiting and start the voting for everyone"));
        proceedButtons.add(Button.primary("transaction", "Transaction"));
        proceedButtons.add(Button.danger("eraseMyVote", "Erase my vote & have me vote again"));
        proceedButtons.add(Button.danger("eraseMyRiders", "Erase my riders"));
        MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), msg, proceedButtons);
    }

    public static List<Player> getInitativeOrder(Game activeGame){
        HashMap<Integer, Player> order = new HashMap<>();
        int naaluSC = 0;
        for (Player player : activeGame.getRealPlayers()) {
            int sc = player.getLowestSC();
            String scNumberIfNaaluInPlay = activeGame.getSCNumberIfNaaluInPlay(player, Integer.toString(sc));
            if (scNumberIfNaaluInPlay.startsWith("0/")) {
                naaluSC = sc;
            }
            order.put(sc, player);
        }
        List<Player> initiativeOrder = new ArrayList<>();
        Integer max = Collections.max(activeGame.getScTradeGoods().keySet());
        if(ButtonHelper.getKyroHeroSC(activeGame) != 1000){
            max = max+1;
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
    public static List<Player> getInitativeOrderFromThisPlayer(Player p1, Game activeGame){
        List<Player> players = new ArrayList<>();
        
        List<Player> initiativeOrder = getInitativeOrder(activeGame);
        boolean found = false;
        for(Player p2 : initiativeOrder){
            if(p2 == p1){
                found = true;
                players.add(p1);
            }else{
                if(found){
                    players.add(p2);
                }
            }
        }
        for(Player p2 : initiativeOrder){
            if(p2 == p1){
                found = false;
            }else{
                if(found){
                    players.add(p2);
                }
            }
        }
        return players;
    }
    public static List<Player> getSpeakerOrderFromThisPlayer(Player player, Game activeGame){
        List<Player> players = new ArrayList<>();
        boolean found = false;
        for(Player p2 : activeGame.getRealPlayers()){
            if(p2 == player){
                found = true;
                players.add(player);
            }else{
                if(found){
                    players.add(p2);
                }
            }
        }

        for(Player p2 : activeGame.getRealPlayers()){
            if(p2 == player){
                found = false;
            }else{
                if(found){
                    players.add(p2);
                }
            }
        }
        return players;
    }

    public static boolean hasEveryoneResolvedBeforeMe(Player player, String factionsThatHaveResolved, List<Player> orderList){
        for(Player p2 : orderList){
            if(p2 == player){
                return true;
            }
            if(!factionsThatHaveResolved.contains(p2.getFaction())){
                return false;
            }
        }
        return true;
    }

    public static void checkAllSaboWindows(Game activeGame) {
        List<String> messageIDs = new ArrayList<>(activeGame.getMessageIDsForSabo());
        for (Player player : activeGame.getRealPlayers()) {
            if (player.getAutoSaboPassMedian() == 0) {
                continue;
            }
            int highNum = player.getAutoSaboPassMedian() * 6 * 3 / 2;
            int result = ThreadLocalRandom.current().nextInt(1, highNum + 1);
            boolean shouldDoIt = result == highNum;
            if (shouldDoIt) {
                for (String messageID : messageIDs) {
                    if (shouldPlayerLeaveAReact(player, activeGame, messageID)) {
                        String message = activeGame.isFoWMode() ? "No sabotage" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, messageID, activeGame);
                    }
                }
            }
            if(activeGame.getCurrentPhase().equals("agendawaiting")){
                int highNum2 = player.getAutoSaboPassMedian() * 4 / 2;
                int result2 = ThreadLocalRandom.current().nextInt(1, highNum2 + 1);
                boolean shouldDoIt2 = result2 == highNum2;
                if(shouldDoIt2){
                    String whensID = activeGame.getLatestWhenMsg();
                    if (!AgendaHelper.doesPlayerHaveAnyWhensOrAfters(player) && !ButtonListener.checkForASpecificPlayerReact(whensID, player, activeGame)) {
                        String message = activeGame.isFoWMode() ? "No whens" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, whensID, activeGame);
                    }
                    String aftersID = activeGame.getLatestAfterMsg();
                    if (!AgendaHelper.doesPlayerHaveAnyWhensOrAfters(player) && !ButtonListener.checkForASpecificPlayerReact(aftersID, player, activeGame)) {
                        String message = activeGame.isFoWMode() ? "No afters" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, aftersID, activeGame);
                    }
                }
            }

        }
    }

    public static Player getPlayerFromUnlockedLeader(Game activeGame, String leader) {
        Player player = null;
        if (leader != null) {
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_.isRealPlayer() && player_.hasLeaderUnlocked(leader)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    public static Player getPlayerFromUnit(Game activeGame, String unit) {
        Player player = null;
        if (unit != null) {
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_.isRealPlayer() && player_.getUnitsOwned().contains(unit)) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    @Nullable
    public static String getColor(Game activeGame, SlashCommandInteractionEvent event) {
        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
        if (factionColorOption != null) {
            String colorFromString = getColorFromString(activeGame, factionColorOption.getAsString());
            if (Mapper.isValidColor(colorFromString)) {
                return colorFromString;
            }
        } else {
            String userID = event.getUser().getId();
            Player foundPlayer = activeGame.getPlayers().values().stream().filter(player -> player.getUserID().equals(userID)).findFirst().orElse(null);
            foundPlayer = getGamePlayer(activeGame, foundPlayer, event, null);
            if (foundPlayer != null) {
                return foundPlayer.getColor();
            }
        }
        return null;
    }

    public static String getColorFromString(Game activeGame, String factionColor) {
        factionColor = AliasHandler.resolveColor(factionColor);
        factionColor = AliasHandler.resolveFaction(factionColor);
        for (Player player_ : activeGame.getPlayers().values()) {
            if (Objects.equals(factionColor, player_.getFaction()) ||
                Objects.equals(factionColor, player_.getColor())) {
                return player_.getColor();
            }
        }
        return factionColor;
    }

    @Nullable
    public static String getDamagePath() {
        String tokenPath = ResourceHelper.getInstance().getResourceFromFolder("extra/", "marker_damage.png", "Could not find damage token file");
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
        String tokenPath = ResourceHelper.getInstance().getResourceFromFolder("extra/", file, "Could not find adjacency file for direction: " + direction);
        if (tokenPath == null) {
            BotLogger.log("Could not find token: " + file);
            return null;
        }
        return tokenPath;
    }

    public static void addMirageToTile(Tile tile) {
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        if (unitHolders.get(Constants.MIRAGE) == null) {
            Point mirageCenter = new Point(Constants.MIRAGE_POSITION.x + Constants.MIRAGE_CENTER_POSITION.x, Constants.MIRAGE_POSITION.y + Constants.MIRAGE_CENTER_POSITION.y);
            Planet planetObject = new Planet(Constants.MIRAGE, mirageCenter);
            unitHolders.put(Constants.MIRAGE, planetObject);
        }
    }

    public static String getDateRepresentation(long dateInfo) {
        Date date = new Date(dateInfo);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd");
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

    public static String getSCAsMention(int sc, Game activeGame) {
        if (activeGame.isHomeBrewSCMode()) {
            return getSCName(sc, activeGame);
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

    public static String getSCRepresentation(Game activeGame, int sc) {
        if (activeGame.isHomeBrewSCMode()) return "SC #" + sc + " " + getSCName(sc, activeGame);
        return getSCAsMention(sc, activeGame);
    }

    public static String getSCName(int sc, Game activeGame) {
        if (Optional.ofNullable(activeGame.getScSetID()).isPresent() && !"null".equals(activeGame.getScSetID())) {
            return Mapper.getStrategyCardSets().get(activeGame.getScSetID()).getCardValues().get(sc);
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

    public static File getSCImageFile(Integer sc, Game activeGame) {
        String scSet = activeGame.getScSetID();
        if (Optional.ofNullable(activeGame.getScSetID()).isEmpty() || "null".equals(activeGame.getScSetID())) { //I don't know *why* this is a thing that can happen, but it is
            scSet = "pok";
        }
        boolean gameWithGroupedSCs = "pbd100".equals(activeGame.getName()) || "pbd500".equals(activeGame.getName()) && !"tribunal".equals(scSet);
        String scAsString = String.valueOf(sc);
        if (gameWithGroupedSCs) {
            char scValue = String.valueOf(sc).charAt(0);
            scAsString = String.valueOf(scValue);
            scSet = scSet.replace("pbd100", "pok");
            scSet = scSet.replace("pbd1000", "pok");
        }

        String scImagePath = ResourceHelper.getInstance().getResourceFromFolder("strat_cards/", scSet + "_" + scAsString + ".png", "Could not find SC image!");
        if (scImagePath == null) scImagePath = ResourceHelper.getInstance().getResourceFromFolder("strat_cards/", "sadFace.png", "Could not find SC image!");

        return new File(scImagePath);
    }

    public static Emoji getPlayerEmoji(Game activeGame, Player player, Message message) {
        Emoji emojiToUse;
        emojiToUse = Emoji.fromFormatted(player.getFactionEmoji());
        String messageId = message.getId();

        if (activeGame.isFoWMode()) {
            int index = 0;
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_ == player) break;
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
            name.append(Mapper.getPlanet(planet).getResources()).append("/").append(Mapper.getPlanet(planet).getInfluence()).append(", ");
        }
        if (TileHelper.getTile(tileID).getPlanets().size() > 0) {
            name = new StringBuilder(name.substring(0, name.length() - 2) + ")");
        }
        return name.toString();
    }

    public static String getPlanetRepresentation(String planet, Game activeGame) {
        planet = planet.toLowerCase().replace(" ", "");
        planet = planet.replace("'", "");
        planet = planet.replace("-", "");
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planet));
        Planet planet2 = (Planet) unitHolder;
        if (planet2 == null) {
            return planet + " bot error. Tell fin";
        }

        return Mapper.getPlanetRepresentations().get(AliasHandler.resolvePlanet(planet)) + " (" + planet2.getResources() + "/" + planet2.getInfluence() + ")";
    }

    public static String getPlanetRepresentationPlusEmojiPlusResourceInfluence(String planetID, Game activeGame) {
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = (Planet) unitHolder;
            String techType = "";
            String techEmoji = "";
            if (Mapper.getPlanet(planetID) != null && Mapper.getPlanet(planetID).getTechSpecialties() != null && Mapper.getPlanet(planetID).getTechSpecialties().size() > 0) {
                techType = Mapper.getPlanet(planetID).getTechSpecialties().get(0).toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(activeGame, AliasHandler.resolvePlanet(planetID));
            }
            if (!"".equalsIgnoreCase(techType)) {
                switch (techType) {
                    case "propulsion" -> techEmoji = Emojis.PropulsionTech;
                    case "warfare" -> techEmoji =Emojis.WarfareTech;
                    case "cybernetic" -> techEmoji = Emojis.CyberneticTech;
                    case "biotic" -> techEmoji = Emojis.BioticTech;
                }
            }
            return getPlanetRepresentationPlusEmoji(planetID) + " " + Emojis.getResourceEmoji(planet.getResources()) + Emojis.getInfluenceEmoji(planet.getInfluence()) + techEmoji;
        }
    }

    public static String getPlanetRepresentationPlusEmojiPlusInfluence(String planetID, Game activeGame) {
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = (Planet) unitHolder;
            return getPlanetRepresentationPlusEmoji(planetID) + " " + Emojis.getInfluenceEmoji(planet.getInfluence());
        }
    }

    public static String getPlanetRepresentationPlusEmojiPlusResources(String planetID, Game activeGame) {
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = (Planet) unitHolder;
            String techType = "";
            String techEmoji = "";
            if (Mapper.getPlanet(planetID).getTechSpecialties() != null && Mapper.getPlanet(planetID).getTechSpecialties().size() > 0) {
                techType = Mapper.getPlanet(planetID).getTechSpecialties().get(0).toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(activeGame, planetID);
            }
            if (!"".equalsIgnoreCase(techType)) {
                switch (techType) {
                    case "propulsion" -> techEmoji = Emojis.PropulsionTech;
                    case "warfare" -> techEmoji =Emojis.WarfareTech;
                    case "cybernetic" -> techEmoji = Emojis.CyberneticTech;
                    case "biotic" -> techEmoji = Emojis.BioticTech;
                }
            }
            return getPlanetRepresentationPlusEmoji(planetID) + " " + Emojis.getResourceEmoji(planet.getResources()) + techEmoji;
        }
    }

    public static List<Button> getPlanetRefreshButtons(GenericInteractionCreateEvent event, Player player, Game activeGame) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : planets) {
            Button button = Button.success("refresh_" + planet, getPlanetRepresentation(planet, activeGame));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static String getPlayerDependingOnFog(Game activeGame, Player player) {
        String ident;

        if (activeGame.isFoWMode()) {
            ident = player.getColor();
        } else {
            ident = player.getFactionEmoji();
        }
        return ident;
    }

    public static List<Button> getRemainingSCButtons(GenericInteractionCreateEvent event, Game activeGame, Player playerPicker) {
        List<Button> scButtons = new ArrayList<>();

        for (Integer sc : activeGame.getSCList()) {
            if (sc <= 0) continue; // some older games have a 0 in the list of SCs
            boolean held = false;
            for (Player player : activeGame.getPlayers().values()) {
                if (player == null || player.getFaction() == null) {
                    continue;
                }
                if (player.getSCs() != null && player.getSCs().contains(sc) && !activeGame.isFoWMode()) {
                    held = true;
                    break;
                }
            }
            if (held) continue;
            Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
            Button button;
            String label = " ";
            if (activeGame.getScTradeGoods().get(sc) > 0 && !activeGame.isFoWMode()) {
                label = "[has " + activeGame.getScTradeGoods().get(sc) + " tg]";
            }
            if(sc == ButtonHelper.getKyroHeroSC(activeGame)){
                label = label + " Kyro Hero Cursed";
            }
            if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back") && !activeGame.isHomeBrewSCMode()) {

                button = Button.secondary("FFCC_" + playerPicker.getFaction() + "_scPick_" + sc, label).withEmoji(scEmoji);
            } else {
                button = Button.secondary("FFCC_" + playerPicker.getFaction() + "_scPick_" + sc, "" + sc + label);
            }
            scButtons.add(button);
        }
        return scButtons;
    }
    public static List<Button> getPlanetExhaustButtons(Player player, Game activeGame) {
        return getPlanetExhaustButtons(player, activeGame, "both");
    }

    public static List<Button> getPlanetExhaustButtons(Player player, Game activeGame, String whatIsItFor) {
        player.resetSpentThings();
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        for (String planet : planets) {
            
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                Button button = Button.danger("spend_" + planet, getPlanetRepresentation(planet, activeGame));
                planetButtons.add(button);
                continue;
            }
            String techType = "none";
            if (Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0) {
                techType = Mapper.getPlanet(planet).getTechSpecialties().get(0).toString().toLowerCase();
            } else {
                techType = ButtonHelper.getTechSkipAttachments(activeGame, planet);
            }
            if ("none".equalsIgnoreCase(techType)) {
                Button button = Button.danger("spend_" + planet +"_"+whatIsItFor, getPlanetRepresentation(planet, activeGame));
                planetButtons.add(button);
            } else {
                Button techB = Button.danger("spend_" + planet+ "_"+whatIsItFor, getPlanetRepresentation(planet, activeGame));
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

    public static List<Button> getPlanetPlaceUnitButtons(Player player, Game activeGame, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        player.resetProducedUnits();
        for (String planet : planets) {
            Button button = Button.danger("FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + planet, getPlanetRepresentation(planet, activeGame));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getTileWithShipsPlaceUnitButtons(Player player, Game activeGame, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, activeGame);
        for (Tile tile : tiles) {
            Button button = Button.danger("FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getTileWithShipsNTokenPlaceUnitButtons(Player player, Game activeGame, String unit, String prefix, @Nullable ButtonInteractionEvent event) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, activeGame);
        for (Tile tile : tiles) {
            if (AddCC.hasCC(event, player.getColor(), tile)) {
                Button button = Button.danger("FFCC_" + player.getFaction() + "_" + prefix + "_" + unit + "_" + tile.getPosition(), tile.getRepresentationForButtons(activeGame, player));
                planetButtons.add(button);
            }
        }
        return planetButtons;
    }

    public static String buildSpentThingsMessageForVoting(Player player, Game activeGame, boolean justVoteTotal){
        List<String> spentThings = player.getSpentThingsThisWindow();
        String msg = ButtonHelper.getIdent(player)+" used the following: \n";
        int votes = 0;
        int tg = player.getSpentTgsThisWindow();
        for(String thing : spentThings){
            int count = 0;
            if(!thing.contains("_")){
                BotLogger.log("Caught the following thing in the voting "+thing+" in game "+activeGame.getName());
                continue;
            }
            String secondHalf = thing.split("_")[1];
            String flavor = thing.split("_")[0];
            if(flavor.contains("planet")){
                count = AgendaHelper.getSpecificPlanetsVoteWorth(player, activeGame, secondHalf);
            }else{
                count = Integer.parseInt(thing.split("_")[1]);
            }
            if(flavor.contains("tg")){
                votes = votes + count*2;
            }else{
                 votes = votes + count;
            }
            msg = msg + "> ";
            switch (flavor) {
                case "tg" ->{
                    msg = msg +  "Spent "+player.getSpentTgsThisWindow()+" tgs for "+tg*2+" votes "+"\n";
                }
                case "infantry" ->{
                    msg = msg +  "Spent "+player.getSpentInfantryThisWindow()+" infantry for "+player.getSpentInfantryThisWindow()+" votes "+"\n";
                }
                case "planet" ->{
                    msg = msg + getPlanetRepresentation(secondHalf, activeGame) + " for "+count+" votes "+"\n";
                }
                case "absolShard" ->{
                    msg = msg +  "Used Absol Shard of the Throne for "+count+" votes "+"\n";
                }
                case "dsghotg" ->{
                    msg = msg +  "Exhausted some silly Ghoti Tech for "+count+" votes "+"\n";
                }
                case "absolsyncretone" ->{
                    msg = msg +  "Used Absol Syncretone for "+count+" votes "+"\n";
                }
                case "augerscommander" ->{
                    msg = msg +  "Used Augurs Commander for "+count+" votes "+"\n";
                }
                case "zeal" ->{
                    msg = msg +  "Used Zeal Ability for "+count+" votes "+"\n";
                }
                case "predictive" ->{
                    msg = msg +  "Used Predictive Intelligence for "+count+" votes "+"\n";
                }
                case "specialVotes" ->{
                    msg = msg +  "Used Special Votes for "+count+" votes "+"\n";
                }
                case "representative" ->{
                    msg = msg +  "Got 1 vote for representative government "+"\n";
                }
                case "distinguished" ->{
                    msg = msg +  "Used the AC Distinguished Councillor for 5 votes "+"\n";
                }
                case "absolRexControlRepresentative" ->{
                    msg = msg +  "Got 1 vote for controlling rex while representative government is in play"+"\n";
                }
                case "bloodPact" ->{
                    msg = msg +  "Got 4 votes from voting the same way as another Blood Pact member"+"\n";
                }
                    
            }
        }
        msg = msg + "For a total of **"+votes+"** votes on the outcome "+StringUtils.capitalize(activeGame.getLatestOutcomeVotedFor());
        if(justVoteTotal){
            return ""+votes;
        }

        return msg;
    }


    public static String buildSpentThingsMessage(Player player, Game activeGame, String resOrInfOrBoth){
        List<String> spentThings = player.getSpentThingsThisWindow();
        String msg = ButtonHelper.getIdent(player)+" exhausted the following: \n";
        int res = 0;
        int inf = 0;
        int tg = player.getSpentTgsThisWindow();
        boolean xxcha = player.hasLeaderUnlocked("xxchahero");
        int bestRes = 0;
        int keleresAgent = 0;
        for(String thing : spentThings){
            if(!thing.contains("tg_") && !thing.contains("sarween") && !thing.contains("ghoti") && !thing.contains("custodia") && !thing.contains("aida") && !thing.contains("commander") && !thing.contains("Agent")){
                UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(thing));
                msg = msg + "> ";
                if (unitHolder == null) {
                    if(thing.contains("reduced comms")){
                        String comms = StringUtils.substringAfter(thing,"by ");
                        comms = StringUtils.substringBefore(comms, " (");
                        keleresAgent = Integer.parseInt(comms);
                    }
                    msg = msg + thing + "\n";
                } else {
                    Planet planet = (Planet) unitHolder;
                    if(!ButtonHelper.isTileHomeSystem(activeGame.getTileFromPlanet(planet.getName()))){
                        if(planet.getResources() > bestRes){
                            bestRes = planet.getResources();
                        }
                    }
                    if("res".equalsIgnoreCase(resOrInfOrBoth)){
                        if(xxcha){
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, activeGame) +"\n";
                            res = res+planet.getSumResourcesInfluence();
                        }else{
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResources(thing, activeGame) +"\n";
                            res = res+ planet.getResources();
                        }
                    }else if("inf".equalsIgnoreCase(resOrInfOrBoth)){
                        if(xxcha){
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, activeGame) +"\n";
                            inf = inf +planet.getSumResourcesInfluence();
                        }else{
                            msg = msg + getPlanetRepresentationPlusEmojiPlusInfluence(thing, activeGame) +"\n";
                            inf = inf + planet.getInfluence();
                        }
                    }else if("freelancers".equalsIgnoreCase(resOrInfOrBoth)){
                        if(xxcha){
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, activeGame) +"\n";
                            res = res +planet.getSumResourcesInfluence();
                        }else{
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, activeGame) +"\n";
                            res = res + Math.max(planet.getInfluence(),planet.getResources());
                        }
                    }else {
                        if(xxcha){
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, activeGame) +"\n";
                            inf = inf + planet.getSumResourcesInfluence();
                            res = res+planet.getSumResourcesInfluence();
                        }else{
                            msg = msg + getPlanetRepresentationPlusEmojiPlusResourceInfluence(thing, activeGame) +"\n";
                            inf = inf +planet.getInfluence();
                            res = res+planet.getResources();
                        }
                    }
                }
            }else{
                if(thing.contains("sarween")){
                    msg = msg + "> Used Sarween Tools "+Emojis.CyberneticTech +"\n";
                    res = res+1;
                }
                if(thing.contains("warmachine")){
                    msg = msg + "> Used War Machine "+Emojis.ActionCard +"\n";
                    res = res+1;
                }
                if(thing.contains("aida")){
                    msg = msg + "> Exhausted AIDEV ";
                    if(thing.contains("_")){
                        res = res + ButtonHelper.getNumberOfUnitUpgrades(player);
                        msg = msg + " for " + ButtonHelper.getNumberOfUnitUpgrades(player) + " resources ";
                    }else{
                       msg =  msg + " for a tech skip on a unit upgrade ";
                    }
                    msg = msg+Emojis.WarfareTech +"\n";
                }
                if(thing.contains("commander") || thing.contains("Gledge Agent")){
                     msg = msg + "> "+thing + "\n";
                }else if(thing.contains("Winnu Agent")){
                     msg = msg +"> "+ thing + "\n";
                    res = res +2;
                }else if(thing.contains("Zealots Agent")){
                    msg = msg +"> "+ thing + "(Best Resources found were "+bestRes+")\n";
                    inf = inf +bestRes;
                }else if(thing.contains("Agent")){
                    msg = msg +"> "+ thing + "\n";
                }else if(thing.contains("custodia")){
                    msg = msg +"> "+ "Custiodia Vigilia (2/3)" + "\n";
                    res = res + 2;
                    inf = inf + 3;
                } else if(thing.contains("ghoti")){
                    msg = msg +"> "+ "Ghoti (3/3)" + "\n";
                    res = res + 3;
                    inf = inf + 3;
                }
            }
        }
        res = res + tg+keleresAgent;
        inf = inf + tg+keleresAgent;
        if(tg > 0){
            msg = msg + "> Spent "+tg+" tgs "+Emojis.getTGorNomadCoinEmoji(activeGame) +" ("+(player.getTg()+tg)+"->"+player.getTg()+") \n";
            if(player.hasTech("mc")){
                res = res + tg+keleresAgent;
                inf = inf + tg+keleresAgent;
                msg = msg + "> Counted the trade goods twice due to Mirror Computing \n";
            }
        }

        if("res".equalsIgnoreCase(resOrInfOrBoth)){
            msg = msg + "For a total spend of **"+res+" Resources**";
        }else if("inf".equalsIgnoreCase(resOrInfOrBoth)){
            msg = msg + "For a total spend of **"+inf+" Influence**";
        }else if("freelancers".equalsIgnoreCase(resOrInfOrBoth)){
            msg = msg + "For a total spend of **"+res+" Resources**";
        }else {
            msg = msg + "For a total spend of **"+res+" Resources** or **"+inf+" Influence**";
        }
        return msg;
    }

    public static String buildProducedUnitsMessage(Player player, Game activeGame){
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        String msg = "";
        List<String> uniquePlaces = new ArrayList<>();
        for(String unit : producedUnits.keySet()){
            String tilePos = unit.split("_")[1];
            String planetOrSpace = unit.split("_")[2];
            if(!uniquePlaces.contains(tilePos+"_"+planetOrSpace)){
                uniquePlaces.add(tilePos+"_"+planetOrSpace);
            }
        }
        for(String uniquePlace : uniquePlaces){
            String tilePos2 = uniquePlace.split("_")[0];
            String planetOrSpace2 = uniquePlace.split("_")[1];
            Tile tile = activeGame.getTileByPosition(tilePos2);
            String localPlace = "__**In "+tile.getRepresentationForButtons(activeGame, player) +" ";
            if("space".equalsIgnoreCase(planetOrSpace2)){
                localPlace = localPlace + " in the space area:**__ \n";
            }else{
                localPlace = localPlace + " on the planet "+ getPlanetRepresentation(planetOrSpace2, activeGame)+":**__ \n";
            }
            for(String unit : producedUnits.keySet()){
                String tilePos = unit.split("_")[1];
                String planetOrSpace = unit.split("_")[2];
                String un = unit.split("_")[0];
                UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(un), player.getColor());
                UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
                if(uniquePlace.contains(tilePos+"_"+planetOrSpace)){
                    localPlace = localPlace + ButtonHelper.getIdent(player) + " produced "+producedUnits.get(unit)+ " "+removedUnit.getUnitEmoji() + "\n";
                }
            }
            msg = msg + localPlace;
        }
        msg = msg +"For the total cost of: **"+calculateCostOfProducedUnits(player, activeGame, true) +" Resources**";
        if(calculateCostOfProducedUnits(player, activeGame, false) > 2){
            msg = msg +" (total units produced: "+calculateCostOfProducedUnits(player, activeGame, false)+").";
        }
        return msg;
    }
    public static void resetProducedUnits(Player player, Game activeGame, GenericInteractionCreateEvent event){
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        
        for(String unit : producedUnits.keySet()){
            String tilePos = unit.split("_")[1];
            String planetOrSpace = unit.split("_")[2];
            if("space".equalsIgnoreCase(planetOrSpace)){
                planetOrSpace = "";
            }else{
                planetOrSpace = " "+planetOrSpace;
            }
            Tile tile = activeGame.getTileByPosition(tilePos);
            String un = unit.split("_")[0];
            //UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(un), player.getColor());
            new ti4.commands.units.RemoveUnits().unitParsing(event, player.getColor(), tile, producedUnits.get(unit)+" "+AliasHandler.resolveUnit(un) +planetOrSpace, activeGame);
        }
        
        
        player.resetProducedUnits();
    }

    public static int getProductionValue(Player player, Game activeGame, Tile tile, boolean warfare){
        int productionValueTotal = 0;
        if(!warfare){
            for(UnitHolder uH : tile.getUnitHolders().values()){
                for(UnitKey unit : uH.getUnits().keySet()){
                    if(unit.getColor().equalsIgnoreCase(player.getColor())){
                        if(unit.getUnitType() == UnitType.TyrantsLament && player.getUnitsByAsyncID(unit.asyncID()).isEmpty()){
                            player.addOwnedUnitByID("tyrantslament");
                        }
                        if(unit.getUnitType() == UnitType.PlenaryOrbital && player.getUnitsByAsyncID(unit.asyncID()).isEmpty()){
                            player.addOwnedUnitByID("plenaryorbital");
                        }
                        UnitModel unitModel = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                        int productionValue = unitModel.getProductionValue();
                        if("fs".equals(unitModel.getAsyncId()) && player.ownsUnit("ghoti_flagship")){
                            productionValueTotal = productionValueTotal + player.getFleetCC();
                        }
                        if("sd".equals(unitModel.getAsyncId()) && (productionValue == 2 || productionValue == 4 || player.ownsUnit("mykomentori_spacedock2") || player.ownsUnit("miltymod_spacedock2"))){
                            if(uH instanceof Planet planet){
                                productionValue = planet.getResources()+ productionValue;
                            }
                        }
                        if(productionValue > 0 && player.hasRelic("boon_of_the_cerulean_god")){
                            productionValue++;
                        }
                        productionValueTotal = productionValueTotal + productionValue * uH.getUnits().get(unit);
                    }
                }
                if("mr".equalsIgnoreCase(uH.getName()) && player.hasTech("iihq") && player.getPlanets().contains("mr")){
                    productionValueTotal = productionValueTotal + 3;
                }
                if(player.hasTech("ah") && (uH.getUnitCount(UnitType.Pds, player.getColor()) > 0 || uH.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)){
                    productionValueTotal = productionValueTotal +1;
                }else{
                    if(player.hasTech("absol_ie")&& player.getPlanets().contains(uH.getName())){
                        productionValueTotal = productionValueTotal +1;
                    }
                }
                if(player.getPlanets().contains(uH.getName()) && player.getLeader("nokarhero").map(Leader::isActive).orElse(false)){
                    productionValueTotal = productionValueTotal +3;
                }
                for(String token : uH.getTokenList()){
                    if(token.contains("orbital_foundries")){
                        productionValueTotal = productionValueTotal +2;
                    }
                }
            }
            if(tile.isSupernova() && player.hasTech("mr")){
                productionValueTotal = productionValueTotal +5;
            }
        }else{
            int highestProd = 0;
            for(UnitHolder uH : tile.getUnitHolders().values()){
                for(UnitKey unit : uH.getUnits().keySet()){
                    if(unit.getColor().equalsIgnoreCase(player.getColor())){
                        UnitModel unitModel = player.getUnitsByAsyncID(unit.asyncID()).get(0);
                        if(!"sd".equals(unitModel.getAsyncId())){
                            continue;
                        }
                        int productionValue = unitModel.getProductionValue();
                        if("sd".equals(unitModel.getAsyncId()) && (productionValue == 2 || productionValue == 4 || player.ownsUnit("mykomentori_spacedock2") || player.ownsUnit("miltymod_spacedock2"))){
                            if(uH instanceof Planet planet){
                                productionValue = planet.getResources()+ productionValue;
                            }
                        }
                        if(productionValue > 0 && player.hasRelic("boon_of_the_cerulean_god")){
                            productionValue++;
                        }
                        if( productionValue > highestProd){
                            highestProd = productionValue;
                        }
                    }
                }
            }
            productionValueTotal = highestProd;
        }
        if(productionValueTotal > 0 && activeGame.playerHasLeaderUnlockedOrAlliance(player, "gledgecommander")){
            productionValueTotal = productionValueTotal+ ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame, player, "sd");
        }
        if(productionValueTotal > 0 && player.hasAbility("policy_the_environment_plunder")){
            productionValueTotal = productionValueTotal-2;
        }
        return productionValueTotal;
    }

    public static int calculateCostOfProducedUnits(Player player, Game activeGame, boolean wantCost){
        Map<String, Integer> producedUnits = player.getCurrentProducedUnits();
        int cost = 0;
        int numInf = 0;
        int numFF = 0;
        int totalUnits = 0;
        boolean regulated = activeGame.getLaws().containsKey("conscription") || activeGame.getLaws().containsKey("absol_conscription");
        for(String unit : producedUnits.keySet()){
            String unit2 = unit.split("_")[0];
            if(unit.contains("gf")){
                numInf = numInf + producedUnits.get(unit);
            }else if(unit.contains("ff")){
                numFF = numFF + producedUnits.get(unit);
            }else {
                UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit2), player.getColor());
                UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).get(0);
                if(removedUnit.getBaseType().equalsIgnoreCase("flagship") && activeGame.playerHasLeaderUnlockedOrAlliance(player, "nomadcommander")){
                    cost = cost; //nomad alliance
                }else{
                    cost = cost + (int)removedUnit.getCost() * producedUnits.get(unit);
                }
                totalUnits = totalUnits + producedUnits.get(unit);
            }
        }
        if(regulated){
            cost = cost + numInf + numFF;
        }else{
            if(player.ownsUnit("cymiae_infantry") || player.ownsUnit("cymiae_infantry2")){
                cost = cost + numInf;
            }else{
                cost = cost + ((numInf+1)/2);
            }
            cost = cost + ((numFF+1)/2);
        }
        totalUnits = totalUnits + numInf+numFF;
        if(wantCost){
            return cost;
        }else{
            return totalUnits;
        }
        
    }



    public static List<Button> getPlaceUnitButtons(GenericInteractionCreateEvent event, Player player, Game activeGame, Tile tile, String warfareNOtherstuff, String placePrefix) {
        List<Button> unitButtons = new ArrayList<>();
        player.resetProducedUnits();
        boolean regulated = activeGame.getLaws().containsKey("conscription") || activeGame.getLaws().containsKey("absol_conscription");
        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String tp = tile.getPosition();
        if (!"muaatagent".equalsIgnoreCase(warfareNOtherstuff)) {
            if (player.hasWarsunTech()) {
                Button wsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_warsun_" + tp, "Produce Warsun");
                wsButton = wsButton.withEmoji(Emoji.fromFormatted(Emojis.warsun));
                unitButtons.add(wsButton);
            }
            if (player.ownsUnit("ghemina_flagship_lady")) {
                Button wsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_lady_" + tp, "Produce The Lady");
                wsButton = wsButton.withEmoji(Emoji.fromFormatted(Emojis.flagship));
                unitButtons.add(wsButton);
            }
            Button fsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_flagship_" + tp, "Produce Flagship");
            fsButton = fsButton.withEmoji(Emoji.fromFormatted(Emojis.flagship));
            unitButtons.add(fsButton);
        }
        Button dnButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_dreadnought_" + tp, "Produce Dreadnought");
        dnButton = dnButton.withEmoji(Emoji.fromFormatted(Emojis.dreadnought));
        unitButtons.add(dnButton);
        Button cvButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_carrier_" + tp, "Produce Carrier");
        cvButton = cvButton.withEmoji(Emoji.fromFormatted(Emojis.carrier));
        unitButtons.add(cvButton);
        Button caButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_cruiser_" + tp, "Produce Cruiser");
        caButton = caButton.withEmoji(Emoji.fromFormatted(Emojis.cruiser));
        unitButtons.add(caButton);
        Button ddButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_destroyer_" + tp, "Produce Destroyer");
        ddButton = ddButton.withEmoji(Emoji.fromFormatted(Emojis.destroyer));
        unitButtons.add(ddButton);
        Button ff1Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_fighter_" + tp, "Produce 1 Fighter");
        ff1Button = ff1Button.withEmoji(Emoji.fromFormatted(Emojis.fighter));
        unitButtons.add(ff1Button);
        if (!"freelancers".equalsIgnoreCase(warfareNOtherstuff) && unitHolders.size() < 4 && !regulated && !"sling".equalsIgnoreCase(warfareNOtherstuff)
            && !"chaosM".equalsIgnoreCase(warfareNOtherstuff) ) {
            Button ff2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2ff_" + tp, "Produce 2 Fighters");
            ff2Button = ff2Button.withEmoji(Emoji.fromFormatted(Emojis.fighter));
            unitButtons.add(ff2Button);
        }

        if (!"freelancers".equalsIgnoreCase(warfareNOtherstuff) && !"sling".equalsIgnoreCase(warfareNOtherstuff) && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {

            if (player.hasUnexhaustedLeader("argentagent")) {
                Button argentButton = Button.success("FFCC_" + player.getFaction() + "_" + "exhaustAgent_argentagent_" + tile.getPosition(), "Use Argent Agent");
                argentButton = argentButton.withEmoji(Emoji.fromFormatted(Emojis.Argent));
                unitButtons.add(argentButton);
            }
            if (player.hasTechReady("sar")) {
                Button argentButton = Button.success("sarMechStep1_" + tile.getPosition()+"_"+warfareNOtherstuff, "Use Self Assembly Routines");
                argentButton = argentButton.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                unitButtons.add(argentButton);
            }
        }
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet planet && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                if ("warfare".equalsIgnoreCase(warfareNOtherstuff) && !"mr".equalsIgnoreCase(unitHolder.getName())) {
                    if (unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) < 1 && unitHolder.getUnitCount(UnitType.CabalSpacedock, player.getColor()) < 1
                        && !player.hasUnit("saar_spacedock") && !player.hasUnit("saar_spacedock2")&& !player.hasUnit("ghoti_flagship")) {
                        continue;
                    }
                }

                String pp = planet.getName();
                if ("genericBuild".equalsIgnoreCase(warfareNOtherstuff)) {
                    Button sdButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_sd_" + pp, "Place 1 Space Dock on " + getPlanetRepresentation(pp, activeGame));
                    sdButton = sdButton.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                    unitButtons.add(sdButton);
                    Button pdsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_pds_" + pp, "Place 1 PDS on " + getPlanetRepresentation(pp, activeGame));
                    pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(Emojis.pds));
                    unitButtons.add(pdsButton);
                }
                Button inf1Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_" + pp, "Produce 1 Infantry on " + getPlanetRepresentation(pp, activeGame));
                inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                unitButtons.add(inf1Button);
                if (!"genericBuild".equalsIgnoreCase(warfareNOtherstuff) && !"freelancers".equalsIgnoreCase(warfareNOtherstuff) && !regulated && unitHolders.size() < 4 && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {
                    Button inf2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_" + pp, "Produce 2 Infantry on " + getPlanetRepresentation(pp, activeGame));
                    inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    unitButtons.add(inf2Button);
                }
                Button mfButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_mech_" + pp, "Produce Mech on " + getPlanetRepresentation(pp, activeGame));
                mfButton = mfButton.withEmoji(Emoji.fromFormatted(Emojis.mech));
                unitButtons.add(mfButton);

            } else if (ButtonHelper.canIBuildGFInSpace(activeGame, player, tile, warfareNOtherstuff) && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                Button inf1Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_infantry_space" + tile.getPosition(), "Produce 1 Infantry in space");
                inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                unitButtons.add(inf1Button);
                if (!"genericBuild".equalsIgnoreCase(warfareNOtherstuff) && !"freelancers".equalsIgnoreCase(warfareNOtherstuff) && unitHolders.size() < 4 && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {
                    Button inf2Button = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_2gf_space" + tile.getPosition(), "Produce 2 Infantry in space");
                    inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    unitButtons.add(inf2Button);
                }
                Button mfButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_mech_space" + tile.getPosition(), "Produce Mech in space");
                mfButton = mfButton.withEmoji(Emoji.fromFormatted(Emojis.mech));
                unitButtons.add(mfButton);
            }
        }
        if ("place".equalsIgnoreCase(placePrefix)) {
            Button DoneProducingUnits = Button.danger("deleteButtons_" + warfareNOtherstuff, "Done Producing Units");
            unitButtons.add(DoneProducingUnits);
            unitButtons.add(Button.secondary("resetProducedThings", "Reset Build"));
        }
        if (player.hasTech("yso")) {
            if ("sling".equalsIgnoreCase(warfareNOtherstuff)) {
                List<Button> unitButtons2 = new ArrayList<>();
                unitButtons2.add(Button.secondary("startYinSpinner", "Yin Spin 2 Duders").withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation(true, true) + " you can use this to Yin Spin", unitButtons2);
            } else {
                unitButtons.add(Button.secondary("startYinSpinner", "Yin Spin 2 Duders").withEmoji(Emoji.fromFormatted(Emojis.Yin)));
            }
        }

        return unitButtons;
    }

    public static List<Button> getPlanetSystemDiploButtons(GenericInteractionCreateEvent event, Player player, Game activeGame, boolean ac, Player mahact) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanetsAllianceMode());
        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
        if (mahact == null) {
            for (String planet : planets) {
                if (!getPlanetRepresentation(planet, activeGame).toLowerCase().contains("mecatol") || ac) {
                    Button button = Button.secondary(finsFactionCheckerPrefix + "diplo_" + planet + "_" + "diploP", getPlanetRepresentation(planet, activeGame) + " System");
                    planetButtons.add(button);
                }
            }
        } else {
            for (Tile tile : activeGame.getTileMap().values()) {
                if (FoWHelper.playerHasUnitsInSystem(player, tile) && !ButtonHelper.isTileHomeSystem(tile)) {
                    Button button = Button.secondary(finsFactionCheckerPrefix + "diplo_" + tile.getPosition() + "_" + "mahact" + mahact.getColor(), tile.getRepresentation() + " System");
                    planetButtons.add(button);
                }
            }

        }

        return planetButtons;
    }

    public static int getPlanetResources(String planetID, Game activeGame) {
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return 0;
        } else {
            Planet planet = (Planet) unitHolder;
            return planet.getResources();
        }
    }

    public static int getPlanetInfluence(String planetID, Game activeGame) {
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return 0;
        } else {
            Planet planet = (Planet) unitHolder;
            return planet.getInfluence();
        }
    }

    @Deprecated
    public static String getLeaderRepresentation(Leader leader, boolean includeTitle, boolean includeAbility, boolean includeUnlockCondition) {
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
        if (includeTitle) representation.append(": ").append(leaderTitle); //add title
        if (includeAbility && Constants.HERO.equals(leader.getType())) representation.append(" - ").append("__**").append(heroAbilityName).append("**__"); //add hero ability name
        if (includeAbility) representation.append(" - *").append(leaderAbilityWindow).append("* ").append(leaderAbilityText); //add ability
        if (includeUnlockCondition) representation.append(" *Unlock:* ").append(leaderUnlockCondition);

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

    public static void isCCCountCorrect(GenericInteractionCreateEvent event, Game activeGame, String color) {
        int ccCount = getCCCount(activeGame, color);
        informUserCCOverLimit(event, activeGame, color, ccCount);
    }

    public static int getCCCount(Game activeGame, String color) {
        int ccCount = 0;
        if (color == null) {
            return 0;
        }
        Map<String, Tile> tileMap = activeGame.getTileMap();
        for (Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
            Tile tile = tileEntry.getValue();
            boolean hasCC = AddCC.hasCC(null, color, tile);
            if (hasCC) {
                ccCount++;
            }
        }
        String factionColor = AliasHandler.resolveColor(color.toLowerCase());
        factionColor = AliasHandler.resolveFaction(factionColor);
        for (Player player_ : activeGame.getPlayers().values()) {
            if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                ccCount += player_.getStrategicCC();
                ccCount += player_.getTacticalCC();
                ccCount += player_.getFleetCC();
                break;
            } else if ("mahact".equals(player_.getFaction())) {
                for (String color_ : player_.getMahactCC()) {
                    if (factionColor.equals(color_)) {
                        ccCount++;
                    }
                }
            }
        }
        return ccCount;
    }

    private static void informUserCCOverLimit(GenericInteractionCreateEvent event, Game activeGame, String color, int ccCount) {
        boolean ccCountIsOver = ccCount > 16;
        if (ccCountIsOver && activeGame.getCCNPlasticLimit()) {
            Player player = null;
            String factionColor = AliasHandler.resolveColor(color.toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeGame.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) ||
                    Objects.equals(factionColor, player_.getColor())) {
                    player = player_;
                }
            }

            String msg = activeGame.getPing() + " ";
            if (!activeGame.isFoWMode()) {
                if (player != null) {
                    msg += player.getFactionEmoji() + " " + player.getFaction() + " ";
                    msg += player.getPing() + " ";
                }
            }

            msg += "(" + color + ") is over the CC limit of 16. CC used: " + ccCount;
            MessageHelper.replyToMessage(event, msg);
        }
    }

    /**
     * @param activeGame : ti4.map.Map object
     * @return String : TTS/TTPG Map String
     */
    public static String getMapString(Game activeGame) {
        List<String> tilePositions = new ArrayList<>();
        tilePositions.add("000");

        int ringCountMax = activeGame.getRingCount();
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

        List<String> sortedTilePositions = tilePositions.stream().sorted(Comparator.comparingInt(Integer::parseInt)).toList();
        Map<String, Tile> tileMap = new HashMap<>(activeGame.getTileMap());
        StringBuilder sb = new StringBuilder();
        for (String position : sortedTilePositions) {
            boolean missingTile = true;
            for (Tile tile : tileMap.values()) {
                if (tile.getPosition().equals(position)) {
                    String tileID = AliasHandler.resolveStandardTile(tile.getTileID()).toUpperCase();
                    if ("000".equalsIgnoreCase(position) && "18".equalsIgnoreCase(tileID)) { //Mecatol Rex in Centre Position
                        sb.append("{18}");
                    } else if ("000".equalsIgnoreCase(position) && !"18".equalsIgnoreCase(tileID)) { //Something else is in the Centre Position
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

    public static Integer getPlayerResourcesAvailable(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        int resourcesCount = 0;
        if (player.hasLeaderUnlocked("xxchahero")) {
            int resourcesCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
            resourcesCount += resourcesCountFromPlanetsRes;
        }

        int resourcesCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();

        resourcesCount += resourcesCountFromPlanets;
        return resourcesCount;
    }

    public static Integer getPlayerResourcesTotal(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        int resourcesCount = 0;
        if (player.hasLeaderUnlocked("xxchahero")) {
            int resourcesCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();
            resourcesCount += resourcesCountFromPlanetsRes;
        }
        int resourcesCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();

        resourcesCount += resourcesCountFromPlanets;
        return resourcesCount;
    }

    public static Integer getPlayerOptimalResourcesAvailable(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getOptimalResources).sum();
    }

    public static Integer getPlayerOptimalResourcesTotal(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getOptimalResources).sum();
    }

    public static Integer getPlayerInfluenceAvailable(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        int influenceCount = 0;
        if (player.hasLeaderUnlocked("xxchahero")) {
            int influenceCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();
            influenceCount += influenceCountFromPlanetsRes;
        }

        int influenceCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();

        influenceCount += influenceCountFromPlanets;
        return influenceCount;
    }

    public static Integer getPlayerInfluenceTotal(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        int influenceCount = 0;
        if (player.hasLeaderUnlocked("xxchahero")) {
            int influenceCountFromPlanetsRes = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getResources).sum();
            influenceCount += influenceCountFromPlanetsRes;
        }

        int influenceCountFromPlanets = planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getInfluence).sum();

        influenceCount += influenceCountFromPlanets;
        return influenceCount;
    }

    public static Integer getPlayerOptimalInfluenceAvailable(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getOptimalInfluence).sum();
    }

    public static Integer getPlayerOptimalInfluenceTotal(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
                .map(planet -> (Planet) planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getOptimalInfluence).sum();
    }

    public static Integer getPlayerFlexResourcesInfluenceAvailable(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getFlexResourcesOrInfluence).sum();
    }

    public static Integer getPlayerFlexResourcesInfluenceTotal(Player player, Game activeGame) {
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return null;
        }
        List<String> planets = new ArrayList<>(player.getPlanets());

        Map<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
        if (player.hasLeaderUnlocked("xxchahero")) {
            return planets.stream().map(planetsInfo::get).filter(Objects::nonNull).map(planet -> (Planet) planet).mapToInt(Planet::getSumResourcesInfluence).sum();
        }

        return planets.stream().map(planetsInfo::get).filter(Objects::nonNull)
            .map(planet -> (Planet) planet).mapToInt(Planet::getFlexResourcesOrInfluence).sum();
    }

    public static String getPlayerResourceInfluenceRepresentation(Player player, Game activeGame) {
        return player.getRepresentation() + ":\n" +
            "Resources: " + getPlayerResourcesAvailable(player, activeGame) + "/" + getPlayerResourcesTotal(player, activeGame) + "  Optimal: " + getPlayerOptimalResourcesAvailable(player, activeGame)
            + "/" + getPlayerOptimalResourcesTotal(player, activeGame) + "\n" +
            "Influence: " + getPlayerInfluenceAvailable(player, activeGame) + "/" + getPlayerInfluenceTotal(player, activeGame) + "  Optimal: " + getPlayerOptimalInfluenceAvailable(player, activeGame)
            + "/" + getPlayerOptimalInfluenceTotal(player, activeGame) + "\n";
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
        if (guild == null) return;
        long threadCount = guild.getThreadChannels().stream().filter(c -> !c.isArchived()).count();
        int closeCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.THREAD_AUTOCLOSE_COUNT.toString(), Integer.class, 20);
        int maxThreadCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.MAX_THREAD_COUNT.toString(), Integer.class, 975);

        if (threadCount >= maxThreadCount) {
            BotLogger.log("`Helper.checkThreadLimitAndArchive:` Server: **" + guild.getName() + "** thread count is too high ( " + threadCount + " ) - auto-archiving  " + closeCount
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

    public static void fixGameChannelPermissions(@NotNull Guild guild, @NotNull Game activeGame) {
        if (!activeGame.isFoWMode() && !activeGame.isCommunityMode()) {
            String gameName = activeGame.getName();
            List<Role> roles = guild.getRolesByName(gameName, true);
            Role role = null;
            if (!roles.isEmpty()) {
                if (roles.size() > 1) {
                    BotLogger.log("There are " + roles.size() + " roles that match the game name: `" + gameName + "` - please investigate, as this may cause issues.");
                    return;
                }
                role = roles.get(0);
            }

            if (role == null) { //make sure players have access to the game channels
                addMapPlayerPermissionsToGameChannels(guild, activeGame);
            } else { //make sure players have the role
                addGameRoleToMapPlayers(guild, activeGame, role);
            }
        }
    }

    public static void addMapPlayerPermissionsToGameChannels(Guild guild, Game activeGame) {
        TextChannel tableTalkChannel = activeGame.getTableTalkChannel();
        if (tableTalkChannel != null) {
            addPlayerPermissionsToGameChannel(guild, activeGame, tableTalkChannel);
        }
        TextChannel actionsChannel = activeGame.getMainGameChannel();
        if (actionsChannel != null) {
            addPlayerPermissionsToGameChannel(guild, activeGame, actionsChannel);
        }
        String gameName = activeGame.getName();
        List<GuildChannel> channels = guild.getChannels().stream().filter(c -> c.getName().startsWith(gameName)).toList();
        for (GuildChannel channel : channels) {
            addPlayerPermissionsToGameChannel(guild, activeGame, channel);
        }
    }

    public static void addBotHelperPermissionsToGameChannels(GenericInteractionCreateEvent event) {
        Guild guild = event.getGuild();
        //long role = 1093925613288562768L;
        long role = 1166011604488425482L;
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game activeGame : mapList.values()) {
            if (!activeGame.isHasEnded()) {
                TextChannel tableTalkChannel = activeGame.getTableTalkChannel();
                if (tableTalkChannel != null && activeGame.getGuild() == guild) {
                    addRolePermissionsToGameChannel(guild, tableTalkChannel, role);
                }
                TextChannel actionsChannel = activeGame.getMainGameChannel();
                if (actionsChannel != null && activeGame.getGuild() == guild) {
                    addRolePermissionsToGameChannel(guild, actionsChannel, role);
                }
                String gameName = activeGame.getName();
                List<GuildChannel> channels = guild.getChannels().stream().filter(c -> c.getName().startsWith(gameName)).toList();
                for (GuildChannel channel : channels) {
                    addRolePermissionsToGameChannel(guild, channel, role);
                }
            }
        }
    }

    private static void addPlayerPermissionsToGameChannel(Guild guild, Game activeGame, GuildChannel channel) {
        TextChannel textChannel = guild.getTextChannelById(channel.getId());
        if (textChannel != null) {
            TextChannelManager textChannelManager = textChannel.getManager();
            for (String playerID : activeGame.getPlayerIDs()) {
                Member member = guild.getMemberById(playerID);
                if (member == null) continue;
                long allow = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();
                textChannelManager.putMemberPermissionOverride(member.getIdLong(), allow, 0);
            }
            textChannelManager.queue();
            // textChannel.sendMessage("This channel's permissions have been updated.").queue();
        }
    }

    private static void addRolePermissionsToGameChannel(Guild guild, GuildChannel channel, long role) {
        TextChannel textChannel = guild.getTextChannelById(channel.getId());
        if (textChannel != null) {
            TextChannelManager textChannelManager = textChannel.getManager();
            long allow = Permission.MESSAGE_MANAGE.getRawValue() | Permission.VIEW_CHANNEL.getRawValue();
            textChannelManager.putRolePermissionOverride(role, allow, 0);
            textChannelManager.queue();
            // textChannel.sendMessage("This channel's permissions have been updated.").queue();
        }
    }

    private static void addGameRoleToMapPlayers(Guild guild, Game activeGame, Role role) {
        for (String playerID : activeGame.getPlayerIDs()) {
            if (activeGame.getRound() > 1 && activeGame.getPlayer(playerID).getFaction() == null) {
                continue;
            }
            Member member = guild.getMemberById(playerID);
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
            BotLogger.log(event, ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    public static List<Button> getTechButtons(List<TechnologyModel> techs, String techType, Player player) {
        return getTechButtons(techs, techType, player, "nope");
    }

    public static List<Button> getTechButtons(List<TechnologyModel> techs, String techType, Player player, String jolNarHeroTech) {
        List<Button> techButtons = new ArrayList<>();

        techs.sort(TechnologyModel.sortByTechRequirements);

        for (TechnologyModel tech : techs) {
            String techName = tech.getName();
            String techID = tech.getAlias();
            String buttonID;
            if (!"nope".equalsIgnoreCase(jolNarHeroTech)) {
                if ("nekro".equalsIgnoreCase(jolNarHeroTech)) {
                    buttonID = "FFCC_" + player.getFaction() + "_getTech_" + techID + "__noPay";
                } else {
                    buttonID = "FFCC_" + player.getFaction() + "_swapTechs_" + jolNarHeroTech + "_" + tech.getAlias();
                }
            } else {
                buttonID = "FFCC_" + player.getFaction() + "_getTech_" + techID;
            }
            Button techB;
            //String requirementsEmoji = tech.getRequirementsEmoji();
            switch (techType) {
                case "propulsion" -> {
                    techB = Button.primary(buttonID, techName);
                    switch (tech.getRequirements().orElse("")) {
                        case "" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.PropulsionDisabled));
                        case "B" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
                        case "BB" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Propulsion2));
                        case "BBB" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Propulsion3));
                    }
                }
                case "cybernetic" -> {
                    techB = Button.secondary(buttonID, techName);
                    switch (tech.getRequirements().orElse("")) {
                        case "" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.CyberneticDisabled));
                        case "Y" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                        case "YY" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Cybernetic2));
                        case "YYY" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Cybernetic3));
                    }
                }
                case "biotic" -> {
                    techB = Button.success(buttonID, techName);
                    switch (tech.getRequirements().orElse("")) {
                        case "" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.BioticDisabled));
                        case "G" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
                        case "GG" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Biotic2));
                        case "GGG" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Biotic3));
                    }
                }
                case "warfare" -> {
                    techB = Button.danger(buttonID, techName);
                    switch (tech.getRequirements().orElse("")) {
                        case "" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.WarfareDisabled));
                        case "R" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                        case "RR" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Warfare2));
                        case "RRR" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Warfare3));
                    }
                }
                case "unitupgrade" -> {
                    techB = Button.secondary(buttonID, techName);
                    String unitType = tech.getBaseUpgrade().isEmpty() ? tech.getAlias() : tech.getBaseUpgrade().get();
                    switch (unitType) {
                        case "inf2" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.infantry));
                        case "ff2" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.fighter));
                        case "pds2" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.pds));
                        case "sd2" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.spacedock));
                        case "dd2" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.destroyer));
                        case "cr2" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.cruiser));
                        case "cv2" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.carrier));
                        case "dn2" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.dreadnought));
                        case "ws" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.warsun));
                        case "fs" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.flagship));
                    }
                }
                default -> techB = Button.secondary(buttonID, techName);
            }
            techButtons.add(techB);
        }
        return techButtons;
    }

    public static List<TechnologyModel> getAllTechOfAType(Game activeGame, String techType, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        Mapper.getTechs().values().stream()
            .filter(tech -> activeGame.getTechnologyDeck().contains(tech.getAlias()))
            .filter(tech -> tech.getType().toString().equalsIgnoreCase(techType))
            .filter(tech -> !player.hasTech(tech.getAlias()))
            .filter(tech -> tech.getFaction().isEmpty() || tech.getFaction().get() == null || "".equalsIgnoreCase(tech.getFaction().get())
                || player.getNotResearchedFactionTechs().contains(tech.getAlias()))
            .forEach(techs::add);

        List<TechnologyModel> techs2 = new ArrayList<>();
        for (TechnologyModel tech : techs) {
            boolean addTech = true;
            if ("unitupgrade".equalsIgnoreCase(tech.getType().toString())) {
                for (String factionTech : player.getNotResearchedFactionTechs()) {
                    TechnologyModel fTech = Mapper.getTech(factionTech);
                    if (fTech != null && !fTech.getAlias().equalsIgnoreCase(tech.getAlias()) && "unitupgrade".equalsIgnoreCase(fTech.getType().toString())
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

    public static List<TechnologyModel> getAllNonFactionUnitUpgradeTech(Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel tech : Mapper.getTechs().values()) {
            String faction = tech.getFaction().orElse("");
            if ("unitupgrade".equalsIgnoreCase(tech.getType().toString())) {
                if (player.hasTech(tech.getAlias())) {
                    if (faction.isEmpty()) {
                        techs.add(tech);
                    }
                }
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

    public static void checkIfHeroUnlocked(GenericInteractionCreateEvent event, Game activeGame, Player player) {
        Leader playerLeader = player.getLeader(Constants.HERO).orElse(null);
        if (playerLeader != null && playerLeader.isLocked()) {
            int scoredSOCount = player.getSecretsScored().size();
            int scoredPOCount = 0;
            Map<String, List<String>> playerScoredPublics = activeGame.getScoredPublicObjectives();
            for (Entry<String, List<String>> scoredPublic : playerScoredPublics.entrySet()) {
                if (Mapper.getPublicObjectivesStage1().containsKey(scoredPublic.getKey()) || Mapper.getPublicObjectivesStage2().containsKey(scoredPublic.getKey())) {
                    if (scoredPublic.getValue().contains(player.getUserID())) {
                        scoredPOCount++;
                    }
                }

            }
            int scoredObjectiveCount = scoredPOCount + scoredSOCount;
            if (scoredObjectiveCount >= 3) {
                //UnlockLeader ul = new UnlockLeader();
                UnlockLeader.unlockLeader(event, "hero", activeGame, player);
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

    public static void setOrder(Game activeGame) {
        List<Integer> hsLocations = new ArrayList<>();
        LinkedHashMap<Integer, Player> unsortedPlayers = new LinkedHashMap<>();
        for (Player player : activeGame.getRealPlayers()) {
            Tile tile = activeGame.getTile(AliasHandler.resolveTile(player.getFaction()));
            if (tile == null) {
                tile = ButtonHelper.getTileOfPlanetWithNoTrait(player, activeGame);
            }
            boolean ghosty = player.getPlayerStatsAnchorPosition() != null && activeGame.getTileByPosition(player.getPlayerStatsAnchorPosition()) != null
                && "17".equals(activeGame.getTileByPosition(player.getPlayerStatsAnchorPosition()).getTileID());
            if ((player.getFaction().contains("ghost") && activeGame.getTile("17") != null) || ghosty) {
                tile = activeGame.getTile("17");
            }
            hsLocations.add(Integer.parseInt(tile.getPosition()));
            unsortedPlayers.put(Integer.parseInt(tile.getPosition()), player);
        }
        Collections.sort(hsLocations);
        List<Player> sortedPlayers = new ArrayList<>();
        for (Integer location : hsLocations) {
            sortedPlayers.add(unsortedPlayers.get(location));
        }
        Map<String, Player> newPlayerOrder = new LinkedHashMap<>();
        Map<String, Player> players = new LinkedHashMap<>(activeGame.getPlayers());
        Map<String, Player> playersBackup = new LinkedHashMap<>(activeGame.getPlayers());
        String msg = activeGame.getPing() + " set order in the following way: \n";
        try {
            for (Player player : sortedPlayers) {
                new SetOrder().setPlayerOrder(newPlayerOrder, players, player);
                msg = msg + player.getRepresentation(true, true) + " \n";
            }
            if (!players.isEmpty()) {
                newPlayerOrder.putAll(players);
            }
            activeGame.setPlayers(newPlayerOrder);
        } catch (Exception e) {
            activeGame.setPlayers(playersBackup);
        }
        msg += "Note: the first player is not necesarily speaker/first pick. This is the general speaker order.";
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msg);
    }

    public static void checkEndGame(Game activeGame, Player player) {
        if (player.getTotalVictoryPoints() >= activeGame.getVp()) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Button.success("gameEnd", "End Game"));
            buttons.add(Button.primary("rematch", "Rematch (make new game with same players/channels)"));
            buttons.add(Button.danger("deleteButtons", "Mistake, delete these"));
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(),
                activeGame.getPing() + " it seems like " + ButtonHelper.getIdentOrColor(player, activeGame)
                    + " has won the game. Press the end game button when you are done with the channels, or ignore this if it was a mistake/more complicated.",
                buttons);
        }
    }

    public static boolean mechCheck(String planetName, Game activeGame, Player player) {
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
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

    public static void removePoKComponents(Game activeGame, String codex) {
        boolean removeCodex = "y".equalsIgnoreCase(codex);

        //removing Action Cards
        Map<String, ActionCardModel> actionCards = Mapper.getActionCards();
        for (ActionCardModel ac : actionCards.values()) {
            if ("pok".equals(ac.getSource().name())) {
                activeGame.removeACFromGame(ac.getAlias());
            } else if ("codex1".equals(ac.getSource().name()) && removeCodex) {
                activeGame.removeACFromGame(ac.getAlias());
            }
        }

        //removing SOs
        Map<String, SecretObjectiveModel> soList = Mapper.getSecretObjectives();
        for (SecretObjectiveModel so : soList.values()) {
            if ("pok".equals(so.getSource().name())) {
                activeGame.removeSOFromGame(so.getAlias());
            }
        }

        //removing POs
        Map<String, PublicObjectiveModel> poList = Mapper.getPublicObjectives();
        for (PublicObjectiveModel po : poList.values()) {
            if ("pok".equals(po.getSource().name())) {
                if (po.getPoints() == 1) {
                    activeGame.removePublicObjective1(po.getAlias());
                }
                if (po.getPoints() == 2) {
                    activeGame.removePublicObjective2(po.getAlias());
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

    public static String getGuildInviteURL(Guild guild) {
        List<Invite> invites = guild.retrieveInvites().complete();
        String inviteUrl = null;
        if (invites != null && !invites.isEmpty()) {
            inviteUrl = invites.get(0).getUrl();
        }
        if (inviteUrl == null) {
            inviteUrl = guild.getDefaultChannel().createInvite().complete().getUrl();
        }
        return inviteUrl;
    }

    public static String getTimeRepresentationToSeconds(long totalMillis) {
        long totalSeconds = totalMillis / 1000; //total seconds (truncates)
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60; //total minutes (truncates)
        long minutes = totalMinutes % 60;
        long hours = totalMinutes / 60; //total hours (truncates)

        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }

    public static String getTimeRepresentationToMillis(long totalMillis) {
        long millis = totalMillis % 1000;
        long totalSeconds = totalMillis / 1000; //total seconds (truncates)
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60; //total minutes (truncates)
        long minutes = totalMinutes % 60;
        long hours = totalMinutes / 60; //total hours (truncates)

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
        long seconds = totalSeconds % 60;
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
        if (messageEmbed == null) return false;
        if (searchString == null) return true;
        searchString = searchString.toLowerCase();

        if (messageEmbed.getTitle() != null && messageEmbed.getTitle().toLowerCase().contains(searchString)) return true;
        if (messageEmbed.getDescription() != null && messageEmbed.getDescription().toLowerCase().contains(searchString)) return true;
        if (messageEmbed.getFooter() != null && messageEmbed.getFooter().getText() != null && messageEmbed.getFooter().getText().toLowerCase().contains(searchString)) return true;
        for (MessageEmbed.Field field : messageEmbed.getFields()) {
            if (field.getName() != null && field.getName().toLowerCase().contains(searchString)) return true;
            if (field.getValue() != null && field.getValue().toLowerCase().contains(searchString)) return true;
        }

        return false;
    }

    public static String getUnitListEmojis(String unitList) {
        String[] units = unitList.split(",");
        StringBuilder sb = new StringBuilder();
        for (String desc: units) {
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

            for (int i = 1; i <= count; i++) {
                sb.append(Emojis.getEmojiFromDiscord(Mapper.getUnitBaseTypeFromAsyncID(AliasHandler.resolveUnit(alias))));
            }
        }
        return sb.toString();
    }
}
