package dev.openmacro.core.engine.perform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import dev.openmacro.core.model.ActionSpec as A
import dev.openmacro.core.model.FileOp
import dev.openmacro.core.model.TextOp
import dev.openmacro.core.model.VarType
import dev.openmacro.core.model.text.Expression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Variables, text, clipboard, files, shell and macro-control actions. */
internal class DataActions(private val app: Context) {

    suspend fun perform(spec: A, ctx: ActionContext): ActionOutcome? = when (spec) {
        is A.SetVariable -> setVariable(spec, ctx)
        is A.ClearVariable -> { ctx.clearVariable(spec.variable); ActionOutcome.Success }
        is A.IncrementVariable -> {
            val current = ctx.variable(spec.variable)?.asNumber() ?: 0.0
            val delta = Expression.evaluateOrNull(ctx.render(spec.amount)) ?: 0.0
            val sum = current + delta
            ctx.setVariableValue(spec.variable, if (sum == Math.floor(sum) && !sum.isInfinite()) sum.toLong().toString() else sum.toString())
            ActionOutcome.Success
        }
        is A.AppendVariable -> {
            val current = ctx.variable(spec.variable)?.value ?: ""
            val addition = ctx.render(spec.text)
            val sep = if (current.isNotEmpty() && spec.separator.isNotEmpty()) ctx.render(spec.separator) else ""
            ctx.setVariableValue(spec.variable, current + sep + addition)
            ActionOutcome.Success
        }
        is A.TextManipulation -> textManipulation(spec, ctx)
        is A.Log -> { ctx.log(ctx.render(spec.text)); ActionOutcome.Success }
        is A.SetClipboard -> setClipboard(ctx.render(spec.text))
        is A.ClearClipboard -> clearClipboard()
        is A.EncodeText -> encodeText(spec, ctx)
        is A.HashText -> hashText(spec, ctx)
        is A.GenerateValue -> generateValue(spec, ctx)
        is A.ParseJson -> parseJson(spec, ctx)
        is A.FormatDateTime -> formatDateTime(spec, ctx)
        is A.WriteFile -> writeFile(spec, ctx)
        is A.ReadFile -> readFile(spec, ctx)
        is A.FileOperation -> fileOperation(spec, ctx)
        is A.RunMacro -> { ctx.engine.runMacro(spec.macro, ctx.scope); ActionOutcome.Success }
        is A.EnableMacro -> { ctx.engine.setMacroEnabled(spec.macro, spec.mode); ActionOutcome.Success }
        is A.CancelMacro -> { ctx.engine.cancelMacro(spec.macro); ActionOutcome.Success }
        is A.DisableThisMacro -> {
            ctx.engine.setMacroEnabled(dev.openmacro.core.model.MacroRef(ctx.scope.macroId, ctx.scope.macroName), dev.openmacro.core.model.SwitchMode.OFF)
            ActionOutcome.Success
        }
        is A.SetEngine -> { ctx.engine.setEngineEnabled(spec.enabled); ActionOutcome.Success }
        is A.ShellCommand -> shell(spec, ctx)
        else -> null
    }

    private suspend fun setVariable(spec: A.SetVariable, ctx: ActionContext): ActionOutcome {
        if (spec.variable.name.isBlank()) return ActionOutcome.Failed("No variable selected")
        val rendered = ctx.render(spec.value)
        val value = when (spec.type) {
            VarType.STRING -> rendered
            VarType.BOOLEAN -> if (rendered.trim().equals("toggle", true)) {
                (!(ctx.variable(spec.variable)?.asBoolean() ?: false)).toString()
            } else {
                rendered
            }
            VarType.INTEGER, VarType.DECIMAL -> Expression.evaluateOrNull(rendered)?.let {
                if (spec.type == VarType.INTEGER) it.toLong().toString() else it.toString()
            } ?: rendered
        }
        ctx.setVariable(spec.variable, spec.type, value)
        return ActionOutcome.Success
    }

