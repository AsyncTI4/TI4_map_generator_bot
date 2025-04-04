package ti4.image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.BotLogger;
import ti4.model.PublicObjectiveModel;

public record Objective(
    String key,
    ti4.image.Objective.Type type,
    Integer index, Boolean revealed,
    List<String> scoredPlayerIDs,
    List<String> peekPlayerIDs
) {

    public enum Type {
        Stage1, Stage2, Custom
    }

    public static List<Objective> retrieve(Game game) {
        List<Objective> objectives = new ArrayList<>();

        appendRevealedObjectives(game, objectives, Type.Stage1);
        appendUnrevealedObjectives(game, objectives, Type.Stage1);
        appendRevealedObjectives(game, objectives, Type.Stage2);
        appendUnrevealedObjectives(game, objectives, Type.Stage2);
        appendRevealedObjectives(game, objectives, Type.Custom);

        return objectives;
    }

    public static List<Objective> retrievePublic1(Game game) {
        List<Objective> objectives = new ArrayList<>();
        appendRevealedObjectives(game, objectives, Type.Stage1);
        appendUnrevealedObjectives(game, objectives, Type.Stage1);
        return objectives;
    }

    public static List<Objective> retrievePublic2(Game game) {
        List<Objective> objectives = new ArrayList<>();
        appendRevealedObjectives(game, objectives, Type.Stage2);
        appendUnrevealedObjectives(game, objectives, Type.Stage2);
        return objectives;
    }

    public static List<Objective> retrieveCustom(Game game) {
        List<Objective> objectives = new ArrayList<>();
        appendRevealedObjectives(game, objectives, Type.Custom);
        return objectives;
    }

    public Integer getWorth(Game game) {
        return switch (type) {
            case Stage1 -> 1;
            case Stage2 -> 2;
            case Custom -> game.getCustomPublicVP().get(key) != null ? game.getCustomPublicVP().get(key) : 1;
        };
    }

    public String getName() {
        if (type == Type.Custom) {
            return key;
        }
        PublicObjectiveModel po = Mapper.getPublicObjective(key);
        if (po == null) {
            BotLogger.warning(String.format("Objective not found: key = %s", key));
            return "";
        }
        return po.getName();
    }

    public String getDisplayText(Game game) {
        String name = this.getName();
        Integer worth = this.getWorth(game);
        if (revealed) {
            return String.format("(%d) %s - %d VP", game.getRevealedPublicObjectives().get(key), name, worth);
        } else if (game.isRedTapeMode()) {
            return String.format("(%d) <Unrevealed> %s - %d VP", index, name, worth);
        }
        return String.format("(%d) <Unrevealed> - %d VP", index, worth);
    }

    public Boolean isMultiScoring(Game game) {
        return Constants.CUSTODIAN.equals(key) || Constants.IMPERIAL_RIDER.equals(key) || game.isFowMode();
    }

    private static Map<String, String> getCustomObjectives(Game game) {
        return game.getCustomPublicVP().keySet().stream()
            .collect(Collectors.toMap(key -> key, name -> {
                name = name.replace("extra1", "");
                name = name.replace("extra2", "");
                String nameOfPO = Mapper.getSecretObjectivesJustNames().get(name);
                return nameOfPO != null ? nameOfPO : name;
            }, (key1, key2) -> key1, LinkedHashMap::new));
    }

    private static List<String> getObjectiveList(Game game, Type type) {
        return switch (type) {
            case Stage1 -> game.getRevealedPublicObjectives().keySet().stream().filter(Mapper.getPublicObjectivesStage1().keySet()::contains).collect(Collectors.toList());
            case Stage2 -> game.getRevealedPublicObjectives().keySet().stream().filter(Mapper.getPublicObjectivesStage2().keySet()::contains).collect(Collectors.toList());
            case Custom -> getCustomObjectives(game).keySet().stream().toList();
        };
    }

    private static void appendRevealedObjectives(Game game, List<Objective> objectives, Type type) {
        Integer index = 1;
        for (String key : getObjectiveList(game, type)) {
            objectives.add(new Objective(key, type, index, Boolean.TRUE, getScoredPlayerIDs(game, key), getPeekPlayerIDs(game, key)));
            index++;
        }
    }

    private static void appendUnrevealedObjectives(Game game, List<Objective> objectives, Type type) {
        List<String> inputList;
        Integer index = 1;

        if (type == Type.Stage1) {
            inputList = game.getPublicObjectives1Peakable();
        } else {
            inputList = game.getPublicObjectives2Peakable();
        }

        for (String key : inputList) {
            objectives.add(new Objective(key, type, index, Boolean.FALSE, getScoredPlayerIDs(game, key), getPeekPlayerIDs(game, key)));
            index++;
        }
    }

    private static List<String> getScoredPlayerIDs(Game game, String objectiveKey) {
        return game.getScoredPublicObjectives().getOrDefault(objectiveKey, Collections.emptyList());
    }

    private static List<String> getPeekPlayerIDs(Game game, String objectiveKey) {
        if (game.getPublicObjectives1Peeked().containsKey(objectiveKey)) {
            return new LinkedHashMap<>(game.getPublicObjectives1Peeked()).get(objectiveKey);
        } else if (game.getPublicObjectives2Peeked().containsKey(objectiveKey)) {
            return new LinkedHashMap<>(game.getPublicObjectives2Peeked()).get(objectiveKey);
        } else {
            return new ArrayList<>();
        }
    }
}
