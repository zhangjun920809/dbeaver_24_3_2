/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.model.impl;

import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPropertyGet;
import org.apache.commons.jexl3.introspection.JexlPropertySet;
import org.apache.commons.jexl3.introspection.JexlUberspect;
import org.eclipse.core.expressions.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.Platform;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.dpi.DPIClientObject;
import org.jkiss.dbeaver.model.runtime.LoggingProgressMonitor;
import org.jkiss.utils.CommonUtils;
import org.osgi.framework.Bundle;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * EntityEditorDescriptor
 */
public abstract class AbstractDescriptor {

    private static final Log log = Log.getLog(AbstractDescriptor.class);

    public static final String VAR_OBJECT = "object";
    public static final String VAR_CONTEXT = "context";

    private static JexlEngine jexlEngine;

    private static class DPIPropertyGetter implements JexlPropertyGet {
        private final String propertyName;

        private DPIPropertyGetter(String propertyName) {
            this.propertyName = propertyName;
        }

        @Override
        public Object invoke(Object obj) throws Exception {
            if (obj instanceof DPIClientObject) {
                return ((DPIClientObject) obj).dpiPropertyValue(new LoggingProgressMonitor(log), propertyName);
            }
            return null;
        }

        @Override
        public Object tryInvoke(Object obj, Object key) {
            try {
                return invoke(obj);
            } catch (Exception e) {
                log.debug(e);
            }
            return null;
        }

        @Override
        public boolean tryFailed(Object rval) {
            return false;
        }

        @Override
        public boolean isCacheable() {
            return true;
        }
    }

    ;
    private static final JexlUberspect.PropertyResolver DPI_RESOLVER = new JexlUberspect.PropertyResolver() {
        @Override
        public JexlPropertyGet getPropertyGet(JexlUberspect uber, Object obj, Object identifier) {
            if (identifier instanceof String && obj instanceof DPIClientObject) {
                return new DPIPropertyGetter((String) identifier);
            }
            return null;
        }

        @Override
        public JexlPropertySet getPropertySet(JexlUberspect uber, Object obj, Object identifier, Object arg) {
            return null;
        }
    };
    private static List<JexlUberspect.PropertyResolver> POJO = Collections.unmodifiableList(Arrays.asList(
        JexlUberspect.JexlResolver.PROPERTY,
        JexlUberspect.JexlResolver.MAP,
        JexlUberspect.JexlResolver.LIST,
        JexlUberspect.JexlResolver.DUCK,
        JexlUberspect.JexlResolver.FIELD,
        JexlUberspect.JexlResolver.CONTAINER,
        DPI_RESOLVER
    ));

    private static final JexlUberspect.ResolverStrategy JEXL_STRATEGY = (op, obj) -> {
        if (op == JexlOperator.ARRAY_GET) {
            return JexlUberspect.MAP;
        }
        if (op == JexlOperator.ARRAY_SET) {
            return JexlUberspect.MAP;
        }
        if (op == null && obj instanceof Map) {
            return JexlUberspect.MAP;
        }
        return POJO;
    };

    public static JexlExpression parseExpression(String exprString) throws DBException {
        synchronized (AbstractDescriptor.class) {
            if (jexlEngine == null) {
                jexlEngine = new JexlBuilder()
                    .cache(100)
                    .strategy(JEXL_STRATEGY)
                    .create();
            }
        }
        try {
            return jexlEngine.createExpression(exprString);
        } catch (JexlException e) {
            throw new DBException("Bad expression", e);
        }
    }

    public static JexlContext makeContext(final Object object, final Object context) {
        return new JexlContext() {
            @Override
            public Object get(String name) {
                return name.equals(VAR_OBJECT) ? object :
                    (name.equals(VAR_CONTEXT) ? context : null); //$NON-NLS-1$
            }

            @Override
            public void set(String name, Object value) {
                log.warn("Set is not implemented"); //$NON-NLS-1$
            }

            @Override
            public boolean has(String name) {
                return name.equals(VAR_OBJECT) && object != null ||
                    name.equals(VAR_CONTEXT) && context != null;
            }
        };
    }

    public static Object evalExpression(String exprString, Object object, Object context) {
        try {
            JexlExpression expression = AbstractDescriptor.parseExpression(exprString);
            return expression.evaluate(AbstractDescriptor.makeContext(object, context));
        } catch (DBException e) {
            log.error("Bad expression: " + exprString, e);
            return null;
        }
    }

    public class ObjectType {
        private static final String ATTR_NAME = "name";
        private static final String ATTR_IF = "if";
        private static final String ATTR_FORCE_CHECK = "forceCheck";

        private final String implName;
        private Class<?> implClass;
        private JexlExpression expression;
        private boolean forceCheck;

        public ObjectType(String implName) {
            this.implName = implName;
        }

        public ObjectType(IConfigurationElement cfg) {
            this(cfg, ATTR_NAME);
        }

        public ObjectType(IConfigurationElement cfg, String typeAttr) {
            this.implName = cfg.getAttribute(typeAttr);
            String condition = cfg.getAttribute(ATTR_IF);
            if (!CommonUtils.isEmpty(condition)) {
                try {
                    this.expression = parseExpression(condition);
                } catch (DBException ex) {
                    log.warn("Can't parse object type expression: " + condition, ex); //$NON-NLS-1$
                }
            }
            String fcAttr = cfg.getAttribute(ATTR_FORCE_CHECK);
            if (!CommonUtils.isEmpty(fcAttr)) {
                forceCheck = CommonUtils.toBoolean(fcAttr);
            }
        }

        public String getImplName() {
            return implName;
        }

        public Class<?> getObjectClass() {
            return getObjectClass(Object.class);
        }

