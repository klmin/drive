package com.thinkfree.drive.weboffice.httpstorage;
import com.thinkfree.drive.api.v1.admin.systemconfig.usecase.AdminSystemConfigUseCase;
import com.thinkfree.drive.api.v1.admin.systemconfig.converter.SystemConfigResponseConverter;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.OfficeResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.request.OfficeUpdateRequest;
import com.thinkfree.drive.systemconfig.service.*;
import com.thinkfree.drive.global.security.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class ManagedOfficeSettingsTest {
 @Mock SystemConfigFindService find;
 @Mock SystemConfigService settings;
 @Mock SystemConfigResponseConverter converter;
 @Mock com.thinkfree.drive.tenant.policy.TenantPolicy policy;
 @InjectMocks AdminSystemConfigUseCase usecase;
 @BeforeEach void tenant(){TenantContext.set(42);}
 @AfterEach void clear(){TenantContext.clear();}
 @Test void managedCredentialsCannotBeReadByTenantAdministrator(){
  when(find.isOfficeServiceManaged(42)).thenReturn(true);
  when(policy.allows(42, com.thinkfree.drive.tenant.policy.TenantFeature.OFFICE_INFO_VIEW)).thenReturn(true);
  when(converter.toOfficeResponse(null)).thenReturn(new OfficeResponse("onPremise","https://office.test","ticket","private-token","42","private-key"));
  var response=usecase.getOfficeConfig();
  assertTrue(response.serviceManaged());assertEquals("",response.officeTicketServiceToken());assertEquals("",response.officeHttpStorageRequestJwtSecret());
 }
 @Test void managedSettingsCannotBeChangedByTenantAdministrator(){
  when(find.isOfficeServiceManaged(42)).thenReturn(true);
  assertThrows(com.thinkfree.drive.global.exception.ApiRuntimeException.class,()->usecase.putOffice(new OfficeUpdateRequest(null,null,null,null,"none"),3));
  verifyNoInteractions(settings);
 }
 @Test void independentlyManagedOnPremiseSettingsRemainEditable(){
  var request=new OfficeUpdateRequest(null,null,null,null,"none");
  usecase.putOffice(request,3);verify(settings).upsertOffice(42,3,request);
 }
}
