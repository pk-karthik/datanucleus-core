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
2004 Andy Jefferson - added toString(), MetaData docs, javadocs.
2004 Andy Jefferson - added unique, indexed
    ...
**********************************************************************/
package org.datanucleus.metadata;

import org.datanucleus.ClassLoaderResolver;

/**
 * This element specifies the mapping for the element component of arrays and collections.
 * If only one column is mapped, and no additional information is needed for the column, 
 * then the column attribute can be used. Otherwise, the column element(s) are used.
 * The serialised attribute specifies that the key values are to be serialised into the named column.
 * The foreign-key attribute specifies the name of a foreign key to be generated.
 */
public class ElementMetaData extends AbstractElementMetaData
{
    private static final long serialVersionUID = 512052075696338985L;

    /**
     * Constructor to create a copy of the passed metadata using the provided parent.
     * @param emd The metadata to copy
     */
    public ElementMetaData(ElementMetaData emd)
    {
        super(emd);
    }

    /**
     * Default constructor. Set the fields using setters, before populate().
     */
    public ElementMetaData()
    {
    }

    /**
     * Populate the MetaData.
     * @param clr Class loader to use
     * @param primary the primary ClassLoader to use (or null)
     */
    public void populate(ClassLoaderResolver clr, ClassLoader primary)
    {
        // Populate the element metadata
        AbstractMemberMetaData mmd = (AbstractMemberMetaData)parent;
        if (mmd.hasCollection())
        {
            if (hasExtension(MetaData.EXTENSION_MEMBER_TYPE_CONVERTER_NAME))
            {
                if (mmd.getCollection().element.embedded == null)
                {
                    // Default to embedded since the converter process requires it
                    mmd.getCollection().element.embedded = Boolean.TRUE;
                }
            }
            mmd.getCollection().element.populate(mmd.getAbstractClassMetaData().getPackageName(), clr, primary);
        }
        else if (mmd.hasArray())
        {
            if (hasExtension(MetaData.EXTENSION_MEMBER_TYPE_CONVERTER_NAME))
            {
                if (mmd.getArray().element.embedded == null)
                {
                    // Default to embedded since the converter process requires it
                    mmd.getArray().element.embedded = Boolean.TRUE;
                }
            }
            mmd.getArray().element.populate(mmd.getAbstractClassMetaData().getPackageName(), clr, primary);
        }

        // TODO Remove this since we should only have <embedded> when the user defines it
        if (embeddedMetaData == null && 
            mmd.hasCollection() && mmd.getCollection().isEmbeddedElement() &&
            mmd.getJoinMetaData() != null && mmd.getCollection().elementIsPersistent())
        {
            // User has specified that the element is embedded in a join table but not how we embed it
            // so add a dummy definition
            embeddedMetaData = new EmbeddedMetaData();
            embeddedMetaData.parent = this;
        }

        super.populate(clr, primary);
    }
}