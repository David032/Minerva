package com.minerva.core;

import com.minerva.api.personality.InterestMemoryStore;
import com.minerva.discord.DiscordListener;
import com.minerva.tools.DateTimeTools;
import com.minerva.core.reminders.ReminderTools;
import com.minerva.core.journiv.JournivService;
import com.minerva.core.journiv.JournivTools;
import com.minerva.api.personality.MediaPersonalityService;
import com.minerva.tools.RssTools;
import com.minerva.skills.SkillImportService;
import com.minerva.skills.SkillImportTools;
import com.minerva.core.personality.PersonalityPromptStore;
import com.minerva.utils.Directory;
import com.minerva.utils.FileManager;
import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.advisors.AutoMemoryToolsAdvisor;
import org.springaicommunity.agent.tools.*;
import org.springaicommunity.agent.utils.AgentEnvironment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.SlidingWindowCompactionStrategy;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;


public class ConversationManager {
    private static volatile ChatClient aiChatClient;
    private static volatile ChatClient heartbeatChatClient;
    private static ChatClient.Builder baseChatClientBuilder;
    private static Advisor[] activeAdvisors;
    private static Advisor[] heartbeatAdvisors;
    private static Object[] activeTools;
    private static String activeSystemPrompt;
    private static RestClient voiceClient;
    private static AiActivityGate activityGate;
    private static JournivService journivService;
    private static final String HEARTBEAT_SESSION_ID = "minerva-heartbeat";
    private static final String HEARTBEAT_SYSTEM_PROMPT = """
            You are performing Minerva's scheduled idle heartbeat. Follow the local operator's
            heartbeat instructions, using available tools when they are needed. If no action is
            needed, say so briefly. Do not claim to have completed actions that you did not complete.
            """;
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
    /// @param skillImportService service used to load and refresh imported skills.
    /// @param dateTimeTools date/time tools exposed to the assistant.
    /// @param reminderTools reminder tools exposed to the assistant.
    /// @param rssTools RSS tools exposed to the assistant.
    /// @param personalityService personality image workflow exposed to the assistant.
    /// @param activityGate gate shared by foreground requests and the heartbeat.
    /// @param journivService activity recorder for the optional Journiv integration.
    /// @param journivTools read-only journal tools.
    /// @throws IOException If an I/O error occurs while reading the prompt files.
    public static synchronized void Initialize(
            ChatClient.Builder builder,
            SessionService sessionService,
            SkillImportService skillImportService,
            DateTimeTools dateTimeTools,
            ReminderTools reminderTools,
            RssTools rssTools,
            MediaPersonalityService personalityService,
            AiActivityGate activityGate,
            JournivService journivService,
            JournivTools journivTools
    ) throws IOException {
        ConversationManager.activityGate = activityGate;
        ConversationManager.journivService = journivService;
        baseChatClientBuilder = builder.clone();
        var pathToMemories = FileManager.getInstance().getDirectoryPath(Directory.MEMORIES);
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
        MessageChatMemoryAdvisor heartbeatShortTermMemoryAdvisor = MessageChatMemoryAdvisor
                .builder(MessageWindowChatMemory.builder().maxMessages(20).build())
                .build();

        AutoMemoryTools memoryTools = AutoMemoryTools.builder()
                .memoriesDir(pathToMemories)
                .build();

        TodoWriteTool todoTool = TodoWriteTool.builder().build();

        activeAdvisors = new Advisor[] {
                sessionMemoryAdvisor,
                longTermMemoryAdvisor,
                shortTermMemoryAdvisor,
                new com.minerva.advisors.LoggerAdvisor()
        };
        heartbeatAdvisors = new Advisor[] {
                sessionMemoryAdvisor,
                longTermMemoryAdvisor,
                heartbeatShortTermMemoryAdvisor,
                new com.minerva.advisors.LoggerAdvisor()
        };
        activeTools = new Object[] {
                ShellTools.builder().build(),
                FileSystemTools.builder().build(),
                GrepTool.builder().build(),
                GlobTool.builder().build(),
                memoryTools,
                todoTool,
                new SkillImportTools(skillImportService),
                dateTimeTools,
                reminderTools,
                rssTools,
                personalityService
        };
        if (journivService.enabled()) {
            activeTools = Arrays.copyOf(activeTools, activeTools.length + 1);
            activeTools[activeTools.length - 1] = journivTools;
        }
        if (System.getenv("BRAVE_API_KEY") != null) {
            activeTools = Arrays.copyOf(activeTools, activeTools.length + 1);
            activeTools[activeTools.length - 1] =
                    BraveWebSearchTool.builder(System.getenv("BRAVE_API_KEY")).resultCount(15).build();
        }
        skillImportService.setSkillChangeListener(ConversationManager::refreshSkills);
        refreshSkills();
    }

