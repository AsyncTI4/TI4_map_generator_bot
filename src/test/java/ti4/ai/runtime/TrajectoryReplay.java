package ti4.ai.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import ti4.ai.AiSettings;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.FactionBrain;
import ti4.ai.brain.FactionBrainRegistry;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.profile.AiProfile;
import ti4.ai.strategy.SecondaryQueue;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.SaveText;
import ti4.message.GameMessage;
import ti4.message.GameMessageType;
import tools.jackson.databind.JsonNode;

/**
 * Asks a seat's brain again for a decision a {@link TrajectoryLog} snapshot recorded: the board is read back from the
 * saved file and the turn context (prompts, pressed keys, memory, time) from the context entry. Brains reach static
 * services, so the caller runs this inside a {@link SelfPlayEnvironment}.
 */
final class TrajectoryReplay {

    record Recorded(
            Game game,
            AiTurnContext context,
            FactionBrain brain,
            List<GameMessage> gameMessages,
            Map<String, Long> secondaryQueue) {}

    private TrajectoryReplay() {}

    static List<Integer> decisions(Path snapshots) {
        try (ZipFile zip = new ZipFile(snapshots.toFile())) {
            List<Integer> indexes = new ArrayList<>();
            zip.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.endsWith(TrajectoryLog.CONTEXT_SUFFIX))
                    .map(name ->
                            Integer.parseInt(name.substring(0, name.length() - TrajectoryLog.CONTEXT_SUFFIX.length())))
                    .sorted()
                    .forEach(indexes::add);
            return indexes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Recorded load(Path snapshots, int index) {
        try (ZipFile zip = new ZipFile(snapshots.toFile())) {
            Game game = SaveText.read(read(zip, String.format("%06d%s", index, TrajectoryLog.SAVE_SUFFIX)));
            JsonNode rest = TrajectoryLog.JSON.readTree(
                    read(zip, String.format("%06d%s", index, TrajectoryLog.CONTEXT_SUFFIX)));
            Player seat = game.getPlayer(rest.get("userId").asString());
            if (seat == null) throw new IllegalStateException("No seat " + rest.get("seat") + " in decision " + index);
            AiProfile profile = AiProfile.of(seat);
            FactionBrain brain = FactionBrainRegistry.forBrainId(profile.brainId())
                    .orElseThrow(() -> new IllegalStateException("No brain " + profile.brainId()));
            AiMemory memory = new AiMemory();
            memory.restore(memory(rest.get("memory")));
            AiTurnContext context = new AiTurnContext(
                    game,
                    seat,
                    profile,
                    prompts(rest.get("prompts")),
                    pressedKeys(rest.get("pressedKeys")),
                    memory,
                    rest.get("now").asLong());
            return new Recorded(
                    game,
                    context,
                    brain,
                    gameMessages(rest.get("gameMessages")),
                    secondaryQueue(rest.get("secondaryQueue")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static AiDecision decide(SelfPlayEnvironment env, Recorded recorded) {
        env.gameMessages.restore(recorded.game().getName(), recorded.gameMessages());
        SecondaryQueue.restore(recorded.game().getName(), recorded.secondaryQueue());
        try (Thinking.Decision ignored = Thinking.open(AiSettings.thinkingLimits());
                DecisionMemo.Open memo = DecisionMemo.open()) {
            return recorded.brain().decide(recorded.context());
        }
    }

    static Optional<String> choice(AiDecision decision) {
        return decision instanceof AiDecision.Press press
                ? Optional.of(press.button().customId())
                : Optional.empty();
    }

    private static String read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("No entry " + name);
        try (var in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Long> secondaryQueue(JsonNode node) {
        Map<String, Long> open = new LinkedHashMap<>();
        node.properties()
                .forEach(entry -> open.put(entry.getKey(), entry.getValue().asLong()));
        return open;
    }

    private static List<GameMessage> gameMessages(JsonNode node) {
        List<GameMessage> messages = new ArrayList<>();
        for (JsonNode message : node) {
            LinkedHashSet<String> reacted = new LinkedHashSet<>();
            message.get("factionsThatReacted").forEach(faction -> reacted.add(faction.asString()));
            JsonNode key = message.get("key");
            messages.add(new GameMessage(
                    message.get("messageId").asString(),
                    GameMessageType.valueOf(message.get("type").asString()),
                    reacted,
                    message.get("gameSaveTime").asLong(),
                    key == null || key.isNull() ? null : key.asString()));
        }
        return messages;
    }

    private static Map<String, String> memory(JsonNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        node.properties()
                .forEach(entry -> values.put(entry.getKey(), entry.getValue().asString()));
        return values;
    }

    private static Set<String> pressedKeys(JsonNode node) {
        Set<String> keys = new LinkedHashSet<>();
        node.forEach(key -> keys.add(key.asString()));
        return keys;
    }

    private static List<AiPrompt> prompts(JsonNode node) {
        List<AiPrompt> prompts = new ArrayList<>();
        for (JsonNode prompt : node) {
            List<PromptButton> buttons = new ArrayList<>();
            for (JsonNode button : prompt.get("buttons")) {
                JsonNode owner = button.get("ownerFaction");
                buttons.add(new PromptButton(
                        button.get("index").asInt(),
                        button.get("customId").asString(),
                        button.get("handlerId").asString(),
                        owner == null || owner.isNull() ? null : owner.asString(),
                        button.get("label").asString(),
                        button.get("disabled").asBoolean()));
            }
            JsonNode content = prompt.get("content");
            prompts.add(new AiPrompt(
                    prompt.get("channelId").asString(),
                    prompt.get("messageId").asString(),
                    AiPrompt.PromptSource.valueOf(prompt.get("source").asString()),
                    content == null || content.isNull() ? null : content.asString(),
                    buttons,
                    prompt.get("createdAtMillis").asLong()));
        }
        return prompts;
    }
}
