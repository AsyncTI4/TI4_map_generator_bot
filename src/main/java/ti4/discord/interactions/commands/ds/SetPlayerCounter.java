package ti4.discord.interactions.commands.ds;

import java.util.function.ObjIntConsumer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.BlueReverieHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class SetPlayerCounter extends GameStateSubcommand {

    private final String label;
    private final ObjIntConsumer<Player> setter;
    private final boolean honorCounter;

    private SetPlayerCounter(
            String name, String description, String label, ObjIntConsumer<Player> setter, boolean honorCounter) {
        super(name, description, true, true);
        this.label = label;
        this.setter = setter;
        this.honorCounter = honorCounter;
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count").setRequired(true));
    }

    static SetPlayerCounter honor() {
        return new SetPlayerCounter(Constants.HONOR_COUNT, "Set Honor amount", "Honor", Player::setHonorCounter, true);
    }

    static SetPlayerCounter dishonor() {
        return new SetPlayerCounter(
                Constants.DISHONOR_COUNT, "Set Dishonor amount", "Dishonor", Player::setDishonorCounter, true);
    }

    static SetPlayerCounter starBalance() {
        return new SetPlayerCounter(
                "starbalance_count", "Set star balance amount", "Star Balance", Player::setStarbalanceCounter, false);
    }

    static SetPlayerCounter steelBalance() {
        return new SetPlayerCounter(
                "steelbalance_count",
                "Set steel balance amount",
                "Steel Balance",
                Player::setSteelbalanceCounter,
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = Math.max(event.getOption("count").getAsInt(), 0);
        Player player = getPlayer();
        setter.accept(player, count);
        if (!honorCounter) {
            BlueReverieHelper.checkXinHarmony(getGame());
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set " + label + " count to " + count + ".");
        if (honorCounter) {
            ButtonHelperFactionSpecific.correctHonorAbilities(player, getGame());
        }
    }
}