    private static synchronized void refreshSkills() throws IOException {
        if (baseChatClientBuilder == null) {
            return;
        }
        var pathToMemories = FileManager.getInstance().getDirectoryPath(Directory.MEMORIES);
        var pathToSkills = FileManager.getInstance().getDirectoryPath(Directory.SKILLS);
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        activeSystemPrompt = buildPrompt();
        var skillsSystem = SkillsTool.builder()
                .addSkillsDirectory(pathToSkills)
                .addSkillsResource(resourceLoader.getResource("classpath:/skills"))
                .build();
        Object[] tools = Arrays.copyOf(activeTools, activeTools.length + 1);
        tools[tools.length - 1] = skillsSystem;
        aiChatClient = buildChatClient(activeAdvisors, tools, pathToMemories);
        heartbeatChatClient = buildChatClient(heartbeatAdvisors, tools, pathToMemories);
    }

    private static ChatClient buildChatClient(Advisor[] advisors, Object[] tools, String pathToMemories) {
        ToolCallback[] callbacks = loggingCallbacks(tools);
        return baseChatClientBuilder.clone()
                .defaultAdvisors(advisors)
                .defaultSystem(p -> p.text(activeSystemPrompt)
                        .param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
                        .param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
                        .param("MEMORIES_ROOT_DIERCTORY", pathToMemories))
                .defaultTools((Object[]) callbacks)
                .build();
    }

    /**
     * Wraps the same annotated tools for foreground and heartbeat activity logging.
     *
     * @param tools assistant tool objects
     * @return callbacks with their definitions and metadata preserved
     */
    static ToolCallback[] loggingCallbacks(Object[] tools) {
        return Arrays.stream(ToolCallbacks.from(tools))
                .map(ActivityLoggingToolCallback::new)
                .toArray(ToolCallback[]::new);
    }

    /**
     * Builds the system prompt from the base prompt, saved personality prompt, and scored interests.
     *
     * @return the composed system prompt with user preference context
     * @throws IOException if a prompt or interest preference cannot be read
     */
    public static String buildPrompt() throws IOException {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        String basePrompt = resourceLoader.getResource("classpath:/prompts/BASE_PROMPT.md").getContentAsString(Charset.defaultCharset()) +
                resourceLoader.getResource("classpath:/prompts/AUTO_MEMORY_TOOLS_SYSTEM_PROMPT.md").getContentAsString(Charset.defaultCharset());
        String personalityPrompt = new PersonalityPromptStore(FileManager.getInstance()).composeWith(basePrompt);
        return new InterestMemoryStore(FileManager.getInstance()).appendScoredPreferences(personalityPrompt);
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
        var lease = activityGate.tryBeginConversation();
        if (lease.isEmpty()) {
            return AiBusyException.USER_MESSAGE;
        }
        try (var ignored = lease.get()) {
            String response = aiChatClient
                    .prompt()
                    .system(system -> system.text(currentPrompt()))
                    .user(message)
                    .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                    .call()
                    .content();
            journivService.record("text", message, response);
            return response;
        }
    }

