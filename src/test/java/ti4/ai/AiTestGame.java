package ti4.ai;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.profile.AggressionLevel;
import ti4.ai.profile.AiProfile;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.model.FactionModel;

/** Small in-memory game with a Nekro AI seat and a human Sol seat, for AI decision tests. */
public final class AiTestGame {

    public static final String AI_ID = "7100000123456789";
    public static final String SECOND_AI_ID = "7100000987654321";
    public static final String HUMAN_ID = "200000000000000001";
    public static final long NOW = 1_800_000_000_000L;
    public static final String HOME = "301";

    public final Game game;
    public final Player nekro;
    public final Player sol;
    public final AiMemory memory = new AiMemory();
    private final Map<String, AiMemory> otherMemories = new HashMap<>();

    public AiTestGame() {
        this(HUMAN_ID);
    }

    private AiTestGame(String solUserId) {
        game = new Game();
        game.setName("ai-test-game");
        game.setRound(2);
        game.setExploreDeck(Mapper.getShuffledDeck("explores_pok"));
        game.setActiveSystem("");
        nekro = addSeat(AI_ID, "nekro", "black");
        sol = addSeat(solUserId, "sol", "blue");
    }

    /** The same table, but with the Sol seat played by a second AI (a self-play game). */
    public static AiTestGame withSolAi() {
        return new AiTestGame(SECOND_AI_ID);
    }

    public Player addSeat(String userId, String faction, String color) {
        FactionModel model = Mapper.getFaction(faction);
        Player player = game.addPlayer(userId, model.getFactionName());
        player.setFaction(game, faction);
        player.setColor(color);
        player.setUnitsOwned(new HashSet<>(model.getUnits()));
        player.setFactionTechs(model.getFactionTech());
        if (model.getStartingTech() != null) player.setTechs(new ArrayList<>(model.getStartingTech()));
        return player;
    }

    /** Nekro's home system (tile 08, Mordai II) at {@link #HOME}, controlled and with a space dock. */
    public Tile nekroHome() {
        Tile home = place("08", HOME);
        nekro.addPlanet("mordaiii");
        units(home, "mordaiii", nekro, UnitType.Spacedock, 1);
        return home;
    }

    /** First board position adjacent to {@code position}, in the bot's own adjacency table. */
    public static String neighbourOf(String position) {
        return PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    public Tile place(String tileId, String position) {
        Tile tile = new Tile(tileId, position);
        game.setTile(tile);
        return tile;
    }

    public void units(Tile tile, String holder, Player player, UnitType type, int count) {
        tile.addUnit(holder, Units.getUnitKey(type, player.getColor()), count);
    }

    public void aiIsActive(String phase) {
        isActive(nekro, phase);
    }

    public void isActive(Player seat, String phase) {
        game.setPhaseOfGame(phase);
        game.setActivePlayerID(seat.getUserID());
        game.setLastActivePlayerChange(new Date(NOW - 1000));
    }

    public AiMemory memoryOf(Player seat) {
        if (seat == nekro) return memory;
        return otherMemories.computeIfAbsent(seat.getUserID(), ignored -> new AiMemory());
    }

    public AiTurnContext context(AiPrompt... prompts) {
        return context(Set.of(), prompts);
    }

    public AiTurnContext context(Set<String> pressedKeys, AiPrompt... prompts) {
        return contextFor(nekro, pressedKeys, NOW, prompts);
    }

    public AiTurnContext contextAt(long now, AiPrompt... prompts) {
        return contextFor(nekro, Set.of(), now, prompts);
    }

    public AiTurnContext contextFor(Player seat, Set<String> pressedKeys, long now, AiPrompt... prompts) {
        AiProfile profile = new AiProfile(
                AiSettings.brainFor(seat.getFaction()),
                AggressionLevel.OPPORTUNIST,
                AiProfile.AggressionMode.DYNAMIC,
                AiProfile.PauseState.RUNNING,
                7L);
        return new AiTurnContext(game, seat, profile, List.of(prompts), pressedKeys, memoryOf(seat), now);
    }

    public static AiPrompt prompt(String messageId, PromptSource source, long created, String... customIds) {
        return prompt(messageId, source, created, List.of(customIds), List.of());
    }

    public static AiPrompt prompt(
            String messageId, PromptSource source, long created, List<String> customIds, List<String> labels) {
        List<PromptButton> buttons = new ArrayList<>();
        for (int i = 0; i < customIds.size(); i++) {
            String label = i < labels.size() ? labels.get(i) : "Option " + i;
            buttons.add(PromptButton.of(i, Button.secondary(customIds.get(i), label)));
        }
        return new AiPrompt("channel-" + messageId, messageId, source, "", buttons, created);
    }

    public static AiPrompt withContent(AiPrompt prompt, String content) {
        return new AiPrompt(
                prompt.channelId(),
                prompt.messageId(),
                prompt.source(),
                content,
                prompt.buttons(),
                prompt.createdAtMillis());
    }

    public static String pressedId(AiDecision decision) {
        if (!(decision instanceof AiDecision.Press press)) {
            throw new AssertionError("Expected a press but got " + decision);
        }
        return press.button().customId();
    }
}
