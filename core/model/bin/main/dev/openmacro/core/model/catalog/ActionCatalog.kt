package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.ActionSpec as A
import dev.openmacro.core.model.AudioStream
import dev.openmacro.core.model.DndMode
import dev.openmacro.core.model.EncodeMode
import dev.openmacro.core.model.FileOp
import dev.openmacro.core.model.GenerateKind
import dev.openmacro.core.model.HashAlgorithm
import dev.openmacro.core.model.HttpMethod
import dev.openmacro.core.model.IntentTarget
import dev.openmacro.core.model.LoopMode
import dev.openmacro.core.model.MediaCommand
import dev.openmacro.core.model.RebootMode
import dev.openmacro.core.model.Requirement
import dev.openmacro.core.model.RingerMode
import dev.openmacro.core.model.SettingNamespace
import dev.openmacro.core.model.SettingsScreen
import dev.openmacro.core.model.SoundChoice
import dev.openmacro.core.model.SwitchMode
import dev.openmacro.core.model.TextOp
import dev.openmacro.core.model.UiButton
import dev.openmacro.core.model.VarType
import dev.openmacro.core.model.VibrationPattern
import dev.openmacro.core.model.VolumeDirection
import dev.openmacro.core.model.WallpaperTarget
import dev.openmacro.core.model.catalog.BlockRole as R
import dev.openmacro.core.model.catalog.UriKind

internal object ActionCatalog {

    // A getter avoids object-init ordering issues with the per-category lists defined below.
    val all: List<ActionDef<*>>
        get() = controlFlow + variablesText + deviceAudio + displayNotifications + appsPhone + filesMacro + network + uiAutomation + notificationsAccess + system

    private val controlFlow: List<ActionDef<*>> = listOf(
        action<A.If>(
            Category.LOGIC, "❓", "If", "Run the next steps only if conditions are met",
            create = { A.If() },
            summary = { if (it.conditions.isEmpty()) "If …" else "If ${it.matchAll.allAny()} of ${it.conditions.size}" },
            fields = listOf(
                conditions("Conditions", { it.conditions }, { s, v -> s.copy(conditions = v) }, { it.matchAll }, { s, v -> s.copy(matchAll = v) }),
            ),
            block = R.IF_START,
        ),
        action<A.ElseIf>(
            Category.LOGIC, "↪️", "Else if", "Alternative branch with its own conditions",
            create = { A.ElseIf() },
            summary = { if (it.conditions.isEmpty()) "Else if …" else "Else if ${it.matchAll.allAny()} of ${it.conditions.size}" },
            fields = listOf(
                conditions("Conditions", { it.conditions }, { s, v -> s.copy(conditions = v) }, { it.matchAll }, { s, v -> s.copy(matchAll = v) }),
            ),
            block = R.ELSE_IF,
        ),
        action<A.Else>(Category.LOGIC, "↩️", "Else", "Runs when no earlier branch matched", { A.Else }, { "Else" }, block = R.ELSE),
        action<A.EndIf>(Category.LOGIC, "⏹️", "End if", "Closes an If block", { A.EndIf }, { "End if" }, block = R.IF_END),
        action<A.Loop>(
            Category.LOGIC, "🔁", "Loop", "Repeat steps a number of times or while conditions hold",
            create = { A.Loop() },
            summary = {
                when (it.mode) {
                    LoopMode.COUNT -> "Loop ${it.count}×"
                    LoopMode.WHILE -> "Loop while ${it.matchAll.allAny()} of ${it.conditions.size}"
                }
            },
            fields = listOf(
                choice("Mode", LoopMode.entries, { it.mode }, { s, v -> s.copy(mode = v) }, { if (it == LoopMode.COUNT) "Fixed count" else "While condition" }),
                number("Repeat", 1..1000, { it.count }, { s, v -> s.copy(count = v) }, "×", visible = { it.mode == LoopMode.COUNT }),
                conditions("While", { it.conditions }, { s, v -> s.copy(conditions = v) }, { it.matchAll }, { s, v -> s.copy(matchAll = v) }, visible = { it.mode == LoopMode.WHILE }),
            ),
            block = R.LOOP_START,
        ),
        action<A.EndLoop>(Category.LOGIC, "⏹️", "End loop", "Closes a Loop block", { A.EndLoop }, { "End loop" }, block = R.LOOP_END),
        action<A.BreakLoop>(Category.LOGIC, "🚪", "Break", "Exit the enclosing loop", { A.BreakLoop }, { "Break loop" }, block = R.LOOP_JUMP),
        action<A.ContinueLoop>(Category.LOGIC, "⏭️", "Continue", "Skip to the next loop iteration", { A.ContinueLoop }, { "Continue loop" }, block = R.LOOP_JUMP),
        action<A.StopMacro>(Category.LOGIC, "🛑", "Stop macro", "Stop running this macro now", { A.StopMacro }, { "Stop macro" }),
        action<A.Wait>(
            Category.LOGIC, "⏳", "Wait", "Pause before the next action (optionally a random range)",
            create = { A.Wait() },
            summary = {
                if (it.maxSeconds > it.seconds) "Wait ${formatDuration(it.seconds)}–${formatDuration(it.maxSeconds)}"
                else "Wait ${formatDuration(it.seconds)}"
            },
            fields = listOf(
                number("Duration (min)", 0..3600, { it.seconds }, { s, v -> s.copy(seconds = v) }, "s"),
                number("Up to (0 = fixed)", 0..3600, { it.maxSeconds }, { s, v -> s.copy(maxSeconds = v) }, "s"),
            ),
        ),
    )

