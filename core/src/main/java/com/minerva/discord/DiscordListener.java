package com.minerva.discord;

import com.minerva.core.ConversationManager;
import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.File;
import java.net.MalformedURLException;

import static java.lang.IO.print;

public class DiscordListener extends ListenerAdapter
{
    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getAuthor().isBot()) return;
        // We don't want to respond to other bot accounts, including ourselves
        Message message = event.getMessage();
        String content = message.getContentRaw();
        String response = null;

        print("Received Message!");
        if (message.getAttachments().isEmpty()){
            print("No attachments! Responding via text");
            response = ConversationManager.sendMessage(content);
        } else {
            print("Got a picture!");
            var attachment = message.getAttachments().getFirst();
            if (attachment.isImage()) {
                File output = new File(FileManager.getInstance().getDirectoryPath(Directory.UPLOADS) + "/" + attachment.getFileName());
                attachment.getProxy().downloadToFile(output)
                        .thenAccept(file -> System.out.println("Saved image to: " + file.getAbsolutePath()))
                        .exceptionally(error -> {
                            error.printStackTrace();
                            return null;
                        });

                try {
                    response = ConversationManager.seekImageOpinion(attachment.getFileName(), content);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        MessageChannel channel = event.getChannel();
        assert response != null;
        channel.sendMessage(response).queue();

    }
}
