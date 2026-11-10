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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

public class MarshallingSupportTest {

    /**
     * Test method for
     * {@link org.apache.activemq.util.MarshallingSupport#propertiesToString(java.util.Properties)}.
     *
     * @throws Exception
     */
    @Test
    public void testPropertiesToString() throws Exception {
        Properties props = new Properties();
        for (int i = 0; i < 10; i++) {
            String key = "key" + i;
            String value = "value" + i;
            props.put(key, value);
        }
        String str = MarshallingSupport.propertiesToString(props);
        Properties props2 = MarshallingSupport.stringToProperties(str);
        assertEquals(props, props2);
    }

    @Test
    public void testUnmarshalPrimitiveMap() throws Exception {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(bytesOut);

        Map<String, Object> testMap = new HashMap<>();
        testMap.put("key1", "value1");
        // nested map with nested collection, should require depth of 2 being allowed
        Map<String, Object> nested = new HashMap<>();
        nested.put("string", "string");
        nested.put("nested", Arrays.<Object>asList("one"));
        testMap.put("key2", nested);

        MarshallingSupport.marshalPrimitiveMap(testMap, dataOut);
        DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(bytesOut.toByteArray()));

        //allowed
        assertEquals(2, unmarshalPrimitiveMap(dataIn, 100, 1024, 10).size());

        // too many properties
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveMap(dataIn, 1, 1024, 10),
                "map is larger than the allowed size");

        // buffers too large
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveMap(dataIn, 100, 4, 10),
                "Max buffer size: 4 exceeded, size: 6");

        // max depth violated
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveMap(dataIn, 100, 1024, 1),
                "Max unmarshaling depth: 1 exceeded, depth: 2");
    }

    @Test
    public void testUnmarshalPrimitiveList() throws Exception {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(bytesOut);

        List<Object> testList = new ArrayList<>();
        testList.add("value");
        // nested list with nested collection, should require depth of 2 being allowed
        testList.add(Arrays.asList(Arrays.asList("one")));
        Map<String, Object> nested = new HashMap<>();
        nested.put("one", "two");
        nested.put("three", "four");
        testList.add(nested);

        MarshallingSupport.marshalPrimitiveList(testList, dataOut);
        DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(bytesOut.toByteArray()));

        //allowed
        assertEquals(3, unmarshalPrimitiveList(dataIn, 100, 1024, 2).size());

        // Max property size applies to maps, so this will check the nested map in the list
        // and should fail because the map has 2 properties
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveList(dataIn, 1, 1024, 2),
                "map is larger than the allowed size");

        // buffers too large
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveList(dataIn, 100, 2, 2),
                "Max buffer size: 2 exceeded, size: 3");

        // max depth violated
        dataIn.reset();
        assertException(() -> unmarshalPrimitiveList(dataIn, 100, 1024, 1),
                "Max unmarshaling depth: 1 exceeded, depth: 2");
    }

    // Reflection, so this test also compiles and fails properly without the limits
    @SuppressWarnings("unchecked")
    private static Map<String, Object> unmarshalPrimitiveMap(DataInputStream in, int maxPropertySize, int maxBufferSize, int maxDepth) throws Exception {
        return (Map<String, Object>) invokeLimited("unmarshalPrimitiveMap", in, maxPropertySize, maxBufferSize, maxDepth);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> unmarshalPrimitiveList(DataInputStream in, int maxPropertySize, int maxBufferSize, int maxDepth) throws Exception {
        return (List<Object>) invokeLimited("unmarshalPrimitiveList", in, maxPropertySize, maxBufferSize, maxDepth);
    }

    private static Object invokeLimited(String name, DataInputStream in, int maxPropertySize, int maxBufferSize, int maxDepth) throws Exception {
        Method method = MarshallingSupport.class.getMethod(name, DataInputStream.class, int.class, int.class, int.class);
        try {
            return method.invoke(null, in, maxPropertySize, maxBufferSize, maxDepth);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    private void assertException(ThrowableRunnable<Exception> runnable, String error) throws Exception {
        try {
            // Trigger unmarshaling the properties. This should throw an error because
            // the buffer is too large
            runnable.run();
            fail("should have IO exception");
        } catch (IOException e) {
            System.out.println(e.getMessage());
            assertTrue(e.getMessage().contains(error));
        }
    }

    private interface ThrowableRunnable<T extends Throwable> {
        void run() throws T;
    }
}
