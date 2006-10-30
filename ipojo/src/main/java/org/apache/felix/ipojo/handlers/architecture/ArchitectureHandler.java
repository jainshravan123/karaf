/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.ipojo.handlers.architecture;

import java.util.Dictionary;
import java.util.Properties;

import org.apache.felix.ipojo.ComponentManagerImpl;
import org.apache.felix.ipojo.Handler;
import org.apache.felix.ipojo.architecture.Architecture;
import org.apache.felix.ipojo.architecture.ComponentDescription;
import org.apache.felix.ipojo.architecture.DependencyDescription;
import org.apache.felix.ipojo.architecture.DependencyHandlerDescription;
import org.apache.felix.ipojo.architecture.HandlerDescription;
import org.apache.felix.ipojo.architecture.ProvidedServiceDescription;
import org.apache.felix.ipojo.architecture.ProvidedServiceHandlerDescription;
import org.apache.felix.ipojo.handlers.dependency.Dependency;
import org.apache.felix.ipojo.handlers.dependency.DependencyHandler;
import org.apache.felix.ipojo.handlers.dependency.DependencyMetadata;
import org.apache.felix.ipojo.handlers.providedservice.Property;
import org.apache.felix.ipojo.handlers.providedservice.PropertyMetadata;
import org.apache.felix.ipojo.handlers.providedservice.ProvidedService;
import org.apache.felix.ipojo.handlers.providedservice.ProvidedServiceHandler;
import org.apache.felix.ipojo.handlers.providedservice.ProvidedServiceMetadata;
import org.apache.felix.ipojo.metadata.Element;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

/**
 * Achtiecture Handler : do reflection on your component.
 * @author <a href="mailto:felix-dev@incubator.apache.org">Felix Project Team</a>
 */
public class ArchitectureHandler extends Handler implements Architecture {

    /**
     * Component Manager.
     */
    private ComponentManagerImpl m_manager;

    /**
     * Service Registration of the Architecture service provided by this handler.
     */
    private ServiceRegistration m_sr;

    /**
     * Name of the component.
     */
    private String m_name;

    /**
     * Component Type.
     */
    private String m_className;

    /**
     * @see org.apache.felix.ipojo.Handler#configure(org.apache.felix.ipojo.ComponentManagerImpl, org.apache.felix.ipojo.metadata.Element)
     */
    public void configure(ComponentManagerImpl cm, Element metadata, Dictionary configuration) {
        if (metadata.containsAttribute("architecture")) {
            String isArchitectureEnabled = (metadata.getAttribute("architecture")).toLowerCase();
            if (isArchitectureEnabled.equals("true")) { cm.register(this); }
        }

        m_className = metadata.getAttribute("className");

        m_name = (String) configuration.get("name");

        m_manager = cm;
    }

    /**
     * @see org.apache.felix.ipojo.Handler#stop()
     */
    public void stop() {
        try {
            if (m_sr != null) { m_sr.unregister(); }
        } catch (Exception e) { return; }
    }

    /**
     * @see org.apache.felix.ipojo.Handler#start()
     */
    public void start() {
        // Unregister the service if already registred
        if (m_sr != null) { m_sr.unregister(); }

        // Register the ManagedService
        BundleContext bc = m_manager.getContext();
        Dictionary properties = new Properties();
        properties.put("Component Implementation Class", m_manager.getComponentMetatada().getClassName());
        properties.put(Constants.SERVICE_PID, m_name);

        m_sr = bc.registerService(Architecture.class.getName(), this, properties);

    }

    /**
     * @see org.apache.felix.ipojo.architecture.Architecture#getComponentDescription()
     */
    public ComponentDescription getComponentDescription() {
        int componentState = m_manager.getState();
        ComponentDescription componentDescription = new ComponentDescription(m_className, m_name, componentState, m_manager.getContext().getBundle().getBundleId());

        String[] instances = new String[m_manager.getInstances().length];
        for (int i = 0; i < m_manager.getInstances().length; i++) {
            instances[i] = m_manager.getInstances()[i].toString();
        }
        componentDescription.setInstances(instances);

        Handler[] handlers = m_manager.getRegistredHandlers();
        for (int i = 0; i < handlers.length; i++) {
            if (handlers[i] instanceof DependencyHandler) {
                DependencyHandler dh = (DependencyHandler) handlers[i];
                DependencyHandlerDescription dhd = new DependencyHandlerDescription(dh.isValid());

                for (int j = 0; j < dh.getDependencies().length; j++) {
                    Dependency dep = dh.getDependencies()[j];
                    DependencyMetadata dm = dep.getMetadata();
                    // Create & add the dependency description
                    DependencyDescription dd = new DependencyDescription(dm.getServiceSpecification(), dm.isMultiple(), dm.isOptional(), dm.getFilter(), dep.getState(), componentDescription);
                    dd.setUsedServices(dep.getUsedServices());
                    dhd.addDependency(dd);
                }
                componentDescription.addHandler(dhd);
                break;
            }

            if (handlers[i] instanceof ProvidedServiceHandler) {
                ProvidedServiceHandler psh = (ProvidedServiceHandler) handlers[i];
                ProvidedServiceHandlerDescription pshd = new ProvidedServiceHandlerDescription(psh.isValid());

                for (int j = 0; j < psh.getProvidedService().length; j++) {
                    ProvidedService ps = psh.getProvidedService()[j];
                    ProvidedServiceMetadata psm = ps.getMetadata();
                    ProvidedServiceDescription psd = new ProvidedServiceDescription(psm.getServiceSpecification(), ps.getState(), ps.getServiceReference(), componentDescription);

                    Properties props = new Properties();
                    for (int k = 0; k < ps.getProperties().length; k++) {
                        Property prop = ps.getProperties()[k];
                        PropertyMetadata pm = prop.getMetadata();
                        if (prop.getValue() != null) { props.put(pm.getName(), prop.getValue().toString()); }
                    }
                    psd.setProperty(props);
                    pshd.addProvidedService(psd);
                }
                componentDescription.addHandler(pshd);
                break;
            }
            // Else add a generic handler to the description
            componentDescription.addHandler(new HandlerDescription(handlers[i].getClass().getName(), handlers[i].isValid()));
        }
        return componentDescription;
    }

}
