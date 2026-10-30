package dev.derops.registry.integration.external;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "LookupTreatyResponse", namespace = LookupTreatyRequest.NS)
@XmlAccessorType(XmlAccessType.FIELD)
public class LookupTreatyResponse {

    @XmlElement(namespace = LookupTreatyRequest.NS, required = true)
    private boolean found;

    @XmlElement(namespace = LookupTreatyRequest.NS)
    private String externalRef;

    @XmlElement(namespace = LookupTreatyRequest.NS)
    private String status;

    @XmlElement(namespace = LookupTreatyRequest.NS)
    private String registeredOn;

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRegisteredOn() {
        return registeredOn;
    }

    public void setRegisteredOn(String registeredOn) {
        this.registeredOn = registeredOn;
    }
}
