package com.minerva.utils.adafruitIO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class AdafruitIOData {

    private String id;

    private String value;

    @JsonProperty("feed_id")
    private int feedId;

    @JsonProperty("feed_key")
    private String feedKey;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    private Location location;

    private double lat;

    private double lon;

    private double ele;

    @JsonProperty("created_epoch")
    private long createdEpoch;

    private OffsetDateTime expiration;

    @Data
    @NoArgsConstructor
    public static class Location {
        // Add specific location fields if they become known
    }

    public String getValue() {
        return value;
    }
}

