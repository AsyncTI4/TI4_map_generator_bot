package ti4.commands.tokens;

import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.Nullable;
import ti4.commands.units.MoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

public class AddCC extends AddRemoveToken {
    @Override
    void parsingForTile(SlashCommandInteractionEvent event, List<String> colors, Tile tile, Game game) {
        boolean usedTactics = false;
        for (String color : colors) {
            OptionMapping option = event.getOption(Constants.CC_USE);
            if (option != null && !usedTactics) {
                usedTactics = true;
                String value = option.getAsString().toLowerCase();
                switch (value) {
                    case "t/tactics", "t", "tactics", "tac", "tact" -> MoveUnits.removeTacticsCC(event, color, tile, game);
                }
            }
            addCC(event, color, tile);
            Helper.isCCCountCorrect(event, game, color);
        }
    }

    public static void addCC(GenericInteractionCreateEvent event, String color, Tile tile) {
        addCC(event, color, tile, true);
    }

    public static void addCC(SlashCommandInteractionEvent event, String color, Tile tile) {
        addCC(event, color, tile, true);
    }

    public static void addCC(GenericInteractionCreateEvent event, String color, Tile tile, boolean ping) {
        String gameName = event.getChannel().getName();
        gameName = gameName.replace(Constants.CARDS_INFO_THREAD_PREFIX, "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Game game = GameManager.getGame(gameName);
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        if (game.isFowMode() && ping) {
            String colorMention = Emojis.getColorEmojiWithName(color);
            FoWHelper.pingSystem(game, event, tile.getPosition(), colorMention + " has placed a token in the system");
        }
        tile.addCC(ccID);
    }

    public static void addCC(SlashCommandInteractionEvent event, String color, Tile tile, boolean ping) {
        Game game = UserGameContextManager.getContextGame(event.getUser().getId());
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        if (game.isFowMode() && ping) {
            String colorMention = Emojis.getColorEmojiWithName(color);
            FoWHelper.pingSystem(game, event, tile.getPosition(), colorMention + " has placed a token in the system");
        }
        tile.addCC(ccID);
    }

    public static boolean hasCC(@Nullable GenericInteractionCreateEvent event, String color, Tile tile) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null && event != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Command Counter: " + color + " is not valid and not supported.");
        }
        return tile.hasCC(ccID);
    }

    public static boolean hasCC(String color, Tile tile) {
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            return false;
        }
        return tile.hasCC(ccID);
    }

    public static boolean hasCC(Player player, Tile tile) {
        String color = player.getColor();
        String ccID = Mapper.getCCID(color);
        String ccPath = tile.getCCPath(ccID);
        if (ccPath == null) {
            return false;
        }
        return tile.hasCC(ccID);
    }

    @Override
    public String getDescription() {
        return "Add CC to tile/system";
    }

    @Override
    public String getName() {
        return Constants.ADD_CC;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getName(), this.getDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.CC_USE, "Type tactics or t, retreat, reinforcements or r").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true)));
    }
}
