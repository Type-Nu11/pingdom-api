package com.typenull.pingdom.moderation.api.place.lookup;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.DisplayStatus;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapQueryService;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminPlaceMapControllerTest {
    private final AdminPlaceMapQueryService service = mock(AdminPlaceMapQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminPlaceMapController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class))).build();
    }

    private MockHttpServletRequestBuilder request() {
        return get("/admin/places/map").param("west","128.5").param("south","35.8")
                .param("east","128.7").param("north","35.95").param("zoom","13")
                .param("width","1200").param("height","800");
    }

    @Test
    void bindsMapParametersAndReturnsNoStoreEmptyResponse() throws Exception {
        when(service.find(any())).thenReturn(new AdminPlaceMapResponse("SPATIAL",13,List.of(),List.of(),List.of(),0,true,null,null,100,DisplayStatus.MARKERS));
        mvc.perform(request().param("discoveryStatus","HIDDEN").param("category","CAFE"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.mode").value("SPATIAL")).andExpect(jsonPath("$.regions").isArray())
                .andExpect(jsonPath("$.clusters").isArray()).andExpect(jsonPath("$.markers").isArray())
                .andExpect(jsonPath("$.viewportPlaceCount").value(0)).andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.displayLimit").value(100)).andExpect(jsonPath("$.displayStatus").value("MARKERS"));
        verify(service).find(argThat(r -> r.zoom()==13 && r.width()==1200 && r.category().equals("CAFE")
                && r.discoveryStatus().name().equals("HIDDEN")));
    }

    @Test
    void returnsAllIndividualMarkersWithExistingMinimalPlaceFields() throws Exception {
        var markers = List.of(
                new AdminPlaceMapResponse.Marker(10, "첫 번째 카페", 35.87, 128.6, "CAFE", "VISIBLE", "OPERATING"),
                new AdminPlaceMapResponse.Marker(20, "두 번째 카페", 35.87, 128.6, "CAFE", "HIDDEN", "PERMANENTLY_CLOSED"));
        when(service.find(any())).thenReturn(new AdminPlaceMapResponse("SPATIAL", 13, List.of(), List.of(), markers,
                2, true, null, null, 100, DisplayStatus.MARKERS));
        mvc.perform(request()).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayStatus").value("MARKERS"))
                .andExpect(jsonPath("$.viewportPlaceCount").value(2))
                .andExpect(jsonPath("$.clusters").isEmpty())
                .andExpect(jsonPath("$.markers.length()").value(2))
                .andExpect(jsonPath("$.markers[0].id").value(10))
                .andExpect(jsonPath("$.markers[0].name").value("첫 번째 카페"))
                .andExpect(jsonPath("$.markers[0].latitude").value(35.87))
                .andExpect(jsonPath("$.markers[0].longitude").value(128.6))
                .andExpect(jsonPath("$.markers[0].category").value("CAFE"))
                .andExpect(jsonPath("$.markers[1].id").value(20))
                .andExpect(jsonPath("$.markers[1].discoveryStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.markers[1].operatingStatus").value("PERMANENTLY_CLOSED"));
    }

    @Test
    void excessPlacesReturnHttp200AndZoomHintInsteadOfError() throws Exception {
        when(service.find(any())).thenReturn(new AdminPlaceMapResponse("SPATIAL",13,List.of(),List.of(),List.of(),
                101,false,"RESULT_BUDGET_EXCEEDED","ZOOM_IN",100,DisplayStatus.ZOOM_REQUIRED));
        mvc.perform(request().param("limit", "1000").param("page", "2").param("sortParam", "LATEST"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.viewportPlaceCount").value(101))
                .andExpect(jsonPath("$.markers").isEmpty()).andExpect(jsonPath("$.regions").isEmpty())
                .andExpect(jsonPath("$.clusters").isEmpty()).andExpect(jsonPath("$.displayLimit").value(100))
                .andExpect(jsonPath("$.displayStatus").value("ZOOM_REQUIRED"))
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.incompleteReason").value("RESULT_BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.recoveryAction").value("ZOOM_IN"));
    }

    @Test
    void timeoutRemainsServiceUnavailableWithExistingErrorShape() throws Exception {
        when(service.find(any())).thenThrow(new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_QUERY_TIMEOUT));
        mvc.perform(request()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ADMIN_PLACE_MAP_QUERY_TIMEOUT"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void missingRequiredInputAndInvalidEnumsAreBadRequests() throws Exception {
        mvc.perform(get("/admin/places/map")).andExpect(status().isBadRequest());
        mvc.perform(request().param("discoveryStatus","UNKNOWN")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnboundedSizeBeforeServiceInvocation() throws Exception {
        mvc.perform(get("/admin/places/map").param("west","128.5").param("south","35.8")
                .param("east","128.7").param("north","35.95").param("zoom","13")
                .param("width","999999").param("height","800"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void expiredGroupUsesExistingGlobalErrorShape() throws Exception {
        when(service.groupPlaces("expired",1,20)).thenThrow(new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_CLUSTER_INVALID));
        mvc.perform(get("/admin/places/map/clusters/expired/places"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_PLACE_MAP_CLUSTER_INVALID"))
                .andExpect(jsonPath("$.message").isString());
    }
}
