package net.md_5.bungee.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents the standard data returned by pinging a server using the modern query protocol.
 */
@Data
@ToString(exclude = "favicon")
@NoArgsConstructor
@AllArgsConstructor
public class ServerPing {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Version version;
    private Players players;
    private Description description;
    private String favicon;

    @Data
    @AllArgsConstructor
    public static class Version {
        private String name;
        private int protocol;
    }

    @Data
    @AllArgsConstructor
    public static class Players {
        private int max;
        private int online;
    }

    @Data
    @AllArgsConstructor
    public static class Description {
        private String text;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

}