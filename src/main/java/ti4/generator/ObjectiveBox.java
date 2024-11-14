package ti4.generator;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import ti4.helpers.ImageHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;

public class ObjectiveBox {
	public static final int objectiveBoxHeight = 38;
	public static final int spacingBetweenBoxes = 5;
	private static final int bufferBetweenTextAndTokens = 15;
	private static final int textVerticalOffset = 23;
	private static final int horizontalBoxOffset = 4;
	private static final float controlTokenScale = 0.55f;

	private static final Color Stage1RevealedColor = new Color(230, 126, 34);
	private static final Color Stage1HiddenColor = new Color(130, 70, 0);
	private static final Color Stage2RevealedColor = new Color(93, 173, 226);
	private static final Color Stage2HiddenColor = new Color(30, 60, 128);

	private int x;
	private final int y;
	private final int boxWidth;
	private final int maxTextWidth;
	private final int scoreTokenWidth;

	ObjectiveBox(int x, int y, int boxWidth, int maxTextWidth, int scoreTokenWidth) {
		this.x = x;
		this.y = y;
		this.boxWidth = boxWidth;
		this.maxTextWidth = maxTextWidth;
		this.scoreTokenWidth = scoreTokenWidth;
	}

	public void Display(Game game, Graphics graphics, MapGenerator generator, Objective objective) {
		setColor(graphics, objective);

		graphics.drawString(objective.getDisplayText(game), x, y + textVerticalOffset);
		graphics.drawRect(x - horizontalBoxOffset, y - spacingBetweenBoxes, boxWidth, objectiveBoxHeight);
		if (objective.revealed() && Mapper.getPublicObjective(objective.key()) != null) {
			generator.addWebsiteOverlay(Mapper.getPublicObjective(objective.key()), x - horizontalBoxOffset, y - spacingBetweenBoxes, boxWidth, objectiveBoxHeight);
		}

		x += maxTextWidth + bufferBetweenTextAndTokens;
		displayScoreMarkers(game, graphics, generator, objective);
	}

	public static Integer GetMaxTextWidth(Game game, Graphics graphics, List<Objective> objectives) {
		int maxTextWidth = 0;
		for (Objective objective : objectives) {
			maxTextWidth = Math.max(maxTextWidth, graphics.getFontMetrics().stringWidth(objective.getDisplayText(game)));
		}
		return maxTextWidth;
	}

	public static Integer GetMinimumBoxWidth(Game game) {
		return game.isRedTapeMode() ? 800 : 400;
	}

	public static Integer GetBoxWidth(Game game, Integer maxTextWidth, Integer scoreTokenWidth) {
		return Math.max(GetMinimumBoxWidth(game), Math.min(MapGenerator.getMaxObjectWidth(game), getMaxLengthOfTokens(game, maxTextWidth, scoreTokenWidth)));
	}

	public static Integer GetVerticalSpacing() {
		return objectiveBoxHeight + spacingBetweenBoxes;
	}

	private static Integer getMaxLengthOfTokens(Game game, Integer maxTextWidth, Integer scoreTokenWidth) {
		return maxTextWidth + (bufferBetweenTextAndTokens * 2) + (game.getPlayers().size() * scoreTokenWidth);
	}

	private void displayScoreMarkers(Game game, Graphics graphics, MapGenerator generator, Objective objective) {
		List<String> playerIDs;
		if (objective.revealed() && objective.scoredPlayerIDs() != null) {
			playerIDs = objective.scoredPlayerIDs();
		} else if (objective.peekPlayerIDs() != null) {
			playerIDs = objective.peekPlayerIDs();
		} else {
			return;
		}
		try {
			for (String playerID : playerIDs) {
				Player player = game.getPlayer(playerID);
				if (player == null) continue;

				boolean convertToGeneric = generator.shouldConvertToGeneric(player);
				String controlID = convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());

				if (controlID.contains("null")) {
					continue;
				}

				BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), controlTokenScale);

				if (objective.isMultiScoring(game) || game.isFowMode()) {
					DrawingUtil.drawControlToken(graphics, controlTokenImage, player, x, y, convertToGeneric, controlTokenScale);
					x += scoreTokenWidth;

				} else {
					int xPosition = x + scoreTokenWidth * (game.getRealPlayers().indexOf(player));
					DrawingUtil.drawControlToken(graphics, controlTokenImage, player, xPosition, y, convertToGeneric, controlTokenScale);
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
