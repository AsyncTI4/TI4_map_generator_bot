package ti4.discord.interactions.commands.frankendraz;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.draft.FrankenDrazDraft;
import ti4.game.Game;
import ti4.message.MessageHelper;

class FrankenDrazSetKeptComponentLimit extends GameStateSubcommand {
    private static final String UNLIMITED = "unlimited";

    FrankenDrazSetKeptComponentLimit() {
        super("set_kept_component_limit", "Set whether FrankenDraz kept components are unlimited", true, false);
        addOptions(new OptionData(
                        OptionType.BOOLEAN,
                        UNLIMITED,
                        "True to remove kept component limits; false to use powered franken limits")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!(game.getActiveBagDraft() instanceof FrankenDrazDraft)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "This command can only be used in a FrankenDraz draft. Please run after draft has begun.");
            return;
        }

        boolean unlimited = event.getOption(UNLIMITED, Boolean.FALSE, OptionMapping::getAsBoolean);
        if (unlimited) {
            game.setStoredValue(FrankenDrazDraft.UNLIMITED_KEPT_COMPONENTS_KEY, "true");
            MessageHelper.sendMessageToEventChannel(event, "FrankenDraz kept component limits removed.");
        } else {
            game.removeStoredValue(FrankenDrazDraft.UNLIMITED_KEPT_COMPONENTS_KEY);
            MessageHelper.sendMessageToEventChannel(
                    event, "FrankenDraz kept component limits now use powered franken limits.");
        }
    }
}
