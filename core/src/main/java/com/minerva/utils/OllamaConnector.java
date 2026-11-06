package com.minerva.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

public class OllamaConnector {
    private static OllamaConnector instance = null;
    private final RestClient ollamaClient;
    private final String baseUrl;
    final Logger logger = LoggerFactory.getLogger(OllamaConnector.class);

    /// <summary>
    /// Initializes a new instance of the OllamaConnector class.
    /// </summary>
    /// @param url The base URL of the Ollama API.
    OllamaConnector(String url) {
        this(url, RestClient.builder());
    }

    OllamaConnector(String url, RestClient.Builder restClientBuilder) {
        baseUrl = url;
        ollamaClient = restClientBuilder.baseUrl(url).build();
    }

    /// <summary>
    /// Gets the singleton instance of the OllamaConnector class.
    /// </summary>
    /// @param url The base URL of the Ollama API.
    /// @return The singleton instance of the OllamaConnector class.
    public static synchronized OllamaConnector getInstance(String url) {
        if (instance == null){
            instance = new OllamaConnector(url);
        }
        return instance;
    }

    /// <summary>
    /// Connects to the Ollama API and pulls the specified model if it is not already present.
    /// </summary>
    /// @param url The base URL of the Ollama API.
    /// @param aimodel The name of the model to pull from the Ollama API.
    public void connectToOllama(String url, String aimodel){
        //Check to see if model is already present
        var initialCheck = ollamaClient.get().uri(url + "/api/tags").retrieve().toEntity(String.class);
        assert initialCheck.getBody() != null;
        boolean isAlreadyPresent = initialCheck.getBody().contains(aimodel);
        logger.info("Is model already present? {}", isAlreadyPresent);
        if (!isAlreadyPresent) {
            //Pull model
            Dictionary<String, String> body = new Hashtable<>();
            body.put("model", aimodel);

            var pullModel = ollamaClient.post().uri(url + "/api/pull").body(body).retrieve().toEntity(String.class);
            assert pullModel.getBody() != null;
            logger.info("Model pulled?{}", pullModel.getBody());

            boolean isAvailable = false;
            while (!isAvailable) {
                logger.info("Checking for model...");
                var checkIfReady = ollamaClient.get().uri(url + "/api/tags").retrieve().toEntity(String.class);
                assert checkIfReady.getBody() != null;
                if (checkIfReady.getBody().contains(aimodel)) {
                    isAvailable = true;
                    logger.info("Model ready!");
                } else {
                    logger.info("Model not ready! Sleeping for 90 seconds...");
                    try {
                        TimeUnit.SECONDS.sleep(90);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    /// <summary>
    /// Checks if the specified model is present in Ollama
    /// </summary>
    /// @param model The name of the model to check for.
    /// @return True if the model is present, false otherwise.
    public boolean checkForModel(String model) {
        var checkModel = ollamaClient.get().uri(baseUrl + "/api/tags").retrieve().toEntity(String.class);
        assert checkModel.getBody() != null;
        return checkModel.getBody().contains(model);
    }
}
