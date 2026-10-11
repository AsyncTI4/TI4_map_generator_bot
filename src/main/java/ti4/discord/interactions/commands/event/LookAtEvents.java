package ti4.discord.interactions.commands.event;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class LookAtEvents extends GameStateSubcommand {

    private final boolean fromTop;
    private final String label;

    private LookAtEvents(String name, String description, boolean fromTop, String label) {
        super(name, description, true, true);
        this.fromTop = fromTop;
        this.label = label;
        addOption(OptionType.INTEGER, Constants.COUNT, "Number of events to look at");
    }

    static LookAtEvents top() {
        return new LookAtEvents(Constants.LOOK_AT_TOP, "Look at top Event from deck", true, "Top");
    }

    static LookAtEvents bottom() {
        return new LookAtEvents(Constants.LOOK_AT_BOTTOM, "Look at bottom Event from deck", false, "Bottom");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);

        StringBuilder sb = new StringBuilder();
        sb.append("-----------\n");
        sb.append("Game: ").append(game.getName()).append('\n');
        sb.append(event.getUser().getAsMention()).append('\n');
        sb.append('`').append(event.getCommandString()).append('`').append('\n');
        if (count > 1) {
            sb.append("__**").append(label).append(' ').append(count).append(" events:**__\n");
        } else {
            sb.append("__**").append(label).append(" event:**__\n");
        }
        for (int i = 0; i < count; i++) {
            String eventID = fromTop ? game.lookAtTopEvent(i) : game.lookAtBottomEvent(i);
            sb.append(i + 1).append(": ");
            sb.append(Mapper.getEvent(eventID).getRepresentation());
            sb.append('\n');
        }
        sb.append("-----------\n");

        Player player = getPlayer();
        User userById = event.getJDA().getUserById(player.getUserID());
        if (userById != null) {
            if (game.isCommunityMode() && player.getPrivateChannel() != null) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), sb.toString());
            } else {
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb.toString());
            }
        } else {
            MessageHelper.sendMessageToUser(sb.toString(), event);
        }
    }
}
