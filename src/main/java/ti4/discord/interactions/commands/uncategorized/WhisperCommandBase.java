package ti4.discord.interactions.commands.uncategorized;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.fow.WhisperService;

abstract class WhisperCommandBase extends GameStateCommand {

    private final String name;
    private final String description;

    protected WhisperCommandBase(String name, String description) {
        super(true, true);
        this.name = name;
        this.description = description;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.TO, "Faction or color to whisper to")
                        .setAutoComplete(true)
                        .setRequired(true),
                new OptionData(OptionType.STRING, Constants.MESSAGE, "Message to send"),
                new OptionData(OptionType.BOOLEAN, Constants.FUTURE, "When true, deliver at start of target's turn"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player sender = getPlayer();
        if (!sender.isRealPlayer()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This command only works on real players.");
            return;
        }

        Game game = getGame();
        String to =
                event.getOption(Constants.TO, "", OptionMapping::getAsString).toLowerCase();
        Player receiver = WhisperService.getPlayerByColorOrFaction(game, to);
        if (receiver == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Player not found: " + to);
            return;
        }

        String message = event.getOption(Constants.MESSAGE, "", OptionMapping::getAsString);
        boolean future = event.getOption(Constants.FUTURE, false, OptionMapping::getAsBoolean);
        if (future) {
            WhisperService.whisperToFutureColorOrFaction(game, message, sender, receiver, event.getChannel());
            return;
        }

        WhisperService.sendWhisper(game, sender, receiver, message, "n", event.getMessageChannel(), event.getGuild());
    }
}
