package org.wildfly.swarm.ts.javaee.jsf;

import javax.enterprise.inject.Model;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

@Model
public class Hello {
    public String hello() {
        return "Hello from JSF";
    }
}
