/**********************************************************************
Copyright (c) 2011 Andy Jefferson and others. All rights reserved.
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
   ...
**********************************************************************/
package org.datanucleus.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.datanucleus.ExecutionContext;
import org.datanucleus.PropertyNames;
import org.datanucleus.cache.CachedPC.CachedId;
import org.datanucleus.exceptions.NucleusObjectNotFoundException;
import org.datanucleus.identity.IdentityUtils;
import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.metadata.MetaDataUtils;
import org.datanucleus.metadata.RelationType;
import org.datanucleus.state.ObjectProvider;
import org.datanucleus.store.fieldmanager.AbstractFieldManager;
import org.datanucleus.store.types.ContainerHandler;
import org.datanucleus.store.types.ElementContainerAdapter;
import org.datanucleus.store.types.MapContainerAdapter;
import org.datanucleus.store.types.SCOUtils;
import org.datanucleus.util.ClassUtils;
import org.datanucleus.util.NucleusLogger;

/**
 * FieldManager responsible for retrieving the values from the provided CachedPC object.
 */
public class L2CacheRetrieveFieldManager extends AbstractFieldManager
{
    /** ObjectProvider of the object we are copying values into. */
    ObjectProvider op;

    /** Execution Context. */
    ExecutionContext ec;

    /** CachedPC that we are taking values from. */
    CachedPC cachedPC;

    List<Integer> fieldsNotLoaded = null;

    public L2CacheRetrieveFieldManager(ObjectProvider op, CachedPC cachedpc)
    {
        this.op = op;
        this.ec = op.getExecutionContext();
        this.cachedPC = cachedpc;
    }

