/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public class FactoryFinder<T> {

    /**
     * The strategy that the FactoryFinder uses to find load and instantiate Objects
     * can be changed out by calling the
     * {@link org.apache.activemq.util.FactoryFinder#setObjectFactory(org.apache.activemq.util.FactoryFinder.ObjectFactory)}
     * method with a custom implementation of ObjectFactory.
     *
     * The default ObjectFactory is typically changed out when running in a specialized container
     * environment where service discovery needs to be done via the container system.  For example,
     * in an OSGi scenario.
     */
    public interface ObjectFactory {
        /**
         * @param path the full service path
         * @return
         */
        public Object create(String path) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException;

    }

    public interface ValidatingObjectFactory extends ObjectFactory {
        <T> T create(String path, Class<T> requiredType, Set<Class<? extends T>> allowedImpls)
                throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException;
    }

    /**
     * The default implementation of Object factory which works well in standalone applications.
     */
    protected static class StandaloneObjectFactory implements ValidatingObjectFactory {
        final ConcurrentMap<String, Class<?>> classMap = new ConcurrentHashMap<String, Class<?>>();

        @Override
        public Object create(final String path)
                throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException {
            throw new UnsupportedOperationException("Create is not supported without requiredType and allowed impls");
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T create(String path, Class<T> requiredType, Set<Class<? extends T>> allowedImpls) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
            Class<?> clazz = classMap.get(path);
            if (clazz == null) {
                clazz = loadClass(loadProperties(path));
                validateClass(clazz, requiredType, allowedImpls);
                classMap.put(path, clazz);
            } else {
                // Validate cached classes too: all finders share this factory, and a path can be used with another type.
                validateClass(clazz, requiredType, allowedImpls);
            }
            return (T) clazz.newInstance();
        }

        static public Class<?> loadClass(Properties properties) throws ClassNotFoundException, IOException {
            return FactoryFinder.loadClass(properties.getProperty("class"));
        }

        static public Properties loadProperties(String uri) throws IOException {
            // lets try the thread context class loader first
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = StandaloneObjectFactory.class.getClassLoader();
            }
            InputStream in = classLoader.getResourceAsStream(uri);
            if (in == null) {
                in = FactoryFinder.class.getClassLoader().getResourceAsStream(uri);
                if (in == null) {
                    throw new IOException("Could not find factory class for resource: " + uri);
                }
            }

            // lets load the file
            BufferedInputStream reader = null;
            try {
                reader = new BufferedInputStream(in);
                Properties properties = new Properties();
                properties.load(reader);
                return properties;
            } finally {
                try {
                    reader.close();
                } catch (Exception e) {
                }
            }
        }
    }

    // ================================================================
    // Class methods and properties
    // ================================================================
    private static ObjectFactory objectFactory = new StandaloneObjectFactory();

    public static ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public static void setObjectFactory(ObjectFactory objectFactory) {
        FactoryFinder.objectFactory = objectFactory;
    }

    // ================================================================
    // Instance methods and properties
    // ================================================================
    private final String path;
    private final Class<T> requiredType;
    private final Set<Class<? extends T>> allowedImpls;

    @SuppressWarnings("unchecked")
    public FactoryFinder(String path) {
        this(path, (Class<T>) Object.class, null);
    }

    public FactoryFinder(String path, Class<T> requiredType, String allowedImpls) {
        this.path = Objects.requireNonNull(path);
        this.requiredType = Objects.requireNonNull(requiredType);
        this.allowedImpls = loadAllowedImpls(requiredType, allowedImpls);
    }

    @SuppressWarnings("unchecked")
    private static <T> Set<Class<? extends T>> loadAllowedImpls(Class<T> requiredType, String allowedImpls) {
        if (allowedImpls == null || allowedImpls.equals("*")) {
            return null;
        }
        // An empty string gives an empty set, which allows none; null and * allow all.
        final Set<Class<? extends T>> impls = new HashSet<Class<? extends T>>();
        for (String s : allowedImpls.split("\\s*,\\s*")) {
            if (s.isEmpty()) {
                continue;
            }
            try {
                final Class<?> clazz = FactoryFinder.loadClass(s);
                if (!requiredType.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException(
                            "Class " + clazz + " is not assignable to " + requiredType);
                }
                impls.add((Class<? extends T>) clazz);
            } catch (ClassNotFoundException | IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return Collections.unmodifiableSet(impls);
    }

    /**
     * Creates a new instance of the given key
     *
     * @param key is the key to add to the path to find a text file containing
     *                the factory name
     * @return a newly created instance
     */
    @SuppressWarnings("unchecked")
    public T newInstance(String key) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException {
        final ObjectFactory factory = objectFactory;
        if (factory instanceof ValidatingObjectFactory) {
            return ((ValidatingObjectFactory) factory).create(resolvePath(key), requiredType, allowedImpls);
        }
        return (T) factory.create(resolvePath(key));
    }

    Set<Class<? extends T>> getAllowedImpls() {
        return allowedImpls;
    }

    Class<T> getRequiredType() {
        return requiredType;
    }

    private String resolvePath(final String key) throws InstantiationException {
        final Path resolvedPath = Paths.get(path).resolve(key).normalize();

        if (!resolvedPath.startsWith(path)) {
            throw new InstantiationException("Provided key escapes the FactoryFinder configured directory");
        }

        return resolvedPath.toString();
    }

    public static String buildAllowedImpls(Class<?>...classes) {
        final StringBuilder impls = new StringBuilder();
        for (Class<?> clazz : Objects.requireNonNull(classes, "List of allowed classes may not be null")) {
            if (impls.length() > 0) {
                impls.append(',');
            }
            impls.append(clazz.getName());
        }
        return impls.toString();
    }

    public static <T> void validateClass(Class<?> clazz, Class<T> requiredType,
            Set<Class<? extends T>> allowedImpls) throws InstantiationException {
        if (allowedImpls != null && !allowedImpls.contains(clazz)) {
            throw new InstantiationException("Class " + clazz + " is not an allowed implementation "
                    + "of type " + requiredType);
        }
        if (!requiredType.isAssignableFrom(clazz)) {
            throw new InstantiationException("Class " + clazz + " is not assignable to " + requiredType);
        }
    }

    static Class<?> loadClass(String className) throws ClassNotFoundException, IOException {
        if (className == null) {
            throw new IOException("Expected property is missing: class");
        }
        Class<?> clazz = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            try {
                clazz = loader.loadClass(className);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        }
        if (clazz == null) {
            clazz = FactoryFinder.class.getClassLoader().loadClass(className);
        }

        return clazz;
    }

}
