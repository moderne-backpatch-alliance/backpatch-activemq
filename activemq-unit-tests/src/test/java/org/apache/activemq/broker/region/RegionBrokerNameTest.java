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
package org.apache.activemq.broker.region;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Method;
import java.util.Collections;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.DestinationView;
import org.apache.activemq.broker.jmx.ManagedRegionBroker;
import org.apache.activemq.command.ActiveMQQueue;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RegionBrokerNameTest {

    private BrokerService brokerService;

    @Before
    public void setUp() throws Exception {
        brokerService = new BrokerService();
        brokerService.setBrokerName("region-broker-name-test");
        brokerService.setPersistent(false);
        brokerService.setUseJmx(false);
        brokerService.start();
        brokerService.waitUntilStarted();
    }

    @After
    public void tearDown() throws Exception {
        brokerService.stop();
        brokerService.waitUntilStopped();
    }

    @Test
    public void testBrokerNameComesFromBrokerService() throws Exception {
        RegionBroker regionBroker = (RegionBroker) brokerService.getRegionBroker();
        assertEquals(brokerService.getBrokerName(), regionBroker.getBrokerName());
    }

    @Test
    public void testBrokerNameCannotBeChanged() {
        for (Method method : RegionBroker.class.getMethods()) {
            assertFalse("RegionBroker must not expose a setter for its name", "setBrokerName".equals(method.getName()));
        }
    }

    @Test
    public void testSendTextMessageUsesVmConnectorUri() throws Exception {
        Mockery context = new Mockery();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        final ManagedRegionBroker broker = context.mock(ManagedRegionBroker.class);
        final Destination destination = context.mock(Destination.class);
        context.checking(new Expectations() {{
            allowing(broker).getBrokerName();
            will(returnValue(brokerService.getBrokerName()));
            oneOf(broker).getVmConnectorURI();
            will(returnValue(brokerService.getVmConnectorURI()));
            allowing(destination).getActiveMQDestination();
            will(returnValue(new ActiveMQQueue("region.broker.name.test")));
        }});

        new DestinationView(broker, destination).sendTextMessage(Collections.<String, String>emptyMap(), "body", null, null);

        context.assertIsSatisfied();
    }
}
