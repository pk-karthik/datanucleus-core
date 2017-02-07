/**********************************************************************
Copyright (c) 2014 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.datanucleus.api.ApiAdapter;
import org.datanucleus.api.ApiAdapterFactory;
import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.metadata.MetaDataListener;
import org.datanucleus.metadata.MetaDataManager;
import org.datanucleus.plugin.PluginManager;
import org.datanucleus.properties.CorePropertyValidator;
import org.datanucleus.store.types.TypeManager;
import org.datanucleus.store.types.TypeManagerImpl;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;
import org.datanucleus.util.StringUtils;

/**
 * Base implementation of a NucleusContext, providing configuration, metadata management, type management, plugin management and ClassLoader services.
 */
public abstract class AbstractNucleusContext implements NucleusContext
{
    /** Configuration for this context. */
    protected final Configuration config;

    /** Manager for plug-ins. */
    protected final PluginManager pluginManager;

    /** MetaDataManager for handling the MetaData. */
    protected MetaDataManager metaDataManager = null;

    /** API adapter used by the context. **/
    protected final ApiAdapter apiAdapter;

    /** Manager for java types. */
    protected TypeManager typeManager;

    /** Name of the class providing the ClassLoaderResolver. */
    protected final String classLoaderResolverClassName;

    /** Map of the ClassLoaderResolver, keyed by the clr class and the primaryLoader name. */
    protected transient Map<String, ClassLoaderResolver> classLoaderResolverMap = new ConcurrentHashMap<>();

    public static final Set<String> STARTUP_PROPERTIES = new HashSet<>();

    static
    {
        STARTUP_PROPERTIES.add(PropertyNames.PROPERTY_PLUGIN_REGISTRY_CLASSNAME);
        STARTUP_PROPERTIES.add(PropertyNames.PROPERTY_PLUGIN_REGISTRYBUNDLECHECK);
        STARTUP_PROPERTIES.add(PropertyNames.PROPERTY_PLUGIN_ALLOW_USER_BUNDLES);
        STARTUP_PROPERTIES.add(PropertyNames.PROPERTY_PLUGIN_VALIDATEPLUGINS);
        STARTUP_PROPERTIES.add(PropertyNames.PROPERTY_CLASSLOADER_RESOLVER_NAME);
        STARTUP_PROPERTIES.add(PropertyNames.PROPERTY_PERSISTENCE_XML_FILENAME);
        STARTUP_PROPERTIES.add(PropertyNames.PROPERTY_CLASSLOADER_PRIMARY);
        STARTUP_PROPERTIES.add(PropertyNames.PROPERTY_METADATA_LISTENER_OBJECT);
    }

    public AbstractNucleusContext(String apiName, Map startupProps, PluginManager pluginMgr)
    {
        if (pluginMgr != null)
        {
            this.pluginManager = pluginMgr;
        }
        else
        {
            this.pluginManager = PluginManager.createPluginManager(startupProps, this.getClass().getClassLoader());
        }

        // Create Configuration (with defaults for plugins), and impose any startup properties
        this.config = new Configuration(this);
        if (startupProps != null && !startupProps.isEmpty())
        {
            this.config.setPersistenceProperties(startupProps);
        }

        // Set the name of class loader resolver
        String clrName = config.getStringProperty(PropertyNames.PROPERTY_CLASSLOADER_RESOLVER_NAME);
        if (clrName != null)
        {
            classLoaderResolverClassName = pluginManager.getAttributeValueForExtension(
                "org.datanucleus.classloader_resolver", "name", clrName, "class-name");
            if (classLoaderResolverClassName == null)
            {
                // User has specified a classloader_resolver plugin that has not registered
                throw new NucleusUserException(Localiser.msg("001001", clrName)).setFatal();
            }
        }
        else
        {
            classLoaderResolverClassName = null;
        }

        // Initialise API, and set defaults for properties for the API
        if (apiName != null)
        {
            this.apiAdapter = ApiAdapterFactory.getInstance().getApiAdapter(apiName, pluginManager);
            this.config.setDefaultProperties(apiAdapter.getDefaultFactoryProperties());
        }
        else
        {
            this.apiAdapter = null;
        }
    }

