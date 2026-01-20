package com.hytown.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hytown.data.Town;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MongoDB storage provider with transaction support.
 * Uses reflection to avoid compile-time dependency on MongoDB driver.
 * If MongoDB driver is not available, init() will throw an exception.
 *
 * Features:
 * - Transaction support for atomic operations (MongoDB 4.0+)
 * - Proper cursor/iterator cleanup
 * - Bulk write operations for efficiency
 * - Connection health checking
 *
 * Requirements:
 * - MongoDB 4.0+ for transaction support
 * - mongodb-driver-sync in classpath
 */
public class MongoStorage implements StorageProvider {

    private final StorageConfig.MongoConfig config;
    private final Gson gson;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // MongoDB objects (loaded via reflection)
    private Object mongoClient;
    private Object database;
    private Object townsCollection;
    private Object invitesCollection;

    // Cached classes for reflection
    private Class<?> documentClass;
    private Class<?> filtersClass;
    private Class<?> replaceOptionsClass;
    private Class<?> bsonClass;
    private Class<?> clientSessionClass;
    private Class<?> transactionOptionsClass;
    private Class<?> readConcernClass;
    private Class<?> writeConcernClass;

    // Flag to indicate if transactions are supported
    private boolean transactionsSupported = false;

    // Validated collection prefix (set in constructor)
    private final String collectionPrefix;

    public MongoStorage(StorageConfig.MongoConfig config) {
        this.config = config;
        this.gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .create();

        // SECURITY: Validate collection prefix to prevent injection attacks
        this.collectionPrefix = sanitizeIdentifier(config.getCollectionPrefix(), "hytown_");
    }

    /**
     * Sanitizes a database identifier (collection prefix) to prevent injection attacks.
     * Only allows alphanumeric characters and underscores.
     * MongoDB collection names have additional restrictions (no $ or null, can't start with system.)
     *
     * @param identifier The identifier to sanitize
     * @param defaultValue Default value if identifier is null/empty/invalid
     * @return A safe identifier string
     */
    private static String sanitizeIdentifier(String identifier, String defaultValue) {
        if (identifier == null || identifier.isEmpty()) {
            return defaultValue;
        }

        // Only allow alphanumeric and underscore
        // Must start with letter (MongoDB restriction: can't start with number)
        if (!identifier.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            System.out.println("\u001B[33m[MongoStorage] WARNING: Invalid collection prefix '" + identifier +
                    "' contains illegal characters. Using default: " + defaultValue + "\u001B[0m");
            return defaultValue;
        }

        // Check for MongoDB reserved prefixes
        if (identifier.toLowerCase().startsWith("system")) {
            System.out.println("\u001B[33m[MongoStorage] WARNING: Collection prefix cannot start with 'system'. Using default: " + defaultValue + "\u001B[0m");
            return defaultValue;
        }

        // Limit length (MongoDB max namespace is 120 bytes, but we'll be conservative)
        if (identifier.length() > 32) {
            System.out.println("\u001B[33m[MongoStorage] WARNING: Collection prefix too long, truncating to 32 characters\u001B[0m");
            identifier = identifier.substring(0, 32);
        }

        return identifier;
    }

