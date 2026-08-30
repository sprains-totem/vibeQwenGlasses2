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
        // 官方 APP 帧头剥离：帧 = 10B 头 + 载荷，[0..1] LE = 总长-2
        // 若开头 10 字节长度字段合理且第 3 字节=0x01，跳过帧头（真正消费这 10 字节）
        if (n - i >= 10) {
            val declared = (pending[i].toInt() and 0xFF or ((pending[i + 1].toInt() and 0xFF) shl 8)) + 2
            val third = pending[i + 2].toInt() and 0xFF
            if (declared in 12..64 * 1024 && (third == 1 || third == 2 || third == 4)) {
                pending.subList(i, i + 10).clear()
                i = 0
            }
        }
        val n2 = pending.size
        if (i >= n2) {
            pending.clear()
            return
        }
        i = 0
        val first = pending[i].toInt().toChar()
        if (first == '{') {
            // 括号配对扫描（处理字符串转义内花括号）
            var depth = 0
            var j = i
            var inString = false
            var escaped = false
            while (j < n2) {
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
            if (j < n2 && depth == 0) {
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
        val bytes = ByteArray(n2 - i) { pending[i + it] }
        pending.clear()
        onNonJson(bytes)
    }
}