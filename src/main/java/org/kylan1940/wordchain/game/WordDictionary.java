package org.kylan1940.wordchain.game;

import org.kylan1940.wordchain.WordChain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds every valid English word in memory.
 * The words come from words.txt inside the jar (one lowercase word per line).
 */
public class WordDictionary {

    private final Set<String> words = new HashSet<>();

    public void load(WordChain plugin) {
        words.clear();

        InputStream stream = plugin.getResource("words.txt");
        if (stream == null) {
            plugin.getLogger().severe("words.txt not found inside the plugin jar!");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty()) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read words.txt: " + e.getMessage());
        }

        plugin.getLogger().info("Loaded " + words.size() + " English words.");
    }

    public boolean contains(String word) {
        return words.contains(word);
    }

    public int size() {
        return words.size();
    }
}
