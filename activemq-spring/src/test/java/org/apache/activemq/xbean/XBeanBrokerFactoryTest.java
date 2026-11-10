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
package org.apache.activemq.xbean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.FatalBeanException;

public class XBeanBrokerFactoryTest {

    // Copies of the XBeanBrokerFactory and Utils constants, kept here so this test also
    // compiles and runs against a broker that does not have them yet.
    private static final String XBEAN_BROKER_FACTORY_PROTOCOLS_PROP =
            "org.apache.activemq.xbean.XBEAN_BROKER_FACTORY_PROTOCOLS";
    private static final String FILE_PROTOCOL = "file";
    private static final String CLASSPATH_PROTOCOL = "classpath";
    private static final String DEFAULT_ALLOWED_PROTOCOLS = FILE_PROTOCOL + "," + CLASSPATH_PROTOCOL;

    @Before
    public void setUp() throws Exception {
        // reset before each test
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, DEFAULT_ALLOWED_PROTOCOLS);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, DEFAULT_ALLOWED_PROTOCOLS);
    }

    @Test
    public void testXBeanAllowedProtocolParsing() throws Exception {
        // new instance will read the current property protocol prop and build set
        XBeanBrokerFactory factory = new XBeanBrokerFactory();
        assertEquals(setOf(FILE_PROTOCOL, CLASSPATH_PROTOCOL), getAllowedProtocols(factory));

        // set property
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "file,jar");
        factory = new XBeanBrokerFactory();
        assertEquals(setOf("jar","file"), getAllowedProtocols(factory));
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "http");
        factory = new XBeanBrokerFactory();
        assertEquals(setOf("http"), getAllowedProtocols(factory));

        // check allow all
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "*");
        factory = new XBeanBrokerFactory();
        assertNull(getAllowedProtocols(factory));

        // check allow none
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "");
        factory = new XBeanBrokerFactory();
        assertTrue(getAllowedProtocols(factory).isEmpty());

        // test empty and white space only
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "jar  , ftp,   http");
        factory = new XBeanBrokerFactory();
        assertEquals(setOf("jar","ftp", "http"), getAllowedProtocols(factory));
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "   ");
        factory = new XBeanBrokerFactory();
        assertTrue(getAllowedProtocols(factory).isEmpty());
    }


    // Test default protocols
    @Test
    public void testDefaultXBeanProtocols() throws Exception {
        // file works
        startBroker("xbean:src/test/resources/spring/xbean-test.xml");
        startBroker("xbean:file:src/test/resources/spring/xbean-test.xml");

        // classpath works
        startBroker("xbean:spring/xbean-test.xml");
        startBroker("xbean:classpath:spring/xbean-test.xml");

        // http/fttp blocked by default
        startBrokerNotAllowedError("xbean:http://bad/xbean-test.xml");
        startBrokerNotAllowedError("xbean:ftp:bad/xbean-test.xml");
        // should get illegal state exception, we are not allowed to use the jar protocol
        startBrokerNotAllowedError("xbean:jar:file:invalid.jar!/");
        // remote file is blocked
        startBrokerNotAllowedError("xbean:file://remote/xbean-test.xml");

        // custom is not a known protocol so this is detected as invalid protocol
        // and not a URI, so it fallsback and tries classpath which is allowed
        // but won't be found
        startBrokerBeanError("xbean:custom://invalid", FileNotFoundException.class);
    }

    @Test
    public void testXBeanAllowNone() throws Exception {
        // Allow nothing
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "");

        // everything is not allowed
        startBrokerNotAllowedError("xbean:http://bad/xbean-test.xml",
                "No protocols are allowed for loading resources");
        startBrokerNotAllowedError("xbean:ftp:bad/xbean-test.xml",
                "No protocols are allowed for loading resources");
        startBrokerNotAllowedError("xbean:jar:file:invalid.jar!/",
                "No protocols are allowed for loading resources");
        startBrokerNotAllowedError("xbean:src/test/resources/spring/xbean-test.xml",
                "No protocols are allowed for loading resources");
        startBrokerNotAllowedError("xbean://src/test/resources/spring/xbean-test.xml",
                "No protocols are allowed for loading resources");
        startBrokerNotAllowedError("xbean:file:src/test/resources/spring/xbean-test.xml",
                "No protocols are allowed for loading resources");
        startBrokerNotAllowedError("xbean:file://src/test/resources/spring/xbean-test.xml",
                "No protocols are allowed for loading resources");
        startBrokerNotAllowedError("xbean:classpath:src/test/resources/spring/xbean-test.xml",
                "No protocols are allowed for loading resources");
    }

    @Test
    public void testXBeanAllowAll() throws Exception {
        // set to asterisk to allow all
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "*");

        // file works
        startBroker("xbean:src/test/resources/spring/xbean-test.xml");
        startBroker("xbean:file:src/test/resources/spring/xbean-test.xml");
        // classpath works
        startBroker("xbean:spring/xbean-test.xml");
        startBroker("xbean:classpath:spring/xbean-test.xml");
        // jar, http, ftp are allowed so they just get bean errors as can't be found
        // Java 8 reports a missing jar as FileNotFoundException, Java 9+ as NoSuchFileException.
        startBrokerBeanError("xbean:jar:file:invalid.jar!/", IOException.class);
        startBrokerBeanError("xbean:ftp://invalid", UnknownHostException.class);
        startBrokerBeanError("xbean:http://invalid", UnknownHostException.class);
        // check remote file too
        startBrokerBeanError("xbean:file://invalid", UnknownHostException.class);
    }

    @Test
    public void testDefaultXBeanProtocolsCustom() throws Exception {
        // update allowed list
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, "jar,ftp,http");

        // jar is allowed, but file is invalid so we get a different error
        // Java 8 reports a missing jar as FileNotFoundException, Java 9+ as NoSuchFileException.
        startBrokerBeanError("xbean:jar:file:invalid.jar!/", IOException.class);

        // ftp/http is allowed but url is invalid we get a different error
        // because the host is unknown
        startBrokerBeanError("xbean:ftp://invalid", UnknownHostException.class);
        startBrokerBeanError("xbean:http://invalid", UnknownHostException.class);

        // file, remote file and classpath are all blocked
        startBrokerNotAllowedError("xbean:src/test/resources/spring/xbean-test.xml",
                "can't be found or the protocol is not allowed");
        startBrokerNotAllowedError("xbean://remote/spring/xbean-test.xml",
                "can't be found or the protocol is not allowed");
        startBrokerNotAllowedError("xbean:file:src/test/resources/spring/xbean-test.xml");
        startBrokerNotAllowedError("xbean:file://remote/spring/xbean-test.xml");
        startBrokerNotAllowedError("xbean:spring/xbean-test.xml",
                "can't be found or the protocol is not allowed");
        startBrokerNotAllowedError("xbean:classpath:spring/xbean-test.xml");
    }

    @Test
    public void testXBeanProtocolsOnlyFileOrClasspath() throws Exception {
        // block classpath
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, FILE_PROTOCOL);

        // Files should work fine
        startBroker("xbean:src/test/resources/spring/xbean-test.xml");
        startBroker("xbean:file:src/test/resources/spring/xbean-test.xml");

        // Classpath entries won't work
        // not a URI and classpath isn't allowed so errors out
        startBrokerNotAllowedError("xbean:spring/xbean-test.xml", "can't be found or the protocol is not allowed");
        startBrokerNotAllowedError("xbean:classpath:spring/xbean-test.xml");

        // block files, allow classpath
        System.setProperty(XBEAN_BROKER_FACTORY_PROTOCOLS_PROP, CLASSPATH_PROTOCOL);

        // Files should now break
        // This will fallback to trying classpath because file isn't allowed and it isn't
        // a qualified URI so it will just get a bean exception as won't be on the classpath
        startBrokerBeanError("xbean:src/test/resources/spring/xbean-test.xml",
                FileNotFoundException.class);
        // qualified URI will try file no matter won't and will be blocked as file is not allowed
        // so it won't fall back
        startBrokerNotAllowedError("xbean:file:src/test/resources/spring/xbean-test.xml");

        // classpath should work
        startBroker("xbean:spring/xbean-test.xml");
        startBroker("xbean:classpath:spring/xbean-test.xml");
    }

    private void startBrokerBeanError(String url, Class<? extends Exception> expected) throws Exception {
        try {
            startBroker(url);
            fail("Should have failed with an exception");
        } catch (FatalBeanException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            assertTrue(expected.isAssignableFrom(cause.getClass()));
        }
    }

    private void startBrokerNotAllowedError(String url) throws Exception {
        startBrokerNotAllowedError(url, "which is not allowed for loading URL resources");
    }

    private void startBrokerNotAllowedError(String url, String expected) throws Exception {
        try {
            startBroker(url);
            fail("Should have failed with an exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(expected));
        }
    }

    private void startBroker(String url) throws Exception {
        BrokerService broker = null;
        try {
            broker = BrokerFactory.createBroker(url);
            assertNotNull(broker);
            broker.stop();
        } finally {
            if (broker != null) {
                broker.stop();
                broker.waitUntilStopped();
            }
        }
    }

    // Reflective, so this test also compiles against a broker without getAllowedProtocols().
    @SuppressWarnings("unchecked")
    private static Set<String> getAllowedProtocols(XBeanBrokerFactory factory) throws Exception {
        Method method = XBeanBrokerFactory.class.getDeclaredMethod("getAllowedProtocols");
        method.setAccessible(true);
        return (Set<String>) method.invoke(factory);
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