    public int[] getFieldsNotLoaded()
    {
        if (fieldsNotLoaded == null)
        {
            return null;
        }
        int[] flds = new int[fieldsNotLoaded.size()];
        int i=0;
        for (Integer fldNum : fieldsNotLoaded)
        {
            flds[i++] = fldNum;
        }
        return flds;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchBooleanField(int)
     */
    @Override
    public boolean fetchBooleanField(int fieldNumber)
    {
        return (Boolean)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchByteField(int)
     */
    @Override
    public byte fetchByteField(int fieldNumber)
    {
        return (Byte)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchCharField(int)
     */
    @Override
    public char fetchCharField(int fieldNumber)
    {
        return (Character)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchDoubleField(int)
     */
    @Override
    public double fetchDoubleField(int fieldNumber)
    {
        return (Double)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchFloatField(int)
     */
    @Override
    public float fetchFloatField(int fieldNumber)
    {
        return (Float)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchIntField(int)
     */
    @Override
    public int fetchIntField(int fieldNumber)
    {
        return (Integer)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchLongField(int)
     */
    @Override
    public long fetchLongField(int fieldNumber)
    {
        return (Long)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchShortField(int)
     */
    @Override
    public short fetchShortField(int fieldNumber)
    {
        return (Short)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchStringField(int)
     */
    @Override
    public String fetchStringField(int fieldNumber)
    {
        return (String)cachedPC.getFieldValue(fieldNumber);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.fieldmanager.AbstractFieldManager#fetchObjectField(int)
     */
    @Override
    public Object fetchObjectField(int fieldNumber)
    {
        Object value = cachedPC.getFieldValue(fieldNumber);
        if (value == null)
        {
            return null;
        }

        AbstractMemberMetaData mmd = op.getClassMetaData().getMetaDataForManagedMemberAtAbsolutePosition(fieldNumber);
        return mmd.hasContainer() ? processContainerField(fieldNumber, value, mmd) : processField(fieldNumber, value, mmd);
    }

    private Object processContainerField(int fieldNumber, Object container, AbstractMemberMetaData mmd)
    {
        ContainerHandler containerHandler = op.getExecutionContext().getTypeManager().getContainerHandler(mmd.getType());
        return mmd.hasMap() ? processMapContainer(fieldNumber, container, mmd, containerHandler) : processElementContainer(fieldNumber, container, mmd, containerHandler);
    }

    private Object processMapContainer(int fieldNumber, Object cachedMapContainer, AbstractMemberMetaData mmd, ContainerHandler<Object, MapContainerAdapter<Object>> containerHandler)
    {
        // Map field, with fieldValue being Map<OID, OID>
        try
        {
            // Create Map<Key, Value> of same type as fieldValue
            MapContainerAdapter<Object> cachedMapContainerAdapter = containerHandler.getAdapter(cachedMapContainer);
            Object newContainer = newContainer(cachedMapContainer, mmd, containerHandler);
            MapContainerAdapter fieldMapContainerAdapter = containerHandler.getAdapter(newContainer);
            for (Entry<Object, Object> entry : cachedMapContainerAdapter.entries())
            {
                Object mapKey = null;
                if (mmd.getMap().keyIsPersistent())
                {
                    mapKey = getObjectFromCachedId(entry.getKey());
                }
                else
                {
                    mapKey = entry.getKey();
                }

                Object mapValue = null;
                Object mapValueId = entry.getValue();
                if (mapValueId != null)
                {
                    if (mmd.getMap().valueIsPersistent())
                    {
                        mapValue = getObjectFromCachedId(entry.getValue());
                    }
                    else
                    {
                        mapValue = entry.getValue();
                    }
                }

                fieldMapContainerAdapter.put(mapKey, mapValue);
            }

            return SCOUtils.wrapSCOField(op, fieldNumber, fieldMapContainerAdapter.getContainer(), true);
        }
        catch (Exception e)
        {
            // Error creating field value
            if (fieldsNotLoaded == null)
            {
                fieldsNotLoaded = new ArrayList<Integer>();
            }
            fieldsNotLoaded.add(fieldNumber);
            NucleusLogger.CACHE.error("Exception thrown creating value for" + " field " + mmd.getFullFieldName() + " of type " + cachedMapContainer.getClass().getName(), e);
     
            return null;
        }
    }

    private Object processElementContainer(int fieldNumber, Object cachedContainer, AbstractMemberMetaData mmd, ContainerHandler<Object, ElementContainerAdapter<Object>> containerHandler)
    {
        try
        {
            // For arrays, rely on the metadata value
            Object newContainer = mmd.hasArray() ? containerHandler.newContainer(mmd) : newContainer(cachedContainer, mmd, containerHandler);
            ElementContainerAdapter<Object> fieldContainerAdapter = containerHandler.getAdapter(newContainer);
            RelationType relType = mmd.getRelationType(ec.getClassLoaderResolver());

            ElementContainerAdapter<Object> cachedContainerAdapter = containerHandler.getAdapter(cachedContainer);
            if (relType == RelationType.NONE)
            {
                String elementType = mmd.hasCollection() ? mmd.getCollection().getElementType() : mmd.getArray().getElementType();
                boolean mutableType = ec.getTypeManager().isSecondClassMutableType(elementType);
                
                if (mutableType)
                {
                    // Container<mutable-SCO> - Create the container with a copy of the SCO mutable values
                    for (Object mutableValue : cachedContainerAdapter)
                    {
                        // TODO Need to return the value wrapped?
                        fieldContainerAdapter.add(SCOUtils.copyValue(mutableValue));
                    }
                }
                else
                {
                    // Container<immutable-SCO> - e.g. List<String> Create the container reusing the immutable object values
                    for (Object value : cachedContainerAdapter)
                    {
                        fieldContainerAdapter.add(value);
                    }
                }
            }
            else
            {
                // Restore the Container<OID> to Container<PC>
                for (Object cachedId : cachedContainerAdapter)
                {
                    Object element = cachedId == null ? null : getObjectFromCachedId(cachedId);  
                    fieldContainerAdapter.add(element);
                }
            }   
            
            return SCOUtils.wrapSCOField(op, fieldNumber, fieldContainerAdapter.getContainer(), true);
        }
        catch (Exception e)
        {
            // Error creating field value
            if (fieldsNotLoaded == null)
            {
                fieldsNotLoaded = new ArrayList<Integer>();
            }
            fieldsNotLoaded.add(fieldNumber);
            NucleusLogger.CACHE.error("Exception thrown creating value for field " + mmd.getFullFieldName() + " of type " + cachedContainer.getClass().getName(), e);
            return null;
        }
    }

    private Object processField(int fieldNumber, Object value, AbstractMemberMetaData mmd)
    {
        RelationType relType = mmd.getRelationType(ec.getClassLoaderResolver());
        if (relType == RelationType.NONE)
        {
            return SCOUtils.wrapSCOField(op, fieldNumber, SCOUtils.copyValue(value), true);
        }
        
        if (mmd.isSerialized() || MetaDataUtils.isMemberEmbedded(mmd, relType, ec.getClassLoaderResolver(), ec.getMetaDataManager()))
        {
            if (ec.getNucleusContext().getConfiguration().getBooleanProperty(PropertyNames.PROPERTY_CACHE_L2_CACHE_EMBEDDED))
            {
                if (value instanceof CachedPC)
                {
                    // Convert the CachedPC back into a managed object loading all cached fields
                    // TODO Perhaps only load fetch plan fields?
                    CachedPC valueCachedPC = (CachedPC)value;
                    AbstractClassMetaData cmd = ec.getMetaDataManager().getMetaDataForClass(valueCachedPC.getObjectClass(), ec.getClassLoaderResolver());
                    int[] fieldsToLoad = ClassUtils.getFlagsSetTo(valueCachedPC.getLoadedFields(), cmd.getAllMemberPositions(), true);

                    ObjectProvider valueOP = ec.getNucleusContext().getObjectProviderFactory().newForEmbedded(ec, cmd, op, mmd.getAbsoluteFieldNumber());
                    if (fieldsToLoad != null && fieldsToLoad.length > 0)
                    {
                        valueOP.replaceFields(fieldsToLoad, new L2CacheRetrieveFieldManager(valueOP, valueCachedPC));
                    }
                    // TODO When we have nested embedded objects with relations to non-embedded then we need to unload the fields not present in L2 cache

                    return valueOP.getObject();
                }
            }
        }

        // PC field so assume is the identity of the object
        try
        {
            return getObjectFromCachedId(value);
        }
        catch (NucleusObjectNotFoundException nonfe)
        {
            if (fieldsNotLoaded == null)
            {
                fieldsNotLoaded = new ArrayList<Integer>();
            }
            fieldsNotLoaded.add(fieldNumber);
            return null;
        }
    }

    private Object getObjectFromCachedId(Object cachedId)
    {
        Object pcId = null;
        String pcClassName = null;
        if (cachedId instanceof CachedId)
        {
            CachedId cId = (CachedId)cachedId;
            pcId = cId.getId();
            pcClassName = cId.getClassName();
        }
        else
        {
            pcId = cachedId;
            pcClassName = IdentityUtils.getTargetClassNameForIdentitySimple(pcId);
        }
        Class pcCls = ec.getClassLoaderResolver().classForName(pcClassName);
        return ec.findObject(pcId, null, pcCls, false, false);
    }
    
    /*
     * Copy container without using the container handler and metadata type info. Calling newContainer from
     * container handler for interfaces will return the default chosen implementation, but this causes the JDO
     * TCK (TestCollectionCollections) to fail because it expects Collection fields to return the same or at
     * most a List.
     */
    static <T> T newContainer(Object container, AbstractMemberMetaData mmd, ContainerHandler containerHandler)
    {
        Object newContainer;
        
        try
        {
            newContainer = container.getClass().newInstance();
        }
        catch (Exception e)
        {
            // Fallback for containers that don't have a default constructor
            newContainer = containerHandler.newContainer(mmd);
        }
        
        return (T) newContainer;
    }
}