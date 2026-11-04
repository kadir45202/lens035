package com.lens035.core.pose

/**
 * Built-in pose guides, drawn as a body silhouette over the camera preview. Coordinates are
 * normalised to a 3:4 portrait frame; "left"/"right" joints are the subject's own sides, so a
 * subject facing the camera has their left side on the right of the frame.
 */
object PoseTemplates {

    private fun pose(
        nose: Pair<Float, Float>,
        ls: Pair<Float, Float>, rs: Pair<Float, Float>,
        le: Pair<Float, Float>, re: Pair<Float, Float>,
        lw: Pair<Float, Float>, rw: Pair<Float, Float>,
        lh: Pair<Float, Float>, rh: Pair<Float, Float>,
        lk: Pair<Float, Float>, rk: Pair<Float, Float>,
        la: Pair<Float, Float>, ra: Pair<Float, Float>,
    ): PoseSkeleton {
        // Joints outside the frame (e.g. legs in a close-up) are kept for drawing continuity but
        // marked invisible so the matcher ignores them.
        fun p(v: Pair<Float, Float>): JointPoint {
            val inFrame = v.first in -0.02f..1.02f && v.second in -0.02f..1.02f
            return JointPoint(v.first, v.second, if (inFrame) 1f else 0f)
        }
        return PoseSkeleton(
            mapOf(
                Joint.NOSE to p(nose),
                Joint.LEFT_SHOULDER to p(ls), Joint.RIGHT_SHOULDER to p(rs),
                Joint.LEFT_ELBOW to p(le), Joint.RIGHT_ELBOW to p(re),
                Joint.LEFT_WRIST to p(lw), Joint.RIGHT_WRIST to p(rw),
                Joint.LEFT_HIP to p(lh), Joint.RIGHT_HIP to p(rh),
                Joint.LEFT_KNEE to p(lk), Joint.RIGHT_KNEE to p(rk),
                Joint.LEFT_ANKLE to p(la), Joint.RIGHT_ANKLE to p(ra),
            ),
        )
    }

    private infix fun Float.at(y: Float) = Pair(this, y)

