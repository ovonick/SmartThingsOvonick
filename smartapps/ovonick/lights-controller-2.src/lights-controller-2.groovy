/**
 *  Lights Controller 2
 *
 *  Copyright 2017 Nick Yantikov
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Lights Controller 2",
    namespace: "ovonick",
    author: "Nick Yantikov",
    description: "Control lights and dimmers with motion, door open",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    section("Events that trigger lights on") {
        input "switchesInput",    "capability.switch",        title: "Switch Devices", required: true,  multiple: true
        input "motionsInput",     "capability.motionSensor",  title: "Motion Sensors", required: false, multiple: true
        input "doorSensorsInput", "capability.contactSensor", title: "Door Sensors",   required: false, multiple: true
    }

    section("Lights that are being controlled") {
        input "switchesControlled", "capability.switch",      title: "Switch Devices",  required: false,  multiple: true
        input "dimmersControlled",  "capability.switchLevel", title: "Dimmers Devices", required: false,  multiple: true
    }
    
    section("Parameters") {
        input "isTimeRestricted",             "bool",    title: "Run only between Sunset and Sunrise?",         required: true, defaultValue: true
        input "sunriseSunsetOffset",          "number",  title: "Sunrise/Sunset Offset"
        input "turnOffIntervalPhysicalEvent", "number",  title: "Minutes to turn off after pressing on switch", required: true, defaultValue: 30, range: "1..*"
        input "turnOffIntervalSensorEvent",   "number",  title: "Minutes to turn off after motion/door open",   required: true, defaultValue: 10, range: "1..*"
        input "dimmToLevel",                  "number",  title: "Set dimmers to this level before turning off"
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
    subscribe(motionsInput,     "motion.active",   motionActiveHandler)
    subscribe(switchesInput,    "switch.on",       switchOnHandler)
    subscribe(switchesInput,    "switch.off",      switchOffHandler)
    subscribe(doorSensorsInput, "contact.open",    contactOpenHandler)
}

def switchOnHandler(event) {
    log.debug "${app.label}, switchOnHandler, event: ${event.value}, event.physical: ${event.physical}"

    if (!event.physical) {
        return
    }
    
    scheduleTurnOff(turnOffIntervalPhysicalEvent)
}

def switchOffHandler(event) {
    log.debug "${app.label}, switchOffHandler, event: ${event.value}, event.physical: ${event.physical}"

    if (!event.physical) {
        return
    }
    
    state.turnOnAfter = now() + 10 * 1000 // 10 seconds silent period where no event will turn switches on after physically turning off
}

def motionActiveHandler(event) {
    log.debug "${app.label}, motionActiveHandler, event.value: ${event.value}, event: ${event}"
    
    //dimmersControlled?.getSupportedAttributes().each {log.debug "${it.name}"}
    //dimmersControlled?.each {log.debug "${it.switchState.value}"}
    
	turnOn()
}

def contactOpenHandler(event) {
    log.debug "${app.label}, contactOpenHandler, event.value: ${event.value}, event: ${event}"
    
    turnOn()
}

def turnOn() {
	if (!isShouldTurnOn()) {
    	 return
    }

	// Turn on only those that are off
    dimmersControlled*.setLevel(100)
    dimmersControlled*.on()
    
    switchesControlled*.on()
    
    scheduleTurnOff(turnOffIntervalSensorEvent)
}

def getDevicesWithSwitchState(devices, stateValue) {
	return devices?.findAll { it.switchState.value == stateValue }
}

def isShouldTurnOn() {
	log.debug "isTimeRestricted: ${isTimeRestricted}"
    
	return (!isTimeRestricted || isBetweenSunsetAndSunrise()) && (now() > state.turnOnAfter)
}

def isBetweenSunsetAndSunrise() {
	def sunriseAndSunsetWithOffset = getSunriseAndSunset(sunriseOffset: "00:${sunriseSunsetOffset}", sunsetOffset: "-00:${sunriseSunsetOffset}")

	def isTimeOfDayIsBetweenSunsetAndSunrise = timeOfDayIsBetween(sunriseAndSunsetWithOffset.sunset, sunriseAndSunsetWithOffset.sunrise, new Date(), location.timeZone)
    
    log.debug "isTimeOfDayIsBetweenSunsetAndSunrise: ${isTimeOfDayIsBetweenSunsetAndSunrise}"
    
    return isTimeOfDayIsBetweenSunsetAndSunrise
}

def scheduleTurnOff(turnOffInterval) {
    def delaySeconds = Math.max(60, turnOffInterval * 60)

    log.debug "${app.label}, scheduleTurnOff(), turnOffInterval: ${turnOffInterval}, delaySeconds: ${delaySeconds}"

	state.turnOffAfter = now() + (delaySeconds * 1000)

    log.debug "state.turnOffAfter: ${state.turnOffAfter}"

    runIn(delaySeconds, switchesOffOrDimHandler)
}

def switchesOffOrDimHandler(event) {
	// Some motion sensors don't fire "motion.active" events for 4-5 minutes after first motion is sensed
    // In this cases we need to check if motion sensors are still in "motion.active" mode then
    // schedule this event one more time
    if (motionsInput?.any {it.currentMotion == 'active'}) {
        log.debug("Rescheduling turn off handler because there are motion sensors still in motion.active mode")
        scheduleTurnOff(turnOffIntervalSensorEvent)
        return
    }

	def dimmersThatAreOn = getDevicesWithSwitchState(dimmersControlled, "on")
    
    if (dimmersThatAreOn) {
    	dimmersThatAreOn*.setLevel(dimmToLevel)
        runIn(30, dimmersFullyOffHandler)
    }
    
    getDevicesWithSwitchState(switchesControlled, "on")*.off()
}

def dimmersFullyOffHandler() {
    log.debug "dimmersFullyOffHandler, state.turnOffAfter: ${state.turnOffAfter}, now(): ${now()}"

	if (now() < state.turnOffAfter)
	    return

	dimmersControlled*.setLevel(100)	
	dimmersControlled*.off()
}