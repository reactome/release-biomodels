package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ReactomeJavaConstants;
import org.gk.util.GKApplicationUtilities;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.Record;

import java.util.*;

public final class BioModelsUtilities {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int EXIT_FAILURE = 1;
    public static Long maxDbId;
    public static final String DATABASE_NAME = "databaseName";
    public static final String DBID = "dbId";
    public static final String STID = "stId";
    public static final String DISPLAY_NAME = "displayName";
    public static final String SCHEMA_CLASS = "schemaClass";

    private BioModelsUtilities() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Attempts to find a 'BioModels Database' instance in the database. If there isn't one, it is created.
     *
     * @param tx           Neo4j Driver Transaction
     * @param instanceEdit Node connecting user to modifications completed by this step.
     * @return The BioModels database instance
     */
    public static Node fetchBioModelsReferenceDatabase(Transaction tx, Node instanceEdit) {
        LOGGER.info("Attempting to fetch an existing BioModels reference database");
        Node biomodelsReferenceDatabase = retrieveBioModelsDatabaseInstance(tx);

        if (biomodelsReferenceDatabase == null) {
            LOGGER.info("Creating BioModels reference database - no existing one was found");
            biomodelsReferenceDatabase = createBioModelsDatabaseInstance(tx, instanceEdit);
        }
        return biomodelsReferenceDatabase;
    }

    /**
     * Attempts to find the BioModels database instance.
     *
     * @param tx Neo4j Driver Transaction
     * @return The BioModels database instance, if found; otherwise, null.
     */
    public static Node retrieveBioModelsDatabaseInstance(Transaction tx) {
        Node biomodelsReferenceDatabase = null;
        try {
            Result result = tx.run("MATCH (n:DatabaseObject:ReferenceDatabase) WHERE 'BioModels' IN n.name RETURN n");
            if (result.hasNext()) {
                Record record = result.single();
                biomodelsReferenceDatabase = record.get("n").asNode();
            }
        } catch (Neo4jException e) {
            logAndThrow("Unable to retrieve BioModels reference database", e);
        }
        return biomodelsReferenceDatabase;
    }

    /**
     * Creates BioModels database instance.
     *
     * @param tx           Neo4j Driver Transaction
     * @param instanceEdit Node connecting user to modifications completed by this step.
     * @return The BioModels database instance
     */
    private static Node createBioModelsDatabaseInstance(Transaction tx, Node instanceEdit) {
        Node biomodelsReferenceDatabase = null;

        try {
            String bioModelsDatabaseName = "BioModels Database";

            HashMap<String, Object> props = new HashMap<>();
            props.put(ReactomeJavaConstants.accessUrl, "https://www.ebi.ac.uk/biomodels/###ID###");
            props.put(DBID, maxDbId + 1);
            props.put(DISPLAY_NAME, bioModelsDatabaseName);
            props.put(ReactomeJavaConstants.name, Arrays.asList(bioModelsDatabaseName, "BioModels"));
            props.put(SCHEMA_CLASS, ReactomeJavaConstants.ReferenceDatabase);
            props.put(ReactomeJavaConstants.url, "https://www.ebi.ac.uk/biomodels/");

            biomodelsReferenceDatabase = createNode(tx, Arrays.asList(ReactomeJavaConstants.ReferenceDatabase), props);
            createRelationship(tx, instanceEdit, biomodelsReferenceDatabase, ReactomeJavaConstants.created, 0, 1);
        } catch (Exception e) {
            LOGGER.error("Unable to create BioModels reference database", e);
            System.exit(EXIT_FAILURE);
        }
        LOGGER.info("Successfully created BioModels reference database with db id of {}", biomodelsReferenceDatabase.get(DBID));

        return biomodelsReferenceDatabase;
    }

