package org.example.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.model.StatsSnapshot;

import java.io.File;
import java.io.IOException;

public class SnapshotStore {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final File snapshotFile;

    public SnapshotStore(File snapshotFile) {
        this.snapshotFile = snapshotFile;
    }

    public void save(StatsSnapshot snapshot) throws IOException {
        mapper.writeValue(snapshotFile, snapshot);
    }

    public StatsSnapshot load() throws IOException {
        if (!snapshotFile.exists()) return null;
        return mapper.readValue(snapshotFile, StatsSnapshot.class);
    }
}
