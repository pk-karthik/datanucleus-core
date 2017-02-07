/**********************************************************************
Copyright (c) 2008 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.store.schema;

import java.util.List;

/**
 * Interface representing schema information for the datastore.
 * Extends the basic StoreSchemaData (set of properties) to allow a List of child elements (bidirectional).
 */
public interface ListStoreSchemaData extends StoreSchemaData
{
    /**
     * Method to set the parentage of a component.
     * @param parent Parent component
     */
    public void setParent(StoreSchemaData parent);

    /**
     * Accessor for the parent store metadata component for this (if any).
     * @return Parent component
     */
    public StoreSchemaData getParent();

    /**
     * Method to define a child component for this component.
     * @param child The component
     */
    public void addChild(StoreSchemaData child);

    /**
     * Method to remove all children.
     */
    public void clearChildren();

    /**
     * Accessor for a List of child metadata components (if stored in a List).
     * @return Child components.
     */
    public List<StoreSchemaData> getChildren();

    /**
     * Accessor for a child store metadata component at a position (if stored in a List).
     * @param position Position of the child component (first = 0)
     * @return The child component.
     */
    public StoreSchemaData getChild(int position);

    /**
     * Accessor for the number of child metadata components.
     * @return Number of child components.
     */
    public int getNumberOfChildren();
}