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
2004 Andy Jefferson - toString(), MetaData, javadocs
2004 Andy Jefferson - nullIndicatorColumn/Value, ownerField
    ...
**********************************************************************/
package org.datanucleus.metadata;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.datanucleus.ClassLoaderResolver;
import org.datanucleus.exceptions.ClassNotResolvedException;
import org.datanucleus.util.ClassUtils;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;

/**
 * This element specifies the mapping for an embedded type. It contains multiple field elements, 
 * one for each field in the type.
 * <P>
 * The <B>null-indicator-column</B> optionally identifies the name of the column used to indicate 
 * whether the embedded instance is null. By default, if the value of this column is null, then the
 * embedded instance is null. This column might be mapped to a field of the embedded instance but 
 * might be a synthetic column for the sole purpose of indicating a null reference.
 * The <B>null-indicator-value</B> specifies the value to indicate that the embedded instance is null. 
 * This is only used for non-nullable columns.
 * If <B>null-indicator-column</B> is omitted, then the embedded instance is assumed always to exist.
 */
public class EmbeddedMetaData extends MetaData
{
    private static final long serialVersionUID = -1180186183944475444L;

    /** Name of the field/property in the embedded object that refers to the owner (bidirectional relation). */
    protected String ownerMember;

    /** Name of a column used for determining if the embedded object is null */
    protected String nullIndicatorColumn;

    /** Value in the null column indicating that the embedded object is null */
    protected String nullIndicatorValue;

    /** Discriminator for use when embedding objects with inheritance. */
    protected DiscriminatorMetaData discriminatorMetaData;

    /** Fields/properties of the embedded object. */
    protected final List<AbstractMemberMetaData> members = new ArrayList();

    // TODO Drop this and just use "members" above
    protected AbstractMemberMetaData memberMetaData[];

    /**
     * Constructor to create a copy of the passed metadata.
     * @param embmd The metadata to copy
     */
    public EmbeddedMetaData(EmbeddedMetaData embmd)
    {
        super(null, embmd);
        this.ownerMember = embmd.ownerMember;
        this.nullIndicatorColumn = embmd.nullIndicatorColumn;
        this.nullIndicatorValue = embmd.nullIndicatorValue;
        for (int i=0;i<embmd.members.size();i++)
        {
            if (embmd.members.get(i) instanceof FieldMetaData)
            {
                addMember(new FieldMetaData(this, embmd.members.get(i)));
            }
            else
            {
                addMember(new PropertyMetaData(this, (PropertyMetaData)embmd.members.get(i)));
            }
        }
    }

    /**
     * Default constructor. Use setters to set fields, before calling populate().
     */
    public EmbeddedMetaData()
    {
    }

