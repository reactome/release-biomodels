package org.reactome.release;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(MockitoExtension.class)
public class BioModelsUtilitiesTester {

    @Mock
    private MySQLAdaptor mockAdaptor;
    @Mock
    private GKInstance mockBiomodelsDatabase;

//    @Test
//    public void bioModelsReferenceDatabaseExistsInDBReturnsInstance() throws Exception {
//        Collection<GKInstance> collectionWithMockDatabaseInstance = new ArrayList<>();
//        collectionWithMockDatabaseInstance.add(mockBiomodelsDatabase);
//        Mockito.when(mockAdaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "BioModels")).thenReturn(collectionWithMockDatabaseInstance);
//
//        GKInstance returnedDbInstance = BioModelsUtilities.retrieveBioModelsDatabaseInstance(mockAdaptor);
//
//        assertThat(returnedDbInstance, is(equalTo(mockBiomodelsDatabase)));
//    }

//    @Test
//    public void bioModelsReferenceDatabaseDoesNotExistInDBReturnsNull() throws Exception {
//        Collection<GKInstance> collectionWithoutMockDatabaseInstance = new ArrayList<>();
//        Mockito.when(mockAdaptor.fetchInstanceByAttribute("ReferenceDatabase", "name", "=", "BioModels")).thenReturn(collectionWithoutMockDatabaseInstance);
//
//        GKInstance returnedDbInstance = BioModelsUtilities.retrieveBioModelsDatabaseInstance(mockAdaptor);
//
//        assertThat(returnedDbInstance, is(nullValue()));
//    }
}
