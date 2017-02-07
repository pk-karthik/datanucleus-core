/**********************************************************************
Copyright (c) 2009 Erik Bengtson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:
2011 Andy Jefferson - extended for bulk usage
    ...
**********************************************************************/
package org.datanucleus.store;

import org.datanucleus.ExecutionContext;
import org.datanucleus.exceptions.NucleusDataStoreException;
import org.datanucleus.exceptions.NucleusObjectNotFoundException;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.state.ObjectProvider;

/**
 * Interface defining persistence operations of a StoreManager.
 * This performs the low level communication with the actual datastore.
 * To be implemented by all new datastore support. Please use AbstractPersistenceHandler and extend it.
 * All PersistenceHandlers should have a single constructor with signature
 * <pre>
 * public MyPersistenceHandler(StoreManager storeMgr)
 * {
 * }
 * </pre>
 */
public interface StorePersistenceHandler
{
    /**
     * Method to close the persistence handler, and release any resources.
     */
    void close();

    /**
     * Signal that a batch of operations are starting for the specified ExecutionContext.
     * The batch type allows the store plugin to create whatever type of batch it needs.
     * @param ec The ExecutionContext
     * @param batchType Type of this batch that is starting
     */
    void batchStart(ExecutionContext ec, PersistenceBatchType batchType);

    /**
     * Signal that the current batch of operations are ending for the specified ExecutionContext.
     * @param ec The ExecutionContext
     * @param type Type of batch that is ending
     */
    void batchEnd(ExecutionContext ec, PersistenceBatchType type);

    /**
     * Inserts a persistent object into the database.
     * @param op The ObjectProvider of the object to be inserted.
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    void insertObject(ObjectProvider op);

    /**
     * Method to insert an array of objects to the datastore.
     * @param ops ObjectProviders for the objects to insert
     */
    void insertObjects(ObjectProvider... ops);

    /**
     * Updates a persistent object in the datastore.
     * @param op The ObjectProvider of the object to be updated.
     * @param fieldNumbers The numbers of the fields to be updated.
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    void updateObject(ObjectProvider op, int fieldNumbers[]);

    /**
     * Deletes a persistent object from the datastore.
     * @param op The ObjectProvider of the object to be deleted.
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    void deleteObject(ObjectProvider op);

    /**
     * Method to delete an array of objects from the datastore.
     * @param ops ObjectProviders for the objects to delete
     */
    void deleteObjects(ObjectProvider... ops);

    /**
     * Fetches a persistent object from the database.
     * @param op The ObjectProvider of the object to be fetched.
     * @param fieldNumbers The numbers of the fields to be fetched.
     * @throws NucleusObjectNotFoundException if the object doesn't exist
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    void fetchObject(ObjectProvider op, int fieldNumbers[]);

    /**
     * Locates this object in the datastore.
     * @param op The ObjectProvider for the object to be found
     * @throws NucleusObjectNotFoundException if the object doesn't exist
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    void locateObject(ObjectProvider op);

    /**
     * Locates object(s) in the datastore.
     * @param ops ObjectProvider(s) for the object(s) to be found
     * @throws NucleusObjectNotFoundException if an object doesn't exist
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    void locateObjects(ObjectProvider[] ops);

    /**
     * Method to find a persistable object with the specified id from the datastore, if the StoreManager 
     * supports this operation (optional). This allows for datastores that perform the instantiation of 
     * objects directly (such as ODBMS). With other types of datastores (e.g RDBMS) this method returns null.
     * @param ec The ExecutionContext
     * @param id the id of the object in question.
     * @return a persistable object with a valid object state (for example: hollow) or null, indicating that the implementation leaves the instantiation work to DataNucleus.
     * @throws NucleusObjectNotFoundException if this route is supported yet the object doesn't exist
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    public Object findObject(ExecutionContext ec, Object id);

    /**
     * Method to find an array of objects with the specified identities from the datastore.
     * This allows for datastores that perform the instantiation of objects directly (such as ODBMS). 
     * With other types of datastores (e.g RDBMS) this method returns null.
     * @param ec The ExecutionContext
     * @param ids identities of the object(s) to retrieve
     * @return The persistable objects with these identities (in the same order as <pre>ids</pre>)
     * @throws NucleusObjectNotFoundException if an object doesn't exist
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    public Object[] findObjects(ExecutionContext ec, Object[] ids);

    /**
     * Method to find the object with the specified value(s) for the member(s) of the specified type.
     * @param ec ExecutionContext
     * @param cmd Metadata for the class in question
     * @param memberNames Member(s) that define the object
     * @param values Value(s) for the member(s)
     * @return The object with these member value(s)
     * @throws NucleusObjectNotFoundException if an object doesn't exist
     * @throws NucleusDataStoreException when an error occurs in the datastore communication
     */
    public Object findObjectForKeys(ExecutionContext ec, AbstractClassMetaData cmd, String[] memberNames, Object[] values);

    /**
     * Enum for the type of a batched operation
     */
    public static enum PersistenceBatchType 
    {
        PERSIST,
        DELETE,
        LOCATE
    }
}