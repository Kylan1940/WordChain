package org.kylan1940.wordchain;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.kylan1940.wordchain.commands.WordChainCommand;
import org.kylan1940.wordchain.game.WordChainGame;
import org.kylan1940.wordchain.game.WordDictionary;
import org.kylan1940.wordchain.listeners.ChatListener;
import org.kylan1940.wordchain.listeners.QuitListener;
import org.kylan1940.wordchain.utils.ConfigUpdater;

public final class WordChain extends JavaPlugin {

    private static WordChain instance;

    private WordDictionary dictionary;
    private WordChainGame game;

    @Override
    public void onEnable() {

        instance = this;
        saveDefaultConfig();
        ConfigUpdater.update(this);

        // Load the English word list (words.txt inside the jar)
        dictionary = new WordDictionary();
        dictionary.load(this);

        game = new WordChainGame(this, dictionary);

        // Command
        WordChainCommand wordChainCommand = new WordChainCommand(this, game);
        PluginCommand command = getCommand("wordchain");
        if (command != null) {
            command.setExecutor(wordChainCommand);
            command.setTabCompleter(wordChainCommand);
        }

        // Listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this, game), this);
        getServer().getPluginManager().registerEvents(new QuitListener(game), this);

        getLogger().info("WordChain Enabled!");
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        getLogger().info("WordChain Disabled!");
    }

    public static WordChain getInstance() {
        return instance;
    }
}
