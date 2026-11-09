package com.clipforge.mobile

import androidx.media3.common.PlaybackException

object PreviewFailurePolicy {
    fun useCompatibleDecoder(alreadyCompatible: Boolean, errorCode: Int): Boolean = !alreadyCompatible &&
        errorCode in listOf(PlaybackException.ERROR_CODE_TIMEOUT, PlaybackException.ERROR_CODE_DECODING_FAILED)
}
