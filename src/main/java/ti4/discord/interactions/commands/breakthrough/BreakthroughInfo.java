package ti4.discord.interactions.commands.breakthrough;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.tyris.TyrisBreakthroughButtonHandler;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;

class BreakthroughInfo extends GameStateSubcommand {

    BreakthroughInfo() {
        super(Constants.INFO, "Send breakthrough information to your Cards Info channel", true, true);
        addOption(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction to see breakthroughs", false, true);
    }

    public void execute(SlashCommandInteractionEvent event) {
        Player p2 = CommandHelper.getOtherPlayerFromEvent(getGame(), event);
        if (p2 == null) p2 = getPlayer();
        BreakthroughCommandHelper.sendBreakthroughInfo(event, getGame(), getPlayer(), p2);
        if (p2.hasUnlockedBreakthrough("tyrisbt")) {
            TyrisBreakthroughButtonHandler.sendNonLinearTimeProgressionInfo(getGame(), p2, getPlayer());
        }
    }
}
