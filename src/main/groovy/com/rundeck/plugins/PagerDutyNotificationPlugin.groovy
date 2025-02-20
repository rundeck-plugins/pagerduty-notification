package com.rundeck.plugins

import com.dtolabs.rundeck.core.plugins.Plugin
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty
import com.dtolabs.rundeck.plugins.descriptions.SelectValues;
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
@PluginDescription(title="PagerDuty Notification", description="Legacy PagerDuty Notification via Events API Plugin")
public class PagerDutyNotificationPlugin implements NotificationPlugin {

    final static String PAGERDUTY_URL = "https://events.pagerduty.com"
    final static String SUBJECT_LINE = '${job.status} [${job.project}] \"${job.name}\" run by ${job.user} (#${job.execid}) [ ${job.href} ]'
    final static String PD_API_VER = "v2"
    final static String PD_KEY = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

    @PluginProperty(title = "Subject", description = "Incident subject line", required = false, defaultValue=PagerDutyNotificationPlugin.SUBJECT_LINE)
    private String subject;

    @PluginProperty(title = "API Version", description = "PagerDuty API Version: v1 or v2", required = true, defaultValue=PagerDutyNotificationPlugin.PD_API_VER)
    private String version;

    @PluginProperty(title = "Integration Key", description = "PagerDuty Service Integration Key", required = true, defaultValue=PagerDutyNotificationPlugin.PD_KEY)
    private String service_key;

    @PluginProperty(title = "Proxy host", description = "Outbound prox", required = false, scope=PropertyScope.Project)
    private String proxy_host;

    @PluginProperty(title = "Proxy port", description = "Outbound proxy port", required = false, scope=PropertyScope.Project)
    private String proxy_port;


    @Override
    public boolean postNotification(String trigger, Map executionData, Map config) {
        triggerEvent(trigger, executionData, config)
        true
    }

    /**
     * Trigger a pager duty incident.
     * @param executionData
     * @param configuration
     */
    def triggerEvent(String trigger, Map executionData, Map configuration) {
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

        if(version=="v1"){
            apiV1(trigger,apiService, executionData)

        }else{
            apiV2(trigger,apiService, executionData)
        }
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


    def apiV1(String trigger,PagerDutyApi apiService, Map executionData){
        def expandedSubject = subjectString(subject.empty==false?subject:SUBJECT_LINE, [execution:executionData])
        def job_data = [
                event_type: 'trigger',
                service_key: service_key,
                description: expandedSubject,
                details:[
                        job: executionData.job.name,
                        group: executionData.job.group,
                        description: executionData.job.description,
                        project: executionData.job.project,
                        user: executionData.user,
                        status: executionData.status,
                        link: executionData.href,
                        trigger: trigger
                ]
        ]

        Response<PagerResponse> response = apiService.sendEvent(job_data).execute()
        if(response.errorBody()!=null){
            println "Error body:" + response.errorBody().string()
        }else{
            println("DEBUG: response: "+response)
        }
    }


    def apiV2(String trigger,PagerDutyApi apiService, Map executionData){
        def expandedSubject = subjectString(subject, [execution:executionData])

        def severity
        if (trigger=="start" || trigger=="success"){
            severity="info"
        }else if(trigger=="failure" ){
            severity="error"
        }else{
            severity="warning"
        }

        def date
        if (trigger=="start" || trigger=="avgduration"){
            date = executionData.dateStartedW3c
        }else{
            date = executionData.dateEndedW3c
        }

        def job_data = [
            event_action: 'trigger',
            routing_key: service_key,
            dedup_key: executionData.id+"-"+trigger,
            payload: [
                    summary: expandedSubject,
                    source: "Project " + executionData.project,
                    severity: severity,
                    timestamp: date,
                    group: executionData.job.name,
                    custom_details:[job: executionData.job.name,
                             group: executionData.job.group,
                             description: executionData.job.description,
                             project: executionData.job.project,
                             user: executionData.user,
                             status: executionData.status,
                             trigger: trigger
                    ]
            ],
            links:[
                    [href: executionData.href, text: "Execution Link"],
                    [href: executionData.job.href, text: "Job Link"],
            ]

        ]

        Response<PagerResponse> response = apiService.sendEventV2(job_data).execute()
        if(response.errorBody()!=null){
            println "Error body:" + response.errorBody().string()
        }else{
            println("DEBUG: response: "+response)
        }
    }
}
