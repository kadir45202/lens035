package com.lens035.core.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/** Body joints used by the pose guide. "Left"/"right" are the person's own sides. */
enum class Joint {
    NOSE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE;

    /** The same joint on the other side of the body. */
    val mirror: Joint
        get() = when (this) {
            NOSE -> NOSE
            LEFT_SHOULDER -> RIGHT_SHOULDER
            RIGHT_SHOULDER -> LEFT_SHOULDER
            LEFT_ELBOW -> RIGHT_ELBOW
            RIGHT_ELBOW -> LEFT_ELBOW
            LEFT_WRIST -> RIGHT_WRIST
            RIGHT_WRIST -> LEFT_WRIST
            LEFT_HIP -> RIGHT_HIP
            RIGHT_HIP -> LEFT_HIP
            LEFT_KNEE -> RIGHT_KNEE
            RIGHT_KNEE -> LEFT_KNEE
            LEFT_ANKLE -> RIGHT_ANKLE
            RIGHT_ANKLE -> LEFT_ANKLE
        }
}

/** A joint position in normalised frame coordinates (x right, y down) and its confidence. */
data class JointPoint(val x: Float, val y: Float, val confidence: Float = 1f)

/** Bones drawn by the overlay and compared by the matcher. */
enum class Limb(val a: Joint, val b: Joint, val weight: Float, val label: String) {
    LEFT_UPPER_ARM(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, 1.0f, "kol"),
    LEFT_FOREARM(Joint.LEFT_ELBOW, Joint.LEFT_WRIST, 0.9f, "ön kol"),
    RIGHT_UPPER_ARM(Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW, 1.0f, "kol"),
    RIGHT_FOREARM(Joint.RIGHT_ELBOW, Joint.RIGHT_WRIST, 0.9f, "ön kol"),
    LEFT_THIGH(Joint.LEFT_HIP, Joint.LEFT_KNEE, 0.8f, "bacak"),
    LEFT_SHIN(Joint.LEFT_KNEE, Joint.LEFT_ANKLE, 0.7f, "alt bacak"),
    RIGHT_THIGH(Joint.RIGHT_HIP, Joint.RIGHT_KNEE, 0.8f, "bacak"),
    RIGHT_SHIN(Joint.RIGHT_KNEE, Joint.RIGHT_ANKLE, 0.7f, "alt bacak"),
    SHOULDERS(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER, 0.6f, "omuzlar"),
    HIPS(Joint.LEFT_HIP, Joint.RIGHT_HIP, 0.5f, "kalça"),
    LEFT_TORSO(Joint.LEFT_HIP, Joint.LEFT_SHOULDER, 0.8f, "gövde"),
    RIGHT_TORSO(Joint.RIGHT_HIP, Joint.RIGHT_SHOULDER, 0.8f, "gövde");

    val mirror: Limb
        get() = entries.first { it.a == a.mirror && it.b == b.mirror || it.a == b.mirror && it.b == a.mirror }
}

/** One person's pose. */
data class PoseSkeleton(val joints: Map<Joint, JointPoint>) {
    operator fun get(j: Joint): JointPoint? = joints[j]

    fun visible(j: Joint, minConfidence: Float = 0.5f): JointPoint? = joints[j]?.takeIf { it.confidence >= minConfidence }

    /** Mirror horizontally inside the frame and swap body sides, so the pose reads the same. */
    fun mirrored(): PoseSkeleton = PoseSkeleton(joints.entries.associate { (j, p) -> j.mirror to p.copy(x = 1f - p.x) })

    /** Maps every point through an affine placement: x' = x * sx + tx, y' = y * sy + ty. */
    fun transformed(sx: Float, sy: Float, tx: Float, ty: Float): PoseSkeleton =
        PoseSkeleton(joints.mapValues { (_, p) -> p.copy(x = p.x * sx + tx, y = p.y * sy + ty) })

    /** Bounding box (minX, minY, maxX, maxY) of all joints. */
    fun bounds(): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in joints.values) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }
}

/** A built-in pose template. Coordinates are for a 3:4 portrait frame. */
data class PoseTemplate(
    val id: String,
    val name: String,
    val category: String,
    val tip: String,
    val skeleton: PoseSkeleton,
)

/** Result of comparing a live pose to the reference. */
data class PoseMatch(
    /** 0..1 overall similarity, or null when too little of the body is visible. */
    val score: Float?,
    /** Short instructions for the photographer to relay, most important first. */
    val hints: List<String>,
    /** Per-limb similarity 0..1 for the limbs that could be compared (in reference orientation). */
    val limbScores: Map<Limb, Float>,
)

object PoseMatcher {
    private const val MIN_LIMBS = 5

    /**
     * Compares [live] with [reference]. Both are in the same normalised view space; [aspect] is
     * the view's width / height so that angles are measured in real proportions.
     * The comparison also tries the mirrored reference and keeps whichever fits better, so the
     * result is right for front (mirrored) and back cameras alike.
     */
    fun match(reference: PoseSkeleton, live: PoseSkeleton, aspect: Float): PoseMatch {
        val direct = compare(reference, live, aspect)
        val mirrored = compare(reference.mirrored(), live, aspect)
        val best = if ((mirrored.score ?: -1f) > (direct.score ?: -1f)) mirrored else direct
        return best
    }