    val all: List<PoseTemplate> = listOf(
        PoseTemplate(
            "classic", "Klasik duruş", "Ayakta",
            "Ağırlığını bir bacağa versin, omuzlar rahat.",
            pose(
                nose = 0.50f at 0.15f,
                ls = 0.585f at 0.27f, rs = 0.415f at 0.27f,
                le = 0.615f at 0.41f, re = 0.385f at 0.41f,
                lw = 0.625f at 0.54f, rw = 0.375f at 0.54f,
                lh = 0.555f at 0.54f, rh = 0.445f at 0.54f,
                lk = 0.565f at 0.72f, rk = 0.445f at 0.72f,
                la = 0.575f at 0.90f, ra = 0.445f at 0.90f,
            ),
        ),
        PoseTemplate(
            "hand_hip", "Elde bel", "Ayakta",
            "Bir eli belinde, dirsek dışa; vücudu hafif yana çevrik.",
            pose(
                nose = 0.49f at 0.15f,
                ls = 0.58f at 0.27f, rs = 0.42f at 0.275f,
                le = 0.685f at 0.39f, re = 0.39f at 0.41f,
                lw = 0.585f at 0.52f, rw = 0.38f at 0.545f,
                lh = 0.555f at 0.54f, rh = 0.445f at 0.545f,
                lk = 0.55f at 0.72f, rk = 0.455f at 0.72f,
                la = 0.565f at 0.90f, ra = 0.43f at 0.895f,
            ),
        ),
        PoseTemplate(
            "arms_crossed", "Kollar bağlı", "Ayakta",
            "Kollarını göğsünde kavuştursun, çene hafif önde.",
            pose(
                nose = 0.50f at 0.15f,
                ls = 0.585f at 0.27f, rs = 0.415f at 0.27f,
                le = 0.61f at 0.40f, re = 0.39f at 0.40f,
                lw = 0.44f at 0.37f, rw = 0.56f at 0.365f,
                lh = 0.555f at 0.54f, rh = 0.445f at 0.54f,
                lk = 0.56f at 0.72f, rk = 0.44f at 0.72f,
                la = 0.565f at 0.90f, ra = 0.435f at 0.90f,
            ),
        ),
        PoseTemplate(
            "walk", "Yürüyüş", "Ayakta",
            "Kameraya doğru doğal adımlarla yürüsün, kollar serbest.",
            pose(
                nose = 0.50f at 0.15f,
                ls = 0.58f at 0.27f, rs = 0.42f at 0.27f,
                le = 0.60f at 0.40f, re = 0.375f at 0.39f,
                lw = 0.575f at 0.52f, rw = 0.37f at 0.50f,
                lh = 0.555f at 0.54f, rh = 0.445f at 0.54f,
                lk = 0.585f at 0.715f, rk = 0.44f at 0.73f,
                la = 0.62f at 0.89f, ra = 0.405f at 0.885f,
            ),
        ),
        PoseTemplate(
            "pockets", "Eller cepte", "Ayakta",
            "Elleri ceplerde, omuzlar düşük ve rahat.",
            pose(
                nose = 0.50f at 0.15f,
                ls = 0.585f at 0.27f, rs = 0.415f at 0.27f,
                le = 0.625f at 0.415f, re = 0.375f at 0.415f,
                lw = 0.575f at 0.55f, rw = 0.425f at 0.55f,
                lh = 0.555f at 0.54f, rh = 0.445f at 0.54f,
                lk = 0.565f at 0.72f, rk = 0.44f at 0.72f,
                la = 0.58f at 0.90f, ra = 0.43f at 0.90f,
            ),
        ),
        PoseTemplate(
            "hair_touch", "Saç düzeltme", "Ayakta",
            "Bir eli saçında, bakışı kameradan hafif uzakta.",
            pose(
                nose = 0.50f at 0.15f,
                ls = 0.585f at 0.27f, rs = 0.415f at 0.27f,
                le = 0.62f at 0.41f, re = 0.34f at 0.20f,
                lw = 0.625f at 0.54f, rw = 0.44f at 0.10f,
                lh = 0.555f at 0.54f, rh = 0.445f at 0.54f,
                lk = 0.565f at 0.72f, rk = 0.445f at 0.72f,
                la = 0.57f at 0.90f, ra = 0.44f at 0.90f,
            ),
        ),
        PoseTemplate(
            "over_shoulder", "Omuz üstü bakış", "Ayakta",
            "Sırtı hafif kameraya dönük, başını omzunun üstünden çevirsin.",
            pose(
                nose = 0.455f at 0.155f,
                ls = 0.555f at 0.28f, rs = 0.435f at 0.27f,
                le = 0.575f at 0.415f, re = 0.42f at 0.41f,
                lw = 0.585f at 0.54f, rw = 0.42f at 0.54f,
                lh = 0.54f at 0.545f, rh = 0.455f at 0.54f,
                lk = 0.545f at 0.72f, rk = 0.455f at 0.72f,
                la = 0.55f at 0.90f, ra = 0.46f at 0.90f,
            ),
        ),
        PoseTemplate(
            "lean", "Duvara yaslanma", "Ayakta",
            "Omzunu duvara yaslasın, bir ayağını duvara dayasın.",
            pose(
                nose = 0.47f at 0.155f,
                ls = 0.56f at 0.275f, rs = 0.40f at 0.265f,
                le = 0.60f at 0.41f, re = 0.37f at 0.40f,
                lw = 0.61f at 0.53f, rw = 0.40f at 0.52f,
                lh = 0.555f at 0.545f, rh = 0.45f at 0.54f,
                lk = 0.56f at 0.72f, rk = 0.40f at 0.69f,
                la = 0.57f at 0.90f, ra = 0.48f at 0.80f,
            ),
        ),
        PoseTemplate(
            "victory", "Zafer", "Eğlenceli",
            "Bir eliyle yüzünün yanında zafer işareti yapsın.",
            pose(
                nose = 0.50f at 0.15f,
                ls = 0.585f at 0.27f, rs = 0.415f at 0.27f,
                le = 0.62f at 0.41f, re = 0.355f at 0.31f,
                lw = 0.625f at 0.54f, rw = 0.395f at 0.17f,
                lh = 0.555f at 0.54f, rh = 0.445f at 0.54f,
                lk = 0.565f at 0.72f, rk = 0.445f at 0.72f,
                la = 0.575f at 0.90f, ra = 0.445f at 0.90f,
            ),
        ),
        PoseTemplate(
            "arms_open", "Kollar açık", "Eğlenceli",
            "Kollarını iki yana açsın, gülümsesin.",
            pose(
                nose = 0.50f at 0.15f,
                ls = 0.585f at 0.27f, rs = 0.415f at 0.27f,
                le = 0.70f at 0.26f, re = 0.30f at 0.26f,
                lw = 0.80f at 0.22f, rw = 0.20f at 0.22f,
                lh = 0.555f at 0.54f, rh = 0.445f at 0.54f,
                lk = 0.575f at 0.72f, rk = 0.425f at 0.72f,
                la = 0.60f at 0.90f, ra = 0.40f at 0.90f,
            ),
        ),
        PoseTemplate(
            "jump", "Zıplama", "Eğlenceli",
            "Kollar yukarıda zıplasın; seri çekim için zamanlayıcıyı kullan.",
            pose(
                nose = 0.50f at 0.20f,
                ls = 0.58f at 0.31f, rs = 0.42f at 0.31f,
                le = 0.65f at 0.20f, re = 0.35f at 0.20f,
                lw = 0.70f at 0.09f, rw = 0.30f at 0.09f,
                lh = 0.555f at 0.56f, rh = 0.445f at 0.56f,
                lk = 0.60f at 0.68f, rk = 0.40f at 0.68f,
                la = 0.57f at 0.80f, ra = 0.43f at 0.80f,
            ),
        ),
        PoseTemplate(
            "dance", "Dans", "Eğlenceli",
            "Bir kol havada, bir dizi kırık; hareketin ortasında yakala.",
            pose(
                nose = 0.51f at 0.16f,
                ls = 0.59f at 0.28f, rs = 0.43f at 0.27f,
                le = 0.66f at 0.17f, re = 0.37f at 0.38f,
                lw = 0.69f at 0.06f, rw = 0.33f at 0.48f,
                lh = 0.56f at 0.55f, rh = 0.45f at 0.55f,
                lk = 0.585f at 0.73f, rk = 0.38f at 0.67f,
                la = 0.59f at 0.90f, ra = 0.43f at 0.78f,
            ),
        ),
        PoseTemplate(
            "sit_chair", "Sandalyede", "Oturarak",
            "Sandalyenin ucuna otursun, sırt dik, bacaklar hafif çapraz.",
            pose(
                nose = 0.50f at 0.20f,
                ls = 0.585f at 0.32f, rs = 0.415f at 0.32f,
                le = 0.62f at 0.45f, re = 0.38f at 0.45f,
                lw = 0.585f at 0.57f, rw = 0.43f at 0.57f,
                lh = 0.555f at 0.60f, rh = 0.445f at 0.60f,
                lk = 0.585f at 0.68f, rk = 0.43f at 0.68f,
                la = 0.57f at 0.90f, ra = 0.45f at 0.90f,
            ),
        ),
        PoseTemplate(
            "sit_floor", "Bağdaş", "Oturarak",
            "Yerde bağdaş kursun, elleri dizlerinde.",
            pose(
                nose = 0.50f at 0.34f,
                ls = 0.585f at 0.45f, rs = 0.415f at 0.45f,
                le = 0.635f at 0.58f, re = 0.365f at 0.58f,
                lw = 0.66f at 0.71f, rw = 0.34f at 0.71f,
                lh = 0.555f at 0.73f, rh = 0.445f at 0.73f,
                lk = 0.70f at 0.80f, rk = 0.30f at 0.80f,
                la = 0.46f at 0.84f, ra = 0.54f at 0.84f,
            ),
        ),
        PoseTemplate(
            "thinker", "Düşünceli", "Oturarak",
            "Dirseği dizinde, çenesi elinde; bakışı uzaklarda.",
            pose(
                nose = 0.47f at 0.24f,
                ls = 0.57f at 0.35f, rs = 0.41f at 0.35f,
                le = 0.60f at 0.50f, re = 0.43f at 0.58f,
                lw = 0.62f at 0.63f, rw = 0.46f at 0.28f,
                lh = 0.555f at 0.62f, rh = 0.445f at 0.62f,
                lk = 0.58f at 0.66f, rk = 0.42f at 0.66f,
                la = 0.58f at 0.90f, ra = 0.42f at 0.90f,
            ),
        ),
        PoseTemplate(
            "portrait_chin", "Portre: çene eli", "Portre",
            "Yakın plan: elini hafifçe çenesinin altına koysun.",
            pose(
                nose = 0.50f at 0.36f,
                ls = 0.74f at 0.70f, rs = 0.26f at 0.70f,
                le = 0.80f at 0.98f, re = 0.20f at 0.98f,
                lw = 0.55f at 0.56f, rw = 0.18f at 1.10f,
                lh = 0.66f at 1.30f, rh = 0.34f at 1.30f,
                lk = 0.66f at 1.60f, rk = 0.34f at 1.60f,
                la = 0.66f at 1.90f, ra = 0.34f at 1.90f,
            ),
        ),
    )

    fun find(id: String): PoseTemplate? = all.firstOrNull { it.id == id }
}
