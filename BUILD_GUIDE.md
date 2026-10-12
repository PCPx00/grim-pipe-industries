# How to build the Grim Pipe Industries jar

You only need Java 21. Gradle, NeoForge and Minecraft download automatically the first time.

## 1. Install Java 21 (one time)

1. Download **Eclipse Temurin JDK 21** from https://adoptium.net (pick the Windows .msi, x64).
2. Run the installer. On the "Custom Setup" screen, turn on **Set JAVA_HOME variable** and **Add to PATH**.
3. Open a new Command Prompt and run:

   ```
   java -version
   ```

   It should say `21.something`. If it says 8, 17 or "not recognized", restart your PC and try again.

## 2. Unzip the project

Unzip `grimpipes.zip` somewhere with a short path, for example `C:\mods\grimpipes`.
The folder should contain `build.gradle`, `gradlew.bat` and a `src` folder.

## Easiest way: double-click BUILD.bat

It checks for Java, runs the build, and opens the folder with the jar when it finishes. If you use it, skip steps 3-5.

## 3. Open a terminal in that folder

Open the folder in File Explorer, click the address bar, type `cmd` and press Enter.

## 4. Build

```
gradlew.bat build
```

(In PowerShell use `.\gradlew build`. On Mac or Linux use `./gradlew build`.)

The first build takes 5-20 minutes because it downloads Minecraft and NeoForge. Later builds take under a minute.
It's done when you see **BUILD SUCCESSFUL**.

## 5. Find the jar

The jar is in:

```
build\libs\grimpipes-1.3.1.jar
```

## 6. Put it in Nine Craft

1. In the CurseForge app, right-click the Nine Craft profile and choose **Open Folder**.
2. Open the `mods` folder and copy the jar in.
3. Launch the pack.

## Optional: test without the modpack

```
gradlew.bat runClient
```

This opens a dev copy of Minecraft with only this mod loaded, which is faster for quick checks.

## If something goes wrong

| Problem | Fix |
| --- | --- |
| `JAVA_HOME is not set` or `java is not recognized` | Reinstall Temurin 21 with "Set JAVA_HOME" turned on, then open a new terminal. |
| `Unsupported class file major version` | Your PC is using an older Java. Run `java -version` and make sure it's 21. |
| `error:` lines with `.java` file names | A compile error. Copy the full error text and send it to Claude to fix. |
| Build hangs on "Downloading" | Check your internet connection or antivirus, then run the command again. |
| Game crashes on launch | Send the crash report from the `crash-reports` folder to Claude. |

## Editing in an IDE (optional)

Open the folder in IntelliJ IDEA Community Edition (File > Open). It imports the project automatically.
Use the Gradle panel on the right: `Tasks > build > build` makes the jar, `Tasks > mod development > runClient` launches the game.
