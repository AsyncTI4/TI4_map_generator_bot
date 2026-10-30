package ti4.ai.perception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.discord.JdaService;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.discord.FakeDiscord;

// Fracture activations stack several combats in one system in one round. The roll buttons of a finished combat stay
// in its thread, and every combat thread of the system shares the same button ids, so an AI that still reads the old
// threads rolls in them and the opponent never sees the hits that follow.
class AiCombatThreadSelectionTest extends BaseTi4Test {

    private final AtomicLong clock = new AtomicLong(1_800_000_000_000L);
    private AiTestGame test;
    private Player hacan;
    private String position;
    private Tile tile;
    private TextChannel actions;
    private JDA savedJda;
    private Guild savedGuild;

    @BeforeEach
    void setUp() {
        savedJda = JdaService.jda;
        savedGuild = JdaService.guildPrimary;
        FakeDiscord discord = new FakeDiscord(clock::get);
        JdaService.jda = discord.jda();
        JdaService.guildPrimary = discord.guild();
        actions = discord.createTextChannel("ai-test-game-actions");
        test = new AiTestGame();
        test.game.setMainChannelID(actions.getId());
        hacan = test.addSeat("300000000000000001", "hacan", "orange");
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
    }

    @AfterEach
    void restoreDiscord() {
        JdaService.jda = savedJda;
        JdaService.guildPrimary = savedGuild;
    }

    @Test
    void doesNotReadTheThreadOfACombatWhoseOpponentIsGone() {
        test.units(tile, "space", test.sol, UnitType.Carrier, 1);
        test.units(tile, "space", hacan, UnitType.Carrier, 1);
        ThreadChannel finished = combatThread("turn-1-nekro-vs-hacan");
        ThreadChannel solVsNekro = combatThread("turn-4-sol-vs-nekro");
        ThreadChannel current = combatThread("turn-5-hacan-vs-sol");

        List<String> channels = channelsRead(hacan);
        assertThat(channels).containsExactly(current.getId());
        assertThat(channelsRead(test.sol)).containsExactly(current.getId());
        assertThat(channels).doesNotContain(finished.getId(), solVsNekro.getId());
    }

    @Test
    void readsOnlyTheNewestThreadOfOneMatchup() {
        test.units(tile, "space", test.sol, UnitType.Carrier, 1);
        test.units(tile, "space", hacan, UnitType.Carrier, 1);
        ThreadChannel earlier = combatThread("turn-2-hacan-vs-sol");
        ThreadChannel later = combatThread("turn-5-sol-vs-hacan");

        assertThat(channelsRead(hacan)).containsExactly(later.getId());
        assertThat(channelsRead(test.sol)).containsExactly(later.getId());
        assertThat(channelsRead(hacan)).doesNotContain(earlier.getId());
    }

    @Test
    void keepsSimultaneousCombatsAgainstDifferentOpponents() {
        test.units(tile, "space", test.sol, UnitType.Carrier, 1);
        test.units(tile, "space", hacan, UnitType.Carrier, 1);
        test.units(tile, "space", test.nekro, UnitType.Carrier, 1);
        ThreadChannel withHacan = combatThread("turn-5-sol-vs-hacan");
        ThreadChannel withNekro = combatThread("turn-5-sol-vs-nekro");

        assertThat(channelsRead(test.sol)).containsExactlyInAnyOrder(withHacan.getId(), withNekro.getId());
    }

    @Test
    void picksTheNewestThreadPerMatchupByCreationOrder() {
        test.units(tile, "space", test.sol, UnitType.Carrier, 1);
        test.units(tile, "space", hacan, UnitType.Carrier, 1);
        String older = test.game.getName() + "-round-2-system-" + position + "-turn-1-hacan-vs-sol";
        String newer = test.game.getName() + "-round-2-system-" + position + "-turn-4-sol-vs-hacan";
        String otherSystem = test.game.getName() + "-round-2-system-999-turn-2-hacan-vs-sol";

        assertThat(AiPerception.newestPerPair(test.game, Map.of(older, 10L, newer, 30L, otherSystem, 20L)))
                .containsExactlyInAnyOrder(newer, otherSystem);
    }

    private ThreadChannel combatThread(String suffix) {
        clock.addAndGet(60_000);
        String name = test.game.getName() + "-round-2-system-" + position + "-" + suffix;
        ThreadChannel thread = actions.createThreadChannel(
                        name,
                        actions.sendMessage("Resolve combat in this thread:")
                                .complete()
                                .getIdLong())
                .complete();
        thread.sendMessage("Buttons for combat.")
                .setComponents(ActionRow.of(Button.primary("combatRoll_" + position + "_space", "Roll Space Combat")))
                .complete();
        return thread;
    }

    private List<String> channelsRead(Player seat) {
        return AiPerception.collect(test.game, seat, Set.of()).stream()
                .map(AiPrompt::channelId)
                .distinct()
                .toList();
    }
}
