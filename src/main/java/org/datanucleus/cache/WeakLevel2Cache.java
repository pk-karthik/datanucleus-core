/**********************************************************************
Copyright (c) 2004 Andy Jefferson and others. All rights reserved.
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.datanucleus.NucleusContext;
import org.datanucleus.PropertyNames;
import org.datanucleus.api.ApiAdapter;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.ConcurrentReferenceHashMap;
import org.datanucleus.util.ConcurrentReferenceHashMap.ReferenceType;
import org.datanucleus.util.Localiser;

/**
 * Weak referenced implementation of a Level 2 cache.
 * <p>
 * Operates with 2 maps internally. One stores all pinned objects that have been
 * selected to be retained by user's application. The other stores all other objects.
 * This second map is the default location where objects are placed when being added here.
 * The second (unpinned) map stores weak references meaning that they can get garbage
 * collected as necessary by the JVM.
 * </P>
 * <P>
 * Maintains collections of the classes and the identities that are to be pinned if they ever
 * are put into the cache. These are defined by the pinAll(), pin() methods.
 * </P>
 * <P>
 * All mutating methods, and the get method have been synchronized to prevent conflicts.
 * </P>
 */
public class WeakLevel2Cache implements Level2Cache
{
    private static final long serialVersionUID = -4521848285620167823L;

    /** Collection of pinned classes whose objects should be pinned if they ever reach the cache. */
    protected Collection<PinnedClass> pinnedClasses;

    /** Collection of ids whose objects should be pinned if they ever reach the cache. */
    protected Collection pinnedIds;

    /** Pinned objects cache. */
    protected Map pinnedCache;

    /** Unpinned objects cache. */
    protected transient Map unpinnedCache; // transient since WeakValueMap is not serialisable

    protected ApiAdapter apiAdapter;

    private int maxSize = -1;

    protected WeakLevel2Cache()
    {
        // nothing to do
    }

    /**
     * Constructor.
     * @param nucleusCtx Context
     */
    public WeakLevel2Cache(NucleusContext nucleusCtx)
    {
        apiAdapter = nucleusCtx.getApiAdapter();
        pinnedCache = new HashMap();
        unpinnedCache = new ConcurrentReferenceHashMap<>(1, ReferenceType.STRONG, ReferenceType.WEAK);
        maxSize = nucleusCtx.getConfiguration().getIntProperty(PropertyNames.PROPERTY_CACHE_L2_MAXSIZE);
    }

    /**
     * Method to close the cache when no longer needed. Provides a hook to release resources etc.
     */
    public void close()
    {
        evictAll();
        pinnedCache = null;
        unpinnedCache = null;
    }

    /**
     * Method to evict an object from the cache.
     * @param oid The id of the object to evict
     */
    public synchronized void evict(Object oid)
    {
        if (oid == null)
        {
            return;
        }

        unpinnedCache.remove(oid);
        pinnedCache.remove(oid);
    }

    /**
     * Method to evict all objects from the L2 cache.
     */
    public synchronized void evictAll()
    {
        unpinnedCache.clear();
        pinnedCache.clear();
    }

    /**
     * Method to evict all objects of the given types from the cache.
     * @param pcClass The class to evict
     * @param subclasses Whether to also evict subclasses
     */
    public synchronized void evictAll(Class pcClass, boolean subclasses)
    {
        if (pcClass == null)
        {
            return;
        }

        Collection oidsToEvict = new HashSet();

        // Find objects to evict from pinned
        Collection pinnedObjects = pinnedCache.entrySet();
        Iterator pinnedIter = pinnedObjects.iterator();
        while (pinnedIter.hasNext())
        {
            Map.Entry entry = (Map.Entry) pinnedIter.next();
            CachedPC pc = (CachedPC) entry.getValue();
            if (pcClass.getName().equals(pc.getObjectClass().getName()) || (subclasses && pcClass.isAssignableFrom(pc.getObjectClass())))
            {
                oidsToEvict.add(entry.getKey());
            }
        }

        // Find objects to evict from unpinned
        Collection unpinnedObjects = unpinnedCache.entrySet();
        Iterator unpinnedIter = unpinnedObjects.iterator();
        while (unpinnedIter.hasNext())
        {
            Map.Entry entry = (Map.Entry) unpinnedIter.next();
            CachedPC pc = (CachedPC) entry.getValue();
            if (pc != null && pcClass.getName().equals(pc.getObjectClass().getName()) || (subclasses && pcClass.isAssignableFrom(pc.getObjectClass())))
            {
                oidsToEvict.add(entry.getKey());
            }
        }

        // Evict the objects
        if (!oidsToEvict.isEmpty())
        {
            evictAll(oidsToEvict);
        }
    }

    /**
     * Method to evict the objects with the specified ids.
     * @param oids The ids of the objects to evict
     */
    public synchronized void evictAll(Collection oids)
    {
        if (oids == null)
        {
            return;
        }

        Iterator iter = oids.iterator();
        while (iter.hasNext())
        {
            evict(iter.next());
        }
    }