    private suspend fun textManipulation(spec: A.TextManipulation, ctx: ActionContext): ActionOutcome {
        val input = ctx.render(spec.input)
        val a1 = ctx.render(spec.arg1)
        val a2 = ctx.render(spec.arg2)
        val result = when (spec.op) {
            TextOp.UPPERCASE -> input.uppercase()
            TextOp.LOWERCASE -> input.lowercase()
            TextOp.TRIM -> input.trim()
            TextOp.LENGTH -> input.length.toString()
            TextOp.SUBSTRING -> {
                val start = a1.toIntOrNull()?.coerceIn(0, input.length) ?: 0
                val end = a2.toIntOrNull()?.coerceIn(start, input.length) ?: input.length
                input.substring(start, end)
            }
            TextOp.REPLACE -> input.replace(a1, a2)
            TextOp.REGEX_REPLACE -> runCatching { input.replace(Regex(a1), a2) }.getOrElse { return ActionOutcome.Failed("Bad regex") }
            TextOp.EXTRACT_REGEX -> runCatching { Regex(a1).find(input)?.let { m -> m.groupValues.getOrNull(1) ?: m.value } ?: "" }
                .getOrElse { return ActionOutcome.Failed("Bad regex") }
        }
        ctx.setVariableValue(spec.target, result)
        return ActionOutcome.Success
    }

    private suspend fun encodeText(spec: A.EncodeText, ctx: ActionContext): ActionOutcome {
        val input = ctx.render(spec.input)
        val result = try {
            when (spec.mode) {
                dev.openmacro.core.model.EncodeMode.BASE64_ENCODE ->
                    android.util.Base64.encodeToString(input.toByteArray(), android.util.Base64.NO_WRAP)
                dev.openmacro.core.model.EncodeMode.BASE64_DECODE ->
                    String(android.util.Base64.decode(input, android.util.Base64.DEFAULT))
                dev.openmacro.core.model.EncodeMode.URL_ENCODE -> java.net.URLEncoder.encode(input, "UTF-8")
                dev.openmacro.core.model.EncodeMode.URL_DECODE -> java.net.URLDecoder.decode(input, "UTF-8")
            }
        } catch (e: Exception) {
            return ActionOutcome.Failed("Invalid input for ${spec.mode}")
        }
        ctx.setVariableValue(spec.target, result)
        return ActionOutcome.Success
    }

    private suspend fun hashText(spec: A.HashText, ctx: ActionContext): ActionOutcome {
        val algo = when (spec.algorithm) {
            dev.openmacro.core.model.HashAlgorithm.MD5 -> "MD5"
            dev.openmacro.core.model.HashAlgorithm.SHA1 -> "SHA-1"
            dev.openmacro.core.model.HashAlgorithm.SHA256 -> "SHA-256"
        }
        val bytes = java.security.MessageDigest.getInstance(algo).digest(ctx.render(spec.input).toByteArray())
        ctx.setVariableValue(spec.target, bytes.joinToString("") { "%02x".format(it) })
        return ActionOutcome.Success
    }

    private suspend fun generateValue(spec: A.GenerateValue, ctx: ActionContext): ActionOutcome {
        val value = when (spec.kind) {
            dev.openmacro.core.model.GenerateKind.UUID -> java.util.UUID.randomUUID().toString()
            dev.openmacro.core.model.GenerateKind.TIMESTAMP -> System.currentTimeMillis().toString()
            dev.openmacro.core.model.GenerateKind.RANDOM_NUMBER -> {
                val lo = minOf(spec.min, spec.max)
                val hi = maxOf(spec.min, spec.max)
                (lo..hi).random().toString()
            }
        }
        ctx.setVariableValue(spec.target, value)
        return ActionOutcome.Success
    }

    private suspend fun parseJson(spec: A.ParseJson, ctx: ActionContext): ActionOutcome {
        return try {
            var node: Any = org.json.JSONTokener(ctx.render(spec.json)).nextValue()
            for (segment in spec.path.split('.').filter { it.isNotBlank() }) {
                node = when {
                    node is org.json.JSONObject -> node.get(segment)
                    node is org.json.JSONArray -> node.get(segment.toInt())
                    else -> return ActionOutcome.Failed("Path '${spec.path}' not found")
                }
            }
            ctx.setVariableValue(spec.target, node.toString())
            ActionOutcome.Success
        } catch (e: Exception) {
            ActionOutcome.Failed("Could not parse JSON or path")
        }
    }

