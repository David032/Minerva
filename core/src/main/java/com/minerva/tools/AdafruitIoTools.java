package com.minerva.tools;

import com.minerva.utils.adafruitIO.AdafruitIOData;
import com.rometools.rome.feed.atom.Feed;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class AdafruitIoTools {

    static String baseUrl = "https://io.adafruit.com";
    static String ioKey;
    static String ioUsername;

    boolean enabled = true;

    final RestClient ioClient;

    public AdafruitIoTools(RestClient ioClient) {
        if (!hasIoKey()){
            enabled = false;
        }
        RestClient.Builder clientBuilder = ioClient.mutate();
        if (ioKey != null) {
            clientBuilder.defaultHeader("X-AIO-Key", ioKey);
        }
        this.ioClient = clientBuilder.build();
    }

    /// <summary>
    /// Check if an io key and username have been provided
    /// </summary>
    /// @return True if they are, else false
    boolean hasIoKey(){
        String ioKeyProp = System.getenv("IO_KEY");
        String ioUsernameProp = System.getenv("IO_USERNAME");
        if ((ioKeyProp != null) && (ioUsernameProp != null)){
            if (!Objects.equals(ioKey, ioKeyProp)) {
                ioKey = ioKeyProp;
            }
            if ((!Objects.equals(ioUsernameProp, ioUsername))){
                ioUsername = ioUsernameProp;
            }
            return true;
        }
        return false;
    }

    /// <summary>
    /// Check if adafruitIO is accessible
    /// </summary>
    /// @return True if accessible, else false
    /// @throws IOException if not accessible
    boolean canConnect() throws IOException {
        return InetAddress.getByName(baseUrl).isReachable(300);
    }

    @Tool(description = "Get the latest value for a particular feed")
    String getLatestValueFromFeed(String feedKey) throws IOException {
        if (enabled) {
            if (canConnect()){
                AdafruitIOData response = ioClient.get()
                        .uri(baseUrl + "/api/v2/" + ioUsername + "/feeds/" + feedKey + "/data?limit=1").retrieve().body(AdafruitIOData.class);
                assert response != null;
                return response.getValue();
            }
            return "Failed! Cannot connect to Adafruit.com";
        } else {
            return "Failed! IO key & username not found";
        }


    }


}
