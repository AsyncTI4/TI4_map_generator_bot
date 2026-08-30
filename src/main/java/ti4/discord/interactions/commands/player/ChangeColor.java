package ti4.discord.interactions.commands.player;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;

class ChangeColor extends GameStateSubcommand {

    public ChangeColor() {
        super(Constants.CHANGE_COLOR, "Player Color Change", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        // In FoW games, color changes are routed through the GM (from the GM channel only) rather
        // than left to individual players - mirrors the GM-only guard used by the spin commands.
        if (game.isFowMode()) {
            Player invoker = CommandHelper.getPlayerFromGame(
                    game, event.getMember(), event.getUser().getId());
            boolean inGmChannel =
                    event.getChannelId().equals(GMService.getGMChannel(game).getId());
            if (invoker == null || !invoker.isGM() || !inGmChannel) {
                MessageHelper.sendMessageToEventChannel(
                        event,
                        "Async staff have restricted color changes in Fog of War games to GMs, from the GM channel only.");
                return;
            }
        }

        String newColor = AliasHandler.resolveColor(
                event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isValidColor(newColor)) {
            MessageHelper.sendMessageToEventChannel(event, "Color not valid");
            return;
        }

        Player player = getPlayer();
        Map<String, Player> players = game.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (newColor.equals(playerInfo.getColor())) {
                    MessageHelper.sendMessageToEventChannel(
                            event,
                            (game.isFowMode() ? "Someone" : "Player:" + playerInfo.getUserName())
                                    + " already uses color:" + newColor);
                    return;
                }
            }
        }

        if (!ColorChangeHelper.isColorAllowedForPlayer(newColor, player)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "You cannot use this color. It has been made solely for its creator's usage. Sorry!");
            return;
        }

        String oldColor = player.getColor();
        String oldRepresentation = player.getRepresentationNoPing();
        ColorChangeHelper.changePlayerColor(game, player, oldColor, newColor);
        MessageHelper.sendMessageToEventChannel(
                event, oldRepresentation + " changed color to " + player.getRepresentationNoPing() + ".");
        ButtonHelper.resolveColorChangeClashChecker(game, player);
    }
}
