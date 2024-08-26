package ti4.generator;

import ti4.helpers.Constants;
import ti4.map.Game;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public record Objective(
	String key,
	ti4.generator.Objective.Type type,
	Integer index, Boolean revealed,
	List<String> scoredPlayerIDs) {

	public enum Type {Stage1, Stage2, Custom}

	public static List<Objective> Retrieve(Game game) {
		List<Objective> objectives = new ArrayList<>();

		appendRevealedObjectives(game, objectives, Type.Stage1);
		appendUnrevealedObjectives(game, objectives, Type.Stage1);
		appendRevealedObjectives(game, objectives, Type.Stage2);
		appendUnrevealedObjectives(game, objectives, Type.Stage2);
		appendRevealedObjectives(game, objectives, Type.Custom);

		return objectives;
	}

	public Integer GetWorth(Game game) {
		return switch (type) {
			case Stage1 -> 1;
			case Stage2 -> 2;
			case Custom -> game.getCustomPublicVP().get(key) != null ? game.getCustomPublicVP().get(key) : 1;
		};
	}

	public String GetName() {
		return Mapper.getPublicObjective(key).getName();
	}

	public String GetDisplayText(Game game) {
		String name = this.GetName();
		Integer worth = this.GetWorth(game);
		if (revealed) {
			return String.format("(%d) %s - %d VP", index, name, worth);
		} else if (game.isRedTapeMode()) {
			return String.format("(%d) <Unrevealed> %s - %d VP", index, name, worth);
		}
		return String.format("(%d) <Unrevealed> - %d VP", index, worth);
	}

	public Boolean IsMultiScoring(Game game) {
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
			case Stage1 -> game.getCustomPublicVP().keySet().stream().filter(Mapper.getPublicObjectivesStage1()::containsKey).toList();
			case Stage2 -> game.getCustomPublicVP().keySet().stream().filter(Mapper.getPublicObjectivesStage2()::containsKey).toList();
			case Custom -> getCustomObjectives(game).keySet().stream().toList();
		};
	}

	private static void appendRevealedObjectives(Game game, List<Objective> objectives, Type type) {
		Integer index = 1;
		for (String key : getObjectiveList(game, type)) {
			objectives.add(new Objective(key, type, index, Boolean.TRUE, getScoredPlayerIDs(game, key)));
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
			objectives.add(new Objective(key, type, index, Boolean.FALSE, getScoredPlayerIDs(game, key)));
			index++;
		}
	}

	private static List<String> getScoredPlayerIDs(Game game, String objectiveKey) {
		return new LinkedHashMap<>(game.getScoredPublicObjectives()).get(objectiveKey);
	}
}
