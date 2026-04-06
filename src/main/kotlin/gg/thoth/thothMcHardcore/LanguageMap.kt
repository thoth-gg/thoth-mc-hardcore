package gg.thoth.thothMcHardcore

class LanguageMap(private val values: Map<String, String>) {

    fun format(key: String, vararg args: String): String {
        val template = values[key] ?: return key
        var result = template

        args.forEachIndexed { index, arg ->
            result = result.replace("%${index + 1}\$s", arg)
        }

        var sequentialIndex = 0
        while ("%s" in result && sequentialIndex < args.size) {
            result = result.replaceFirst("%s", args[sequentialIndex])
            sequentialIndex++
        }

        return result.replace("%%", "%")
    }
}
