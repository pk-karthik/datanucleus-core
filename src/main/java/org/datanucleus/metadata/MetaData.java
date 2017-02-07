/**********************************************************************
Copyright (c) 2004 Erik Bengtson and others. All rights reserved.
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
2005 Andy Jefferson - addition of states
2007 Andy Jefferson - moved extensions to this class, javadocs
    ...
**********************************************************************/
package org.datanucleus.metadata;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.exceptions.NucleusException;

/**
 * Base class for all MetaData.
 * <h3>MetaData Lifecycle</h3>
 * The states represent the lifecycle of a MetaData object. The lifecycle goes as follows :
 * <OL>
 * <LI>MetaData object is created (values passed in from a parsed file, or manually generated)</LI>
 * <LI>MetaData object is populated (maybe pass in a class that it represents, creating any additional information that wasn't in the initial data).</LI>
 * <LI>MetaData object is initialised (any internal arrays are set up, and additions of data is blocked from this point).
 * <LI>MetaData object is added to with runtime information like actual column names and types in use.</LI> 
 * </OL>
 * <h3>MetaData Extensibility</h3>
 * <p>
 * All MetaData elements are extensible with extensions. We only store the DataNucleus vendor extensions here.
 */
public class MetaData implements Serializable
{
    private static final long serialVersionUID = -5477406260914096062L;

    /** State representing the start state of MetaData, representing the initial values passed in. */
    public static final int METADATA_CREATED_STATE = 0;

    /** State reflecting that MetaData has been populated with real class definition adding any defaulted info. */
    public static final int METADATA_POPULATED_STATE = 1;

    /** State reflecting that MetaData object has been initialised with any internal info required. */
    public static final int METADATA_INITIALISED_STATE = 2;

    /** State reflecting that MetaData object has been modified with usage information (e.g defaulted column names). */
    public static final int METADATA_USED_STATE = 3;

    /** Vendor name (DataNucleus) used for extensions. */
    public static final String VENDOR_NAME = "datanucleus";

    /** Class : read only. */
    public static final String EXTENSION_CLASS_READ_ONLY = "read-only";

    /** Class : when using multitenancy, disables its use for this class. */
    public static final String EXTENSION_CLASS_MULTITENANCY_DISABLE = "multitenancy-disable";

    /** Class : when using multitenancy, defines the column name used for the mutitenancy discriminator. */
    public static final String EXTENSION_CLASS_MULTITENANCY_COLUMN_NAME = "multitenancy-column-name";

    /** Class : when using multitenancy, defines the length of column used for the mutitenancy discriminator. */
    public static final String EXTENSION_CLASS_MULTITENANCY_COLUMN_LENGTH = "multitenancy-column-length";

    /** Class : when using multitenancy, defines the jdbc-type used for the mutitenancy discriminator column. */
    public static final String EXTENSION_CLASS_MULTITENANCY_JDBC_TYPE = "multitenancy-jdbc-type";

    /** Class : when the class will use soft deletion (deletion flag column) rather than actually deleting objects. */
    public static final String EXTENSION_CLASS_SOFTDELETE = "softdelete";

    /** Class : when the class will use soft deletion, specifies the column name to use. */
    public static final String EXTENSION_CLASS_SOFTDELETE_COLUMN_NAME = "softdelete-column-name";

    /** Class : define the name of a field that will store the version of this class. */
    public static final String EXTENSION_CLASS_VERSION_FIELD_NAME = "field-name";

    /** Class : initial value to use for this class for versioning (when using version-number strategy). */
    public static final String EXTENSION_VERSION_NUMBER_INITIAL_VALUE = "version-initial-value";

    /** Member : name of type converter to use. */
    public final static String EXTENSION_MEMBER_TYPE_CONVERTER_NAME = "type-converter-name";

    public final static String EXTENSION_MEMBER_TYPE_CONVERTER_DISABLED = "type-converter-disabled";

