/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.jmx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import javax.management.ObjectName;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.BrokerViewMBean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JmxDeniedSchemeNCTest {

    private static final String BROKER_NAME = "jmx-denied-scheme-broker";

    private BrokerService broker;
    private BrokerViewMBean proxy;

    @Before
    public void setUp() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName(BROKER_NAME);
        broker.setUseJmx(true);
        broker.setPersistent(false);
        broker.start();
        broker.waitUntilStarted();

        ObjectName brokerObjectName = new ObjectName("org.apache.activemq:type=Broker,brokerName=" + BROKER_NAME);
        proxy = (BrokerViewMBean) broker.getManagementContext().newProxyInstance(brokerObjectName,
                BrokerViewMBean.class, true);
    }

    @After
    public void tearDown() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    public void testHttpDiscoveryRefused() throws Exception {
        assertRefused("http", "http://localhost");
        assertRefused("http", "static:(http://localhost)");
    }

    @Test
    public void testOtherSchemesRefused() throws Exception {
        for (String scheme : new String[] { "failover", "fanout", "discovery", "mock", "peer", "proxy", "reliable",
                "simple", "udp", "zeroconf", "multicast" }) {
            assertRefused(scheme, scheme + "://localhost");
        }
    }

    private void assertRefused(String scheme, String uri) throws Exception {
        try {
            proxy.addNetworkConnector(uri);
            fail("Should have refused " + uri);
        } catch (IllegalArgumentException e) {
            assertEquals("Transport scheme '" + scheme + "' is not allowed", e.getMessage());
        }
    }
}
