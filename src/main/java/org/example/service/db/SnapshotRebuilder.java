package org.example.service.db;

import org.example.collector.DatabaseManager;
import org.example.model.ChampionStats;
import org.example.model.StatsSnapshot;
import org.example.model.WinPlay;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class SnapshotRebuilder {

    public StatsSnapshot rebuildSnapshot() throws SQLException {
        try (Connection conn = DatabaseManager.connect()) {
            Map<Integer, String> championNames = getChampionNames(conn);
            Map<String, ChampionStats> championStatsMap = new HashMap<>();

            for (String name : championNames.values()) {
                championStatsMap.put(name, new ChampionStats())
            }

            loadOverallChampionStats(conn, championStatsMap, championNames);
            loadRoleStats(conn, championStatsMap, championNames);
            loadSynergyStats(conn, championStatsMap, championNames);
            loadCounterStats(conn, championStatsMap, championNames);

            return new StatsSnapshot(championStatsMap);
        }
    }

    private Map<Integer, String> getChampionNames(Connection conn) throws SQLException {
        Map<Integer, String> championNames = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name FROM champions")) {
            while (rs.next()) {
                championNames.put(rs.getInt("id"), rs.getString("name"));
            }
        }
        return championNames;
    }

    private void loadOverallChampionStats(Connection conn, Map<String, ChampionStats> statsMap, Map<Integer, String> championNames) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT champion_id, wins, plays FROM champion_stats")) {
            while (rs.next()) {
                String name = championNames.get(rs.getInt("champion_id"));
                if (name != null) {
                    ChampionStats existingStats = statsMap.get(name);
                    ChampionStats newStats = new ChampionStats(
                        rs.getInt("wins"),
                        rs.getInt("plays"),
                        existingStats.getRoleCounts(),  
                        existingStats.getSynergy(),    
                        existingStats.getCounters()     
                    );
                    statsMap.put(name, newStats);
                }
            }
        }
    }

    private void loadRoleStats(Connection conn, Map<String, ChampionStats> statsMap, Map<Integer, String> championNames) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT champion_id, role, plays FROM role_stats")) {
            while (rs.next()) {
                String name = championNames.get(rs.getInt("champion_id"));
                if (name != null) {
                    ChampionStats stats = statsMap.get(name);
                    stats.getRoleCounts().put(rs.getString("role"), rs.getInt("plays"));
                }
            }
        }
    }

    private void loadSynergyStats(Connection conn, Map<String, ChampionStats> statsMap, Map<Integer, String> championNames) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT champion_id, ally_id, wins, plays FROM synergy_stats")) {
            while (rs.next()) {
                String champ1Name = championNames.get(rs.getInt("champion_id"));
                String champ2Name = championNames.get(rs.getInt("ally_id"));
                if (champ1Name != null && champ2Name != null) {
                    WinPlay wp = new WinPlay(rs.getInt("wins"), rs.getInt("plays"));
                    statsMap.get(champ1Name).getSynergy().put(champ2Name, wp);
                    statsMap.get(champ2Name).getSynergy().put(champ1Name, wp);
                }
            }
        }
    }

    private void loadCounterStats(Connection conn, Map<String, ChampionStats> statsMap, Map<Integer, String> championNames) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT champion_id, enemy_id, wins, plays FROM counter_stats")) {
            while (rs.next()) {
                String champName = championNames.get(rs.getInt("champion_id"));
                String enemyName = championNames.get(rs.getInt("enemy_id"));
                if (champName != null && enemyName != null) {
                    WinPlay wp = new WinPlay(rs.getInt("wins"), rs.getInt("plays"));
                    statsMap.get(champName).getCounters().put(enemyName, wp);
                }
            }
        }
    }
}