    private val variablesText: List<ActionDef<*>> = listOf(
        action<A.SetVariable>(
            Category.VARIABLES, "🔢", "Set variable", "Assign a value or expression to a variable",
            create = { A.SetVariable() },
            summary = { "${it.variable.display()} = ${it.value.quoted()}" },
            fields = listOf(
                variable("Variable", { it.variable }, { s, v -> s.copy(variable = v) }),
                choice("Type", VarType.entries, { it.type }, { s, v -> s.copy(type = v) }),
                text("Value", { it.value }, { s, v -> s.copy(value = v) }, hint = "e.g. {lv=count} + 1, or 'toggle' for booleans"),
            ),
        ),
        action<A.ClearVariable>(
            Category.VARIABLES, "🧹", "Clear variable", "Reset a variable to its empty value",
            create = { A.ClearVariable() },
            summary = { "Clear ${it.variable.display()}" },
            fields = listOf(variable("Variable", { it.variable }, { s, v -> s.copy(variable = v) })),
        ),
        action<A.IncrementVariable>(
            Category.VARIABLES, "➕", "Increment variable", "Add a number (or expression) to a variable",
            create = { A.IncrementVariable() },
            summary = { "${it.variable.display()} += ${it.amount.quoted()}" },
            fields = listOf(
                variable("Variable", { it.variable }, { s, v -> s.copy(variable = v) }),
                text("Amount", { it.amount }, { s, v -> s.copy(amount = v) }, hint = "1, -1, or {lv=x}*2"),
            ),
        ),
        action<A.AppendVariable>(
            Category.VARIABLES, "📝", "Append to variable", "Append text to a string variable",
            create = { A.AppendVariable() },
            summary = { "${it.variable.display()} += ${it.text.quoted()}" },
            fields = listOf(
                variable("Variable", { it.variable }, { s, v -> s.copy(variable = v) }),
                text("Text", { it.text }, { s, v -> s.copy(text = v) }, multiline = true),
                text("Separator (optional)", { it.separator }, { s, v -> s.copy(separator = v) }),
            ),
        ),
        action<A.TextManipulation>(
            Category.VARIABLES, "✂️", "Text manipulation", "Transform text into a variable",
            create = { A.TextManipulation() },
            summary = { "${humanize(it.op)} → ${it.target.display()}" },
            fields = listOf(
                text("Input", { it.input }, { s, v -> s.copy(input = v) }, multiline = true),
                choice("Operation", TextOp.entries, { it.op }, { s, v -> s.copy(op = v) }),
                text("Argument 1", { it.arg1 }, { s, v -> s.copy(arg1 = v) }, visible = { it.op.usesArg1() }, hint = "pattern / start index / search"),
                text("Argument 2", { it.arg2 }, { s, v -> s.copy(arg2 = v) }, visible = { it.op.usesArg2() }, hint = "replacement / end index"),
                variable("Store in", { it.target }, { s, v -> s.copy(target = v) }),
            ),
        ),
        action<A.Log>(
            Category.VARIABLES, "📝", "Log", "Write a line to the OpenMacro log",
            create = { A.Log() },
            summary = { "Log ${it.text.quoted()}" },
            fields = listOf(text("Message", { it.text }, { s, v -> s.copy(text = v) }, multiline = true)),
        ),
        action<A.SetClipboard>(
            Category.VARIABLES, "📋", "Set clipboard", "Copy text to the clipboard",
            create = { A.SetClipboard() },
            summary = { "Clipboard = ${it.text.quoted()}" },
            fields = listOf(text("Text", { it.text }, { s, v -> s.copy(text = v) }, multiline = true)),
        ),
        action<A.ClearClipboard>(Category.VARIABLES, "🧽", "Clear clipboard", "Empty the clipboard", { A.ClearClipboard }, { "Clear clipboard" }),
        action<A.EncodeText>(
            Category.VARIABLES, "🔐", "Encode / decode text", "Base64 or URL encode/decode into a variable",
            create = { A.EncodeText() },
            summary = { "${humanize(it.mode)} → ${it.target.display()}" },
            fields = listOf(
                choice("Mode", EncodeMode.entries, { it.mode }, { s, v -> s.copy(mode = v) }),
                text("Input", { it.input }, { s, v -> s.copy(input = v) }, multiline = true),
                variable("Store in", { it.target }, { s, v -> s.copy(target = v) }),
            ),
        ),
        action<A.HashText>(
            Category.VARIABLES, "#️⃣", "Hash text", "MD5/SHA hash of text (hex) into a variable",
            create = { A.HashText() },
            summary = { "${humanize(it.algorithm)} → ${it.target.display()}" },
            fields = listOf(
                choice("Algorithm", HashAlgorithm.entries, { it.algorithm }, { s, v -> s.copy(algorithm = v) }),
                text("Input", { it.input }, { s, v -> s.copy(input = v) }, multiline = true),
                variable("Store in", { it.target }, { s, v -> s.copy(target = v) }),
            ),
        ),
        action<A.GenerateValue>(
            Category.VARIABLES, "🎲", "Generate value", "A UUID, random number or timestamp into a variable",
            create = { A.GenerateValue() },
            summary = { "${humanize(it.kind)} → ${it.target.display()}" },
            fields = listOf(
                choice("Kind", GenerateKind.entries, { it.kind }, { s, v -> s.copy(kind = v) }),
                number("Minimum", 0..1_000_000, { it.min }, { s, v -> s.copy(min = v) }, visible = { it.kind == GenerateKind.RANDOM_NUMBER }),
                number("Maximum", 0..1_000_000, { it.max }, { s, v -> s.copy(max = v) }, visible = { it.kind == GenerateKind.RANDOM_NUMBER }),
                variable("Store in", { it.target }, { s, v -> s.copy(target = v) }),
            ),
        ),
        action<A.ParseJson>(
            Category.VARIABLES, "🧩", "Parse JSON", "Extract a value from JSON by dot path into a variable",
            create = { A.ParseJson() },
            summary = { "JSON ${it.path.quoted("(path)")} → ${it.target.display()}" },
            fields = listOf(
                text("JSON", { it.json }, { s, v -> s.copy(json = v) }, multiline = true),
                text("Path", { it.path }, { s, v -> s.copy(path = v) }, magicText = false, hint = "data.items.0.name"),
                variable("Store in", { it.target }, { s, v -> s.copy(target = v) }),
            ),
        ),
        action<A.FormatDateTime>(
            Category.VARIABLES, "📅", "Format date/time", "Format the current time or a timestamp into a variable",
            create = { A.FormatDateTime() },
            summary = { "Format ${it.pattern.quoted()} → ${it.target.display()}" },
            fields = listOf(
                text("Source (now or epoch)", { it.source }, { s, v -> s.copy(source = v) }, hint = "now, or {lv=timestamp}"),
                text("Pattern", { it.pattern }, { s, v -> s.copy(pattern = v) }, magicText = false, hint = "yyyy-MM-dd HH:mm"),
                variable("Store in", { it.target }, { s, v -> s.copy(target = v) }),
            ),
        ),
    )

