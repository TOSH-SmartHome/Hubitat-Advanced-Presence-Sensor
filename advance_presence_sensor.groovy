/**
 *  Advanced Presence Sensor
 *
 *  MIT License
 *
 *  Copyright (c) 2023 This Old Smart Home
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 */

public static String version()          {  return "v3.0.1"  }
public static String name()             {  return "Advanced Presence Sensor"  }
public static String codeUrl()
{
    return "https://raw.githubusercontent.com/TOSH-SmartHome/Hubitat-Advanced-Presence-Sensor/main/advance_presence_sensor.groovy"
}
public static String driverInfo()
{
    return """
        <p style='text-align:center'></br>
        <strong><a href='https://thisoldsmarthome.com' target='_blank'>This Old Smart Home</a></strong> (TOSH-SmartHome)</br>
        ${name()}</br>
        <em>${version()}</em></p>
    """
}

metadata {
    definition (name: name(), namespace: "tosh", author: "John Goughenour", importUrl: codeUrl()) {
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"
        capability "Presence Sensor"
        
        attribute "MqttConnection", "enum", ["CONNECTED", "DISCONNECTED", "UNKNOWN"]
        attribute "room", "enum", ["not_home", "garage"]
        attribute "pingable", "enum", ["yes", "no"]
		
        command "arrived"
        command "departed"
    }   
    
    preferences {
        def poll = [:]
		poll << [5 : "Every 5 seconds"]
		poll << [10 : "Every 10 seconds"]
		poll << [15 : "Every 15 seconds"]
		poll << [30 : "Every 30 seconds"]
		poll << [45 : "Every 45 seconds"]
		poll << [60 : "Every 1 minute"]
		poll << [120 : "Every 2 minutes"]
		poll << [300 : "Every 5 minutes"]
		poll << [600 : "Every 10 minutes"]
        
        input (name: "ipAddress", type: "string", title: "<b>Phone IP Address</b>", required: false, 
               description: "Enter IP address to enable Wi-F- presence. </br>Timeout is required to change presence state.")
        input (name: "timeout", type: "number", title: "<b>Timeout</b>", range: "0..99", required: false, defaultValue: 0, 
               description: "Number of tries without a response before device is not present. </br>Range: 0-99 Default: 0 (Disabled)")
        input (name: "heartbeat", type: "enum", title: "<b>Heartbeat</b>", options: poll, defaultValue: 60, 
               description: "Set polling intervals from every 5 seconds to every 10 minutes </br><i>Default: </i><font color=\"red\">Every 1 minute</font>")
        input(name: "mqttBroker", type: "string", title: "<b>MQTT Broker</b>", description: "Enter MQTT Broker IP and Port e.g. server_IP:1883", required: false)
        input(name: "mqttUsername", type: "string", title: "<b>MQTT Username</b>", description: "Enter MQTT broker username", required: false)
        input(name: "mqttPassword", type: "password", title: "<b>MQTT Password</b>", description: "Enter password for your MQTT Broker.", required: false)
        input(name: "mqttTopic", type: "string", title: "<b>MQTT Topic</b>", description: "Enter MQTT Room Topic to listen for", required: false)
        input(name: "infoLogging", type: "bool", title: "<b>Enable Description Text</b>", defaultValue: "true", description: "", required: false)
        input(name: "debugLogging", type: "bool", title: "<b>Enable Debug Logging</b>", defaultValue: "true", description: "", required: false)
        input name:"about", type: "text", title: "<b>About Driver</b>", 
            description: "An advanced virtual presence sensor that uses MQTT to update additional attributes. ${driverInfo()}"
     }
}

def installed(){
	if(infoLogging) log.info "${device.displayName} is installing"
    configure()
}

def updated(){
	if(infoLogging) log.info "${device.displayName} is updating"
    configure()
}

def uninstalled(){
	if(infoLogging) log.info "${device.displayName} is uninstalling"
	unschedule()
    if(mqttTopic) 
        interfaces.mqtt.unsubscribe("${mqttTopic}")
    interfaces.mqtt.disconnect()
}

def configure() {
    if(infoLogging) log.info "${device.displayName} is configuring"
	unschedule()
    if(mqttBroker && mqttUsername) {
        state.mqtt = true
        if(mqttTopic) 
            interfaces.mqtt.unsubscribe("${mqttTopic}")
        interfaces.mqtt.disconnect()
    } else state.mqtt = false

    if(infoLogging) log.info "${device.displayName} is initializing attributes"    
    // Initialize Attributes and Stat Variables
    sendEvent(name: "MqttConnection", value: 'DISCONNECTED')
    sendEvent(name: "room", value: 'not_home')
    sendEvent(name: "pingable", value: 'no')
    state.tryCount = 0   

    initialize()
}