    public void applyDefaultProperties(Configuration conf)
    {
        conf.addDefaultProperty(PropertyNames.PROPERTY_PLUGIN_REGISTRY_CLASSNAME, null, null, null, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_PLUGIN_ALLOW_USER_BUNDLES, null, true, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_PLUGIN_VALIDATEPLUGINS, null, false, false, false);
        conf.addDefaultProperty(PropertyNames.PROPERTY_PLUGIN_REGISTRYBUNDLECHECK, null, "EXCEPTION", CorePropertyValidator.class.getName(), false, false);
        conf.addDefaultProperty(PropertyNames.PROPERTY_CLASSLOADER_RESOLVER_NAME, null, null, null, false, false);
        conf.addDefaultProperty(PropertyNames.PROPERTY_CLASSLOADER_PRIMARY, null, null, null, false, false);

        // MetaData
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_IGNORE_METADATA_FOR_MISSING_CLASSES, null, false, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_ALWAYS_DETACHABLE, null, false, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_XML_VALIDATE, null, false, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_XML_NAMESPACE_AWARE, null, true, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_AUTOREGISTER, null, true, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_ALLOW_XML, null, true, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_ALLOW_ANNOTATIONS, null, true, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_DEFAULT_NULLABLE, null, true, true, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_ALLOW_LOAD_AT_RUNTIME, null, true, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_SUPPORT_ORM, null, null, false, false);
        conf.addDefaultProperty(PropertyNames.PROPERTY_METADATA_JDO_SUFFIX, null, "jdo", null, false, false);
        conf.addDefaultProperty(PropertyNames.PROPERTY_METADATA_ORM_SUFFIX, null, "orm", null, false, false);
        conf.addDefaultProperty(PropertyNames.PROPERTY_METADATA_JDOQUERY_SUFFIX, null, "jdoquery", null, false, false);
        conf.addDefaultProperty(PropertyNames.PROPERTY_METADATA_DEFAULT_INHERITANCE_STRATEGY, null, "JDO2", CorePropertyValidator.class.getName(), false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_EMBEDDED_PC_FLAT, null, true, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_USE_DISCRIMINATOR_FOR_SINGLE_TABLE, null, false, false, false);
        conf.addDefaultBooleanProperty(PropertyNames.PROPERTY_METADATA_USE_DISCRIMINATOR_DEFAULT_CLASS_NAME, null, true, false, false);
    }

    public synchronized void initialise()
    {
        logConfiguration();
    }

    public void close()
    {
        if (typeManager != null)
        {
            typeManager.close();
        }
    }

    public ApiAdapter getApiAdapter()
    {
        return apiAdapter;
    }

    public String getApiName()
    {
        return apiAdapter != null ? apiAdapter.getName() : null;
    }

    public Configuration getConfiguration()
    {
        return config;
    }

    public PluginManager getPluginManager()
    {
        return pluginManager;
    }

    public synchronized MetaDataManager getMetaDataManager()
    {
        if (metaDataManager == null)
        {
            String apiName = getApiName();
            try
            {
                metaDataManager = (MetaDataManager)pluginManager.createExecutableExtension("org.datanucleus.metadata_manager", new String[]{"name"}, new String[]{apiName}, 
                    "class", new Class[] {ClassConstants.NUCLEUS_CONTEXT}, new Object[]{this});
                if (config.hasProperty(PropertyNames.PROPERTY_METADATA_LISTENER_OBJECT))
                {
                    MetaDataListener mdl = (MetaDataListener)config.getProperty(PropertyNames.PROPERTY_METADATA_LISTENER_OBJECT);
                    metaDataManager.registerListener(mdl);
                }
            }
            catch (Exception e)
            {
                throw new NucleusException(Localiser.msg("008010", apiName, e.getMessage()), e);
            }
            if (metaDataManager == null)
            {
                throw new NucleusException(Localiser.msg("008009", apiName));
            }
        }
    
        return metaDataManager;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.NucleusContext#supportsORMMetaData()
     */
    @Override
    public boolean supportsORMMetaData()
    {
        return true;
    }

    public TypeManager getTypeManager()
    {
        if (typeManager == null)
        {
            this.typeManager = new TypeManagerImpl(this);
        }
        return typeManager;
    }

    public ClassLoaderResolver getClassLoaderResolver(ClassLoader primaryLoader)
    {
        // Set the key we will refer to this loader by
        String resolverName = config.getStringProperty(PropertyNames.PROPERTY_CLASSLOADER_RESOLVER_NAME);
        String key = resolverName!=null ? resolverName : "datanucleus";
        if (primaryLoader != null)
        {
            key += ":[" + StringUtils.toJVMIDString(primaryLoader) + "]"; 
        }

        if (classLoaderResolverMap == null)
        {
            classLoaderResolverMap = new HashMap<String, ClassLoaderResolver>();
        }
        ClassLoaderResolver clr = classLoaderResolverMap.get(key);
        if (clr != null)
        {
            // Return the cached loader resolver
            return clr;
        }

        // Create the ClassLoaderResolver of the required type with this primary loader
        if (resolverName == null)
        {
            clr = new ClassLoaderResolverImpl(primaryLoader);
        }
        else
        {
            try
            {
                clr = (ClassLoaderResolver)pluginManager.createExecutableExtension("org.datanucleus.classloader_resolver", "name", resolverName, 
                    "class-name", new Class[] {ClassLoader.class}, new Object[] {primaryLoader});
            }
            catch (ClassNotFoundException cnfe)
            {
                throw new NucleusUserException(Localiser.msg("001002", classLoaderResolverClassName), cnfe).setFatal();
            }
            catch (Exception e)
            {
                throw new NucleusUserException(Localiser.msg("001003", classLoaderResolverClassName), e).setFatal();
            }
        }
        clr.registerUserClassLoader((ClassLoader)config.getProperty(PropertyNames.PROPERTY_CLASSLOADER_PRIMARY));
        classLoaderResolverMap.put(key, clr);

        return clr;
    }

    /**
     * Method to log the configuration of this context.
     */
    protected void logConfiguration()
    {
        if (NucleusLogger.PERSISTENCE.isDebugEnabled())
        {
            NucleusLogger.PERSISTENCE.debug("================= NucleusContext ===============");
            String javaVersion = System.getProperty("java.version");
            if (StringUtils.isWhitespace(javaVersion))
            {
                javaVersion = "unknown";
            }
            NucleusLogger.PERSISTENCE.debug(Localiser.msg("008000", pluginManager.getVersionForBundle("org.datanucleus"), javaVersion, System.getProperty("os.name")));
            NucleusLogger.PERSISTENCE.debug("Persistence API : " + getApiName());
            if (config.hasPropertyNotNull(PropertyNames.PROPERTY_PERSISTENCE_UNIT_NAME))
            {
                NucleusLogger.PERSISTENCE.debug("Persistence-Unit : " + config.getStringProperty(PropertyNames.PROPERTY_PERSISTENCE_UNIT_NAME));
            }
            NucleusLogger.PERSISTENCE.debug("Plugin Registry : " + pluginManager.getRegistryClassName());

            Object primCL = config.getProperty(PropertyNames.PROPERTY_CLASSLOADER_PRIMARY);
            String clrName = config.getStringProperty(PropertyNames.PROPERTY_CLASSLOADER_RESOLVER_NAME);
            if (clrName == null)
            {
                clrName = "default";
            }
            NucleusLogger.PERSISTENCE.debug("ClassLoading : " + clrName + (primCL != null ? ("primary=" + primCL) : ""));

            logConfigurationDetails();

            NucleusLogger.PERSISTENCE.debug("================================================");
        }
    }

    /**
     * Convenience method so that extending implementations can log their own configuration.
     */
    protected abstract void logConfigurationDetails();
}