    private val deviceAudio: List<ActionDef<*>> = listOf(
        action<A.Flashlight>(
            Category.DEVICE, "🔦", "Flashlight", "Turn the torch on, off or toggle it",
            create = { A.Flashlight() },
            summary = { "Flashlight ${humanize(it.mode).lowercase()}" },
            fields = listOf(choice("Mode", SwitchMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
        ),
        action<A.Vibrate>(
            Category.DEVICE, "📳", "Vibrate", "Vibrate with a pattern",
            create = { A.Vibrate() },
            summary = { "Vibrate (${humanize(it.pattern).lowercase()})" },
            fields = listOf(choice("Pattern", VibrationPattern.entries, { it.pattern }, { s, v -> s.copy(pattern = v) })),
            requirements = { setOf() },
        ),
        action<A.FlashlightStrobe>(
            Category.DEVICE, "🔦", "Flashlight strobe", "Flash the torch on and off repeatedly",
            create = { A.FlashlightStrobe() },
            summary = { "Strobe ${it.times}× every ${it.intervalMs}ms" },
            fields = listOf(
                number("Flashes", 1..100, { it.times }, { s, v -> s.copy(times = v) }),
                number("Interval", 50..2000, { it.intervalMs }, { s, v -> s.copy(intervalMs = v) }, "ms"),
            ),
        ),
        action<A.TakePhoto>(
            Category.DEVICE, "📷", "Take photo", "Capture a still from the front or back camera to a file",
            create = { A.TakePhoto() },
            summary = { "Photo (${if (it.frontCamera) "front" else "back"}) → ${it.destination}" },
            fields = listOf(
                toggle("Camera", "Back", "Front", { !it.frontCamera }, { s, v -> s.copy(frontCamera = !v) }),
                text("Save folder or file", { it.destination }, { s, v -> s.copy(destination = v) }, magicText = false),
                variable("Store saved path in", { it.savedPath }, { s, v -> s.copy(savedPath = v) }, optional = true),
                info("Works best with the screen on; background capture is limited by Android."),
            ),
            requirements = { setOf(Requirement.CAMERA) },
        ),
        action<A.SetRingtone>(
            Category.AUDIO_MEDIA, "🎶", "Set ringtone", "Set the default ringtone, notification or alarm sound",
            create = { A.SetRingtone() },
            summary = { "Set ${humanize(it.type).lowercase()} sound" },
            fields = listOf(
                choice("Type", dev.openmacro.core.model.RingtoneType.entries, { it.type }, { s, v -> s.copy(type = v) }),
                uri("Sound", UriKind.SOUND, { it.uri }, { s, v -> s.copy(uri = v) }),
            ),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
        ),
        action<A.KeepAwake>(
            Category.DEVICE, "☕", "Keep awake", "Hold a wake lock to keep the screen or CPU on",
            create = { A.KeepAwake() },
            summary = { if (it.on) "Keep awake ${formatDuration(it.minutes * 60)}" else "Release keep awake" },
            fields = listOf(
                toggle("Action", "Keep awake", "Release", { it.on }, { s, v -> s.copy(on = v) }),
                number("Duration", 1..240, { it.minutes }, { s, v -> s.copy(minutes = v) }, "min", visible = { it.on }),
            ),
        ),
        action<A.ScreenOn>(Category.DEVICE, "💡", "Turn screen on", "Wake the screen", { A.ScreenOn }, { "Turn screen on" }),
        action<A.CarMode>(
            Category.DEVICE, "🚗", "Car mode", "Enter or exit the system car/UI mode",
            create = { A.CarMode() },
            summary = { "Car mode ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ),
        action<A.GetLocation>(
            Category.CONNECTIVITY, "📍", "Get location", "Read the current location into variables",
            create = { A.GetLocation() },
            summary = { "Location → ${it.latitude.display()}, ${it.longitude.display()}" },
            fields = listOf(
                variable("Latitude →", { it.latitude }, { s, v -> s.copy(latitude = v) }),
                variable("Longitude →", { it.longitude }, { s, v -> s.copy(longitude = v) }),
            ),
            requirements = { setOf(Requirement.LOCATION) },
        ),
        action<A.SetVolume>(
            Category.AUDIO_MEDIA, "🔊", "Set volume", "Set a stream's volume level",
            create = { A.SetVolume() },
            summary = { "${humanize(it.stream)} volume → ${it.percent}%" },
            fields = listOf(
                choice("Stream", AudioStream.entries, { it.stream }, { s, v -> s.copy(stream = v) }),
                number("Volume", 0..100, { it.percent }, { s, v -> s.copy(percent = v) }, "%"),
            ),
            requirements = { if (it.stream == AudioStream.RING || it.stream == AudioStream.NOTIFICATION) setOf(Requirement.DND_ACCESS) else emptySet() },
        ),
        action<A.VolumeStep>(
            Category.AUDIO_MEDIA, "🎚️", "Volume up/down", "Raise, lower or mute a stream",
            create = { A.VolumeStep() },
            summary = { "${humanize(it.stream)} ${humanize(it.direction).lowercase()}" },
            fields = listOf(
                choice("Stream", AudioStream.entries, { it.stream }, { s, v -> s.copy(stream = v) }),
                choice("Direction", VolumeDirection.entries, { it.direction }, { s, v -> s.copy(direction = v) }),
                switch("Show volume UI", { it.showUi }, { s, v -> s.copy(showUi = v) }),
            ),
        ),
        action<A.SetRingerMode>(
            Category.AUDIO_MEDIA, "🔔", "Ringer mode", "Normal, vibrate or silent",
            create = { A.SetRingerMode() },
            summary = { "Ringer: ${humanize(it.mode)}" },
            fields = listOf(choice("Mode", RingerMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = { if (it.mode == RingerMode.SILENT) setOf(Requirement.DND_ACCESS) else emptySet() },
        ),
        action<A.DoNotDisturb>(
            Category.AUDIO_MEDIA, "🌚", "Do Not Disturb", "Set the DND filter",
            create = { A.DoNotDisturb() },
            summary = { "DND: ${humanize(it.mode)}" },
            fields = listOf(choice("Mode", DndMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = { setOf(Requirement.DND_ACCESS) },
        ),
        action<A.MediaControl>(
            Category.AUDIO_MEDIA, "⏯️", "Media control", "Play, pause or skip media",
            create = { A.MediaControl() },
            summary = { humanize(it.command) },
            fields = listOf(choice("Command", MediaCommand.entries, { it.command }, { s, v -> s.copy(command = v) })),
        ),
        action<A.PlaySound>(
            Category.AUDIO_MEDIA, "🎵", "Play sound", "Play a notification, ringtone, alarm or custom sound",
            create = { A.PlaySound() },
            summary = { "Play ${humanize(it.sound).lowercase()}" },
            fields = listOf(
                choice("Sound", SoundChoice.entries, { it.sound }, { s, v -> s.copy(sound = v) }),
                uri("Custom sound", UriKind.SOUND, { it.uri }, { s, v -> s.copy(uri = v) }, visible = { it.sound == SoundChoice.CUSTOM }),
                switch("Wait to finish", { it.waitToFinish }, { s, v -> s.copy(waitToFinish = v) }),
            ),
        ),
        action<A.SpeakText>(
            Category.AUDIO_MEDIA, "🗣️", "Speak text", "Read text aloud (text-to-speech)",
            create = { A.SpeakText() },
            summary = { "Speak ${it.text.quoted()}" },
            fields = listOf(
                text("Text", { it.text }, { s, v -> s.copy(text = v) }, multiline = true),
                switch("Wait to finish", { it.waitToFinish }, { s, v -> s.copy(waitToFinish = v) }),
            ),
        ),
        action<A.SayTime>(Category.AUDIO_MEDIA, "🕰️", "Say current time", "Speak the current time aloud", { A.SayTime }, { "Say current time" }),
        action<A.StopSpeaking>(Category.AUDIO_MEDIA, "🤐", "Stop speaking", "Stop any ongoing text-to-speech", { A.StopSpeaking }, { "Stop speaking" }),
        action<A.MuteMicrophone>(
            Category.AUDIO_MEDIA, "🎙️", "Mute microphone", "Mute or unmute the mic",
            create = { A.MuteMicrophone() },
            summary = { "Mic ${humanize(it.mode).lowercase()}" },
            fields = listOf(choice("Mode", SwitchMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
        ),
        action<A.Speakerphone>(
            Category.AUDIO_MEDIA, "📢", "Speakerphone", "Turn speakerphone on or off during a call",
            create = { A.Speakerphone() },
            summary = { "Speakerphone ${if (it.on) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.on }, { s, v -> s.copy(on = v) })),
        ),
    )

    private val displayNotifications: List<ActionDef<*>> = listOf(
        action<A.SetBrightness>(
            Category.DISPLAY, "🔆", "Set brightness", "Set screen brightness or switch to auto",
            create = { A.SetBrightness() },
            summary = { if (it.auto) "Brightness: auto" else "Brightness → ${it.percent}%" },
            fields = listOf(
                switch("Automatic", { it.auto }, { s, v -> s.copy(auto = v) }),
                number("Brightness", 0..100, { it.percent }, { s, v -> s.copy(percent = v) }, "%", visible = { !it.auto }),
            ),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
        ),
        action<A.ScreenTimeout>(
            Category.DISPLAY, "⏲️", "Screen timeout", "Set the display sleep timeout",
            create = { A.ScreenTimeout() },
            summary = { "Screen timeout → ${formatDuration(it.seconds)}" },
            fields = listOf(number("Timeout", 5..1800, { it.seconds }, { s, v -> s.copy(seconds = v) }, "s")),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
        ),
        action<A.SetAutoRotate>(
            Category.DISPLAY, "🔄", "Auto-rotate", "Turn auto-rotate on, off or toggle",
            create = { A.SetAutoRotate() },
            summary = { "Auto-rotate ${humanize(it.mode).lowercase()}" },
            fields = listOf(choice("Mode", SwitchMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
        ),
        action<A.SetScreenRotation>(
            Category.DISPLAY, "🔁", "Screen rotation", "Force an orientation, or re-enable auto-rotate",
            create = { A.SetScreenRotation() },
            summary = { "Rotation: ${humanize(it.orientation).lowercase()}" },
            fields = listOf(choice("Orientation", dev.openmacro.core.model.ScreenRotation.entries, { it.orientation }, { s, v -> s.copy(orientation = v) })),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
        ),
        action<A.FontScale>(
            Category.DISPLAY, "🔡", "Font size", "Set the system font scale",
            create = { A.FontScale() },
            summary = { "Font scale → ${it.percent}%" },
            fields = listOf(number("Scale", 50..200, { it.percent }, { s, v -> s.copy(percent = v) }, "%")),
            requirements = { setOf(Requirement.WRITE_SETTINGS) },
        ),
        action<A.SetWallpaper>(
            Category.DISPLAY, "🖼️", "Set wallpaper", "Set the home and/or lock screen wallpaper",
            create = { A.SetWallpaper() },
            summary = { "Set ${humanize(it.target).lowercase()} wallpaper" },
            fields = listOf(
                uri("Image", UriKind.IMAGE, { it.uri }, { s, v -> s.copy(uri = v) }),
                choice("Apply to", WallpaperTarget.entries, { it.target }, { s, v -> s.copy(target = v) }),
            ),
        ),
        action<A.ShowNotification>(
            Category.NOTIFICATIONS, "💬", "Notification", "Post a local notification",
            create = { A.ShowNotification() },
            summary = { "Notify: ${it.title.quoted("(no title)")}" },
            fields = listOf(
                text("Title", { it.title }, { s, v -> s.copy(title = v) }),
                text("Text", { it.text }, { s, v -> s.copy(text = v) }, multiline = true),
            ),
            requirements = { setOf(Requirement.NOTIFICATIONS) },
        ),
        action<A.ShowToast>(
            Category.NOTIFICATIONS, "🍞", "Toast", "Show a short on-screen message",
            create = { A.ShowToast() },
            summary = { "Toast: ${it.text.quoted()}" },
            fields = listOf(
                text("Message", { it.text }, { s, v -> s.copy(text = v) }, multiline = true),
                switch("Long duration", { it.long }, { s, v -> s.copy(long = v) }),
            ),
        ),
        action<A.ShowOverlay>(
            Category.NOTIFICATIONS, "💬", "Floating text", "Show text over other apps for a few seconds",
            create = { A.ShowOverlay() },
            summary = { "Overlay: ${it.text.quoted()}" },
            fields = listOf(
                text("Text", { it.text }, { s, v -> s.copy(text = v) }, multiline = true),
                number("Duration", 1..120, { it.seconds }, { s, v -> s.copy(seconds = v) }, "s"),
                choice("Position", dev.openmacro.core.model.OverlayPosition.entries, { it.position }, { s, v -> s.copy(position = v) }),
            ),
            requirements = { setOf(Requirement.OVERLAY) },
        ),
    )

    private val appsPhone: List<ActionDef<*>> = listOf(
        action<A.LaunchApp>(
            Category.APPS, "🚀", "Launch app", "Open an installed app",
            create = { A.LaunchApp() },
            summary = { "Launch ${it.packageName.orAny("(choose app)")}" },
            fields = listOf(app("App", { it.packageName }, { s, v -> s.copy(packageName = v) })),
            requirements = { setOf(Requirement.OVERLAY) },
        ),
        action<A.OpenWebPage>(
            Category.APPS, "🌐", "Open web page", "Open a URL in the browser",
            create = { A.OpenWebPage() },
            summary = { "Open ${it.url.quoted()}" },
            fields = listOf(text("URL", { it.url }, { s, v -> s.copy(url = v) }, hint = "https://example.com")),
            requirements = { setOf(Requirement.OVERLAY) },
        ),
        action<A.LaunchHome>(Category.APPS, "🏠", "Home screen", "Go to the launcher", { A.LaunchHome }, { "Launch home screen" }),
        action<A.OpenMaps>(
            Category.APPS, "🗺️", "Open maps", "Open a place in maps, or start navigation",
            create = { A.OpenMaps() },
            summary = { "${if (it.navigate) "Navigate to" else "Map"} ${it.query.quoted()}" },
            fields = listOf(
                text("Place or address", { it.query }, { s, v -> s.copy(query = v) }),
                switch("Start navigation", { it.navigate }, { s, v -> s.copy(navigate = v) }),
            ),
        ),
        action<A.DialNumber>(
            Category.PHONE_SMS, "☎️", "Dial number", "Open the dialer with a number (no call permission)",
            create = { A.DialNumber() },
            summary = { "Dial ${it.number.quoted()}" },
            fields = listOf(text("Number", { it.number }, { s, v -> s.copy(number = v) })),
        ),
        action<A.LaunchAssistant>(Category.APPS, "🎙️", "Voice assistant", "Open the device voice assistant", { A.LaunchAssistant }, { "Launch voice assistant" }),
        action<A.SendIntent>(
            Category.APPS, "📡", "Send intent", "Broadcast or start a component with a custom intent",
            create = { A.SendIntent() },
            summary = { "Intent ${it.action.orAny("(custom)")}" },
            fields = listOf(
                choice("Type", IntentTarget.entries, { it.target }, { s, v -> s.copy(target = v) }),
                text("Action", { it.action }, { s, v -> s.copy(action = v) }, magicText = false, hint = "android.intent.action.VIEW"),
                text("Data URI", { it.data }, { s, v -> s.copy(data = v) }),
                text("Package", { it.packageName }, { s, v -> s.copy(packageName = v) }, magicText = false),
                text("Class", { it.className }, { s, v -> s.copy(className = v) }, magicText = false),
                text("Extras (key=value per line)", { it.extras }, { s, v -> s.copy(extras = v) }, multiline = true),
            ),
        ),
        action<A.ShareText>(
            Category.APPS, "📤", "Share text", "Open the system share sheet with text",
            create = { A.ShareText() },
            summary = { "Share ${it.text.quoted()}" },
            fields = listOf(text("Text", { it.text }, { s, v -> s.copy(text = v) }, multiline = true)),
        ),
        action<A.SetAlarm>(
            Category.APPS, "⏰", "Set alarm clock", "Create a system alarm",
            create = { A.SetAlarm() },
            summary = { "Alarm at ${formatTime(it.hour, it.minute)}" },
            fields = listOf(
                time("Time", { it.hour }, { it.minute }, { s, h, m -> s.copy(hour = h, minute = m) }),
                text("Label", { it.label }, { s, v -> s.copy(label = v) }),
            ),
        ),
        action<A.SetTimer>(
            Category.APPS, "⏲️", "Set countdown timer", "Start a timer in the clock app",
            create = { A.SetTimer() },
            summary = { "Timer ${formatDuration(it.seconds)}" },
            fields = listOf(
                number("Duration", 1..86400, { it.seconds }, { s, v -> s.copy(seconds = v) }, "s"),
                text("Label", { it.label }, { s, v -> s.copy(label = v) }),
            ),
        ),
        action<A.KillBackgroundApp>(
            Category.APPS, "💀", "Kill background app", "Stop an app running in the background",
            create = { A.KillBackgroundApp() },
            summary = { "Kill ${it.packageName.orAny("(choose app)")}" },
            fields = listOf(app("App", { it.packageName }, { s, v -> s.copy(packageName = v) })),
        ),
        action<A.OpenAppInfo>(
            Category.APPS, "ℹ️", "Open app info", "Open the system App Info screen for an app",
            create = { A.OpenAppInfo() },
            summary = { "App info: ${it.packageName.orAny("(choose app)")}" },
            fields = listOf(app("App", { it.packageName }, { s, v -> s.copy(packageName = v) })),
        ),
        action<A.UninstallApp>(
            Category.APPS, "🗑️", "Uninstall app", "Start uninstalling an app (asks for confirmation)",
            create = { A.UninstallApp() },
            summary = { "Uninstall ${it.packageName.orAny("(choose app)")}" },
            fields = listOf(app("App", { it.packageName }, { s, v -> s.copy(packageName = v) })),
        ),
        action<A.ForceStopApp>(
            Category.SYSTEM, "🛑", "Force-stop app", "Force-stop an app (needs Shizuku or root)",
            create = { A.ForceStopApp() },
            summary = { "Force-stop ${it.packageName.orAny("(choose app)")}" },
            fields = listOf(app("App", { it.packageName }, { s, v -> s.copy(packageName = v) })),
            requirements = secureReq,
        ),
        action<A.OpenCameraApp>(
            Category.APPS, "📷", "Open camera", "Launch the default camera app",
            create = { A.OpenCameraApp },
            summary = { "Open camera" },
        ),
        action<A.OpenSettings>(
            Category.APPS, "⚙️", "Open settings screen", "Jump to a specific system settings page",
            create = { A.OpenSettings() },
            summary = { "Open ${humanize(it.screen)} settings" },
            fields = listOf(choice("Screen", SettingsScreen.entries, { it.screen }, { s, v -> s.copy(screen = v) })),
        ),
        action<A.MakeCall>(
            Category.PHONE_SMS, "📞", "Make call", "Place a phone call",
            create = { A.MakeCall() },
            summary = { "Call ${it.number.quoted()}" },
            fields = listOf(text("Number", { it.number }, { s, v -> s.copy(number = v) }, hint = "+1…")),
            requirements = { setOf(Requirement.CALL_PHONE) },
        ),
        action<A.SendSms>(
            Category.PHONE_SMS, "✉️", "Send SMS", "Send a text message",
            create = { A.SendSms() },
            summary = { "SMS to ${it.number.quoted()}" },
            fields = listOf(
                text("Number", { it.number }, { s, v -> s.copy(number = v) }),
                text("Message", { it.message }, { s, v -> s.copy(message = v) }, multiline = true),
            ),
            requirements = { setOf(Requirement.SEND_SMS) },
        ),
        action<A.AnswerCall>(
            Category.PHONE_SMS, "✅", "Answer call", "Answer an incoming call", { A.AnswerCall }, { "Answer call" },
            requirements = { setOf(Requirement.ANSWER_CALLS) },
        ),
        action<A.EndCall>(
            Category.PHONE_SMS, "🔴", "End call", "End the current call", { A.EndCall }, { "End call" },
            requirements = { setOf(Requirement.ANSWER_CALLS) },
        ),
    )

    private val filesMacro: List<ActionDef<*>> = listOf(
        action<A.WriteFile>(
            Category.FILES, "💾", "Write to file", "Write or append text to a file",
            create = { A.WriteFile() },
            summary = { "${if (it.append) "Append to" else "Write"} ${it.path}" },
            fields = listOf(
                text("File path", { it.path }, { s, v -> s.copy(path = v) }, magicText = false),
                text("Text", { it.text }, { s, v -> s.copy(text = v) }, multiline = true),
                switch("Append", { it.append }, { s, v -> s.copy(append = v) }),
            ),
            requirements = { setOf(Requirement.ALL_FILES) },
        ),
        action<A.ReadFile>(
            Category.FILES, "📖", "Read file", "Read a file's contents into a variable",
            create = { A.ReadFile() },
            summary = { "Read ${it.path} → ${it.target.display()}" },
            fields = listOf(
                text("File path", { it.path }, { s, v -> s.copy(path = v) }, magicText = false),
                variable("Store in", { it.target }, { s, v -> s.copy(target = v) }),
            ),
            requirements = { setOf(Requirement.ALL_FILES) },
        ),
        action<A.FileOperation>(
            Category.FILES, "🗂️", "File operation", "Copy, move, delete, rename or create folders",
            create = { A.FileOperation() },
            summary = {
                when (it.op) {
                    FileOp.DELETE -> "Delete ${it.source}"
                    FileOp.CREATE_FOLDER -> "Create folder ${it.destination}"
                    else -> "${humanize(it.op)} ${it.source} → ${it.destination}"
                }
            },
            fields = listOf(
                choice("Operation", FileOp.entries, { it.op }, { s, v -> s.copy(op = v) }),
                text("Source", { it.source }, { s, v -> s.copy(source = v) }, magicText = false, visible = { it.op != FileOp.CREATE_FOLDER }),
                text("Destination", { it.destination }, { s, v -> s.copy(destination = v) }, magicText = false, visible = { it.op != FileOp.DELETE }),
            ),
            requirements = { setOf(Requirement.ALL_FILES) },
        ),
        action<A.RunMacro>(
            Category.MACRO, "▶️", "Run macro", "Run another macro's actions",
            create = { A.RunMacro() },
            summary = { "Run ${it.macro.display()}" },
            fields = listOf(macro("Macro", { it.macro }, { s, v -> s.copy(macro = v) })),
        ),
        action<A.EnableMacro>(
            Category.MACRO, "🎚️", "Enable/disable macro", "Enable, disable or toggle another macro",
            create = { A.EnableMacro() },
            summary = { "${humanize(it.mode)} ${it.macro.display()}" },
            fields = listOf(
                macro("Macro", { it.macro }, { s, v -> s.copy(macro = v) }),
                choice("Action", SwitchMode.entries, { it.mode }, { s, v -> s.copy(mode = v) }, { humanize(it) }),
            ),
        ),
        action<A.CancelMacro>(
            Category.MACRO, "✋", "Cancel macro", "Stop a running macro",
            create = { A.CancelMacro() },
            summary = { "Cancel ${it.macro.display()}" },
            fields = listOf(macro("Macro", { it.macro }, { s, v -> s.copy(macro = v) })),
        ),
        action<A.DisableThisMacro>(
            Category.MACRO, "🚫", "Disable this macro", "Disable the macro that's running (run-once patterns)",
            create = { A.DisableThisMacro },
            summary = { "Disable this macro" },
        ),
        action<A.SetEngine>(
            Category.MACRO, "⚡", "Enable/disable engine", "Turn the whole automation engine on or off",
            create = { A.SetEngine() },
            summary = { "Engine ${if (it.enabled) "on" else "off"}" },
            fields = listOf(toggle("State", "On", "Off", { it.enabled }, { s, v -> s.copy(enabled = v) })),
        ),
        action<A.ShellCommand>(
            Category.MACRO, "⌨️", "Shell command", "Run a shell command (optionally as root)",
            create = { A.ShellCommand() },
            summary = { "${if (it.root) "su" else "sh"}: ${it.command.quoted()}" },
            fields = listOf(
                text("Command", { it.command }, { s, v -> s.copy(command = v) }, multiline = true),
                switch("Run as root", { it.root }, { s, v -> s.copy(root = v) }),
                variable("Store output in", { it.output }, { s, v -> s.copy(output = v) }, optional = true),
                number("Timeout", 1..600, { it.timeoutSeconds }, { s, v -> s.copy(timeoutSeconds = v) }, "s"),
            ),
        ),
    )

    private val network: List<ActionDef<*>> = listOf(
        action<A.HttpRequest>(
            Category.NETWORK, "🌍", "HTTP request", "Call a URL / webhook and store the response",
            create = { A.HttpRequest() },
            summary = { "${it.method} ${it.url.quoted()}" },
            fields = listOf(
                choice("Method", HttpMethod.entries, { it.method }, { s, v -> s.copy(method = v) }),
                text("URL", { it.url }, { s, v -> s.copy(url = v) }, hint = "https://example.com/hook"),
                text("Headers (Name: value per line)", { it.headers }, { s, v -> s.copy(headers = v) }, multiline = true),
                text("Content-Type", { it.contentType }, { s, v -> s.copy(contentType = v) }, magicText = false, visible = { it.method.hasBody() }),
                text("Body", { it.body }, { s, v -> s.copy(body = v) }, multiline = true, visible = { it.method.hasBody() }),
                variable("Store response in", { it.responseBody }, { s, v -> s.copy(responseBody = v) }, optional = true),
                variable("Store status code in", { it.responseCode }, { s, v -> s.copy(responseCode = v) }, optional = true),
                number("Timeout", 1..120, { it.timeoutSeconds }, { s, v -> s.copy(timeoutSeconds = v) }, "s"),
            ),
            needsNetwork = true,
        ),
        action<A.UdpCommand>(
            Category.NETWORK, "📶", "Send UDP packet", "Send a UDP datagram (e.g. Wake-on-LAN, smart-home)",
            create = { A.UdpCommand() },
            summary = { "UDP ${it.host}:${it.port}" },
            fields = listOf(
                text("Host", { it.host }, { s, v -> s.copy(host = v) }, magicText = false, hint = "192.168.1.50"),
                number("Port", 1..65535, { it.port }, { s, v -> s.copy(port = v) }),
                text("Payload", { it.payload }, { s, v -> s.copy(payload = v) }, multiline = true),
            ),
            needsNetwork = true,
        ),
        action<A.ConnectivityCheck>(
            Category.NETWORK, "🔗", "Connectivity check", "Check if the internet is reachable",
            create = { A.ConnectivityCheck() },
            summary = { "Check ${it.url.quoted()}" },
            fields = listOf(
                text("URL", { it.url }, { s, v -> s.copy(url = v) }),
                variable("Store result in", { it.result }, { s, v -> s.copy(result = v) }, optional = true),
            ),
            needsNetwork = true,
        ),
        action<A.GetWeather>(
            Category.NETWORK, "🌦️", "Get weather", "Fetch current temperature and conditions into variables",
            create = { A.GetWeather() },
            summary = { "Weather → ${it.temperature.display()}" },
            fields = listOf(
                location(
                    "Location",
                    lat = { it.latitude }, lng = { it.longitude },
                    setLatLng = { s, lat, lng -> s.copy(latitude = lat, longitude = lng) },
                    showRadius = false,
                ),
                variable("Temperature (°C) →", { it.temperature }, { s, v -> s.copy(temperature = v) }, optional = true),
                variable("Condition →", { it.condition }, { s, v -> s.copy(condition = v) }, optional = true),
                info("Uses the free Open-Meteo API (no key)."),
            ),
            needsNetwork = true,
        ),
        action<A.DownloadFile>(
            Category.NETWORK, "⬇️", "Download file", "Download a URL to a file on the device",
            create = { A.DownloadFile() },
            summary = { "Download ${it.url.quoted()}" },
            fields = listOf(
                text("URL", { it.url }, { s, v -> s.copy(url = v) }),
                text("Save to", { it.destination }, { s, v -> s.copy(destination = v) }, magicText = false),
                variable("Store saved path in", { it.savedPath }, { s, v -> s.copy(savedPath = v) }, optional = true),
                number("Timeout", 5..600, { it.timeoutSeconds }, { s, v -> s.copy(timeoutSeconds = v) }, "s"),
            ),
            requirements = { setOf(Requirement.ALL_FILES) },
            needsNetwork = true,
        ),
    )

    private val uiAutomation: List<ActionDef<*>> = listOf(
        action<A.PressButton>(
            Category.UI_AUTOMATION, "⬅️", "Press button", "Back, Home, Recents, screenshot and other global actions",
            create = { A.PressButton() },
            summary = { humanize(it.button) },
            fields = listOf(choice("Button", UiButton.entries, { it.button }, { s, v -> s.copy(button = v) })),
            requirements = uiReq,
        ),
        action<A.TapScreen>(
            Category.UI_AUTOMATION, "👆", "Tap screen", "Tap or long-press at screen coordinates",
            create = { A.TapScreen() },
            summary = { "${if (it.longPress) "Long-press" else "Tap"} (${it.x}, ${it.y})" },
            fields = listOf(
                number("X", 0..4000, { it.x }, { s, v -> s.copy(x = v) }),
                number("Y", 0..4000, { it.y }, { s, v -> s.copy(y = v) }),
                switch("Long press", { it.longPress }, { s, v -> s.copy(longPress = v) }),
            ),
            requirements = uiReq,
        ),
        action<A.TapElement>(
            Category.UI_AUTOMATION, "🔤", "Tap on text", "Find an element by its text and tap it",
            create = { A.TapElement() },
            summary = { "${if (it.longPress) "Long-press" else "Tap"} element ${it.text.quoted()}" },
            fields = listOf(
                text("Element text contains", { it.text }, { s, v -> s.copy(text = v) }),
                switch("Long press", { it.longPress }, { s, v -> s.copy(longPress = v) }),
            ),
            requirements = uiReq,
        ),
        action<A.Swipe>(
            Category.UI_AUTOMATION, "↕️", "Swipe", "Swipe or drag between two points",
            create = { A.Swipe() },
            summary = { "Swipe (${it.startX},${it.startY}) → (${it.endX},${it.endY})" },
            fields = listOf(
                number("Start X", 0..4000, { it.startX }, { s, v -> s.copy(startX = v) }),
                number("Start Y", 0..4000, { it.startY }, { s, v -> s.copy(startY = v) }),
                number("End X", 0..4000, { it.endX }, { s, v -> s.copy(endX = v) }),
                number("End Y", 0..4000, { it.endY }, { s, v -> s.copy(endY = v) }),
                number("Duration", 50..3000, { it.durationMs }, { s, v -> s.copy(durationMs = v) }, "ms"),
            ),
            requirements = uiReq,
        ),
        action<A.InputText>(
            Category.UI_AUTOMATION, "⌨️", "Input text", "Type text into the focused field",
            create = { A.InputText() },
            summary = { "Type ${it.text.quoted()}" },
            fields = listOf(text("Text", { it.text }, { s, v -> s.copy(text = v) }, multiline = true)),
            requirements = uiReq,
        ),
        action<A.ReadScreen>(
            Category.UI_AUTOMATION, "👁️", "Read screen", "Read all visible text into a variable",
            create = { A.ReadScreen() },
            summary = { "Read screen → ${it.target.display()}" },
            fields = listOf(variable("Store in", { it.target }, { s, v -> s.copy(target = v) })),
            requirements = uiReq,
        ),
    )

    private val notificationsAccess: List<ActionDef<*>> = listOf(
        action<A.ClearNotifications>(
            Category.NOTIFICATIONS, "🧹", "Clear notifications", "Dismiss notifications from an app, or all of them",
            create = { A.ClearNotifications() },
            summary = { if (it.packageName.isBlank()) "Clear all notifications" else "Clear ${it.packageName} notifications" },
            fields = listOf(app("App (blank = all)", { it.packageName }, { s, v -> s.copy(packageName = v) }, allowAny = true)),
            requirements = { setOf(Requirement.NOTIFICATION_ACCESS) },
        ),
    )


    private val system: List<ActionDef<*>> = listOf(
        action<A.SetWifi>(
            Category.SYSTEM, "📶", "Set Wi-Fi", "Turn Wi-Fi on or off (needs root or ADB grant)",
            create = { A.SetWifi() },
            summary = { "Wi-Fi ${humanize(it.mode).lowercase()}" },
            fields = listOf(choice("Mode", SwitchMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = secureReq,
        ),
        action<A.SetMobileData>(
            Category.SYSTEM, "📡", "Set mobile data", "Turn mobile data on or off (needs root or ADB grant)",
            create = { A.SetMobileData() },
            summary = { "Mobile data ${humanize(it.mode).lowercase()}" },
            fields = listOf(choice("Mode", SwitchMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = secureReq,
        ),
        action<A.SetAirplaneMode>(
            Category.SYSTEM, "✈️", "Set airplane mode", "Turn airplane mode on or off (needs root or ADB grant)",
            create = { A.SetAirplaneMode() },
            summary = { "Airplane mode ${humanize(it.mode).lowercase()}" },
            fields = listOf(choice("Mode", SwitchMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = secureReq,
        ),
        action<A.SetNfc>(
            Category.SYSTEM, "📳", "Set NFC", "Turn NFC on or off (needs root)",
            create = { A.SetNfc() },
            summary = { "NFC ${humanize(it.mode).lowercase()}" },
            fields = listOf(choice("Mode", SwitchMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = secureReq,
        ),
        action<A.SetLocationMode>(
            Category.SYSTEM, "📍", "Set location mode", "Off / device-only / battery-saving / high accuracy",
            create = { A.SetLocationMode() },
            summary = { "Location mode: ${humanize(it.mode).lowercase()}" },
            fields = listOf(choice("Mode", dev.openmacro.core.model.LocationMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = secureReq,
        ),
        action<A.Reboot>(
            Category.SYSTEM, "🔁", "Reboot", "Reboot the device (needs root)",
            create = { A.Reboot() },
            summary = { "Reboot (${humanize(it.mode).lowercase()})" },
            fields = listOf(choice("Mode", RebootMode.entries, { it.mode }, { s, v -> s.copy(mode = v) })),
            requirements = secureReq,
        ),
        action<A.InputKeyEvent>(
            Category.SYSTEM, "⌨️", "Input key event", "Inject a hardware/media key (needs Shizuku or root)",
            create = { A.InputKeyEvent() },
            summary = { "Key event ${it.keyCode}" },
            fields = listOf(
                number("Key code", 0..300, { it.keyCode }, { s, v -> s.copy(keyCode = v) }),
                info("Android KEYCODE_* number, e.g. 26 = power, 24/25 = volume up/down, 85 = play/pause."),
            ),
            requirements = secureReq,
        ),
        action<A.WriteSetting>(
            Category.SYSTEM, "⚙️", "Write setting", "Write a system / secure / global setting value",
            create = { A.WriteSetting() },
            summary = { "${humanize(it.namespace)}: ${it.key.orAny("(key)")} = ${it.value.quoted()}" },
            fields = listOf(
                choice("Namespace", SettingNamespace.entries, { it.namespace }, { s, v -> s.copy(namespace = v) }),
                text("Key", { it.key }, { s, v -> s.copy(key = v) }, magicText = false, hint = "e.g. screen_brightness"),
                text("Value", { it.value }, { s, v -> s.copy(value = v) }),
            ),
            requirements = secureReq,
        ),
    )
}

private fun Boolean.allAny(): String = if (this) "all" else "any"

private fun TextOp.usesArg1(): Boolean = this in setOf(TextOp.SUBSTRING, TextOp.REPLACE, TextOp.REGEX_REPLACE, TextOp.EXTRACT_REGEX)

private fun TextOp.usesArg2(): Boolean = this in setOf(TextOp.SUBSTRING, TextOp.REPLACE, TextOp.REGEX_REPLACE)

private fun HttpMethod.hasBody(): Boolean = this in setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)

private val uiReq: (A) -> Set<Requirement> = { setOf(Requirement.ACCESSIBILITY) }

private val secureReq: (A) -> Set<Requirement> = { setOf(Requirement.SECURE_SETTINGS) }
