package com.minerva.core;

import com.minerva.tools.DateTimeTools;
import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import org.apache.commons.io.IOUtils;
import org.springaicommunity.agent.tools.*;
import org.springaicommunity.agent.utils.AgentEnvironment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.SlidingWindowCompactionStrategy;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.boot.actuate.web.exchanges.HttpExchange;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;

public class ConversationManager {
    private static ConversationManager single_instance = null;
    private static ChatClient aiChatClient;

    private static RestClient voiceClient;

    private ConversationManager()
    {
        single_instance = this;
        voiceClient = RestClient.builder().baseUrl("http://localhost:8000").build();
    }

    public static void Initialize(ChatClient.Builder builder, SessionService sessionService) throws IOException {
        SessionMemoryAdvisor advisor = SessionMemoryAdvisor.builder(sessionService)
                .defaultUserId("user")
                // Compact when 20 turns accumulate, keeping the last 10 events
                .compactionTrigger(new TurnCountTrigger(20))
                .compactionStrategy(SlidingWindowCompactionStrategy.builder().maxEvents(10).build())
                .build();
        aiChatClient = builder
                .defaultAdvisors(advisor)
                .defaultSystem(p -> p.text("You are a helpful agent")
                        .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
                        .param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus()))
                .defaultTools(
                        ShellTools.builder().build(),
                        FileSystemTools.builder().build(),
                        GrepTool.builder().build(),
                        GlobTool.builder().build())
                .build();
    }

    public static synchronized ConversationManager getInstance()
    {
        if (single_instance == null)
            single_instance = new ConversationManager();

        return single_instance;
    }

    public static String sendMessage(String message) {
        return aiChatClient
                .prompt()
                .tools(new DateTimeTools())
                .user(message)
                .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                .call()
                .content();
    }

    public static String seekImageOpinion(String pathToFile, String request) throws MalformedURLException {
        if (request == null) request = "Explain what do you see on this picture?";

        var pathToResource = new FileUrlResource(pathToFile);

        String finalRequest = request;
        String response = aiChatClient.prompt()
                .user(u -> u.text(finalRequest)
                        .media(MimeTypeUtils.IMAGE_JPEG, pathToResource))
                .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                .call()
                .content();
        return  response;
    }

    public static File audioProcessing(String pathToFile) throws IOException {
        //Convert audio file to text
        MultiValueMap<String, File> body = new LinkedMultiValueMap<>();
        var pathToResource = new FileUrlResource(pathToFile);
        body.add("file", pathToResource.getFile());
        var convertedText = voiceClient.post().uri("http://localhost:8000/speech").body(body).retrieve().body(String.class);
        String response = "";
        //Process the text
        if  (convertedText != null) {
            response = aiChatClient.prompt()
                    .user(u -> u.text(convertedText))
                    .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                    .call()
                    .content();

        }
        else {
            response = "No input provided";
        }
        //Convert the response back to audio
        var responseAudio = voiceClient
                .post()
                .uri("http://localhost:8000/speech?text={textToConvert}", response)
                .retrieve()
                .body(MultipartFile.class);

        var pathToAudio = FileManager.getInstance().UploadFile(responseAudio.getOriginalFilename(), responseAudio);
        //Return the audio
        return FileManager.getInstance().GetFile(pathToAudio);
    }

}
