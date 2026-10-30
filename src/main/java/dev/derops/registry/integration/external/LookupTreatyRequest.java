package dev.derops.registry.integration.external;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Request payload of the (placeholder) external registry SOAP contract. */
@XmlRootElement(name = "LookupTreatyRequest", namespace = LookupTreatyRequest.NS)
@XmlAccessorType(XmlAccessType.FIELD)
public class LookupTreatyRequest {

    public static final String NS = "urn:example:treaty-registry:v1";

    @XmlElement(namespace = NS, required = true)
    private String registrationNumber;

    public LookupTreatyRequest() {}

    public LookupTreatyRequest(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }
}
