![Mejais Logo](src/main/resources/org/example/images/logo/logot.png)

# Mejais

Mejais is your personal League of Legends draft assistant, designed to give you a competitive edge before the game even starts. By analyzing millions of high-tier matches, Mejais provides real-time insights and recommendations during Champion Select.

> Built by a 24-year-old programming student who tinkers with APIs and Spring Boot projects by day and crafted Mejais as a passion project by night.

## How to Use Mejais

### 1. Live Game Analysis
While in an active Champion Select in the League of Legends client, Mejais will automatically mirror the picks and bans in the 'Game' view. It analyzes team compositions and suggests the strongest champions for you to play based on synergies, counters, and overall team strength.

### 2. Simulate a Draft
Want to theorycraft or prepare for a specific matchup? You can manually simulate a draft. Simply click on any empty champion slot to open the champion grid and make a selection. To clear a pick, just right-click on the champion's icon.

### 3. Explore Champions
Navigate to the 'Champions' tab to see detailed statistics for every champion. Select a champion to view their best and worst matchups, helping you understand their strengths and weaknesses in lane.

### 4. Role Assignment
Roles are typically assigned automatically based on the most common positions for each champion. If you're planning an off-meta pick, you can manually change the assigned role to refine the recommendations for your team's unique strategy.

### 5. Check for Match Updates
When the "Check for match database update" link lights up in the bottom-left corner of the Game view, new matchup data is available (typically a few days after Riot publishes a new patch). Click it to download the latest snapshot and keep your recommendations fresh.

Ready to climb? Let Mejais guide your draft!

## Legal
Mejais isn't endorsed by Riot Games and doesn't reflect the views or opinions of Riot Games or anyone officially involved in producing or managing Riot Games properties. Riot Games, and all associated properties are trademarks or registered trademarks of Riot Games, Inc.

## Packaging a Windows Installer

Ship a self-contained Windows build (portable zip + installer) with the helper script:

1. Install JDK 17+ so `jpackage` is available. Either add it to `PATH`, set `JAVA_HOME`, or point `JPACKAGE_PATH` directly at `jpackage.exe`.
2. Make sure `data/snapshot.db` contains the snapshot you want to distribute (the client reads/writes this file at runtime).
3. Run `powershell packaging/windows/package.ps1`. Pass `-Version 1.4.0` if you want the installer/app metadata to differ from the `pom.xml` version.
   - The script automatically downloads the matching JavaFX jmods for Windows x64 into `target/javafx/`, feeds them to `jlink`, and builds a trimmed runtime image (set `JAVAFX_JMODS` to reuse a local cache or `JAVAFX_PLATFORM` for ARM builds).
   - If `riotgames.pem` exists under `C:\Riot Games\League of Legends` or `%LOCALAPPDATA%\Riot Games\Riot Client\Config`, it will be copied into the packaged `data/` folder so SSL handshakes with the League client stay trusted.
   - The script also writes `data/lockfile.override` inside the app image so the runtime always targets the League Client lockfile path, even when installed through jpackage.

The script performs `mvn package`, stages the shaded JAR together with the `data/` folder, and invokes `jpackage` twice:

- `target/dist/Mejais-<version>-portable.zip` - unzip and run `Mejais.exe` directly (no installer).
- `target/dist/Mejais-<version>.exe` - a per-user installer that bundles a private JRE, JavaFX, shortcuts, and the snapshot database.

Both artifacts include everything the runtime needs, so end users don't have to install Java or download additional JavaFX modules.

Whenever you push an updated `data/snapshot.db` to GitHub, the installed app will light up the "Check for match database update" link automatically so users can grab the latest matchup data straight from the Game view.

### Optional: Code signing

Unsigned executables will always trigger Windows Defender SmartScreen warnings. Provide a valid Authenticode certificate (issued by a commercial CA) and set these environment variables before running the packaging script to embed a trusted signature automatically:

- `SIGNING_CERT_PATH` – path to your `.pfx` (or `.p12`) code-signing certificate file.
- `SIGNING_CERT_PASSWORD` – password for the certificate (omit if the key is not protected).
- `SIGNTOOL_PATH` – optional override if `signtool.exe` isn't on `PATH` (usually `C:\Program Files (x86)\Windows Kits\10\bin\<version>\x64\signtool.exe`).
- `SIGNING_TIMESTAMP_URL` – optional timestamp authority URL (defaults to DigiCert's TSA).

With those variables in place the build script signs both `Mejais.exe` inside the portable app image and the generated installer, which keeps SmartScreen from flagging downloads as “unknown publisher.”
