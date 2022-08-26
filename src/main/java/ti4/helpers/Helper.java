package ti4.helpers;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.map.*;
import ti4.message.BotLogger;

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
        Collection<Player> players = map.getPlayers().values();
        if (!map.isCommunityMode()) {
            Player player = map.getPlayer(userID);
            if (player != null) return player;
            return initialPlayer;
        }
        Member member = event.getMember();
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
            if (foundPlayer != null){
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

    public static String getGamePing(SlashCommandInteractionEvent event, Map activeMap) {
        String categoryForPlayers = "";
        Guild guild = event.getGuild();
        if (guild != null) {
            for (Role role : guild.getRoles()) {
                if (activeMap.getName().equals(role.getName().toLowerCase())) {
                    categoryForPlayers = role.getAsMention();
                }
            }
        }
        return categoryForPlayers;
    }

    public static String getPlayerPing(SlashCommandInteractionEvent event, Player player) {
        User userById = event.getJDA().getUserById(player.getUserID());
        if (userById == null) {
            return "";
        }
        return userById.getAsMention();
    }
}