    /**
     * Runs the scheduled heartbeat in its isolated conversation history.
     *
     * <p>The caller must hold the exclusive idle slot from {@link AiActivityGate}.</p>
     *
     * @param instructions contents of the configured heartbeat file
     * @param run identifies and tracks this heartbeat's tool invocations
     * @return assistant response
     */
    public static String sendHeartbeat(String instructions, HeartbeatRun run) {
        return heartbeatChatClient
                .prompt()
                .system(system -> system.text(currentPrompt() + "\n\n" + HEARTBEAT_SYSTEM_PROMPT))
                .user("Follow these recurring heartbeat instructions from the local operator:\n\n" + instructions)
                .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, HEARTBEAT_SESSION_ID))
                .toolContext(Map.of(HeartbeatRun.CONTEXT_KEY, run))
                .call()
                .content();
    }

    /// <summary>
    /// Seeks an opinion on an image by sending it to the AI along with a user request.
    /// </summary>
    /// @param pathToFile The path to the image file to analyse.
    /// @param request The user request or question regarding the image.
    /// @return The AI's response to the image and request.
    /// @throws MalformedURLException If the path to the image file is malformed.
    public static String seekImageOpinion(String pathToFile, String request) throws MalformedURLException {
        return seekImageOpinion(pathToFile, request, MimeTypeUtils.IMAGE_JPEG);
    }

    /**
     * Seeks an opinion on an image using the configured chat model and its actual MIME type.
     *
     * @param pathToFile path to the image file to analyse
     * @param request user request or question about the image
     * @param imageMimeType MIME type used to attach the image
     * @return the model's response to the image and request
     * @throws MalformedURLException if the image path is malformed
     */
    public static String seekImageOpinion(String pathToFile, String request, MimeType imageMimeType) throws MalformedURLException {
        if (request == null) request = "Explain what do you see on this picture?";

        var pathToResource = new FileUrlResource(pathToFile);

        String finalRequest = request;
        logger.info("Received image message: {}", finalRequest);
        try (var ignored = activityGate.tryBeginConversation().orElseThrow(AiBusyException::new)) {
            String response = aiChatClient.prompt()
                    .system(system -> system.text(currentPrompt()))
                    .user(u -> u.text(finalRequest)
                            .media(imageMimeType, pathToResource))
                    .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                    .call()
                    .content();
            logger.info("Responding to image message with: {}", response);
            journivService.record("image", finalRequest, response);
            return response;
        }
    }

    /// <summary>
    /// Processes an audio file by converting it to text, generating a response, and converting the response back to audio.
    /// </summary>
    /// @param pathToFile The path to the audio file to process.
    /// @return The processed audio file.
    /// @throws IOException If an I/O error occurs during file processing.
    public static File audioProcessing(String pathToFile) throws IOException {
        try (var ignored = activityGate.tryBeginConversation().orElseThrow(AiBusyException::new)) {
            //Convert audio file to text
            MultiValueMap<String, File> body = new LinkedMultiValueMap<>();
            var pathToResource = new FileUrlResource(pathToFile);
            body.add("file", pathToResource.getFile());
            var convertedText = voiceClient.post().uri("/speech").body(body).retrieve().body(String.class);
            String response;
            //Process the text
            if (convertedText != null) {
                response = aiChatClient.prompt()
                        .system(system -> system.text(currentPrompt()))
                        .user(u -> u.text(convertedText))
                        .advisors(a -> a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, "session-abc"))
                        .call()
                        .content();

            } else {
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
            File result = FileManager.getInstance().GetFile(pathToAudio);
            if (convertedText != null) {
                journivService.record("audio", convertedText, response);
            }
            return result;
        }
    }

    private static String currentPrompt() {
        try {
            return buildPrompt();
        } catch (IOException e) {
            throw new IllegalStateException("Could not load Minerva's system and personality prompts.", e);
        }
    }

}
