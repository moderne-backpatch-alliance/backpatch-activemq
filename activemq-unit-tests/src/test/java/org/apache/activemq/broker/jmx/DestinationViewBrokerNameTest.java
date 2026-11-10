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
package org.apache.activemq.broker.jmx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.apache.activemq.broker.BrokerRegistry;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.RegionBroker;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DestinationViewBrokerNameTest {

    private static final String BROKER_NAME = "viewBroker";
    private static final String OTHER_NAME = "notTheBrokerName";

    private BrokerService broker;

    @Before
    public void setUp() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName(BROKER_NAME);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.start();
        broker.waitUntilStarted();
    }

    @After
    public void tearDown() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
        BrokerService other = BrokerRegistry.getInstance().lookup(OTHER_NAME);
        if (other != null) {
            other.stop();
            other.waitUntilStopped();
        }
    }

    @Test
    public void testRegionBrokerNameCannotBeChanged() throws Exception {
        RegionBroker regionBroker = (RegionBroker) broker.getRegionBroker();
        assertEquals(BROKER_NAME, regionBroker.getBrokerName());

        regionBroker.setBrokerName(OTHER_NAME);

        assertEquals(BROKER_NAME, regionBroker.getBrokerName());
    }

    // The view has to reach its broker through the VM connector URI and not through
    // a URL built from the name, so a different name must not change where a message goes.
    @Test
    public void testSendTextMessageUsesVmConnectorUri() throws Exception {
        ActiveMQQueue queue = new ActiveMQQueue("viewQueue");
        Destination destination = broker.getBroker().addDestination(broker.getAdminConnectionContext(), queue, true);

        ManagedRegionBroker managed = mock(ManagedRegionBroker.class);
        when(managed.getBrokerName()).thenReturn(OTHER_NAME);
        when(managed.getVmConnectorURI()).thenReturn(broker.getVmConnectorURI());

        DestinationView view = new DestinationView(managed, destination);

        assertNotNull(view.sendTextMessage(Collections.<String, String>emptyMap(), "body", null, null));
        assertEquals(1, destination.getDestinationStatistics().getMessages().getCount());
    }
}