    private fun compare(ref: PoseSkeleton, live: PoseSkeleton, aspect: Float): PoseMatch {
        val scores = LinkedHashMap<Limb, Float>()
        var weighted = 0f
        var weights = 0f
        for (limb in Limb.entries) {
            val ra = ref.visible(limb.a, 0.3f) ?: continue
            val rb = ref.visible(limb.b, 0.3f) ?: continue
            val la = live.visible(limb.a) ?: continue
            val lb = live.visible(limb.b) ?: continue
            val angle = angleBetween(
                (rb.x - ra.x) * aspect, rb.y - ra.y,
                (lb.x - la.x) * aspect, lb.y - la.y,
            )
            val s = (1f - angle / 55f).coerceIn(0f, 1f)
            scores[limb] = s
            weighted += s * limb.weight
            weights += limb.weight
        }
        val comparableInReference = Limb.entries.count { ref.visible(it.a, 0.3f) != null && ref.visible(it.b, 0.3f) != null }
        val required = minOf(MIN_LIMBS, maxOf(2, comparableInReference))
        if (scores.size < required || weights <= 0f) {
            return PoseMatch(null, listOf("Kişinin tüm vücudu kadrajda görünsün"), scores)
        }
        val hints = ArrayList<String>()
        placementHint(ref, live, aspect)?.let { hints.add(it) }
        val worst = scores.entries.filter { it.value < 0.7f }.minByOrNull { it.value * it.key.weight }
        if (worst != null) limbHint(worst.key, ref, live, aspect)?.let { hints.add(it) }
        return PoseMatch(weighted / weights, hints, scores)
    }

    private fun placementHint(ref: PoseSkeleton, live: PoseSkeleton, aspect: Float): String? {
        val rc = torsoCenter(ref) ?: return null
        val lc = torsoCenter(live) ?: return null
        val rl = torsoLength(ref, aspect) ?: return null
        val ll = torsoLength(live, aspect) ?: return null
        val ratio = ll / max(rl, 1e-4f)
        if (ratio < 0.82f) return "Biraz yaklaşın"
        if (ratio > 1.22f) return "Biraz uzaklaşın"
        val dx = lc[0] - rc[0]
        if (dx > 0.07f) return "Kişi kadrajın soluna kaysın"
        if (dx < -0.07f) return "Kişi kadrajın sağına kaysın"
        val dy = lc[1] - rc[1]
        if (dy > 0.08f) return "Kamerayı biraz aşağı eğin"
        if (dy < -0.08f) return "Kamerayı biraz yukarı kaldırın"
        return null
    }

    private fun limbHint(limb: Limb, ref: PoseSkeleton, live: PoseSkeleton, aspect: Float): String? {
        val ra = ref[limb.a] ?: return null
        val rb = ref[limb.b] ?: return null
        val la = live[limb.a] ?: return null
        val lb = live[limb.b] ?: return null
        // Which side of the screen the limb is on (from the photographer's point of view).
        val screenSide = if ((la.x + lb.x) / 2f < 0.5f) "Soldaki" else "Sağdaki"
        val refDy = rb.y - ra.y
        val liveDy = lb.y - la.y
        val refDx = (rb.x - ra.x) * aspect
        val liveDx = (lb.x - la.x) * aspect
        return when (limb) {
            Limb.SHOULDERS, Limb.HIPS -> if (abs(refDy - liveDy) > 0.03f) "Omuzları düz tutsun" else "Vücudunu kameraya biraz çevirsin"
            Limb.LEFT_TORSO, Limb.RIGHT_TORSO -> if (abs(refDx - liveDx) > 0.03f) "Gövdesini biraz yana eğsin" else "Dik dursun"
            else -> {
                val raise = liveDy - refDy > 0.04f // live limb points lower than the reference
                val lower = refDy - liveDy > 0.04f
                when {
                    raise -> "$screenSide ${limb.label} biraz yukarı"
                    lower -> "$screenSide ${limb.label} biraz aşağı"
                    abs(liveDx) < abs(refDx) -> "$screenSide ${limb.label} biraz dışa açılsın"
                    else -> "$screenSide ${limb.label} gövdeye yaklaşsın"
                }
            }
        }
    }

    private fun torsoCenter(p: PoseSkeleton): FloatArray? {
        val pts = listOf(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER, Joint.LEFT_HIP, Joint.RIGHT_HIP).mapNotNull { p.visible(it, 0.3f) }
        if (pts.size < 3) return null
        return floatArrayOf(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
    }

    private fun torsoLength(p: PoseSkeleton, aspect: Float): Float? {
        val ls = p.visible(Joint.LEFT_SHOULDER, 0.3f)
        val rs = p.visible(Joint.RIGHT_SHOULDER, 0.3f)
        val lh = p.visible(Joint.LEFT_HIP, 0.3f)
        val rh = p.visible(Joint.RIGHT_HIP, 0.3f)
        if (ls == null || rs == null || lh == null || rh == null) return null
        val sx = (ls.x + rs.x) / 2f * aspect
        val sy = (ls.y + rs.y) / 2f
        val hx = (lh.x + rh.x) / 2f * aspect
        val hy = (lh.y + rh.y) / 2f
        return sqrt((sx - hx) * (sx - hx) + (sy - hy) * (sy - hy))
    }

    /** Angle in degrees between two 2D vectors. */
    fun angleBetween(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val la = sqrt(ax * ax + ay * ay)
        val lb = sqrt(bx * bx + by * by)
        if (la < 1e-6f || lb < 1e-6f) return 180f
        val c = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(c).toDouble()).toFloat()
    }
}
