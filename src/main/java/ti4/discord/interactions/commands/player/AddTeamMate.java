package ti4.discord.interactions.commands.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.TeamMateAccessService;

class AddTeamMate extends GameStateSubcommand {

    private static final List<String> TEAMMATE_OPTIONS =
            IntStream.rangeClosed(2, 6).mapToObj(number -> "player" + number).toList();

    public AddTeamMate() {
        super(Constants.ADD_TEAMMATE, "Add up to 5 teammates at once", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "User who is on your team").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to add the user to")
                .setAutoComplete(true));
        for (String option : TEAMMATE_OPTIONS.subList(1, TEAMMATE_OPTIONS.size())) {
            addOptions(new OptionData(OptionType.USER, option, "Another user on your team"));
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        List<User> users = TEAMMATE_OPTIONS.stream()
                .map(event::getOption)
                .filter(Objects::nonNull)
                .map(OptionMapping::getAsUser)
                .distinct()
                .toList();

        List<String> added = new ArrayList<>();
        List<String> alreadyOnTeam = new ArrayList<>();
        List<String> notOnServer = new ArrayList<>();
        for (User user : users) {
            if (player.getTeamMateIDs().contains(user.getId())) {
                alreadyOnTeam.add(user.getAsMention());
            } else {
                player.addTeamMateID(user.getId());
                added.add(user.getAsMention());
            }
            if (!TeamMateAccessService.isOnGameServer(game, user.getId())) {
                notOnServer.add(user.getAsMention());
            }
            TeamMateAccessService.grantAccess(game, player, user.getId());
        }
        if (!added.isEmpty()) {
            game.setCommunityMode(true);
        }

        String team = player.getFaction() + "'s team.";
        String message = Stream.of(
                        added.isEmpty() ? null : "Added " + String.join(", ", added) + " as part of " + team,
                        alreadyOnTeam.isEmpty()
                                ? null
                                : String.join(", ", alreadyOnTeam)
                                        + (alreadyOnTeam.size() == 1 ? " is" : " are")
                                        + " already a part of " + team,
                        notOnServer.isEmpty()
                                ? null
                                : String.join(", ", notOnServer)
                                        + (notOnServer.size() == 1 ? " is" : " are")
                                        + " not on this server, so they cannot be given access to the game channels.")
                .filter(Objects::nonNull)
                .reduce((first, second) -> first + "\n" + second)
                .orElse("");
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            return;
        }
        MessageHelper.sendMessageToEventChannel(event, message);
    }
}
