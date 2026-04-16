package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.LocalDevelopmentSampleGameService;
import ti4.service.game.RecreateGameService;

class LocalDevelopment extends Subcommand {

    private static final String ACTION_CREATE = "create";
    private static final String ACTION_CLEAN = "clean";

    LocalDevelopment() {
        super(Constants.LOCAL_DEV, "Manage local development test games.");
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION, "Local development action")
                .setRequired(true)
                .addChoice("Create", ACTION_CREATE)
                .addChoice("Clean", ACTION_CLEAN));
        addOptions(new OptionData(OptionType.STRING, Constants.SOURCE, "Source game name").setRequired(false));
        addOptions(
                new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm cleanup").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            MessageHelper.replyToMessage(event, "This command must be run in a server.");
            return;
        }

        String action = event.getOption(Constants.ACTION, ACTION_CREATE, OptionMapping::getAsString);
        if (ACTION_CLEAN.equals(action)) {
            String confirm = event.getOption(Constants.CONFIRM, "", OptionMapping::getAsString);
            if (!"YES".equalsIgnoreCase(confirm)) {
                MessageHelper.replyToMessage(event, "Cleanup requires `confirm: YES`.");
                return;
            }
            var result = LocalDevelopmentSampleGameService.cleanTestGames(guild);
            MessageHelper.replyToMessage(event, result.getSummary());
            return;
        }

        String sourceGame = event.getOption(
                Constants.SOURCE,
                LocalDevelopmentSampleGameService.DEFAULT_SOURCE_GAME_NAME,
                OptionMapping::getAsString);
        String result = LocalDevelopmentSampleGameService.createAndRecreateTestGame(guild, event.getUser().getId(), sourceGame);
        if (result == null) {
            MessageHelper.replyToMessage(
                    event, "Failed to create a local development test game from `" + sourceGame + "`.");
            return;
        }
        MessageHelper.replyToMessage(event, result);
    }
}
