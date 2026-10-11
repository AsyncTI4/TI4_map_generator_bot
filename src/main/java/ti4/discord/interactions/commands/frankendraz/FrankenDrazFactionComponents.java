package ti4.discord.interactions.commands.frankendraz;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class FrankenDrazFactionComponents extends GameStateSubcommand {

    private final boolean add;

    private FrankenDrazFactionComponents(String name, String verb, boolean add) {
        super(name, verb + " a faction's components", true, true);
        this.add = add;
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction to " + verb.toLowerCase())
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(CommonOptions.factionColor("Player faction or color"));
    }

    static FrankenDrazFactionComponents add() {
        return new FrankenDrazFactionComponents("add_faction", "Add", true);
    }

    static FrankenDrazFactionComponents remove() {
        return new FrankenDrazFactionComponents("remove_faction", "Remove", false);
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
        String message;
        if (add) {
            FrankenDrazFactionHelper.ComponentChange change =
                    FrankenDrazFactionHelper.addFactionComponents(game, player, faction);
            message = FrankenDrazFactionHelper.buildAddMessage(player, faction, change.added());
        } else {
            FrankenDrazFactionHelper.ComponentChange change =
                    FrankenDrazFactionHelper.removeFactionComponents(game, player, faction);
            message = FrankenDrazFactionHelper.buildRemoveMessage(player, faction, change.removed());
        }
        MessageHelper.sendMessageToEventChannel(event, message);
    }
}
