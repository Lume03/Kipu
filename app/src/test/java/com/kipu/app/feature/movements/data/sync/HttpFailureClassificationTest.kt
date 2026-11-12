package com.kipu.app.feature.movements.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpFailureClassificationTest {
    @Test
    fun expiredSessionAndTransientErrorsAreRetried() {
        listOf(401, 404, 408, 409, 425, 429, 500, 503).forEach { assertFalse("$it", isPermanentHttpFailure(it)) }
    }

    @Test
    fun invalidCommandsArePermanent() {
        listOf(400, 403, 422).forEach { assertTrue("$it", isPermanentHttpFailure(it)) }
    }
}
