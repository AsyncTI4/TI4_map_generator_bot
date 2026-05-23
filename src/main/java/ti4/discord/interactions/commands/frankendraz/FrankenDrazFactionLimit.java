package ti4.discord.interactions.commands.frankendraz;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.draft.FrankenDrazDraft;
import ti4.game.Game;
import ti4.message.MessageHelper;

class FrankenDrazFactionLimit extends GameStateSubcommand {
    private static final String LIMIT = "limit";

    FrankenDrazFactionLimit() {
        super("faction_limit", "Set the number of factions each player drafts", true, false);
        addOptions(new OptionData(OptionType.INTEGER, LIMIT, "Faction draft limit").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.getActiveBagDraft() != null && !(game.getActiveBagDraft() instanceof FrankenDrazDraft)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "This command can only be used before a draft starts or in a FrankenDraz draft.");
            return;
        }

        int limit = event.getOption(LIMIT).getAsInt();
        if (limit < 1) {
            MessageHelper.sendMessageToEventChannel(event, "Faction limit must be at least 1.");
            return;
        }

        game.setStoredValue(FrankenDrazDraft.FACTION_LIMIT_KEY, String.valueOf(limit));
        MessageHelper.sendMessageToEventChannel(
                event, "FrankenDraz faction draft limit set to " + limit + " per player.");
    }
}
