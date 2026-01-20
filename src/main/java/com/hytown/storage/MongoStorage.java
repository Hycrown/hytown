package com.hytown.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hytown.data.Town;

import java.lang.reflect.Method;
import java.util.*;

/**
 * MongoDB storage provider.
 * Uses reflection to avoid compile-time dependency on MongoDB driver.
 * If MongoDB driver is not available, init() will throw an exception.
 */
public class MongoStorage implements StorageProvider {

    private final StorageConfig.MongoConfig config;
    private final Gson gson;

    // MongoDB objects (loaded via reflection)
    private Object mongoClient;
    private Object database;
    private Object townsCollection;
    private Object invitesCollection;

    // Cached reflection methods
    private Method findMethod;
    private Method replaceOneMethod;
    private Method deleteOneMethod;
    private Method deleteManyMethod;
    private Method insertManyMethod;

    public MongoStorage(StorageConfig.MongoConfig config) {
        this.config = config;
        this.gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .create();
    }

    @Override
    public void init() throws Exception {
        try {
            // Check if MongoDB driver is available
            Class<?> mongoClientsClass = Class.forName("com.mongodb.client.MongoClients");
            Class<?> mongoDatabaseClass = Class.forName("com.mongodb.client.MongoDatabase");
            Class<?> mongoCollectionClass = Class.forName("com.mongodb.client.MongoCollection");
            Class<?> filtersClass = Class.forName("com.mongodb.client.model.Filters");
            Class<?> replaceOptionsClass = Class.forName("com.mongodb.client.model.ReplaceOptions");
            Class<?> documentClass = Class.forName("org.bson.Document");

            // Create client
            Method createMethod = mongoClientsClass.getMethod("create", String.class);
            mongoClient = createMethod.invoke(null, config.getConnectionString());

            // Get database
            Method getDatabaseMethod = mongoClient.getClass().getMethod("getDatabase", String.class);
            database = getDatabaseMethod.invoke(mongoClient, config.getDatabase());

            // Get collections
            String prefix = config.getCollectionPrefix();
            Method getCollectionMethod = mongoDatabaseClass.getMethod("getCollection", String.class);
            townsCollection = getCollectionMethod.invoke(database, prefix + "towns");
            invitesCollection = getCollectionMethod.invoke(database, prefix + "invites");

            System.out.println("[MongoStorage] Connected to MongoDB: " + config.getConnectionString() + " / " + config.getDatabase());

        } catch (ClassNotFoundException e) {
            throw new Exception("MongoDB driver not found. Please add mongodb-driver-sync to the classpath.", e);
        }
    }

