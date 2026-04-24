package ti4.discord.interactions.buttons.handlers.galacticevent;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.commands.special.SetupNeutralPlayer;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.option.TEOptionService;

@UtilityClass
public class GalacticEventButtonHandler {

    @ButtonHandler("enableDaneMode_")
    public static void enableGalacticEvent(ButtonInteractionEvent event, String buttonID, Game game) {
        String mode = buttonID.split("_")[1];
        boolean enable = "enable".equalsIgnoreCase(buttonID.split("_")[2]);
        String message = "Successfully " + buttonID.split("_")[2] + "d the ";
        if ("hiddenagenda".equalsIgnoreCase(mode)) {
            game.setHiddenAgendaMode(enable);
            message += "Hidden Agenda Mode. Nothing more needs to be done.";
        }
        if ("minorFactions".equalsIgnoreCase(mode)) {
            game.setMinorFactionsMode(enable);
            message += "Minor Factions Mode. ";
            if (enable) {
                message +=
                        "Note that the bot does not currently handle the draft for minor factions very well. This site has a decent setup for it, "
                                + "and you can import the map using buttons above: https://tidraft.com/draft/prechoice. Note that you can add 3 infantry to the minor faction planets with the provided button.";
                List<Button> mfButtons = new ArrayList<>();
                mfButtons.add(Buttons.blue("addMinorFactionsInfantry", "Add Minor Factions Infantry"));
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "After setting up the map, use this button to auto populate the neutral infantry",
                        mfButtons);
            }
        }
        if ("ageOfExploration".equalsIgnoreCase(mode)) {
            game.setAgeOfExplorationMode(enable);
            message += "Age of Exploration Mode. Nothing more needs to be done.";
        }
        if ("ageOfCommerce".equalsIgnoreCase(mode)) {
            game.setAgeOfCommerceMode(enable);
            message += "Age of Commerce Mode. Nothing more needs to be done.";
        }
        if ("totalWar".equalsIgnoreCase(mode)) {
            game.setTotalWarMode(enable);
            message += "Total War Mode. Nothing more needs to be done.";
        }
        if ("DangerousWilds".equalsIgnoreCase(mode)) {
            game.setDangerousWildsMode(enable);
            message += "Dangerous Wilds Mode. Nothing more needs to be done.";
            if (enable) {
                message += " The game will automatically put down infantry upon the start of every strategy phase.";
            }
        }
        if ("CivilizedSociety".equalsIgnoreCase(mode)) {
            game.setCivilizedSocietyMode(enable);
            message += "Civilized Society Mode. Nothing more needs to be done.";
        }
        if ("ZealousOrthodoxy".equalsIgnoreCase(mode)) {
            game.setZealousOrthodoxyMode(enable);
            message += "Zealous Orthodoxy Mode. Nothing more needs to be done.";
        }
        if ("AdventOfTheWarsun".equalsIgnoreCase(mode)) {
            game.setAdventOfTheWarsunMode(enable);
            message += "Advent of the Warsun Mode. Nothing more needs to be done.";
        }
        if ("MercenariesForHire".equalsIgnoreCase(mode)) {
            game.setMercenariesForHireMode(enable);
            message += "Mercenaries For Hire Mode. Nothing more needs to be done.";
        }
        if ("CulturalExchangeProgram".equalsIgnoreCase(mode)) {
            game.setCulturalExchangeProgramMode(enable);
            message += "Cultural Exchange Program Mode. Nothing more needs to be done.";
            if (enable) {
                message += " Leaders will be exchanged when secrets are dealt.";
            }
        }
        if ("Conventions".equalsIgnoreCase(mode)) {
            game.setConventionsOfWarAbandonedMode(enable);
            message += "Conventions of War Abandoned Mode. Nothing more needs to be done.";
        }
        if ("Cosmic".equalsIgnoreCase(mode)) {
            game.setCosmicPhenomenaeMode(enable);
            message += "Cosmic Phenomenae Mode. Nothing more needs to be done.";
        }
        if ("WildGalaxy".equalsIgnoreCase(mode)) {
            game.setWildWildGalaxyMode(enable);
            message += "Wild, Wild Galaxy Mode. Nothing more needs to be done.";
        }
        if ("WeirdWormholes".equalsIgnoreCase(mode)) {
            game.setWeirdWormholesMode(enable);
            message += "Weird Wormholes Mode. Nothing more needs to be done.";
        }
        if ("CallOfTheVoid".equalsIgnoreCase(mode)) {
            game.setCallOfTheVoidMode(enable);
            message += "Call of the Void. Nothing more needs to be done.";
        }
        if ("Monument".equalsIgnoreCase(mode)) {
            game.setMonumentToTheAgesMode(enable);
            message += "Monuments to the Ages Mode. Nothing more needs to be done.";
            if (enable) {
                Player neutral = game.getPlayerFromColorOrFaction("neutral");
                if (neutral == null) {
                    String color = SetupNeutralPlayer.pickNeutralColor(game);
                    game.setupNeutralPlayer(color);
                }
            }
        }
        if ("RapidMobilization".equalsIgnoreCase(mode)) {
            game.setRapidMobilizationMode(enable);
            message += "Rapid Mobilization Mode. Nothing more needs to be done.";
            if (enable) {
                message += " make sure to set up players after the map is set up.";
            }
        }
        if ("AgeOfFighters".equalsIgnoreCase(mode)) {
            game.setAgeOfFightersMode(enable);
            message += "Age Of Fighters Mode. Nothing more needs to be done.";
            if (enable) {
                for (Player player : game.getRealPlayers()) {
                    String tech = "ff2";
                    for (String factionTech : player.getNotResearchedFactionTechs()) {
                        TechnologyModel fTech = Mapper.getTech(factionTech);
                        if (fTech != null
                                && !fTech.getAlias()
                                        .equalsIgnoreCase(Mapper.getTech(tech).getAlias())
                                && fTech.isUnitUpgrade()
                                && fTech.getBaseUpgrade()
                                        .orElse("bleh")
                                        .equalsIgnoreCase(Mapper.getTech(tech).getAlias())) {
                            tech = fTech.getAlias();
                            break;
                        }
                    }
                    player.addTech(tech);
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation() + " gained the "
                                    + Mapper.getTech(tech).getNameRepresentation()
                                    + " technology due to the _Age of Fighters_ galactic event.");
                }
            }
        }
        if ("StellarAtomics".equalsIgnoreCase(mode)) {
            game.setStellarAtomicsMode(enable);
            if (enable) {
                int poIndex = game.addCustomPO("Stellar Atomics", 0);
                for (Player playerWL : game.getRealPlayers()) {
                    game.scorePublicObjective(playerWL.getUserID(), poIndex);
                }
            }
            message += "Stellar Atomics Mode. Nothing more needs to be done.";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        List<Button> buttons = TEOptionService.getGalacticEventButtons(game);
        event.getMessage()
                .editMessage(event.getMessage().getContentRaw())
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
