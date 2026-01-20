package com.hytown.storage;

/**
 * Configuration for storage backends.
 * Supports flatfile (JSON), SQL (MySQL/MariaDB), and MongoDB.
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

        // Connection pool settings
        private int minPoolSize = 2;
        private int maxPoolSize = 10;
        private int connectionTimeout = 5000;  // ms
        private int idleTimeout = 300000;      // 5 minutes in ms
        private int maxLifetime = 1800000;     // 30 minutes in ms

        // Connection options
        private boolean useSSL = false;
        private boolean allowPublicKeyRetrieval = true;
        private boolean autoReconnect = true;
        private String characterEncoding = "utf8mb4";

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getDatabase() { return database; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getTablePrefix() { return tablePrefix != null ? tablePrefix : "hytown_"; }

        public int getMinPoolSize() { return Math.max(1, minPoolSize); }
        public int getMaxPoolSize() { return Math.max(getMinPoolSize(), maxPoolSize); }
        public int getConnectionTimeout() { return connectionTimeout; }
        public int getIdleTimeout() { return idleTimeout; }
        public int getMaxLifetime() { return maxLifetime; }
        public boolean isUseSSL() { return useSSL; }
        public boolean isAllowPublicKeyRetrieval() { return allowPublicKeyRetrieval; }
        public boolean isAutoReconnect() { return autoReconnect; }
        public String getCharacterEncoding() { return characterEncoding; }

        public void setHost(String host) { this.host = host; }
        public void setPort(int port) { this.port = port; }
        public void setDatabase(String database) { this.database = database; }
        public void setUsername(String username) { this.username = username; }
        public void setPassword(String password) { this.password = password; }
        public void setTablePrefix(String tablePrefix) { this.tablePrefix = tablePrefix; }
        public void setMinPoolSize(int minPoolSize) { this.minPoolSize = minPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        public void setIdleTimeout(int idleTimeout) { this.idleTimeout = idleTimeout; }
        public void setMaxLifetime(int maxLifetime) { this.maxLifetime = maxLifetime; }
        public void setUseSSL(boolean useSSL) { this.useSSL = useSSL; }
        public void setAllowPublicKeyRetrieval(boolean allowPublicKeyRetrieval) { this.allowPublicKeyRetrieval = allowPublicKeyRetrieval; }
        public void setAutoReconnect(boolean autoReconnect) { this.autoReconnect = autoReconnect; }
        public void setCharacterEncoding(String characterEncoding) { this.characterEncoding = characterEncoding; }

        /**
         * Build JDBC connection URL with all configured options.
         */
        public String getJdbcUrl() {
            StringBuilder url = new StringBuilder();
            url.append("jdbc:mysql://")
               .append(host)
               .append(":")
               .append(port)
               .append("/")
               .append(database);

            // Add connection parameters
            url.append("?useSSL=").append(useSSL);
            url.append("&allowPublicKeyRetrieval=").append(allowPublicKeyRetrieval);
            url.append("&autoReconnect=").append(autoReconnect);
            url.append("&characterEncoding=").append(characterEncoding);
            url.append("&useUnicode=true");
            url.append("&serverTimezone=UTC");
            url.append("&connectTimeout=").append(connectionTimeout);

            return url.toString();
        }

        @Override
        public String toString() {
            return "SQLConfig{host='" + host + "', port=" + port + ", database='" + database +
                   "', username='" + username + "', tablePrefix='" + tablePrefix +
                   "', poolSize=" + minPoolSize + "-" + maxPoolSize + "}";
        }
    }

    // ==================== MONGODB CONFIG ====================

    public static class MongoConfig {
        private String connectionString = "mongodb://localhost:27017";
        private String database = "hytown";
        private String collectionPrefix = "hytown_";

        // Connection pool settings
        private int minPoolSize = 2;
        private int maxPoolSize = 10;
        private int connectTimeout = 5000;     // ms
        private int socketTimeout = 30000;     // ms
        private int serverSelectionTimeout = 5000;  // ms

        // Write concern options
        private String writeConcern = "majority";  // "1", "majority", "journaled"
        private String readConcern = "local";      // "local", "majority", "linearizable"

        public String getConnectionString() { return connectionString; }
        public String getDatabase() { return database; }
        public String getCollectionPrefix() { return collectionPrefix != null ? collectionPrefix : "hytown_"; }

        public int getMinPoolSize() { return Math.max(1, minPoolSize); }
        public int getMaxPoolSize() { return Math.max(getMinPoolSize(), maxPoolSize); }
        public int getConnectTimeout() { return connectTimeout; }
        public int getSocketTimeout() { return socketTimeout; }
        public int getServerSelectionTimeout() { return serverSelectionTimeout; }
        public String getWriteConcern() { return writeConcern; }
        public String getReadConcern() { return readConcern; }

        public void setConnectionString(String connectionString) { this.connectionString = connectionString; }
        public void setDatabase(String database) { this.database = database; }
        public void setCollectionPrefix(String collectionPrefix) { this.collectionPrefix = collectionPrefix; }
        public void setMinPoolSize(int minPoolSize) { this.minPoolSize = minPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
        public void setSocketTimeout(int socketTimeout) { this.socketTimeout = socketTimeout; }
        public void setServerSelectionTimeout(int serverSelectionTimeout) { this.serverSelectionTimeout = serverSelectionTimeout; }
        public void setWriteConcern(String writeConcern) { this.writeConcern = writeConcern; }
        public void setReadConcern(String readConcern) { this.readConcern = readConcern; }

        /**
         * Build a full connection string with options appended.
         * Only adds options if they're not already in the base connection string.
         */
        public String getFullConnectionString() {
            String base = connectionString;

            // If connection string already has options, return as-is
            if (base.contains("minPoolSize=") || base.contains("maxPoolSize=")) {
                return base;
            }

            StringBuilder url = new StringBuilder(base);

            // Add options
            if (!base.contains("?")) {
                url.append("?");
            } else {
                url.append("&");
            }

            url.append("minPoolSize=").append(minPoolSize);
            url.append("&maxPoolSize=").append(maxPoolSize);
            url.append("&connectTimeoutMS=").append(connectTimeout);
            url.append("&socketTimeoutMS=").append(socketTimeout);
            url.append("&serverSelectionTimeoutMS=").append(serverSelectionTimeout);
            url.append("&w=").append(writeConcern);
            url.append("&readConcernLevel=").append(readConcern);

            return url.toString();
        }

        @Override
        public String toString() {
            return "MongoConfig{connectionString='" + connectionString + "', database='" + database +
                   "', collectionPrefix='" + collectionPrefix +
                   "', poolSize=" + minPoolSize + "-" + maxPoolSize + "}";
        }
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

    @Override
    public String toString() {
        return "StorageConfig{loadFrom='" + loadFrom + "', saveTo='" + saveTo +
               "', sql=" + sql + ", mongodb=" + mongodb + "}";
    }
}
