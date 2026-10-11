package ti4.discord.interactions.commands.search;

import java.util.Comparator;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.AttachmentModel;

class SearchAttachmentsSubcommand extends SearchComponentModelSubcommand<AttachmentModel> {

    SearchAttachmentsSubcommand() {
        super(
                Constants.SEARCH_ATTACHMENTS,
                "List all attachments the bot can use",
                Mapper::isValidAttachment,
                Mapper::getAttachmentInfo,
                valuesOf(() -> Mapper.getAttachments().values()),
                AttachmentModel::getRepresentationEmbed,
                Comparator.comparing(AttachmentModel::getSource));
    }
}
