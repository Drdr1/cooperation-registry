package dev.derops.registry.integration.soap;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/** SOAP endpoint at /ws, WSDL at /ws/agreements.wsdl (generated from the XSD). */
@EnableWs
@Configuration
public class WebServiceConfig {

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "agreements")
    public DefaultWsdl11Definition agreementsWsdl(XsdSchema agreementsSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("AgreementsPort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace(SoapNs.AGREEMENTS);
        definition.setSchema(agreementsSchema);
        return definition;
    }

    @Bean
    public XsdSchema agreementsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("wsdl/agreements.xsd"));
    }
}