    private suspend fun formatDateTime(spec: A.FormatDateTime, ctx: ActionContext): ActionOutcome {
        val raw = ctx.render(spec.source).trim()
        val millis = when {
            raw.isBlank() || raw.equals("now", ignoreCase = true) -> System.currentTimeMillis()
            else -> {
                val n = raw.toLongOrNull() ?: return ActionOutcome.Failed("Source isn't 'now' or an epoch value")
                // Heuristic: 10-digit values are epoch seconds, otherwise milliseconds.
                if (raw.length <= 11) n * 1000 else n
            }
        }
        return try {
            val formatted = java.text.SimpleDateFormat(spec.pattern, java.util.Locale.getDefault()).format(java.util.Date(millis))
            ctx.setVariableValue(spec.target, formatted)
            ActionOutcome.Success
        } catch (e: IllegalArgumentException) {
            ActionOutcome.Failed("Invalid date pattern")
        }
    }

    private fun setClipboard(text: String): ActionOutcome {
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("OpenMacro", text))
        return ActionOutcome.Success
    }

    private fun clearClipboard(): ActionOutcome {
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        return ActionOutcome.Success
    }

    private suspend fun writeFile(spec: A.WriteFile, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        try {
            val file = File(ctx.render(spec.path))
            file.parentFile?.mkdirs()
            val text = ctx.render(spec.text)
            if (spec.append) file.appendText(text + "\n") else file.writeText(text)
            ActionOutcome.Success
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Write failed")
        }
    }

    private suspend fun readFile(spec: A.ReadFile, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        try {
            val file = File(ctx.render(spec.path))
            if (!file.exists()) return@withContext ActionOutcome.Failed("File not found")
            ctx.setVariableValue(spec.target, file.readText())
            ActionOutcome.Success
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "Read failed")
        }
    }

    private suspend fun fileOperation(spec: A.FileOperation, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        try {
            val source = File(ctx.render(spec.source))
            val dest = File(ctx.render(spec.destination))
            when (spec.op) {
                FileOp.COPY -> { source.copyTo(dest, overwrite = true); ActionOutcome.Success }
                FileOp.MOVE -> { source.copyTo(dest, overwrite = true); source.delete(); ActionOutcome.Success }
                FileOp.RENAME -> if (source.renameTo(dest)) ActionOutcome.Success else ActionOutcome.Failed("Rename failed")
                FileOp.DELETE -> if (source.deleteRecursively()) ActionOutcome.Success else ActionOutcome.Failed("Delete failed")
                FileOp.CREATE_FOLDER -> if (dest.mkdirs() || dest.isDirectory) ActionOutcome.Success else ActionOutcome.Failed("Create failed")
            }
        } catch (e: Exception) {
            ActionOutcome.Failed(e.message ?: "File operation failed")
        }
    }

    private suspend fun shell(spec: A.ShellCommand, ctx: ActionContext): ActionOutcome = withContext(Dispatchers.IO) {
        val command = ctx.render(spec.command)
        if (command.isBlank()) return@withContext ActionOutcome.Failed("Empty command")
        try {
            val process = if (spec.root) {
                ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            } else {
                ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            }
            val output = withTimeoutOrNull(spec.timeoutSeconds.coerceIn(1, 600) * 1000L) {
                process.inputStream.bufferedReader().readText()
            }
            if (output == null) {
                process.destroy()
                return@withContext ActionOutcome.Failed("Command timed out")
            }
            process.waitFor()
            if (spec.output.name.isNotBlank()) ctx.setVariableValue(spec.output, output.trim())
            ActionOutcome.Success
        } catch (e: Exception) {
            ActionOutcome.Failed(if (spec.root) "Root not available" else (e.message ?: "Command failed"))
        }
    }
}