    /**
     * Method to evict the objects with the specified ids.
     * @param oids The ids of the objects to evict
     */
    public synchronized void evictAll(Object[] oids)
    {
        if (oids == null)
        {
            return;
        }

        for (int i = 0; i < oids.length; i++)
        {
            evict(oids[i]);
        }
    }

    /**
     * Method to pin an object to the cache.
     * @param oid The id of the object to pin
     */
    public synchronized void pin(Object oid)
    {
        if (oid == null)
        {
            return;
        }

        if (pinnedIds == null)
        {
            pinnedIds = new HashSet();
        }
        else if (!pinnedIds.contains(oid))
        {
            // Add this oid to the to-be-pinned collection
            pinnedIds.add(oid);
        }

        Object pc = unpinnedCache.get(oid);
        if (pc != null)
        {
            pinnedCache.put(oid, pc);
            unpinnedCache.remove(oid);
        }
    }

    /**
     * Method to pin all objects of the given types.
     * @param cls The class
     * @param subs Whether to include subclasses
     */
    public synchronized void pinAll(Class cls, boolean subs)
    {
        if (cls == null)
        {
            return;
        }

        if (pinnedClasses == null)
        {
            pinnedClasses = new HashSet();
        }

        // Check if it already exists as a pinned class
        PinnedClass pinnedCls = new PinnedClass(cls, subs);
        if (pinnedClasses.contains(pinnedCls))
        {
            return;
        }
        pinnedClasses.add(pinnedCls);

        // Update all currently unpinned objects to comply with the new class specification
        Collection unpinnedObjects = unpinnedCache.values();
        Iterator unpinnedIter = unpinnedObjects.iterator();
        while (unpinnedIter.hasNext())
        {
            CachedPC obj = (CachedPC) unpinnedIter.next();
            if ((subs && cls.isInstance(obj.getObjectClass())) || cls.getName().equals(obj.getObjectClass().getName()))
            {
                pin(obj);
            }
        }
    }

    /**
     * Method to pin all of the supplied objects
     * @param oids The Object ids to pin
     */
    public synchronized void pinAll(Collection oids)
    {
        if (oids == null)
        {
            return;
        }

        Iterator iter = oids.iterator();
        while (iter.hasNext())
        {
            pin(iter.next());
        }
    }

    /**
     * Method to pin all of the supplied objects
     * @param oids The object ids to pin
     */
    public synchronized void pinAll(Object[] oids)
    {
        if (oids == null)
        {
            return;
        }

        for (int i = 0; i < oids.length; i++)
        {
            pin(oids[i]);
        }
    }

    /**
     * Method to unpin an object
     * @param oid The object id
     */
    public synchronized void unpin(Object oid)
    {
        if (oid == null)
        {
            return;
        }

        Object pc = pinnedCache.get(oid);
        if (pc != null)
        {
            unpinnedCache.put(oid, pc);
            pinnedCache.remove(oid);
        }

        if (pinnedIds != null && pinnedIds.contains(oid))
        {
            // Remove this oid from the to-be-pinned collection
            pinnedIds.remove(oid);
        }
    }

    /**
     * Method to unpin all objects of the specified types.
     * @param cls Base class
     * @param subs Whether to include subclasses
     */
    public synchronized void unpinAll(Class cls, boolean subs)
    {
        if (cls == null)
        {
            return;
        }

        // Remove the class from the pinned collection
        if (pinnedClasses != null)
        {
            PinnedClass pinnedCls = new PinnedClass(cls, subs);
            pinnedClasses.remove(pinnedCls);
        }

        // Unpin all objects of this type currently pinned
        Collection pinnedObjects = pinnedCache.values();
        Iterator pinnedIter = pinnedObjects.iterator();
        while (pinnedIter.hasNext())
        {
            CachedPC obj = (CachedPC) pinnedIter.next();
            if ((subs && cls.isInstance(obj.getObjectClass())) || cls.getName().equals(obj.getObjectClass().getName()))
            {
                unpin(obj);
            }
        }
    }

    /**
     * Method to unpin all of the supplied objects
     * @param oids The object ids to unpin
     */
    public synchronized void unpinAll(Collection oids)
    {
        if (oids == null)
        {
            return;
        }

        Iterator iter = oids.iterator();
        while (iter.hasNext())
        {
            unpin(iter.next());
        }
    }

    /**
     * Method to unpin all of the specified objects
     * @param oids The object ids to unpin
     */
    public synchronized void unpinAll(Object[] oids)
    {
        if (oids == null)
        {
            return;
        }

        for (int i = 0; i < oids.length; i++)
        {
            unpin(oids[i]);
        }
    }