    /** Member : name of comparator class when of SortedSet/SortedMap type. */
    public static final String EXTENSION_MEMBER_COMPARATOR_NAME = "comparator-name";

    /** Member : implementation class names, when the member is of a interface/reference type. */
    public static final String EXTENSION_MEMBER_IMPLEMENTATION_CLASSES = "implementation-classes";

    /** Member : when field is enum, name of the method to get the "value" of the enum. */
    public static final String EXTENSION_MEMBER_ENUM_VALUE_GETTER = "enum-value-getter";

    /** Member : when field is enum, name of the method to return the enum given the value. @deprecated */
    public static final String EXTENSION_MEMBER_ENUM_GETTER_BY_VALUE = "enum-getter-by-value";

    /** Member : when the field is Calendar, signifies that it should be stored as a single column. */
    public static final String EXTENSION_MEMBER_CALENDAR_ONE_COLUMN = "calendar-one-column";

    /** Member : whether the field is insertable (for JDO). */
    public static final String EXTENSION_MEMBER_INSERTABLE = "insertable";

    /** Member : whether the field is updateable (for JDO). */
    public static final String EXTENSION_MEMBER_UPDATEABLE = "updateable";

    /** Member : whether the field is cascade-persist (for JDO). */
    public static final String EXTENSION_MEMBER_CASCADE_PERSIST = "cascade-persist";

    /** Member : whether the field is cascade-update (for JDO). */
    public static final String EXTENSION_MEMBER_CASCADE_UPDATE = "cascade-update";

    /** Member : whether the field is cascade-refresh (for JDO). */
    public static final String EXTENSION_MEMBER_CASCADE_REFRESH = "cascade-refresh";

    /** Member : whether the field is (L2) cacheable (for JPA). */
    public static final String EXTENSION_MEMBER_CACHEABLE = "cacheable";

    /** Member : whether to fetch just the FK (and not populate the related object). */
    public static final String EXTENSION_MEMBER_FETCH_FK_ONLY = "fetch-fk-only";

    /** Member : whether this member (collection/map/array) should allow null elements/keys/values. */
    public static final String EXTENSION_MEMBER_CONTAINER_ALLOW_NULLS = "allow-nulls";

    /** Member : the ordering clause to use for this List field. */
    public static final String EXTENSION_MEMBER_LIST_ORDERING = "list-ordering";

    /** Member : when this field has a value generator, only apply it when the field is not set. */
    public static final String EXTENSION_MEMBER_STRATEGY_WHEN_NOTNULL = "strategy-when-notnull";

    /** Member : shared relation, column name for relation discriminator column. */
    public static final String EXTENSION_MEMBER_RELATION_DISCRIM_COLUMN = "relation-discriminator-column";

    /** Member : shared relation, value for this class for relation discriminator column. */
    public static final String EXTENSION_MEMBER_RELATION_DISCRIM_VALUE = "relation-discriminator-value";

    /** Member : shared relation, where the relation discriminator column is part of the PK. */
    public static final String EXTENSION_MEMBER_RELATION_DISCRIM_PK = "relation-discriminator-pk";

    /** Class : definition of VIEW (when mapping to a view). */
    public static final String EXTENSION_CLASS_VIEW_DEFINITION = "view-definition";

    /** Class : definition of imports for VIEW (when mapping to a view). */
    public static final String EXTENSION_CLASS_VIEW_IMPORTS = "view-imports";

    /** State of the MetaData. */
    protected int metaDataState = METADATA_CREATED_STATE;

    /** Parent MetaData object, allowing hierarchical MetaData structure. */
    protected MetaData parent;

    /** Extensions for this MetaData element. */
    protected Map<String, String> extensions = null;

    public MetaData()
    {
    }

    /**
     * Constructor. Taking the parent MetaData object (if any).
     * @param parent The parent MetaData object.
     */
    public MetaData(MetaData parent)
    {
        this.parent = parent;
    }