def initialize() {
    if(infoLogging) log.info "${device.displayName} is initializing"
    
    // Setup MQTT Connection
    if(state.mqtt) mqtt_connect()
    
    if(heartbeat.toInteger() < 60) {
        cron = "0/$heartbeat * * * * ? *"
    } else {
        cron = "0 0/${heartbeat.toInteger() / 60} * * * ? *" 
    }
    schedule(cron, refresh)
    runIn(2, refresh)
}

def refresh() {
    if(infoLogging) log.info "${device.displayName} is refreshing"
    
    if( state.mqtt && !interfaces.mqtt.isConnected() ) mqtt_connect()
    
    if (ipAddress) {
        state.tryCount++
        if (timeout > 0 && state.tryCount > timeout) departed()
	    asynchttpGet("httpGetCallback", [uri: "http://${ipAddress}/"])
    }
}

// handle commands
def arrived() {
    if(infoLogging) log.info "${device.displayName} is present"
	sendEvent(name: "presence", value: 'present')
    if(state.mqtt) sendMqttCommand("present", 'presence')
}

def departed() {
    if(infoLogging) log.info "${device.displayName} is not present"
	if(device.currentValue("room") == 'not_home' && device.currentValue("pingable") == 'no') {
        sendEvent(name: "presence", value: 'not present')
        if(state.mqtt) sendMqttCommand("not present", 'presence')
    }
}

def change_rooms(room) {
    if(infoLogging) log.info "${device.displayName} is  changing rooms"
    sendEvent(name: "room", value: room)
    if(state.mqtt) sendMqttCommand(room, 'room')
    if(room != 'not_home' && device.currentValue("presence") == 'not present') arrived()
}

def mqtt_connect() {
    if(debugLogging) log.debug "${device.displayName} is to connect to MQTT Broker."
    
    try {
        if(debugLogging) log.debug "${device.displayName} settting up MQTT Broker"
        interfaces.mqtt.connect(
            "tcp://${mqttBroker}", 
            "${location.hub.name.toLowerCase().replaceAll(' ', '_')}_${device.getDeviceNetworkId()}", 
            mqttUsername, 
            mqttPassword
        )
                
        if(mqttTopic) {
            if(debugLogging) log.debug "${device.displayName} subscribing to topic: ${mqttTopic}"
            interfaces.mqtt.subscribe("${mqttTopic}")
        }
    } catch(Exception e) {
        log.error "Unable to connect to the MQTT Broker ${e}"
        sendEvent(name: "MqttConnection", value: 'DISCONNECTED')
    }
}

def sendMqttCommand(cmnd, payload) {
    if(debugLogging) log.debug "${device.displayName} MQTT sending Command: ${cmnd} Payload: ${payload}"
    if(debugLogging) 
            log.debug "${device.displayName} is sending Topic: stat/${device.displayName.toLowerCase().replaceAll(' ', '_')}/${payload}/ Command: ${cmnd}"
        interfaces.mqtt.publish(
            "stat/${device.displayName.toLowerCase().replaceAll(' ', '_')}/${payload}/", 
            "${cmnd}", 2, true
        )
}

// parse events and messages
def parse(message) {
    def msg = interfaces.mqtt.parseMessage(message)
	if(debugLogging) log.debug "Parsing '${msg}'"
	// TODO: handle 'switch' attribute
    switch(msg.topic) {
        case "${mqttTopic}":
           change_rooms(msg.payload)
           break
        default:
            if(debugLogging) log.info "Parsing ${device.displayName}: nothing to parse"
    }
}

def mqttClientStatus(status) {
    if(debugLogging) log.debug "MQTT Client Status: ${status}"
    switch(status) {
        case ~/.*Connection succeeded.*/:
            sendEvent(name: "MqttConnection", value: 'CONNECTED')
            break
        case ~/.*Error.*/:
            log.error "MQTT Client Status ${device.displayName}: has errored"
            sendEvent(name: "MqttConnection", value: 'DISCONNECTED')
            interfaces.mqtt.disconnect()
            runIn(5, mqtt_connect)
            break
        default:
            log.warn "MQTT Client Status ${device.displayName}: unknown status"
            sendEvent(name: "MqttConnection", value: 'UNKNOWN')
    }
}

def httpGetCallback(response, data) {
	if(debugLogging) log.debug "${device.displayName}: httpGetCallback(${groovy.json.JsonOutput.toJson(response)}, data)"
	
	if (response && response.status == 408 && response.errorMessage.contains("Connection refused")) {
		state.tryCount = 0
        sendEvent(name: "pingable", value: 'yes')
		arrived()
	} else sendEvent(name: "pingable", value: 'no')
}