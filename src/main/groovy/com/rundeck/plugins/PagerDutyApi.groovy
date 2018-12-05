package com.rundeck.plugins

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Created by luistoledo on 6/28/17.
 */
interface PagerDutyApi {
    @POST("generic/2010-04-15/create_event.json")
    Call<PagerResponse> sendEvent(@Body LinkedHashMap json);

    @POST("v2/enqueue")
    Call<PagerResponse> sendEventV2(@Body LinkedHashMap json);
}