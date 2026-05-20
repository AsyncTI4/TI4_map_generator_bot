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

class FrankenDrazRemoveFaction extends GameStateSubcommand {

    FrankenDrazRemoveFaction() {
        super("remove_faction", "Remove a faction's components", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction to remove")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Player faction or color")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String faction = FrankenDrazFactionHelper.resolveFaction(
                event.getOption(Constants.FACTION, null, OptionMapping::getAsString), event);
        if (faction == null || !FrankenDrazFactionHelper.canManagePostDraftComponents(game, player, event)) {
            return;
        }

        FrankenDrazFactionHelper.ComponentChange change =
                FrankenDrazFactionHelper.removeFactionComponents(game, player, faction);
        MessageHelper.sendMessageToEventChannel(
                event, FrankenDrazFactionHelper.buildRemoveMessage(player, faction, change.removed()));
    }
}
