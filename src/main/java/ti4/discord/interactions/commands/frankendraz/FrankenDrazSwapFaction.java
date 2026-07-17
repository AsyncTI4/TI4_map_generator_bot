package ti4.discord.interactions.commands.frankendraz;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class FrankenDrazSwapFaction extends GameStateSubcommand {

    FrankenDrazSwapFaction() {
        super("swap_faction", "Swap out a drafted faction", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction to remove")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION2, "Faction to add")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Player faction or color")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String removeFaction = FrankenDrazFactionHelper.resolveFaction(
                event.getOption(Constants.FACTION, null, OptionMapping::getAsString), event);
        String addFaction = FrankenDrazFactionHelper.resolveFaction(
                event.getOption(Constants.FACTION2, null, OptionMapping::getAsString), event);
        if (removeFaction == null
                || addFaction == null
                || !FrankenDrazFactionHelper.canManagePostDraftComponents(game, player, event)) {
            return;
        }

        FrankenDrazFactionHelper.ComponentChange removeChange =
                FrankenDrazFactionHelper.removeFactionComponents(game, player, removeFaction);
        FrankenDrazFactionHelper.ComponentChange addChange =
                FrankenDrazFactionHelper.addFactionComponents(game, player, addFaction);
        MessageHelper.sendMessageToEventChannel(
                event,
                FrankenDrazFactionHelper.buildSwapMessage(player, removeFaction, addFaction, removeChange, addChange));
    }
}
