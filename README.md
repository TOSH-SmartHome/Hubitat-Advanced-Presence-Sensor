# Advanced Presence Sensor
The Advanced Presence Sensor is a virtual presence detector designed for the Hubitat Elevation platform, boasting additional features such as network ping functionality and integration with MQTT ESPresence to track room-by-room presence. When you set up the Life360 integration, it utilizes a virtual presence sensor for each family member. You can use this driver to replace the initially installed one, providing you with advanced presence detection capabilities. If there's a glitch in Life360 falsely indicating that you've left home while your phone is still connected to the home network, this sensor won't mark you as away until both conditions are genuinely met.

## FEATURES
    - Can replace the driver installed by 3rd party presence detection integrations
    - Utilizes a Wi-Fi ping feature to ping your phone for additional redundancy
        `Note: you will need to set a static IP or IP reservation on your home network router`
    - Can integrate with Home Assistant's [ESPresense](https://espresense.com/) using MQTT***

## Installation
You can find detailed step by step instruction at [This Old Smart Home](https://thisoldsmarthome.com/automations/life360/?tab=hubitat).

### MQTT
Easily integrates with MQTT, just add your broker information under preferences and click save. The topics are automatically configured and uses the devices display name in lowercase and an '_' replaces the spaces. Below are all five topics below.
#### Topics
    - stat/device_name/presence
    - stat/device_name/room

#### ESPresense
ESPresense is a work in progress, once I have ironed out all the details I will post a tutorial with detailed instructions. However in the meantime if you are going to play around with it you will need to make a change to the driver. You will have to add your rooms to the room attribute enum `attribute "room", "enum", ["not_home", "garage"]`.