        public <T> Class<? extends T> getObjectClass(Class<T> type) {
            if (implName == null) {
                return null;
            }
            if (implClass == null) {
                implClass = AbstractDescriptor.this.getObjectClass(implName, type);
            }
            return (Class<? extends T>) implClass;
        }

        public <T> void checkObjectClass(Class<T> type)
            throws DBException {
            Class<? extends T> objectClass = getObjectClass(type);
            if (objectClass == null) {
                throw new DBException("Class '" + implName + "' not found");
            }
            if (!type.isAssignableFrom(objectClass)) {
                throw new DBException("Class '" + implName + "' do not implements '" + type.getName() + "'");
            }
        }

        public boolean appliesTo(Object object, Object context) {
            if (!matchesType(object.getClass())) {
                return false;
            }
            if (expression != null) {
                try {
                    Object result = expression.evaluate(makeContext(object, context));
                    return Boolean.TRUE.equals(result);
                } catch (Exception e) {
                    log.debug("Error evaluating EL expression '" + expression + "'", e);
                    return false;
                }
            }
            return true;
        }

        public <T> T createInstance(Class<T> type)
            throws DBException {
            if (implName == null) {
                throw new DBException("No implementation class name set for '" + type.getName() + "'");
            }
            Class<? extends T> objectClass = getObjectClass(type);
            if (objectClass == null) {
                throw new DBException("Can't load class '" + getImplName() + "'");
            }
            try {
                return objectClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                     InvocationTargetException e) {
                throw new DBException("Can't instantiate class '" + getImplName() + "'", e);
            }
        }

        public boolean matchesType(Class<?> clazz) {
            // Check class only if bundle was loaded or forceCheck is set. Otherwise we'll load ALL bundles which have some
            // data type mappings (no matter which type they refer)

            if (getContributorBundle().getState() != Bundle.ACTIVE && !forceCheck) {
                // Use only type name
                return getTypeInfoCache(clazz).containsKey(implName);
            }
            getObjectClass();
            return implClass != null && implClass.isAssignableFrom(clazz);
        }

        @Override
        public String toString() {
            return implName;
        }
    }

    private static Map<String, Map<String, Boolean>> classInfoCache = new HashMap<>();

    private static synchronized Map<String, Boolean> getTypeInfoCache(Class<?> clazz) {
        Map<String, Boolean> intCache = classInfoCache.get(clazz.getName());
        if (intCache != null) {
            return intCache;
        }
        intCache = new HashMap<>();
        classInfoCache.put(clazz.getName(), intCache);
        for (Class<?> sc = clazz; sc != null && sc != Object.class; sc = sc.getSuperclass()) {
            collectInterface(sc, intCache);
        }

        return intCache;
    }

    private static void collectInterface(Class<?> clazz, Map<String, Boolean> intCache) {
        intCache.put(clazz.getName(), Boolean.TRUE);
        for (Class<?> i : clazz.getInterfaces()) {
            collectInterface(i, intCache);
        }
    }


    private String pluginId;
    private Bundle originBundle;

    protected AbstractDescriptor(IConfigurationElement contributorConfig) {
        this.pluginId = contributorConfig.getContributor().getName();
    }

    protected AbstractDescriptor(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginId() {
        return pluginId;
    }

    public Bundle getContributorBundle() {
        if (originBundle == null) {
            originBundle = Platform.getBundle(pluginId);
        }
        return originBundle;
    }

    protected void replaceContributor(IContributor contributor) {
        this.pluginId = contributor.getName();
        this.originBundle = null;
    }

    @NotNull
    protected DBPImage iconToImage(String icon, @NotNull DBPImage defIcon) {
        DBPImage result = iconToImage(icon);
        if (result == null) {
            return defIcon;
        } else {
            return result;
        }
    }

    @Nullable
    public DBPImage iconToImage(String icon) {
        if (CommonUtils.isEmpty(icon)) {
            return null;
        } else if (icon.startsWith("#")) {
            // Predefined image
            return DBIcon.getImageById(icon.substring(1));
        } else {
            if (!icon.startsWith("platform:")) {
                icon = "platform:/plugin/" + pluginId + "/" + icon;
            }
            return new DBIcon(icon);
        }
    }

    public Class<?> getObjectClass(@NotNull String className) {
        return getObjectClass(className, null);
    }

    public <T> Class<T> getObjectClass(@NotNull String className, Class<T> type) {
        return getObjectClass(getContributorBundle(), className, type);
    }

    protected boolean isExpressionTrue(Expression expression, Object exprContext) {
        if (expression != null) {
            try {
                IEvaluationContext context = new EvaluationContext(null, exprContext);
                EvaluationResult result = expression.evaluate(context);
                if (result != EvaluationResult.TRUE) {
                    return false;
                }
            } catch (CoreException e) {
                log.debug(e);
                return false;
            }
        }
        return true;
    }

    public static <T> Class<T> getObjectClass(@NotNull Bundle fromBundle, @NotNull String className, Class<T> type) {
        Class<?> objectClass;
        try {
            objectClass = fromBundle.loadClass(className);
        } catch (Throwable ex) {
            log.error("Can't determine object class '" + className + "'", ex);
            return null;
        }

        if (type != null && !type.isAssignableFrom(objectClass)) {
            log.error("Object class '" + className + "' doesn't match requested type '" + type.getName() + "'");
            return null;
        }
        return (Class<T>) objectClass;
    }

    protected static Expression getEnablementExpression(IConfigurationElement config) {
        IConfigurationElement[] elements = config.getChildren("enabledWhen");
        if (elements.length > 0) {
            try {
                IConfigurationElement[] enablement = elements[0].getChildren();
                if (enablement.length > 0) {
                    return ExpressionConverter.getDefault().perform(enablement[0]);
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }
        return null;
    }

}