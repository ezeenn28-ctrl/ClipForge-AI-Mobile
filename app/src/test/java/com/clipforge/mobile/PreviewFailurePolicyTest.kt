package com.clipforge.mobile

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class PreviewFailurePolicyTest {
    @Test fun decoderTimeoutRetriesOnceInCompatibleMode() {
        assertTrue(PreviewFailurePolicy.useCompatibleDecoder(false, PlaybackException.ERROR_CODE_TIMEOUT))
        assertTrue(PreviewFailurePolicy.useCompatibleDecoder(false, PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertFalse(PreviewFailurePolicy.useCompatibleDecoder(true, PlaybackException.ERROR_CODE_TIMEOUT))
    }
    @Test fun fileAccessFailuresDoNotTriggerAnUnrelatedDecoderSwitch() {
        assertFalse(PreviewFailurePolicy.useCompatibleDecoder(false, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertFalse(PreviewFailurePolicy.useCompatibleDecoder(false, PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
    }
}
