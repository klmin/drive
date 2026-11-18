package com.thinkfree.drive.weboffice.httpstorage;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.OfficeUpdateRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OfficeConnectionSettingsTest {
    private boolean valid(OfficeUpdateRequest request) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(request).isEmpty();
        }
    }
    @Test void disabledNeedsNoCredentials() {
        assertTrue(valid(new OfficeUpdateRequest(null,null,null,null,"none")));
    }
    @Test void cloudCannotBeSelectedByCallingApiDirectly() {
        assertFalse(valid(new OfficeUpdateRequest(null,null,null,null,"cloud")));
    }
    @Test void ticketRequiresTokenAndSigningKey() {
        assertFalse(valid(new OfficeUpdateRequest("https://office.test","ticket",null,"k".repeat(32),"onPremise")));
        assertTrue(valid(new OfficeUpdateRequest("https://office.test","ticket","service-token","k".repeat(32),"onPremise")));
    }
    @Test void legacyRequiresSigningButNotTicketToken() {
        assertTrue(valid(new OfficeUpdateRequest("https://office.test","legacy",null,"k".repeat(32),"onPremise")));
        assertFalse(valid(new OfficeUpdateRequest("https://office.test","legacy",null,null,"onPremise")));
    }
    @Test void invalidAddressAndModeRejected() {
        assertFalse(valid(new OfficeUpdateRequest("javascript:alert(1)","ticket","token","k".repeat(32),"onPremise")));
        assertFalse(valid(new OfficeUpdateRequest("https://office.test",null,"token","k".repeat(32),"onPremise")));
    }

    @Test void disablingPreservesExistingConnectionForOpenDocuments() {
        var repository = org.mockito.Mockito.mock(com.thinkfree.drive.systemconfig.repository.SystemConfigRepository.class);
        var converter = org.mockito.Mockito.mock(com.thinkfree.drive.systemconfig.converter.SystemConfigConverter.class);
        var service = new com.thinkfree.drive.systemconfig.service.SystemConfigService(repository, converter, org.mockito.Mockito.mock(com.thinkfree.drive.systemconfig.service.SystemConfigFindService.class));
        service.upsertOffice(42, 7, new OfficeUpdateRequest("https://old.test","ticket","old-token","old-key","none"));
        org.mockito.Mockito.verify(repository).upsertBatch(org.mockito.ArgumentMatchers.argThat(rows ->
            rows.size() == 1 && rows.get(0).getTenantSeq() == 42
            && rows.get(0).getSettingKey().equals("office_connection_type")
            && rows.get(0).getValueText().equals("none")));

    }
    @Test void publicResponseNeverContainsSecrets() throws Exception {
        var mapper = org.mapstruct.factory.Mappers.getMapper(com.thinkfree.drive.api.v1.config.converter.ConfigResponseConverter.class);
        var data = com.thinkfree.drive.systemconfig.data.OfficeData.builder().officeConnectionType("none")
            .officeTicketServiceToken("private-ticket-token").officeHttpStorageRequestJwtSecret("private-storage-key").build();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mapper.toOfficeResponse(data));
        assertFalse(json.contains("private-"));
        assertTrue(json.contains("none"));
    }
    @Test void oversizedCredentialsAreRejectedWithoutEchoingTheirValues() {
        String secret = "private-secret-".repeat(400);
        var request = new OfficeUpdateRequest("https://office.test","ticket",secret,secret,"onPremise");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var errors = factory.getValidator().validate(request);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().allMatch(error -> Boolean.FALSE.equals(error.getInvalidValue())));
            assertFalse(request.toString().contains(secret));
        }
    }

    @Test void adapterNameAcceptsStorageUuidButRejectsPathsAndOversizedValues() {
        for(String name:java.util.List.of("2","75126ed1-4473-40ac-9fe1-3f2d80ef2474","local_adapter"))
            assertTrue(valid(new OfficeUpdateRequest("https://office.test","ticket","token","k".repeat(32),"onPremise",name)));
        for(String name:java.util.List.of("../other","a/b","a b","a".repeat(129)))
            assertFalse(valid(new OfficeUpdateRequest("https://office.test","ticket","token","k".repeat(32),"onPremise",name)));
    }
    @Test void configuredNameReplacesTenantDefaultAndMissingNameUsesTenantNumber() {
        var query=org.mockito.Mockito.mock(com.thinkfree.drive.systemconfig.repository.SystemConfigQueryRepository.class);
        var find=new com.thinkfree.drive.systemconfig.service.SystemConfigFindService(query);
        org.mockito.Mockito.when(query.findByTenantAndSettingConfigKeys(org.mockito.ArgumentMatchers.eq(42),org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(java.util.List.of());
        assertEquals("42",find.findOfficeConfig(42).getOfficeHttpStorageAdapterName());
        org.mockito.Mockito.when(query.findByTenantAndSettingConfigKeys(org.mockito.ArgumentMatchers.eq(42),org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(java.util.List.of(new com.thinkfree.drive.systemconfig.data.SystemConfigValueData("office_http_storage_adapter_name","storage-uuid")));
        assertEquals("storage-uuid",find.findOfficeConfig(42).getOfficeHttpStorageAdapterName());
    }
    @Test void storesAdapterNameAndOmittedNamePreservesCurrentSetting() {
        var repository=org.mockito.Mockito.mock(com.thinkfree.drive.systemconfig.repository.SystemConfigRepository.class);
        var find=org.mockito.Mockito.mock(com.thinkfree.drive.systemconfig.service.SystemConfigFindService.class);
        org.mockito.Mockito.when(find.findOfficeConfig(42)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder().officeHttpStorageAdapterName("existing-id").build());
        var service=new com.thinkfree.drive.systemconfig.service.SystemConfigService(repository,null,find);
        service.upsertOffice(42,7,new OfficeUpdateRequest("https://office.test","ticket","token","k".repeat(32),"onPremise","storage-id"));
        org.mockito.Mockito.verify(repository).upsertBatch(org.mockito.ArgumentMatchers.argThat(rows->rows.stream().anyMatch(r->r.getSettingKey().equals("office_http_storage_adapter_name")&&r.getValueText().equals("storage-id"))));
        service.upsertOffice(42,7,new OfficeUpdateRequest("https://office.test","ticket","token","k".repeat(32),"onPremise"));
        org.mockito.Mockito.verify(repository).upsertBatch(org.mockito.ArgumentMatchers.argThat(rows->rows.stream().anyMatch(r->r.getSettingKey().equals("office_http_storage_adapter_name")&&r.getValueText().equals("existing-id"))));
    }
}
