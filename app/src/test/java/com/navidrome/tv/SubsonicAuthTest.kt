package com.navidrome.tv

import com.navidrome.tv.data.api.SubsonicAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthTest {
    @Test
    fun `token is md5 of password plus salt`() {
        // 已知向量：md5("sesame" + "c19b2d")
        assertEquals(
            "26719a1196d2a940705a59634eb18eab",
            SubsonicAuth.token("sesame", "c19b2d"),
        )
    }

    @Test
    fun `salt has usable length`() {
        repeat(20) {
            assertTrue(SubsonicAuth.salt().length >= 6)
        }
    }
}
