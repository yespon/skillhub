package com.iflytek.skillhub.auth.sourceid;

import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceIdOsdsOrganizationClientTest {

    @Test
    void resolveSignServerAuthHeaderValue_buildsDynamicSignatureWithExpectedFormat() throws Exception {
        SourceIdOsdsProperties properties = new SourceIdOsdsProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://osds.example.internal");
        properties.setSysid("skillhub-app");
        properties.setAccessKeySecret("test-secret");

        SourceIdOsdsOrganizationClient client = new SourceIdOsdsOrganizationClient(properties);

        Method resolver = SourceIdOsdsOrganizationClient.class
                .getDeclaredMethod("resolveSignServerAuthHeaderValue");
        resolver.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<String> signatureOptional = (Optional<String>) resolver.invoke(client);

        assertThat(signatureOptional).isPresent();
        String signature = signatureOptional.orElseThrow();
        String[] parts = signature.split("\\|");

        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo("skillhub-app");
        assertThat(parts[1]).matches("\\d{13}");
        assertThat(parts[2]).matches("[A-F0-9]{32}");
    }

    @Test
    void resolveSignServerAuthHeaderValue_fallsBackToStaticHeaderWhenSecretMissing() throws Exception {
        SourceIdOsdsProperties properties = new SourceIdOsdsProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://osds.example.internal");
        properties.setSysid("skillhub-app");
        properties.setSignServerAuth("fixed-sign");

        SourceIdOsdsOrganizationClient client = new SourceIdOsdsOrganizationClient(properties);

        Method resolver = SourceIdOsdsOrganizationClient.class
                .getDeclaredMethod("resolveSignServerAuthHeaderValue");
        resolver.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<String> signatureOptional = (Optional<String>) resolver.invoke(client);

        assertThat(signatureOptional).contains("fixed-sign");
    }
}
