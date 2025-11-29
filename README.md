![Mejais Logo](src/main/resources/org/example/images/logo/logot.png)

# Mejais

Mejais is your personal League of Legends draft assistant, designed to give you a competitive edge before the game even starts. By analyzing millions of high-tier matches, Mejais provides real-time insights and recommendations during Champion Select.

## How to Use Mejais

### 1. Live Game Analysis
While in an active Champion Select in the League of Legends client, Mejais will automatically mirror the picks and bans in the 'Game' view. It analyzes team compositions and suggests the strongest champions for you to play based on synergies, counters, and overall team strength.

### 2. Simulate a Draft
Want to theorycraft or prepare for a specific matchup? You can manually simulate a draft. Simply click on any empty champion slot to open the champion grid and make a selection. To clear a pick, just right-click on the champion's icon.

### 3. Explore Champions
Navigate to the 'Champions' tab to see detailed statistics for every champion. Select a champion to view their best and worst matchups, helping you understand their strengths and weaknesses in lane.

### 4. Role Assignment
Roles are typically assigned automatically based on the most common positions for each champion. If you're planning an off-meta pick, you can manually change the assigned role to refine the recommendations for your team's unique strategy.

Ready to climb? Let Mejais guide your draft!

## Legal
Mejais isn't endorsed by Riot Games and doesn't reflect the views or opinions of Riot Games or anyone officially involved in producing or managing Riot Games properties. Riot Games, and all associated properties are trademarks or registered trademarks of Riot Games, Inc.

## Packaging a Windows Installer

To ship a standalone EXE that bundles a private JRE:

1. Install JDK 17+ so `jpackage` is available (set `JAVA_HOME` or `JPACKAGE_PATH`).
2. Place your latest `data/snapshot.db` under the project’s `data/` folder.
3. Run `powershell packaging/windows/package.ps1 -Version 1.3.0`.

The script runs `mvn clean package`, copies the app JAR and snapshot into a staging area, and invokes `jpackage` to create `target/installer/dist/Mejais-1.3.0.exe`. Upload that EXE to your website—players won’t need Java installed locally.
