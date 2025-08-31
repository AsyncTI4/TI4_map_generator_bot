package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.game.SetDeckService;

class ChangeToBaseGame extends GameStateSubcommand {

    public AddRemoveCodex() {
        super(Constants.ADD_REMOVE_CODEX, "Add or Remove Codex Components From The Game", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.CODEX_NUMBER, "Codex to add/remove")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ADD_REMOVE, "Add or remove")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String codex = event.getOption(Constants.CODEX_NUMBER).getAsString();
        String add = "add".equals(event.getOption(Constants.ADD_REMOVE).getAsString());
        String message = "";
        switch (codex) {
            case "1ac":
                message += (add ? addCodex1ActionCards : removeCodex1ActionCards)(event);
                break;
            case "1omega":
                message += (add ? addCodex1Omegas : removeCodex1Omegas)(event);
                break;
            case "1":
                message += (add ? addCodex1ActionCards : removeCodex1ActionCards)(event);
                message += (add ? addCodex1Omegas : removeCodex1Omegas)(event);
                break;
            case "2":
                message += (add ? addCodex2Relics : removeCodex2Relics)(event);
                break;
        }
    }
    
    public String addCodex1ActionCards(SlashCommandInteractionEvent event)
    {
        Game game = getGame();
        List<String> existingCards = game.getActionCards();
        List<String> newCards = new ArrayList<>();
        Map<String, ActionCardModel> actionCards = Mapper.getActionCards();
        for (ActionCardModel ac : actionCards.values()) {
            if ("codex1".equals(ac.getSource().name()) && !existingCards.contains(ac.getAlias())) {
                newCards.add(ac.getAlias());
            }
        }
        game.addActionCardDuplicates(newCards);
        return "Added" + newCards.size() + " Codex I action cards to game.\n";
    }
    
    public String removeCodex1ActionCards(SlashCommandInteractionEvent event)
    {
        Game game = getGame();
        Map<String, ActionCardModel> actionCards = Mapper.getActionCards();
        int number = 0;
        for (ActionCardModel ac : actionCards.values()) {
            if ("codex1".equals(ac.getSource().name())) {
                number += game.removeACFromGame(ac.getAlias()) ? 1 : 0;
            }
        }
        return "Removed " + number + " Codex I action cards from game.\n";
    }
    
    public String addCodex1Omegas(SlashCommandInteractionEvent event)
    {
        Game game = getGame();
        String message = "";
        for (Player player : game.getPlayers().values()) {
            for (String pn : player.getPromissoryNotes().keySet()) {
                switch (pn) {
                    case "war_funding":
                        message += "Only _War Funding Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "greyfire":
                        message += "Only _Greyfire Mutagen Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "stymie":
                        message += "Only _Stymie Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "acq":
                        message += "Only _Acquiescence Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "ce":
                        message += "Only _Cybernetic Enhancements_ is implemented in the async bot; no changes made.\n";
                        break;
                }
            }
            for (String pn : player.getPromissoryNotesInPlayArea().keySet()) {
                switch (pn) {
                    case "war_funding":
                        message += "Only _War Funding Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "greyfire":
                        message += "Only _Greyfire Mutagen Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "stymie":
                        message += "Only _Stymie Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "acq":
                        message += "Only _Acquiescence Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "ce":
                        message += "Only _Cybernetic Enhancements_ is implemented in the async bot; no changes made.\n";
                        break;
                }
            }
        }
        for (Player player : game.getPlayers().values()) {
            for (String tech : player.getTechs()) {
                switch (tech) {
                    case "wg":
                        message += "Only _Wormhole Generator Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "yso":
                        message += "Only _Yin Spinner Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "mr":
                        message += "Only Magmus Reactor Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "md_c1":
                    case "md":
                        player.removeTech(tech);
                        player.addTech("md_base");
                        message += "Removed _Magen Defense Grid Ω" + ("md".equals(tech) ? "Ω" : "") + "_ from " 
                            + player.getRepresentationNoPing() ", and replaced it with _Magen Defense Grid_ (base).\n";
                        break;
                    case "x89":
                    case "x89c4":
                        player.removeTech(tech);
                        player.addTech("x89_base");
                        message += "Removed _X-89 Bacterial Weapon Ω" + ("x89c4".equals(tech) ? "Ω" : "") + "_ from " 
                            + player.getRepresentationNoPing() ", and replaced it with _X-89 Bacterial Weapon_ (base).\n";
                        break;
                }
            }
            for (String pn : player.getFactionTechs()) {
                switch (pn) {
                    case "wg":
                        message += "Only _Wormhole Generator Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "yso":
                        message += "Only _Yin Spinner Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                    case "mr":
                        message += "Only Magmus Reactor Ω_ is implemented in the async bot; no changes made.\n";
                        break;
                }
            }
        }
        game.setTechnologyDeckID("techs_pok_basemagen");
        return message;
    }
    
    public String removeCodex1Omegas(SlashCommandInteractionEvent event)
    {
        Game game = getGame();
        String message = "";
        for (Player player : game.getPlayers().values()) {
            for (String tech : player.getTechs()) {
                switch (tech) {
                    case "md_base":
                        player.removeTech("md_base");
                        player.addTech("md_c1");
                        message += "Removed _Magen Defense Grid_ from " 
                            + player.getRepresentationNoPing() ", and replaced it with _Magen Defense Grid Ω_.\n";
                        break;
                    case "x89_base":
                        player.removeTech("x89_base");
                        player.addTech("x89");
                        message += "Removed _X-89 Bacterial Weapon_ from " 
                            + player.getRepresentationNoPing() ", and replaced it with _X-89 Bacterial Weapon Ω_.\n";
                        break;
                }
            }
        }
    }
    
    public String addCodex2Relics(SlashCommandInteractionEvent event)
    {
        Game game = getGame();
        List<String> allRelics = game.getAllRelics();
        int number = 0;
        if (!allRelics.contains("dynamiscore")) {
            getGame().shuffleRelicBack("dynamiscore");
            number++;
        }
        if (!allRelics.contains("titanprototype")) {
            getGame().shuffleRelicBack("titanprototype");
            number++;
        }
        if (!allRelics.contains("nanoforge")) {
            getGame().shuffleRelicBack("nanoforge");
            number++;
        }
        
        return "Added" + number + " Codex II relics to game.\n";
    }
    
    public String removeCodex2Relics(SlashCommandInteractionEvent event)
    {
        Game game = getGame();
        List<String> allRelics = game.getAllRelics();
        int number = 0;
        if (allRelics.contains("dynamiscore")) {
            allRelics.remove("dynamiscore");
            number++;
        }
        if (allRelics.contains("titanprototype")) {
            allRelics.remove("titanprototype");
            number++;
        }
        if (allRelics.contains("nanoforge")) {
            allRelics.remove("nanoforge");
            number++;
        }
        return "Removed" + number + " Codex II relics to game.\n";
    }
}
