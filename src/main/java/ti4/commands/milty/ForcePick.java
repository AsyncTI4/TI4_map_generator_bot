package ti4.commands.milty;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.milty.MiltyDraftManager;

class ForcePick extends GameStateSubcommand {

    public static final String PICK = "draft_pick";

    public ForcePick() {
        super("force_pick", "Pick for the active player in milty draft", true, false);
        addOptions(new OptionData(OptionType.STRING, PICK, "What should be picked")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (!"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        Game game = getGame();
        MiltyDraftManager manager = game.getMiltyDraftManager();
        Player player = manager.getCurrentDraftPlayer(game);
        if (player == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There is not an active milty draft for this game.");
        }

        boolean error = false;
        String fauxButton = "miltyForce_";
        String pick = event.getOption(PICK).getAsString();
        if (pick.startsWith("Slice ")) {
            String slice = pick.replace("Slice ", "");
            fauxButton += "slice_" + slice;
            if (manager.getSlice(slice) == null) error = true;
        } else if (pick.endsWith(" pick")) {
            String position = pick.substring(0, pick.length() - 7);
            fauxButton += "order_" + position;
            try {
                int pos = Integer.parseInt(position);
                if (pos < 1 || pos > manager.getPlayers().size()) error = true;
            } catch (Exception e) {
                error = true;
            }
        } else {
            if (!manager.getFactionDraft().contains(pick)) error = true;
            fauxButton += "faction_" + pick;
        }

        if (error) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), pick + " is not a valid choice");
        } else {
            manager.doMiltyPick(event, game, fauxButton, player);
        }
    }
}
