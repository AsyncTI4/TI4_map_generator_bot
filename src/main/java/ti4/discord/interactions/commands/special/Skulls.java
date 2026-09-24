package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;

class Skulls extends GameStateSubcommand {

    Skulls() {
        super(Constants.SKULLS, "Specify how many skulls to have next to your name (max 6)", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AMOUNT, "skull count (max 6)").setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        Game game = getGame();
        game.setStoredValue("skulls", "yes");
        game.setStoredValue(
                player.getFaction() + "skulls",
                event.getOption(Constants.AMOUNT).getAsInt() + "");
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return false;
    }
}
