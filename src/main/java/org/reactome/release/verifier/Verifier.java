package org.reactome.release.verifier;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;

import static org.reactome.release.common.Utils.getDriver;

public class Verifier {
    @Parameter(names ={"--currentUser", "--cu"})
    private String currentUserName = "neo4j";

    @Parameter(names ={"--currentPassword", "--cp"})
    private String currentPassword = "root";

    @Parameter(names ={"--currentHost", "--ch"})
    private String currentHost = "localhost";

    @Parameter(names ={"--currentPort", "--cP"})
    private long currentPort = 7687;

    @Parameter(names ={"--previousUser", "--pu"})
    private String previousUserName = "neo4j";

    @Parameter(names ={"--previousPassword", "--pp"})
    private String previousPassword = "root";

    @Parameter(names ={"--previousHost", "--ph"})
    private String previousHost = "localhost";

    @Parameter(names ={"--previousPort", "--pP"})
    private long previousPort = 7688;

    public static void main(String[] args) {
        Verifier verifier = new Verifier();
        JCommander.newBuilder()
            .addObject(verifier)
            .build()
            .parse(args);

        verifier.run();
    }

    public void run() {
        // Connect to graph database
        Driver currentReleaseDriver =
            getDriver(this.currentUserName, this.currentPassword, this.currentHost, this.currentPort);
        Driver previousReleaseDriver =
            getDriver(this.previousUserName, this.previousPassword, this.previousHost, this.previousPort);

        Long currentReleaseBioModelsCount = getBioModelsCrossReferenceCount(currentReleaseDriver);
        if (currentReleaseBioModelsCount == null) {
            System.err.println("Unable to find BioModels Database reference database for current release");
            System.exit(1);
        }

        Long previousReleaseBioModelsCount = getBioModelsCrossReferenceCount(previousReleaseDriver);
        if (currentReleaseBioModelsCount < previousReleaseBioModelsCount) {
            System.err.println(
                String.format("Current BioModels cross reference count (%d) is lower than the previous release's count (%d)",
                        currentReleaseBioModelsCount, previousReleaseBioModelsCount));
            System.exit(1);
        }

        System.out.println(
            String.format("Proper count for BioModels - current (%d); previous (%d)",
                currentReleaseBioModelsCount, previousReleaseBioModelsCount)
        );
    }

    private static Long getBioModelsCrossReferenceCount(Driver driver) {
        Result result = driver.session().run(
            "MATCH (rd:ReferenceDatabase)-[:referenceDatabase]-(cr) " +
            "WHERE rd.displayName = \"BioModels Database\" " +
            "RETURN count(cr) as CrossRefCount"
        );

        if (!result.hasNext()) {
            return null;
        }

        return result.next().get("CrossRefCount").asLong();
    }
}
