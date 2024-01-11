package org.reactome.release;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gk.model.ReactomeJavaConstants;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.reactome.release.BioModelsUtilities.*;

/**
 * @author jweiser
 *
 */
public class Main {

    private static final Map<String, Node> bioModelsInstances = new HashMap<>();
    private static final Logger logger = LogManager.getLogger();

    /**
     * @param args Command line arguments for the BioModels program
     */
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        logger.info("Running BioModels insertion");
        runBioModelsInsertion(args);
        logger.info("Completed BioModels insertion in {} seconds.", (System.currentTimeMillis() - start)/1000L);
    }

    private static void runBioModelsInsertion(String[] args) {
        String pathToResources = args.length > 0 ? args[0] : "src/main/resources/config.properties";
        String pathToModels2Pathways = args.length > 1 ? args[1] : "src/main/resources/models2pathways.tsv";

        Properties props = loadProperties(pathToResources);

        try (Driver driver = getDriver(props); Session session = driver.session()) {
            session.writeTransaction(tx -> {
                maxDbId = BioModelsUtilities.getMaxDbId(tx);

                // Create new instanceEdit in database to track modified pathways
                long personId = Long.parseLong(props.getProperty("personId"));
                Node instanceEdit = createInstanceEdit(tx, personId, "BioModels reference database creation");
                Map<String, Set<String>> pathwayStableIdToBioModelsIds =
                        ModelsTSVParser.parse(pathToModels2Pathways);

                Node referenceDatabase = BioModelsUtilities.fetchBioModelsReferenceDatabase(tx, instanceEdit);

                for (Node pathway: getPathwaysWithBioModelsIds(tx, pathwayStableIdToBioModelsIds.keySet())) {
                    String pathwayExtendedDisplayName = "[Pathway:" + pathway.get(DBID) + "] " + pathway.get(DISPLAY_NAME);
                    logger.info("Adding BioModels ids to pathway {}", pathwayExtendedDisplayName);
                    Set<String> bioModelsIds = pathwayStableIdToBioModelsIds.get(pathway.get(STID).asString());
                    List<Node> bioModelsDatabaseIdentifiers =
                            createBioModelsDatabaseIdentifiers(bioModelsIds, referenceDatabase, instanceEdit, tx);
                    try {
                        for (int i=0; i < bioModelsDatabaseIdentifiers.size(); i++) {
                            Node bioModelsDatabaseIdentifier = bioModelsDatabaseIdentifiers.get(i);
                            BioModelsUtilities.createRelationship(tx, pathway, bioModelsDatabaseIdentifier,
                                    ReactomeJavaConstants.crossReference, i, 1);
                        }
                        BioModelsUtilities.createRelationship(tx, instanceEdit, pathway,
                                ReactomeJavaConstants.modified, 0, 1);
                    } catch (Exception e) {
                        BioModelsUtilities.logAndThrow("Unable to update pathway " + pathwayExtendedDisplayName +
                                " with BioModels ids " + bioModelsIds, e);
                    }
                    logger.info("BioModels ids successfully added to pathway " + pathwayExtendedDisplayName);
                }
                tx.commit();
                return null; // Return value for a transaction
            });
        } catch (Exception e) {
            BioModelsUtilities.logAndThrow("Error during BioModels insertion", e);
        }
    }

    /**
     * Load program configuration properties from a file
     * @param pathToResources -- Path to file containing config properties
     * @return -- (Properties) Configuration properties for the program
     */
    private static Properties loadProperties(String pathToResources) {
        Properties props = new Properties();
        try {
            props.load(Files.newInputStream(Paths.get(pathToResources)));
        } catch (IOException e) {
            BioModelsUtilities.logAndThrow("Error loading properties file: " + pathToResources, e);
        }

        return props;
    }

    private static Driver getDriver(Properties props) {
        String neo4jUri = props.getProperty("neo4jUri", "bolt://localhost:7687");
        String neo4jUser = props.getProperty("neo4jUser", "neo4j");
        String neo4jPassword = props.getProperty("neo4jPassword", "neo4j");

        return GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPassword));
    }

    /**
     * Creates a DatabaseIdentifier instance for the BioModel identifier, if it hasn't already been created during the current run.
     * @param bioModelsIds -- Set of BioModels IDs
     * @param instanceEdit -- Node instanceEdit attached to the person ID that is executing this program
     * @param tx -- Neo4j Driver Transaction
     * @return -- Returns a list of DatabaseIdentifier objects pertaining to the BioModel identifier
     */
    private static List<Node> createBioModelsDatabaseIdentifiers(Set<String> bioModelsIds, Node referenceDatabase,
                                                                 Node instanceEdit, Transaction tx) {

        List<Node> bioModelsDatabaseIdentifiers = new ArrayList<>();

        for (String bioModelsId : bioModelsIds) {

            // If the identifier already had an object created during this run, use that. Otherwise, create one.
            if (bioModelsInstances.get(bioModelsId) != null) {
                bioModelsDatabaseIdentifiers.add(bioModelsInstances.get(bioModelsId));
            } else {
                logger.info("Creating database identifier for BioModels id {}", bioModelsId);

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

                    bioModelsDatabaseIdentifier = BioModelsUtilities.createNode(tx, Collections.singletonList(ReactomeJavaConstants.DatabaseIdentifier), props);
                    maxDbId++;

                    BioModelsUtilities.createRelationship(tx, instanceEdit, bioModelsDatabaseIdentifier,
                            ReactomeJavaConstants.created, 0, 1);
                    BioModelsUtilities.createRelationship(tx, bioModelsDatabaseIdentifier, referenceDatabase,
                            ReactomeJavaConstants.referenceDatabase, 0, 1);
                } catch (Exception e) {
                    BioModelsUtilities.logAndThrow("Unable to create BioModels database identifier for " + bioModelsId, e);
                }

                bioModelsDatabaseIdentifiers.add(bioModelsDatabaseIdentifier);
                bioModelsInstances.put(bioModelsId, bioModelsDatabaseIdentifier);
                logger.info("Successfully created database identifier for BioModels id {}", bioModelsId);
            }
        }

        return bioModelsDatabaseIdentifiers;
    }

    private static List<Node> getPathwaysWithBioModelsIds(Transaction tx, Set<String> pathwayStableIds) {
        List<Node> pathwayNodes = new ArrayList<>();
        String query = "MATCH (p:DatabaseObject:Pathway) WHERE p.stId IN $pathwayStableIds RETURN p";
        Result result = tx.run(query, Collections.singletonMap("pathwayStableIds", pathwayStableIds));
        while (result.hasNext()) {
            Record record = result.next();
            pathwayNodes.add(record.get("p").asNode());
        }

        return pathwayNodes;
    }

    private static Node createInstanceEdit(Transaction tx, long defaultPersonId, String note) {
        logger.info("Creating new instance edit for person id {}", defaultPersonId);

        Node defaultPerson = null;
        try {
            defaultPerson = BioModelsUtilities.getNodeByDbId(tx, defaultPersonId);
        } catch (Exception e) {
            BioModelsUtilities.logAndThrow("Could not fetch Person entity with ID " + defaultPersonId +
                ". Please check that a Person entity exists in the database with this ID", e);
        }

        Node newIE = null;
        try {
            String dateTime = BioModelsUtilities.getDateTime();
            String date = dateTime.split(" ")[0];
            String displayName = defaultPerson.get(ReactomeJavaConstants.surname).asString() + ", " +
                    defaultPerson.get(ReactomeJavaConstants.firstname).asString() + ", " + date;

            HashMap<String, Object> props = new HashMap<>();
            props.put(ReactomeJavaConstants.dateTime, dateTime);
            props.put(DBID, maxDbId + 1);
            props.put(DISPLAY_NAME, displayName);
            props.put(ReactomeJavaConstants.note, note);
            props.put(SCHEMA_CLASS, ReactomeJavaConstants.InstanceEdit);

            newIE = BioModelsUtilities.createNode(tx, Collections.singletonList(ReactomeJavaConstants.InstanceEdit), props);
            maxDbId++;

            BioModelsUtilities.createRelationship(tx, defaultPerson, newIE,
                    ReactomeJavaConstants.author, 0, 1);
            logger.info("Successfully created new instance edit with db id {} for person id {}", newIE.get(DBID), defaultPerson.get(DBID));
        } catch (Exception e) {
            BioModelsUtilities.logAndThrow("Unable to create instance edit", e);
        }
        return newIE;
    }
}
