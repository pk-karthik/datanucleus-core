/**********************************************************************
Copyright (c) 2012 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.store.schema.table;

import java.util.List;
import java.util.Set;

import org.datanucleus.metadata.AbstractClassMetaData;
import org.datanucleus.metadata.AbstractMemberMetaData;
import org.datanucleus.store.StoreManager;

/**
 * Representation of a table for a class.
 */
public interface Table
{
    StoreManager getStoreManager();

    String getName();

    String getSchemaName();

    String getCatalogName();

    /**
     * Accessor for the primary class metadata for this table.
     * @return Class metadata
     */
    AbstractClassMetaData getClassMetaData();

    /**
     * Accessor for number of columns.
     * @return Number of cols
     */
    int getNumberOfColumns();

    List<Column> getColumns();

    /**
     * Accessor for the column at the specified position (numbered from 0 to numcols-1).
     * @param pos Position of the column
     * @return The column at this position (or null if invalid position)
     */
    Column getColumnForPosition(int pos);

    /**
     * Accessor for the surrogate column of the specified type.
     * @param colType The type of surrogate column
     * @return The column definition, if it exists for this table
     */
    Column getSurrogateColumn(SurrogateColumnType colType);

    /**
     * Accessor for the column with the supplied name (if present).
     * @param name name of the column (the identifier)
     * @return The column (or null if none present with that name)
     */
    Column getColumnForName(String name);

    /**
     * Method to return the member-column mapping for the specified member.
     * @param mmd Metadata for the member
     * @return The member-column mapping
     */
    MemberColumnMapping getMemberColumnMappingForMember(AbstractMemberMetaData mmd);

    /**
     * Method to return the member-column mapping for the specified embedded member.
     * @param mmds Metadata for the member(s) to navigate to the required member
     * @return The member-column mapping
     */
    MemberColumnMapping getMemberColumnMappingForEmbeddedMember(List<AbstractMemberMetaData> mmds);

    Set<MemberColumnMapping> getMemberColumnMappings();
}