    @Override
    public void shutdown() {
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
        if (mongoClient == null || database == null) {
            return false;
        }
        try {
            // Ping the database
            Class<?> documentClass = Class.forName("org.bson.Document");
            Object pingDoc = documentClass.getConstructor(String.class, Object.class).newInstance("ping", 1);
            Method runCommandMethod = database.getClass().getMethod("runCommand", Class.forName("org.bson.conversions.Bson"));
            runCommandMethod.invoke(database, pingDoc);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== TOWN OPERATIONS ====================

    @Override
    public Town loadTown(String townName) {
        try {
            Class<?> filtersClass = Class.forName("com.mongodb.client.model.Filters");
            Class<?> documentClass = Class.forName("org.bson.Document");

            // Create filter
            Method eqMethod = filtersClass.getMethod("eq", String.class, Object.class);
            Object filter = eqMethod.invoke(null, "_id", townName.toLowerCase());

            // Find document
            Method findMethod = townsCollection.getClass().getMethod("find", Class.forName("org.bson.conversions.Bson"));
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
        }
        return null;
    }

    @Override
    public Collection<Town> loadAllTowns() {
        List<Town> towns = new ArrayList<>();

        try {
            Class<?> documentClass = Class.forName("org.bson.Document");

            // Find all documents
            Method findMethod = townsCollection.getClass().getMethod("find");
            Object findIterable = findMethod.invoke(townsCollection);

            // Iterate
            Method iteratorMethod = findIterable.getClass().getMethod("iterator");
            Object iterator = iteratorMethod.invoke(findIterable);
            Method hasNextMethod = iterator.getClass().getMethod("hasNext");
            Method nextMethod = iterator.getClass().getMethod("next");

            while ((Boolean) hasNextMethod.invoke(iterator)) {
                Object doc = nextMethod.invoke(iterator);
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
        }

        return towns;
    }

    @Override
    public void saveTown(Town town) {
        try {
            Class<?> documentClass = Class.forName("org.bson.Document");
            Class<?> filtersClass = Class.forName("com.mongodb.client.model.Filters");
            Class<?> replaceOptionsClass = Class.forName("com.mongodb.client.model.ReplaceOptions");

            String json = gson.toJson(town);

            // Create document
            Object doc = documentClass.getConstructor().newInstance();
            Method appendMethod = documentClass.getMethod("append", String.class, Object.class);
            appendMethod.invoke(doc, "_id", town.getName().toLowerCase());
            appendMethod.invoke(doc, "name", town.getName());
            appendMethod.invoke(doc, "data", json);
            appendMethod.invoke(doc, "updatedAt", new Date());

            // Create filter
            Method eqMethod = filtersClass.getMethod("eq", String.class, Object.class);
            Object filter = eqMethod.invoke(null, "_id", town.getName().toLowerCase());

            // Create options with upsert
            Object options = replaceOptionsClass.getConstructor().newInstance();
            Method upsertMethod = replaceOptionsClass.getMethod("upsert", boolean.class);
            upsertMethod.invoke(options, true);

            // Replace
            Method replaceOneMethod = townsCollection.getClass().getMethod("replaceOne",
                    Class.forName("org.bson.conversions.Bson"), documentClass, replaceOptionsClass);
            replaceOneMethod.invoke(townsCollection, filter, doc, options);

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error saving town " + town.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public void deleteTown(String townName) {
        try {
            Class<?> filtersClass = Class.forName("com.mongodb.client.model.Filters");

            // Create filter
            Method eqMethod = filtersClass.getMethod("eq", String.class, Object.class);
            Object filter = eqMethod.invoke(null, "_id", townName.toLowerCase());

            // Delete
            Method deleteOneMethod = townsCollection.getClass().getMethod("deleteOne",
                    Class.forName("org.bson.conversions.Bson"));
            deleteOneMethod.invoke(townsCollection, filter);

        } catch (Exception e) {
            System.err.println("[MongoStorage] Error deleting town " + townName + ": " + e.getMessage());
        }
    }

    // ==================== INVITE OPERATIONS ====================

    @Override
    public Map<UUID, Set<String>> loadInvites() {
        Map<UUID, Set<String>> invites = new HashMap<>();

        try {
            Class<?> documentClass = Class.forName("org.bson.Document");

            // Find all documents
            Method findMethod = invitesCollection.getClass().getMethod("find");
            Object findIterable = findMethod.invoke(invitesCollection);

            // Iterate
            Method iteratorMethod = findIterable.getClass().getMethod("iterator");
            Object iterator = iteratorMethod.invoke(findIterable);
            Method hasNextMethod = iterator.getClass().getMethod("hasNext");
            Method nextMethod = iterator.getClass().getMethod("next");

            while ((Boolean) hasNextMethod.invoke(iterator)) {
                Object doc = nextMethod.invoke(iterator);
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
        }

        return invites;
    }

    @Override
    public void saveInvites(Map<UUID, Set<String>> invites) {
        try {
            Class<?> documentClass = Class.forName("org.bson.Document");

            // Clear existing invites
            Object emptyDoc = documentClass.getConstructor().newInstance();
            Method deleteManyMethod = invitesCollection.getClass().getMethod("deleteMany",
                    Class.forName("org.bson.conversions.Bson"));
            deleteManyMethod.invoke(invitesCollection, emptyDoc);

            // Insert all invites
            List<Object> docs = new ArrayList<>();
            for (Map.Entry<UUID, Set<String>> entry : invites.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    Object doc = documentClass.getConstructor().newInstance();
                    Method appendMethod = documentClass.getMethod("append", String.class, Object.class);
                    appendMethod.invoke(doc, "_id", entry.getKey().toString());
                    appendMethod.invoke(doc, "invites", new ArrayList<>(entry.getValue()));
                    docs.add(doc);
                }
            }

            if (!docs.isEmpty()) {
                Method insertManyMethod = invitesCollection.getClass().getMethod("insertMany", List.class);
                insertManyMethod.invoke(invitesCollection, docs);
            }
        } catch (Exception e) {
            System.err.println("[MongoStorage] Error saving invites: " + e.getMessage());
        }
    }

    /**
     * Get connection info for status display.
     */
    public String getConnectionInfo() {
        return config.getConnectionString() + " / " + config.getDatabase();
    }
}
