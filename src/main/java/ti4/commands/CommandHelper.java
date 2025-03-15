package ti4.commands;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.manage.GameManager;
import ti4.service.game.GameNameService;

@UtilityClass
public class CommandHelper {

    public static List<Choice> toChoices(String... values) {
        return toChoices(Arrays.asList(values));
    }

    public static List<Choice> toChoices(List<String> values) {
        return values.stream().map(v -> new Choice(v, v)).toList();
    }

    public static boolean acceptIfValidGame(SlashCommandInteractionEvent event, boolean checkChannel, boolean checkPlayer) {
        var gameName = GameNameService.getGameName(event);
        var managedGame = GameManager.getManagedGame(gameName);
        if (managedGame == null) {
            event.getHook().editOriginal("'" + event.getFullCommandName() + "' command canceled. Game name '" + gameName + "' is not valid. " +
                "Execute command in correctly named channel that starts with the game name. For example, for game `pbd123`, the channel name should start with `pbd123-`")
                .queue();
            return false;
        }
        if (checkChannel && !event.getChannel().getName().startsWith(managedGame.getName() + "-")) {
            event.getHook().editOriginal("'" + event.getFullCommandName() + "' can only be executed in a game channel.").queue();
            return false;
        }
        if (checkPlayer && getPlayerFromEvent(managedGame.getGame(), event) == null) {
            event.getHook().editOriginal("Command must be ran by a player in the game, please use `/game join gameName` or `/special2 setup_neutral_player`.").queue();
            return false;
        }
        return true;
    }

    /**
     * Supported inputs include:
     * <ul>
     * <li>id: 228999251328368640
     * <li>username: Jazzxhands
     * <li>autocomplete: No Color / No Faction / Jazzxhands
     * </ul>
     */
    @Nullable
    public static Player getPlayerFromReplaceEvent(Game game, GenericCommandInteractionEvent event) {
        String nameOrID = event.getOption(Constants.PLAYER_FACTION).getAsString();
        int replaceIndex = nameOrID.lastIndexOf(" / ");
        String name = nameOrID.substring(replaceIndex < 0 ? 0 : replaceIndex + 3);

        for (Player player : game.getPlayers().values()) {
            if (player.getUserID().equals(nameOrID) || player.getUserName().equals(name)) {
                return player;
            }
        }
        return null;
    }

    @Nullable
    public static Player getPlayerFromEvent(Game game, GenericCommandInteractionEvent event) {
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            return game.getPlayer(playerID);
        }

        OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
        if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            return getPlayerByFactionColor(factionColor, game);
        }
        return getPlayerFromGame(game, event.getMember(), event.getUser().getId());
    }

    @Nullable
    public static Player getPlayerFromGame(Game game, Member member, String userId) {
        if (!game.isCommunityMode() || member == null) {
            return game.getPlayer(userId);
        }

        Collection<Player> players = game.getPlayers().values();
        List<Role> roles = member.getRoles();
        for (Player player : players) {
            if (roles.contains(player.getRoleForCommunity()) || player.getTeamMateIDs().contains(member.getUser().getId())) {
                return player;
            }
        }
        return null;
    }

    @Nullable
    public static Player getPlayerByFactionColor(String factionColor, Game game) {
        factionColor = StringUtils.substringBefore(factionColor, " "); // TO HANDLE UNRESOLVED AUTOCOMPLETE
        factionColor = AliasHandler.resolveFaction(factionColor);
        for (Player player_ : game.getPlayers().values()) {
            if (Objects.equals(factionColor, player_.getFaction()) ||
                Objects.equals(factionColor, player_.getColor())) {
                return player_;
            }
        }
        return null;
    }

    @Nullable
    public static Player getOtherPlayerFromEvent(Game game, SlashCommandInteractionEvent event) {
        OptionMapping playerOption = event.getOption(Constants.TARGET_PLAYER);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            return game.getPlayer(playerID);
        }

        OptionMapping factionColorOption = event.getOption(Constants.TARGET_FACTION_OR_COLOR);
        if (factionColorOption != null) {
            String factionColor = AliasHandler.resolveColor(factionColorOption.getAsString().toLowerCase());
            return getPlayerByFactionColor(factionColor, game);
        }

        return null;
    }

    public static boolean acceptIfHasRoles(SlashCommandInteractionEvent event, List<Role> acceptedRoles) {
        if (hasRole(event, acceptedRoles)) {
            return true;
        }
        var acceptRolesStr = acceptedRoles.stream().map(Role::getName).distinct().collect(Collectors.joining(", "));
        event.getHook().editOriginal("You are not authorized to use this command. You must have one of the following roles: " + acceptRolesStr).queue();
        return false;
    }

   public static boolean hasRole(SlashCommandInteractionEvent event, List<Role> acceptedRoles) {
        Member member = event.getMember();
        if (member == null) {
            return false;
        }
        List<Role> roles = member.getRoles();
        for (Role role : acceptedRoles) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public static String getHeaderText(GenericInteractionCreateEvent event) {
        if (event instanceof SlashCommandInteractionEvent) {
            return " used `" + ((SlashCommandInteractionEvent) event).getCommandString() + "`";
        }
        if (event instanceof ButtonInteractionEvent) {
            return " pressed `" + ((ButtonInteractionEvent) event).getButton().getId() + "`";
        }
        return " used the force";
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
            Player foundPlayer = getPlayerFromGame(game, event.getMember(), event.getUser().getId());
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

    public Tile getTile(SlashCommandInteractionEvent event, Game game) {
        String tileName = StringUtils.substringBefore(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase(), " ");
        return TileHelper.getTile(event, tileName, game);
    }

    public Tile getTile(SlashCommandInteractionEvent event, Game game, String tileName) {
        tileName = StringUtils.substringBefore(tileName.toLowerCase(), " ");
        return TileHelper.getTile(event, tileName, game);
    }
}
