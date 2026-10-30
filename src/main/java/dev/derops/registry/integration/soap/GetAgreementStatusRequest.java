package dev.derops.registry.integration.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "GetAgreementStatusRequest", namespace = SoapNs.AGREEMENTS)
@XmlAccessorType(XmlAccessType.FIELD)
public class GetAgreementStatusRequest {

    @XmlElement(namespace = SoapNs.AGREEMENTS, required = true)
    private String registrationNumber;

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }
}