    @Override
    public void init() throws Exception {
        try {
            // Check if MongoDB driver is available
            Class<?> mongoClientsClass = Class.forName("com.mongodb.client.MongoClients");
            Class<?> mongoDatabaseClass = Class.forName("com.mongodb.client.MongoDatabase");
            Class<?> mongoCollectionClass = Class.forName("com.mongodb.client.MongoCollection");

            // Cache commonly used classes
            this.filtersClass = Class.forName("com.mongodb.client.model.Filters");
            this.replaceOptionsClass = Class.forName("com.mongodb.client.model.ReplaceOptions");
            this.documentClass = Class.forName("org.bson.Document");
            this.bsonClass = Class.forName("org.bson.conversions.Bson");

            // Try to load transaction-related classes (MongoDB 4.0+)
            try {
                this.clientSessionClass = Class.forName("com.mongodb.client.ClientSession");
                this.transactionOptionsClass = Class.forName("com.mongodb.TransactionOptions");
                this.readConcernClass = Class.forName("com.mongodb.ReadConcern");
                this.writeConcernClass = Class.forName("com.mongodb.WriteConcern");
                transactionsSupported = true;
            } catch (ClassNotFoundException e) {
                System.out.println("[MongoStorage] Transaction classes not found - transactions disabled");
                transactionsSupported = false;
            }

            // Create client
            Method createMethod = mongoClientsClass.getMethod("create", String.class);
            mongoClient = createMethod.invoke(null, config.getConnectionString());

            // Get database using validated name
            String dbName = sanitizeIdentifier(config.getDatabase(), "hytown");
            Method getDatabaseMethod = mongoClient.getClass().getMethod("getDatabase", String.class);
            database = getDatabaseMethod.invoke(mongoClient, dbName);

            // Get collections using validated prefix
            Method getCollectionMethod = mongoDatabaseClass.getMethod("getCollection", String.class);
            townsCollection = getCollectionMethod.invoke(database, collectionPrefix + "towns");
            invitesCollection = getCollectionMethod.invoke(database, collectionPrefix + "invites");

            // Create indexes for better query performance
            createIndexes();

            initialized.set(true);
            System.out.println("[MongoStorage] Connected to MongoDB: " + config.getConnectionString() + " / " + config.getDatabase());
            System.out.println("[MongoStorage] Transactions supported: " + transactionsSupported);

        } catch (ClassNotFoundException e) {
            throw new Exception("MongoDB driver not found. Please add mongodb-driver-sync to the classpath.", e);
        }
    }

    /**
     * Create indexes for better query performance.
     */
    private void createIndexes() {
        try {
            // Create index on towns collection
            Object townsIndexDoc = documentClass.getConstructor(String.class, Object.class)
                    .newInstance("updatedAt", 1);
            Method createIndexMethod = townsCollection.getClass().getMethod("createIndex", bsonClass);
            createIndexMethod.invoke(townsCollection, townsIndexDoc);

            System.out.println("[MongoStorage] Created indexes on collections");
        } catch (Exception e) {
            System.out.println("\u001B[33m[MongoStorage] Warning: Could not create indexes: " + e.getMessage() + "\u001B[0m");
        }
    }

    @Override
    public void shutdown() {
        initialized.set(false);

        if (mongoClient != null) {
            try {
                Method closeMethod = mongoClient.getClass().getMethod("close");
                closeMethod.invoke(mongoClient);
                System.out.println("[MongoStorage] Disconnected from MongoDB");
            } catch (Exception e) {
                System.err.println("[MongoStorage] Error closing connection: " + e.getMessage());
            }
        }
    }

    @Override
    public String getName() {
        return "MongoDB";
    }

