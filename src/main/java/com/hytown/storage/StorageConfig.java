package com.hytown.storage;

/**
 * Configuration for storage backends.
 * Supports flatfile (JSON), SQL (MySQL), and MongoDB.
 */
public class StorageConfig {

    // Which backend to load data from
    private String loadFrom = "flatfile";  // "flatfile", "sql", or "mongodb"

    // Which backend to save data to
    private String saveTo = "flatfile";    // "flatfile", "sql", or "mongodb"

    // SQL configuration
    private SQLConfig sql = new SQLConfig();

    // MongoDB configuration
    private MongoConfig mongodb = new MongoConfig();

    // ==================== GETTERS ====================

    public String getLoadFrom() {
        return loadFrom != null ? loadFrom.toLowerCase() : "flatfile";
    }

    public String getSaveTo() {
        return saveTo != null ? saveTo.toLowerCase() : "flatfile";
    }

    public SQLConfig getSql() {
        return sql != null ? sql : new SQLConfig();
    }

    public MongoConfig getMongodb() {
        return mongodb != null ? mongodb : new MongoConfig();
    }

    // ==================== SETTERS ====================

    public void setLoadFrom(String loadFrom) {
        this.loadFrom = loadFrom;
    }

    public void setSaveTo(String saveTo) {
        this.saveTo = saveTo;
    }

    public void setSql(SQLConfig sql) {
        this.sql = sql;
    }

    public void setMongodb(MongoConfig mongodb) {
        this.mongodb = mongodb;
    }

    // ==================== SQL CONFIG ====================

    public static class SQLConfig {
        private String host = "localhost";
        private int port = 3306;
        private String database = "hytown";
        private String username = "root";
        private String password = "";
        private String tablePrefix = "hytown_";

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getDatabase() { return database; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getTablePrefix() { return tablePrefix != null ? tablePrefix : "hytown_"; }

        public void setHost(String host) { this.host = host; }
        public void setPort(int port) { this.port = port; }
        public void setDatabase(String database) { this.database = database; }
        public void setUsername(String username) { this.username = username; }
        public void setPassword(String password) { this.password = password; }
        public void setTablePrefix(String tablePrefix) { this.tablePrefix = tablePrefix; }

        /**
         * Build JDBC connection URL.
         */
        public String getJdbcUrl() {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true",
                    host, port, database);
        }
    }

    // ==================== MONGODB CONFIG ====================

    public static class MongoConfig {
        private String connectionString = "mongodb://localhost:27017";
        private String database = "hytown";
        private String collectionPrefix = "hytown_";

        public String getConnectionString() { return connectionString; }
        public String getDatabase() { return database; }
        public String getCollectionPrefix() { return collectionPrefix != null ? collectionPrefix : "hytown_"; }

        public void setConnectionString(String connectionString) { this.connectionString = connectionString; }
        public void setDatabase(String database) { this.database = database; }
        public void setCollectionPrefix(String collectionPrefix) { this.collectionPrefix = collectionPrefix; }
    }

    // ==================== VALIDATION ====================

    /**
     * Check if SQL is needed (either load or save uses SQL).
     */
    public boolean needsSQL() {
        return "sql".equals(getLoadFrom()) || "sql".equals(getSaveTo());
    }

    /**
     * Check if MongoDB is needed (either load or save uses MongoDB).
     */
    public boolean needsMongoDB() {
        return "mongodb".equals(getLoadFrom()) || "mongodb".equals(getSaveTo());
    }

    /**
     * Check if flatfile is needed (either load or save uses flatfile).
     */
    public boolean needsFlatfile() {
        return "flatfile".equals(getLoadFrom()) || "flatfile".equals(getSaveTo());
    }
}
