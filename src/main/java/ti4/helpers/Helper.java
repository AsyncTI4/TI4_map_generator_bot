package ti4.helpers;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.MapGenerator;
import ti4.ResourceHelper;
import ti4.commands.tokens.AddCC;
import ti4.generator.Mapper;
import ti4.map.*;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

public class Helper {

    @CheckForNull
    public static Player getGamePlayer(Map map, Player initialPlayer, SlashCommandInteractionEvent event, String userID) {
        return getGamePlayer(map, initialPlayer, event.getMember(), userID);
    }

    @CheckForNull
    public static Player getGamePlayer(Map map, Player initialPlayer, Member member, String userID) {
        Collection<Player> players = map.getPlayers().values();
        if (!map.isCommunityMode()) {
            Player player = map.getPlayer(userID);
            if (player != null) return player;
            return initialPlayer;
        }
        if (member == null) {
            Player player = map.getPlayer(userID);
            if (player != null) return player;
            return initialPlayer;
        }
        java.util.List<Role> roles = member.getRoles();
        for (Player player : players) {
            if (roles.contains(player.getRoleForCommunity())) {
                return player;
            }
        }
        return initialPlayer != null ? initialPlayer : map.getPlayer(userID);
    }

    @CheckForNull
    public static Player getPlayer(Map activeMap, Player player, SlashCommandInteractionEvent event) {
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            if (activeMap.getPlayer(playerID) != null) {
                player = activeMap.getPlayers().get(playerID);
            } else {
                player = null;
            }
        } else if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeMap.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) ||
                        Objects.equals(factionColor, player_.getColor())) {
                    player = player_;
                    break;
                }
            }
        }
        return player;
    }

    @CheckForNull
    public static String getColor(Map activeMap, SlashCommandInteractionEvent event) {
        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
        if (factionColorOption != null) {
            String colorFromString = getColorFromString(activeMap, factionColorOption.getAsString());
            if (Mapper.isColorValid(colorFromString)) {
                return colorFromString;
            }
        } else {
            String userID = event.getUser().getId();
            Player foundPlayer = activeMap.getPlayers().values().stream().filter(player -> player.getUserID().equals(userID)).findFirst().orElse(null);
            foundPlayer = Helper.getGamePlayer(activeMap, foundPlayer, event, null);
            if (foundPlayer != null) {
                return foundPlayer.getColor();
            }
        }
        return null;
    }

    @CheckForNull
    public static String getColorFromString(Map activeMap, String factionColor) {
        factionColor = AliasHandler.resolveColor(factionColor);
        factionColor = AliasHandler.resolveFaction(factionColor);
        for (Player player_ : activeMap.getPlayers().values()) {
            if (Objects.equals(factionColor, player_.getFaction()) ||
                    Objects.equals(factionColor, player_.getColor())) {
                return player_.getColor();
            }
        }
        return factionColor;
    }

    @CheckForNull
    public static String getDamagePath() {
        String tokenPath = ResourceHelper.getInstance().getResourceFromFolder("extra/", "marker_damage.png", "Could not find damage token file");
        if (tokenPath == null) {
            BotLogger.log("Could not find token: marker_damage");
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

    public static String getSCName(int sc) {
        return switch (sc) {
            case 1 -> "leadership";
            case 2 -> "diplomacy";
            case 3 -> "politics";
            case 4 -> "construction";
            case 5 -> "trade";
            case 6 -> "warfare";
            case 7 -> "technology";
            case 8 -> "imperial";
            default -> "" + sc;
        };
    }

    public static String getFactionIconFromDiscord(String faction) {
        return switch (faction) {
            case "arborec" -> "<:Arborec:946891797567799356>";
            case "argent" -> "<:Argent:946891797366472725>";
            case "cabal" -> "<:VuilRaith:946891797236441089>";
            case "empyrean" -> "<:Empyrean:946891797257404466>";
            case "ghost" -> "<:Creuss:946891797609721866>";
            case "hacan" -> "<:Hacan:946891797228060684>";
            case "jolnar" -> "<:JolNar:946891797114789918>";
            case "l1z1x" -> "<:L1Z1X:946891797219647559>";
            case "letnev" -> "<:Letnev:946891797458714704>";
            case "yssaril" -> "<:Yssaril:946891798138196008>";
            case "mahact" -> "<:Mahact:946891797274165248>";
            case "mentak" -> "<:Mentak:946891797395800084>";
            case "muaat" -> "<:Muaat:946891797177716777>";
            case "naalu" -> "<:Naalu:946891797412601926>";
            case "naaz" -> "<:NaazRokha:946891797437747200>";
            case "nekro" -> "<:Nekro:946891797681025054>";
            case "nomad" -> "<:Nomad:946891797400002561>";
            case "saar" -> "<:Saar:946891797366472735>";
            case "sardakk" -> "<:Sardakk:946891797307748436>";
            case "sol" -> "<:Sol:946891797706194995>";
            case "titans" -> "<:Titans:946891798062694400>";
            case "winnu" -> "<:Winnu:946891798050136095>";
            case "xxcha" -> "<:Xxcha:946891797639086090>";
            case "yin" -> "<:Yin:946891797475491892>";
            case "lazax" -> "<:Lazax:946891797639073884>";
            case "keleres" -> "<:Keleres:968233661654765578>";
            default -> "";
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
            case "hfrag" -> "<:HFrag:1053857012766752788>";
            case "cfrag" -> "<:CFrag:1053856733849722880>";
            case "ifrag" -> "<:IFrag:1053857037131460648>";
            case "ufrag" -> "<:UFrag:1053857056991490119>";
            case "relic" -> "<:Relic:1054075788711964784>";
            case "cultural" -> "<:Cultural:947250123333836801>";
            case "industrial" -> "<:Industrial:946892033031819305>";
            case "hazardous" -> "<:Hazardous:946892033006645318>";
            case "frontier" -> "<:Frontier:966025493805678632>";

            //CARDS
            case "sc1" -> "<:SC1:1056594715673366548>";
            case "sc2" -> "<:SC2:1056594746023366716>";
            case "sc3" -> "<:SC3:1056594774620110879>";
            case "sc4" -> "<:SC4:1056594795193172009>";
            case "sc5" -> "<:SC5:1056594816454107187>";
            case "sc6" -> "<:SC6:1056594839778623599>";
            case "sc7" -> "<:SC7:1056594860360073236>";
            case "sc8" -> "<:SC8:1056594882141098055>";
            case "actioncard" -> "<:Actioncard:1054660449515352114>";
            case "secretobjective" -> "<:Secretobjective:1054660535544729670>";
            case "public1" -> "<:Public1:1054075764510826539>";
            case "public2" -> "<:Public2:1054075738602622986>";
            case "agenda" -> "<:Agenda:1054660476874792990> ";
            case "pn" -> "<:PN:1054660504175521882>";
            
            //COMPONENTS
            case "tg" -> "<:tg:1053857635570553024>";
            case "comm" -> "<:comm:1053857614028607538>";
            case "sleeper" -> "<:Sleeper:1047871121451663371>";
            case "sleeperb" -> "<:SleeperB:1047871220831506484>";
            
            //UNITS
            case "warsun" -> "<:warsun:993064568626614375>";
            case "spacedock" -> "<:spacedock:993064508828418159>";
            case "pds" -> "<:pds:993064415639384064>";
            case "mech" -> "<:mech:993064350988390430>";
            case "infantry" -> "<:infantry:993064251994407004>";
            case "flagship" -> "<:flagship:993064196264710204>";
            case "fighter" -> "<:fighter:993064145907892284>";
            case "dreadnought" -> "<:dreadnought:993064090589216828>";
            case "destroyer" -> "<:destroyer:993063959840182323>";
            case "carrier" -> "<:carrier:993063885168967700>";
            case "cruiser" -> "<:cruiser:993063818844459098>";

            //OTHER
            case "whalpha" -> "<:WHalpha:1056593618250518529>";
            case "whbeta" -> "<:WHbeta:1056593596012302366>";
            case "whgamma" -> "<:WHgamma:1056593568766111814>";
            case "influence" -> "<:influence:959575421337358336>";
            case "resources" -> "<:resources:959575421274451998>";
            case "legendaryplanet" -> "<:Legendaryplanet:947250386375426108>";
            case "cybernetictech" -> "<:Cybernetictech:947250608149245972>";
            case "propulsiontech" -> "<:Propulsiontech:947250608145068074>";
            case "biotictech" -> "<:Biotictech:947250608107315210>";
            case "warfaretech" -> "<:Warfaretech:947250607855644743>";
            case "public1alt" -> "<:Public1Alt:1058978029243728022>";
            case "public2alt" -> "<:Public2Alt:1058977929725493398>";
            case "secretobjectivealt" -> "<:SecretobjectiveAlt:1058977803728584734>";

            default -> "";
        };
    }

    public static String getGamePing(SlashCommandInteractionEvent event, Map activeMap) {
        return getGamePing(event.getGuild(), activeMap);
    }

    public static String getGamePing(Guild guild, Map activeMap) {
        String categoryForPlayers = "";
        if (guild != null) {
            for (Role role : guild.getRoles()) {
                if (activeMap.getName().equals(role.getName().toLowerCase())) {
                    categoryForPlayers = role.getAsMention();
                }
            }
        }
        return categoryForPlayers;
    }

    public static String getPlayerPing(Player player) {
        User userById = MapGenerator.jda.getUserById(player.getUserID());
        if (userById == null) {
            return "";
        }
        return userById.getAsMention();
    }

    public static String getPlayerRepresentation(GenericCommandInteractionEvent event, Player player) {
        String text = "";
        String playerFaction = player.getFaction();
        text += Helper.getFactionIconFromDiscord(playerFaction);
        text += " " + Helper.getPlayerPing(player);
        return text;
    }

    public static void isCCCountCorrect(SlashCommandInteractionEvent event, Map map, String color) {
        int ccCount = getCCCount(map, color);
        informUserCCOverLimit(event, map, color, ccCount);
    }

    public static int getCCCount(Map map, String color) {
        int ccCount = 0;
        if (color == null){
            return 0;
        }
        HashMap<String, Tile> tileMap = map.getTileMap();
        for (java.util.Map.Entry<String, Tile> tileEntry : tileMap.entrySet()) {
            Tile tile = tileEntry.getValue();
            boolean hasCC = AddCC.hasCC(null, color, tile);
            if (hasCC) {
                ccCount++;
            }
        }
        String factionColor = AliasHandler.resolveColor(color.toLowerCase());
        factionColor = AliasHandler.resolveFaction(factionColor);
        for (Player player_ : map.getPlayers().values()) {
            if (Objects.equals(factionColor, player_.getFaction()) ||
                    Objects.equals(factionColor, player_.getColor())) {
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

    private static void informUserCCOverLimit(SlashCommandInteractionEvent event, Map map, String color, int ccCount) {
        boolean ccCountIsOver = ccCount > 16;
        if (ccCountIsOver) {
            Player player = null;
            String factionColor = AliasHandler.resolveColor(color.toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : map.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) ||
                        Objects.equals(factionColor, player_.getColor())) {
                    player = player_;
                }
            }
            String msg = getGamePing(event, map) + " ";
            if (player != null) {
                msg += getFactionIconFromDiscord(player.getFaction()) + " " + player.getFaction() + " ";
                msg += getPlayerPing(player) + " ";
            }
            msg += "(" + color + ") is over CC limit. CC used: " + ccCount;
            MessageHelper.replyToMessage(event, msg);
        }
    }
}
