package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.RelicHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class RelicSendFragments extends GameStateSubcommand {

    public RelicSendFragments() {
        super(Constants.SEND_FRAGMENT, "Send a number of relic fragments (default 1) to another player", true, true);
        addOptions(
                new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.")
                        .setAutoComplete(true)
                        .setRequired(true),
                new OptionData(
                                OptionType.STRING,
                                Constants.TARGET_FACTION_OR_COLOR,
                                "Faction or Color you are sending to.")
                        .setAutoComplete(true)
                        .setRequired(true),
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color (defaults to you)")
                        .setAutoComplete(true),
                new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of fragments (default 1)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player sender = getPlayer();
        Player receiver = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (receiver == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }
        String trait = event.getOption(Constants.TRAIT, null, OptionMapping::getAsString);
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        ButtonHelperAbilities.pillageCheck(sender, game);
        ButtonHelperAbilities.pillageCheck(receiver, game);
        RelicHelper.sendFrags(event, sender, receiver, trait, count, game);
    }
}
