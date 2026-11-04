package com.lens035.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionTest {
    @Test
    fun name() = assertEquals("lens035-core", CoreVersion.NAME)
}
