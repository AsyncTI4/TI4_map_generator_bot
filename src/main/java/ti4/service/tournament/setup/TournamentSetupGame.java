package ti4.service.tournament.setup;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.service.game.CreateGameService;
import ti4.service.testbed.TestBedService;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentGameNames;
import ti4.service.tournament.TournamentSetupAccess;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentService;

@UtilityClass
public class TournamentSetupGame {

    private static final String SEAT_NAME = "Setup seat ";

    private static String gameName(Tournament tournament) {
        return "tsetup" + tournament.getId();
    }

    public static String create(Tournament tournament, Member organizer, MessageChannel reportChannel) {
        Guild guild = TournamentDiscord.hostGuild(tournament);
        if (guild == null) return "Pick a host server on the Settings tab first.";
        Member hostMember = TournamentDiscord.member(guild, organizer.getId());
        if (hostMember == null) return "Join **" + guild.getName() + "** first, the setup game is created there.";
        String name = gameName(tournament);
        if (TournamentGameNames.isTaken(name)) {
            return "**" + name + "** already exists. Use **Use setup from game** to link it.";
        }
        Game game = CreateGameService.createGameChannels(
                List.of(hostMember),
                reportChannel,
                tournament.getSeriesName() + " setup",
                name,
                hostMember,
                TournamentDiscord.category(guild, StringUtils.left(tournament.getSeriesName() + " Setup", 100)),
                true);
        if (game == null) return "Could not create the setup game. Check the host server's capacity.";
        ExecutionLockManager.wrapWithLockAndRelease(name, ExecutionLockType.WRITE, () -> {
                    game.setStoredValue(TournamentSetupAccess.SETUP_GAME_KEY, String.valueOf(tournament.getId()));
                    syncSeats(game, tournament.getGroupSize());
                    GameManager.save(game, "Tournament setup game created");
                })
                .run();
        int admins = giveRoleToAdmins(guild, name);
        TournamentService.getBean().update(tournament.getId(), pool -> pool.setSetupSourceGame(name));
        return "Created setup game " + actionsChannelMention(game) + " with " + tournament.getGroupSize() + " seats. "
                + admins + " tournament admin(s) can see it. Set it up with the normal buttons there.";
    }

    public static String actionsChannelMention(Game game) {
        return game.getActionsChannel() == null
                ? game.getName()
                : game.getActionsChannel().getAsMention();
    }

    private static int giveRoleToAdmins(Guild guild, String gameName) {
        Role role = guild.getRolesByName(gameName, true).stream().findFirst().orElse(null);
        if (role == null) return 0;
        int given = 0;
        for (Member member : guild.getMembers()) {
            if (member.getUser().isBot() || !TournamentAccess.isTournamentAdmin(member)) continue;
            if (!member.getRoles().contains(role)) {
                guild.addRoleToMember(member, role).queue(Consumers.nop(), BotLogger::catchRestError);
            }
            given++;
        }
        return given;
    }

    private static void syncSeats(Game game, int groupSize) {
        List<Player> seats = setupSeats(game);
        int realSeats = game.getPlayers().size() - seats.size();
        int wanted = Math.max(0, groupSize - realSeats);
        for (int i = seats.size() - 1; i >= wanted; i--) {
            game.removePlayer(seats.get(i).getUserID());
        }
        for (int number = 1; setupSeats(game).size() < wanted; number++) {
            String userId = TestBedService.VIRTUAL_SEAT_ID_PREFIX + String.format("%02d", number);
            if (game.getPlayer(userId) != null) continue;
            Player seat = game.addPlayer(userId, SEAT_NAME + number);
            seat.setBotPlayer(true);
            seat.setDummy(true);
        }
        int players = Math.max(1, game.getPlayers().size());
        game.setPlayerCountForMap(players);
        game.setStrategyCardsPerPlayer(Math.max(1, game.getSCList().size() / players));
    }

    public static boolean isOwnSetupGame(Game game, Tournament tournament) {
        return game != null
                && String.valueOf(tournament.getId()).equals(game.getStoredValue(TournamentSetupAccess.SETUP_GAME_KEY));
    }

    public static boolean dealtOrDrafted(Game game) {
        return draftStarted(game) || cardsDealt(game);
    }

    public static boolean cardsDealt(Game game) {
        return game.getRevealedPublicObjectives().size() > 1
                || !game.getScoredPublicObjectives().isEmpty()
                || game.getPlayers().values().stream()
                        .anyMatch(player -> !player.getSecrets().isEmpty()
                                || !player.getActionCards().isEmpty());
    }

    private static boolean draftStarted(Game game) {
        return "miltydraft".equals(game.getPhaseOfGame())
                || game.getPlayers().values().stream()
                        .anyMatch(player -> !player.isDummy() && StringUtils.isNotBlank(player.getFaction()));
    }

    public static String resizeSeats(Tournament tournament) {
        Game probe = TournamentSetupApplier.loadSource(tournament);
        if (!isOwnSetupGame(probe, tournament)) return null;
        if (draftStarted(probe)) return "The setup game's draft has started, so its seats were not changed.";
        String name = tournament.getSetupSourceGame();
        ExecutionLockManager.wrapWithLockAndRelease(name, ExecutionLockType.WRITE, () -> {
                    Game game = GameManager.getManagedGame(name).getGame();
                    syncSeats(game, tournament.getGroupSize());
                    GameManager.save(game, "Tournament setup game seats changed");
                })
                .run();
        return "The setup game now has " + tournament.getGroupSize() + " seats.";
    }

    private static List<Player> setupSeats(Game game) {
        return game.getPlayers().values().stream()
                .filter(Player::isBotPlayer)
                .filter(player -> player.getUserID().startsWith(TestBedService.VIRTUAL_SEAT_ID_PREFIX))
                .toList();
    }
}
