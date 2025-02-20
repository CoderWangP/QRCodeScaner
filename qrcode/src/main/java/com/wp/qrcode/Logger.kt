package com.wp.qrcode

import android.text.TextUtils
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Created by wp on 2018/6/21.
 * Log工具类
 */
class Logger private constructor() {
    enum class LogLevelDef(level:Int) {
        V(0),
        D(1),
        I(2),
        W(3),
        E(4),
        A(5)
    }

    companion object {
        private const val sTag = "android_base"

        //Logcat对单个日志输出限制为 4 * 1024个字符
        private const val MAX_LEN = 4000
        private const val TOP_BORDER = "╔═══════════════════════════════════════════════════════════════════════════════════════════════════"
        private const val LEFT_BORDER = "║ "
        private const val BOTTOM_BORDER = "╚═══════════════════════════════════════════════════════════════════════════════════════════════════"
        private val LINE_SEPARATOR = System.getProperty("line.separator")
        private const val NULL_TIPS = "Log with null object."
        private const val NULL = "null"
        private const val ARGS = "args"
        private val ENABLE_LOG = BuildConfig.DEBUG
        fun v(contents: Any?) {
            log(LogLevelDef.V, sTag, contents!!)
        }

        fun v(tag: String, vararg contents: Any?) {
            log(LogLevelDef.V, tag, *contents)
        }

        fun d(contents: Any?) {
            log(LogLevelDef.D, sTag, contents!!)
        }

        fun d(tag: String, vararg contents: Any?) {
            log(LogLevelDef.D, tag, *contents)
        }

        fun i(contents: Any?) {
            log(LogLevelDef.I, sTag, contents!!)
        }

        fun i(tag: String, vararg contents: Any?) {
            log(LogLevelDef.I, tag, *contents)
        }

        fun w(contents: Any?) {
            log(LogLevelDef.W, sTag, contents!!)
        }

        fun w(tag: String, vararg contents: Any?) {
            log(LogLevelDef.W, tag, *contents)
        }

        fun e(contents: Any?) {
            log(LogLevelDef.E, sTag, contents!!)
        }

        fun e(tag: String, vararg contents: Any?) {
            log(LogLevelDef.E, tag, *contents)
        }

        fun a(contents: Any?) {
            log(LogLevelDef.A, sTag, contents!!)
        }

        fun a(tag: String, vararg contents: Any?) {
            log(LogLevelDef.A, tag, *contents)
        }

        private fun log(logLevel: LogLevelDef, tag: String, vararg contents: Any?) {
            var tag = tag
            if (!ENABLE_LOG) {
                return
            }
            val logContents = createLogContents(tag, *contents)
            tag = logContents[0]
            val msg = logContents[1]
            printLog(logLevel, tag, msg)
        }

        fun json(tag: String, json: String) {
            printLog(LogLevelDef.D, tag, json)
        }

        fun json(logLevel: LogLevelDef, tag: String, json: String) {
            var json = json
            if (!ENABLE_LOG) {
                return
            }
            if (TextUtils.isEmpty(json)) {
                return
            }
            try {
                if (json.startsWith("{")) {
                    val jsonObject = JSONObject(json)
                    json = jsonObject.toString(4)
                } else if (json.startsWith("[")) {
                    val jsonArray = JSONArray(json)
                    json = jsonArray.toString(4)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            printLog(logLevel, tag, json)
        }

        private fun printLog(logLevel: LogLevelDef, tag: String, msg: String) {
            printBorder(logLevel, tag, true)
            val len = msg.length
            val countOfSub = len / MAX_LEN
            if (countOfSub > 0) {
                var index = 0
                var sub: String
                for (i in 0 until countOfSub) {
                    sub = msg.substring(index, index + MAX_LEN)
                    printSubLog(logLevel, tag, sub)
                    index += MAX_LEN
                }
                printSubLog(logLevel, tag, msg.substring(index, len))
            } else {
                printSubLog(logLevel, tag, msg)
            }
            printBorder(logLevel, tag, false)
        }

        private fun printSubLog(logLevel: LogLevelDef, tag: String, msg: String) {
            var msg = msg
            msg = LEFT_BORDER + msg
            when (logLevel) {
                LogLevelDef.V -> Log.v(tag, msg)
                LogLevelDef.D -> Log.d(tag, msg)
                LogLevelDef.I -> Log.i(tag, msg)
                LogLevelDef.W -> Log.w(tag, msg)
                LogLevelDef.E -> Log.e(tag, msg)
                LogLevelDef.A -> Log.wtf(tag, msg)
            }
        }

        private fun printBorder(logLevel: LogLevelDef, tag: String, isTop: Boolean) {
            val border = if (isTop) TOP_BORDER else BOTTOM_BORDER
            when (logLevel) {
                LogLevelDef.V -> Log.v(tag, border)
                LogLevelDef.D -> Log.d(tag, border)
                LogLevelDef.I -> Log.i(tag, border)
                LogLevelDef.W -> Log.w(tag, border)
                LogLevelDef.E -> Log.e(tag, border)
                LogLevelDef.A -> Log.wtf(tag, border)
            }
        }

        private fun createLogContents(tag: String, vararg contents: Any?): Array<String> {
            var tag = tag
            val targetElement = targetStackTraceElement
            val className = targetElement?.fileName
            val methodName = targetElement?.methodName
            val lineNumber = targetElement?.lineNumber
            val header = Formatter()
                    .format("%s(%s:%d)$LINE_SEPARATOR",
                            methodName,
                            className,
                            lineNumber)
                    .toString()
            if (TextUtils.isEmpty(sTag)) {
                if (TextUtils.isEmpty(tag)) {
                    tag = className ?: sTag
                }
            } else {
                if (TextUtils.isEmpty(tag)) {
                    tag = sTag
                }
            }
            var msg = NULL_TIPS
            if (contents != null) {
                val sb = StringBuilder()
                if (contents.size == 1) {
                    val `object` = contents[0]
                    sb.append(`object`?.toString() ?: NULL)
                    sb.append(LINE_SEPARATOR)
                } else {
                    var i = 0
                    val len = contents.size
                    while (i < len) {
                        val content = contents[i]
                        sb.append(ARGS)
                                .append("[")
                                .append(i)
                                .append("]")
                                .append(" = ")
                                .append(content?.toString() ?: NULL)
                                .append(LINE_SEPARATOR)
                        ++i
                    }
                }
                msg = sb.toString()
            }
            val sb = StringBuilder()
            val lines = msg.split(LINE_SEPARATOR!!).toTypedArray()
            for (line in lines) {
                sb.append(LEFT_BORDER).append(line).append(LINE_SEPARATOR)
            }
            msg = sb.toString()
            return arrayOf(tag, header + msg)
        }

        // find the target invoked method
        private val targetStackTraceElement: StackTraceElement?
            get() {
                // find the target invoked method
                var targetStackTrace: StackTraceElement? = null
                var shouldTrace = false
                val stackTrace = Thread.currentThread().stackTrace
                for (stackTraceElement in stackTrace) {
                    val isLogMethod = stackTraceElement.className == Logger::class.java.name ||
                            stackTraceElement.className.startsWith("java.lang")
                    if (shouldTrace && !isLogMethod) {
                        targetStackTrace = stackTraceElement
                        break
                    }
                    shouldTrace = isLogMethod
                }
                return targetStackTrace
            }
    }

    init {
        throw UnsupportedOperationException("cannot be instantiated")
    }
}