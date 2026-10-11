package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.PromissoryNoteModel;

class SearchPromissoryNotesSubcommand extends SearchComponentModelSubcommand<PromissoryNoteModel> {

    SearchPromissoryNotesSubcommand() {
        super(
                Constants.SEARCH_PROMISSORY_NOTES,
                "List all promissory notes the bot can use",
                Mapper::isValidPromissoryNote,
                Mapper::getPromissoryNote,
                valuesOf(() -> Mapper.getPromissoryNotes().values()),
                model -> model.getRepresentationEmbed(false, true, true),
                null);
    }
}
