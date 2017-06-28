package com.rundeck.plugins

import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.notification.NotificationPlugin
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


/**
 * Created by luistoledo on 6/28/17.
 */
@Plugin(service="Notification", name="PagerDutyNotification")
@PluginDescription(title="PagerDuty", description="Create a Trigger event.")
public class PagerDutyNotificationPlugin implements NotificationPlugin {

    final static String PAGERDUTY_URL = "https://events.pagerduty.com"
    final static String SUBJECT_LINE='${job.status} [${job.project}] \"${job.name}\" run by ${job.user} (#${job.execid}) [${job.href}]'

    @PluginProperty(title = "subject", description = "Incident subject line", required = true, defaultValue=PagerDutyNotificationPlugin.SUBJECT_LINE)
    private String subject;

    @PluginProperty(title = "Service API Key", description = "Service API Key", required = true, scope=PropertyScope.Project)
    private String service_key;

    @PluginProperty(title = "Proxy host", description = "Outbound prox", required = false, scope=PropertyScope.Project)
    private String proxy_host;

    @PluginProperty(title = "Proxy port", description = "Outbound proxy port", required = false, scope=PropertyScope.Project)
    private String proxy_port;



    // See http://rundeck.org/docs/developer/notification-plugin-development.html

    // curl -H "Content-type: application/json" -X POST \
    //    -d '{
    //      "service_key": "ee59049e89dd45f28ce35467a08577cb",
    //      "event_type": "trigger",
    //      "description": "FAILURE for production/HTTP on machine srv01.acme.com",
    //      "details": {
    //        "ping time": "1500ms",
    //        "load avg": 0.75
    //      }
    //    }' \
    //    "https://events.pagerduty.com/generic/2010-04-15/create_event.json"

    @Override
    public boolean postNotification(String trigger, Map executionData, Map config) {
        triggerEvent(executionData, config)
        true
    }

    /**
     * Trigger a pager duty incident.
     * @param executionData
     * @param configuration
     */
    def triggerEvent(Map executionData, Map configuration) {
        System.err.println("DEBUG: service_key="+service_key)
        def expandedSubject = subjectString(subject, [execution:executionData])
        def job_data = [
                event_type: 'trigger',
                service_key: service_key,
                description: expandedSubject,
                details:[job: executionData.job.name,
                         group: executionData.job.group,
                         description: executionData.job.description,
                         project: executionData.job.project,
                         user: executionData.user,
                         status: executionData.status,
                ]
        ]
        if (proxy_host != null && proxy_port != null) {
            System.err.println("DEBUG: proxy_host="+proxy_host)
            System.err.println("DEBUG: proxy_port="+proxy_port)
            System.getProperties().put("proxySet", "true")
            System.getProperties().put("proxyHost", proxy_host)
            System.getProperties().put("proxyPort", proxy_port)
        }

         Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(PAGERDUTY_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        PagerDutyApi apiService = retrofit.create(PagerDutyApi.class)

        Response<PagerResponse> response = apiService.sendEvent(job_data).execute()

        println("DEBUG: response: "+response)
        println "Status:" + response.body().status
        println "message:" + response.body().message
        println "errors:" + response.body().errors

    }


/**
 * Expands the Subject string using a predefined set of tokens
 */
    def subjectString(text,binding) {
        //defines the set of tokens usable in the subject configuration property
        def tokens=[
                '${job.status}': binding.execution.status.toUpperCase(),
                '${job.project}': binding.execution.job.project,
                '${job.name}': binding.execution.job.name,
                '${job.group}': binding.execution.job.group,
                '${job.user}': binding.execution.user,
                '${job.href}': binding.execution.href,
                '${job.execid}': binding.execution.id.toString()
        ]
        text.replaceAll(/(\$\{\S+?\})/){
            if(tokens[it[1]]){
                tokens[it[1]]
            } else {
                it[0]
            }
        }
    }


}
