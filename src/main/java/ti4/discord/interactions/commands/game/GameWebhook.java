package ti4.discord.interactions.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.message.MessageHelper;
import ti4.service.webhook.GameWebhookConfig;

class GameWebhook extends GameStateSubcommand {
    private static final String OPTION_URL = "url";
    private static final String OPTION_ENABLED = "enabled";
    private static final String OPTION_ALLOW_FOW = "allow_fow";
    private static final String OPTION_CLEAR = "clear";

    public GameWebhook() {
        super("webhook", "Set or inspect game webhook notifications", true, false);
        addOptions(new OptionData(OptionType.STRING, OPTION_URL, "Webhook URL to send game event notifications to"));
        addOptions(new OptionData(OptionType.BOOLEAN, OPTION_ENABLED, "Enable or disable webhook notifications"));
        addOptions(new OptionData(
                OptionType.BOOLEAN, OPTION_ALLOW_FOW, "Allow notifications for FoW games (default false for privacy)"));
        addOptions(new OptionData(OptionType.BOOLEAN, OPTION_CLEAR, "Clear all webhook configuration"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean clear = event.getOption(OPTION_CLEAR, false, OptionMapping::getAsBoolean);
        String webhookUrl = event.getOption(OPTION_URL, null, OptionMapping::getAsString);
        OptionMapping enabledOption = event.getOption(OPTION_ENABLED);
        OptionMapping allowFowOption = event.getOption(OPTION_ALLOW_FOW);

        if (clear) {
            GameWebhookConfig.clearWebhookConfig(game);
            MessageHelper.sendMessageToEventChannel(event, "Webhook configuration cleared for this game.");
            return;
        }

        boolean updated = false;
        if (webhookUrl != null) {
            String trimmedWebhookUrl = webhookUrl.trim();
            if (!GameWebhookConfig.isWebhookUrlValid(trimmedWebhookUrl)) {
                MessageHelper.sendMessageToEventChannel(event, "Invalid webhook URL. Only http(s) URLs are allowed.");
                return;
            }
            GameWebhookConfig.setWebhookUrl(game, trimmedWebhookUrl);
            updated = true;
        }

        if (enabledOption != null) {
            GameWebhookConfig.setWebhookEnabled(game, enabledOption.getAsBoolean());
            updated = true;
        }
        if (allowFowOption != null) {
            GameWebhookConfig.setFowAllowed(game, allowFowOption.getAsBoolean());
            updated = true;
        }

        String urlDisplay = GameWebhookConfig.getWebhookUrl(game).orElse("<not set>");
        String response = "Webhook config for `" + game.getName() + "`"
                + (updated ? " updated.\n" : ":\n")
                + "- URL: " + urlDisplay + "\n"
                + "- Enabled: " + GameWebhookConfig.isWebhookEnabled(game) + "\n"
                + "- Allow FoW: " + GameWebhookConfig.isFowAllowed(game);
        MessageHelper.sendMessageToEventChannel(event, response);
    }
}
