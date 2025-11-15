package ti4.commands.draft.manage;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;

class DraftManagerUnpick extends GameStateSubcommand {
    public DraftManagerUnpick() {
        super(Constants.DRAFT_MANAGE_UNPICK, "Unpick a choice for any player that drafted it", true, false);
        addOption(
                OptionType.STRING,
                Constants.DRAFTABLE_TYPE_OPTION,
                "Type of draftable to remove the pick from",
                true,
                true);
        addOption(OptionType.STRING, Constants.PLAYER_PICKS_OPTION, "Key of the choice to unpick", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        DraftableType draftableType = DraftableType.of(
                event.getOption(Constants.DRAFTABLE_TYPE_OPTION).getAsString());
        String choiceKey = event.getOption(Constants.PLAYER_PICKS_OPTION).getAsString();
        Draftable draftable = draftManager.getDraftable(draftableType);
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No draftable of type: " + draftableType);
            return;
        }
        List<String> playerIds = draftManager.getPlayersWithChoiceKey(draftableType, choiceKey);
        if (playerIds.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "No players have picked choice with key: " + choiceKey + " in draftable of type: " + draftableType);
            return;
        }

        for (String playerUserId : playerIds) {
            draftManager
                    .getPlayerStates()
                    .get(playerUserId)
                    .getPicks(draftableType)
                    .removeIf(choice -> choice.getChoiceKey().equals(choiceKey));
        }

        List<String> players = playerIds.stream()
                .map(game::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getRepresentation)
                .collect(Collectors.toList());
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Unpicked choice with key: " + choiceKey + " in draftable of type: " + draftableType + " for players: "
                        + String.join(", ", players));
    }
}
