package com.minerva.core;

import com.minerva.tools.DateTimeTools;
import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.advisors.AutoMemoryToolsAdvisor;
import org.springaicommunity.agent.tools.*;
import org.springaicommunity.agent.utils.AgentEnvironment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.SlidingWindowCompactionStrategy;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;


public class ConversationManager {
    private static ChatClient aiChatClient;
    private static RestClient voiceClient;
    static final Logger logger = LoggerFactory.getLogger(ConversationManager.class);

    /// <summary>
    /// Initializes a new instance of the ConversationManager class.
    /// </summary>
    private ConversationManager()
    {
        voiceClient = RestClient.builder().baseUrl(System.getenv().getOrDefault("VOICE_SERVICE_URL", "http://localhost:8000")).build();
    }

    /// <summary>
    /// Initializes the ConversationManager. This method sets up the AI chat client with the necessary advisors, tools, and system prompts.
    /// </summary>
    /// @param builder The ChatClient.Builder used to build the AI chat client.
    /// @param sessionService The SessionService used for managing user sessions.
    /// @throws IOException If an I/O error occurs while reading the prompt files.
    public static void Initialize(ChatClient.Builder builder, SessionService sessionService) throws IOException {
        var pathToMemories = FileManager.getInstance().getDirectoryPath(Directory.MEMORIES);
        var pathToSkills = FileManager.getInstance().getDirectoryPath(Directory.SKILLS);
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        String prompt = buildPrompt();

        SessionMemoryAdvisor sessionMemoryAdvisor = SessionMemoryAdvisor.builder(sessionService)
                .defaultUserId("user")
                // Compact when 20 turns accumulate, keeping the last 10 events
                .compactionTrigger(new TurnCountTrigger(20))
                .compactionStrategy(SlidingWindowCompactionStrategy.builder().maxEvents(10).build())
                .build();

        AutoMemoryToolsAdvisor longTermMemoryAdvisor = AutoMemoryToolsAdvisor.builder()
                .memoriesRootDirectory(pathToMemories)
                .build();

        MessageChatMemoryAdvisor shortTermMemoryAdvisor = MessageChatMemoryAdvisor
                .builder(MessageWindowChatMemory.builder().maxMessages(20).build())
                .build();

        AutoMemoryTools memoryTools = AutoMemoryTools.builder()
                .memoriesDir(pathToMemories)
                .build();

        var skillsSystem = SkillsTool.builder()
                .addSkillsDirectory(pathToSkills)
                .addSkillsResource(resourceLoader.getResource("classpath:/skills"))
                .build();

        var LoggerAdvisor = new com.minerva.advisors.LoggerAdvisor();

        var webSearch = BraveWebSearchTool.builder(System.getenv("BRAVE_API_KEY")).resultCount(15).build();

        aiChatClient = builder
                .defaultAdvisors(
                        sessionMemoryAdvisor,
                        longTermMemoryAdvisor,
                        shortTermMemoryAdvisor,
                        LoggerAdvisor
                )
                .defaultSystem(p -> p.text(prompt)
                        .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
                        .param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
                        .param("MEMORIES_ROOT_DIERCTORY", pathToMemories))
                .defaultTools(
                        ShellTools.builder().build(),
                        FileSystemTools.builder().build(),
                        GrepTool.builder().build(),
                        GlobTool.builder().build(),
                        memoryTools,
                        skillsSystem,
                        webSearch)
                .build();
    }

    /// <summary>
    /// Builds the base prompt for the AI by combining multiple prompt files.
    /// </summary>
    /// @return The combined prompt as a string.
    /// @throws IOException If an I/O error occurs while reading the prompt files.
    public static String buildPrompt() throws IOException {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        return resourceLoader.getResource("classpath:/prompts/BASE_PROMPT.md").getContentAsString(Charset.defaultCharset()) +
                resourceLoader.getResource("classpath:/prompts/AUTO_MEMORY_TOOLS_SYSTEM_PROMPT.md").getContentAsString(Charset.defaultCharset());
    }

    /// <summary>
    /// Sends a message to the AI and returns the response.
    /// </summary>
    /// @param message The message to send to the AI.
    /// @return The AI's response to the message.
    public static String sendMessage(String message) {
        if (message == null || message.isEmpty()){
            logger.warn("Null or empty message");
            return "";
        }
        var response = aiChatClient
                .prompt()
                .tools(new DateTimeTools())
                .user(message)
                .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                .call()
                .content();
        return response;
    }

    /// <summary>
    /// Seeks an opinion on an image by sending it to the AI along with a user request.
    /// </summary>
    /// @param pathToFile The path to the image file to analyze.
    /// @param request The user request or question regarding the image.
    /// @return The AI's response to the image and request.
    /// @throws MalformedURLException If the path to the image file is malformed.
    public static String seekImageOpinion(String pathToFile, String request) throws MalformedURLException {
        if (request == null) request = "Explain what do you see on this picture?";

        var pathToResource = new FileUrlResource(pathToFile);

        String finalRequest = request;
        logger.info("Received image message: {}", finalRequest);
        String response = aiChatClient.prompt()
                .user(u -> u.text(finalRequest)
                        .media(MimeTypeUtils.IMAGE_JPEG, pathToResource))
                .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                .call()
                .content();
        logger.info("Responding to image message with: {}", response);
        return  response;
    }

    /// <summary>
    /// Processes an audio file by converting it to text, generating a response, and converting the response back to audio.
    /// </summary>
    /// @param pathToFile The path to the audio file to process.
    /// @return The processed audio file.
    /// @throws IOException If an I/O error occurs during file processing.
    public static File audioProcessing(String pathToFile) throws IOException {
        //Convert audio file to text
        MultiValueMap<String, File> body = new LinkedMultiValueMap<>();
        var pathToResource = new FileUrlResource(pathToFile);
        body.add("file", pathToResource.getFile());
        var convertedText = voiceClient.post().uri("/speech").body(body).retrieve().body(String.class);
        String response;
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
                .uri("/speech?text={textToConvert}", response)
                .retrieve()
                .body(MultipartFile.class);

        assert responseAudio != null;
        var pathToAudio = FileManager.getInstance().UploadFile(responseAudio.getOriginalFilename(), responseAudio);
        //Return the audio
        return FileManager.getInstance().GetFile(pathToAudio);
    }

}
