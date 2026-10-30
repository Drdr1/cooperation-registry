package dev.derops.registry.integration.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(name = "GetAgreementStatusResponse", namespace = SoapNs.AGREEMENTS)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"registrationNumber", "title", "status", "signedOn", "validUntil", "openItems", "overdueItems"})
public class GetAgreementStatusResponse {

    @XmlElement(namespace = SoapNs.AGREEMENTS, required = true)
    private String registrationNumber;

    @XmlElement(namespace = SoapNs.AGREEMENTS, required = true)
    private String title;

    @XmlElement(namespace = SoapNs.AGREEMENTS, required = true)
    private String status;

    /** ISO date (xs:date), omitted when unknown. */
    @XmlElement(namespace = SoapNs.AGREEMENTS)
    private String signedOn;

    @XmlElement(namespace = SoapNs.AGREEMENTS)
    private String validUntil;

    @XmlElement(namespace = SoapNs.AGREEMENTS, required = true)
    private int openItems;

    @XmlElement(namespace = SoapNs.AGREEMENTS, required = true)
    private int overdueItems;

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSignedOn() {
        return signedOn;
    }

    public void setSignedOn(String signedOn) {
        this.signedOn = signedOn;
    }

    public String getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(String validUntil) {
        this.validUntil = validUntil;
    }

    public int getOpenItems() {
        return openItems;
    }

    public void setOpenItems(int openItems) {
        this.openItems = openItems;
    }

    public int getOverdueItems() {
        return overdueItems;
    }

    public void setOverdueItems(int overdueItems) {
        this.overdueItems = overdueItems;
    }
}
