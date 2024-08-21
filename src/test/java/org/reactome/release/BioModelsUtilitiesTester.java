package org.reactome.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(MockitoExtension.class)
public class BioModelsUtilitiesTester {

    private static Driver driver;
    @Mock
    private Transaction mockTransaction;
    @Mock
    Result mockResult;
    @Mock
    org.neo4j.driver.Record mockRecord;
    @Mock
    Value mockValue;
    @Mock
    private Node mockBioModelsDatabase;

    @Test
    public void bioModelsReferenceDatabaseExistsInDBReturnsInstance() throws Exception {
        Mockito.when(mockTransaction.run("MATCH (n:DatabaseObject:ReferenceDatabase) WHERE 'BioModels' IN n.name RETURN n")).thenReturn(mockResult);
        Mockito.when(mockResult.hasNext()).thenReturn(true);
        Mockito.when(mockResult.single()).thenReturn(mockRecord);
        Mockito.when(mockRecord.get("n")).thenReturn(mockValue);
        Mockito.when(mockValue.asNode()).thenReturn(mockBioModelsDatabase);

        Node returnedDbInstance = BioModelsUtilities.retrieveBioModelsDatabaseInstance(mockTransaction);
        assertThat(returnedDbInstance, is(equalTo(mockBioModelsDatabase)));
    }

    @Test
    public void bioModelsReferenceDatabaseDoesNotExistInDBReturnsNull() throws Exception {
        Mockito.when(mockTransaction.run("MATCH (n:DatabaseObject:ReferenceDatabase) WHERE 'BioModels' IN n.name RETURN n")).thenReturn(mockResult);
        Mockito.when(mockResult.hasNext()).thenReturn(false);

        Node returnedDbInstance = BioModelsUtilities.retrieveBioModelsDatabaseInstance(mockTransaction);

        assertThat(returnedDbInstance, is(nullValue()));
    }
}
