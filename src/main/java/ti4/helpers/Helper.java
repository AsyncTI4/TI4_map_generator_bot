package ti4.helpers;

import java.awt.Point;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import ti4.ResourceHelper;
import ti4.buttons.ButtonListener;
import ti4.commands.bothelper.ArchiveOldThreads;
import ti4.commands.bothelper.ListOldThreads;
import ti4.commands.game.SetOrder;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.commands.milty.StartMilty;
import ti4.commands.tokens.AddCC;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
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
import ti4.model.TechnologyModel.TechnologyType;

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
            if(player.getTeamMateIDs().contains(member.getUser().getId())){
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
        if (player.hasTech("tp") && activeGame.getActivePlayer() != null && activeGame.getActivePlayer().equalsIgnoreCase(player.getUserID())) {
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
        if (player.getActionCards().keySet().contains("sabo1") || player.getActionCards().keySet().contains("sabotage_ds") || player.getActionCards().keySet().contains("sabo2") ||
            player.getActionCards().keySet().contains("sabo3") || player.getActionCards().keySet().contains("sabo4")
            || (activeGame.getActionCardDeckSize() + activeGame.getDiscardActionCards().size()) > 180) {
            return false;
        }
        if (player.hasUnit("empyrean_mech") && ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Mech).size() > 0) {
            return false;
        }
        if (ButtonListener.checkForASpecificPlayerReact(messageID, player, activeGame)) {
            return false;
        }
        // int highNum = player.getAutoSaboPassMedian()*6*3/2;
        // int result = ThreadLocalRandom.current().nextInt(1,highNum+1);
        // if(result == highNum){
        //     return true;
        // }else{
        //     return false;
        // }
        return true;
    }

    public static void checkAllSaboWindows(Game activeGame) {
        List<String> messageIDs = new ArrayList<>();
        messageIDs.addAll(activeGame.getMessageIDsForSabo());
        for (Player player : activeGame.getRealPlayers()) {
            if (player.getAutoSaboPassMedian() == 0) {
                continue;
            }
            int highNum = player.getAutoSaboPassMedian() * 6 * 3 / 2;
            int result = ThreadLocalRandom.current().nextInt(1, highNum + 1);
            boolean shouldDoIt = false;
            if (result == highNum) {
                shouldDoIt = true;
            }
            if (shouldDoIt) {
                for (String messageID : messageIDs) {
                    if (shouldPlayerLeaveAReact(player, activeGame, messageID)) {
                        String message = activeGame.isFoWMode() ? "No sabotage" : null;
                        ButtonHelper.addReaction(player, false, false, message, null, messageID, activeGame);
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
            if (Mapper.isColorValid(colorFromString)) {
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
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
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

        return new File(ResourceHelper.getInstance().getResourceFromFolder("strat_cards/", scSet +
            "_" + scAsString + ".png", "Could not find SC image!"));
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
        String name = TileHelper.getTile(tileID).getName();
        if (TileHelper.getTile(tileID).getPlanets().size() > 0) {
            name = name + " (";
        }
        for (String planet : TileHelper.getTile(tileID).getPlanets()) {
            name = name + Mapper.getPlanet(planet).getResources() + "/" + Mapper.getPlanet(planet).getInfluence() + ", ";
        }
        if (TileHelper.getTile(tileID).getPlanets().size() > 0) {
            name = name.substring(0, name.length() - 2) + ")";
        }
        return name;
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
            return getPlanetRepresentationPlusEmoji(planetID) + " " + Emojis.getResourceEmoji(planet.getResources()) + Emojis.getInfluenceEmoji(planet.getInfluence());
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
            return getPlanetRepresentationPlusEmoji(planetID) + " " + Emojis.getResourceEmoji(planet.getResources());
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
            if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back") && !activeGame.isHomeBrewSCMode()) {

                button = Button.secondary("FFCC_" + playerPicker.getFaction() + "_scPick_" + sc, label).withEmoji(scEmoji);
            } else {
                button = Button.secondary("FFCC_" + playerPicker.getFaction() + "_scPick_" + sc, "" + sc + label);
            }
            scButtons.add(button);
        }
        return scButtons;
    }

    public static List<Button> getPlanetExhaustButtons(GenericInteractionCreateEvent event, Player player, Game activeGame) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        for (String planet : planets) {
            String techType = "none";
            if(planet.contains("custodia") || planet.contains("ghoti")){
                Button button = Button.danger("spend_" + planet, getPlanetRepresentation(planet, activeGame));
                planetButtons.add(button);
                continue;
            }
            if(Mapper.getPlanet(planet).getTechSpecialties() != null && Mapper.getPlanet(planet).getTechSpecialties().size() > 0){
                techType = Mapper.getPlanet(planet).getTechSpecialties().get(0).toString().toLowerCase();
            }else{
                techType = ButtonHelper.getTechSkipAttachments(activeGame, planet);
            }
            if(techType.equalsIgnoreCase("none")){
                Button button = Button.danger("spend_" + planet, getPlanetRepresentation(planet, activeGame));
                planetButtons.add(button);
            }else{
                Button techB = Button.danger("spend_" + planet, getPlanetRepresentation(planet, activeGame));
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

    public static List<Button> getPlaceUnitButtons(GenericInteractionCreateEvent event, Player player, Game activeGame, Tile tile, String warfareNOtherstuff, String placePrefix) {
        List<Button> unitButtons = new ArrayList<>();
        boolean regulated = activeGame.getLaws().containsKey("conscription") || activeGame.getLaws().containsKey("absol_conscription");
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String tp = tile.getPosition();
        if (!"muaatagent".equalsIgnoreCase(warfareNOtherstuff)) {
            if (player.hasWarsunTech()) {
                Button wsButton = Button.success("FFCC_" + player.getFaction() + "_" + placePrefix + "_warsun_" + tp, "Produce Warsun");
                wsButton = wsButton.withEmoji(Emoji.fromFormatted(Emojis.warsun));
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
            && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {
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
        }
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet planet && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                if ("warfare".equalsIgnoreCase(warfareNOtherstuff)) {
                    if (unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) < 1 && unitHolder.getUnitCount(UnitType.CabalSpacedock, player.getColor()) < 1
                        && !player.hasUnit("saar_spacedock") && !player.hasUnit("saar_spacedock2")) {
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
                if (!"freelancers".equalsIgnoreCase(warfareNOtherstuff) && !regulated && unitHolders.size() < 4 && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {
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
                if (!"freelancers".equalsIgnoreCase(warfareNOtherstuff) && unitHolders.size() < 4 && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)) {
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

    /**
     * Deprecated - use game.getPing() instead
     */
    @Deprecated
    public static String getGamePing(SlashCommandInteractionEvent event, Game activeGame) {
        return getGamePing(event.getGuild(), activeGame);
    }

    /**
     * Deprecated - use game.getPing() instead
     */
    @Deprecated
    public static String getGamePing(GenericInteractionCreateEvent event, Game activeGame) {
        return getGamePing(activeGame.getGuild(), activeGame);
    }

    /**
     * Deprecated - use game.getPing() instead
     */
    @Deprecated
    public static String getGamePing(Guild guild, Game activeGame) {
        return activeGame.getPing();
    }

    /**
     * Deprecated - use player.getRepresentation(overrideFow:true, includePing:true) instead
     */
    @Deprecated
    public static String getPlayerRepresentation(Player player, Game activeGame, Guild guild, boolean overrideFow) {
        return player.getRepresentation(overrideFow, true);
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
        HashMap<String, Tile> tileMap = activeGame.getTileMap();
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

            String msg = getGamePing(event, activeGame) + " ";
            if (!activeGame.isFoWMode()) {
                if (player != null) {
                    msg += player.getFactionEmoji() + " " + player.getFaction() + " ";
                    msg += player.getPing() + " ";
                }
            }

            msg += "(" + color + ") is over CC limit. CC used: " + ccCount;
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

        HashMap<String, UnitHolder> planetsInfo = activeGame.getPlanetsInfo();
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

    public static HashMap<String, Integer> getLastEntryInHashMap(Map<String, Integer> linkedHashMap) {
        int count = 1;
        for (Map.Entry<String, Integer> it : linkedHashMap.entrySet()) {
            if (count == linkedHashMap.size()) {
                HashMap<String, Integer> lastEntry = new HashMap<>();
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
                    addRolePermissionsToGameChannel(guild, activeGame, tableTalkChannel, role);
                }
                TextChannel actionsChannel = activeGame.getMainGameChannel();
                if (actionsChannel != null && activeGame.getGuild() == guild) {
                    addRolePermissionsToGameChannel(guild, activeGame, actionsChannel, role);
                }
                String gameName = activeGame.getName();
                List<GuildChannel> channels = guild.getChannels().stream().filter(c -> c.getName().startsWith(gameName)).toList();
                for (GuildChannel channel : channels) {
                    addRolePermissionsToGameChannel(guild, activeGame, channel, role);
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

    private static void addRolePermissionsToGameChannel(Guild guild, Game activeGame, GuildChannel channel, long role) {
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
            String buttonID = "FFCC_" + player.getFaction() + "_getTech_" + techName;
            if (!jolNarHeroTech.equalsIgnoreCase("nope")) {
                if(jolNarHeroTech.equalsIgnoreCase("nekro")){
                    buttonID = "FFCC_" + player.getFaction() + "_getTech_" + techName+"_nopay";
                }else{
                    buttonID = "FFCC_" + player.getFaction() + "_swapTechs_" + jolNarHeroTech + "_" + tech.getAlias();
                }
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

    public static List<TechnologyModel> getAllTechOfAType(Game activeGame, String techType, String playerfaction, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        Mapper.getTechs().values().stream()
                .filter(tech -> activeGame.getTechnologyDeck().contains(tech.getAlias()))
                .filter(tech -> tech.getType().toString().equalsIgnoreCase(techType))
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .filter(tech -> tech.getFaction().isEmpty() ||tech.getFaction().get() == null || tech.getFaction().get().equalsIgnoreCase("") ||  player.getNotResearchedFactionTechs().contains(tech.getAlias()))
                .forEach(tech -> techs.add(tech));

        List<TechnologyModel> techs2 = new ArrayList<>();
        for(TechnologyModel tech : techs){
            boolean addTech = true;
            if(tech.getType().toString().toLowerCase().equalsIgnoreCase("unitupgrade")){
                for(String factionTech : player.getNotResearchedFactionTechs()){
                    TechnologyModel fTech = Mapper.getTech(factionTech);
                    if(fTech != null && !fTech.getAlias().equalsIgnoreCase(tech.getAlias()) && fTech.getType().toString().toLowerCase().equalsIgnoreCase("unitupgrade") && fTech.getBaseUpgrade().orElse("bleh").equalsIgnoreCase(tech.getBaseUpgrade().orElse(""))) {
                        addTech = false;
                    }
                }
            }
            if(addTech){
                techs2.add(tech);
            }
        }
        return techs2;
    }

    public static List<TechnologyModel> getAllNonFactionUnitUpgradeTech(Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel tech : Mapper.getTechs().values()) {
            String faction = tech.getFaction().orElse("");
            if (tech.getType().toString().equalsIgnoreCase("unitupgrade")) {
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
            HashMap<String, List<String>> playerScoredPublics = activeGame.getScoredPublicObjectives();
            for (Entry<String, List<String>> scoredPublic : playerScoredPublics.entrySet()) {
                if (Mapper.getPublicObjectivesStage1().containsKey(scoredPublic.getKey()) || Mapper.getPublicObjectivesStage2().containsKey(scoredPublic.getKey())) {
                    if (scoredPublic.getValue().contains(player.getUserID())) {
                        scoredPOCount++;
                    }
                }

            }
            int scoredObjectiveCount = scoredPOCount + scoredSOCount;
            if (scoredObjectiveCount >= 3) {
                UnlockLeader ul = new UnlockLeader();
                ul.unlockLeader(event, "hero", activeGame, player);
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
            if (player.getFaction().contains("ghost") && activeGame.getTile("17") != null) {
                tile = activeGame.getTile("17");
            }
            hsLocations.add(Integer.parseInt(tile.getPosition()));
            unsortedPlayers.put(Integer.parseInt(tile.getPosition()), player);
        }
        Collections.sort(hsLocations);
        List<Player> sortedPlayers = new ArrayList<Player>();
        for (Integer location : hsLocations) {
            sortedPlayers.add(unsortedPlayers.get(location));
        }
        LinkedHashMap<String, Player> newPlayerOrder = new LinkedHashMap<>();
        LinkedHashMap<String, Player> players = new LinkedHashMap<>(activeGame.getPlayers());
        LinkedHashMap<String, Player> playersBackup = new LinkedHashMap<>(activeGame.getPlayers());
        String msg = getGamePing(activeGame.getGuild(), activeGame) + " set order in the following way: \n";
        try {
            for (Player player : sortedPlayers) {
                new SetOrder().setPlayerOrder(newPlayerOrder, players, player);
                msg = msg + ButtonHelper.getTrueIdentity(player, activeGame) + " \n";
            }
            if (!players.isEmpty()) {
                newPlayerOrder.putAll(players);
            }
            activeGame.setPlayers(newPlayerOrder);
        } catch (Exception e) {
            activeGame.setPlayers(playersBackup);
        }
        msg = msg + "Note: the first player is not necesarily speaker/first pick. This is the general speaker order.";
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msg);

    }

    public static void checkEndGame(Game activeGame, Player player) {
        if (player.getTotalVictoryPoints() >= activeGame.getVp()) {
            List<Button> buttons = new ArrayList<Button>();
            buttons.add(Button.success("gameEnd", "End Game"));
            buttons.add(Button.danger("deleteButtons", "Mistake, delete these"));
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(),
                getGamePing(activeGame.getGuild(), activeGame) + " it seems like " + ButtonHelper.getIdentOrColor(player, activeGame)
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
        HashMap<String, ActionCardModel> actionCards = Mapper.getActionCards();
        for (ActionCardModel ac : actionCards.values()) {
            if ("pok".equals(ac.getSource())) {
                activeGame.removeACFromGame(ac.getAlias());
            } else if ("codex1".equals(ac.getSource()) && removeCodex) {
                activeGame.removeACFromGame(ac.getAlias());
            }
        }

        //removing SOs
        Map<String, SecretObjectiveModel> soList = Mapper.getSecretObjectives();
        for (SecretObjectiveModel so : soList.values()) {
            if ("pok".equals(so.getSource())) {
                activeGame.removeSOFromGame(so.getAlias());
            }
        }

        //removing POs
        HashMap<String, PublicObjectiveModel> poList = Mapper.getPublicObjectives();
        for (PublicObjectiveModel po : poList.values()) {
            if ("pok".equals(po.getSource())) {
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

        StringBuilder sb = new StringBuilder();
        // sb.append(String.format("%d:", days));
        // sb.append(String.format("%02dh:", hours));
        // sb.append(String.format("%02dm:", minutes));
        sb.append(String.format("%02ds:", seconds));
        sb.append(String.format("%03d:", milleSeconds));
        sb.append(String.format("%03d:", microSeconds));
        sb.append(String.format("%03d", nanoSeconds));

        return sb.toString();
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
}
