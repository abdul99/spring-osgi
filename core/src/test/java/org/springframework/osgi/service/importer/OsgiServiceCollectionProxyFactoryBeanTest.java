/*
 * Copyright 2006-2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.osgi.service.importer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import junit.framework.TestCase;

import org.easymock.MockControl;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.springframework.osgi.TestUtils;
import org.springframework.osgi.mock.MockBundleContext;
import org.springframework.osgi.mock.MockServiceReference;
import org.springframework.osgi.service.ServiceUnavailableException;
import org.springframework.osgi.service.importer.support.Availability;
import org.springframework.osgi.service.importer.support.MemberType;
import org.springframework.osgi.service.importer.support.OsgiServiceCollectionProxyFactoryBean;
import org.springframework.osgi.util.OsgiFilterUtils;

/**
 * @author Costin Leau
 * 
 */
public class OsgiServiceCollectionProxyFactoryBeanTest extends TestCase {

	private OsgiServiceCollectionProxyFactoryBean serviceFactoryBean;

	private MockBundleContext bundleContext;

	private ServiceReference ref;

	protected void setUp() throws Exception {
		super.setUp();
		this.serviceFactoryBean = new OsgiServiceCollectionProxyFactoryBean();
		// this.serviceFactoryBean.setApplicationContext(new
		// GenericApplicationContext());

		ref = new MockServiceReference(new String[] { Runnable.class.getName() });

		bundleContext = new MockBundleContext() {

			private final String filter_Serializable = OsgiFilterUtils.unifyFilter(Runnable.class, null);

			public ServiceReference[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
				if (this.filter_Serializable.equalsIgnoreCase(filter))
					return new ServiceReference[] { ref };

				return new ServiceReference[0];
			}
		};

		serviceFactoryBean.setBundleContext(this.bundleContext);
		serviceFactoryBean.setBeanClassLoader(getClass().getClassLoader());
		serviceFactoryBean.setInterfaces(new Class<?>[] { TestCase.class });

	}

	protected void tearDown() {
		serviceFactoryBean = null;
	}

	public void testListenersSetOnCollection() throws Exception {
		serviceFactoryBean.setAvailability(Availability.OPTIONAL);

		OsgiServiceLifecycleListener[] listeners =
				{ (OsgiServiceLifecycleListener) MockControl.createControl(OsgiServiceLifecycleListener.class)
						.getMock() };
		serviceFactoryBean.setListeners(listeners);
		serviceFactoryBean.afterPropertiesSet();

		serviceFactoryBean.getObject();
		Object exposedProxy = TestUtils.getFieldValue(serviceFactoryBean, "exposedProxy");
		assertSame(listeners, TestUtils.getFieldValue(exposedProxy, "listeners"));
	}

	public void tstMandatoryServiceAtStartupFailure() throws Exception {
		serviceFactoryBean.setAvailability(Availability.MANDATORY);

		try {
			serviceFactoryBean.afterPropertiesSet();
			Collection col = (Collection) serviceFactoryBean.getObject();
			col.size();
			fail("should have thrown exception");
		} catch (ServiceUnavailableException ex) {
			// expected
		}
	}

	public void testMandatoryServiceAvailableAtStartup() {
		serviceFactoryBean.setInterfaces(new Class<?>[] { Runnable.class });
		serviceFactoryBean.afterPropertiesSet();

		assertNotNull(serviceFactoryBean.getObject());
	}

	public void testMandatoryServiceUnAvailableWhileWorking() {
		serviceFactoryBean.setInterfaces(new Class<?>[] { Runnable.class });
		serviceFactoryBean.afterPropertiesSet();

		Collection col = (Collection) serviceFactoryBean.getObject();

		assertFalse(col.isEmpty());
		Set listeners = bundleContext.getServiceListeners();

		ServiceListener list = (ServiceListener) listeners.iterator().next();
		// disable filter
		list.serviceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING, ref));
		col.isEmpty();
	}

	public void testServiceReferenceMemberType() throws Exception {
		serviceFactoryBean.setMemberType(MemberType.SERVICE_REFERENCE);
		serviceFactoryBean.setInterfaces(new Class<?>[] { Runnable.class });
		serviceFactoryBean.afterPropertiesSet();

		Collection col = (Collection) serviceFactoryBean.getObject();

		assertFalse(col.isEmpty());
		assertSame(ref, col.iterator().next());

		Set listeners = bundleContext.getServiceListeners();
		ServiceListener list = (ServiceListener) listeners.iterator().next();
		ServiceReference ref2 = new MockServiceReference();
		list.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, ref2));

		assertEquals(2, col.size());
		Iterator iter = col.iterator();
		iter.next();
		assertSame(ref2, iter.next());
	}
}