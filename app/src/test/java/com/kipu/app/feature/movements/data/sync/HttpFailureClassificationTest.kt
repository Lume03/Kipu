package com.kipu.app.feature.movements.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpFailureClassificationTest {
    @Test
    fun expiredSessionAndTransientErrorsAreRetried() {
        listOf(401, 403, 408, 429, 500, 503).forEach { assertFalse("$it", isPermanentHttpFailure(it)) }
    }

    @Test
    fun invalidCommandsArePermanent() {
        listOf(400, 404, 409, 422).forEach { assertTrue("$it", isPermanentHttpFailure(it)) }
    }
}
