/**
 *  Lights Controller
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
    name: "Lights Controller",
    namespace: "ovonick",
    author: "Nick Yantikov",
    description: "Control Lights with switches, motion sensors, illuminance sensor, and more",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	section("Select Devices and enter parameters") {
		input "switches",          "capability.switch",                 title: "Switch Devices",      required: true,  multiple: true
		input "motions",           "capability.motionSensor",           title: "Motion Sensors",      required: false, multiple: true
        input "illuminanceDevice", "capability.illuminanceMeasurement", title: "Illuminance Device",  required: false, multiple: false
		input "minutes",           "number",                            title: "Minutes to turn off", required: true,                  defaultValue: 1
		input "illuminance",       "number",                            title: "Illuminance",         required: false,                 defaultValue: 0, range: "0..100"
	}
}

def installed() {
	log.debug "${app.label}, installed()"
	initialize()
}

def updated() {
	log.debug "${app.label}, updated()"
	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "${app.label}, initialize()"

	subscribe(motions,  "motion.active",   motionActiveHandler)
	subscribe(motions,  "motion.inactive", motionInactiveHandler)
	subscribe(switches, "switch.on",       switchOnHandler)
	//subscribe(switches, "switch", switchAllHandler, [filterEvents: false])
}

def motionActiveHandler(event) {
	log.debug "${app.label}, motionActiveHandler"
    
    state.isMotionActive = true
}

def motionInactiveHandler(event) {
	log.debug "${app.label}, motionInactiveHandler"
    
    state.isMotionActive = false
	requestToTurnOff()
}

/*
def switchAllHandler(event) {
	log.debug "${app.label}, switchAllHandler"
}
*/

def switchOnHandler(event) {
	log.debug "${app.label}, switchOnHandler"
    
    switchesOn()
    
    // Motion sensors may not have picked up motion when switch was turned on.
    // If switch was turned on we also assume there was a motion (that is if there are motion sensors at all)
    if (motions) {
    	state.isMotionActive = true
    } else {
    	state.isMotionActive = false
    }
        
	requestToTurnOff()
}

def requestToTurnOff() {
	def delaySeconds = minutes * 60
    //def delayMilliseconds = delaySeconds * 1000
	//state.turnOffAt = now() + delayMilliseconds
    
    //log.debug "${app.label}, requestToTurnOff() - delaySeconds: ${delaySeconds}, delayMilliseconds: ${delayMilliseconds}, now(): ${now()}, state.turnOffAt: ${state.turnOffAt}"
    log.debug "${app.label}, requestToTurnOff() - delaySeconds: ${delaySeconds}"
    
    if (delaySeconds == 0) {
    	switchesOff()
    } else {
    	runIn(delaySeconds, switchesOff)
    }
}

def switchesOff(event) {
	switchesOff()
}

def switchesOff() {
    //log.debug "${app.label}, switchesOff() - now(): ${now()}, state.turnOffAt: ${state.turnOffAt}"
    log.debug "${app.label}, switchesOff() state.isMotionActive: ${state.isMotionActive}"

	if (state.isMotionActive) {
    	return
    }
    
    switches.off()
}

def switchesOn() {
	log.debug "${app.label}, switchesOn() state.isMotionActive: ${state.isMotionActive}"

    switches.on()
}