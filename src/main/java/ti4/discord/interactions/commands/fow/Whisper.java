package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.fow.WhisperService;

class Whisper extends GameStateSubcommand {

    Whisper() {
        super(Constants.WHISPER, "Send a private message to a player in fog mode", true, true);
        addOptions(CommonOptions.targetFactionOrColor("Faction or Color to which you send the message"));
        addOptions(new OptionData(OptionType.STRING, Constants.MSG, "Message to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ANON, "Send anonymously").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "This game is not fog mode, and should not use this command."
                            + " Instead whisper by beginning your message with to[color] or to[faction] from inside your `#cards-info` thread (for instance, saying `toblue hi`).");
            return;
        }

        Player otherPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (otherPlayer == null) return;

        String msg = event.getOption(Constants.MSG).getAsString();
        String anonY = event.getOption(Constants.ANON, "", OptionMapping::getAsString);
        WhisperService.sendWhisper(
                game, getPlayer(), otherPlayer, msg, anonY, event.getMessageChannel(), event.getGuild());
    }
}