    /**
     * Copy constructor. Taking the parent MetaData object, and an object to copy from.
     * @param parent The parent MetaData object.
     * @param copy The metadata to copy from
     */
    public MetaData(MetaData parent, MetaData copy)
    {
        this.parent = parent;
        if (copy != null && copy.extensions != null)
        {
            if (extensions == null)
            {
                extensions = new HashMap<>(copy.extensions);
            }
            else
            {
                extensions.clear();
                extensions.putAll(copy.extensions);
            }
        }
    }

    public void initialise(ClassLoaderResolver clr)
    {
        setInitialised();
    }

    void setInitialised()
    {
        metaDataState = METADATA_INITIALISED_STATE;
    }

    void setPopulated()
    {
        metaDataState = METADATA_POPULATED_STATE;
    }

    void setUsed()
    {
        metaDataState = METADATA_USED_STATE;
    }

    public boolean isPopulated()
    {
        return metaDataState >= METADATA_POPULATED_STATE;
    }

    public boolean isInitialised()
    {
        return metaDataState >= METADATA_INITIALISED_STATE;
    }

    public boolean isUsed()
    {
        return metaDataState == METADATA_USED_STATE;
    }

    public MetaDataManager getMetaDataManager()
    {
        return parent != null ? parent.getMetaDataManager() : null;
    }

    public void setParent(MetaData md)
    {
        if (isPopulated() || isInitialised())
        {
            throw new NucleusException("Cannot set parent of " + this + " since it is already populated/initialised");
        }
        this.parent = md;
    }

    public MetaData getParent()
    {
        return parent;
    }

    public MetaData addExtensions(Map<String, String> exts)
    {
        if (exts == null || exts.size() == 0)
        {
            return this;
        }

        if (extensions == null)
        {
            extensions = new HashMap<>(exts);
        }
        else
        {
            extensions.putAll(exts);
        }
        return this;
    }

    public MetaData setExtensions(Map<String, String> exts)
    {
        if (exts == null)
        {
            extensions = null;
        }
        else
        {
            extensions = new HashMap<>(exts);
        }
        return this;
    }

    public MetaData addExtension(String key, String value)
    {
        if (key == null || value == null)
        {
            throw new InvalidMetaDataException("044160", VENDOR_NAME, key, value);
        }

        if (hasExtension(key))
        {
            // Remove any existing value
            removeExtension(key);
        }

        if (extensions == null)
        {
            extensions = new HashMap();
        }
        extensions.put(key, value);
        return this;
    }

    public MetaData removeExtension(String key)
    {
        if (extensions == null)
        {
            return this;
        }
        extensions.remove(key);
        return this;
    }

    public int getNoOfExtensions()
    {
        return extensions != null ? extensions.size() : 0;
    }

    public Map<String, String> getExtensions()
    {
        if (extensions == null || extensions.isEmpty())
        {
            return null;
        }
        return extensions;
    }

    public boolean hasExtension(String key)
    {
        if (extensions == null || key == null)
        {
            return false;
        }
        return extensions.containsKey(key);
    }

    /**
     * Accessor for the value of a particular extension.
     * @param key The key of the extension
     * @return The value of the extension (null if not existing)
     */
    public String getValueForExtension(String key)
    {
        if (extensions == null || key == null)
        {
            return null;
        }
        return extensions.get(key);
    }

    /**
     * Accessor for the value of a particular extension, but splitting it into separate parts. 
     * This is for extension tags that have a value as comma separated.
     * @param key The key of the extension
     * @return The value(s) of the extension (null if not existing)
     */
    public String[] getValuesForExtension(String key)
    {
        if (extensions == null || key == null)
        {
            return null;
        }

        String value = extensions.get(key);
        if (value != null)
        {
            return MetaDataUtils.getInstance().getValuesForCommaSeparatedAttribute(value);
        }
        return null;
    }
}