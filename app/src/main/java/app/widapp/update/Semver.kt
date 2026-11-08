package app.widapp.update

/** Minimal semantic-version comparison for release tags like "v1.4.0" or "1.5.0-beta.2". */
data class Semver(val major: Int, val minor: Int, val patch: Int, val pre: String?) : Comparable<Semver> {
    override fun compareTo(other: Semver): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        // A release (no suffix) is newer than any of its pre-releases.
        return when {
            pre == null && other.pre == null -> 0
            pre == null -> 1
            other.pre == null -> -1
            else -> comparePre(pre, other.pre)
        }
    }

    private fun comparePre(a: String, b: String): Int {
        val pa = a.split('.'); val pb = b.split('.')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: return -1
            val y = pb.getOrNull(i) ?: return 1
            val xi = x.toIntOrNull(); val yi = y.toIntOrNull()
            val c = if (xi != null && yi != null) xi.compareTo(yi) else x.compareTo(y)
            if (c != 0) return c
        }
        return 0
    }

    override fun toString() = "$major.$minor.$patch" + (pre?.let { "-$it" } ?: "")

    companion object {
        private val re = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:[-+]([0-9A-Za-z.\-]+))?""")
        fun parse(s: String?): Semver? {
            val m = re.find(s?.trim() ?: return null) ?: return null
            return Semver(
                m.groupValues[1].toInt(),
                m.groupValues[2].ifEmpty { "0" }.toInt(),
                m.groupValues[3].ifEmpty { "0" }.toInt(),
                m.groupValues[4].ifEmpty { null },
            )
        }
    }
}
