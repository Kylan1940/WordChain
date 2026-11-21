[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P1DB75T)
# Description
English Word Chain minigame plugin for Minecraft Java Server. Take turns typing English words in chat, each word must start with the last letter of the previous word. Run out of time too many times and you are out! If you have a question, please make an [issue](https://github.com/Kylan1940/WordChain/issues/new)

# Features
>- Simple
>- Configuration (settings and all messages)
>- English word chain with ~370,000 words dictionary
>- Lives (game health) for the whole game, plus turn health (tries) that resets every turn
>- Third person view: a camera glides to the player whose turn it is
>- The required letter and every typed word (white, wrong or right) float above the player
>- Turn timer that gets shorter every round
>- Timer shown on the action bar
>- Wrong word sound (falling anvil)
>- Winner reward commands
>- Tab completion
>- Compatible with Spigot, CraftBukkit, Paper and Paper forks (Purpur, Pufferfish, Leaves)

# How to Play
1. Type `/wordchain join` to create a room. You become the **host**.
2. Invite players with `/wordchain invite <player>`. The invited player types `/wordchain accept` to join (invites expire after 60 seconds, `/wordchain deny` to decline).
3. When there are enough players, the host types `/wordchain start`. After the countdown the game begins.
4. Players take turns in random order. When it is your turn, **type your word in chat**.
5. The first word can start with any letter. After that, your word must start with the **last letter of the previous word**.
6. A word must be a real English word, only letters (a-z), have the minimum length, and can only be used **once** per game.
7. Every turn you have a few **tries** (turn health). A wrong word costs 1 try, and you can try again until the tries or the time run out.
8. If you use all your tries, or the time runs out, you lose 1 **life** (game health) and the turn goes to the next player. At 0 lives you are eliminated.
9. Leaving the room (or disconnecting) in the middle of a match eliminates you.
10. The last player standing wins! The camera shows the winner, then everyone gets their game mode and location back. The room is closed after the game.

**Third person view:** players spectate from a camera that follows the current turn, with the required letter (gold) and typed words (white) shown above them. Every player is shown with their own skin (Minecraft 1.21.9+; older versions show just their head). Disable with `stage.enabled: false`.

Many rooms can run at the same time. If the host leaves before the game starts, the next player becomes the host.

# Download
| Version | JAR                                                                                                | Minecraft Support | Software Support                                           |
|---------|----------------------------------------------------------------------------------------------------|-------------------|------------------------------------------------------------|
| 1.0.0   | [DOWNLOAD](https://github.com/Kylan1940/WordChain/releases/download/1.0.0/WordChain-1.0.0.jar)    | 1.21+             | Spigot, CraftBukkit, Paper, Purpur, Pufferfish, Leaves     |

# Installation
1. Download the [plugin](https://github.com/Kylan1940/WordChain/releases/download/1.0.0/WordChain-1.0.0.jar)
2. Put plugin to **plugins** file
3. Restart the server

# Permission
| Command                     | Description                         | Permission               | Default | Console Support | Aliases |
|-----------------------------|-------------------------------------|--------------------------|---------|-----------------|---------|
| /wordchain                  | Show help                           | NONE                     | YES     | YES             | /wc     |
| /wordchain join             | Create a room (you are the host)    | wordchain-play.command   | YES     | NO              | /wc     |
| /wordchain invite <player>  | Invite a player to your room (host) | wordchain-play.command   | YES     | NO              | /wc     |
| /wordchain accept [host]    | Accept an invite                    | wordchain-play.command   | YES     | NO              | /wc     |
| /wordchain deny             | Deny your invites                   | wordchain-play.command   | YES     | NO              | /wc     |
| /wordchain start            | Start the game (host)               | wordchain-play.command   | YES     | NO              | /wc     |
| /wordchain leave            | Leave your room                     | wordchain-play.command   | YES     | NO              | /wc     |
| /wordchain stop             | Close your room (host)              | wordchain-play.command   | YES     | NO              | /wc     |
| /wordchain status           | Show your room status               | wordchain-play.command   | YES     | NO              | /wc     |
| /wordchain stop <host>      | Close the room of a host            | wordchain-stop.command   | OP      | YES             | /wc     |
| /wordchain reload           | Reload the config                   | wordchain-reload.command | OP      | YES             | /wc     |

# Configuration
Settings are in `plugins/WordChain/config.yml` (`src/main/resources/config.yml` in the source code). Use `/wordchain reload` to apply changes.
```yaml
room:
  invite-timeout: 60           # seconds an invite stays valid

game:
  min-players: 2               # minimum players before the host can /wc start
  lives: 3                     # lives per player for the whole game (1 = instant elimination)
  turn-health: 3               # wrong words allowed in ONE turn (resets every turn)
  max-players: 10              # per room
  start-countdown: 10          # seconds between /wc start and the real start
  turn-time: 20                # seconds per turn in the first round
  time-decrease-per-round: 1   # turn time gets shorter every round...
  min-turn-time: 8             # ...but never below this
  min-word-length: 3
```

# Stage (third person view)
```yaml
stage:
  enabled: true
  radius: 5.0            # circle of seats around the host
  camera-distance: 3.5   # camera distance in front of the player
  camera-height: 1.4
  use-player-skin: true  # real skin figure (1.21.9+), falls back to head-only on older versions
  move-ticks: 25         # how long the camera glides to the next player
  end-delay: 80          # winner stays in view (ticks)
  text-height: 1.9
  text-scale: 1.5
```
Letter, word and name colors are in `stage` too (`letter-format`, `attempt-format`, ...).

# Sounds
A falling anvil sound plays for everyone in the game when a player types a wrong word. Change it with any [Minecraft sound name](https://minecraft.wiki/w/Sounds.json), or leave it empty to turn it off.
```yaml
sounds:
  wrong-answer: "block.anvil.land"
  volume: 1.0
  pitch: 1.0
```

# Rewards
Commands run by the console when someone wins. `%player%` is the winner name.
```yaml
rewards:
  winner-commands:
    - "give %player% diamond 3"
```