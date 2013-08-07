Use this [notification](http://rundeck.org/docs/developer/notification-plugin-development.html)
plugin to send [trigger](http://developer.pagerduty.com/documentation/integration/events/trigger)
events to your [PagerDuty](https://pagerduty.com) service.

The plugin requires two configuration parameters:

* subject: This string will be set as the description for the generated incident.
* service_key: This is the API Key to your service.

Context variables usable in the subject line:

* `${job.status}`: Job execution status (eg, FAILED, SUCCESS).
* `${job.project}`: Job project name.
* `${job.name}`: Job name.
* `${job.group}`: Job group name.
* `${job.user}`: User that executed the job.
* `${job.execid}`: Job execution ID.

## Installation

Copy the groovy script to the plugins directory:

    cp src/PagerDutyNotification.groovy to $RDECK_BASE/libext

and start using it!