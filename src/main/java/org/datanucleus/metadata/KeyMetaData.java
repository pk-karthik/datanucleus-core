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
import org.datanucleus.exceptions.ClassNotResolvedException;
import org.datanucleus.exceptions.NucleusUserException;

/**
 * This element specifies the mapping for the key component of maps.
 * The serialised attribute specifies that the key values are to be serialised into the named column.
 */
public class KeyMetaData extends AbstractElementMetaData
{
    private static final long serialVersionUID = -3379637846354140692L;

    /**
     * Constructor to create a copy of the passed metadata using the provided parent.
     * @param kmd The metadata to copy
     */
    public KeyMetaData(KeyMetaData kmd)
    {
        super(kmd);
    }

    /**
     * Default constructor. Set the fields using setters, before populate().
     */
    public KeyMetaData()
    {
    }

    /**
     * Populate the MetaData.
     * @param clr Class loader to use
     * @param primary the primary ClassLoader to use (or null)
     */
    public void populate(ClassLoaderResolver clr, ClassLoader primary)
    {
        AbstractMemberMetaData mmd = (AbstractMemberMetaData)parent;
        if (mmd.getMap() == null)
        {
            // TODO Use InvalidMemberMetaDataException
            throw new NucleusUserException("The field "+mmd.getFullFieldName()+" is defined with <key>, however no <map> definition was found.").setFatal();
        }

        // Populate the key metadata
        if (hasExtension(MetaData.EXTENSION_MEMBER_TYPE_CONVERTER_NAME))
        {
            if (mmd.getMap().key.embedded == null)
            {
                // Default to embedded since the converter process requires it
                mmd.getMap().key.embedded = Boolean.TRUE;
            }
        }
        mmd.getMap().key.populate(mmd.getAbstractClassMetaData().getPackageName(), clr, primary);

        // Make sure key type is set and is valid
        String keyType = mmd.getMap().getKeyType();
        Class keyTypeClass = null;
        try
        {
            keyTypeClass = clr.classForName(keyType, primary);
        }
        catch (ClassNotResolvedException cnre)
        {
            throw new InvalidMemberMetaDataException("044147", mmd.getClassName(), mmd.getName(), keyType);
        }
        if (embeddedMetaData != null && (keyTypeClass.isInterface() || keyTypeClass.getName().equals("java.lang.Object")))
        {
            throw new InvalidMemberMetaDataException("044152", mmd.getClassName(), mmd.getName(), keyTypeClass.getName());
        }

        // TODO Remove this since we should only have <embedded> when the user defines it
        // TODO This will not work currently since MapMetaData is populated *after* KeyMetaData and so the
        // keyClassMetaData is not yet populated. What we should do is provide a postPopulate() method here
        // that MapMetaData can call when it is populated
        if (embeddedMetaData == null && mmd.hasMap() && mmd.getMap().isEmbeddedKey() &&
            mmd.getJoinMetaData() != null && mmd.getMap().keyIsPersistent())
        {
            // User has specified that the key is embedded in a join table but not how we embed it
            // so add a dummy definition
            embeddedMetaData = new EmbeddedMetaData();
            embeddedMetaData.parent = this;
        }        

        super.populate(clr, primary);
    }
}