    @Override
    public boolean isConnected() {
        if (!initialized.get() || mongoClient == null || database == null) {
            return false;
        }
        try {
            // Ping the database
            Object pingDoc = documentClass.getConstructor(String.class, Object.class).newInstance("ping", 1);
            Method runCommandMethod = database.getClass().getMethod("runCommand", bsonClass);
            runCommandMethod.invoke(database, pingDoc);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Create a Document with the given key-value pairs.
     */
    private Object createDocument(Object... keyValues) throws Exception {
        Object doc = documentClass.getConstructor().newInstance();
        Method appendMethod = documentClass.getMethod("append", String.class, Object.class);
        for (int i = 0; i < keyValues.length; i += 2) {
            appendMethod.invoke(doc, keyValues[i], keyValues[i + 1]);
        }
        return doc;
    }

    /**
     * Create an equality filter.
     */
    private Object createEqFilter(String field, Object value) throws Exception {
        Method eqMethod = filtersClass.getMethod("eq", String.class, Object.class);
        return eqMethod.invoke(null, field, value);
    }

    /**
     * Create replace options with upsert.
     */
    private Object createUpsertOptions() throws Exception {
        Object options = replaceOptionsClass.getConstructor().newInstance();
        Method upsertMethod = replaceOptionsClass.getMethod("upsert", boolean.class);
        upsertMethod.invoke(options, true);
        return options;
    }

    /**
     * Safely close a cursor/iterator.
     */
    private void closeCursor(Object cursor) {
        if (cursor != null) {
            try {
                Method closeMethod = cursor.getClass().getMethod("close");
                closeMethod.invoke(cursor);
            } catch (Exception e) {
                // Ignore close errors
            }
        }
    }

    /**
     * Start a client session for transactions.
     * Returns null if transactions are not supported.
     */
    private Object startSession() {
        if (!transactionsSupported || mongoClient == null) {
            return null;
        }
        try {
            Method startSessionMethod = mongoClient.getClass().getMethod("startSession");
            return startSessionMethod.invoke(mongoClient);
        } catch (Exception e) {
            System.err.println("[MongoStorage] Could not start session: " + e.getMessage());
            return null;
        }
    }

    /**
     * Close a client session.
     */
    private void closeSession(Object session) {
        if (session != null) {
            try {
                Method closeMethod = session.getClass().getMethod("close");
                closeMethod.invoke(session);
            } catch (Exception e) {
                // Ignore close errors
            }
        }
    }

    /**
     * Start a transaction on a session.
     */
    private void startTransaction(Object session) {
        if (session != null) {
            try {
                Method startMethod = session.getClass().getMethod("startTransaction");
                startMethod.invoke(session);
            } catch (Exception e) {
                System.err.println("[MongoStorage] Could not start transaction: " + e.getMessage());
            }
        }
    }

    /**
     * Commit a transaction.
     */
    private void commitTransaction(Object session) {
        if (session != null) {
            try {
                Method commitMethod = session.getClass().getMethod("commitTransaction");
                commitMethod.invoke(session);
            } catch (Exception e) {
                System.err.println("[MongoStorage] Could not commit transaction: " + e.getMessage());
            }
        }
    }

    /**
     * Abort a transaction.
     */
    private void abortTransaction(Object session) {
        if (session != null) {
            try {
                Method abortMethod = session.getClass().getMethod("abortTransaction");
                abortMethod.invoke(session);
            } catch (Exception e) {
                System.err.println("[MongoStorage] Could not abort transaction: " + e.getMessage());
            }
        }
    }

    // ==================== TOWN OPERATIONS ====================

    @Override
    public Town loadTown(String townName) {
        Object cursor = null;
        try {
            Object filter = createEqFilter("_id", townName.toLowerCase());

            // Find document
            Method findMethod = townsCollection.getClass().getMethod("find", bsonClass);
            Object findIterable = findMethod.invoke(townsCollection, filter);
            Method firstMethod = findIterable.getClass().getMethod("first");
            Object doc = firstMethod.invoke(findIterable);

            if (doc != null) {
                Method getStringMethod = documentClass.getMethod("getString", Object.class);
                String json = (String) getStringMethod.invoke(doc, "data");
                if (json != null) {
                    Town town = gson.fromJson(json, Town.class);
                    if (town != null) {
                        town.validateAfterLoad();
                    }
                    return town;
                }
            }
        } catch (Exception e) {
            System.err.println("[MongoStorage] Error loading town " + townName + ": " + e.getMessage());
        } finally {
            closeCursor(cursor);
        }
        return null;
    }

    @Override
    public Collection<Town> loadAllTowns() {
        List<Town> towns = new ArrayList<>();
        Object cursor = null;

        try {
            // Find all documents
            Method findMethod = townsCollection.getClass().getMethod("find");
            Object findIterable = findMethod.invoke(townsCollection);

            // Get iterator
            Method iteratorMethod = findIterable.getClass().getMethod("iterator");
            cursor = iteratorMethod.invoke(findIterable);
            Method hasNextMethod = cursor.getClass().getMethod("hasNext");
            Method nextMethod = cursor.getClass().getMethod("next");

            while ((Boolean) hasNextMethod.invoke(cursor)) {
                Object doc = nextMethod.invoke(cursor);
                try {
                    Method getStringMethod = documentClass.getMethod("getString", Object.class);
                    String json = (String) getStringMethod.invoke(doc, "data");
                    if (json != null) {
                        Town town = gson.fromJson(json, Town.class);
                        if (town != null && town.getName() != null) {
                            town.validateAfterLoad();
                            towns.add(town);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[MongoStorage] Error parsing town document: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[MongoStorage] Error loading towns: " + e.getMessage());
        } finally {
            closeCursor(cursor);
        }

        return towns;
    }

    @Override
    public void saveTown(Town town) {
        try {
            String json = gson.toJson(town);

            // Create document
            Object doc = createDocument(
                    "_id", town.getName().toLowerCase(),
                    "name", town.getName(),
                    "data", json,
                    "updatedAt", new Date()
            );

            // Create filter and options
            Object filter = createEqFilter("_id", town.getName().toLowerCase());
            Object options = createUpsertOptions();

            // Replace
            Method replaceOneMethod = townsCollection.getClass().getMethod("replaceOne",
                    bsonClass, documentClass, replaceOptionsClass);
            replaceOneMethod.invoke(townsCollection, filter, doc, options);

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error saving town " + town.getName() + ": " + e.getMessage());
            throw new RuntimeException("Failed to save town: " + town.getName(), e);
        }
    }

    @Override
    public void deleteTown(String townName) {
        try {
            Object filter = createEqFilter("_id", townName.toLowerCase());

            Method deleteOneMethod = townsCollection.getClass().getMethod("deleteOne", bsonClass);
            deleteOneMethod.invoke(townsCollection, filter);

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error deleting town " + townName + ": " + e.getMessage());
        }
    }

    /**
     * Rename a town atomically using a transaction (if supported).
     * Falls back to non-transactional rename if transactions are not available.
     */
    @Override
    public void renameTown(String oldName, String newName, Town town) {
        Object session = null;

        try {
            String json = gson.toJson(town);

            // Create new document
            Object newDoc = createDocument(
                    "_id", newName.toLowerCase(),
                    "name", newName,
                    "data", json,
                    "updatedAt", new Date()
            );

            if (transactionsSupported) {
                // Use transaction for atomic rename
                session = startSession();
                if (session != null) {
                    try {
                        startTransaction(session);

                        // Insert new document
                        Object newFilter = createEqFilter("_id", newName.toLowerCase());
                        Object options = createUpsertOptions();

                        // Use session-aware methods
                        Method replaceOneMethod = townsCollection.getClass().getMethod("replaceOne",
                                clientSessionClass, bsonClass, documentClass, replaceOptionsClass);
                        replaceOneMethod.invoke(townsCollection, session, newFilter, newDoc, options);

                        // Delete old document (if name changed)
                        if (!oldName.equalsIgnoreCase(newName)) {
                            Object oldFilter = createEqFilter("_id", oldName.toLowerCase());
                            Method deleteOneMethod = townsCollection.getClass().getMethod("deleteOne",
                                    clientSessionClass, bsonClass);
                            deleteOneMethod.invoke(townsCollection, session, oldFilter);

                            // Update invites to reference new town name
                            updateInvitesForRename(session, oldName, newName);
                        }

                        commitTransaction(session);
                        System.out.println("[MongoStorage] Renamed town '" + oldName + "' to '" + newName + "' (transactional)");
                        return;

                    } catch (Exception e) {
                        abortTransaction(session);
                        throw e;
                    }
                }
            }

            // Fallback: Non-transactional rename (save new first, then delete old)
            Object newFilter = createEqFilter("_id", newName.toLowerCase());
            Object options = createUpsertOptions();

            Method replaceOneMethod = townsCollection.getClass().getMethod("replaceOne",
                    bsonClass, documentClass, replaceOptionsClass);
            replaceOneMethod.invoke(townsCollection, newFilter, newDoc, options);

            if (!oldName.equalsIgnoreCase(newName)) {
                Object oldFilter = createEqFilter("_id", oldName.toLowerCase());
                Method deleteOneMethod = townsCollection.getClass().getMethod("deleteOne", bsonClass);
                deleteOneMethod.invoke(townsCollection, oldFilter);

                // Update invites (non-transactional)
                updateInvitesForRename(null, oldName, newName);
            }

            System.out.println("[MongoStorage] Renamed town '" + oldName + "' to '" + newName + "' (non-transactional)");

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error renaming town " + oldName + " to " + newName + ": " + e.getMessage());
            throw new RuntimeException("Failed to rename town", e);
        } finally {
            closeSession(session);
        }
    }

    /**
     * Update invite documents when a town is renamed.
     */
    private void updateInvitesForRename(Object session, String oldName, String newName) throws Exception {
        // For each invite document, update the invites array if it contains oldName
        // This is more complex in MongoDB as we need to update array elements

        Object cursor = null;
        try {
            // Find all invite documents
            Method findMethod = invitesCollection.getClass().getMethod("find");
            Object findIterable = findMethod.invoke(invitesCollection);
            Method iteratorMethod = findIterable.getClass().getMethod("iterator");
            cursor = iteratorMethod.invoke(findIterable);
            Method hasNextMethod = cursor.getClass().getMethod("hasNext");
            Method nextMethod = cursor.getClass().getMethod("next");

            List<Object> docsToUpdate = new ArrayList<>();

            while ((Boolean) hasNextMethod.invoke(cursor)) {
                Object doc = nextMethod.invoke(cursor);
                Method getListMethod = documentClass.getMethod("getList", Object.class, Class.class);

                @SuppressWarnings("unchecked")
                List<String> invites = (List<String>) getListMethod.invoke(doc, "invites", String.class);

                if (invites != null && invites.contains(oldName)) {
                    List<String> updatedInvites = new ArrayList<>(invites);
                    updatedInvites.remove(oldName);
                    if (!updatedInvites.contains(newName)) {
                        updatedInvites.add(newName);
                    }

                    Method getStringMethod = documentClass.getMethod("getString", Object.class);
                    String playerId = (String) getStringMethod.invoke(doc, "_id");

                    // Create updated document
                    Object updatedDoc = createDocument(
                            "_id", playerId,
                            "invites", updatedInvites
                    );
                    docsToUpdate.add(updatedDoc);
                }
            }

            // Update all affected documents
            for (Object updatedDoc : docsToUpdate) {
                Method getStringMethod = documentClass.getMethod("getString", Object.class);
                String playerId = (String) getStringMethod.invoke(updatedDoc, "_id");
                Object filter = createEqFilter("_id", playerId);
                Object options = createUpsertOptions();

                if (session != null) {
                    Method replaceOneMethod = invitesCollection.getClass().getMethod("replaceOne",
                            clientSessionClass, bsonClass, documentClass, replaceOptionsClass);
                    replaceOneMethod.invoke(invitesCollection, session, filter, updatedDoc, options);
                } else {
                    Method replaceOneMethod = invitesCollection.getClass().getMethod("replaceOne",
                            bsonClass, documentClass, replaceOptionsClass);
                    replaceOneMethod.invoke(invitesCollection, filter, updatedDoc, options);
                }
            }

        } finally {
            closeCursor(cursor);
        }
    }

    // ==================== INVITE OPERATIONS ====================

    @Override
    public Map<UUID, Set<String>> loadInvites() {
        Map<UUID, Set<String>> invites = new HashMap<>();
        Object cursor = null;

        try {
            // Find all documents
            Method findMethod = invitesCollection.getClass().getMethod("find");
            Object findIterable = findMethod.invoke(invitesCollection);

            // Iterate with proper cursor management
            Method iteratorMethod = findIterable.getClass().getMethod("iterator");
            cursor = iteratorMethod.invoke(findIterable);
            Method hasNextMethod = cursor.getClass().getMethod("hasNext");
            Method nextMethod = cursor.getClass().getMethod("next");

            while ((Boolean) hasNextMethod.invoke(cursor)) {
                Object doc = nextMethod.invoke(cursor);
                try {
                    Method getStringMethod = documentClass.getMethod("getString", Object.class);
                    Method getListMethod = documentClass.getMethod("getList", Object.class, Class.class);

                    String uuidStr = (String) getStringMethod.invoke(doc, "_id");
                    UUID playerId = UUID.fromString(uuidStr);

                    @SuppressWarnings("unchecked")
                    List<String> townNames = (List<String>) getListMethod.invoke(doc, "invites", String.class);
                    if (townNames != null) {
                        invites.put(playerId, new HashSet<>(townNames));
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        } catch (Exception e) {
            System.err.println("[MongoStorage] Error loading invites: " + e.getMessage());
        } finally {
            closeCursor(cursor);
        }

        return invites;
    }

    /**
     * Save all pending invites atomically using a transaction (if supported).
     * Uses bulk write operations for efficiency.
     */
    @Override
    public void saveInvites(Map<UUID, Set<String>> invites) {
        Object session = null;

        try {
            if (transactionsSupported) {
                session = startSession();
                if (session != null) {
                    try {
                        startTransaction(session);

                        // Clear existing invites
                        Object emptyDoc = documentClass.getConstructor().newInstance();
                        Method deleteManyMethod = invitesCollection.getClass().getMethod("deleteMany",
                                clientSessionClass, bsonClass);
                        deleteManyMethod.invoke(invitesCollection, session, emptyDoc);

                        // Insert all invites
                        if (!invites.isEmpty()) {
                            List<Object> docs = createInviteDocuments(invites);
                            if (!docs.isEmpty()) {
                                Method insertManyMethod = invitesCollection.getClass().getMethod("insertMany",
                                        clientSessionClass, List.class);
                                insertManyMethod.invoke(invitesCollection, session, docs);
                            }
                        }

                        commitTransaction(session);
                        return;

                    } catch (Exception e) {
                        abortTransaction(session);
                        throw e;
                    }
                }
            }

            // Fallback: Non-transactional save
            // Delete all first, then insert
            Object emptyDoc = documentClass.getConstructor().newInstance();
            Method deleteManyMethod = invitesCollection.getClass().getMethod("deleteMany", bsonClass);
            deleteManyMethod.invoke(invitesCollection, emptyDoc);

            if (!invites.isEmpty()) {
                List<Object> docs = createInviteDocuments(invites);
                if (!docs.isEmpty()) {
                    Method insertManyMethod = invitesCollection.getClass().getMethod("insertMany", List.class);
                    insertManyMethod.invoke(invitesCollection, docs);
                }
            }

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error saving invites: " + e.getMessage());
            throw new RuntimeException("Failed to save invites", e);
        } finally {
            closeSession(session);
        }
    }

    /**
     * Create invite documents for bulk insert.
     */
    private List<Object> createInviteDocuments(Map<UUID, Set<String>> invites) throws Exception {
        List<Object> docs = new ArrayList<>();
        Method appendMethod = documentClass.getMethod("append", String.class, Object.class);

        for (Map.Entry<UUID, Set<String>> entry : invites.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                Object doc = documentClass.getConstructor().newInstance();
                appendMethod.invoke(doc, "_id", entry.getKey().toString());
                appendMethod.invoke(doc, "invites", new ArrayList<>(entry.getValue()));
                docs.add(doc);
            }
        }

        return docs;
    }

    // ==================== ADDITIONAL OPERATIONS ====================

    /**
     * Add a single invite without full replace.
     */
    public void addInvite(UUID playerId, String townName) {
        try {
            Object filter = createEqFilter("_id", playerId.toString());

            // Use $addToSet to add to array without duplicates
            Class<?> updatesClass = Class.forName("com.mongodb.client.model.Updates");
            Method addToSetMethod = updatesClass.getMethod("addToSet", String.class, Object.class);
            Object update = addToSetMethod.invoke(null, "invites", townName);

            // Upsert the document
            Class<?> updateOptionsClass = Class.forName("com.mongodb.client.model.UpdateOptions");
            Object options = updateOptionsClass.getConstructor().newInstance();
            Method upsertMethod = updateOptionsClass.getMethod("upsert", boolean.class);
            upsertMethod.invoke(options, true);

            Method updateOneMethod = invitesCollection.getClass().getMethod("updateOne",
                    bsonClass, bsonClass, updateOptionsClass);
            updateOneMethod.invoke(invitesCollection, filter, update, options);

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error adding invite: " + e.getMessage());
        }
    }

    /**
     * Remove a single invite.
     */
    public void removeInvite(UUID playerId, String townName) {
        try {
            Object filter = createEqFilter("_id", playerId.toString());

            // Use $pull to remove from array
            Class<?> updatesClass = Class.forName("com.mongodb.client.model.Updates");
            Method pullMethod = updatesClass.getMethod("pull", String.class, Object.class);
            Object update = pullMethod.invoke(null, "invites", townName);

            Method updateOneMethod = invitesCollection.getClass().getMethod("updateOne", bsonClass, bsonClass);
            updateOneMethod.invoke(invitesCollection, filter, update);

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error removing invite: " + e.getMessage());
        }
    }

    /**
     * Remove all invites for a player.
     */
    public void clearPlayerInvites(UUID playerId) {
        try {
            Object filter = createEqFilter("_id", playerId.toString());
            Method deleteOneMethod = invitesCollection.getClass().getMethod("deleteOne", bsonClass);
            deleteOneMethod.invoke(invitesCollection, filter);
        } catch (Exception e) {
            System.err.println("[MongoStorage] Error clearing player invites: " + e.getMessage());
        }
    }

    /**
     * Remove all invites for a town (when town is deleted).
     */
    public void clearTownInvites(String townName) {
        try {
            // Use $pull on all documents to remove the town from invites arrays
            Object emptyDoc = documentClass.getConstructor().newInstance();

            Class<?> updatesClass = Class.forName("com.mongodb.client.model.Updates");
            Method pullMethod = updatesClass.getMethod("pull", String.class, Object.class);
            Object update = pullMethod.invoke(null, "invites", townName);

            Method updateManyMethod = invitesCollection.getClass().getMethod("updateMany", bsonClass, bsonClass);
            updateManyMethod.invoke(invitesCollection, emptyDoc, update);

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error clearing town invites: " + e.getMessage());
        }
    }

    /**
     * Get connection info for status display.
     */
    public String getConnectionInfo() {
        return config.getConnectionString() + " / " + config.getDatabase() +
               " (transactions: " + (transactionsSupported ? "enabled" : "disabled") + ")";
    }

    /**
     * Check if transactions are supported.
     */
    public boolean isTransactionsSupported() {
        return transactionsSupported;
    }

    /**
     * Get detailed status for monitoring.
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("MongoDB Status:\n");
        sb.append("  Connected: ").append(isConnected()).append("\n");
        sb.append("  Database: ").append(config.getDatabase()).append("\n");
        sb.append("  Transactions: ").append(transactionsSupported ? "supported" : "not supported").append("\n");
        return sb.toString();
    }
}
