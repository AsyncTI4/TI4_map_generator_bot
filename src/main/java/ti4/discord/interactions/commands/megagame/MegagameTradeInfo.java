package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.megagame.MegagameTradeInfoService;

class MegagameTradeInfo extends GameStateSubcommand {

    private static final String PUBLIC = "public";

    MegagameTradeInfo() {
        super("tradeinfo", "Show a faction's public tradables, with a button to start a trade", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or colour, default you")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, PUBLIC, "True to show it to everyone in this channel"));
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return !isPublic(event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!MegagameTradeInfoService.isAvailable(game)) {
            reply(event, "Trade info is only available in megagames (tag `megagame`) outside Fog of War.");
            return;
        }
        Player viewer = CommandHelper.getPlayerFromGame(
                game, event.getMember(), event.getUser().getId());
        OptionMapping factionOption = event.getOption(Constants.FACTION_COLOR);
        Player target = factionOption == null ? viewer : game.getPlayerFromColorOrFaction(factionOption.getAsString());
        if (target == null || !target.isRealPlayer()) {
            reply(event, "Could not find that faction or colour in this game.");
            return;
        }
        if (isPublic(event)) {
            MegagameTradeInfoService.sendPublicTradeInfo(event.getChannel(), game, target);
        } else {
            MegagameTradeInfoService.sendPrivateTradeInfo(event.getHook(), game, viewer, target);
        }
    }

    private static boolean isPublic(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(PUBLIC);
        return option != null && option.getAsBoolean();
    }

    private static void reply(SlashCommandInteractionEvent event, String message) {
        if (isPublic(event)) {
            MessageHelper.sendMessageToEventChannel(event, message);
            return;
        }
        event.getHook().setEphemeral(true).sendMessage(message).queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
