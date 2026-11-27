package ti4.discord.interactions.commands.homebrew.lost_legacies;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.HydromorphicHelper;
import ti4.message.MessageHelper;

class HydromorphicDraw extends GameStateSubcommand {
    HydromorphicDraw() {
        super("hc_draw", "Draw Hydromorphic cards", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Cards to draw, default 1, max 10")
                .setRequiredRange(1, 10));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int count = Math.clamp(event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt), 1, 10);
        int drawn = 0;
        for (int index = 0; index < count; index++) {
            if (game.drawHydromorphicCard(player) != null) {
                drawn++;
            }
        }
        if (drawn == 0) {
            MessageHelper.sendMessageToEventChannel(event, "No Hydromorphic cards remain in the deck.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " drew " + drawn + " Hydromorphic card" + (drawn == 1 ? "." : "s."));
        HydromorphicHelper.sendHydromorphicInfo(game, player);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
