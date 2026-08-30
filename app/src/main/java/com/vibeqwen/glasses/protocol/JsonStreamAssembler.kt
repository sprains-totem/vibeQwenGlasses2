package com.vibeqwen.glasses.protocol

/**
 * 控制通道 JSON 流解复用（纯 Kotlin，可单测）。
 *
 * 眼镜下行控制通道以 JSON 文本为主，但可能夹杂：
 * - 分段 JSON（长消息跨多次 read 到达）
 * - 非 JSON 残余（如 HFP AT 文本 / 可能的音频残余）
 *
 * 本组装器把字节流切成"完整 JSON 文本"与"非 JSON 字节块"两种输出，
 * 使上层可把 JSON 喂给事件解析、把非 JSON 字节喂给音频帧解析。
 */
class JsonStreamAssembler(
    private val onJson: (String) -> Unit,
    private val onNonJson: (ByteArray) -> Unit,
) {
    private val pending = ArrayList<Byte>()

    fun feed(data: ByteArray) {
        for (b in data) pending.add(b)
        drain()
    }

    private fun drain() {
        var i = 0
        val n = pending.size
        // 跳过前导空白
        while (i < n && pending[i].toInt().toChar().isWhitespace()) i++
        if (i >= n) {
            pending.clear()
            return
        }
        val first = pending[i].toInt().toChar()
        if (first == '{') {
            // 括号配对扫描（处理字符串转义内花括号）
            var depth = 0
            var j = i
            var inString = false
            var escaped = false
            while (j < n) {
                val c = pending[j].toInt().toChar()
                if (inString) {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') inString = false
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                }
                j++
            }
            if (j < n && depth == 0) {
                // 完整 JSON
                val bytes = ByteArray(j - i + 1) { pending[i + it] }
                onJson(String(bytes, Charsets.UTF_8))
                pending.subList(0, j + 1).clear()
                drain()
                return
            }
            // 不完整 JSON：等待后续字节；过大多余直接丢弃防止内存膨胀
            if (pending.size > 64 * 1024) pending.clear()
            return
        }
        // 非 JSON 开头：整段作为非 JSON 字节交给回调
        val bytes = ByteArray(n - i) { pending[i + it] }
        pending.clear()
        onNonJson(bytes)
    }
}