    /**
     * Method to populate the embedded MetaData.
     * This performs checks on the validity of the field types for embedding.
     * @param clr The class loader to use where necessary
     * @param primary the primary ClassLoader to use (or null)
     */
    public void populate(ClassLoaderResolver clr, ClassLoader primary)
    {
        // Find the class that the embedded fields apply to
        MetaDataManager mmgr = getMetaDataManager();
        AbstractMemberMetaData apmd = null; // Field that has <embedded>
        AbstractClassMetaData embCmd = null; // Definition for the embedded class
        String embeddedType = null; // Name of the embedded type

        MetaData md = getParent();
        if (md instanceof AbstractMemberMetaData)
        {
            // PC embedded in PC object
            apmd = (AbstractMemberMetaData)md;
            embeddedType = apmd.getTypeName();
            embCmd = mmgr.getMetaDataForClassInternal(apmd.getType(), clr);
            if (embCmd == null && apmd.getFieldTypes() != null && apmd.getFieldTypes().length == 1)
            {
                // The specified field is not embeddable, nor is it persistent-interface, so try field-type for embedding
                embCmd = mmgr.getMetaDataForClassInternal(clr.classForName(apmd.getFieldTypes()[0]), clr);
            }
            if (embCmd == null)
            {
                NucleusLogger.METADATA.error(Localiser.msg("044121", apmd.getFullFieldName(), apmd.getTypeName()));
                throw new InvalidMemberMetaDataException("044121", apmd.getClassName(), apmd.getName(), apmd.getTypeName());
            }
        }
        else if (md instanceof ElementMetaData)
        {
            // PC element embedded in collection
            ElementMetaData elemmd = (ElementMetaData)md;
            apmd = (AbstractMemberMetaData)elemmd.getParent();
            embeddedType = apmd.getCollection().getElementType();
            try
            {
                Class cls = clr.classForName(embeddedType, primary);
                embCmd = mmgr.getMetaDataForClassInternal(cls, clr);
            }
            catch (ClassNotResolvedException cnre)
            {
                // Should be caught by populating the Collection
            }
            if (embCmd == null)
            {
                NucleusLogger.METADATA.error(Localiser.msg("044122", apmd.getFullFieldName(), embeddedType));
                throw new InvalidMemberMetaDataException("044122", apmd.getClassName(), apmd.getName(), embeddedType);
            }
        }
        else if (md instanceof KeyMetaData)
        {
            // PC key embedded in Map
            KeyMetaData keymd = (KeyMetaData)md;
            apmd = (AbstractMemberMetaData)keymd.getParent();
            embeddedType = apmd.getMap().getKeyType();
            try
            {
                Class cls = clr.classForName(embeddedType, primary);
                embCmd = mmgr.getMetaDataForClassInternal(cls, clr);
            }
            catch (ClassNotResolvedException cnre)
            {
                // Should be caught by populating the Map
            }
            if (embCmd == null)
            {
                NucleusLogger.METADATA.error(Localiser.msg("044123", apmd.getFullFieldName(), embeddedType));
                throw new InvalidMemberMetaDataException("044123", apmd.getClassName(), apmd.getName(), embeddedType);
            }
        }
        else if (md instanceof ValueMetaData)
        {
            // PC value embedded in Map
            ValueMetaData valuemd = (ValueMetaData)md;
            apmd = (AbstractMemberMetaData)valuemd.getParent();
            embeddedType = apmd.getMap().getValueType();
            try
            {
                Class cls = clr.classForName(embeddedType, primary);
                embCmd = mmgr.getMetaDataForClassInternal(cls, clr);
            }
            catch (ClassNotResolvedException cnre)
            {
                // Should be caught by populating the Map
            }
            if (embCmd == null)
            {
                NucleusLogger.METADATA.error(Localiser.msg("044124", apmd.getFullFieldName(), embeddedType));
                throw new InvalidMemberMetaDataException("044124", apmd.getClassName(), apmd.getName(), embeddedType);
            }
        }

        // Check that all "members" are of the correct type for the embedded object
        Iterator<AbstractMemberMetaData> memberIter = members.iterator();
        while (memberIter.hasNext())
        {
            AbstractMemberMetaData mmd = memberIter.next();
            // TODO Should allow PropertyMetaData here I think
            if (embCmd instanceof InterfaceMetaData && mmd instanceof FieldMetaData)
            {
                // Cannot have a field within a persistent interface
                throw new InvalidMemberMetaDataException("044129", apmd.getClassName(), apmd.getName(), mmd.getName());
            }
        }

        Set<String> memberNames = new HashSet<>();
        for (AbstractMemberMetaData mmd : members)
        {
            memberNames.add(mmd.getName());
        }

        // Add fields for the class that aren't in the <embedded> block using Reflection.
        // TODO Consider getting rid of this ... should fall back to the ClassMetaData for the embedded class
        // NOTE 1 : We ignore fields in superclasses
        // NOTE 2 : We ignore "enhanced" fields (added by the JDO enhancer)
        // NOTE 3 : We ignore inner class fields (containing "$") 
        // NOTE 4 : We sort the fields into ascending alphabetical order
        Class embeddedClass = null;
        Collections.sort(members);
        try
        {
            // Load the embedded class
            embeddedClass = clr.classForName(embeddedType, primary);

            // TODO Cater for properties in the populating class when the user defines using setters

            // Get all (reflected) fields in the populating class
            Field[] cls_fields=embeddedClass.getDeclaredFields();
            for (int i=0;i<cls_fields.length;i++)
            {
                // Limit to fields in this class, that aren't enhanced fields that aren't inner class fields, and that aren't static
                if (cls_fields[i].getDeclaringClass().getName().equals(embeddedType) &&
                    !mmgr.isEnhancerField(cls_fields[i].getName()) &&
                    !ClassUtils.isInnerClass(cls_fields[i].getName()) && !Modifier.isStatic(cls_fields[i].getModifiers()))
                {
                    // Find if there is a AbstractMemberMetaData for this field.
                    if (!memberNames.contains(cls_fields[i].getName()))
                    {
                        // Start from the metadata of the field in the owning class if available
                        AbstractMemberMetaData embMmd = embCmd.getMetaDataForMember(cls_fields[i].getName());
                        FieldMetaData omittedFmd = null;
                        if (embMmd != null)
                        {
                            FieldPersistenceModifier fieldModifier = embMmd.getPersistenceModifier();
                            if (fieldModifier == FieldPersistenceModifier.DEFAULT)
                            {
                                // Modifier not yet set, so work it out
                                fieldModifier = embMmd.getDefaultFieldPersistenceModifier(cls_fields[i].getType(), cls_fields[i].getModifiers(), 
                                        mmgr.isFieldTypePersistable(cls_fields[i].getType()), mmgr);
                            }

                            if (fieldModifier == FieldPersistenceModifier.PERSISTENT)
                            {
                                // Only add if the owning class defines it as persistent
                                omittedFmd = new FieldMetaData(this, embMmd);
                                omittedFmd.setPrimaryKey(false); // Embedded field can't default to being part of PK, user has to set that
                            }
                        }
                        else
                        {
                            // No metadata defined, so add a default FieldMetaData for this field
                            omittedFmd = new FieldMetaData(this, cls_fields[i].getName());
                        }
                        if (omittedFmd != null)
                        {
                            NucleusLogger.METADATA.debug(Localiser.msg("044125", apmd.getClassName(), cls_fields[i].getName(), embeddedType));
                            members.add(omittedFmd);
                            memberNames.add(omittedFmd.getName());
                            Collections.sort(members);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            NucleusLogger.METADATA.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        }

        // add properties of interface, only if interface
        if (embCmd instanceof InterfaceMetaData)
        {
            try
            {
                // Get all (reflected) fields in the populating class
                Method[] clsMethods = embeddedClass.getDeclaredMethods();
                for (int i=0; i<clsMethods.length; i++)
                {
                    // Limit to methods in this class, that aren't enhanced fields
                    // that aren't inner class fields, and that aren't static
                    if (clsMethods[i].getDeclaringClass().getName().equals(embeddedType) &&
                        (clsMethods[i].getName().startsWith("get") || clsMethods[i].getName().startsWith("is")) &&
                        !clsMethods[i].isBridge() &&
                        !ClassUtils.isInnerClass(clsMethods[i].getName()))
                    {
                        // Find if there is a PropertyMetaData for this field
                        String fieldName = ClassUtils.getFieldNameForJavaBeanGetter(clsMethods[i].getName());
                        if (!memberNames.contains(fieldName))
                        {
                            // Add a default PropertyMetaData for this field.
                            NucleusLogger.METADATA.debug(Localiser.msg("044060", apmd.getClassName(), fieldName));
                            PropertyMetaData pmd=new PropertyMetaData(this, fieldName);
                            members.add(pmd);
                            memberNames.add(pmd.getName());
                            Collections.sort(members);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                NucleusLogger.METADATA.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage());
            }
        }
        Collections.sort(members);

        memberIter = members.iterator();
        while (memberIter.hasNext())
        {
            Class embFmdClass = embeddedClass;
            AbstractMemberMetaData fieldFmd = memberIter.next();
            if (!fieldFmd.fieldBelongsToClass())
            {
                try
                {
                    embFmdClass = clr.classForName(fieldFmd.getClassName(true));
                }
                catch (ClassNotResolvedException cnre)
                {
                    // Maybe the user specified just "classBasicName.fieldName", so try with package name of this
                    String fieldClsName = embeddedClass.getPackage().getName() + "." + fieldFmd.getClassName(true);
                    fieldFmd.setClassName(fieldClsName);
                    embFmdClass = clr.classForName(fieldClsName);
                }
            }
            if (fieldFmd instanceof FieldMetaData)
            {
                Field cls_field = null;
                try
                {
                    cls_field = embFmdClass.getDeclaredField(fieldFmd.getName());
                }
                catch (Exception e)
                {
                    // MetaData field doesn't exist in the class!
                    throw new InvalidMemberMetaDataException("044071", embFmdClass.getName(), fieldFmd.getName());
                }
                fieldFmd.populate(clr, cls_field, null, primary, mmgr);
            }
            else
            {
                Method cls_method = null;
                try
                {
                    cls_method = embFmdClass.getDeclaredMethod(ClassUtils.getJavaBeanGetterName(fieldFmd.getName(),true));
                }
                catch(Exception e)
                {
                    try
                    {
                        cls_method = embFmdClass.getDeclaredMethod(ClassUtils.getJavaBeanGetterName(fieldFmd.getName(),false));
                    }
                    catch (Exception e2)
                    {
                        // MetaData field doesn't exist in the class!
                        throw new InvalidMemberMetaDataException("044071", embFmdClass.getName(), fieldFmd.getName());
                    }
                }
                fieldFmd.populate(clr, null, cls_method, primary, mmgr);
            }
        }

        if (embCmd.isEmbeddedOnly())
        {
            // Check for recursive embedding of the same type and throw exception if so.
            // We do not support recursive embedding since if a 1-1 this would result in adding embedded columns infinite times, and for 1-N infinite join tables.
            for (AbstractMemberMetaData mmd : members)
            {
                if (mmd.getTypeName().equals(embCmd.getFullClassName()))
                {
                    throw new InvalidMetaDataException("044128", embCmd.getFullClassName(), mmd.getName());
                }
                else if (mmd.hasCollection() && mmd.getCollection().getElementType().equals(embCmd.getFullClassName()))
                {
                    throw new InvalidMetaDataException("044128", embCmd.getFullClassName(), mmd.getName());
                }
            }
        }
    }

    /**
     * Method to initialise the object, creating all internal convenience arrays.
     * @param clr ClassLoader resolver
     */
    public void initialise(ClassLoaderResolver clr)
    {
        memberMetaData = new AbstractMemberMetaData[members.size()];
        for (int i=0; i<memberMetaData.length; i++)
        {
            memberMetaData[i] = members.get(i);
            memberMetaData[i].initialise(clr);
        }

        if (discriminatorMetaData != null)
        {
            discriminatorMetaData.initialise(clr);
        }

        setInitialised();
    }

    /**
     * Accessor for metadata for the embedded members.
     * @return Returns the metadata for any defined members.
     */
    public final AbstractMemberMetaData[] getMemberMetaData()
    {
        return memberMetaData;
    }

    public final String getOwnerMember()
    {
        return ownerMember;
    }

    public EmbeddedMetaData setOwnerMember(String ownerMember)
    {
        this.ownerMember = StringUtils.isWhitespace(ownerMember) ? null : ownerMember;
        return this;
    }

    public final String getNullIndicatorColumn()
    {
        return nullIndicatorColumn;
    }

    public EmbeddedMetaData setNullIndicatorColumn(String column)
    {
        this.nullIndicatorColumn = StringUtils.isWhitespace(column) ? null : column;
        return this;
    }

    public final String getNullIndicatorValue()
    {
        return nullIndicatorValue;
    }

    public EmbeddedMetaData setNullIndicatorValue(String value)
    {
        this.nullIndicatorValue = StringUtils.isWhitespace(value) ? null : value;
        return this;
    }

    public final DiscriminatorMetaData getDiscriminatorMetaData()
    {
        return discriminatorMetaData;
    }

    public EmbeddedMetaData setDiscriminatorMetaData(DiscriminatorMetaData dismd)
    {
        this.discriminatorMetaData = dismd;
        this.discriminatorMetaData.parent = this;
        return this;
    }

    /**
     * Method to create a new discriminator metadata, assign it to this inheritance, and return it.
     * @return The discriminator metadata
     */
    public DiscriminatorMetaData newDiscriminatorMetadata()
    {
        DiscriminatorMetaData dismd = new DiscriminatorMetaData();
        setDiscriminatorMetaData(dismd);
        return dismd;
    }

    /**
     * Method to add a field/property to the embedded definition.
     * Rejects the addition of duplicate named fields/properties.
     * @param mmd Meta-Data for the field/property.
     */
    public void addMember(AbstractMemberMetaData mmd)
    {
        if (mmd == null)
        {
            return;
        }

        if (isInitialised())
        {
            throw new InvalidMemberMetaDataException("044108", mmd.getClassName(), mmd.getName());
        }
        Iterator<AbstractMemberMetaData> iter = members.iterator();
        while (iter.hasNext())
        {
            AbstractMemberMetaData md = iter.next();
            if (mmd.getName().equals(md.getName()))
            {
                throw new InvalidMemberMetaDataException("044112", mmd.getClassName(), mmd.getName());
            }
        }
        members.add(mmd);
        mmd.parent = this;
    }

    /**
     * Method to create a new FieldMetaData, add it, and return it.
     * @param name Name of the field
     * @return The FieldMetaData
     */
    public FieldMetaData newFieldMetaData(String name)
    {
        FieldMetaData fmd = new FieldMetaData(this, name);
        addMember(fmd);
        return fmd;
    }

    /**
     * Method to create a new PropertyMetaData, add it, and return it.
     * @param name Name of the property
     * @return The PropertyMetaData
     */
    public PropertyMetaData newPropertyMetaData(String name)
    {
        PropertyMetaData pmd = new PropertyMetaData(this, name);
        addMember(pmd);
        return pmd;
    }

    public String toString()
    {
        StringBuilder str = new StringBuilder(super.toString());
        if (memberMetaData != null)
        {
            str.append(" [" + memberMetaData.length + " members] (");
            for (int i=0;i<memberMetaData.length;i++)
            {
                if (i > 0)
                {
                    str.append(", ");
                }
                str.append(memberMetaData[i].getName());
            }
            str.append(")");
        }
        return str.toString();
    }
}