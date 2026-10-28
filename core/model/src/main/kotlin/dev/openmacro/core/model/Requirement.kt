package dev.openmacro.core.model

/** A grant the user may need to give, derived from what their macros actually use. */
enum class Requirement(val title: String, val rationale: String) {
    NOTIFICATIONS("Notifications", "Show the engine status and notifications posted by macros."),
    BLUETOOTH_CONNECT("Nearby devices", "Detect which Bluetooth device connected or disconnected."),
    EXACT_ALARMS("Alarms & reminders", "Run time and interval triggers on the exact minute."),
    DND_ACCESS("Do Not Disturb access", "Change ringer, DND mode and DND-affected volumes."),
    PHONE_STATE("Phone", "Detect incoming, outgoing, answered and missed calls."),
    CALL_LOG("Call log", "See the phone number of incoming calls."),
    CALL_PHONE("Make calls", "Place phone calls from a macro."),
    ANSWER_CALLS("Answer calls", "Answer or end calls from a macro."),
    SEND_SMS("Send SMS", "Send text messages from a macro."),
    RECEIVE_SMS("Receive SMS", "Trigger macros when a text message arrives."),
    WRITE_SETTINGS("Modify system settings", "Change brightness, screen timeout, rotation and font size."),
    OVERLAY("Display over other apps", "Open apps, web pages and intents while OpenMacro is in the background."),
    ALL_FILES("All files access", "Read, write, copy and delete files in shared storage."),
    ACCESSIBILITY("Accessibility service", "Tap and swipe the screen, read on-screen text, press Back/Home, and detect the foreground app."),
    NOTIFICATION_ACCESS("Notification access", "React to notifications from other apps and read or clear them."),
    SECURE_SETTINGS("Secure settings", "Toggle Wi-Fi, mobile data, airplane mode and other protected settings. Needs root or a one-time ADB grant."),
    LOCATION("Location", "Detect when you enter or leave a place."),
    BACKGROUND_LOCATION("Background location", "Detect places while OpenMacro runs in the background."),
    ACTIVITY_RECOGNITION("Physical activity", "Count your steps for step-based triggers."),
    CAMERA("Camera", "Take photos from a macro."),
}
