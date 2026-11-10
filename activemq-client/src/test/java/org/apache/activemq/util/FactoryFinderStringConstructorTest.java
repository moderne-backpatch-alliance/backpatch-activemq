/*
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.activemq.transport.tcp.TcpTransportFactory;
import org.junit.Test;

public class FactoryFinderStringConstructorTest {

    private static final String PATH = "META-INF/services/org/apache/activemq/transport/";

    @Test
    public void testLoadsFromPath() throws Exception {
        assertTrue(new FactoryFinder<Object>(PATH).newInstance("tcp") instanceof TcpTransportFactory);
    }

    @Test
    public void testKeyWithSeparatorRefused() throws Exception {
        try {
            new FactoryFinder<Object>(PATH).newInstance("../../transport/tcp");
            fail("should have failed instantiation");
        } catch (InstantiationException e) {
            assertEquals("Provided key may not contain path separators", e.getMessage());
        }
    }
}
