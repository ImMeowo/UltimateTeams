package dev.xf3d3.ultimateteams.database;

import com.google.gson.JsonSyntaxException;
import com.zaxxer.hikari.HikariDataSource;
import dev.xf3d3.ultimateteams.UltimateTeams;
import dev.xf3d3.ultimateteams.models.Team;
import dev.xf3d3.ultimateteams.models.TeamPlayer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class MySqlDatabase extends Database {
    private static final String DATA_POOL_NAME = "UltimateTeamsHikariPool";

    private final String driverClass;
    private HikariDataSource dataSource;
    final String type;

    public MySqlDatabase(@NotNull UltimateTeams plugin) {
        super(plugin);

        this.type = plugin.getSettings().getDatabaseType().getProtocol();
        this.driverClass = plugin.getSettings().getDatabaseType() == Type.MARIADB ? "org.mariadb.jdbc.Driver" : "com.mysql.cj.jdbc.Driver";
    }

    /**
     * Fetch the auto-closeable connection from the hikariDataSource
     *
     * @return The {@link Connection} to the MySQL database
     * @throws SQLException if the connection fails for some reason
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }


    /**
     * Used to set up a connection from the provided data
     */
    private void setConnection() {
        plugin.log(Level.INFO, "Attempting to connect to database");

        dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClass);
        dataSource.setJdbcUrl(String.format("jdbc:%s://%s:%s/%s%s",
                type,
                plugin.getSettings().getMySqlHost(),
                plugin.getSettings().getMySqlPort(),
                plugin.getSettings().getMySqlDatabase(),
                plugin.getSettings().getMySqlConnectionParameters()
        ));

        // Authenticate
        dataSource.setUsername(plugin.getSettings().getMySqlUsername());
        dataSource.setPassword(plugin.getSettings().getMySqlPassword());

        // Set connection pool options
        dataSource.setMaximumPoolSize(plugin.getSettings().getMySqlConnectionPoolSize());
        dataSource.setMinimumIdle(plugin.getSettings().getMySqlConnectionPoolIdle());
        dataSource.setMaxLifetime(plugin.getSettings().getMySqlConnectionPoolLifetime());
        dataSource.setKeepaliveTime(plugin.getSettings().getMySqlConnectionPoolKeepAlive());
        dataSource.setConnectionTimeout(plugin.getSettings().getMySqlConnectionPoolTimeout());
        dataSource.setPoolName(DATA_POOL_NAME);

        // Set additional connection pool properties
        dataSource.setDataSourceProperties(new Properties() {{
            put("cachePrepStmts", "true");
            put("prepStmtCacheSize", "250");
            put("prepStmtCacheSqlLimit", "2048");
            put("useServerPrepStmts", "true");
            put("useLocalSessionState", "true");
            put("useLocalTransactionState", "true");
            put("rewriteBatchedStatements", "true");
            put("cacheResultSetMetadata", "true");
            put("cacheServerConfiguration", "true");
            put("elideSetAutoCommits", "true");
            put("maintainTimeStats", "false");
        }});
    }

    public void initialize() {
        // Establish connection
        this.setConnection();

        // Create tables
        try (Connection connection = getConnection()) {
            plugin.log(Level.INFO, "MySQL Database Connected!");

            try (Statement statement = connection.createStatement()) {
                for (String tableCreationStatement : getSchema(String.format("database/%s_schema.sql", type))) {
                    statement.execute(tableCreationStatement);
                }
            } catch (SQLException e) {
                setLoaded(false);

                throw new IllegalStateException("Failed to create database tables. Please ensure you are running MySQL v8.0+ " +
                        "and that your connecting user account has privileges to create tables.", e);
            }

            setLoaded(true);

        } catch (SQLException | IOException e) {
            setLoaded(false);

            throw new IllegalStateException("Failed to establish a connection to the MySQL database. " +
                    "Please check the supplied database credentials in the config file", e);
        }

        plugin.getLogger().info("Database tables created");
    }

    public List<Team> getAllTeams() {
        final List<Team> teams = new ArrayList<>();
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(format("""
                    SELECT `id`, `data`
                    FROM `%team_table%`
                    """))) {
                final ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    final String data = new String(resultSet.getBytes("data"), StandardCharsets.UTF_8);
                    final Team team = plugin.getGson().fromJson(data, Team.class);

                    if (team != null) {
                        team.setId(resultSet.getInt("id"));
                        teams.add(team);
                    }
                }
            }
        } catch (SQLException | JsonSyntaxException e) {
            plugin.log(Level.SEVERE, "Failed to fetch list of teams from table", e);
        }

        return teams;
    }

    public void createPlayer(@NotNull TeamPlayer teamplayer) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(format("""
                    INSERT INTO `%user_table%` (`uuid`, `username`, `isBedrock`, `bedrockUUID`, `canChatSpy`)
                    VALUES (?, ?, ?, ?, ?)
                    """))) {

                statement.setString(1, String.valueOf(teamplayer.getJavaUUID()));
                statement.setString(2, teamplayer.getLastPlayerName());
                statement.setBoolean(3, teamplayer.isBedrockPlayer());
                statement.setString(4, teamplayer.getBedrockUUID());
                statement.setBoolean(5, teamplayer.isCanChatSpy());

                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to create team in table", e);
        }
    }

    public void updatePlayer(@NotNull TeamPlayer teamplayer) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(format("""
                    UPDATE `%user_table%`
                    SET `uuid` = ?, `username` = ?, `isBedrock` = ?, `bedrockUUID` = ?, `canChatSpy` = ?
                    WHERE `uuid` = ?
                    """))) {

                statement.setString(1, String.valueOf(teamplayer.getJavaUUID()));
                statement.setString(2, teamplayer.getLastPlayerName());
                statement.setBoolean(3, teamplayer.isBedrockPlayer());
                statement.setString(4, teamplayer.getBedrockUUID());
                statement.setBoolean(5, teamplayer.isCanChatSpy());

                statement.setString(6, String.valueOf(teamplayer.getJavaUUID()));

                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to create team in table", e);
        }
    }

    public Optional<TeamPlayer> getPlayer(@NotNull UUID uuid) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(format("""
                    SELECT *
                    FROM `%user_table%`
                    WHERE `uuid` = ?
                    """))) {
                statement.setString(1, String.valueOf(uuid));

                final ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    final TeamPlayer teamPlayer = new TeamPlayer(
                            resultSet.getString("uuid"),
                            resultSet.getString("username"),
                            resultSet.getBoolean("isBedrock"),
                            resultSet.getString("bedrockUUID"),
                            resultSet.getBoolean("canChatSpy")
                    );

                    return Optional.of(teamPlayer);
                }
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to get player from table", e);
        }
        return Optional.empty();
    }

    public Optional<TeamPlayer> getPlayer(@NotNull String name) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(format("""
                    SELECT *
                    FROM `%user_table%`
                    WHERE `username` = ?
                    """))) {
                statement.setString(1, name);

                final ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    final TeamPlayer teamPlayer = new TeamPlayer(
                            resultSet.getString("uuid"),
                            resultSet.getString("username"),
                            resultSet.getBoolean("isBedrock"),
                            resultSet.getString("bedrockUUID"),
                            resultSet.getBoolean("canChatSpy")
                    );

                    return Optional.of(teamPlayer);
                }
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to get player from table", e);
        }
        return Optional.empty();
    }

    public void createTeam(@NotNull Team team, @NotNull UUID uuid) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(format("""
                    INSERT INTO `%team_table%` (`uuid`, `name`, `data`)
                    VALUES (?, ?, ?)
                    """))) {

                statement.setString(1, String.valueOf(uuid));
                statement.setString(2, team.getTeamFinalName());
                statement.setBytes(3, plugin.getGson().toJson(team).getBytes(StandardCharsets.UTF_8));

                statement.executeUpdate();

                final ResultSet insertedRow = statement.getGeneratedKeys();
                if (insertedRow.next()) {
                    team.setId(insertedRow.getInt(1));
                }
            }
        } catch (SQLException | JsonSyntaxException e) {
            plugin.log(Level.SEVERE, "Failed to create team in table", e);
        }
    }

    public void updateTeam(@NotNull Team team) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(format("""
                    UPDATE `%team_table%`
                    SET `name` = ?, `data` = ?
                    WHERE `id` = ?
                    """))) {

                statement.setString(1, team.getTeamFinalName());
                statement.setBytes(2, plugin.getGson().toJson(team).getBytes(StandardCharsets.UTF_8));
                statement.setInt(3, team.getId());

                statement.executeUpdate();
            }
        } catch (SQLException | JsonSyntaxException e) {
            plugin.log(Level.SEVERE, "Failed to update team in table", e);
        }
    }

    public void deleteTeam(@NotNull UUID uuid) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(format("""
                    DELETE FROM `%team_table%`
                    WHERE `uuid` = ?
                    """))) {

                statement.setString(1, String.valueOf(uuid));

                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.log(Level.SEVERE, "Failed to delete team in table", e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}