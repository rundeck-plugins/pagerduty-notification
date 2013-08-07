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

def pagerduty_url = "https://events.pagerduty.com/generic/2010-04-15/create_event.json"

/**
 * define the default subject line configuration
 */
def defaultSubjectLine='$STATUS [$PROJECT] $JOB run by $USER (#$ID)'
/**
 * Expands the Subject string using a predefined set of tokens
 */
def subjectString={text,binding->
    //defines the set of tokens usable in the subject configuration property
    def tokens=[
        '$STATUS': binding.execution.status.toUpperCase(),
        '$status': binding.execution.status.toLowerCase(),
        '$PROJECT': binding.execution.project,
        '$JOB': binding.execution.job.name,
        '$GROUP': binding.execution.job.group,
        '$JOB_FULL': (binding.execution.job.group?binding.execution.job.group+'/':'')+binding.execution.job.name,
        '$USER': binding.execution.user,
        '$ID': binding.execution.id.toString()
    ]
    text.replaceAll(/(\$\w+)/){
        if(tokens[it[1]]){
            tokens[it[1]]
        }else{
            it[0]
        }
    }
}

rundeckPlugin(NotificationPlugin){
    title="PagerDuty Trigger"
    description="Create a Trigger event."
    configuration{
        subject title:"Subject", description:"Incident subject line",defaultValue:defaultSubjectLine,required:true
        service_key title:"Service API Key", description:"The service key", required:true
    }
    onstart { Map executionData,Map config ->
        true
    }
    onfailure { Map executionData, Map configuration ->
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
        def url = new URL(pagerduty_url)
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

        def response = connection.content.text
        System.err.println(response)

        JsonNode jsnode= json.readTree(response)
        def status = jsnode.get("status").asText()
        if (! "success".equals(status)) {
            System.err.println("ERROR: PagerDutyNotification plugin status: " + status)
        }
        true
    }
    onsuccess {
        true
    }

}
