# Notification-2-Sample-MS

This repository contains a practical example of a microservice utilizing the Notification 2.0 API.

## Overview

This running example addresses various aspects of the Notification 2.0 API, providing insights into common scenarios encountered by developers.

### Key Features
* Subscription to a device in managed object context
* Subscription in tenant context
* Example of subscribing for measurements
* Subscription to all devices
* Handling disconnects
* Reconnect logic
* Posting an alarm directly on the microservice (MS) if a disconnect is detected
* Multitenancy support

### Important Points for Notification 2.0 API
Here are essential considerations for anyone working with the Notification 2.0 API:

* Unsubscribe Subscribers: It's crucial to unsubscribe a subscriber if it's no longer needed. Failure to do so can lead to a continuous accumulation of notifications on the tenant, potentially causing storage issues.

* Disconnect Handling: Disconnects are equally important. When a subscriber disconnects, the platform persists the notification for later consumption upon reconnection. The WebSocket should keep attempting to reconnect. To alert users, the MS includes logic to raise a critical alarm upon disconnect detection, clearing the alarm once reconnected.

* Reconnect Challenges: Immediate reconnection attempts may result in a 409 Conflict error. This occurs when you try to reconnect before the platform recognizes a disconnected WebSocket and drops the connection. The MS logic retries with a delay to avoid this issue.

* Explicit Management of WebSocket Connection State: This example manages the WebSocket connection state explicitly.

* Consideration for WebSocket Library: While this example uses explicit reconnection logic, it might be beneficial to explore WebSocket libraries that handle automatic reconnects, such as tooTallNate.

* Subscribing to all devices is generally not recommended. The Notification 2.0 API intentionally lacks direct support for this practice. However, recognizing its frequent need, this sample demonstrates how it can be achieved.

* The microservice also incorporates multitenancy support. This enables deployment on the management tenant, with multiple subtenants subscribing to it. While the example illustrates the process, for actual deployment, consider enhancing the code through the implementation of multi-threading. Utilizing multiple threads and shared resources is advisable; refer to the documentation [here](https://cumulocity.com/guides/reference/notifications/#shared-consumer-tokens) for more information.


### Assumption
This MS assumes that it subscribes to all devices and maintains an open connection indefinitely.

### Disclaimer
These tools are provided as-is and without warranty or support. They do not constitute part of the Software AG product suite. Users are free to use, fork and modify them, subject to the license agreement. While Software AG welcomes contributions, we cannot guarantee to include every contribution in the master project
