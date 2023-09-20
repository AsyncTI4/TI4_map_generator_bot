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
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
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
import ti4.AsyncTI4DiscordBot;
import ti4.ResourceHelper;
import ti4.commands.bothelper.ArchiveOldThreads;
import ti4.commands.bothelper.ListOldThreads;
import ti4.commands.game.SetOrder;
import ti4.commands.leaders.UnlockLeader;
import ti4.commands.tokens.AddCC;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;

public class Helper {

    @Nullable
    public static Player getGamePlayer(Game activeGame, Player initialPlayer, GenericInteractionCreateEvent event, String userID) {
        return getGamePlayer(activeGame, initialPlayer, event.getMember(), userID);
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

    @Nullable
    public static Player getPlayerFromColorOrFaction(Game activeGame, String factionOrColor) {
        Player player = null;
        if (factionOrColor != null) {
            String factionColor = AliasHandler.resolveColor(factionOrColor.toLowerCase());
            factionColor = StringUtils.substringBefore(factionColor, " "); //TO HANDLE UNRESOLVED AUTOCOMPLETE
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeGame.getPlayers().values()) {
                if("keleres".equalsIgnoreCase(factionColor))
                {
                    if (Objects.equals(factionColor+"a", player_.getFaction())) {
                    player = player_;
                    break;
                    }
                    if (Objects.equals(factionColor+"x", player_.getFaction())) {
                    player = player_;
                    break;
                    }
                    if (Objects.equals(factionColor+"m", player_.getFaction())) {
                        player = player_;
                        break;
                    }

                }
                if (Objects.equals(factionColor, player_.getFaction()) ||
                        Objects.equals(factionColor, player_.getColor())) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }
    public static boolean isAllianceModeAndPreviouslyOwnedCheck(Game activeGame, String planet) {
        return (activeGame.isAllianceMode() && doesAnyoneOwnPlanet(activeGame, planet));
    }

     public static boolean isSaboAllowed(Game activeGame, Player player) {
       
        if("pbd100".equalsIgnoreCase(activeGame.getName())){
            return true;
        }
        if(activeGame.getDiscardActionCards().containsKey("sabo1") && activeGame.getDiscardActionCards().containsKey("sabo2") && activeGame.getDiscardActionCards().containsKey("sabo3") && activeGame.getDiscardActionCards().containsKey("sabo4")){
            return false;
        }
        if(player.hasTech("tp") && activeGame.getActivePlayer() != null && activeGame.getActivePlayer().equalsIgnoreCase(player.getUserID())){
            for(Player p2 : activeGame.getRealPlayers()){
                if(p2 == player){
                    continue;
                }
                if(!p2.isPassed()){
                    return true;
                }
            }
            return false;
        }
        return true;
    
    }

    public static boolean doesAnyoneOwnPlanet(Game activeGame, String planet) {
        for(Player player : activeGame.getRealPlayers()){
            if(player.getPlanets().contains(planet)){
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

    public static String getRoleMentionByName(Guild guild,  String roleName) {
        if(roleName == null){
            return "@Oopsidoops no name";
        }
        List<Role> roles = guild.getRolesByName(roleName, true);
        if (!roles.isEmpty()){
            return roles.get(0).getAsMention();
        }
        return "[@" + roleName + "]";
    }

    public static String getColourAsMention(String colour) {
        return getColourAsMention(null, colour);
    }

    public static String getColourAsMention(Guild guild, String colour) {
        if (guild == null) return "@" + colour;
        return getRoleMentionByName(guild, colour);
    }

    public static String getSCAsMention(int sc) {
        return switch (sc) {
            case 1 -> "<@&947965021168762890>";
            case 2 -> "<@&947965277633650699>";
            case 3 -> "<@&947965381488807956>";
            case 4 -> "<@&947965493376061441>";
            case 5 -> "<@&947965546660495381>";
            case 6 -> "<@&947965592013525022>";
            case 7 -> "<@&947965632933146634>";
            case 8 -> "<@&947965671394906172>";
            default -> "**" + sc + "**";
        };
    }

    public static String getSCAsMention(Guild guild, int sc, Game activeGame) {
        if(!getRoleMentionByName(guild, getSCName(sc, activeGame)).isEmpty()){
            return getRoleMentionByName(guild, getSCName(sc, activeGame));
        }else{
            return "@SC #"+sc;
        }
        
    }

    public static String getSCAsMention(Guild guild, String scname) {
        return getRoleMentionByName(guild, scname);
    }

    public static String getSCFrontRepresentation(Game activeGame, int sc) {
        if (activeGame.isHomeBrewSCMode()) return "SC #" + sc + " " + getSCName(sc, activeGame);
        return getSCEmojiFromInteger(sc) + getSCAsMention(activeGame.getGuild(), sc, activeGame);
    }

    public static String getSCBackRepresentation(Game activeGame, int sc) {
        if (activeGame.isHomeBrewSCMode()) return "SC #" + sc + " " + getSCName(sc, activeGame) + " (Played)";
        return getSCBackEmojiFromInteger(sc) + getSCAsMention(activeGame.getGuild(), sc, activeGame);
    }

    public static String getSCName(int sc, Game activeGame) {
        if (Optional.ofNullable(activeGame.getScSetID()).isPresent() && !"null".equals(activeGame.getScSetID())) {
            return Mapper.getStrategyCardSets().get(activeGame.getScSetID()).getCardValues().get(sc);
        }
        return "" + sc;
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
        if(Optional.ofNullable(activeGame.getScSetID()).isEmpty() || "null".equals(activeGame.getScSetID())) { //I don't know *why* this is a thing that can happen, but it is
            scSet = "pok";
        }
        boolean pbd100or500 = "pbd100".equals(activeGame.getName()) || "pbd500".equals(activeGame.getName()) && !"tribunal".equals(scSet);
        String scAsString = String.valueOf(sc);
        if(pbd100or500) {
            char scValue = String.valueOf(sc).charAt(0);
            scAsString = String.valueOf(scValue);
        }

        return new File(ResourceHelper.getInstance().getResourceFromFolder("strat_cards/", scSet +
            "_" + scAsString + ".png", "Could not find SC image!"));
    }

    //private static List<String> testingEmoji = Arrays.asList("🐷","🙉","💩","👺","🥵","🤯","😜","👀","🦕","🐦","🦏","🐸");

    public static Emoji getPlayerEmoji(Game activeGame, Player player, Message message) {
        Emoji emojiToUse;
        String playerFaction = player.getFaction();
        emojiToUse = Emoji.fromFormatted(getFactionIconFromDiscord(playerFaction));
        String messageId = message.getId();

        if (activeGame.isFoWMode()) {
            int index = 0;
            for (Player player_ : activeGame.getPlayers().values()) {
                if (player_ == player) break;
                index++;
            }
            emojiToUse = Emoji.fromFormatted(getRandomizedEmoji(index, messageId));
        }

        return emojiToUse;
    }

    public static String getRandomizedEmoji(int value, String messageID) {
        List<String> symbols = new ArrayList<>(Emojis.symbols);
        //symbols = new ArrayList<>(testingEmoji);
        Random seed = messageID == null ? ThreadLocalRandom.current() : new Random(messageID.hashCode());
        Collections.shuffle(symbols, seed);
        value = value % symbols.size();
        return symbols.get(value);
    }

    public static String getRandomSemLore() {
        List<String> semLores = new ArrayList<>(Emojis.SemLores);
        Random seed = ThreadLocalRandom.current();
        Collections.shuffle(semLores, seed);
        return semLores.get(0);
    }

    public static String getRandomGoodDog() {
        List<String> goodDogs = new ArrayList<>(Emojis.GoodDogs);
        Random seed = ThreadLocalRandom.current();
        Collections.shuffle(goodDogs, seed);
        return goodDogs.get(0);
    }

    public static String getFactionIconFromDiscord(String faction) {
        if (faction == null) {
            return getRandomizedEmoji(0, null);
        }
        return switch (faction.toLowerCase()) {
            case "arborec" -> Emojis.Arborec;
            case "argent" -> Emojis.Argent;
            case "cabal" -> Emojis.Cabal;
            case "empyrean" -> Emojis.Empyrean;
            case "ghost", "creuss" -> Emojis.Ghost;
            case "hacan" -> Emojis.Hacan;
            case "jolnar" -> Emojis.Jolnar;
            case "l1z1x" -> Emojis.L1Z1X;
            case "letnev" -> Emojis.Letnev;
            case "yssaril" -> Emojis.Yssaril;
            case "mahact" -> Emojis.Mahact;
            case "mentak" -> Emojis.Mentak;
            case "muaat" -> Emojis.Muaat;
            case "naalu" -> Emojis.Naalu;
            case "naaz" -> Emojis.Naaz;
            case "nekro" -> Emojis.Nekro;
            case "nomad" -> Emojis.Nomad;
            case "saar" -> Emojis.Saar;
            case "sardakk" -> Emojis.Sardakk;
            case "sol" -> Emojis.Sol;
            case "titans" -> Emojis.Titans;
            case "winnu" -> Emojis.Winnu;
            case "xxcha" -> Emojis.Xxcha;
            case "yin" -> Emojis.Yin;

            case "lazax" -> Emojis.Lazax;

            case "keleres", "keleresx", "keleresm", "keleresa" -> Emojis.Keleres;

            case "augers" -> Emojis.augers;
            case "axis" -> Emojis.axis;
            case "bentor" -> Emojis.bentor;
            case "blex", "kyro" -> Emojis.blex;
            case "celdauri" -> Emojis.celdauri;
            case "cheiran" -> Emojis.cheiran;
            case "cymiae" -> Emojis.cymiae;
            case "dihmohn" -> Emojis.dihmohn;
            case "edyn" -> Emojis.edyn;
            case "florzen" -> Emojis.florzen;
            case "freesystems" -> Emojis.freesystems;
            case "ghemina" -> Emojis.ghemina;
            case "ghoti" -> Emojis.ghoti;
            case "gledge" -> Emojis.gledge;
            case "khrask" -> Emojis.khrask;
            case "kjalengard" -> Emojis.kjalengard;
            case "kollecc" -> Emojis.kollecc;
            case "kolume" -> Emojis.kolume;
            case "kortali" -> Emojis.kortali;
            case "lanefir" -> Emojis.lanefir;
            case "lizho" -> Emojis.lizho;
            case "mirveda" -> Emojis.mirveda;
            case "mortheus" -> Emojis.mortheus;
            case "mykomentori" -> Emojis.mykomentori;
            case "nivyn" -> Emojis.nivyn;
            case "nokar" -> Emojis.nokar;
            case "olradin" -> Emojis.olradin;
            case "rohdhna" -> Emojis.rohdhna;
            case "tnelis" -> Emojis.tnelis;
            case "vaden" -> Emojis.vaden;
            case "vaylerian" -> Emojis.vaylerian;
            case "veldyr" -> Emojis.veldyr;
            case "zealots" -> Emojis.zealots;
            case "zelian" -> Emojis.zelian;

            case "admins" -> Emojis.AdminsFaction;
            case "qulane" -> Emojis.Qulane;

            case "franken1" -> Emojis.OneToe;
            case "franken2" -> Emojis.TwoToes;
            case "franken3" -> Emojis.ThreeToes;
            case "franken4" -> Emojis.FourToes;
            case "franken5" -> Emojis.FiveToes;
            case "franken6" -> Emojis.SixToes;
            case "franken7" -> Emojis.SevenToes;
            case "franken8" -> Emojis.EightToes;
            
            default -> getRandomizedEmoji(0, null);
        };
    }

    public static String getPlanetEmoji(String planet) {
        return switch (planet.toLowerCase()) {
            case "mr" -> Emojis.MecatolRex;
            case "hopesend" -> Emojis.HopesEnd;
            case "primor" -> Emojis.Primor;
            case "archonvail" -> Emojis.PlanetArchonVail;
            case "semlore" -> getRandomSemLore();
            default -> Emojis.SemLor;
        };
    }

    public static String getPlanetRepresentationPlusEmoji(String planet) {
        String planetProper = Mapper.getPlanetRepresentations().get(planet);
        return getPlanetEmoji(planet) + " " + (Objects.isNull(planetProper) ? planet : planetProper);
    }
    public static String getPlanetRepresentation(String planet, Game activeGame) {
        planet = planet.toLowerCase().replace(" ", "");
        planet = planet.replace("'", "");
        planet = planet.replace("-", "");
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planet));
        Planet planet2 = (Planet) unitHolder;
        if(planet2 == null){
            return planet + " bot error. Tell fin";
        }

        return Mapper.getPlanetRepresentations().get(AliasHandler.resolvePlanet(planet)) + " (" +planet2.getResources() + "/"+planet2.getInfluence()+")";
    }

    public static String getPlanetRepresentationPlusEmojiPlusResourceInfluence(String planetID, Game activeGame) {
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = (Planet) unitHolder;
            return getPlanetRepresentationPlusEmoji(planetID) + " " + getResourceEmoji(planet.getResources()) + getInfluenceEmoji(planet.getInfluence());
        }
    }

    public static String getPlanetRepresentationPlusEmojiPlusInfluence(String planetID, Game activeGame) {
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = (Planet) unitHolder;
            return getPlanetRepresentationPlusEmoji(planetID) + " " + getInfluenceEmoji(planet.getInfluence());
        }
    }

    public static String getPlanetRepresentationPlusEmojiPlusResources(String planetID, Game activeGame) {
        UnitHolder unitHolder = activeGame.getPlanetsInfo().get(AliasHandler.resolvePlanet(planetID));
        if (unitHolder == null) {
            return getPlanetRepresentationPlusEmoji(planetID);
        } else {
            Planet planet = (Planet) unitHolder;
            return getPlanetRepresentationPlusEmoji(planetID) + " " + getResourceEmoji(planet.getResources());
        }
    }

    public static List<Button> getPlanetRefreshButtons(GenericInteractionCreateEvent event, Player player, Game activeGame) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getExhaustedPlanets());
        for (String planet : planets) {
            Button button = Button.success("refresh_"+planet, getPlanetRepresentation(planet, activeGame));
            planetButtons.add(button);
        }
        return planetButtons;
    }
    public static String getPlayerDependingOnFog(Game activeGame, Player player)
    {
        String ident;

        if(activeGame.isFoWMode())
        {
            ident = player.getColor();
        }
        else
        {
            ident = getFactionIconFromDiscord(player.getFaction());
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
            Emoji scEmoji = Emoji.fromFormatted(getSCBackEmojiFromInteger(sc));
            Button button;
            String label = " ";
            if(activeGame.getScTradeGoods().get(sc) > 0 && !activeGame.isFoWMode()){
                label = "[has "+ activeGame.getScTradeGoods().get(sc)+" tg]";
            }
            if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back") && !activeGame.isHomeBrewSCMode()) {
                
                button = Button.secondary("FFCC_"+playerPicker.getFaction()+"_scPick_" + sc, label).withEmoji(scEmoji);
            } else {
                button = Button.secondary("FFCC_"+playerPicker.getFaction()+"_scPick_" + sc, "" + sc + label);
            }
            scButtons.add(button);
        }
        return scButtons;
    }

    public static List<Button> getPlanetExhaustButtons(GenericInteractionCreateEvent event, Player player, Game activeGame) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        for (String planet : planets) {
            Button button = Button.danger("spend_"+planet, getPlanetRepresentation(planet, activeGame));
            planetButtons.add(button);
        }
        return planetButtons;
    }

    public static List<Button> getPlanetPlaceUnitButtons(Player player, Game activeGame, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets(activeGame));
        for (String planet : planets) {
            Button button = Button.danger("FFCC_"+player.getFaction()+"_"+prefix+"_"+unit+"_"+planet, getPlanetRepresentation(planet, activeGame));
            planetButtons.add(button);
        }
        return planetButtons;
    }
    
    public static List<Button> getTileWithShipsPlaceUnitButtons(Player player, Game activeGame, String unit, String prefix) {
        List<Button> planetButtons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, activeGame);
        for (Tile tile : tiles) {
            Button button = Button.danger("FFCC_"+player.getFaction()+"_"+prefix+"_"+unit+"_"+tile.getPosition(), tile.getRepresentationForButtons(activeGame,player));
            planetButtons.add(button);
        }
        return planetButtons;
    }
    
    public static List<Button> getPlaceUnitButtons(GenericInteractionCreateEvent event, Player player, Game activeGame, Tile tile, String warfareNOtherstuff, String placePrefix) {
        List<Button> unitButtons = new ArrayList<>();
        boolean regulated = activeGame.getLaws().containsKey("conscription") ||  activeGame.getLaws().containsKey("absol_conscription");
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String tp = tile.getPosition();
        if(!"muaatagent".equalsIgnoreCase(warfareNOtherstuff))
        {
            if(player.hasWarsunTech()) {
                Button wsButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_warsun_"+tp, "Produce Warsun" );
                wsButton = wsButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("warsun")));
                unitButtons.add(wsButton);
            }
            Button fsButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_flagship_"+tp, "Produce Flagship" );
            fsButton = fsButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("flagship")));
            unitButtons.add(fsButton);
        }
        Button dnButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_dreadnought_"+tp, "Produce Dreadnought" );
        dnButton = dnButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("dreadnought")));
        unitButtons.add(dnButton);
        Button cvButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_carrier_"+tp, "Produce Carrier" );
        cvButton = cvButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("carrier")));
        unitButtons.add(cvButton);
        Button caButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_cruiser_"+tp, "Produce Cruiser" );
        caButton = caButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("cruiser")));
        unitButtons.add(caButton);
        Button ddButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_destroyer_"+tp, "Produce Destroyer" );
        ddButton = ddButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("destroyer")));
        unitButtons.add(ddButton);
        Button ff1Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_fighter_"+tp, "Produce 1 Fighter" );
        ff1Button = ff1Button.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("fighter")));
        unitButtons.add(ff1Button);
        if(!"freelancers".equalsIgnoreCase(warfareNOtherstuff) && unitHolders.size() < 4 && !regulated && !"sling".equalsIgnoreCase(warfareNOtherstuff)&& !"chaosM".equalsIgnoreCase(warfareNOtherstuff)){
            Button ff2Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_2ff_"+tp, "Produce 2 Fighters" );
            ff2Button = ff2Button.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("fighter")));
            unitButtons.add(ff2Button);
        }
        
        if(!"freelancers".equalsIgnoreCase(warfareNOtherstuff) && !"sling".equalsIgnoreCase(warfareNOtherstuff)&& !"chaosM".equalsIgnoreCase(warfareNOtherstuff)){

            if(player.hasUnexhaustedLeader("argentagent", activeGame)){
                        Button argentButton = Button.success("FFCC_"+player.getFaction()+"_"+"exhaustAgent_argentagent_"+tile.getPosition(), "Use Argent Agent");
                        argentButton = argentButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("argent")));
                        unitButtons.add(argentButton);
            }
        }
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet planet && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                String colorID = Mapper.getColorID(player.getColor());
                String sdKey =  colorID+ "_sd.png";
                String csdKey =  colorID+ "_csd.png";
                if("cabal".equalsIgnoreCase(player.getFaction())){
                    sdKey = csdKey;
                }

                if ("warfare".equalsIgnoreCase(warfareNOtherstuff)) {

                    if ((planet.getUnits().get(sdKey) == null || planet.getUnits().get(sdKey) == 0) && !"saar".equalsIgnoreCase(player.getFaction())) {
                        continue;
                    }
                }
                
                String pp = planet.getName();
                if ("genericBuild".equalsIgnoreCase(warfareNOtherstuff)) {
                    Button sdButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_sd_"+pp, "Place 1 Space Dock on "+ getPlanetRepresentation(pp, activeGame));
                    sdButton = sdButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("spacedock")));
                    unitButtons.add(sdButton);
                    Button pdsButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_pds_"+pp, "Place 1 PDS on "+ getPlanetRepresentation(pp, activeGame));
                    pdsButton = pdsButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("pds")));
                    unitButtons.add(pdsButton);
                }
                Button inf1Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_infantry_"+pp, "Produce 1 Infantry on "+ getPlanetRepresentation(pp, activeGame));
                inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("infantry")));
                unitButtons.add(inf1Button);
                if(!"freelancers".equalsIgnoreCase(warfareNOtherstuff) && !regulated && unitHolders.size() < 4 && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)){
                    Button inf2Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_2gf_"+pp, "Produce 2 Infantry on "+ getPlanetRepresentation(pp, activeGame) );
                    inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("infantry")));
                    unitButtons.add(inf2Button);
                }
                Button mfButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_mech_"+pp, "Produce Mech on "+ getPlanetRepresentation(pp, activeGame) );
                mfButton = mfButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("mech")));
                unitButtons.add(mfButton);

                
            }
            else if(ButtonHelper.canIBuildGFInSpace(activeGame, player, tile, warfareNOtherstuff) && !"sling".equalsIgnoreCase(warfareNOtherstuff)) {
                Button inf1Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_infantry_space"+tile.getPosition(), "Produce 1 Infantry in space");
                inf1Button = inf1Button.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("infantry")));
                unitButtons.add(inf1Button);
                if(!"freelancers".equalsIgnoreCase(warfareNOtherstuff) && unitHolders.size() < 4 && !"chaosM".equalsIgnoreCase(warfareNOtherstuff)){
                    Button inf2Button = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_2gf_space"+tile.getPosition(), "Produce 2 Infantry in space" );
                    inf2Button = inf2Button.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("infantry")));
                    unitButtons.add(inf2Button);
                }
                Button mfButton = Button.success("FFCC_"+player.getFaction()+"_"+placePrefix+"_mech_space"+tile.getPosition(), "Produce Mech in space" );
                mfButton = mfButton.withEmoji(Emoji.fromFormatted(getEmojiFromDiscord("mech")));
                unitButtons.add(mfButton);
            }
        }
        if("place".equalsIgnoreCase(placePrefix)){
            Button DoneProducingUnits = Button.danger("deleteButtons_"+warfareNOtherstuff, "Done Producing Units");
            unitButtons.add(DoneProducingUnits);
        }
        
        return unitButtons;
    }

    public static List<Button> getPlanetSystemDiploButtons(GenericInteractionCreateEvent event, Player player, Game activeGame, boolean ac, Player mahact) {
        List<Button> planetButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets(activeGame));
        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
        if(mahact == null){
            for (String planet : planets) {
                if (!getPlanetRepresentation(planet, activeGame).toLowerCase().contains("mecatol") || ac) {
                    Button button = Button.secondary(finsFactionCheckerPrefix+"diplo_"+planet+"_"+"diploP", getPlanetRepresentation(planet, activeGame) + " System");
                    planetButtons.add(button);
                }
            }
        }else{
            for(Tile tile : activeGame.getTileMap().values()){
                if(FoWHelper.playerHasUnitsInSystem(player, tile) && !ButtonHelper.isTileHomeSystem(tile, activeGame)){
                    Button button = Button.secondary(finsFactionCheckerPrefix+"diplo_"+tile.getPosition()+"_"+"mahact"+mahact.getColor(), tile.getRepresentation() + " System");
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

    public static String getInfluenceEmoji(int count) {
        return switch (count) {
            case 0 -> Emojis.Influence_0;
            case 1 -> Emojis.Influence_1;
            case 2 -> Emojis.Influence_2;
            case 3 -> Emojis.Influence_3;
            case 4 -> Emojis.Influence_4;
            case 5 -> Emojis.Influence_5;
            case 6 -> Emojis.Influence_6;
            case 7 -> Emojis.Influence_7;
            case 8 -> Emojis.Influence_8;
            case 9 -> Emojis.Influence_9;
            default -> Emojis.influence + count;
        };
    }

    public static String getResourceEmoji(int count) {
        return switch (count) {
            case 0 -> Emojis.Resources_0;
            case 1 -> Emojis.Resources_1;
            case 2 -> Emojis.Resources_2;
            case 3 -> Emojis.Resources_3;
            case 4 -> Emojis.Resources_4;
            case 5 -> Emojis.Resources_5;
            case 6 -> Emojis.Resources_6;
            case 7 -> Emojis.Resources_7;
            case 8 -> Emojis.Resources_8;
            case 9 -> Emojis.Resources_9;
            default -> Emojis.resources + count;
        };
    }

    public static String getToesEmoji(int count) {
        return switch (count) {
            case 0 -> Emojis.NoToes;
            case 1 -> Emojis.OneToe;
            case 2 -> Emojis.TwoToes;
            case 3 -> Emojis.ThreeToes;
            case 4 -> Emojis.FourToes;
            case 5 -> Emojis.FiveToes;
            case 6 -> Emojis.SixToes;
            case 7 -> Emojis.SevenToes;
            case 8 -> Emojis.EightToes;
            case 9 -> Emojis.NineToes;
            default -> Emojis.NoToes + count;
        };
    }

    /**
     * Takes an emoji's name string and returns its full name including ID.
     * @emojiName the name of the emoji as entered on the Emoji section of the server
     * @return the name of the emoji including ID
     */
    public static String getEmojiFromDiscord(String emojiName) {
        return switch (emojiName.toLowerCase()) {
            //EXPLORATION
            case "hfrag" -> Emojis.HFrag;
            case "cfrag" -> Emojis.CFrag;
            case "ifrag" -> Emojis.IFrag;
            case "ufrag" -> Emojis.UFrag;
            case "relic" -> Emojis.Relic;
            case "cultural" -> Emojis.Cultural;
            case "industrial" -> Emojis.Industrial;
            case "hazardous" -> Emojis.Hazardous;
            case "frontier" -> Emojis.Frontier;

            //CARDS
            case "sc1" -> Emojis.SC1;
            case "sc2" -> Emojis.SC2;
            case "sc3" -> Emojis.SC3;
            case "sc4" -> Emojis.SC4;
            case "sc5" -> Emojis.SC5;
            case "sc6" -> Emojis.SC6;
            case "sc7" -> Emojis.SC7;
            case "sc8" -> Emojis.SC8;
            case "sc1back" -> Emojis.SC1Back;
            case "sc2back" -> Emojis.SC2Back;
            case "sc3back" -> Emojis.SC3Back;
            case "sc4back" -> Emojis.SC4Back;
            case "sc5back" -> Emojis.SC5Back;
            case "sc6back" -> Emojis.SC6Back;
            case "sc7back" -> Emojis.SC7Back;
            case "sc8back" -> Emojis.SC8Back;
            case "actioncard" -> Emojis.ActionCard;
            case "agenda" -> Emojis.Agenda;
            case "pn" -> Emojis.PN;

            //OBJECTIVES
            case "secretobjective" -> Emojis.SecretObjective;
            case "public1" -> Emojis.Public1;
            case "public2" -> Emojis.Public2;
            case "public1alt" -> Emojis.Public1alt;
            case "public2alt" -> Emojis.Public2alt;
            case "secretobjectivealt" -> Emojis.SecretObjectiveAlt;

            //COMPONENTS
            case "tg" -> Emojis.tg;
            case "comm" -> Emojis.comm;
            case "sleeper" -> Emojis.Sleeper;
            case "sleeperb" -> Emojis.SleeperB;

            //UNITS
            case "warsun" -> Emojis.warsun;
            case "spacedock" -> Emojis.spacedock;
            case "pds" -> Emojis.pds;
            case "mech" -> Emojis.mech;
            case "infantry" -> Emojis.infantry;
            case "flagship" -> Emojis.flagship;
            case "fighter" -> Emojis.fighter;
            case "dreadnought" -> Emojis.dreadnought;
            case "destroyer" -> Emojis.destroyer;
            case "carrier" -> Emojis.carrier;
            case "cruiser" -> Emojis.cruiser;

            //LEADERS - AGENTS
            case "arborecagent" -> Emojis.ArborecAgent;
            case "argentagent" -> Emojis.ArgentAgent;
            case "cabalagent" -> Emojis.CabalAgent;
            case "ghostagent" -> Emojis.GhostAgent;
            case "empyreanagent" -> Emojis.EmpyreanAgent;
            case "hacanagent" -> Emojis.HacanAgent;
            case "jolnaragent" -> Emojis.JolnarAgent;
            case "keleresagent" -> Emojis.KeleresAgent;
            case "l1z1xagent" -> Emojis.L1z1xAgent;
            case "letnevagent" -> Emojis.LetnevAgent;
            case "mahactagent" -> Emojis.MahactAgent;
            case "mentakagent" -> Emojis.MentakAgent;
            case "muaatagent" -> Emojis.MuaatAgent;
            case "naaluagent" -> Emojis.NaaluAgent;
            case "naazagent" -> Emojis.NaazAgent;
            case "nekroagent" -> Emojis.NekroAgent;
            case "nomadagentartuno" -> Emojis.NomadAgentArtuno;
            case "nomadagentmercer" -> Emojis.NomadAgentMercer;
            case "nomadagentthundarian" -> Emojis.NomadAgentThundarian;
            case "sardakkagent" -> Emojis.SardakkAgent;
            case "saaragent" -> Emojis.SaarAgent;
            case "solagent" -> Emojis.SolAgent;
            case "titansagent" -> Emojis.TitansAgent;
            case "winnuagent" -> Emojis.WinnuAgent;
            case "xxchaagent" -> Emojis.XxchaAgent;
            case "yinagent" -> Emojis.YinAgent;
            case "yssarilagent" -> Emojis.YssarilAgent;

            //LEADERS - COMMANDERS
            case "arboreccommander" -> Emojis.ArborecCommander;
            case "argentcommander" -> Emojis.ArgentCommander;
            case "cabalcommander" -> Emojis.CabalCommander;
            case "ghostcommander" -> Emojis.GhostCommander;
            case "empyreancommander" -> Emojis.EmpyreanCommander;
            case "hacancommander" -> Emojis.HacanCommander;
            case "jolnarcommander" -> Emojis.JolnarCommander;
            case "kelerescommander" -> Emojis.KeleresCommander;
            case "l1z1xcommander" -> Emojis.L1z1xCommander;
            case "letnevcommander" -> Emojis.LetnevCommander;
            case "mahactcommander" -> Emojis.MahactCommander;
            case "mentakcommander" -> Emojis.MentakCommander;
            case "muaatcommander" -> Emojis.MuaatCommander;
            case "naalucommander" -> Emojis.NaaluCommander;
            case "naazcommander" -> Emojis.NaazCommander;
            case "nekrocommander" -> Emojis.NekroCommander;
            case "nomadcommander" -> Emojis.NomadCommander;
            case "sardakkcommander" -> Emojis.SardakkCommander;
            case "saarcommander" -> Emojis.SaarCommander;
            case "solcommander" -> Emojis.SolCommander;
            case "titanscommander" -> Emojis.TitansCommander;
            case "winnucommander" -> Emojis.WinnuCommander;
            case "xxchacommander" -> Emojis.XxchaCommander;
            case "yincommander" -> Emojis.YinCommander;
            case "yssarilcommander" -> Emojis.YssarilCommander;

            //LEADERS - HEROES
            case "arborechero" -> Emojis.ArborecHero;
            case "argenthero" -> Emojis.ArgentHero;
            case "cabalhero" -> Emojis.CabalHero;
            case "ghosthero" -> Emojis.GhostHero;
            case "empyreanhero" -> Emojis.EmpyreanHero;
            case "hacanhero" -> Emojis.HacanHero;
            case "jolnarhero" -> Emojis.JolnarHero;
            case "keleresherokuuasi" -> Emojis.KeleresHeroKuuasi;
            case "keleresheroodlynn" -> Emojis.KeleresHeroOdlynn;
            case "keleresheroharka" -> Emojis.KeleresHeroHarka;
            case "l1z1xhero" -> Emojis.L1z1xHero;
            case "letnevhero" -> Emojis.LetnevHero;
            case "mahacthero" -> Emojis.MahactHero;
            case "mentakhero" -> Emojis.MentakHero;
            case "muaathero" -> Emojis.MuaatHero;
            case "naaluhero" -> Emojis.NaaluHero;
            case "naazhero" -> Emojis.NaazHero;
            case "nekrohero" -> Emojis.NekroHero;
            case "nomadhero" -> Emojis.NomadHero;
            case "sardakkhero" -> Emojis.SardakkHero;
            case "saarhero" -> Emojis.SaarHero;
            case "solhero" -> Emojis.SolHero;
            case "titanshero" -> Emojis.TitansHero;
            case "winnuhero" -> Emojis.WinnuHero;
            case "xxchahero" -> Emojis.XxchaHero;
            case "yinhero" -> Emojis.YinHero;
            case "yssarilhero" -> Emojis.YssarilHero;

            //OTHER
            case "whalpha" -> Emojis.WHalpha;
             case "grift" -> Emojis.GRift;
            case "whbeta" -> Emojis.WHbeta;
            case "whgamma" -> Emojis.WHgamma;
            case "creussalpha" -> Emojis.CreussAlpha;
            case "creussbeta" -> Emojis.CreussBeta;
            case "creussgamma" -> Emojis.CreussGamma;
            case "influence" -> Emojis.influence;
            case "resources" -> Emojis.resources;
            case "legendaryplanet" -> Emojis.LegendaryPlanet;
            case "cybernetictech" -> Emojis.CyberneticTech;
            case "propulsiontech" -> Emojis.PropulsionTech;
            case "biotictech" -> Emojis.BioticTech;
            case "warfaretech" -> Emojis.WarfareTech;
            case "unitupgradetech" -> Emojis.UnitUpgradeTech;

            default -> getRandomGoodDog();
        };
    }

    public static String getGamePing(SlashCommandInteractionEvent event, Game activeGame) {
        return getGamePing(event.getGuild(), activeGame);
    }

    public static String getGamePing(GenericInteractionCreateEvent event, Game activeGame) {
        return getGamePing(activeGame.getGuild(), activeGame);
    }

    public static String getGamePing(Guild guild, Game activeGame) {
        if (guild != null) {
            for (Role role : guild.getRoles()) {
                if (activeGame.getName().equals(role.getName().toLowerCase())) {
                    return role.getAsMention();
                }
            }
            StringBuilder sb = new StringBuilder(activeGame.getName()).append(" ");
            for (String playerID : activeGame.getPlayerIDs()) {
                Member member = guild.getMemberById(playerID);
                if (member != null) sb.append(guild.getMemberById(playerID).getAsMention()).append(" ");
            }
            return sb.toString();
        }
        return "";
    }

    public static String getPlayerPing(Player player) {
        User userById = AsyncTI4DiscordBot.jda.getUserById(player.getUserID());
        if (userById == null) {
            return "";
        }
        String mention = userById.getAsMention();
        switch (player.getUserID()) {
            case "154000388121559040" ->  //mysonisalsonamedbort
                mention += " " + Emojis.BortWindow;
            case "150809002974904321" ->  //tispoon
                mention += " " + Emojis.SpoonAbides;
            case "228999251328368640" ->  //Jazzx
                mention += " " + Emojis.Scout;
        }
        return mention;
    }

    // Pass the buck
    public static String getPlayerRepresentation(Player player, Game activeGame) {
        if (activeGame == null) return getPlayerRepresentation(player, null, null, false);
        return getPlayerRepresentation(player, activeGame, activeGame.getGuild(), false);
    }

    // One representation to rule them all
    public static String getPlayerRepresentation(Player player, Game activeGame, Guild guild, boolean overrideFow) {
        boolean privateGame = FoWHelper.isPrivateGame(activeGame);
        if (privateGame && !overrideFow){
            return getColourAsMention(guild, player.getColor());
        }

        if (activeGame != null && activeGame.isCommunityMode()) {
            Role roleForCommunity = player.getRoleForCommunity();
            if (roleForCommunity == null) {
                return defaultPlayerRepresentation(player, guild);
            } else {
                return getRoleMentionByName(guild, roleForCommunity.getName());
            }
        }

        return defaultPlayerRepresentation(player, guild);
    }

    private static String defaultPlayerRepresentation(Player player, Guild guild) {
        StringBuilder sb = new StringBuilder(getFactionIconFromDiscord(player.getFaction()));
        sb.append(" ").append(getPlayerPing(player));
        if (player.getColor() != null && !"null".equals(player.getColor())) {
            sb.append(" _").append(getColourAsMention(guild, player.getColor())).append("_");
        }
        return sb.toString();
    }

    public static String getFactionLeaderEmoji(Leader leader) {
        return getEmojiFromDiscord(leader.getId());
    }

    public static String getLeaderRepresentation(Leader leader, boolean includeTitle, boolean includeAbility, boolean includeUnlockCondition) {
        String leaderID = leader.getId();

        String leaderRep =  Mapper.getLeaderRepresentations().get(leaderID);
        if (leaderRep == null) {
            BotLogger.log("Invalid `leaderID=" + leaderID + "` caught within `Helper.getLeaderRepresentation`");
            return leader.getId();
        }

        //leaderID = 0:LeaderName ; 1:LeaderTitle ; 2:BacksideTitle/HeroAbility ; 3:AbilityWindow ; 4:AbilityText
        String[] leaderRepSplit = leaderRep.split(";");
        if (leaderRepSplit.length != 6) {
            BotLogger.log("Invalid `leaderID=" + leaderID + "` `leaderRepSplit.length=" + leaderRepSplit.length + "` caught within `Helper.getLeaderRepresentation`");
            return leaderRep;
        }
        String leaderName = leaderRepSplit[0];
        String leaderTitle = leaderRepSplit[1];
        String heroAbilityName = leaderRepSplit[2];
        String leaderAbilityWindow = leaderRepSplit[3];
        String leaderAbilityText = leaderRepSplit[4];
        String leaderUnlockCondition = leaderRepSplit[5];

        StringBuilder representation = new StringBuilder();
        representation.append(getFactionLeaderEmoji(leader)).append(" **").append(leaderName).append("**");
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

    public static String getSCEmojiFromInteger(Integer strategy_card) {
        String scEmojiName = "SC" + strategy_card;
        return getEmojiFromDiscord(scEmojiName);
    }

    public static String getSCBackEmojiFromInteger(Integer strategy_card) {
        String scEmojiName = "SC" + strategy_card + "Back";
        return getEmojiFromDiscord(scEmojiName);
    }

    public static void isCCCountCorrect(GenericInteractionCreateEvent event, Game activeGame, String color) {
        int ccCount = getCCCount(activeGame, color);
        informUserCCOverLimit(event, activeGame, color, ccCount);
    }

    public static int getCCCount(Game activeGame, String color) {
        int ccCount = 0;
        if (color == null){
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
            } else if ("mahact".equals(player_.getFaction())){
                for (String color_ : player_.getMahactCC()) {
                    if (factionColor.equals(color_)){
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
                    msg += getFactionIconFromDiscord(player.getFaction()) + " " + player.getFaction() + " ";
                    msg += getPlayerPing(player) + " ";
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
            for (Tile tile : tileMap.values()){
                if (tile.getPosition().equals(position)){
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
        return getPlayerRepresentation(player, activeGame) + ":\n" +
            "Resources: " + getPlayerResourcesAvailable(player, activeGame) + "/" + getPlayerResourcesTotal(player, activeGame) + "  Optimal: " + getPlayerOptimalResourcesAvailable(player, activeGame) + "/" + getPlayerOptimalResourcesTotal(player, activeGame) + "\n" +
            "Influence: " + getPlayerInfluenceAvailable(player, activeGame) + "/" + getPlayerInfluenceTotal(player, activeGame) + "  Optimal: " + getPlayerOptimalInfluenceAvailable(player, activeGame) + "/" + getPlayerOptimalInfluenceTotal(player, activeGame) + "\n";
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
        long threadCount = guild.getThreadChannels().stream().filter(c -> !c.isArchived()).count();
        int closeCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.THREAD_AUTOCLOSE_COUNT.toString(), Integer.class, 20);
        int maxThreadCount = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.MAX_THREAD_COUNT.toString(), Integer.class, 975);

        if (threadCount >= maxThreadCount) {
            BotLogger.log("`Helper.checkThreadLimitAndArchive:` Server: **" + guild.getName() + "** thread count is too high ( " + threadCount + " ) - auto-archiving  " + closeCount + " threads. The oldest thread was " + ListOldThreads.getHowOldOldestThreadIs(guild));
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
        }
        catch (Exception e) {
            BotLogger.log(event, ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    public static String getTechRepresentation(String techID) {
        TechnologyModel tech = Mapper.getTechs().get(techID);

        String techName = tech.getName();
        TechnologyType techType = tech.getType();
        String techFaction = tech.getFaction();
        String factionEmoji = "";
        if (!techFaction.isBlank()) factionEmoji = getFactionIconFromDiscord(techFaction);
        String techEmoji = getEmojiFromDiscord(techType.toString().toLowerCase() + "tech");
        return techEmoji + "**" + techName + "**" + factionEmoji + "\n";
    }

    public static List<Button> getTechButtons(List<TechnologyModel> techs, String techType, Player player) {
        List<Button> techButtons = new ArrayList<>();

        techs.sort((tech1, tech2) -> {
            int req1 = tech1.getRequirements().length();
            int req2 = tech2.getRequirements().length();
            if (req1 < req2) {
                return -1;
            }
            if (req2 < req1) {
                return 1;
            }
            return tech1.getName().compareTo(tech2.getName());
        });

        for (TechnologyModel tech : techs) {
            String techName = tech.getName();
            String buttonID = "FFCC_" + player.getFaction() + "_getTech_" + techName;
            Button techB;
            switch (techType) {
                case "propulsion" -> {
                    techB = Button.primary(buttonID, techName);
                    switch (tech.getRequirements()) {
                        case "" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.PropulsionDisabled));
                        case "B" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.PropulsionTech));
                        case "BB" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Propulsion2));
                        case "BBB" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Propulsion3));
                    }
                }
                case "cybernetic" -> {
                    techB = Button.secondary(buttonID, techName);
                    switch (tech.getRequirements()) {
                        case "" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.CyberneticDisabled));
                        case "Y" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech));
                        case "YY" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Cybernetic2));
                        case "YYY" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Cybernetic3));
                    }
                }
                case "biotic" -> {
                    techB = Button.success(buttonID, techName);
                    switch (tech.getRequirements()) {
                        case "" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.BioticDisabled));
                        case "G" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
                        case "GG" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Biotic2));
                        case "GGG" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Biotic3));
                    }
                }
                case "warfare" -> {
                    techB = Button.danger(buttonID, techName);
                    switch (tech.getRequirements()) {
                        case "" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.WarfareDisabled));
                        case "R" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.WarfareTech));
                        case "RR" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Warfare2));
                        case "RRR" -> techB = techB.withEmoji(Emoji.fromFormatted(Emojis.Warfare3));
                    }
                }
                case "unitupgrade" -> {
                    techB = Button.secondary(buttonID, techName);
                    String unitType = tech.getBaseUpgrade().isEmpty() ? tech.getAlias() : tech.getBaseUpgrade();
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

    public static List<TechnologyModel> getAllTechOfAType(String techType, String playerfaction, Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel tech : Mapper.getTechs().values()) {
            String faction = tech.getFaction();
            if (tech.getType().toString().equalsIgnoreCase(techType)) {
                if (!player.hasTech(tech.getAlias())) {
                    if (!faction.isEmpty()) {
                        if (playerfaction.equalsIgnoreCase(faction) || (playerfaction.toLowerCase().startsWith("keleres") && "Keleres".equalsIgnoreCase(faction))) {
                            techs.add(tech);
                        }
                    } else {
                        techs.add(tech);
                    }
                }
            }
        }
        return techs;
    }

     public static List<TechnologyModel> getAllNonFactionUnitUpgradeTech(Player player) {
        List<TechnologyModel> techs = new ArrayList<>();
        for (TechnologyModel tech : Mapper.getTechs().values()) {
            String faction = tech.getFaction();
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

    public static String getTechRepresentationLong(String techID) {
        TechnologyModel tech = Mapper.getTechs().get(techID);

        String techName = tech.getName();
        TechnologyType techType = tech.getType();
        String techFaction = tech.getFaction();
        String factionEmoji = "";
        if (!techFaction.isBlank()) factionEmoji = getFactionIconFromDiscord(techFaction);
        String techEmoji = getEmojiFromDiscord(techType.toString().toLowerCase() + "tech");
        
        String techText = tech.getText();
        return techEmoji + "**" + techName + "**" + factionEmoji + "\n" +
            "> " + techText + "\n";
    }

    public static String getAgendaRepresentation(@NotNull String agendaID) {
        return getAgendaRepresentation(agendaID, null);
    }

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

     public static void setOrder(Game activeGame){
        
        List<Integer> hsLocations = new ArrayList<Integer>();
        LinkedHashMap<Integer, Player> unsortedPlayers = new LinkedHashMap<>();
        for(Player player : activeGame.getRealPlayers()){
            Tile tile = activeGame.getTile(AliasHandler.resolveTile(player.getFaction()));
            if (tile == null) {
                tile = ButtonHelper.getTileOfPlanetWithNoTrait(player, activeGame);
            }
            if(player.getFaction().contains("ghost") && activeGame.getTile("17") != null){
                tile = activeGame.getTile("17");
            }
            hsLocations.add(Integer.parseInt(tile.getPosition()));
            unsortedPlayers.put(Integer.parseInt(tile.getPosition()), player);
        }
        Collections.sort(hsLocations);
        List<Player> sortedPlayers = new ArrayList<Player>();
        for(Integer location : hsLocations){
            sortedPlayers.add(unsortedPlayers.get(location));
        }
        LinkedHashMap<String, Player> newPlayerOrder = new LinkedHashMap<>();
        LinkedHashMap<String, Player> players = new LinkedHashMap<>(activeGame.getPlayers());
        LinkedHashMap<String, Player> playersBackup = new LinkedHashMap<>(activeGame.getPlayers());
        String msg = getGamePing(activeGame.getGuild(), activeGame) + " set order in the following way: \n";
        try {
            for(Player player : sortedPlayers){
                new SetOrder().setPlayerOrder(newPlayerOrder, players, player);
                msg = msg + ButtonHelper.getTrueIdentity(player, activeGame) + " \n";
            }
            if (!players.isEmpty()) {
                newPlayerOrder.putAll(players);
            }
            activeGame.setPlayers(newPlayerOrder);
        } catch (Exception e){
            activeGame.setPlayers(playersBackup);
        }
        msg = msg + "Note: the first player is not necesarily speaker/first pick. This is the general speaker order.";
        MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msg);
        
    }
    

    public static void checkEndGame(Game activeGame, Player player){
        if(player.getTotalVictoryPoints(activeGame) >= activeGame.getVp()){
            List<Button> buttons = new ArrayList<Button>();
            buttons.add(Button.success("gameEnd", "End Game"));
            buttons.add(Button.danger("deleteButtons", "Mistake, delete these"));
            MessageHelper.sendMessageToChannelWithButtons(activeGame.getMainGameChannel(), getGamePing(activeGame.getGuild(), activeGame) + " it seems like "+ButtonHelper.getIdentOrColor(player, activeGame) + " has won the game. Press the end game button when you are done with the channels, or ignore this if it was a mistake/more complicated.", buttons);
        }
    }
    public static Tile getTileFromPlanet(String planetName, Game activeGame) {
        Tile tile = null;
        for (Tile tile_ : activeGame.getTileMap().values()) {
            if (tile != null) {
                break;
            }
            for (Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                    tile = tile_;
                    break;
                }
            }
        }
        return tile;
    }
    public static String getExploreNameFromID(String cardID) {
        String card = Mapper.getExplore(cardID);
        StringBuilder sb = new StringBuilder();
        if (card != null) {
            String[] cardInfo = card.split(";");
            return cardInfo[0];
        } else {
            sb.append("Invalid ID ").append(cardID);
        }
        return sb.toString();

    }
    public static String getPlayerCCs(Player player) {
        return player.getTacticalCC()+"/"+player.getFleetCC()+"/"+player.getStrategicCC();

    }

    public static boolean mechCheck(String planetName, Game activeGame, Player player) {
        Tile tile = activeGame.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;

        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";

        if (unitHolder.getUnits() != null) {

            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }

        }
        return numMechs > 0;

    }

    public static boolean playerHasMechInSystem(Tile tile, Game activeGame, Player player) {
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder.getUnits() == null || unitHolder.getUnits().isEmpty()) continue;

            if (unitHolder.getUnits().get(mechKey) != null) {
                return true;
            }
        }
        return false;
    }
    public static boolean playerHasProductionUnitInSystem(Tile tile, Game activeGame, Player player) {
        HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey;
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder.getUnits() == null || unitHolder.getUnits().isEmpty()) continue;
            mechKey = colorID + "_sd.png";
            if (unitHolder.getUnits().get(mechKey) != null) {
                return true;
            }
            mechKey = colorID + "_csd.png";
            if (unitHolder.getUnits().get(mechKey) != null) {
                return true;
            }
            if(player.hasUnit("arborec_mech")){
                mechKey = colorID + "_mf.png";
                if (unitHolder.getUnits().get(mechKey) != null) {
                    return true;
                }
            }
            if(player.hasUnit("arborec_infantry") || player.hasTech("lw2")){
                mechKey = colorID + "_gf.png";
                if (unitHolder.getUnits().get(mechKey) != null) {
                    return true;
                }
            }
        }
        return false;
    }
    

    public static Set<Player> getNeighbouringPlayers(Game activeGame, Player player) {
        Set<Player> adjacentPlayers = new HashSet<>();
        Set<Player> realPlayers = new HashSet<>(activeGame.getPlayers().values().stream().filter(Player::isRealPlayer).toList());

        Set<Tile> playersTiles = new HashSet<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            if (FoWHelper.playerIsInSystem(activeGame, tile, player)) {
                playersTiles.add(tile);
            }
        }

        for (Tile tile : playersTiles) {
            adjacentPlayers.addAll(FoWHelper.getAdjacentPlayers(activeGame, tile.getPosition(), false));
            if (realPlayers.size() == adjacentPlayers.size()) break;
        }
        adjacentPlayers.remove(player);
        return adjacentPlayers;
    }

    public static int getNeighbourCount(Game activeGame, Player player) {
        return getNeighbouringPlayers(activeGame, player).size();
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
        HashMap<String,ActionCardModel> actionCards = Mapper.getActionCards();
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
        long totalMinutes = totalSeconds / 60;
        long totalHours = totalMinutes / 60;
        long totalDays = totalHours /24;

        long nanoSeconds = totalNanoSeconds % 1000;
        long microSeconds = totalMicroSeconds % 1000;
        long milleSeconds = totalMilliSeconds % 1000;
        long seconds = totalSeconds % 60;
        long minutes = totalMinutes % 60;
        long hours = totalHours % 24;
        long days = totalDays;

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
        int middle = turnTimesSorted.size()/2;
        if (turnTimesSorted.size()%2 == 1) {
            return turnTimesSorted.get(middle);
        } else {
            return (turnTimesSorted.get(middle - 1) + turnTimesSorted.get(middle)) / 2;
        }
    }
}
