package ti4.service.leader.agent.modules.homebrew.wftv;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.adjacency.Adjacency;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class VyserixAgent extends AgentModule<Tile> {

    static final String ID = "vyserixagent";

    public VyserixAgent() {
        super(ID, "Vaexis, the Vyserix");
    }

    public static Button button(Player vyserix, Tile placementSystem) {
        return Buttons.blue(
                ownedButtonId(vyserix, ID, placementSystem.getPosition()),
                useLabel(vyserix, ID, "Vyserix Agent"),
                FactionEmojis.vyserix);
    }

    public static boolean hasTargets(Game game, Player producer, Tile placementSystem) {
        return !targetSystems(game, producer, placementSystem).isEmpty();
    }

    @Override
    public Optional<Tile> decode(Game game, Player user, String payload) {
        return Optional.ofNullable(game.getTileByPosition(payload));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Tile placementSystem) {
        if (!hasTargets(game, user, placementSystem)) {
            return Optional.of("No system adjacent to " + placementSystem.getPosition() + " contains your units");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Tile> use) {
        Player producer = use.user();
        return AgentOutcome.of(Message.addressedTo(
                producer,
                ", use buttons to place fighters/destroyers in an adjacent system via " + use.agentName() + ".",
                placementButtons(use.game(), producer, use.payload())));
    }

    private static List<Tile> targetSystems(Game game, Player producer, Tile placementSystem) {
        List<Tile> targets = new ArrayList<>();
        for (String position : Adjacency.of(game, producer).forMovement().from(placementSystem.getPosition())) {
            Tile tile = game.getTileByPosition(position);
            if (tile != null && tile.containsPlayersUnits(producer)) {
                targets.add(tile);
            }
        }
        return targets;
    }

    private static List<Button> placementButtons(Game game, Player producer, Tile placementSystem) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : targetSystems(game, producer, placementSystem)) {
            buttons.add(Buttons.green(
                    producer.factionButtonChecker() + "place_fighter_" + tile.getPosition(),
                    "Produce 1 Fighter in " + tile.getPosition(),
                    UnitEmojis.fighter));
            buttons.add(Buttons.green(
                    producer.factionButtonChecker() + "place_destroyer_" + tile.getPosition(),
                    "Produce 1 Destroyer in " + tile.getPosition(),
                    UnitEmojis.destroyer));
        }
        buttons.add(
                Buttons.red(producer.factionButtonChecker() + "deleteButtons_spitItOut", "Done With Vyserix Agent"));
        return buttons;
    }
}
