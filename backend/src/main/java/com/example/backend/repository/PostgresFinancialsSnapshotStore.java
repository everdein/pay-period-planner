package com.example.backend.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
@Profile("postgres")
public class PostgresFinancialsSnapshotStore implements FinancialsSnapshotStore {

  private static final String LOAD_ACTIVE_SNAPSHOT =
      """
      select version, snapshot_json::text
      from financial_snapshot_document
      where active = true
      order by id
      limit 1
      """;
  private static final String UPDATE_ACTIVE_SNAPSHOT =
      """
      update financial_snapshot_document
      set snapshot_json = ?::jsonb,
          version = ?,
          updated_at = now()
      where active = true
      """;
  private static final String INSERT_ACTIVE_SNAPSHOT =
      """
      insert into financial_snapshot_document (active, version, snapshot_json)
      values (true, ?, ?::jsonb)
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final Path dataPath;
  private final Path examplePath;

  public PostgresFinancialsSnapshotStore(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      @Value("${financials.data.path:data/financials.local.json}") Path dataPath,
      @Value("${financials.example-data.path:data/financials.example.json}") Path examplePath) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.dataPath = dataPath;
    this.examplePath = examplePath;
  }

  @Override
  public FinancialsData load() {
    StoredSnapshot storedSnapshot =
        jdbcTemplate.query(
            LOAD_ACTIVE_SNAPSHOT,
            (resultSet) ->
                resultSet.next()
                    ? new StoredSnapshot(resultSet.getLong(1), resultSet.getString(2))
                    : null);

    if (storedSnapshot == null) {
      FinancialsData seedData = seedData().withVersion(1);
      save(seedData);
      return seedData;
    }

    try {
      return objectMapper
          .readValue(storedSnapshot.snapshotJson(), FinancialsData.class)
          .withVersion(storedSnapshot.version());
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "Unable to read financial snapshot from PostgreSQL", exception);
    }
  }

  @Override
  public void save(FinancialsData data) {
    String snapshotJson = snapshotJson(data);
    int updated = jdbcTemplate.update(UPDATE_ACTIVE_SNAPSHOT, snapshotJson, data.version());

    if (updated == 0) {
      jdbcTemplate.update(INSERT_ACTIVE_SNAPSHOT, data.version(), snapshotJson);
    }
  }

  private record StoredSnapshot(long version, String snapshotJson) {}

  private String snapshotJson(FinancialsData data) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "Unable to serialize financial snapshot for PostgreSQL", exception);
    }
  }

  private FinancialsData seedData() {
    if (Files.exists(dataPath)) {
      return readSeedData(dataPath);
    }

    if (!Files.exists(examplePath)) {
      return FinancialsData.empty();
    }

    return readSeedData(examplePath);
  }

  private FinancialsData readSeedData(Path seedPath) {
    try {
      return objectMapper.readValue(seedPath.toFile(), FinancialsData.class);
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "Unable to load seed financial data from " + seedPath, exception);
    }
  }
}
