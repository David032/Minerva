package com.minerva.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;

public class OllamaConnector {
    private static OllamaConnector instance = null;
    static RestClient ollamaClient;
    static String baseUrl;
    Logger logger = LoggerFactory.getLogger(OllamaConnector.class);
    OllamaConnector(String url) {
        instance = this;
        baseUrl = url;
        ollamaClient = RestClient.builder().baseUrl(url).build();
    }

    public static synchronized OllamaConnector getInstance(String url) {
        if (instance == null){
            instance = new OllamaConnector(url);
        }
        return instance;
    }

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

    public boolean checkForModel(String model) {
        var checkModel = ollamaClient.get().uri(baseUrl + "/api/tags").retrieve().toEntity(String.class);
        assert checkModel.getBody() != null;
        return checkModel.getBody().contains(model);
    }
}
