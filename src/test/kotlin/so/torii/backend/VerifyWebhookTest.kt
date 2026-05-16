package so.torii.backend

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VerifyWebhookTest {
    @Test
    fun `stub throws explaining feature is not shipped`() {
        val ex = assertThrows(ToriiAuthException::class.java) {
            verifyWebhook("whsec_fake", emptyMap(), ByteArray(0))
        }
        assertTrue(ex.message!!.contains("not shipped yet"))
    }
}
