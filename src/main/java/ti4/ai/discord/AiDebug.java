package ti4.ai.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ai.trace.AiDebugLog;
import ti4.ai.trace.AiDebugLog.Mode;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.message.MessageHelper;

class AiDebug extends GameStateSubcommand {

    private static final String MODE = "mode";

    AiDebug() {
        super("debug", "Log every AI decision with its reasons, privately or in a thread of this game", true, false);
        addOptions(new OptionData(OptionType.STRING, MODE, "Where the AI decisions go")
                .setRequired(true)
                .addChoice("Off", Mode.OFF.id())
                .addChoice("Bot log only", Mode.LOG.id())
                .addChoice("Public thread in this game (reveals the AI's hidden cards)", Mode.PUBLIC.id()));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!AiCommandSupport.mayManage(event, game)) {
            MessageHelper.replyToMessage(event, AiCommandSupport.notAllowed());
            return;
        }
        Mode mode = Mode.of(event.getOption(MODE, Mode.OFF.id(), OptionMapping::getAsString));
        if (mode == Mode.PUBLIC && game.isFowMode()) {
            MessageHelper.replyToMessage(
                    event, "A public debug thread would show the AI's hidden state to everyone in a Fog of War game.");
            return;
        }
        AiDebugLog.setMode(game, mode);
        if (mode == Mode.PUBLIC) {
            AiDebugLog.withThread(
                    game,
                    thread -> MessageHelper.sendMessageToChannel(
                            thread,
                            "AI debug log: every AI decision in " + game.getName()
                                    + " is posted here with its reasons, including what the AI knows about its own"
                                    + " hand and secrets."));
        }
        MessageHelper.replyToMessage(event, confirmation(game, mode));
    }

    private static String confirmation(Game game, Mode mode) {
        return switch (mode) {
            case OFF -> "AI debug logging is off for " + game.getName() + ".";
            case LOG -> "Every AI decision in " + game.getName() + " is now written to the bot log.";
            case PUBLIC ->
                "Every AI decision in " + game.getName() + " is now written to the bot log and posted to the "
                        + game.getName() + "-ai-debug thread.";
        };
    }
}