    /**
     * Retrieves a DatabaseObject from the db by its dbId
     *
     * @param tx   Neo4j Driver Transaction
     * @param dbId dbId of the node to retrieve
     * @return The DatabaseObject
     */
    public static Node getNodeByDbId(Transaction tx, long dbId) {
        String query = "MATCH (n:DatabaseObject {dbId: $nodeId}) RETURN n";
        Value parameters = Values.parameters("nodeId", dbId);
        Result result = tx.run(query, parameters);
        Record record = result.single();
        return record.get("n").asNode();
    }

    /**
     * Create a DatabaseObject in the database with specified properties and labels
     *
     * @param tx    Neo4j Driver Transaction
     * @param labels Labels for the node
     * @param props Properties of DatabaseObject
     * @return The created DatabaseObject
     */
    public static Node createNode(Transaction tx, List<String> labels, HashMap<String, Object> props) {
        String nodeLabels = String.join(":", labels);
        String query = "CREATE (n:DatabaseObject:" + nodeLabels + createNodeQuery(props) + ") RETURN n";
        Result result = tx.run(query, props);
        Record record = result.single();
        Node node = record.get("n").asNode();
        maxDbId++;
        return node;
    }

    private static String createNodeQuery(HashMap<String, Object> props) {
        String query = " {";
        List<String> queryParams = new ArrayList<>();
        for (String key : props.keySet()) {
            queryParams.add(key + ": $" + key);
        }
        query += String.join(", ", queryParams);
        query += "}";
        return query;
    }

    /**
     * Create a relationship between two nodes
     *
     * @param tx               Neo4j Driver Transaction
     * @param from             Source node
     * @param to               Target node
     * @param relationshipType Type of relationship
     * @param order            Order of the relationship
     * @param stoichiometry    Stoichiometry of the relationship
     */
    public static void createRelationship(Transaction tx, Node from, Node to, String relationshipType, int order, int stoichiometry) {
        String query = "MATCH (n1:DatabaseObject {dbId: $fromDbId}) " +
                "MATCH (n2:DatabaseObject {dbId: $toDbId}) " +
                "CREATE (n1)-[r:" + relationshipType + " {order: $order, stoichiometry: $stoichiometry}]->(n2)";
        Value parameters = Values.parameters(
                "fromDbId", from.get(DBID),
                "toDbId", to.get(DBID),
                "order", order,
                "stoichiometry", stoichiometry);
        tx.run(query, parameters);
    }

    /**
     * Returns the maximal dbId of a DatabaseObject stored in the database
     *
     * @param tx Neo4j Driver Transaction
     * @return The maximal dbId
     */
    public static Long getMaxDbId(Transaction tx) {
        String query = "MATCH (n:DatabaseObject) RETURN max(n.dbId) AS maxDbId";
        Result result = tx.run(query);
        Record record = result.single();
        return record.get("maxDbId").asLong();
    }

    /**
     * Returns the current date and time in the format yyyy-MM-dd HH:mm:ss.
     * @return The current date and time
     */
    public static String getDateTime() {
        Calendar calendar = GKApplicationUtilities.getCalendar();
        StringBuilder buffer = new StringBuilder();

        buffer.append(calendar.get(Calendar.YEAR)).append("-");

        int month = calendar.get(Calendar.MONTH);
        if (month < 10) buffer.append("0");
        buffer.append(month).append("-");

        int day = calendar.get(Calendar.DAY_OF_MONTH);
        if (day < 10) buffer.append("0");
        buffer.append(day);

        buffer.append(" ");
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (hour < 10) buffer.append("0");
        buffer.append(hour).append(":");

        int minute = calendar.get(Calendar.MINUTE);
        if (minute < 10) buffer.append("0");
        buffer.append(minute).append(":");

        int sec = calendar.get(Calendar.SECOND);
        if (sec < 10) buffer.append("0");
        buffer.append(sec);

        return buffer.toString();
    }

    /**
     * Log an error message and throw a RuntimeException
     *
     * @param errorMessage Error message to log
     * @param e            Exception to log
     */
    public static void logAndThrow(String errorMessage, Throwable e) {
        LOGGER.error(errorMessage, e);
        throw new RuntimeException(errorMessage, e);
    }
}
