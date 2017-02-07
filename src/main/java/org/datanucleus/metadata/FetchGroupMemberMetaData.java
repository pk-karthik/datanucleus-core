/**********************************************************************
Copyright (c) 2013 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.metadata;

import org.datanucleus.util.StringUtils;

/**
 * Metadata for a member of a fetch group
 */
public class FetchGroupMemberMetaData extends MetaData
{
    private static final long serialVersionUID = 548676970076554443L;

    String name;

    int recursionDepth = 1;

    boolean isProperty = false;

    public FetchGroupMemberMetaData(MetaData parent, String name)
    {
        super(parent);
        this.name = name;
    }

    public void setProperty()
    {
        this.isProperty = true;
    }

    public boolean isProperty()
    {
        return isProperty;
    }

    public String getName()
    {
        return name;
    }

    public int getRecursionDepth()
    {
        return recursionDepth;
    }

    public void setRecursionDepth(int depth)
    {
        this.recursionDepth = depth;
    }

    public void setRecursionDepth(String depth)
    {
        if (!StringUtils.isWhitespace(depth))
        {
            try
            {
                this.recursionDepth = Integer.parseInt(depth);
            }
            catch (NumberFormatException nfe) 
            {
            }
        }
    }
}