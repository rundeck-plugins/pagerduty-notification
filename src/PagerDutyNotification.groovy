import com.dtolabs.rundeck.plugins.notification.NotificationPlugin;
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants;
import com.dtolabs.rundeck.core.plugins.configuration.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode

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

class DEFAULTS {
    static String PAGERDUTY_URL = "https://events.pagerduty.com/generic/2010-04-15/create_event.json"
    static String SUBJECT_LINE='${job.status} [${job.project}] \"${job.name}\" run by ${job.user} (#${job.execid})'
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

/**
 * Trigger a pager duty incident.
 * @param executionData
 * @param configuration
 */
def triggerEvent(Map executionData, Map configuration) {
    System.err.println("DEBUG: service_key="+configuration.service_key)
    def expandedSubject = subjectString(configuration.subject, [execution:executionData])
    def job_data = [
            event_type: 'trigger',
            service_key: configuration.service_key,
            description: expandedSubject,
            details:[job: executionData.job.name,
                    group: executionData.job.group,
                    description: executionData.job.description,
                    project: executionData.job.project,
                    user: executionData.user,
                    status: executionData.status,
            ]
    ]

    // Send the request.
    def url = new URL(DEFAULTS.PAGERDUTY_URL)
    def connection = url.openConnection()
    connection.setRequestMethod("POST")
    connection.addRequestProperty("Content-type", "application/json")
    connection.doOutput = true
    def writer = new OutputStreamWriter(connection.outputStream)
    def json = new ObjectMapper()
    writer.write(json.writeValueAsString(job_data))
    writer.flush()
    writer.close()
    connection.connect()

    // process the response.
    def response = connection.content.text
    System.err.println("DEBUG: response: "+response)
    JsonNode jsnode= json.readTree(response)
    def status = jsnode.get("status").asText()
    if (! "success".equals(status)) {
        System.err.println("ERROR: PagerDutyNotification plugin status: " + status)
    }
}


rundeckPlugin(NotificationPlugin){
    title="PagerDuty"
    description="Create a Trigger event."
    configuration{
        subject title:"Subject", description:"Incident subject line. Can contain \${job.status}, \${job.project}, \${job.name}, \${job.group}, \${job.user}, \${job.execid}", defaultValue:DEFAULTS.SUBJECT_LINE,required:true

        service_key title:"Service API Key", description:"The service key", scope:"Project"
    }
    onstart { Map executionData,Map configuration ->
        triggerEvent(executionData, configuration)
        true
    }
    onfailure { Map executionData, Map configuration ->
        triggerEvent(executionData, configuration)
        // return success.
        true
    }
    onsuccess {
        triggerEvent(executionData, configuration)
        true
    }

}
