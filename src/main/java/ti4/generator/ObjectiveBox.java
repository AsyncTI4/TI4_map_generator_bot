package ti4.generator;

import ti4.helpers.ImageHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

public class ObjectiveBox {
	public static final int objectiveBoxHeight = 38;
	public static final int spacingBetweenBoxes = 5;
	private static final int bufferBetweenTextAndTokens = 15;
	private static final int scoreTokenWidth = 14;
	private static final int textVerticalOffset = 23;
	private static final int horizontalBoxOffset = 4;
	private static final float controlTokenScale = 0.55f;

	private static final Color Stage1RevealedColor = new Color(230, 126, 34);
	private static final Color Stage1HiddenColor = new Color(130, 70, 0);
	private static final Color Stage2RevealedColor = new Color(93, 173, 226);
	private static final Color Stage2HiddenColor = new Color(30, 60, 128);

	public static void Display(Game game, Graphics graphics, MapGenerator generator, Objective objective, int x, int y, int boxWidth, int maxTextWidth) {
		setColor(graphics, objective);

		graphics.drawString(objective.GetDisplayText(game), x, y + textVerticalOffset);
		graphics.drawRect(x - horizontalBoxOffset, y - spacingBetweenBoxes, boxWidth, objectiveBoxHeight);

		y += objectiveBoxHeight + spacingBetweenBoxes;
		x += maxTextWidth + bufferBetweenTextAndTokens;

		displayScoreMarkers(game, graphics, generator, objective, x, y);
	}

	public static Integer GetMaxTextWidth(Game game, List<Objective> objectives) {
		int maxTextWidth = 0;
		Objective.Type lastType = Objective.Type.Stage1;
		Boolean lastRevealed = true;
		for (Objective objective : objectives) {
			if (objective.type() != lastType) {
				lastType = objective.type();
			} else if (objective.revealed() != lastRevealed) {
				lastRevealed = objective.revealed();
			}
			maxTextWidth = Math.max(maxTextWidth, objective.GetDisplayText(game).length());
		}
		return maxTextWidth;
	}

	public static Integer GetMaxLengthOfTokens(Game game, Integer maxTextWidth) {
		return maxTextWidth + (bufferBetweenTextAndTokens * 2) + (game.getPlayers().size() * scoreTokenWidth);
	}

	public static Integer GetMinimumBoxWidth(Game game) {
		return game.isRedTapeMode() ? 800: 400;
	}

	public static Integer GetBoxWidth(Game game, Integer maxTextWidth) {
		return Math.max(GetMinimumBoxWidth(game), Math.min(MapGenerator.GetMaxObjectWidth(game), GetMaxLengthOfTokens(game, maxTextWidth)));
	}

	public static Integer GetVerticalSpacing() {
		return objectiveBoxHeight + spacingBetweenBoxes;
	}

	private static void displayScoreMarkers(Game game, Graphics graphics, MapGenerator generator, Objective objective, int x, int y) {
		try {
			for (String playerID: objective.scoredPlayerIDs()) {
				Player player = game.getPlayer(playerID);
				boolean convertToGeneric = generator.shouldConvertToGeneric(player);
				String controlID = convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());

				if (controlID.contains("null")) {
					continue;
				}

				BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), controlTokenScale);

				if (objective.IsMultiScoring(game)) {
					int frequency = Collections.frequency(objective.scoredPlayerIDs(), playerID);
					for (int i = 0; i < frequency; i++) {
						MapGenerator.drawControlToken(graphics, controlTokenImage, player, x, y, convertToGeneric, controlTokenScale);
						x += scoreTokenWidth;
					}
				} else {
					MapGenerator.drawControlToken(graphics, controlTokenImage, player, x, y, convertToGeneric, controlTokenScale);
				}
				if (!objective.IsMultiScoring(game)) {
					x += scoreTokenWidth;
				}
			}
		} catch (Exception e) {
			BotLogger.log("Error drawing score control token markers", e);
		}
	}

	private static void setColor(Graphics graphics, Objective objective) {
		switch (objective.type()) {
			case Stage1 -> {
				if (objective.revealed()) {
					graphics.setColor(Stage1RevealedColor);
				} else {
					graphics.setColor(Stage1HiddenColor);
				}
			}
			case Stage2 -> {
				if (objective.revealed()) {
					graphics.setColor(Stage2RevealedColor);
				} else {
					graphics.setColor(Stage2HiddenColor);
				}
			}
			case Custom -> graphics.setColor(Color.WHITE);
		}
	}
}
