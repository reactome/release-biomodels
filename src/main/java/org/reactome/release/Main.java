package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ReactomeJavaConstants;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.reactome.release.BioModelsUtilities.createInstanceEdit;
import static org.reactome.release.BioModelsUtilities.createNode;
import static org.reactome.release.BioModelsUtilities.createRelationship;
import static org.reactome.release.BioModelsUtilities.fetchBioModelsReferenceDatabase;
import static org.reactome.release.BioModelsUtilities.getMaxDbId;
import static org.reactome.release.BioModelsUtilities.getNodeByDbId;
import static org.reactome.release.BioModelsUtilities.getDateTime;
import static org.reactome.release.BioModelsUtilities.logAndThrow;
import static org.reactome.release.BioModelsUtilities.parse;

/**
 * Main class for BioModels insertion
 */
public class Main {

    private static final Map<String, Node> BIO_MODELS_INSTANCES = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Main method
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        LOGGER.info("Running BioModels insertion");
        runBioModelsInsertion(args);
        LOGGER.info("Completed BioModels insertion in {} seconds.", (System.currentTimeMillis() - start) / 1000L);
    }

    private static void runBioModelsInsertion(String[] args) {
        String pathToResources = args.length > 0 ? args[0] : "src/main/resources/config.properties";
        String pathToModels2Pathways = args.length > 1 ? args[1] : "src/main/resources/models2pathways.tsv";

        Properties props = loadProperties(pathToResources);

        try (Driver driver = getDriver(props); Session session = driver.session()) {
            session.writeTransaction(tx -> {
                long maxDbId = getMaxDbId(tx);

                // Create new instanceEdit in database to track modified pathways
                long personId = Long.parseLong(props.getProperty("personId"));
                Node instanceEdit = createInstanceEdit(tx, personId, "BioModels reference database creation");
                Map<String, Set<String>> pathwayStableIdToBioModelsIds =
                        parse(pathToModels2Pathways);

                Node referenceDatabase = fetchBioModelsReferenceDatabase(tx, instanceEdit);

                for (Node pathway : getPathwaysWithBioModelsIds(tx, pathwayStableIdToBioModelsIds.keySet())) {
                    String pathwayExtendedDisplayName = "[Pathway:" + pathway.get(DBID) + "] " + pathway.get(DISPLAY_NAME);
                    LOGGER.info("Adding BioModels ids to pathway {}", pathwayExtendedDisplayName);
                    Set<String> bioModelsIds = pathwayStableIdToBioModelsIds.get(pathway.get(STID).asString());
                    List<Node> bioModelsDatabaseIdentifiers =
                            createBioModelsDatabaseIdentifiers(bioModelsIds, referenceDatabase, instanceEdit, tx);
                    try {
                        for (int i = 0; i < bioModelsDatabaseIdentifiers.size(); i++) {
                            Node bioModelsDatabaseIdentifier = bioModelsDatabaseIdentifiers.get(i);
                            createRelationship(tx, pathway, bioModelsDatabaseIdentifier,
                                    ReactomeJavaConstants.crossReference, i, 1);
                        }
                        createRelationship(tx, instanceEdit, pathway,
                                ReactomeJavaConstants.modified, 0, 1);
                    } catch (Exception e) {
                        logAndThrow("Unable to update pathway " + pathwayExtendedDisplayName +
                                " with BioModels ids " + bioModelsIds, e);
                    }
                    LOGGER.info("BioModels ids successfully added to pathway " + pathwayExtendedDisplayName);
                }
                tx.commit();
                return null; // Return value for a transaction
            });
        } catch (Exception e) {
            logAndThrow("Error during BioModels insertion", e);
        }
    }

    /**
     * Load program configuration properties from a file
     *
     * @param pathToResources Path to file containing config properties
     * @return Configuration properties for the program
     */
    private static Properties loadProperties(String pathToResources) {
        Properties props = new Properties();
        try {
            props.load(Files.newInputStream(Paths.get(pathToResources)));
        } catch (IOException e) {
            logAndThrow("Error loading properties file: " + pathToResources, e);
        }

        return props;
    }

    private static Driver getDriver(Properties props) {
        String neo4jUser = props.getProperty("user", "neo4j");
        String neo4jPass = props.getProperty("password", "neo4j");
        String neo4jHost = props.getProperty("host", "localhost");
        String neo4jPort = props.getProperty("port", "7687");
        String neo4jUri = "bolt://" + neo4jHost + ":" + neo4jPort;

        return GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPass));
    }

    /**
     * Creates a DatabaseIdentifier instance for the BioModel identifier
     *
     * @param bioModelsIds Set of BioModels IDs
     * @param referenceDatabase Node instanceEdit attached to the person ID that is executing this program
     * @param instanceEdit Neo4j Driver Transaction
     * @param tx Neo4j Driver Transaction
     * @return List of DatabaseIdentifier objects pertaining to the BioModel identifier
     */
    private static List<Node> createBioModelsDatabaseIdentifiers(Set<String> bioModelsIds, Node referenceDatabase,
                                                                 Node instanceEdit, Transaction tx) {

        List<Node> bioModelsDatabaseIdentifiers = new ArrayList<>();

        for (String bioModelsId : bioModelsIds) {

            // If the identifier already had an object created during this run, use that. Otherwise, create one.
            if (BIO_MODELS_INSTANCES.get(bioModelsId) != null) {
                bioModelsDatabaseIdentifiers.add(BIO_MODELS_INSTANCES.get(bioModelsId));
            } else {
                LOGGER.info("Creating database identifier for BioModels id {}", bioModelsId);

                Node bioModelsDatabaseIdentifier = null;
                try {
                    String databaseName = referenceDatabase.get(DISPLAY_NAME).asString();

                    HashMap<String, Object> props = new HashMap<>();
                    props.put(DATABASE_NAME, databaseName);
                    props.put(DBID, maxDbId + 1);
                    props.put(DISPLAY_NAME, databaseName + ":" + bioModelsId);
                    props.put(ReactomeJavaConstants.identifier, bioModelsId);
                    props.put(SCHEMA_CLASS, ReactomeJavaConstants.DatabaseIdentifier);
                    props.put(ReactomeJavaConstants.url, referenceDatabase.get(ReactomeJavaConstants.url).asString() + bioModelsId);

                    bioModelsDatabaseIdentifier = createNode(tx, Collections.singletonList(ReactomeJavaConstants.DatabaseIdentifier), props);
                    maxDbId++;

                    createRelationship(tx, instanceEdit, bioModelsDatabaseIdentifier,
                            ReactomeJavaConstants.created, 0, 1);
                    createRelationship(tx, bioModelsDatabaseIdentifier, referenceDatabase,
                            ReactomeJavaConstants.referenceDatabase, 0, 1);
                } catch (Exception e) {
                    logAndThrow("Unable to create BioModels database identifier for " + bioModelsId, e);
                }

                bioModelsDatabaseIdentifiers.add(bioModelsDatabaseIdentifier);
                BIO_MODELS_INSTANCES.put(bioModelsId, bioModelsDatabaseIdentifier);
                LOGGER.info("Successfully created database identifier for BioModels id {}", bioModelsId);
            }
        }

        return bioModelsDatabaseIdentifiers;
    }

    private static List<Node> getPathwaysWithBioModelsIds(Transaction tx, Set<String> pathwayStableIds) {
        List<Node> pathwayNodes = new ArrayList<>();
        String query = "MATCH (p:DatabaseObject:Pathway) WHERE p.stId IN $pathwayStableIds RETURN p";
        org.neo4j.driver.Result result = tx.run(query, Collections.singletonMap("pathwayStableIds", pathwayStableIds));
        while (result.hasNext()) {
            Record record = result.next();
            pathwayNodes.add(record.get("p").asNode());
        }

        return pathwayNodes;
    }

        /**
     * Creates a new instance edit for the specified person ID and note.
     *
     * @param tx             Neo4j Driver Transaction
     * @param defaultPersonId The ID of the person for whom the instance edit is created
     * @param note           A note describing the instance edit
     * @return The newly created instance edit Node
     */
    private static Node createInstanceEdit(Transaction tx, long defaultPersonId, String note) {
        LOGGER.info("Creating new instance edit for person id {}", defaultPersonId);

        Node defaultPerson = null;
        try {
            defaultPerson = getNodeByDbId(tx, defaultPersonId);
        } catch (Exception e) {
            logAndThrow("Could not fetch Person entity with ID " + defaultPersonId +
                    ". Please check that a Person entity exists in the database with this ID", e);
        }

        Node newIE = null;
        try {
            String dateTime = getDateTime();
            String date = dateTime.split(" ")[0];
            String displayName = defaultPerson.get(ReactomeJavaConstants.surname).asString() + ", " +
                    defaultPerson.get(ReactomeJavaConstants.firstname).asString() + ", " + date;

            HashMap<String, Object> props = new HashMap<>();
            props.put(ReactomeJavaConstants.dateTime, dateTime);
            props.put(DBID, maxDbId + 1);
            props.put(DISPLAY_NAME, displayName);
            props.put(ReactomeJavaConstants.note, note);
            props.put(SCHEMA_CLASS, ReactomeJavaConstants.InstanceEdit);

            newIE = createNode(tx, Collections.singletonList(ReactomeJavaConstants.InstanceEdit), props);
            maxDbId++;

            createRelationship(tx, defaultPerson, newIE,
                    ReactomeJavaConstants.author, 0, 1);
            LOGGER.info("Successfully created new instance edit with db id {} for person id {}", newIE.get(DBID), defaultPerson.get(DBID));
        } catch (Exception e) {
            logAndThrow("Unable to create instance edit", e);
        }
        return newIE;
    }
}
