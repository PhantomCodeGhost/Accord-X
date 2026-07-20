package com.phantom.accord

import com.phantom.accord.logic.utils.LrcUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcUtilsTest {

    @Test
    fun emptyInEmptyOut() {
        assertTrue(LrcUtils.parseLrcString("", false).isEmpty())
    }

}