    /**
     * Accessor for an object from the cache. The returned object will not have a ObjectProvider connected.
     * This is because data stored in the Level 2 cache is ObjectProvider and PersistenceManager independent.
     * @param oid The Object ID
     * @return The L2 cacheable object
     */
    public synchronized CachedPC get(Object oid)
    {
        if (oid == null)
        {
            return null;
        }

        CachedPC pc = (CachedPC) pinnedCache.get(oid);
        if (pc != null)
        {
            return pc;
        }
        pc = (CachedPC) unpinnedCache.get(oid);
        return pc;
    }

    /*
     * (non-Javadoc)
     * @see org.datanucleus.cache.Level2Cache#getAll(java.util.Collection)
     */
    public Map<Object, CachedPC> getAll(Collection oids)
    {
        if (oids == null)
        {
            return null;
        }
        Map<Object, CachedPC> objs = new HashMap<Object, CachedPC>();
        for (Object oid : oids)
        {
            CachedPC obj = get(oid);
            if (obj != null)
            {
                objs.put(oid, obj);
            }
        }
        return objs;
    }

    /**
     * Accessor for the number of pinned objects in the cache.
     * @return Number of pinned objects
     */
    public int getNumberOfPinnedObjects()
    {
        return pinnedCache.size();
    }

    /**
     * Accessor for the number of unpinned objects in the cache.
     * @return Number of unpinned objects
     */
    public int getNumberOfUnpinnedObjects()
    {
        return unpinnedCache.size();
    }

    /**
     * Accessor for the total number of objects in the L2 cache.
     * @return Number of objects
     */
    public int getSize()
    {
        return getNumberOfPinnedObjects() + getNumberOfUnpinnedObjects();
    }

    /*
     * (non-Javadoc)
     * @see org.datanucleus.cache.Level2Cache#putAll(java.util.Map)
     */
    public void putAll(Map<Object, CachedPC> objs)
    {
        if (objs == null)
        {
            return;
        }

        // TODO Support maxSize, and use putAll

        // Just fallback to doing multiple puts
        Iterator<Map.Entry<Object, CachedPC>> entryIter = objs.entrySet().iterator();
        while (entryIter.hasNext())
        {
            Map.Entry<Object, CachedPC> entry = entryIter.next();
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Method to put an object in the cache. Note that the pc object being passed in must NOT have a
     * ObjectProvider connected. Data stored in the Level 2 cache has to be independent of PersistenceManager
     * and ObjectProvider.
     * @param oid The Object id for this object
     * @param pc The cacheable object
     * @return The value previously associated with this oid
     */
    public synchronized CachedPC put(Object oid, CachedPC pc)
    {
        if (oid == null || pc == null)
        {
            NucleusLogger.CACHE.warn(Localiser.msg("004011"));
            return null;
        }
        else if (maxSize >= 0 && getSize() == maxSize)
        {
            return null;
        }

        // Check if we should pin this
        // a). check if the object class type is to be pinned
        boolean toBePinned = false;
        if (pinnedClasses != null)
        {
            Iterator<PinnedClass> pinnedClsIter = pinnedClasses.iterator();
            while (pinnedClsIter.hasNext())
            {
                PinnedClass pinCls = pinnedClsIter.next();
                if (pinCls.cls.getName().equals(pc.getObjectClass().getName()) || (pinCls.subclasses && pinCls.cls.isAssignableFrom(pc.getObjectClass())))
                {
                    toBePinned = true;
                    break;
                }
            }
        }

        // b). check if the id is to be pinned
        if (pinnedIds != null && pinnedIds.contains(oid))
        {
            toBePinned = true;
        }

        Object obj = null;
        if (pinnedCache.get(oid) != null)
        {
            // Update the pinned cache if object is already there
            obj = pinnedCache.put(oid, pc);
            if (obj != null)
            {
                return (CachedPC) obj;
            }
        }
        else
        {
            if (toBePinned)
            {
                // Update the pinned cache
                pinnedCache.put(oid, pc);
                unpinnedCache.remove(oid); // Just in case it was unpinned previously
            }
            else
            {
                // Update the unpinned cache otherwise
                obj = unpinnedCache.put(oid, pc);
                if (obj != null)
                {
                    return (CachedPC) obj;
                }
            }
        }

        return null;
    }

    /**
     * Method to check if an object with the specified id is in the cache
     * @param oid The object ID
     * @return Whether it is present
     */
    public boolean containsOid(Object oid)
    {
        return pinnedCache.containsKey(oid) || unpinnedCache.containsKey(oid);
    }

    /**
     * Accessor for whether the cache is empty.
     * @return Whether it is empty.
     */
    public boolean isEmpty()
    {
        return pinnedCache.isEmpty() && unpinnedCache.isEmpty();
    }

    private void writeObject(ObjectOutputStream out) throws IOException
    {
        out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
    {
        // our "pseudo-constructor"
        in.defaultReadObject();
        unpinnedCache = new ConcurrentReferenceHashMap<>(1, ReferenceType.STRONG, ReferenceType.WEAK);
    }
}